package com.guidehub.sync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GuideHubSyncConfig.GROUP)
public interface GuideHubSyncConfig extends Config
{
	String GROUP = "guidehubsync";

	@ConfigItem(keyName = "syncEnabled", name = "Enable sync", description = "Off by default. When on, the data listed below is sent to the Hub URL you set, and nowhere else.", position = 1)
	default boolean syncEnabled()
	{
		return false;
	}

	@ConfigItem(keyName = "hubUrl", name = "Hub URL", description = "Guide Hub address. Leave as is unless you run your own Hub (https://..., or http://127.0.0.1:8787 on this computer)", position = 2)
	default String hubUrl()
	{
		return "https://hub.gamingcircle.org";
	}

	@ConfigItem(keyName = "hubKey", name = "Account key", description = "Your Guide Hub account key (ghk_...), shown once when you create the account and under Account > Rotate key", secret = true, position = 3)
	default String hubKey()
	{
		return "";
	}

	@ConfigItem(keyName = "syncBank", name = "Send bank", description = "Bank value plus item names and quantities, sent when you open your bank. Powers 'best setup from what you own'.", position = 4)
	default boolean syncBank()
	{
		return true;
	}

	@ConfigItem(keyName = "syncBankItems", name = "Include bank item list", description = "Turn off to send only the total bank value", position = 5)
	default boolean syncBankItems()
	{
		return true;
	}

	@ConfigItem(keyName = "syncLoot", name = "Send loot", description = "Boss and monster drops (names, quantities, GE value)", position = 6)
	default boolean syncLoot()
	{
		return true;
	}

	@ConfigItem(keyName = "syncSlayer", name = "Send Slayer task", description = "Current task, kills left, streak and points (read from the built-in Slayer plugin)", position = 7)
	default boolean syncSlayer()
	{
		return true;
	}

	@ConfigItem(keyName = "partyOverlay", name = "Party overlay", description = "Show a small on-screen box with your current party (name, members, loot). Off by default; also toggled from the party panel.", position = 8)
	default boolean partyOverlay()
	{
		return false;
	}
}
