# Changelog

## 0.2.0 — Beta, phase two

- Added an original 3D Justice Desk with scales, gavel and case ledger, royal handcuffs and sealed warrant items, recipes and Portuguese/English translations.
- Added accusations, attributed evidence, hearings, immutable acquittals and prison verdicts, including offline defendants.
- The live elected and sworn Realm Council President is the ordinary judicial authority. Term expiry and turnover revoke authority immediately; administrator emergency decisions are recorded explicitly.
- Added physical, server-validated warrants with expiry, revocation and recoverable paper copies. One warrant authorizes one temporary arrest; copied or forged display data cannot grant another arrest.
- Added bounded handcuff detention, partial movement reduction, collision-respecting escorting and safe cleanup without changing game modes or abilities.
- Persisted court and custody decisions atomically in the same world file, with migration from phase-one saves, private party access, replay protection and physical court sessions.
- Added native authority, paper, handcuff and migration tests plus actual-client elected court, testimony, warrant and presidential turnover validation.

## 0.1.0 — Beta, phase one

- Added player-built prison registration, bounded prison areas, cells, capacities and safe arrival/release points. No prison buildings are generated.
- Added four original medieval blocks: a two-block cell door, prison bars, a reinforced auxiliary control lock and a prison control ledger, plus a three-dimensional surveyor tool.
- Added physical administrator menus for prisons, cells, online-player sentencing, emergency release and recent custody history, with English and Portuguese translations.
- Added a private prisoner status screen showing the original sentence, online time served, reductions and remaining time. Work reductions remain zero in this phase.
- Sentences advance only through online server ticks. Disconnecting pauses the clock; death, respawn, teleportation and dimension changes preserve custody.
- Added automatic release and durable queued releases for offline players, without changing game modes or copying custody into items.
- Added atomic world-file checkpoints, strict schema validation, cell occupancy checks, physical sessions, request replay protection and audited decisions.
- Added optional Chronicles RP display names and Realm Notifications messages. Phase-one decisions require administrator permission; President authority, trials, warrants, handcuffs, work zones, fines and bail follow the saved roadmap.
