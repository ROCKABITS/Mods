# Realm Studios — Architecture and integrations

This document describes the **published project structure** at a high level. It is an orientation guide, not a replacement for each mod's installation guide, API documentation or validation report.

## Platform

- Minecraft Java **1.21.1**
- NeoForge **21.1** (the individual projects specify their supported minimum/build)
- Java **21**

## System areas

| Area | Main project | Responsibility |
| --- | --- | --- |
| Character and roleplay | Realm Origins | Character creation, roleplay identity, public character name, wallet and reputation. |
| Governance | Realm Council | Elections, terms, oath of office and public decisions. |
| Public finances | Realm Treasury | Kingdom treasury, taxes, salaries, public contracts, loans and approved spending. |
| Prices and services | Realm Economy | Economy rules, prices, taxes and paid services; includes the MineColonies economy integration. |
| Contracts and progression | Contracts & Bounties, Realm Progression | Player/public contracts, daily quests and configurable progression unlocks. |
| Commerce and entertainment | Realm Markets, Realm Casino | Player stalls, stock, sales, casino games and multiplayer tables. |
| Justice and social systems | Laws & Justice, Realm Marriage | Prison and court workflows, fines/bail, marriage ceremonies and shared-account options. |
| Identity and presentation | Realm Identity, Realm Titles, Realm Notifications, Realm Rewards | Physical identity documents, public titles, event notifications and advancement rewards. |
| Modpack presentation | Season 8 | Custom main menu, loading presentation, title and window icon. This is client-side only. |

## How integrations fit together

The ecosystem is modular. A mod should not be assumed to require every other Realm mod. Integrations are activated only when the relevant companion mod and supported version are present.

### Character and money

- **Realm Origins** is the roleplay character and wallet foundation used by several optional integrations.
- **Realm Economy** can use the Realm Origins wallet and Realm Treasury for the shared economy and tax flows.
- **Realm Treasury** can also operate in its documented standalone mode; consult its guide for the exact behaviour with and without Realm Origins.
- **Realm Rewards**, **Realm Markets**, **Contracts & Bounties**, **Realm Casino** and **Realm Marriage** document their own wallet or payment integrations. Their guides explain which features remain available without a companion mod.

### Governance and public authority

- **Realm Council** manages elections, mandates and the oath of office.
- Other systems can consult the active sworn governor when deciding who may manage public settings or treasury operations.
- A Realm Council mandate does not grant Minecraft operator permissions. Administrative access remains separate and is checked according to each mod's guide.

### Shared presentation and identity

- **Realm Identity** provides the physical character document.
- **Realm Titles** maintains public appointments and titles, which can be displayed through Realm Identity.
- **Realm Notifications** provides an optional in-game notification layer for supported events. Where the optional integration is absent, individual mods may retain their own chat or interface feedback.
- **Realm Rewards** pays supported Minecraft advancements and mod advancements according to its configuration.

## Known mandatory external dependencies

These are the dependencies explicitly identified in the current repository overview and mod guides. Always check the individual guide before downloading, because supported versions can change.

| Project | Dependency | Status |
| --- | --- | --- |
| Realm Origins 0.5.7 | Origins (NeoForge) 0.3.2 | Required |
| Realm Origins 0.5.7 | Jupiter for NeoForge 1.21.1, 2.3.7 | Required |
| Realm Origins 0.5.7 | Origins: Classes (NeoForge) 0.3 | Optional |
| Realm Progression 0.1.0 | Realm Treasury 0.4.0 | Required |
| Season 8 0.1.0 | Additional mod dependencies | None listed; client-side only |

For all other projects, use the dependency and compatibility table in that mod's own README. Optional integrations are not the same as mandatory dependencies.

## Data and multiplayer principles

The project documentation describes several recurring design rules:

- Important multiplayer actions are validated by the server, rather than trusting a client-side menu.
- Financial operations use persistent records/receipts and recovery procedures where documented by the relevant mod.
- Player-owned data is generally associated with player UUIDs, not only visible usernames.
- A mod's validation report describes the exact test scope and known limitations. A passing test suite should not be interpreted as proof that every possible modpack combination has been tested.

## Documentation map

- [Installation and updates](INSTALLATION.md)
- [All mods and current downloads](README.md)
- [Realm Origins](realm-origins/README.md)
- [Realm Economy](realm-economy/README.md)
- [Realm Treasury](crown-ledger/README.md) — the historical folder name is retained for compatibility; the published mod is Realm Treasury.
- [Realm Council](realm-council/README.md)
- [Realm Progression](realm-progression/README.md)
- [Contracts & Bounties](contracts-bounties/README.md)
- [Realm Markets](realm-markets/README.md)
- [Realm Casino](realm-casino/README.md)
- [Laws & Justice](laws-justice/README.md)
- [Realm Marriage](realm-marriage/README.md)
- [Realm Identity](realm-identity/README.md)
- [Realm Titles](realm-titles/README.md)
- [Realm Notifications](realm-notifications/README.md)
- [Realm Rewards](realm-rewards/README.md)
- [Season 8](season-eight/README.md)

## Accuracy and maintenance

This page is intentionally high-level. The individual mod README is the source of truth for installation, exact dependency versions, configuration, commands and compatibility. The corresponding `CHANGELOG.md` records release changes, while `VALIDACAO*.md` records test evidence and limitations.
