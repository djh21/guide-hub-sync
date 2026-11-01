# Publishing the plugin on the RuneLite Plugin Hub (later, optional)

Do these in order. Do not start until the plugin compiles and you have used it in the dev client for a while.

1. **Compile and test** (`./gradlew build`, then run `GuideHubSyncPluginTest`). Fix every warning that mentions a deprecated or missing API.
2. **Set a real name**: `runelite-plugin.properties` author, description, tags. Pick a unique plugin name (check the Plugin Hub list).
3. **Put it on GitHub** as a public repository with a licence (BSD-2 is what most plugins use). Include the data attribution (OSRS Wiki, CC BY-NC-SA 3.0).
4. **Hard-code the hosted Hub URL as the default** in `GuideHubSyncConfig.hubUrl()` once you have a real https domain. Sync stays off by default.
5. **Be upfront** in the plugin description and README: what is sent, where, that it is opt-in, link to the privacy page, how to delete data.
6. **Review the rules** at https://github.com/runelite/plugin-hub (README and the plugin guidelines) and Jagex's third-party client guidelines. Reviewers care about:
   no automation or input injection, no tick-timing coaching, no sending data without consent, no obfuscated code, no unreviewed dependencies.
7. **Submit**: fork `runelite/plugin-hub`, add `plugins/<your-plugin-name>` containing `repository=<git url>` and `commit=<full commit hash>`, open a pull request.
8. Expect review comments. To release an update, open another pull request with the new commit hash.

Honest risk: the sync feature sends player data to a server you run. Expect questions about it. If the review stalls, ship the plugin without sync
(the web app still works via hiscores, Dink and ticked gear) and keep the Hub accessible through hiscores and Dink.
