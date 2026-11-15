# Realm Gym — Fabric 1.20.1 upload

Upload a separate file to the existing **Realm Gym** CurseForge project.

- **Display name:** Realm Gym 0.7.0 Beta — Fabric 1.20.1
- **Upload:** [realm-gym-0.7.0-fabric-1.20.1.jar](../mod-pronto/realm-gym-0.7.0-fabric-1.20.1.jar)
- **Minecraft:** 1.20.1
- **Mod loader:** Fabric
- **Release type:** Beta
- **Environment:** Client and server
- **Java:** 17
- **Minimum Fabric Loader:** 0.16.14
- **Required dependency:** Fabric API 0.92.12+1.20.1
- **License:** All Rights Reserved

## Changelog — 0.7.0

- Added a Fabric edition for Minecraft 1.20.1 with all eight machines, Java 17 support and dedicated networking adapters.
- Added optional physical condition for each of the six training attributes. The system is disabled by default.
- By default, condition remains at 100% for seven full days without maintenance, then loses five percentage points per additional complete day, down to 60%.
- Condition scales Realm Gym's earned attribute bonuses. Lifetime XP, levels, unlocked loads, workout plans and personal records remain saved.
- Completing two valid sets of at least eight repetitions for an attribute restores 20 condition points, once per attribute per training day, and renews its grace period. Maintenance still counts after reaching the daily XP cap.
- Added condition, effective bonuses and maintenance details to the athlete profile, with additional information in attribute tooltips.
- Added `/gym conditioning on`, `/gym conditioning off` and `/gym conditioning status`. Enabling or disabling requires operator permission level 2; everyone can check the status.
- Command changes apply immediately to the whole server and are saved automatically across restarts. No file editing or `/gym reload` is needed.
- Added console support and the `/fitness conditioning` alias. Existing configuration values are preserved when toggling the system.
- Disabling condition restores full earned bonuses and pauses saved condition. Repeating `on` while already enabled does not reset condition or renew the grace period.
- Added configurable grace period, loss rate, minimum condition, recovery and maintenance requirements. Offline time can be included or excluded.
- Existing profiles receive full condition and a complete grace period when the feature is first enabled, with no inactivity charged from before activation.

Available for NeoForge, Fabric and Forge on Minecraft 1.21.1, and Fabric and Forge on Minecraft 1.20.1. Fabric 1.20.1 and all 1.21.1 editions include eight machines; Forge 1.20.1 includes six. Install the same edition and version on the server and every client. Fabric also requires Fabric API.
