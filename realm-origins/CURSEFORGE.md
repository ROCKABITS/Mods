# Realm Origins

**Summary:** Cinematic RP character creation, public character names, a parchment journal, and a connected digital and physical euro economy.

Create your character through a dark, starry interface with lilac accents, chapter navigation and a live 3D preview of your skin. Choose an RP name, age and Origins lineage, write an occupation in one word, add a backstory and draw your signature. Quick occupation suggestions are available, and the server validates custom entries.

After registration, press **P** to open the original parchment journal. Browse your profile, deeds, history and wallet, or edit your memories, occupation and signature. Origins classes and powers remain in the actual Origins interface. No personality sliders or extra attributes are added by this visual rework.

Your RP name appears above your head, in TAB and as the author of ordinary chat messages. Players without a valid created character retain their normal username. The account UUID, command targets and internal account identity remain unchanged. Other mods that read raw account usernames can still display those names.

Use digital euros, pay other players, or withdraw physical coins and banknotes. Deposit them with exact change through the book. Values use integer cents, with eight coin denominations and seven banknotes ranging from one cent to €500. The reveal animation shows the starting funds already saved by the server; it does not pay or roll them twice.

Integrates with the other Realm mods, including Contracts & Bounties, Crown Ledger and Realm Identity. Origins class, RP occupation and economic background are separate. Interfaces support English, Portuguese (Portugal) and Portuguese (Brazil); Origins and add-ons provide their own lineage/class translations.

Realm Origins is the new name of **Chronicles RP**. Existing characters, wallets, items and integrations keep the internal `cronicasrp` identifiers. Replace the previous JAR rather than installing both names together.

## Requirements

- Minecraft Java 1.21.1.
- NeoForge 21.1.256+ in the 21.1 series, and Java 21.
- Origins (NeoForge) 0.3.2 by IAFEnvoy, and Jupiter 2.3.7 for NeoForge 1.21.1.
- Origins: Classes (NeoForge) 0.3 is optional.

Install the same version and dependency set on the server and all clients. Protocol **8** requires Realm Origins **0.5.7** on both sides; older clients cannot decode arbitrary occupations. Existing profiles and balances are preserved.

## Images

[Full screenshot gallery](../media/realm-origins/README.md) · [23 PNG images and icon](../media/realm-origins/0.5.7/realm-origins-0.5.7-media.zip) · [Project icon](../media/realm-origins/0.5.7/icon.png)

![Cinematic character creation](../media/realm-origins/0.5.7/realm-origins-03-creation-en.png)

![Original parchment journal opened with P](../media/realm-origins/0.5.7/realm-origins-07-profile-en.png)

## File submission

Upload `realm-origins-0.5.7.jar`, select Minecraft 1.21.1, NeoForge, Java 21, and both client and server. Use the 0.5.7 section of [CHANGELOG.md](CHANGELOG.md) for the changelog.

Mark Origins (NeoForge), project 1375372, and Jupiter, project 1072905, as Required Dependencies. Mark Origins: Classes (NeoForge), project 1536425, as Optional. External dependencies are not bundled. See [VALIDACAO.md](VALIDACAO.md) for actual executed checks and their limits.
