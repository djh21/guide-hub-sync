package com.guidehub.sync;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import okhttp3.OkHttpClient;

/**
 * Guide Hub Sync: opt-in, read-only. Observes stats, worn gear, inventory, bank, Slayer task and loot that RuneLite
 * already exposes and posts them to the Guide Hub URL the user configured. Nothing is sent until "Enable sync" is on
 * and a key is set; nothing ever controls the game.
 */
@Slf4j
@PluginDescriptor(
	name = "Guide Hub Sync",
	description = "Opt-in sync of stats, gear, bank and Slayer task to your Guide Hub web app, plus ::party loot parties",
	tags = {"guide", "gear", "bank", "sync", "slayer", "companion"}
)
public class GuideHubSyncPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GuideHubSyncConfig config;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	private PartyCommands partyCommands;
	private PartyOverlay partyOverlay;
	private PartyPanel partyPanel;
	private int ticks;
	private NavigationButton navButton;

	private HubSync hubSync;

	@Override
	protected void startUp()
	{
		hubSync = new HubSync(client, itemManager, configManager, config, gson, okHttpClient);
		eventBus.register(hubSync);
		partyCommands = new PartyCommands(client, clientThread, config, gson, okHttpClient);
		eventBus.register(partyCommands);
		partyOverlay = new PartyOverlay(partyCommands, config);
		overlayManager.add(partyOverlay);
		partyPanel = new PartyPanel(partyCommands, config, configManager);
		navButton = NavigationButton.builder()
			.tooltip("Guide Hub Party")
			.icon(buildIcon())
			.priority(7)
			.panel(partyPanel)
			.build();
		clientToolbar.addNavigation(navButton);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			partyCommands.refresh();
		}
		log.debug("Guide Hub Sync started (sync {})", config.syncEnabled() ? "enabled" : "off");
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
			partyPanel = null;
		}
		if (partyOverlay != null)
		{
			overlayManager.remove(partyOverlay);
			partyOverlay = null;
		}
		if (partyCommands != null)
		{
			eventBus.unregister(partyCommands);
			partyCommands = null;
		}
		if (hubSync != null)
		{
			eventBus.unregister(hubSync);
			hubSync.shutDown();
			hubSync = null;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN && partyCommands != null)
		{
			partyCommands.refresh();
		}
	}

	/** 16x16 toolbar icon: two orange heads (a party), drawn in code so no image file is needed. */
	private static BufferedImage buildIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0xF9, 0x73, 0x16));
		g.fillOval(2, 2, 5, 5);
		g.fillOval(9, 2, 5, 5);
		g.fillRoundRect(1, 8, 7, 7, 4, 4);
		g.fillRoundRect(8, 8, 7, 7, 4, 4);
		g.dispose();
		return img;
	}

	@Subscribe
	public void onGameTick(GameTick t)
	{
		// Refresh the party panel/overlay about every 30 seconds while in a party, so loot appears without clicking.
		if (partyCommands != null && partyCommands.getState() != null && ++ticks % 50 == 0)
		{
			partyCommands.refresh();
		}
	}

	@Provides
	GuideHubSyncConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GuideHubSyncConfig.class);
	}
}
