# Realm Treasury 0.4.0 — Royal Credit Counter

- Adds an original craftable 3D credit counter with 66 model elements, custom walnut, brass, green leather, ledger, parchment, quill, inkwell, coin and wax artwork.
- Adds public loan applications, sworn-President approval, fixed interest, administrator bounds and explicit borrower signatures in a parchment interface with an animated royal seal.
- Reserves existing public funds when an offer is approved; the principal reaches the existing Realm Origins wallet only after signing. Supports physical emeralds without that wallet.
- Adds exact installment schedules, manual payments, early full settlement, overdue indicators, contract history and automatic return of unsigned expired or cancelled reservations.
- Enforces one open application or debt per player, current Council authority, personal signing/payment, physical desk sessions and one-use action nonces. Existing terms and currencies remain frozen.
- Adds durable starting-wallet saves, treasury intents and wallet receipts in both native integrated-host restart files. Interrupted principal transfers and repayments recover without a second debit or credit; ambiguous state retains the receipt and pauses public financial operations.
- Preserves loans through public tax receipts, votes, treasury saves, broken desks and world reopening. Adds English, European Portuguese and Brazilian Portuguese text.
- Migrates treasury data to format 9 while reading formats 1–8. Network protocol is now 6; update the server and all clients together and retain a complete pre-upgrade world backup.

# Realm Treasury 0.3.1 — Free Public Votes

- Made free public voting the default proposal option, with separate custom title and multiline description fields.
- Added full, scrollable proposal descriptions to ballots and archived decision readers, plus description previews in the on-screen notice.
- Preserved title and description through votes, external income receipts, final results and world reopening.
- Kept kingdom benefits as an additional option; general votes remain advisory and never charge treasury funds.
- Added title/description validation, larger bounded packet codecs and migration tests for 0.3.0 ballots without descriptions.
- Updated treasury storage to format 8 and network protocol to 5. Update both clients and server to 0.3.1.

# Realm Treasury 0.3.0 — Public Votes and Kingdom Benefits

- Renamed Crown Ledger to Realm Treasury, preserving the kingdomtreasury mod ID, existing blocks, accounts, policies, contracts and public APIs.
- Added President-announced secret yes/no votes, opened from the physical treasury chest, with a non-blocking HUD and rebindable F8 ballot shortcut.
- Added one mutable vote per eligible online citizen, participation quorum, strict-majority approval, tie rejection, cooldown and public aggregate decision history.
- Added treasury-funded Harvest, Public Works, Feast and Knowledge benefits, with administrator-defined prices, duration, strengths and concurrency limits.
- Reserved costs before announcement; rejected, inquorate or emergency-cancelled proposals return their reserved funds exactly once.
- Added durable atomic ballots and financial state, real-time expiry, restart recovery and preservation of votes during external tax receipts.
- Added medieval parchment interfaces and English/Portuguese translations.
- Migrated treasury storage to format 7 and network protocol to 4. Update both clients and server; replace the old Crown Ledger JAR.

# Crown Ledger 0.2.7 — Wedding Fees

- Added CrownMarriageApi v1 for wedding fees already reserved by Realm Marriage.
- Reused payload-bound, durable treasury receipts to recover interrupted credits without duplicate income.
- Added readable English and Portuguese marriage-fee history labels.
- Preserved treasury format 6 and existing balances, taxes, salaries and integrations.

# Crown Ledger 0.2.6 — Daily Quest Tax

- Added CrownDailyApi v1 for the already-reserved public share of Contracts & Bounties 0.4.0 daily quest rewards.
- Reused payload-bound, idempotent and durably persisted CrownIncomeApi receipts: interrupted transfers remain recoverable without duplicate treasury income.
- Classified daily quest tax as public tax income and added a readable English/Portuguese history label.
- Preserved treasury format 6, existing balances, fiscal policies, salaries, contracts, markets, casino and judicial/economic integrations.
- Validated native existing regressions and actual cross-mod wallet/treasury conservation, interrupted-write recovery and integrated LAN-owner world reopen.

# Crown Ledger 0.2.4 — Beta

- Added CrownVillagerApi v1 for already-funded public tax from Realm Villager Economy purchases.
- Added zero-value, idempotent fiscal approval receipts carrying the approving player's UUID, percentage and original approval date.
- Added distinct public history categories for Villager purchase-tax income and policy approvals, including the author's RP name while online.
- Preserved CrownIncomeApi v1 and CrownCasinoApi v1 compatibility, existing market/contract rules, salaries and judicial income.
- Added treasury format 5, reading formats 1–4 and preserving all balances and previous receipts.
- Extended receipt validation and actual cross-mod purchase, interrupted-write, replay, inventory and integrated-host tests.

# Crown Ledger 0.2.3 — Beta

- Added an independent governor-configured casino profit tax (0–100%, default 0%) for Realm Casino 0.3.0.
- Added CrownCasinoApi v1 for funded owner-profit transfers, retaining CrownIncomeApi v1 compatibility for judicial payments.
- Added idempotent, payload-bound casino receipts, atomic treasury persistence and a distinct public casino-tax history category.
- Added casino tax to the physical Crown Chest policy controls and public overview in English and Portuguese.
- Added treasury format 4 with migration from formats 1–3, preserving balances, existing policy, judicial receipts and salaries. Previous worlds start with zero casino tax.
- Extended governor/succession, migration, native transfer failure/retry and real-client menu validation. Update server and clients together (network protocol 3).

# Crown Ledger 0.2.2 — Beta

- Added the server-only CrownIncomeApi v1 for already-funded judicial payments from Laws & Justice 0.4.0.
- Added persistent, payload-bound transaction receipts. Retrying a receipt confirms the actual saved file and never credits the same transfer twice.
- Committed income and receipt together using the ordered NeoForge save worker, atomic replacement and file synchronization. Write failures preserve the caller’s funded escrow for later retry.
- Added judicial income to public treasury history. Confirmed income can fund pending salary cycles under the existing policy.
- Added treasury format 3 with automatic migration from formats 1 and 2, preserving balances, policy, payroll and activity.
- Added real compressed-file, queued-save, failure/recovery, migration and replay tests, plus joint release validation with Laws & Justice and the actual Chronicles LAN-host wallet.

# Crown Ledger 0.2.1 — Beta

- Optional Realm Notifications integration for salary availability, actual salary collection, donations and fixed-tax payments.
- Online governors receive receipts for treasury donations and transferred contract or market income.
- Funded salary notices explicitly direct players to the Crown Chest; only a completed collection is described as a payment.
- Existing financial rules and world-data version 2 remain unchanged. Notification failures cannot undo or repeat a treasury transfer.

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
