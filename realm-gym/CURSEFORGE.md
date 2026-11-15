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

## Changelog

Version 0.7.1 adds in-game physical condition controls: `/gym conditioning on`, `/gym conditioning off` and `/gym conditioning status`. Operators can enable or disable it immediately; the choice is saved automatically and survives server restarts. Status is available to all players. Console and `/fitness conditioning` commands are supported. Existing configuration values, lifetime XP, unlocked weights and records are preserved.


Version 0.7.1 adds optional per-attribute physical condition, disabled by default. After a configurable grace period, inactivity gradually reduces Realm Gym bonuses. Valid maintenance sets restore condition once per attribute per training day. Lifetime XP, equipment unlocks, workout plans and personal records remain saved. Includes an offline-time option, RPG condition indicators, legacy-world migration and client/server persistence checks. Enable with `/gym conditioning on` and disable with `/gym conditioning off`. Update the server and clients together.
 — 0.7.1

- Added a plate-loaded leg press with a reclined seat, moving sled and independent left/right pressure minigame.
- Added a high-pulley rope triceps machine with moving plates, cable and rope, timed contraction and controlled return.
- Added articulated player poses with feet following the platform and hands following the rope.
- Added both machines to recipes, the catalog, workout plans, personal records and world leaderboards.
- Leg press loads range from 40–240 kg; triceps loads range from 5–55 kg, subject to progression unlocks.
- Shared existing attribute progression and fatigue without adding duplicate permanent bonuses. Preserved 0.5.1 progress and configuration.
- Requires matching 0.7.1 clients and server. This expansion targets NeoForge 1.21.1; Forge 1.20.1 remains on 0.5.1.
