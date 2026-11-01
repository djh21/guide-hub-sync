# Guide Hub Sync (RuneLite plugin)

A small, opt-in RuneLite plugin that sends your **bank, worn gear, inventory, stats, Slayer task and loot** to your own
Guide Hub web app, so the Battle Plans "Mine" tab can build the best setup from items you actually own.

It is read-only: it watches what RuneLite already shows you and posts it as JSON. It never clicks, moves, or types.

## What it sends (only when "Enable sync" is on)
| Data | When | Setting |
|---|---|---|
| Stats (levels, XP, combat level) | on login and after XP changes, about once a minute | always |
| Worn gear + inventory (item names, quantities) | about once a minute when they change | always |
| Bank value, and item names + quantities | when you open your bank | Send bank / Include bank item list |
| Slayer task, kills left, streak, points | when it changes (read from the built-in Slayer plugin) | Send Slayer task |
| Loot drops (source, items, GE value) | as they happen | Send loot |
| Level-ups and a session summary | as they happen / on logout | always |

It goes only to the **Hub URL you type** and nowhere else. Plain `http://` is accepted only for this computer or a home-network address (`localhost`, `192.168.x.x`, `10.x.x.x`, `172.16-31.x.x`, `*.local`);
anything on the internet must be `https://` so your key is never sent in the clear.

## Build and run it (first time, in IntelliJ)
1. Open **this folder** (`guide-hub-sync-plugin`) in IntelliJ IDEA Community. Trust the project. Let Gradle import (it downloads RuneLite; a few minutes).
2. **File > Project Structure > Project > SDK**: Add SDK > Download JDK > version **11**. OK, then reload Gradle (elephant icon > reload).
3. **Build > Build Project**. If red errors appear, copy them into the chat; the code has only been syntax-checked, never compiled.
4. Open `src/test/java/com/guidehub/sync/GuideHubSyncPluginTest.java`, click the green arrow next to `main`, and choose Run. A development RuneLite opens with the plugin loaded.
   - Jagex account login in the dev client: start your normal RuneLite once with `--insecure-write-credentials`, log in, close it, then run the test class again. Delete `~/.runelite/credentials.properties` when you're done developing.
5. In the dev client: wrench icon > **Guide Hub Sync**:
   - Enable sync: on
   - Hub URL: `http://127.0.0.1:8787` (local) or your `https://` hosted Hub
   - Account key: from the Hub's Account page (`ghk_...`)
6. Log in, **open your bank once**, wait up to a minute. In the Hub, open Battle Plans > any boss > **Mine**. It should show "Bank synced … items".

## Running it on a PC without IntelliJ (dev-client bundle)
The official RuneLite launcher ignores developer mode and sideloaded jars, so the plugin cannot simply be dropped into
`.runelite/sideloaded-plugins`. Instead build a portable development client:
1. In IntelliJ's Gradle panel: Tasks > other > **devBundle** (or `gradle devBundle`). Output: `build/devclient/`.
2. Copy the `devclient` folder to the gaming PC (it is big: RuneLite plus its libraries).
3. Jagex account: once, set the launcher's Client arguments to `--insecure-write-credentials`, start RuneLite via the Jagex launcher,
   log in, close it, remove the argument. This saves `%USERPROFILE%\.runelite\credentials.properties` (delete it when finished).
4. Double-click `run.bat`. The dev RuneLite opens with **Guide Hub Sync** in the wrench menu.
The long-term fix is publishing on the Plugin Hub, after which friends install it with one click.

## Parties (loot pooled with friends)
Open the **Guide Hub Party** side panel (orange two-heads icon on RuneLite's right toolbar): create a party, join with a code, pause,
leave, copy the code. The same works as client-side chat commands (nothing is sent to other players):
`::party create Raid Night` (prints and copies a code), `::party join ABCD-EFGH`, `::party leave`, `::party status`, `::party pause` / `::party resume`.
While you are in a party every drop you receive is also added to the party's loot feed on the web app (Parties page: members, feed,
even split, filters, optional Discord channel posting set by the party owner). A small overlay shows the party name, members and loot total
(toggle in settings).

## Known limits
- Reading the Slayer task relies on the built-in Slayer plugin's saved config keys (`taskName`, `amount`, `initialAmount`, `streak`, `points`, `taskLoc`). If a RuneLite update renames them, the task simply stops updating; nothing else breaks.
- Loot events come from RuneLite's `LootReceived` event, so the Loot Tracker plugin should be enabled.
- Requests are batched (up to 200 events) every ~30 seconds and retried on server errors; a wrong key (401) drops the batch rather than retrying forever.

## Publishing to the Plugin Hub (so friends can one-click install)
See `PLUGIN-HUB-CHECKLIST.md` in the main project. Short version: put this folder in a public GitHub repo with a licence, fill in `runelite-plugin.properties`
(author, support URL), make the hosted `https://` Hub URL the default in `GuideHubSyncConfig.hubUrl()`, then open a pull request to
`runelite/plugin-hub`. Reviewers will ask about the data upload; keep it opt-in and described honestly.
