# Changelog

## 0.5.0 — MineColonies colony budgets

- Adds optional MineColonies 1.1.1403 integration inside Realm Economy, with independent, persistent budgets for each colony.
- Adds the original 70-element Colony Coffer, seven pixel textures, crafting, creative search, loot and English/Portuguese interfaces.
- Provides confirmed deposits and withdrawals using the existing Realm Origins wallet. Owners, authorised native officers, members and administrators have distinct access.
- Adds five deterministic daily export orders from twenty configurable products, with real item consumption and initial €250 caps per colony and per exporter across colonies.
- Introduces construction/upgrade permits paid from colony funds. Native builders wait for approval; the same site, building type and target level reuse an already-paid permit.
- Adds visitor-specific recruitment permits while preserving native recruitment materials and colony requirements. Zero fees authorise automatically; old work orders remain valid.
- Records every budget movement and administrative policy change. Native colony deletion is blocked until the budget is empty, and reused colony IDs start fresh accounts.
- Extends the durable Economy journal to colony transfers and export deliveries, including integrated-host wallet/inventory receipts and recoverable interrupted withdrawals.
- Migrates Economy data formats 1/2 to format 3 while preserving existing trading, services, taxes, stock and escrow. Growing colony ledgers use bounded UTF-8 byte arrays.
- Validates the selected Bits and Bobs, Byzantine, ColonyRank, MapColonies and SmallColonies packs. MapColonies requires NeoForge 21.1.262+; the base integration retains 21.1.256+ support.
- Keeps all earlier enchanted-book previews and economic services. Third-party mods remain optional and are not bundled.

## 0.4.1 — Enchanted book previews

- Shows stored enchantment names and levels directly in Villager trade offers and the selected preview.
- Restores full native item tooltips when hovering an offer or its result preview, including multiple enchantments, curses, custom names, lore and mod-provided tooltip extensions.
- Keeps enchanted books identifiable in the compact trading layout. Long summaries remain available in full through the native tooltip.
- Preserves the server's original item components when previewing and buying books; prices, materials, taxes and stock are unchanged.
- Validates against the published Realm Origins 0.5.7, Realm Treasury 0.3.1 and current roleplay companions.

## 0.4.0 — First Realm Economy release

- Replaces Realm Villager Economy 0.1.0 while migrating its approved tax, merchant stock, pending treasury transfers and interrupted trade receipts.
- Adds an original 3D Economy Desk with crafting, a creative tab, a medieval interface and English/Portuguese translations.
- Lets the sworn President govern emerald reference prices and separate service prices/taxes within administrator bounds. Every change records the author, date, previous values and new values.
- Adds wallet-funded anvil repairs, enchantment combinations, renaming, enchanting and smithing. Euros replace XP payments; the original materials remain necessary.
- Adds paid potion batches with real 20-second preparation, native mixtures, 20 batches per blaze powder, durable personal escrow and one-time collection.
- Adds funded beacon periods with normal pyramids, tier requirements, public effects and expiration. Manual extensions preserve the confirmed effects and price.
- Adds server-owned administrative bounds, grouped anvil switches and configurable beacon duration. Disabled systems restore Vanilla behavior.
- Integrates Crown Ledger 0.2.5 for funded taxes and zero-value economic policy audits, and Realm Notifications for confirmed payments, ready potions and expired periods.
- Exposes a server-only permission-provider API for future Realm Admin integration, including authorization without Vanilla OP and fail-closed provider errors.
- Extends durable player receipts to enchantment seeds, brewing inventory escrow and beacon leases, including both integrated-host restart files.
- Retains physical emerald payouts for Villager material sales, Vanilla discounts, demand, professions, levels and restocking.
