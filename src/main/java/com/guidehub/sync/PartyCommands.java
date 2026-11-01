package com.guidehub.sync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Chat commands for Guide Hub parties. These are RuneLite client-side "::" commands: nothing is sent to other players.
 *
 * ::party create <name>   ::party join <code>   ::party leave   ::party status   ::party pause   ::party resume
 */
public class PartyCommands
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String PREFIX = "[Guide Hub] ";

	private final Client client;
	private final ClientThread clientThread;
	private final GuideHubSyncConfig config;
	private final Gson gson;
	private final OkHttpClient http;

	/** Last known party summary, for the overlay/panel. Null when not in a party or unknown. */
	private volatile PartyState state;
	private volatile Runnable listener;
	private volatile String lastMessage = "";

	public PartyCommands(Client client, ClientThread clientThread, GuideHubSyncConfig config, Gson gson, OkHttpClient http)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.gson = gson;
		this.http = http;
	}

	public PartyState getState()
	{
		return state;
	}

	public String getLastMessage()
	{
		return lastMessage;
	}

	/** Called (on a background thread) whenever the party state or last message changes. */
	public void setListener(Runnable listener)
	{
		this.listener = listener;
	}

	public boolean isConfigured()
	{
		return configured();
	}

	private void setState(PartyState s)
	{
		state = s;
		Runnable l = listener;
		if (l != null)
		{
			l.run();
		}
		// Summary endpoints do not include members/feed: fetch the detail so the panel can list who got what.
		if (s != null && s.memberList.isEmpty() && s.id > 0)
		{
			get("api/parties/" + s.id + "?limit=8", json ->
			{
				PartyState cur = state;
				if (cur != null && cur.id == s.id)
				{
					state = PartyState.from(json);
					Runnable l2 = listener;
					if (l2 != null)
					{
						l2.run();
					}
				}
			}, err -> { });
		}
	}

	// ---- public actions (used by the side panel and the chat commands) ----

	public void create(String name)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name == null || name.trim().isEmpty() ? "Party" : name.trim());
		post("api/parties", body, json ->
		{
			setState(PartyState.from(json));
			copy(state.code);
			say("Created party '" + state.name + "'. Code: " + state.code + " (copied to clipboard). Friends join with ::party join " + state.code);
		});
	}

	public void join(String code)
	{
		JsonObject body = new JsonObject();
		body.addProperty("code", code == null ? "" : code.trim());
		post("api/parties/join", body, json ->
		{
			setState(PartyState.from(json));
			say("Joined '" + state.name + "' (" + state.members + " member" + (state.members == 1 ? "" : "s") + "). Your drops now count for the party.");
		});
	}

	public void leave()
	{
		post("api/parties/leave", new JsonObject(), json ->
		{
			setState(null);
			say("Left the party.");
		});
	}

	public void setPaused(boolean paused)
	{
		JsonObject body = new JsonObject();
		body.addProperty("paused", paused);
		post("api/parties/pause", body, json ->
		{
			setState(PartyState.from(json));
			say(paused ? "Paused: your drops are not counted until you resume." : "Resumed: your drops count for the party again.");
		});
	}

	public void status(boolean announce)
	{
		get("api/parties", json ->
		{
			JsonObject cur = json.has("current") && json.get("current").isJsonObject() ? json.getAsJsonObject("current") : null;
			if (cur == null)
			{
				setState(null);
				if (announce)
				{
					say("You are not in a party. ::party create <name> or ::party join <code>");
				}
			}
			else
			{
				setState(PartyState.from(cur));
				if (announce)
				{
					say("Party '" + state.name + "': " + state.members + " members, " + fmtGp(state.totalValue) + " gp from " + state.drops
						+ " drops. Code " + state.code + (state.paused ? " (you are paused)" : ""));
				}
			}
		}, announce ? this::sayError : err -> { });
	}

	/** Fetches the current party once (used at login so the overlay is right without typing a command). */
	public void refresh()
	{
		if (configured())
		{
			status(false);
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted e)
	{
		if (!"party".equalsIgnoreCase(e.getCommand()))
		{
			return;
		}
		String[] args = e.getArguments();
		String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
		if (!configured())
		{
			say("Set your Hub URL and account key in the Guide Hub Sync plugin settings first.");
			return;
		}
		switch (sub)
		{
			case "create":
				create(args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Party");
				break;
			case "join":
				if (args.length < 2)
				{
					say("Usage: ::party join ABCD-EFGH");
					return;
				}
				join(args[1]);
				break;
			case "leave":
				leave();
				break;
			case "pause":
				setPaused(true);
				break;
			case "resume":
				setPaused(false);
				break;
			case "status":
				status(true);
				break;
			default:
				say("Commands: ::party create <name> | join <code> | leave | status | pause | resume");
		}
	}

	// ---- http ----

	private boolean configured()
	{
		return !config.hubKey().isEmpty() && base() != null;
	}

	private HttpUrl base()
	{
		HttpUrl u = HttpUrl.parse(config.hubUrl().trim());
		return u == null ? null : u;
	}

	private Request.Builder req(String path)
	{
		// "api/x/1?limit=8": the query part must be added as parameters, not as path segments.
		String query = null;
		int q = path.indexOf('?');
		if (q >= 0)
		{
			query = path.substring(q + 1);
			path = path.substring(0, q);
		}
		HttpUrl.Builder b = base().newBuilder().addPathSegments(path);
		if (query != null)
		{
			for (String kv : query.split("&"))
			{
				int eq = kv.indexOf('=');
				b.addQueryParameter(eq < 0 ? kv : kv.substring(0, eq), eq < 0 ? "" : kv.substring(eq + 1));
			}
		}
		return new Request.Builder()
			.url(b.build())
			.header("Authorization", "Bearer " + config.hubKey());
	}

	private void post(String path, JsonObject body, Consumer<JsonObject> ok)
	{
		run(req(path).post(RequestBody.create(JSON, gson.toJson(body))).build(), ok, this::sayError);
	}

	private void get(String path, Consumer<JsonObject> ok, Consumer<String> err)
	{
		run(req(path).get().build(), ok, err);
	}

	private void run(Request request, Consumer<JsonObject> ok, Consumer<String> err)
	{
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				err.accept("Could not reach the Hub (" + e.getMessage() + ")");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					String text = r.body() == null ? "" : r.body().string();
					JsonObject json;
					try
					{
						json = text.isEmpty() ? new JsonObject() : gson.fromJson(text, JsonObject.class);
					}
					catch (RuntimeException ex)
					{
						json = new JsonObject();
					}
					if (json == null)
					{
						json = new JsonObject();
					}
					if (r.code() >= 400)
					{
						String msg = json.has("error") ? json.get("error").getAsString() : "HTTP " + r.code();
						err.accept(r.code() == 401 ? "Your account key was rejected; check the plugin settings." : msg);
						return;
					}
					ok.accept(json);
				}
				catch (IOException ex)
				{
					err.accept("Bad reply from the Hub");
				}
			}
		});
	}

	// ---- chat ----

	private void say(String msg)
	{
		lastMessage = msg;
		Runnable l = listener;
		if (l != null)
		{
			l.run();
		}
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == net.runelite.api.GameState.LOGGED_IN)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + msg, null);
			}
		});
	}

	private void sayError(String msg)
	{
		say("Party: " + msg);
	}

	private static void copy(String text)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		}
		catch (RuntimeException ignored)
		{
			// headless or clipboard busy: the code is still printed in chat
		}
	}

	static String fmtGp(long n)
	{
		if (n >= 1_000_000_000L)
		{
			return String.format(Locale.ROOT, "%.2fB", n / 1_000_000_000.0);
		}
		if (n >= 1_000_000L)
		{
			return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
		}
		if (n >= 1_000L)
		{
			return (n / 1_000) + "K";
		}
		return Long.toString(n);
	}

	/** Snapshot of the party summary the server returns. */
	public static final class Member
	{
		public final String name;
		public final long value;
		public final int drops;
		public final boolean active;
		public final boolean paused;

		Member(String name, long value, int drops, boolean active, boolean paused)
		{
			this.name = name;
			this.value = value;
			this.drops = drops;
			this.active = active;
			this.paused = paused;
		}
	}

	public static final class Loot
	{
		public final String player;
		public final String source;
		public final long value;
		public final String items;
		public final long ts;

		Loot(String player, String source, long value, String items, long ts)
		{
			this.player = player;
			this.source = source;
			this.value = value;
			this.items = items;
			this.ts = ts;
		}
	}

	public static final class PartyState
	{
		public final int id;
		public final String name;
		public final String code;
		public final int members;
		public final long totalValue;
		public final int drops;
		public final boolean paused;
		/** Filled in by the detail fetch; empty until then. */
		public final java.util.List<Member> memberList;
		public final java.util.List<Loot> recent;

		private PartyState(int id, String name, String code, int members, long totalValue, int drops, boolean paused,
			java.util.List<Member> memberList, java.util.List<Loot> recent)
		{
			this.id = id;
			this.name = name;
			this.code = code;
			this.members = members;
			this.totalValue = totalValue;
			this.drops = drops;
			this.paused = paused;
			this.memberList = memberList;
			this.recent = recent;
		}

		static PartyState from(JsonObject j)
		{
			java.util.List<Member> mem = new java.util.ArrayList<>();
			if (j.has("members") && j.get("members").isJsonArray())
			{
				for (com.google.gson.JsonElement e : j.getAsJsonArray("members"))
				{
					JsonObject m = e.getAsJsonObject();
					mem.add(new Member(str(m, "name", "?"), num(m, "value"), (int) num(m, "drops"),
						m.has("active") && m.get("active").getAsBoolean(), m.has("paused") && m.get("paused").getAsBoolean()));
				}
			}
			java.util.List<Loot> recent = new java.util.ArrayList<>();
			if (j.has("feed") && j.get("feed").isJsonArray())
			{
				for (com.google.gson.JsonElement e : j.getAsJsonArray("feed"))
				{
					JsonObject f = e.getAsJsonObject();
					StringBuilder items = new StringBuilder();
					if (f.has("items") && f.get("items").isJsonArray())
					{
						int n = 0;
						for (com.google.gson.JsonElement ie : f.getAsJsonArray("items"))
						{
							JsonObject it = ie.getAsJsonObject();
							if (n++ == 3)
							{
								items.append(", +").append(f.getAsJsonArray("items").size() - 3).append(" more");
								break;
							}
							if (items.length() > 0)
							{
								items.append(", ");
							}
							items.append(str(it, "name", "?"));
							long q = num(it, "qty");
							if (q > 1)
							{
								items.append(" x").append(q);
							}
						}
					}
					recent.add(new Loot(str(f, "player", "?"), str(f, "source", "?"), num(f, "totalValue"), items.toString(), num(f, "ts")));
					if (recent.size() >= 8)
					{
						break;
					}
				}
			}
			return new PartyState(
				(int) num(j, "id"), str(j, "name", "Party"), str(j, "code", "?"),
				(int) num(j, "memberCount"), num(j, "totalValue"), (int) num(j, "drops"),
				j.has("paused") && j.get("paused").getAsBoolean(), mem, recent);
		}

		private static long num(JsonObject j, String k)
		{
			return j.has(k) && !j.get(k).isJsonNull() && j.get(k).isJsonPrimitive() ? j.get(k).getAsLong() : 0;
		}

		private static String str(JsonObject j, String k, String d)
		{
			return j.has(k) && !j.get(k).isJsonNull() ? j.get(k).getAsString() : d;
		}
	}
}
