# Realm Gym changelog

## 0.7.1 — Real-time and Minecraft-day conditioning

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

## 0.7.0 — Physical condition and training maintenance

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
