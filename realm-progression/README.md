# Realm Progression

**Minecraft 1.21.1 · NeoForge 21.1.256+ · Java 21 · 0.1.0 Beta**

An optional addon for **Realm Treasury** that turns public wealth into permanent, kingdom-wide crafting unlocks. Administrators select installed mods and set a euro target for each one. There is no additional wallet and unlocking never deducts money.

The initial rule locks normal **Create crafting until the available public euro balance reaches €8,000**. Create remains optional: other supported mods can be selected in the administration menu without rebuilding the addon.

[Download JAR](../mod-pronto/realm-progression-0.1.0.jar) · [Download ZIP](../mod-pronto/realm-progression-0.1.0.zip) · [Actual game screenshots](../media/realm-progression/README.md) · [Validation](VALIDACAO.md)

## Install

1. Back up the world and install the same Realm Progression version on every client and the server/LAN host.
2. Install **Realm Treasury 0.4.0** on both sides. Its internal ID remains `kingdomtreasury`. This release supports the Treasury 0.4 series.
3. For euro income and donations, use **Realm Origins 0.5.7** with its own dependencies. Research reads the Treasury's `CHRONICLES` balance in cents; emerald balances do not satisfy euro goals.
4. Install the mods you want to govern normally, then restart the game/server. The addon cannot install, unload or activate mod JARs while Minecraft is running.
5. Craft a **Research Table** and place it anywhere accessible to players. Every table opens the same world-wide research data.

Create **6.0.10 for Minecraft 1.21.1** is the pinned version used for validation. Create is not required to launch the addon. Its other versions and unrelated mods need their own compatibility checks. The validated companion set includes Realm Economy 0.5.0, Realm Origins 0.5.7, Realm Notifications 0.1.1, Contracts & Bounties 0.4.0 and Realm Council 0.2.2.

## Original Research Table

A carved walnut desk with brass fittings, a blue cloth, an open atlas, a jade bookmark, a rolled charter, an inkwell, a quill and a brass astrolabe. The original model has 63 elements and seven original pixel materials. Includes four horizontal orientations, a crafting recipe, drops, creative/search registration and English, Portuguese (Portugal) and Portuguese (Brazil) translations.

```text
Amethyst shard     Gold ingot         Amethyst shard
Dark oak planks    Book and quill     Dark oak planks
Dark oak planks    [empty]            Dark oak planks
```

## Player menu

Right-click a Research Table to consult kingdom goals, the available treasury balance, progress, unlock status and recent records. Research approvals apply to all players, including players joining later, in every dimension of the same world.

If the treasury already satisfies a goal when a mod is selected or the addon is first installed, its supported crafting unlocks after the balance is confirmed. The public balance is observed; the addon never creates, reserves, charges or transfers money. Balances already allocated to salaries, contracts, loans or funded votes are not added back to available funds.

An unlock remains approved after spending, a presidential election, a restart, temporary mod removal or a later increase to its target. It is stored by mod ID and world. A removed mod is not newly approved while absent. Its previous approval survives reinstallation.

A small animated royal banner and a sound announce new approvals to online clients. Announcements queue rather than overlap, and do not control crafting. Locked outputs have an explanatory tooltip. Recipes can remain visible in the recipe book/JEI; recipe hiding is not used as enforcement.

## Administration

The same physical table exposes administrative controls to authorised operators (**permission level 2+**). A President has no implicit administrator powers. The optional `ProgressionPermissions.Provider` API allows a Realm Admin integration; when a mod with ID `realmadmin` is installed without a registered provider, administration fails closed.

1. Switch **Kingdom goals** to **All installed mods**.
2. Search for a mod and select it.
3. Set its **Treasury target (€)**. Values accept a decimal point or comma, with at most two decimal places.
4. Review the detected standard crafting count and confirm **Save goal**.
5. **Disable lock** allows its crafting freely while preserving any earlier permanent approval.

