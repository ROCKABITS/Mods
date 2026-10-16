# Crown Ledger 0.2.0 — Beta

- Added a governor-controlled sales-tax percentage for Realm Markets 0.2.0 and newer.
- Kept market tax independent from contract reward tax, fixed periodic tax and salaries.
- Added automatic collection of funded market tax in its original currency, normally within one server second or when refreshing the Crown Chest.
- Added market-tax income to treasury history and allowed it to fund pending salary cycles.
- Preserved reserved market tax when a treasury transfer cannot complete, including treasury balance limits.
- Added the market-tax setting to the Crown Chest interface and public policy information.
- Added version 2 treasury records with automatic migration from version 1; existing worlds start with 0% market tax.
- Added joint production builds, actual cross-mod server tests and client validation for Crown Ledger and Realm Markets from the same source commit.

Update both Crown Ledger and Realm Markets to 0.2.0 or newer on the server and all clients when using them together. Back up the complete world and Chronicles profiles before updating. Downgrading after the saved-data migration requires restoring the pre-update backup. Full Chronicles RP, Origins and Jupiter Minecraft runtime validation remains pending.

# Crown Ledger 0.1.1 — Beta

- Corrected full-minute online activity accounting across reconnects and cycle boundaries.
- Preserved pending salary eligibility without counting offline time or accumulating activity for later unfunded cycles.
- Revalidated standalone operation, Contracts and Realm Council integration, and the physical Crown Chest interface.
