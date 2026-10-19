# Changelog

## 0.4.0 — Beta, phase four

- Added heard fine judgments, voluntary wallet/emerald payments and President-authorized bail, including payment by friends and queued releases for offline defendants. Unpaid fines never create another prison sentence.
- Added current-President judicial releases and reasoned revocation of unpaid charges, without modifying earned work credit.
- Added original Judicial Register furniture, crafting, creative entry and public final decisions/custody history. Private testimony and unresolved accusations remain restricted. New criminal records preserve the original sentence duration.
- Added physical fine, bail, payment, public record and authority interfaces with confirmations, pagination, small-screen scrolling and Portuguese/English translations.
- Added durable player debit receipts, death-persistent markers, safe retry/cancellation and authoritative LAN-host level.dat validation. Offline players are never freshly debited.
- Added backed court escrow and optional Crown Ledger 0.2.2 income receipts. Unavailable or full treasuries retain funded payments for safe later forwarding; receipt replays cannot create a second income.
- Added optional nearby held-document authentication through Realm Identity and post-commit Realm Notifications.
- Prevented late server updates from reopening a physical menu after the player closes it.
- Added strict format-four migration from all prior prison/court/work saves, financial consistency checks, native failure/recovery tests and actual-client payment/LAN persistence validation.

## 0.3.0 — Beta, phase three

- Added seven original work blocks: Work Board, Delivery Box, Mining Post, Agricultural Post, Workshop Table, Maintenance Post and Warehouse Post, plus a server-issued Sealed Work Pouch.
- Added optional mining, farming, carpentry, maintenance, smithing and transport tasks, with administrator-configured objectives, rewards and a default 50% total sentence-reduction cap.
- Added surveyed prison work zones, finite commissioned world resources, mature-crop and tool checks, safe manual resource renewal and single-use work provenance.
- Added timed workshop and brush actions that require the prisoner to remain nearby. Normal online sentence time continues while working.
- Added sealed deliveries bound to the correct task, prisoner, sentence and zone. External inventory, creative items and copied bags cannot create work progress.
- Committed work completion, actual reduction credits and custody changes atomically, including automatic release when work completes the remaining sentence.
- Added work menus, progress displays, recent records, recipes and Portuguese/English translations. Existing prison and elected-court features are preserved.
- Added strict world-format migration from phases one and two, resource/credit persistence, rollback and replay checks, plus actual-client task and furniture validation.

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
