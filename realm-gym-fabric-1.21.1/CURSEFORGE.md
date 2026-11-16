# Realm Gym — ficheiro Fabric 1.21.1

Publicar como um novo ficheiro no mesmo projeto **Realm Gym**. Associar a dependência Fabric API a este ficheiro Fabric.

- **Display name:** Realm Gym 0.7.1 Beta — Fabric 1.21.1
- **Upload:** [realm-gym-0.7.1-fabric-1.21.1.jar](../mod-pronto/realm-gym-0.7.1-fabric-1.21.1.jar)
- **Minecraft:** 1.21.1
- **Mod loader:** Fabric
- **Release type:** Beta
- **Environment:** Client and server
- **Java:** 21
- **Required dependency:** Fabric API
- **Minimum Fabric Loader:** 0.16.14
- **Tested Fabric API:** 0.116.18+1.21.1

## Changelog — 0.7.1

- Added configurable real-time or Minecraft-day clocks for physical condition. Existing worlds keep real-time mode by default.
- Added `/gym conditioning days real <days>` and `/gym conditioning days minecraft <days>` to choose the clock and grace period directly in game. The range is 0–365 days; changing settings requires operator permission level 2.
- Added `conditioning.timeMode` (`real` or `minecraft`) alongside `graceDays` in the configuration file. Loss rate, minimum condition and recovery remain configurable.
- Condition starts at 100% and remains unchanged for the configured grace period. Each additional complete day gradually removes the configured number of condition points, down to the configured minimum.
- A real day is 24 hours. A Minecraft day is 24,000 simulation ticks, approximately 20 minutes at 20 TPS. Minecraft time advances while the world runs; sleeping and daylight commands do not skip condition time.
- Maintenance and its daily recovery limit follow the selected clock. Daily XP limits, workout history and the training calendar retain their existing real-time schedule.
- Day settings apply immediately and persist across restarts. Console commands and the `/fitness conditioning` alias are supported; `/gym conditioning status` shows the selected clock and grace period.
- Switching clocks preserves current condition, permanent XP, levels, unlocked loads, plans and records, and starts a fresh grace period. Repeating an unchanged setting does not restart it.
- Updated the athlete profile to show the selected time mode and remaining Minecraft days where applicable.
- Added clock migration, tick-boundary, paused-world, offline, recovery-window, command-permission and restart-persistence coverage across all five editions.

Available for NeoForge, Fabric and Forge on Minecraft 1.21.1, and Fabric and Forge on Minecraft 1.20.1. Fabric 1.20.1 and all 1.21.1 editions include eight machines; Forge 1.20.1 includes six. Install matching versions on the server and every client. Fabric also requires Fabric API.
