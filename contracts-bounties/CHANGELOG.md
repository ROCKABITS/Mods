# Contracts & Bounties 0.4.0 — Daily Quests & RPG Celebrations

- Added five server-generated daily RNG quests per UUID: two easy, two medium and one hard, with 57 configurable templates across mining, combat, farming, fishing, exploration and physical deliveries.
- Added persistent real UTC renewal, accepted-only progress, distinct objectives, frozen rewards/incentives, expired quests and a default €300 gross daily-set cap, including the optional five-quest bonus.
- Added server-validated survival events, tracked placed/consumed blocks, natural-spawn kill attribution, mature harvesting, real rod catches, new discoveries and transactional material consumption.
- Added recoverable Chronicles wallet, reputation and mission-counter payments, pending credits, shared public-contract reputation limits and administrator-enabled emerald fallback.
- Added live, sworn Realm Council presidential tax and future category incentives, bounded by administrator policy; requires Crown Ledger 0.2.6+ for idempotent public daily-tax receipts.
- Added physical daily-quest, journal, government, administration, catalogue, audit and safe repair menus; JSON file and datapack templates support optional modpack content.
- Added original silver, gold and royal-purple parchment cinematics, animated seals, particles, reward count-up, four fanfares and a distinct 5/5 crown celebration. Queued HUD animations leave movement and camera controls available.
- Added configurable effects, reduced particles, simplified mode, volume, normal duration and rebindable Backspace skip; payments never depend on presentation. Avoided duplicate Realm Notifications completion notices.
- Preserved all three existing 3D noticeboards, recipes, private/public contracts, escrow and legacy saved data. Daily data uses a separate recoverable ledger; update client and server together, network protocol 3.
- Added unit, native dedicated-server, optional integration and actual integrated-LAN-host client validation, including interrupted treasury writes and real world close/reopen. Documented observable anti-exploit limits and untested two-authenticated-client scope.

# Contracts & Bounties 0.3.2 — Optional Realm Notifications

- Added optional medieval HUD notifications for published, completed and cancelled contracts, material deliveries and collected rewards/refunds.
- Distinguished retained noticeboard credits from money actually collected; public cancellations return reserved funds to the kingdom.
- Preserved chat feedback for clients without the optional notification channel, without duplicating HUD and chat.
- Kept the existing physical noticeboards, economy, reputation rules, network protocol and saved-data format.

# Contracts & Bounties 0.3.0

- Contract menus now require a nearby physical noticeboard. Removed remote opening through `/contracts`; operator commands still provide board items.
- Added a server integration API for the future Kingdom Treasury: configurable publication reputation requirements, locked contract taxes and publicly funded contracts.
- Added private/public contract labels, current and required reputation, and gross reward, tax and net reward previews.
- Tax is reserved within the existing reward and paid to the kingdom only upon completion. Cancellations refund the entire reserved reward.
- Public funds, refunds and delivered materials belong to the kingdom across changes of governor.
- Added Chronicles RP reputation and mission progress integration for eligible public contracts, persistent pending awards and daily reputation limits.
- Private contracts and self-issued public jobs do not award reputation.
- Migrates existing contract data without retroactive taxes or reputation rewards.

Kingdom Treasury is not included in this release. Without a registered Treasury integration, private contracts use 0% tax and no reputation requirement. All three existing noticeboards remain available.

Requires Minecraft 1.21.1, NeoForge and Java 21. Update both the client and server to 0.3.0. Back up the world before upgrading; older mod versions cannot read the new data format.
