# Realm Treasury 0.3.1 — Validation scope

[Successful GitHub Actions run](https://github.com/ROCKABITS/Mods/actions/runs/39856588511): **80 unit tests, 40 standalone native tests, 40 native integration tests**, both actual-client validations and the two-player LAN/world-reopening sequence passed. Production source: `c3eb8d8da29c3e1181e4b71fa2b7017d9ef9990a`.

This release adds free-form titles and multiline descriptions, makes free votes the default form, and preserves optional funded benefits. The release gate requires 80 successful unit tests, 40 standalone GameTests, 40 integration GameTests, the legacy client regression and the actual two-client LAN sequence. Published results are recorded in [the build report](../mod-pronto/realm-treasury-0.3.1.build.json).

New tests exercise description validation and lifecycle, compressed-file persistence through external income and final history, actual format-seven ballots without descriptions, and the complete 64-result history through the production packet codecs. The LAN harness enters both fields in the real form, verifies their server echo and the second client's read-only ballot, reads the archived description, checks compact widget bounds and retains the text after closing and reopening the world. New screenshots show the actual free-vote form, ballot and archived reader.

The production JAR is built before any QA-only source sets and saved with a SHA-256 hash. The release gate verifies that the saved bytes remain unchanged and exclude all GameTest, visual-test and LAN-client fixtures. Its mod ID remains `kingdomtreasury` and its public compatibility classes remain in `pt.crownledger.api`.

## Automated coverage

The domain suite covers the existing treasury, credits, payroll, tax and receipt rules plus proposals, immutable quotes, electorate snapshots, participation rounding, mutable individual ballots, deadline rejection, majority and ties, cooldown, nonfunded consultations, exact reservations/refunds, restart reconstruction, overflow recovery, capped history and clock rollback.

Native Minecraft tests exercise compressed `data/crown_ledger.dat` files, format 6/7 migration to format 8, corrupt/downgraded ballot rejection, actual failed atomic replacements and retry, persisted votes alongside external income receipts, private server snapshots, physical chest range, active/sworn President authorization when Realm Council is installed, positive XP grants, the actual player exhaustion mixin, server mining events and crop growth event/protection behavior. Existing treasury regressions remain part of the same suite.

The optional integration run uses the exact released JARs and hashes recorded in `mod-pronto/realm-treasury-0.3.1.build.json`, including Realm Council, Contracts, Markets, Casino, Laws & Justice, Economy, Marriage, Identity, Notifications, Titles and Rewards. Realm Origins and its Origins/Jupiter dependencies are not loaded in this native run. Existing compatibility API signatures are retained; this is not a claim that every companion's full functionality was retested.

## Actual client and LAN checks

The existing actual Minecraft client harness validates the chest model, textures, facing states and prior treasury/currency controls, using clearly labelled demonstration menu data.

The new opt-in `-PtreasuryClientTests` harness starts **two actual Minecraft client processes** connected to a published integrated server over TCP LAN, with offline test identities. Starting funds and short voting durations are controlled fixtures. The new ballot UI and transport are production code, not synthetic menu snapshots. It opens the real chest, announces a funded benefit, verifies that the guest is not an administrator, receives an unforced HUD, moves with the HUD visible, opens the ballot via F8, changes the guest vote, verifies two final votes and one cost reservation on disk, waits for approval, inspects benefits/history and rejects a tied free consultation. It closes and reopens the real world and checks the retained benefit, history and balance. Compact widget bounds are checked at 320×240, 480×270 and 800×450.

The canonical standalone LAN harness does not load optional companions. Realm Council authority is exercised separately in the native integration run. This does not represent testing a remote authenticated dedicated server, every operating system, high latency or every modpack combination. Consult the published build JSON for the actually executed results.

## Limits and migration

Storage format 8 reads formats 1–7 without inventing funds. Keep a world backup before upgrading. Old binaries cannot safely read new funded votes, so restore the backup when downgrading. Clients and server must all use protocol 5 / Realm Treasury 0.3.1.

Reservations, ballots, final results and refunds are persisted in one ordered, atomically replaced treasury file. Old queued saves and income API writes use the same NeoForge IO worker. A normal rejected vote returns its original reserved amount once; an extreme balance overflow retains a pending refund and blocks further proposals until room is available. File replacement failures retain the previous in-memory state for retry. These checks do not claim durability against arbitrary filesystem corruption or faulty storage hardware.

Prices and effects are fixed when proposed. Expiry uses real time and a monotonic clock while the server runs, with durable time checkpoints every 30 seconds while a vote or benefit exists. Rewinding the clock never reopens a finished poll or extends an already persisted expiry. An offline clock rollback cannot reveal the true elapsed time; the server resumes from its last recorded high-water time.

Crop support is limited to standard `CropBlock` growth events in ticking chunks. The configured percentage is an extra chance on an eligible growth event, not an exact multiplicative speed guarantee. Protection decisions are respected and no chunks are force-loaded. Mining multiplies the existing break speed, retaining other modifiers; positive fractional XP bonuses are probabilistic. Creative, spectator and FakePlayer entities receive no player benefit.

Administrators may edit server configuration and use emergency proposal/cancellation access at a physical chest. Voter choices are never broadcast to other clients or retained in final history, but server operators with filesystem access can inspect an ongoing ballot's saved data.

## Reproduction

```sh
./gradlew build
GITHUB_SHA=$(git rev-parse HEAD) python3 tools/treasury_release.py snapshot
./gradlew -PcrownGameTests runGameTestServer
python3 tools/treasury_release.py install
./gradlew -PcrownGameTests -PintegrationTests runGameTestServer
./gradlew -PcrownVisualTests runClient
./gradlew -PtreasuryClientTests runClient
```

The managed cloud equivalent is `python3 tools/cloud_gradle.py` with the same flags. GUI runs need an actual display or Xvfb with software OpenGL. Release packaging additionally requires the fresh successful build/native logs and client reports; it rejects missing, failed or stale evidence.
