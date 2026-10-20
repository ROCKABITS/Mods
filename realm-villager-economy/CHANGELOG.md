# Changelog

## 0.1.0 — Beta

- Added Chronicles RP wallet payments for Villager and wandering trader purchases, initially converting one emerald to €5.
- Preserved physical emerald income from material sales, Vanilla discounts and demand, secondary ingredients, item components, professions, experience, levels, stock and restocking.
- Added a medieval merchant ledger showing base price, public tax and the exact total before confirmation, with English and Portuguese translations.
- Added a sworn-President purchase-tax setting, initially 0%, with an administrator-configured ceiling initially 25% and emergency operator access.
- Added Crown Ledger 0.2.4 integration for funded purchase-tax receipts and dated, attributed policy approvals. The base payment leaves circulation; only tax reaches the treasury.
- Added exact integer cent rounding, authoritative quotes, nonce/replay protection, complete inventory preflight and rejection of stale prices or changed policy.
- Added persistent wallet/inventory intents, player receipts, integrated-host dual saves, recoverable merchant-stock revisions and durable treasury outboxes.
- Deferred inventory recipe/advancement listeners until financial commitment, preserving independent Realm Rewards payments and the live advancement tracker.
- Added domain, native independent/integrated and actual Minecraft client validation for payments, recovery, permissions, prices, sales and public history.
