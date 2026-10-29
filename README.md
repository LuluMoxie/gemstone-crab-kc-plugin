# Gemstone Crab KC

A RuneLite plugin that tracks your Gemstone Crab kill count and, if you turn it on, keeps your entry on the [gemstonecrabkc.com](https://www.gemstonecrabkc.com) leaderboard up to date automatically. No screenshots needed.

## What it does

- Counts a kill each time the Gemstone Crab burrows after you fought it for more than 5 minutes. This is exactly the rule the Gemstone Crab plugin uses, so both plugins show the same KC.
- Counts your "top crab crusher" (top 3) finishes.
- If you also use the **Gemstone Crab** plugin, it shows and syncs exactly that plugin's kill count, so the numbers always match what you see in-game.
- Shows your KC, top-3 finishes and leaderboard rank in a side panel.
- With syncing turned on, sends your KC to the leaderboard a few seconds after each kill and when you log in.

## Setting up sync

1. Log in at [gemstonecrabkc.com/sync](https://www.gemstonecrabkc.com/sync) and click **Get my sync key**, then **Copy**.
2. In RuneLite, log in to the character you want to sync and click the **crab icon** in the right-hand sidebar.
3. **Paste your sync key into the box and click Connect.**

Each key belongs to one character: it locks to the first character that syncs with it. To sync another character, make another key on the website and paste it in while logged in as that character. The plugin remembers each character's key separately.

## What is sent

Syncing is **off by default**. When it's on, the plugin sends only your own:

- RuneScape name
- account type (main, ironman or hardcore)
- Gemstone Crab kill count and top-3 finishes
- RuneLite account hash (an anonymous id, so one in-game account can't be claimed by two website accounts)
- plugin version

along with your sync key. As with any web request, the server sees your IP address. Nothing about other players is sent.

## Building

Requires Java 11+. From this folder:

```
./gradlew run
```

This opens a development RuneLite client with the plugin loaded. To log in with a Jagex account, follow [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

To test against a local copy of the website, set **Advanced → Server URL** to `http://localhost:3000`.
