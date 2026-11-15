# Realm Gym 0.7.0 — Fabric 1.20.1 validation

The [Fabric 1.20.1 workflow](../.github/workflows/build-realm-gym-fabric-1.20.1.yml) builds the remapped production JAR and runs 128 shared JUnit tests, native Minecraft GameTests and an integrated Minecraft client. The release is published only when all stages pass.

Native scenarios cover eight machines in every orientation, placement, support, collisions, working states, drops, recipes, records, NBT persistence, mounting, safe dismounts, broken occupied equipment, aliases and operator permissions. The 1.20.1-specific jump test checks actual velocity, height scaling, non-stacking modifiers, condition and restoration of full earned bonuses. Swimming and fall protection are also exercised through actual Minecraft methods.

The production smoke server boots the remapped JAR twice with Java 17, Fabric Loader 0.16.14 and Fabric API 0.92.12+1.20.1. It checks configuration defaults, console commands, immediate persistence, reload and clean shutdown, then verifies the enabled state after restarting and disables it through the legacy alias.

The graphical scenario uses all eight minigames through the actual Fabric keyboard/mouse hooks and C2S/S2C play channels. It checks loading and moving models, mounting, load selection, Shift cancellation, routine editing, daily progress, physical condition, active bonuses, records and world reopening. Scores are produced by server rules, with no client-submitted or synthetic set results. The controlled QA baseline uses 90 XP per attribute.

The packager validates Fabric metadata, required mixin refmaps, Java 17 bytecode, pack format 15, all eight machines, translations, geometry and 1.20.1 recipe/loot/tag paths. It rejects test classes, loader or Minecraft classes embedded in the JAR, and 1.21 resource paths. Download metadata records the source commit, tested CI run, counts and SHA-256 hashes.

Remote authenticated multiplayer and combinations with third-party attribute or rendering mods are outside these automated scenarios.