The menu lists mods loaded on the server, not client-only installations. Mods without supported standard recipes cannot be enabled through a default empty output scope. Minecraft, NeoForge, research itself and the components necessary for public money and administration are protected.

**Item namespaces** is an advanced control for mods whose registered item namespace differs from their mod ID, or several namespaces belonging to one target. It is prefilled for ordinary mods; separate multiple values with commas. The selected namespaces must exist, cannot name protected systems and cannot overlap another enabled rule. An already approved namespace scope cannot be expanded, preventing an old approval from silently granting access to additional output groups. Ask for an integration if a mod cannot be represented by its item namespaces and normal crafting.

Every administrative change records UUID, public RP name when available, time and previous/new policy. Server permissions, session distance, table existence, nonce, revision, field names, amount types and limits are checked again on each request. Clients cannot submit a treasury balance or an unlock flag. Stale confirmations must be reviewed again.

## Exactly what is blocked

Supported output items are blocked **before pickup/transfer** in:

- The player's normal 2×2 inventory crafting grid.
- The Vanilla 3×3 crafting table.
- The Vanilla automatic crafter, including cached recipes and its preview.

Normal clicks, repeated/Shift crafting and a result cached before an administrator enabled a lock have server guards. Failed crafts preserve ingredients. The actual produced item is checked; a datapack can rename a recipe to the `minecraft` namespace without bypassing the selected output namespace. Unlocks refresh open Vanilla crafting menus.

**Custom machines are outside this release's coverage**, including Create mixing, pressing, mechanical crafting and production performed directly by workers or custom menus in other mods. Furnaces, stonecutters, smithing, loot, trading, commands and pre-existing items are not governed. Items obtained through those paths remain usable and placeable. Mods remain loaded and world generation, mobs and passive features continue normally. This is a crafting progression system, not universal runtime deactivation of selected mods. Extending coverage to a particular machine requires an adapter and tests.

The recipe count measures static normal crafting outputs, not every custom or dynamic production path. Actual-result checks still protect dynamic outputs when they pass through the supported Vanilla containers.

## Persistence and recovery

`data/realm_progression.json` stores versioned rules, approval timestamps, witnessed public balances and up to 256 recent audit entries. Goals support 1–900,000,000,000,000 cents, up to 256 configured targets and up to eight output namespaces per target. All Research Tables share this file.

The addon confirms the Treasury's available euro balance against its saved file before committing an approval. Treasury income, ordinary Treasury commits and durable Treasury operations have read-only observer hooks; a periodic server check covers other balance changes. Pending loan transactions suspend balance confirmation. The research file is written to a temporary file, forced and atomically replaced before approval is synchronised or announced. Supported operating systems also force the containing directory. No offline wallet files are edited.

Malformed, unsupported or inaccessible research data is preserved. A failed write retains the previous rule and does not announce an approval. If the file cannot be loaded safely, non-essential mod crafting fails closed until the administrator restores a consistent backup. Existing items are not confiscated. Keep the world, Treasury and progression data together when backing up or restoring.

Removing the addon removes its enforcement. Keep it installed on all sides to retain progression. JSON edits while the world is running are not a supported administration workflow.

## Build and verify

```sh
./gradlew build --console=plain
./gradlew -PprogressionGameTests runGameTestServer --console=plain
python3 ../.github/scripts/progression_release.py install
./gradlew -PprogressionGameTests -PcreateTests -PintegrationTests runGameTestServer --console=plain
./gradlew -PprogressionVisualTests -PcreateTests -PintegrationTests runClient --console=plain
```

The release helper pins the Treasury, companions and Create by SHA-256. The production JAR excludes all game-test fixtures, the temporary test mod, client automation and bundled third-party libraries. Actual-client evidence seeds a demonstration world and connects two offline-identity clients over TCP LAN; it does not establish two authenticated Microsoft accounts or compatibility with every mod/machine.

Cloud builds use `python3 tools/cloud_gradle.py` instead of the direct wrapper, preserving the managed environment's proxy and CA.
