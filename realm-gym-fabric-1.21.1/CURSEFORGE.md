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

## Changelog

Version 0.7.1 adds in-game physical condition controls: `/gym conditioning on`, `/gym conditioning off` and `/gym conditioning status`. Operators can enable or disable it immediately; the choice is saved automatically and survives server restarts. Status is available to all players. Console and `/fitness conditioning` commands are supported. Existing configuration values, lifetime XP, unlocked weights and records are preserved.


Version 0.7.1 adds optional per-attribute physical condition, disabled by default. After a configurable grace period, inactivity gradually reduces Realm Gym bonuses. Valid maintenance sets restore condition once per attribute per training day. Lifetime XP, equipment unlocks, workout plans and personal records remain saved. Includes an offline-time option, RPG condition indicators, legacy-world migration and client/server persistence checks. Enable with `/gym conditioning on` and disable with `/gym conditioning off`. Update the server and clients together.


Fabric 1.21.1 edition of Realm Gym 0.7.1.

- All eight functional gym machines, including plate-loaded leg press and cable rope triceps.
- Right-click mounting, articulated character animations, moving equipment and compact exercise HUD.
- Eight server-scored minigames, progression, fatigue, daily training goals, custom workout routines and physical leaderboard boards.
- Permanent melee damage, movement speed, attack speed, fall protection, swimming speed and jump-height bonuses.
- Shared domain rules and original equipment assets with the NeoForge edition.
- Fabric lifecycle, networking, input handling, model loading and physical effects.
- English and Portuguese interface; `/gym` commands and the `/fitness` alias.

Requires Minecraft 1.21.1, Java 21, Fabric Loader 0.16.14+ and Fabric API 0.116.18+1.21.1. Install matching Realm Gym and Fabric API versions on the server and clients.
