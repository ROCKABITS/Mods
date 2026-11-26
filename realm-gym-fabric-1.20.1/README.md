# Realm Gym 0.7.3 Beta — Fabric 1.20.1

Eight animated workout machines, mounted minigames, permanent training levels, workout plans, an RPG athlete profile and physical leaderboard boards for **Minecraft 1.20.1, Fabric Loader 0.16.14+ and Java 17**.

[Download JAR](../mod-pronto/realm-gym-0.7.3-fabric-1.20.1.jar) · [ZIP](../mod-pronto/realm-gym-0.7.3-fabric-1.20.1.zip) · [Gameplay gallery](../media/realm-gym/0.7.3-fabric-1.20.1/README.md) · [Validation](VALIDACAO.md) · [CurseForge upload](CURSEFORGE.md) · [English changelog](../realm-gym/CHANGELOG.md)

**Supino a dois.** Clica com o botão direito num banco ocupado para ser spotter. O atleta pede ajuda com **W** quando vê o aviso, e o spotter segura a barra com **Espaço**. **Shift** sai do posto. [Changelog 0.7.2](../realm-gym/CHANGELOG.md#072--bench-press-spotter).

## Installation

Install the same Realm Gym edition on the server and every client. This edition requires **Fabric API 0.92.12+1.20.1**. Use the Fabric API file for Minecraft 1.20.1. It does not require other Realm mods or animation libraries.

Keep only one Realm Gym/Realm Fitness JAR per installation. The mod retains its `realmfitness` ID, `config/realm-fitness.json` and `data/realm_fitness.dat` storage. It does not downgrade Minecraft 1.21.1 worlds or combine Fabric and Forge loaders.

## Equipment and controls

- Right-click an intact machine to mount and start its minigame immediately.
- Sneak-right-click before mounting to increase the unlocked load. Leg press uses 10 kg steps; bench press, squats and triceps use 5 kg steps.
- Press Shift to leave and cancel an unfinished set; F5 changes the camera.
- Press K, use an athlete card or run `/gym` to open the profile, records, routines, workout history and calendar.

The equipment includes bench press, treadmill, punching bag, pull-up bar, rowing machine, squat rack, leg press and cable rope triceps. The animated leg press and rope machine use the same original models, moving components and body contact targets as the 1.21.1 editions.

All eight server-scored minigames share the current domain rules. Leg press and squats share leg progression and fatigue; cable triceps and bench press share strength. Loads, preferred weights and records remain machine-specific. Six attributes have 20 permanent levels each. Lighter loads become easier as the corresponding attribute improves.

## Optional physical condition

**New in 0.7.1:** choose real-time or Minecraft-day physical condition with `/gym conditioning days real 7` or `/gym conditioning days minecraft 7`. Commands save and apply immediately. [Clock configuration guide](../realm-gym/CONDITIONING.md).

The system is disabled by default. Operators can run `/gym conditioning on` or `/gym conditioning off`; everyone can run `/gym conditioning status`. Changes apply immediately and persist across restarts. The console and `/fitness conditioning` alias work too.

[Full condition and maintenance guide](../realm-gym/CONDITIONING.md). XP, levels, unlocked loads, routines and records remain saved.

## Implementation and validation

The domain, eight-machine geometry, articulated poses, rig, HUD and translations are compiled from `../realm-gym`. The build adapts clamp and List conveniences to Java 17 and converts recipes, loot tables and tags to Minecraft 1.20.1 paths. Version-specific blocks, storage, screens, skin rendering and legacy Fabric channels stay in this project.

Minecraft 1.20.1 has no vanilla player jump-strength attribute. A synchronized Realm Gym jump multiplier scales jump velocity by the square root of the earned height bonus, while a separate swim attribute adjusts water movement. Native tests verify actual jump velocity, swimming, fall damage and physical-condition modifiers.

```sh
./gradlew build
./gradlew -PfitnessGameTests runGametest
./gradlew -PfitnessVisualTests runClient
```

The client fixture needs a graphical session. CI runs it with Xvfb, actual keyboard/mouse mixin input, all eight mounted minigames and world reopening. Test classes and templates are excluded from the remapped production JAR.
