# Guide Hub Sync

RuneLite plugin for **Guide Hub** (https://hub.gamingcircle.org), an unofficial Old School RuneScape companion web app:
boss gear plans that build your best setup from the items you actually own, Slayer block/skip advice, and **loot parties** with friends.

## What it does
- **Opt-in sync** (off by default): sends your stats, worn gear, inventory, bank item names/quantities, Slayer task and loot to your
  Guide Hub account so the website can show your best setups and a drop log. It goes only to the Hub URL in the settings, nothing else.
- **Parties**: the "Guide Hub Party" side panel (and `::party` chat commands) lets you create or join a party by code; drops you receive
  while in it are pooled into the party's feed on the website (per-player totals, even split, optional Discord channel posting).

The plugin is read-only: it never clicks, moves or types, and sends nothing until you enable sync and paste your account key.

## Setup
1. Sign in at https://hub.gamingcircle.org (Continue with Discord) and open **Account** → **Generate new plugin key**.
2. In RuneLite: wrench → **Guide Hub Sync** → tick **Enable sync** and paste the key. Leave the Hub URL as it is.
3. Open your bank once in game. Battle Plans → any boss → **Mine** on the website now shows your gear.

## Privacy
What is sent and why is listed on the website's Privacy page. You can export or delete all your data from the Account page at any time.
Unofficial fan project, not affiliated with Jagex or RuneLite. Guide content adapted from the OSRS Wiki (CC BY-NC-SA 3.0).

## Building (for developers)
Standard RuneLite plugin: `./gradlew build`; run `GuideHubSyncPluginTest` for a development client. `gradle devBundle` packs a portable dev client.
