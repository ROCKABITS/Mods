# Realm Studios — Installation and update guide

This guide covers the general installation process for the Realm Studios mods on Minecraft Java 1.21.1 with NeoForge 21.1 and Java 21.

**Do not install every mod automatically.** Choose the features you want, then check the individual mod guide for mandatory dependencies and optional integrations.

## 1. Choose the mods

Open the [main repository README](README.md) and find the mod you want. Each entry links to the published JAR and ZIP, plus a feature summary.

- **JAR:** place the file directly in the `mods` folder.
- **ZIP:** extract it first, then place the included JAR file(s) in `mods`. Do not put the ZIP itself in the mods folder unless the specific guide explicitly says otherwise.

## 2. Check dependencies

Before installing, open the mod's own README and check:

- Required Minecraft and NeoForge versions.
- Required external mods and libraries.
- Required Realm companion mods.
- Optional integrations and the features they enable.
- Whether the mod is server-side, client-side or required on both sides.

For example, Realm Origins 0.5.7 requires Origins (NeoForge) 0.3.2 and Jupiter 2.3.7; Origins: Classes 0.3 is optional. Realm Progression 0.1.0 requires Realm Treasury 0.4.0. Season 8 is client-side only.

## 3. Install on a multiplayer server

For a normal multiplayer Realm setup:

1. Stop the server before changing any JARs.
2. Make a backup of the world and the relevant player data.
3. Install the selected game mods and their required dependencies on the server.
4. Install the same versions of those mods and dependencies on every player client.
5. Install Season 8 only on the clients that want its menu and loading-screen presentation.
6. Start the server and let it complete its first load.
7. Check the server log for missing dependencies, version mismatches or loading errors.
8. Join with a client using the same mod versions and test the features you plan to use.

Unless a mod's own guide explicitly says otherwise, treat Realm gameplay mods as required on both server and clients.

## 4. Update an existing installation

1. Read the mod's changelog and update notes before replacing files.
2. Stop the server and close all clients.
3. Back up the complete world and relevant player data. Keep the backup until the updated world has been tested.
4. Remove the old JAR for the mod being updated.
5. Install the new JAR on the server and all required clients.
6. Check whether the update changes a network protocol, save-data format or companion-mod requirement.
7. Start the server, review the log and test the relevant gameplay flows before inviting everyone back.

**Never keep two versions of the same mod in the `mods` folder.** A renamed old JAR can still be loaded and cause duplicate-mod or compatibility problems.

Some updates include data migrations. For example, the current Realm Treasury guide describes a format migration; follow its specific backup and update instructions rather than relying only on this general guide.

## 5. Common installation problems

| Symptom | What to check |
| --- | --- |
| Game refuses to start | Minecraft, NeoForge, Java and dependency versions. |
| Server starts but client cannot join | The mod list and exact versions on server and client; also check protocol changes in the mod guide. |
| Missing dependency message | Install the dependency version specified by that mod's README. |
| Duplicate mod or duplicate ID | Remove old/duplicate JARs; keep only one version of each mod. |
| Feature is unavailable | Check whether the companion integration is optional, installed on both sides where required, and enabled/configured. |
| Data looks missing after an update | Stop using the updated world and restore the matching world/player-data backup; do not delete mod data files to try to repair it. |

## 6. Where to find help

Use the relevant mod documentation first:

- [Main README and downloads](README.md)
- [Architecture and integrations](ARCHITECTURE.md)
- [Realm Origins guide](realm-origins/README.md)
- [Realm Economy guide](realm-economy/README.md)
- [Realm Treasury guide](crown-ledger/README.md)
- [Validation reports](README.md)

Each mod's validation report describes what was tested and what remains outside the tested scope. Include the Minecraft version, NeoForge version, mod versions and relevant log excerpt when reporting a problem.
