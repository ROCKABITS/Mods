# Realm Identity 0.1.2 — Civil Status

- Added optional live Realm Marriage civil status and spouse display, respecting the actual viewer and the owner's privacy setting.
- Added server-verified marriage certificate inspection with Shift-use, including recorded partners, celebrant, witnesses, date, financial mode and historical status.
- Kept marriage data out of permanent identity snapshots; existing identity documents remain valid without reissue.
- Preserved document authentication, consent, proximity and owner checks, and protocol 2.

# Realm Identity 0.1.1 — Beta

- Added optional read-only Realm Titles API v1 integration. Authenticated document inspections and physical desk previews read current public honours from the server, including offline owners.
- Added complete, scrollable public title cards with name, description, permanent/expiry status and consultation time. Temporary honours expire in an open screen using server-relative time; a new consultation reflects assignments, edits and revocations.
- Kept existing documents, serial numbers, world seals, profile snapshots and item NBT unchanged. Titles require no reissue or extra materials.
- Kept the existing consent, recipient, owner, held-item, proximity and line-of-sight checks. Forged documents never receive a Titles section; optional registry failure affects only that section.
- Bumped the network protocol to 2. Update Realm Identity to 0.1.1 on the server and every client; existing world and item data remain compatible.
- Corrected the writing desk recipe category for Minecraft 1.21.1 so the survival recipe loads normally.
- Added strict public API contract tests and native checks for fresh assignments/revocation, offline expiry, unchanged identity data and optional API failure. Integration runs use the real installed Titles mod; standalone checks retain the absent-mod behavior.

# Realm Identity 0.1.0 — Beta

- Added a craftable 3D writing desk and physical identity document.
- Added issuance and replacement documents costing one paper and one ink sac.
- Added read-only Chronicles RP identity, occupation, written signature and handwritten drawing support.
- Added basic Minecraft account identity when Chronicles RP is absent.
- Added a persistent world registry, unique document references and serial numbers. Reissuing invalidates the previous document and its copies.
- Added server-verified document views that ignore forged names, lore and profile fields on the item.
- Added owner-only presentation through a recipient-bound, short-lived consent invitation, with proximity, line-of-sight and held-document checks.
- Added ownership and profile freshness indicators, including offline and outdated identity snapshots.
- Added English and Portuguese interface translations.

The complete Chronicles RP, Origins and Jupiter Minecraft runtime remains to be validated. The original Chronicles 0.5.2 record API was checked separately without redistributing its JAR.
