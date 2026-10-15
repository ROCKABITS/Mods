# Crown Ledger integration — Contracts API 2

`pt.contracts.api.ContractsApi` is the server-only integration boundary introduced in Contracts & Bounties 0.3.0. Crown Ledger is the separate treasury mod (`kingdomtreasury`). Contracts 0.3.1 adds API 2 funding transfers; Crown Ledger supplies its physical chest, governance controls and payroll.

## Registering policy and authority

Register exactly one `ContractsApi.TreasuryProvider` during common mod setup. Its two methods are called with the current server/player:

- `Policy policy(MinecraftServer server)` reads that world's saved government rules.
- `boolean mayManage(ServerPlayer player)` checks the current authorization for every management request. With Realm Council, require the mandate's UUID to match, an active term and a completed oath; do not cache a governor across mandates. A standalone fallback may use a designated manager.

`Policy(minimumReputation, taxBasisPoints, reputationPerPublicContract, dailyReputationLimit)` validates reputations/limits in 0–100 and tax in 0–10000 basis points. **500 basis points = 5%**. These are API representation limits; the Treasury should apply the administrator's configured governance limits before saving policy.

The disconnected default is `(0, 0, 1, 3)`: private publication is available with zero tax and no reputation threshold. Public methods still require a registered manager provider. If `kingdomtreasury` is loaded but fails to register, or a registered provider fails to supply policy, new publication fails closed. Existing contracts retain their saved tax and can still be completed; individual claims and refunds remain available.

Registration is an in-process server capability. There are no client packets, player commands or GUI fields that can register a provider or set government rules. Conflicting registrations are rejected. `unregisterTreasury(provider)` requires the identical registered object and supports lifecycle cleanup and isolated tests.

## Funds and materials

The reserved account is `Ledger.KINGDOM_ACCOUNT` (`00000000-0000-0000-0000-000000000001`). Its balances and delivered materials persist in the same contract ledger as escrow and player claims. Public contracts record the issuing player separately; the issuer does not own the public account.

| Method | Behavior |
| --- | --- |
| `depositKingdom(donor, amount, currency)` | Debits the donor's real inventory or Chronicles wallet and credits the kingdom. Currency must match the active economy. |
| `kingdomBalance(server, currency)` | Reads the stored public balance for `EMERALD` or `CHRONICLES`. |
| `kingdomDeliveries(server)` | Reads materials awaiting authorized public collection. |
| `publishPublic(manager, title, item, quantity, reward)` | Rechecks authority, snapshots policy and reserves the gross reward from existing public credit. |
| `cancelPublic(manager, contractId)` | Rechecks authority and refunds the full gross escrow to the public account. |
| `collectKingdom(manager)` | Rechecks authority, inserts as many public materials as fit and retains the rest. |

All calls must run on the main server thread. Management calls reject spectators and unauthorized users. The Treasury's own menu must verify its physical chest session before invoking the API; these trusted server calls do not require a Contracts noticeboard session.

The API does not mint balances or withdraw public funds to a manager's personal wallet. Crown Ledger holds its own salary reserves and claims; only contract funding and settlement use this account. The Treasury should use this account as the authoritative contract budget and reconcile any separate standalone account explicitly, rather than keeping two independently spendable copies.

## Settlement and progress

Completing a contract distributes its saved gross reward into net worker credit and kingdom tax credit, then stores delivered materials for the contract owner. The tax is rounded down to the smallest currency unit; fees never exceed gross escrow. Collecting credits does not tax them again.

Public materials and refunds remain with the kingdom after a change of governor. Its successor can manage old public contracts if the provider authorizes that player. Removing the Treasury does not change already published tax or ownership; public balances remain stored until an authorized integration returns.

Only public jobs completed by a player other than their issuer queue RP progress. Reputation and mission awards are tracked separately: reputation has a persistent UTC daily cap, while the mission counter continues to increase. The profile adapter preserves identity, money, occupation and medals. Missing profiles or synchronization errors leave the award pending for a later board open/refresh.

The contract ledger uses saved format 2 and migrates format 1 with private ownership, zero tax and no retroactive progress. This gives runtime transaction checks and normal Minecraft persistence; it does not provide an atomic disk transaction across player data and world data during abrupt process/power failure.

## Verification boundary

Native tests register an explicitly identified test provider to validate funding, policy changes, permission changes, taxation, progress queues and persistence. The separate Crown Ledger CI also runs the actual release JARs of Contracts and Realm Council together, including succession, escrow, refunds and tax conservation.

The supplied Chronicles RP 0.5.2 record API was checked against its original JAR. Full runtime verification with Chronicles, Origins and Jupiter installed remains separate from standalone contract tests.

### Crown Ledger transfers (API 2, Contracts 0.3.1+)

`treasuryCredit(server, provider, currency, amount)` and `treasuryDebit(...)` move exact, positive amounts between the registered treasury and the contracts settlement account. Both require the identical registered provider and the main server thread. Debit rejects insufficient funds without changing the ledger. The treasury must preflight destination capacity and debit its own balance when funding escrow; these methods are trusted integration calls and must never be exposed directly to client packets. Crown Ledger sweeps taxes and refunds into its public account; reserved contracts remain in Contracts escrow. Normal Minecraft world backups must include both SavedData files.
