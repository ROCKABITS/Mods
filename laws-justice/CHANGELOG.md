# Changelog

## 0.1.0 — Beta, phase one

- Added player-built prison registration, bounded prison areas, cells, capacities and safe arrival/release points. No prison buildings are generated.
- Added four original medieval blocks: a two-block cell door, prison bars, a reinforced auxiliary control lock and a prison control ledger, plus a three-dimensional surveyor tool.
- Added physical administrator menus for prisons, cells, online-player sentencing, emergency release and recent custody history, with English and Portuguese translations.
- Added a private prisoner status screen showing the original sentence, online time served, reductions and remaining time. Work reductions remain zero in this phase.
- Sentences advance only through online server ticks. Disconnecting pauses the clock; death, respawn, teleportation and dimension changes preserve custody.
- Added automatic release and durable queued releases for offline players, without changing game modes or copying custody into items.
- Added atomic world-file checkpoints, strict schema validation, cell occupancy checks, physical sessions, request replay protection and audited decisions.
- Added optional Chronicles RP display names and Realm Notifications messages. Phase-one decisions require administrator permission; President authority, trials, warrants, handcuffs, work zones, fines and bail follow the saved roadmap.
