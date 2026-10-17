# Realm Rewards

## 0.1.2

- Optional Realm Notifications integration for advancement payments and rewards awaiting a wallet.
- Pending payouts use a general advancement-reward reason when they include older rewards, avoiding attributing the whole amount to the newest advancement.
- Chat remains the fallback when the notification channel is unavailable; no duplicate payment chat accompanies a HUD notification.
- Completion history, reward amounts and network protocol remain unchanged.

## 0.1.1

- Fixed Minecraft's default background blur affecting the administration menu's labels.
- Updated demonstration screenshots with real Vanilla advancement identifiers.
- Reward rules, completion history and network protocol remain unchanged.

## 0.1.0

- Automatic discovery of Vanilla and normal mod advancements.
- Configurable task, goal and challenge defaults, plus per-advancement overrides.
- Operator-only searchable administration menu with English and Portuguese localization.
- Exactly one reward per player UUID and advancement ID; revoking progress does not reset reward history.
- Existing completed advancements are registered without retroactive rewards.
- Optional Chronicles RP wallet; pending payments survive missing characters, unavailable wallets and balance limits.
- Chat payment messages, persistent completion history and strict world-data loading.
- Recipe and background advancement rewards default to zero.

Minecraft 1.21.1, NeoForge 21.1.256+, Java 21. Install on server and clients.
