package com.guidehub.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.game.ItemManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Optional, opt-in sync to the user's own Guide Hub. Read-only: it observes game state that RuneLite already exposes
 * and posts JSON to the configured URL. Nothing is sent unless "Enable sync" is on, and only to that URL.
 * Everything here runs on the client thread; network calls are asynchronous.
 */
public class HubSync
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final int SNAPSHOT_TICKS = 100; // about a minute
	private static final int FLUSH_TICKS = 50; // about 30 seconds
	private static final int MAX_QUEUE = 2000;
	private static final int BATCH = 200;
	private static final int ACCOUNT_TYPE_VARBIT = 1777;

	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final GuideHubSyncConfig config;
	private final Gson gson;
	private final OkHttpClient http;

	private final Deque<JsonObject> queue = new ArrayDeque<>();
	private final AtomicBoolean inFlight = new AtomicBoolean();
	private final Map<String, Integer> knownLevels = new HashMap<>();
	private final Map<String, Integer> startXp = new HashMap<>();

	private int ticks;
	private boolean statsDirty;
	private boolean loadoutDirty;
	private long sessionStart;
	private long sessionGp;
	private int sessionKills;
	private int sessionLevelUps;
	private String lastSlayerJson = "";

	public HubSync(Client client, ItemManager itemManager, ConfigManager configManager, GuideHubSyncConfig config,
		Gson gson, OkHttpClient http)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.config = config;
		this.gson = gson;
		this.http = http;
	}

	public void shutDown()
	{
		endSession();
		flush();
	}

	private boolean on()
	{
		return config.syncEnabled() && !config.hubKey().isEmpty() && baseUrl() != null;
	}

	private HttpUrl baseUrl()
	{
		HttpUrl u = HttpUrl.parse(config.hubUrl().trim());
		if (u == null)
		{
			return null;
		}
		// Plain http is only allowed for a Hub on this computer or on the home network (private IP ranges);
		// anything on the internet must use https so the key is not sent in the clear.
		return u.isHttps() || (u.scheme().equals("http") && isPrivateHost(u.host())) ? u : null;
	}

	private static boolean isPrivateHost(String host)
	{
		if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]")
			|| host.endsWith(".local") || host.endsWith(".lan"))
		{
			return true;
		}
		String[] p = host.split("\\.");
		if (p.length != 4)
		{
			return false;
		}
		try
		{
			int a = Integer.parseInt(p[0]);
			int b = Integer.parseInt(p[1]);
			return a == 10 || (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31) || a == 127;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	}

	private String playerName()
	{
		Player p = client.getLocalPlayer();
		return p == null ? null : p.getName();
	}

	// ---- events ----

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (GuideHubSyncConfig.GROUP.equals(e.getGroup()))
		{
			// Settings changed (for example sync just turned on): resend the snapshots at the next tick window.
			statsDirty = true;
			loadoutDirty = true;
			lastSlayerJson = "";
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			startSession();
			statsDirty = true;
			loadoutDirty = true;
		}
		else if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			endSession();
			flush();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		if (e.getSkill() == Skill.OVERALL)
		{
			return;
		}
		statsDirty = true;
		String name = e.getSkill().name();
		int level = e.getLevel();
		Integer old = knownLevels.put(name, level);
		if (old != null && level > old && on())
		{
			sessionLevelUps++;
			JsonObject d = new JsonObject();
			d.addProperty("skill", name);
			d.addProperty("level", level);
			enqueue("levelup", d);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		int id = e.getContainerId();
		if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			loadoutDirty = true;
		}
		else if (id == InventoryID.BANK && on() && config.syncBank())
		{
			sendBank(e.getItemContainer());
		}
	}

	@Subscribe
	public void onLootReceived(LootReceived e)
	{
		if (!on() || !config.syncLoot())
		{
			return;
		}
		JsonArray items = new JsonArray();
		long total = 0;
		Collection<net.runelite.client.game.ItemStack> stacks = e.getItems();
		for (net.runelite.client.game.ItemStack s : stacks)
		{
			long price = itemManager.getItemPrice(s.getId());
			JsonObject it = new JsonObject();
			it.addProperty("id", s.getId());
			it.addProperty("name", itemManager.getItemComposition(s.getId()).getName());
			it.addProperty("qty", s.getQuantity());
			it.addProperty("priceEach", price);
			items.add(it);
			total += (long) price * s.getQuantity();
		}
		JsonObject d = new JsonObject();
		d.addProperty("source", e.getName());
		d.addProperty("kind", e.getType().name());
		d.add("items", items);
		d.addProperty("totalValue", total);
		sessionGp += total;
		sessionKills++;
		enqueue("drop", d);
	}

	@Subscribe
	public void onGameTick(GameTick t)
	{
		if (!on() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		ticks++;
		if (ticks % SNAPSHOT_TICKS == 0)
		{
			if (statsDirty)
			{
				statsDirty = false;
				sendStats();
			}
			if (loadoutDirty)
			{
				loadoutDirty = false;
				sendLoadout();
			}
			if (config.syncSlayer())
			{
				sendSlayer();
			}
		}
		if (ticks % FLUSH_TICKS == 0)
		{
			flush();
		}
	}

	// ---- collectors ----

	private void sendStats()
	{
		JsonObject skills = new JsonObject();
		int total = 0;
		for (Skill s : Skill.values())
		{
			if (s == Skill.OVERALL)
			{
				continue;
			}
			JsonObject v = new JsonObject();
			int lvl = client.getRealSkillLevel(s);
			v.addProperty("level", lvl);
			v.addProperty("xp", client.getSkillExperience(s));
			skills.add(s.name(), v);
			total += lvl;
			knownLevels.putIfAbsent(s.name(), lvl);
		}
		JsonObject d = new JsonObject();
		d.add("skills", skills);
		Player p = client.getLocalPlayer();
		if (p != null)
		{
			d.addProperty("combatLevel", p.getCombatLevel());
		}
		d.addProperty("totalLevel", total);
		enqueue("stats", d);
	}

	private void sendLoadout()
	{
		JsonObject d = new JsonObject();
		JsonArray eq = new JsonArray();
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			Item[] items = worn.getItems();
			for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
			{
				int idx = slot.getSlotIdx();
				if (idx < items.length && items[idx].getId() > 0)
				{
					eq.add(itemJson(items[idx], slot.name()));
				}
			}
		}
		JsonArray inv = new JsonArray();
		ItemContainer pack = client.getItemContainer(InventoryID.INV);
		if (pack != null)
		{
			for (Item it : pack.getItems())
			{
				if (it.getId() > 0)
				{
					inv.add(itemJson(it, null));
				}
			}
		}
		d.add("equipment", eq);
		d.add("inventory", inv);
		enqueue("loadout", d);
	}

	private JsonObject itemJson(Item it, String slot)
	{
		JsonObject o = new JsonObject();
		if (slot != null)
		{
			o.addProperty("slot", slot);
		}
		o.addProperty("id", it.getId());
		o.addProperty("name", itemManager.getItemComposition(it.getId()).getName());
		o.addProperty("qty", it.getQuantity());
		return o;
	}

	private void sendBank(ItemContainer bank)
	{
		if (bank == null)
		{
			return;
		}
		long value = 0;
		JsonArray items = new JsonArray();
		for (Item it : bank.getItems())
		{
			if (it.getId() <= 0 || it.getQuantity() <= 0)
			{
				continue;
			}
			value += (long) itemManager.getItemPrice(it.getId()) * it.getQuantity();
			if (config.syncBankItems())
			{
				JsonObject o = new JsonObject();
				o.addProperty("id", it.getId());
				o.addProperty("name", itemManager.getItemComposition(it.getId()).getName());
				o.addProperty("qty", it.getQuantity());
				items.add(o);
			}
		}
		JsonObject d = new JsonObject();
		d.addProperty("value", value);
		if (config.syncBankItems())
		{
			d.add("items", items);
		}
		enqueue("bank", d);
	}

	/** Reads the core Slayer plugin's saved state from the profile config; keys may change between releases. */
	private void sendSlayer()
	{
		String task = cfg("taskName");
		JsonObject d = new JsonObject();
		d.addProperty("master", "");
		d.addProperty("task", task == null ? "" : task);
		d.addProperty("remaining", intCfg("amount"));
		d.addProperty("initial", intCfg("initialAmount"));
		d.addProperty("streak", intCfg("streak"));
		d.addProperty("points", intCfg("points"));
		d.addProperty("location", cfg("taskLoc") == null ? "" : cfg("taskLoc"));
		String json = d.toString();
		if (!json.equals(lastSlayerJson))
		{
			lastSlayerJson = json;
			enqueue("slayer", d);
		}
	}

	private String cfg(String key)
	{
		return configManager.getRSProfileConfiguration("slayer", key);
	}

	private int intCfg(String key)
	{
		try
		{
			String v = cfg(key);
			return v == null ? 0 : Integer.parseInt(v);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	// ---- sessions ----

	private void startSession()
	{
		if (sessionStart != 0)
		{
			return;
		}
		sessionStart = System.currentTimeMillis();
		sessionGp = 0;
		sessionKills = 0;
		sessionLevelUps = 0;
		startXp.clear();
		for (Skill s : Skill.values())
		{
			if (s != Skill.OVERALL)
			{
				startXp.put(s.name(), client.getSkillExperience(s));
			}
		}
	}

	private void endSession()
	{
		if (sessionStart == 0)
		{
			return;
		}
		long end = System.currentTimeMillis();
		if (on() && end - sessionStart > 60_000)
		{
			JsonObject xp = new JsonObject();
			for (Skill s : Skill.values())
			{
				Integer base = startXp.get(s.name());
				if (s != Skill.OVERALL && base != null)
				{
					int gained = client.getSkillExperience(s) - base;
					if (gained > 0)
					{
						xp.addProperty(s.name(), gained);
					}
				}
			}
			JsonObject d = new JsonObject();
			d.addProperty("start", sessionStart);
			d.addProperty("end", end);
			d.addProperty("gp", sessionGp);
			d.addProperty("kills", sessionKills);
			d.addProperty("levelUps", sessionLevelUps);
			d.add("xp", xp);
			enqueue("session", d);
		}
		sessionStart = 0;
	}

	// ---- queue + upload ----

	private void enqueue(String type, JsonObject data)
	{
		JsonObject ev = new JsonObject();
		ev.addProperty("type", type);
		ev.addProperty("ts", System.currentTimeMillis());
		ev.add("data", data);
		synchronized (queue)
		{
			queue.addLast(ev);
			while (queue.size() > MAX_QUEUE)
			{
				queue.removeFirst();
			}
		}
	}

	private void flush()
	{
		String name = playerName();
		HttpUrl base = baseUrl();
		if (!on() || name == null || base == null || !inFlight.compareAndSet(false, true))
		{
			return;
		}
		final JsonArray batch = new JsonArray();
		final List<JsonObject> taken = new java.util.ArrayList<>();
		synchronized (queue)
		{
			while (!queue.isEmpty() && taken.size() < BATCH)
			{
				JsonObject ev = queue.removeFirst();
				taken.add(ev);
				batch.add(ev);
			}
		}
		if (taken.isEmpty())
		{
			inFlight.set(false);
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("player", name);
		body.addProperty("accountType", accountType());
		body.add("events", batch);
		Request req = new Request.Builder()
			.url(base.newBuilder().addPathSegments("api/ingest").build())
			.header("Authorization", "Bearer " + config.hubKey())
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				requeue(taken);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					// 4xx means the hub refused the data (bad key etc.); retrying the same batch would loop forever.
					if (r.code() >= 500)
					{
						requeue(taken);
					}
					else
					{
						inFlight.set(false);
					}
				}
			}
		});
	}

	private void requeue(List<JsonObject> taken)
	{
		synchronized (queue)
		{
			for (int i = taken.size() - 1; i >= 0; i--)
			{
				queue.addFirst(taken.get(i));
			}
			while (queue.size() > MAX_QUEUE)
			{
				queue.removeFirst();
			}
		}
		inFlight.set(false);
	}

	private String accountType()
	{
		switch (client.getVarbitValue(ACCOUNT_TYPE_VARBIT))
		{
			case 1:
				return "IRONMAN";
			case 2:
				return "ULTIMATE_IRONMAN";
			case 3:
				return "HARDCORE_IRONMAN";
			default:
				return "NORMAL";
		}
	}
}
