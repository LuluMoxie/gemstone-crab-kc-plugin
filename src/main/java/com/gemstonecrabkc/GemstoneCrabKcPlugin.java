package com.gemstonecrabkc;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Gemstone Crab KC",
	description = "Tracks your Gemstone Crab kill count and syncs it to the gemstonecrabkc.com leaderboard",
	tags = {"gemstone", "crab", "kc", "kill", "count", "leaderboard", "varlamore", "tracker"}
)
public class GemstoneCrabKcPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(GemstoneCrabKcPlugin.class);

	static final String PLUGIN_VERSION = "1.0.0";

	/** RuneLite's required wording for features that talk to a 3rd-party server. */
	static final String THIRD_PARTY_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";

	private static final String NOT_CONNECTED = "This character isn't connected yet. Paste its sync key in the box above and click Connect.";

	/** Sync keys are saved per character (RuneScape profile): one key per character. */
	private static final String KEY_SYNC_KEY = "syncKey";

	/**
	 * Shown to everyone nearby when a fight ends. A kill is counted here when
	 * the player fought the crab for more than {@link #MIN_FIGHT_MS}: exactly
	 * the rule the popular "Gemstone Crab" plugin uses for its KC, so the two
	 * plugins always count the same kills.
	 */
	static final String BURROW_MESSAGE = "The gemstone crab burrows away, leaving a piece of its shell behind.";

	/** How long the player must have been fighting for a burrow to count as a kill. */
	static final long MIN_FIGHT_MS = 5 * 60 * 1000;
	/** "The top three crab crushers were A, B, & C!" or "The top crab crusher was A!" */
	static final Pattern TOP_CRUSHERS = Pattern.compile("^The top (?:\\w+ )?crab crushers? (?:were|was) (.+?)!?$");

	// Our own per-account counters (stored per RuneScape profile).
	private static final String KEY_KILLS = "killCount";
	private static final String KEY_TOP3 = "top3Count";

	// Counters kept by the popular "Gemstone Crab" plugin, which most players
	// already use. Reading them lets a player's existing KC carry over.
	private static final String OTHER_PLUGIN_GROUP = "gemstonecrab";
	private static final String OTHER_KEY_KILLS = "crabCount";
	private static final String OTHER_KEY_TOP3 = "top3Count";

	private static final long KILL_COOLDOWN_MS = 60_000;
	private static final int AFTER_KILL_DELAY_TICKS = 3;
	private static final int AFTER_LOGIN_DELAY_TICKS = 5;
	private static final long MIN_SYNC_INTERVAL_MS = 10_000;

	private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GemstoneCrabKcConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private SyncClient syncClient;

	private GemstoneCrabKcPanel panel;
	private NavigationButton navButton;

	private int kills;
	private int top3;
	private long lastKillAt;

	// Current fight. Reset when a new crab appears.
	private boolean crabPresent;
	private long fightStartAt;
	private long lastCombatXpAt;
	private boolean killCountedThisFight;
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);

	private int syncCountdown = -1;
	private boolean forceNextSync;
	private boolean syncInFlight;
	private long lastSyncAt;
	private SyncRequest lastSent;
	private Integer lastAnnouncedKills;
	private String lastErrorCode;

	@Override
	protected void startUp()
	{
		panel = new GemstoneCrabKcPanel(this::connect, this::disconnect, this::requestManualSync, this::openLeaderboard, this::openSyncPage);
		panel.setConnected(isConnected());
		BufferedImage icon = ImageUtil.loadImageResource(GemstoneCrabKcPlugin.class, "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Gemstone Crab KC")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadCounts();
				scheduleSync(1, true);
			}
		});
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		resetSessionState();
		resetFight();
		lastXp.clear();
		kills = 0;
		top3 = 0;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// Switched to a different account: load that account's counts and key.
		loadCounts();
		resetSessionState();
		if (panel != null)
		{
			panel.setConnected(isConnected());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Give the client a moment to load the player's name and account type.
			scheduleSync(AFTER_LOGIN_DELAY_TICKS, true);
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Logged out (maybe switching accounts): forget the current fight.
			resetFight();
			lastXp.clear();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (event.getNpc().getId() == NpcID.GEMSTONE_CRAB)
		{
			// A new crab (or we just arrived at one): start a new fight.
			crabPresent = true;
			fightStartAt = System.currentTimeMillis();
			lastCombatXpAt = 0;
			killCountedThisFight = false;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc().getId() == NpcID.GEMSTONE_CRAB)
		{
			crabPresent = false;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int xp = event.getXp();
		Integer previous = lastXp.put(skill, xp);
		if (previous == null || xp <= previous || !crabPresent)
		{
			return;
		}
		switch (skill)
		{
			case ATTACK:
			case STRENGTH:
			case DEFENCE:
			case RANGED:
			case MAGIC:
				lastCombatXpAt = System.currentTimeMillis();
				break;
			default:
				break;
		}
	}

	/** True when the player fought the crab for long enough this fight. */
	private boolean fightQualifies()
	{
		return qualifies(fightStartAt, lastCombatXpAt);
	}

	/**
	 * Same rule as the Gemstone Crab plugin: the time from the crab appearing
	 * to the player's last combat XP on it must be more than 5 minutes.
	 */
	static boolean qualifies(long fightStartAt, long lastCombatXpAt)
	{
		return fightStartAt > 0
			&& lastCombatXpAt > 0
			&& lastCombatXpAt - fightStartAt > MIN_FIGHT_MS;
	}

	/** Adds one kill, at most once per crab fight. */
	private void countKill(String reason)
	{
		long now = System.currentTimeMillis();
		if (killCountedThisFight || now - lastKillAt < KILL_COOLDOWN_MS)
		{
			return;
		}
		killCountedThisFight = true;
		lastKillAt = now;
		if (mirrorsOtherPlugin())
		{
			// The Gemstone Crab plugin counts this kill itself; we pick up its
			// new number when we sync a few ticks from now.
			scheduleSync(AFTER_KILL_DELAY_TICKS, false);
			return;
		}
		kills++;
		saveCounts();
		log.debug("Gemstone Crab kill counted ({}), KC now {}", reason, kills);
		scheduleSync(AFTER_KILL_DELAY_TICKS, false);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage()).replace('\u00A0', ' ').trim();

		if (message.equals(BURROW_MESSAGE))
		{
			// The fight is over. It counts as a kill if the player fought the
			// crab for more than 5 minutes.
			if (fightQualifies())
			{
				countKill("fought for 5+ minutes");
			}
			// The Gemstone Crab plugin also bumps its own count on this message,
			// so sync shortly after in case its number moved.
			scheduleSync(AFTER_KILL_DELAY_TICKS, false);
		}
		else
		{
			Matcher matcher = TOP_CRUSHERS.matcher(message);
			if (matcher.matches() && isLocalPlayerListed(matcher.group(1)))
			{
				if (mirrorsOtherPlugin())
				{
					// Counted by the Gemstone Crab plugin; picked up on sync.
					scheduleSync(AFTER_KILL_DELAY_TICKS, false);
					return;
				}
				top3++;
				saveCounts();
				log.debug("Top 3 finish counted, now {}", top3);
				scheduleSync(AFTER_KILL_DELAY_TICKS, false);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (syncCountdown < 0)
		{
			return;
		}
		if (syncCountdown > 0)
		{
			syncCountdown--;
			return;
		}
		syncCountdown = -1;
		boolean force = forceNextSync;
		forceNextSync = false;
		syncNow(force);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GemstoneCrabKcConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		switch (event.getKey())
		{
			case "syncEnabled":
			case "syncKey":
			case "serverUrl":
			case "importGemstoneCrabPlugin":
				clientThread.invoke(() ->
				{
					resetSessionState();
					scheduleSync(1, true);
					if (panel != null)
					{
						panel.setConnected(isConnected());
						if (!isConnected())
						{
							panel.setStatus(NOT_CONNECTED, ColorScheme.LIGHT_GRAY_COLOR);
						}
					}
				});
				break;
			default:
				break;
		}
	}

	/** This character's saved key, or null. Needs a logged-in character. */
	private String characterKey()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return null;
		}
		String key = configManager.getRSProfileConfiguration(GemstoneCrabKcConfig.GROUP, KEY_SYNC_KEY);
		return key == null || key.trim().isEmpty() ? null : key.trim();
	}

	private boolean isConnected()
	{
		return config.syncEnabled() && characterKey() != null;
	}

	/**
	 * "Connect" button in the panel: saves the pasted key and turns syncing on,
	 * after showing RuneLite's 3rd-party warning (the same warning as ticking
	 * the setting). Runs on the Swing thread.
	 */
	private void connect(String pasted)
	{
		String key = pasted == null ? "" : pasted.trim();
		if (key.isEmpty())
		{
			panel.setKeyError("Paste your sync key in the box first (Ctrl+V).");
			return;
		}
		if (!key.startsWith("gck_") || key.length() < 20 || key.contains(" "))
		{
			panel.setKeyError("That doesn't look like a sync key. It starts with gck_ - copy it again from the website.");
			return;
		}
		if (configManager.getRSProfileKey() == null || client.getGameState() != GameState.LOGGED_IN)
		{
			panel.setKeyError("Log in to the character first, then paste its key. Each character has its own key.");
			return;
		}
		panel.setKeyError(null);

		int choice = JOptionPane.showConfirmDialog(
			SwingUtilities.getWindowAncestor(panel),
			THIRD_PARTY_WARNING + ".\n\nOnly your RuneScape name, account type and Gemstone Crab kill count are sent.\n\nConnect to gemstonecrabkc.com?",
			"Gemstone Crab KC",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}

		configManager.setRSProfileConfiguration(GemstoneCrabKcConfig.GROUP, KEY_SYNC_KEY, key);
		configManager.setConfiguration(GemstoneCrabKcConfig.GROUP, "syncEnabled", true);
		panel.setConnected(true);
		panel.setStatus("Connecting...", ColorScheme.LIGHT_GRAY_COLOR);
	}

	/** "Disconnect" button: removes this character's key. Other characters keep syncing. */
	private void disconnect()
	{
		forgetCharacterKey();
		panel.setStatus("Disconnected this character. Its kill count is still tracked here, but no longer sent to the leaderboard.", ColorScheme.LIGHT_GRAY_COLOR);
	}

	private void forgetCharacterKey()
	{
		if (configManager.getRSProfileKey() != null)
		{
			configManager.unsetRSProfileConfiguration(GemstoneCrabKcConfig.GROUP, KEY_SYNC_KEY);
		}
		if (panel != null)
		{
			panel.setConnected(false);
		}
	}

	private void scheduleSync(int ticks, boolean force)
	{
		forceNextSync |= force;
		if (syncCountdown < 0 || ticks < syncCountdown)
		{
			syncCountdown = ticks;
		}
	}

	private void requestManualSync()
	{
		clientThread.invoke(() -> syncNow(true));
	}

	/** Must run on the client thread. */
	private void syncNow(boolean force)
	{
		if (panel == null)
		{
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			panel.setStatus("Log in to sync your kill count.", ColorScheme.LIGHT_GRAY_COLOR);
			return;
		}

		refreshFromOtherPlugin();
		panel.setCounts(kills, top3);

		if (!isConnected())
		{
			panel.setStatus(NOT_CONNECTED, ColorScheme.LIGHT_GRAY_COLOR);
			return;
		}
		String key = characterKey();

		Player local = client.getLocalPlayer();
		long accountHash = client.getAccountHash();
		if (local == null || local.getName() == null || accountHash == -1)
		{
			// Still loading; try again shortly.
			scheduleSync(2, force);
			return;
		}

		SyncRequest request = new SyncRequest(
			Long.toString(accountHash),
			Text.removeTags(local.getName()).replace('\u00A0', ' ').trim(),
			accountType(client.getVarbitValue(VarbitID.IRONMAN)),
			kills,
			top3,
			PLUGIN_VERSION);

		if (!force && request.equals(lastSent))
		{
			return;
		}
		if (syncInFlight)
		{
			scheduleSync(5, force);
			return;
		}
		long sinceLast = System.currentTimeMillis() - lastSyncAt;
		if (sinceLast < MIN_SYNC_INTERVAL_MS)
		{
			int ticks = (int) ((MIN_SYNC_INTERVAL_MS - sinceLast) / 600) + 1;
			scheduleSync(ticks, force);
			return;
		}

		syncInFlight = true;
		lastSyncAt = System.currentTimeMillis();
		panel.setStatus("Syncing...", ColorScheme.LIGHT_GRAY_COLOR);
		syncClient.sync(config.serverUrl(), key, request,
			result -> clientThread.invoke(() -> onSyncResult(request, result)));
	}

	/** Runs on the client thread. */
	private void onSyncResult(SyncRequest request, SyncResult result)
	{
		syncInFlight = false;
		if (panel == null)
		{
			return;
		}

		if (result.isOk())
		{
			lastSent = request;
			lastErrorCode = null;
			int shown = result.getKillCount() != null ? result.getKillCount() : request.getKillCount();
			if (shown > kills && !mirrorsOtherPlugin())
			{
				// The leaderboard already has a higher verified number (e.g. an
				// approved screenshot). Count up from it, so the next kill moves
				// the leaderboard straight away instead of catching up first.
				log.debug("Adopting leaderboard KC {} (was {})", shown, kills);
				kills = shown;
				saveCounts();
			}
			panel.setRank(result.getRank());
			String text = "Synced as " + request.getRsn() + ". The leaderboard shows " + NUMBER.format(shown) + " KC.";
			if (result.getMessage() != null && !result.getMessage().isEmpty())
			{
				text = text + " " + result.getMessage();
			}
			panel.setStatus(text, ColorScheme.PROGRESS_COMPLETE_COLOR);

			if (lastAnnouncedKills == null || lastAnnouncedKills != shown)
			{
				lastAnnouncedKills = shown;
				String rankText = result.getRank() != null ? " (rank #" + NUMBER.format(result.getRank()) + ")" : "";
				chat("Gemstone Crab KC synced: " + NUMBER.format(shown) + rankText);
			}
		}
		else
		{
			String message = result.getMessage() != null ? result.getMessage() : "Sync failed.";
			if ("BAD_KEY".equals(result.getCode()) || "KEY_FOR_OTHER_CHARACTER".equals(result.getCode()))
			{
				// This key can't be used on this character: show the Connect box again.
				forgetCharacterKey();
				panel.setKeyError(message);
			}
			panel.setStatus(message, ColorScheme.PROGRESS_ERROR_COLOR);
			// Tell the player in chat once per distinct problem, not on every kill.
			String code = result.getCode() != null ? result.getCode() : String.valueOf(result.getHttpStatus());
			if (!code.equals(lastErrorCode))
			{
				lastErrorCode = code;
				chat("Gemstone Crab KC: " + message);
			}
		}
	}

	private void chat(String text)
	{
		if (!config.chatMessages())
		{
			return;
		}
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(text)
				.build())
			.build());
	}

	/**
	 * Maps the in-game account type to the website's three leaderboards.
	 * Group ironmen go on the Ironman board, hardcore group ironmen on Hardcore.
	 */
	static String accountType(int ironmanVarbit)
	{
		switch (ironmanVarbit)
		{
			case 1: // ironman
			case 2: // ultimate ironman
			case 4: // group ironman
			case 6: // unranked group ironman
				return "ironman";
			case 3: // hardcore ironman
			case 5: // hardcore group ironman
				return "hardcore";
			default:
				return "main";
		}
	}

	private boolean isLocalPlayerListed(String names)
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return false;
		}
		return namesContain(names, local.getName());
	}

	/** True when {@code playerName} is one of the names in "A, B, & C". */
	static boolean namesContain(String names, String playerName)
	{
		String target = Text.standardize(playerName);
		for (String name : names.split("[,&]"))
		{
			if (Text.standardize(name).equals(target))
			{
				return true;
			}
		}
		return false;
	}

	private void loadCounts()
	{
		kills = readInt(GemstoneCrabKcConfig.GROUP, KEY_KILLS);
		top3 = readInt(GemstoneCrabKcConfig.GROUP, KEY_TOP3);
		refreshFromOtherPlugin();
		if (panel != null)
		{
			panel.setCounts(kills, top3);
		}
	}

	/** Adopt the Gemstone Crab plugin's counts when they are higher than ours. */
	/**
	 * True when the player also uses the "Gemstone Crab" plugin on this
	 * character. Its KC is what players see in-game, so ours mirrors it
	 * exactly instead of counting separately.
	 */
	private boolean mirrorsOtherPlugin()
	{
		return config.importGemstoneCrabPlugin()
			&& configManager.getRSProfileKey() != null
			&& readInt(OTHER_PLUGIN_GROUP, OTHER_KEY_KILLS) > 0;
	}

	/** When mirroring, copy the Gemstone Crab plugin's numbers exactly. */
	private void refreshFromOtherPlugin()
	{
		if (!mirrorsOtherPlugin())
		{
			return;
		}
		int otherKills = readInt(OTHER_PLUGIN_GROUP, OTHER_KEY_KILLS);
		int otherTop3 = readInt(OTHER_PLUGIN_GROUP, OTHER_KEY_TOP3);
		if (otherKills != kills || otherTop3 != top3)
		{
			kills = otherKills;
			top3 = otherTop3;
			saveCounts();
		}
	}

	private int readInt(String group, String key)
	{
		Integer value = configManager.getRSProfileConfiguration(group, key, Integer.class);
		return value == null || value < 0 ? 0 : value;
	}

	private void saveCounts()
	{
		configManager.setRSProfileConfiguration(GemstoneCrabKcConfig.GROUP, KEY_KILLS, kills);
		configManager.setRSProfileConfiguration(GemstoneCrabKcConfig.GROUP, KEY_TOP3, top3);
		if (panel != null)
		{
			panel.setCounts(kills, top3);
		}
	}

	private void resetFight()
	{
		crabPresent = false;
		fightStartAt = 0;
		lastCombatXpAt = 0;
		killCountedThisFight = false;
	}

	private void resetSessionState()
	{
		lastSent = null;
		lastAnnouncedKills = null;
		lastErrorCode = null;
		lastKillAt = 0;
		if (panel != null)
		{
			panel.setRank(null);
		}
	}

	private void openLeaderboard()
	{
		HttpUrl base = SyncClient.baseUrl(config.serverUrl());
		LinkBrowser.browse(base != null ? base.toString() : GemstoneCrabKcConfig.DEFAULT_SERVER_URL);
	}

	private void openSyncPage()
	{
		HttpUrl base = SyncClient.baseUrl(config.serverUrl());
		LinkBrowser.browse(base != null
			? base.newBuilder().addPathSegment("sync").build().toString()
			: GemstoneCrabKcConfig.DEFAULT_SERVER_URL + "/sync");
	}

	@Provides
	GemstoneCrabKcConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GemstoneCrabKcConfig.class);
	}
}
