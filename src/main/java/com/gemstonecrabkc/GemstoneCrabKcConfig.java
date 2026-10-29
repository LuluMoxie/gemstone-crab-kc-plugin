package com.gemstonecrabkc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(GemstoneCrabKcConfig.GROUP)
public interface GemstoneCrabKcConfig extends Config
{
	String GROUP = "gemstonecrabkc";
	String DEFAULT_SERVER_URL = "https://www.gemstonecrabkc.com";

	@ConfigSection(
		name = "Leaderboard sync",
		description = "Sync your kill count to the gemstonecrabkc.com leaderboard",
		position = 0
	)
	String syncSection = "sync";

	@ConfigSection(
		name = "Advanced",
		description = "Settings you normally don't need to change",
		position = 10,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Sync to gemstonecrabkc.com",
		description = "Send your Gemstone Crab kill count to the gemstonecrabkc.com leaderboard after each kill. Each character connects with its own sync key: paste it into the box in the Gemstone Crab KC side panel (crab icon) and click Connect.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0,
		section = syncSection
	)
	default boolean syncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "importGemstoneCrabPlugin",
		name = "Use Gemstone Crab plugin KC",
		description = "If you also use the 'Gemstone Crab' plugin, start from its kill count when it is higher than this plugin's",
		position = 2,
		section = syncSection
	)
	default boolean importGemstoneCrabPlugin()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatMessages",
		name = "Chat messages",
		description = "Show a chat message when your kill count syncs, or if syncing fails",
		position = 3,
		section = syncSection
	)
	default boolean chatMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL",
		description = "Leaderboard server address. Leave as https://www.gemstonecrabkc.com unless you are testing the website locally.",
		position = 0,
		section = advancedSection
	)
	default String serverUrl()
	{
		return DEFAULT_SERVER_URL;
	}
}
