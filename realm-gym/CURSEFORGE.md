# Realm Gym — CurseForge

- **Project name:** Realm Gym
- **Suggested slug:** `realm-gym`
- **File display name:** Realm Gym 0.7.1 Beta — NeoForge 1.21.1
- **Release type:** Beta
- **Minecraft:** 1.21.1
- **Loader:** NeoForge 21.1.256+
- **Java:** 21
- **Environment:** Client and server
- **License:** All Rights Reserved
- **Categories:** Adventure and RPG; Miscellaneous
- **Required third-party mods:** None
- **Source:** https://github.com/ROCKABITS/Mods/tree/main/realm-gym
- **Icon:** [realm-gym-icon.png](../media/realm-gym/branding/realm-gym-icon.png)
- **Upload:** [realm-gym-0.7.1.jar](../mod-pronto/realm-gym-0.7.1.jar)

## Summary

Build your own gym with animated workout machines, skill-based exercise minigames, permanent attribute bonuses, custom training plans and physical leaderboard boards.

## Description

**Build a gym. Train daily. Grow stronger.**

Realm Gym brings eight functional gym machines to Minecraft. Right-click to mount the equipment and train through exercise minigames with a compact HUD.

- **Bench Press:** tap Space repeatedly to overcome pressure and lift the weight.
- **Treadmill:** alternate A/D to maintain your pace and manage stamina.
- **Punching Bag:** time your punches, land perfect hits and build a streak.
- **Pull-up Bar:** control your ascent, descent, grip and balance.
- **Rowing Machine:** charge your stroke, release in the target zone and recover.
- **Squat Rack:** control your depth, pause and drive back up.
- **Leg Press:** balance the pressure of both legs with A/D, push the sled and release for a controlled return.
- **Cable Triceps:** pull with Space, time your click, hold the contraction and release under control.

Progress through six attributes with 20 permanent levels each. Leg press shares leg progression with squats; cable triceps shares strength progression with bench press. Fatigue is shared within these muscle groups, while loads and records remain machine-specific. Training can improve melee damage, movement speed, attack speed, fall protection, swimming speed and jump height. Unlock heavier loads; lighter weights become easier as your character develops.

Press **K** to open your athlete profile, view bonuses and records, create custom workout routines and track your history and calendar. Daily goals, configurable XP limits, fatigue and recovery encourage regular training. Physical record boards display leaderboards in your gym.

**Controls:** right-click to train; sneak-right-click before entering to increase the load; Shift to leave. Operators can obtain the equipment with `/gym equipment`.

Requires **Minecraft 1.21.1, NeoForge 21.1.256+ and Java 21**. Install the same version on the server and every client. Works independently; Realm Origins and other stats mods are not required.

**Previously named Realm Fitness.** Replace the old JAR with Realm Gym; use one edition at a time. Existing worlds, machines and player progress are preserved. `/fitness` remains available as an alias for `/gym`.

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
