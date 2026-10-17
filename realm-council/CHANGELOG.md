# 0.2.2 — Optional Realm Notifications

- Added optional HUD announcements for election creation, campaign, voting, runoffs, public results, cancellation, oaths and government decisions.
- Added private candidacy and sealed-vote confirmations. Notifications never contain a voter’s choice or individual ballot details.
- Used a shared clock path for automatic phase changes, including changes triggered by opening a station, so announcements are sent once.
- Kept per-player chat fallback when the optional notification channel is absent, plus the existing physical stations, permissions, network protocol and saved data.

# 0.2.1 — Physical council stations

- Removed the O key binding and public `/council` and `/eleicoes` menu opening. Interact with the Notice Board, Ballot Box or Oath Desk instead.
- All menu reads, refreshes and actions require an intact matching station within eight blocks in the same dimension. Sessions expire after ten minutes.
- Rejected legacy remote-opening packets; invalid sessions receive no council snapshot and their stale menu closes.
- Kept remote operator administration commands and console access. Ordinary players can still use the physical stations without OP.
- Updated Portuguese and English instructions; retained election world-data compatibility. Update the mod on both clients and server.

# 0.2.0 — Administration in the council book

- Added an operator-only Manage page opened with O, including block delivery and election status.
- Added a two-page election form with a review step, custom office, eligibility, turnout, candidate/round limits and all phase/term durations.
- Added searchable, paginated elector management, including known offline players. Excluding an elector also withdraws their candidacy.
- Added candidate removal, phase progression, counting, cancellation and term ending through menus with confirmations.
- Server-side operator checks apply to every action. Stale election, phase and round confirmations are rejected; revoked operators lose access.
- Electoral rolls are sent only to operators. Individual ballots stay on the server.
- Added Portuguese and English translations for the management menus.
- Kept the book header and footer at a fixed height so decorative rules no longer cross buttons in short windows.
- Kept existing world-data compatibility and optional Crónicas RP integration; bumped the network protocol, so update both server and clients.

# 0.1.0

- Added a ballot box, election notice board and oath desk, with custom 3D models, textures, crafting recipes and loot.
- Added a council ledger, candidate form, candidate profiles, ballots, sealed vote confirmation and oath ceremony.
- Added a fixed electoral roll, play-time eligibility, nominations, campaigns, secret ballots, blank votes and automatic runoff rounds.
- Added configurable real-time deadlines, minimum participation, candidate limits and terms.
- Added public government decisions and election history.
- Added server-owned validation and persistent election data, including repeat-vote prevention across restarts.
- Added optional Crónicas RP identity integration; the mod also works independently.
- Added English and Portuguese localization and operator administration commands.
