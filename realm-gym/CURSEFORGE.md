# Realm Gym — CurseForge

- **Project name:** Realm Gym
- **Suggested slug:** `realm-gym`
- **File display name:** Realm Gym 0.7.0 Beta — NeoForge 1.21.1
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
- **Upload:** [realm-gym-0.7.0.jar](../mod-pronto/realm-gym-0.7.0.jar)

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

## Changelog — 0.7.0

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

Available for NeoForge, Fabric and Forge on Minecraft 1.21.1, and Forge on Minecraft 1.20.1. The 1.21.1 editions include eight machines; Forge 1.20.1 includes six. Install the same edition and version on the server and every client. Fabric also requires Fabric API.
