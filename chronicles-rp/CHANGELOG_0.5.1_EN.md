# Chronicles RP 0.5.1 — Origins compatibility fix

- Fixed the Elytrian claustrophobia power definition bundled with Origins (NeoForge) 0.3.2. Weakness and Slowness now use the effect format accepted by that release.
- The correction is included in Chronicles RP and loads automatically; no manual editing of Origins is required.
- Added native regression coverage for power loading and for the low-ceiling condition applying both effects.
- Keeps the Origins integration introduced in 0.5.0, including optional Origins: Classes, datapack lineages, the character book and preserved RP/economy data.
- Dependency versions and network protocol 6 are unchanged: Origins 0.3.2 and Jupiter 2.3.7 are required; Origins: Classes 0.3 is optional.

Minecraft Java 1.21.1 · NeoForge 21.1.256 · Java 21.

Replace the previous Chronicles RP JAR with `cronicasrp-0.5.1.jar` on the server and clients. Keep the existing world and configuration files.
