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
