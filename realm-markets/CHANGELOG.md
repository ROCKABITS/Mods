# Realm Markets 0.2.1 — Optional Realm Notifications

- Added optional HUD purchase confirmations with quantity, item and gross price.
- Notified online stall owners of each completed sale, explicitly showing net earnings retained at the stall; offline earnings remain available to collect normally.
- Added notifications for earnings actually collected into the wallet or inventory, without re-taxing or crediting a sale twice.
- Preserved readable chat feedback for clients without the optional notification channel. Existing physical stalls, stock, tax rules, network protocol and saved data remain compatible.

# Realm Markets 0.2.0 — Beta

- Added optional sales tax controlled by the governor in Crown Ledger 0.2.0 and newer.
- Added purchase previews showing gross total, market tax and seller net earnings.
- Added server verification of the current tax rate, refusing stale quotes without charging the buyer.
- Added durable, currency-separated tax reserves collected by Crown Ledger; seller earnings contain only the net sale amount.
- Applied tax only to completed sales, without taxing stock movements, earnings collection or existing earnings again.
- Preserved reserved tax until collection if the treasury transfer is unavailable or its balance limit is reached.
- Kept owner stock management and earnings collection available when a loaded Crown Ledger integration is unavailable, while suspending new purchases.
- Added version 2 market records with automatic migration preserving existing stalls, stock and earnings.
- Added joint production builds, actual cross-mod server tests and client validation for Realm Markets and Crown Ledger from the same source commit.

Update both Realm Markets and Crown Ledger to 0.2.0 or newer on the server and all clients when using them together. Back up the complete world and Chronicles profiles before updating. Downgrading after the saved-data migration requires restoring the pre-update backup. Without Crown Ledger, sales remain untaxed. Tax is rounded down on the purchase total to whole emeralds or Chronicles cents. Full Chronicles RP, Origins and Jupiter Minecraft runtime validation remains pending.

# Realm Markets 0.1.1 — Beta

- Corrected the inventory display angle of both market stalls so the front counter, sign and striped awning face the viewer.

# Realm Markets 0.1.0 — Beta

- Added two craftable medieval market stalls with crimson and sage fabric awnings, original wooden counters and 3D decorations.
- Added a physical two-block-wide, two-block-tall stall with four-direction placement, creative inventory entries and crafting recipes.
- Added eight finite-stock offers per stall, individual item prices, restocking, stock withdrawal and stall renaming.
- Added server-verified purchases using real items, buyer inventory space and available currency.
- Added sales while the owner is offline, with retained earnings collected by the owner at the stall.
- Added standalone emerald payments and optional Chronicles RP digital-currency support using integer cents.
- Added ownership checks and protected removal: stock and earnings must be collected before dismantling a stall.
- Added persistent stall records, stock and earnings, with strict validation of saved data.
- Added English and Portuguese interfaces with unit prices, quantities and purchase totals.

This first version supports ordinary, unmodified items. Named, enchanted or otherwise customized items, bundles, block-entity containers such as chests and shulker boxes, and writable or written books are not supported, even when empty. Ordinary books can be sold. Market sales have no tax. The full Chronicles RP, Origins and Jupiter Minecraft runtime remains to be validated.
