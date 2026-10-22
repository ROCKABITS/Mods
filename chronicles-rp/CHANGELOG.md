# Changelog

## 0.5.6 — O livrinho no P

- P volta a abrir o livro original de pergaminho depois da criação da personagem.
- Restabelecidos o layout, as cores, a fonte e os botões das páginas Ficha, Feitos, História, Bolsa e câmbio.
- A criação por capítulos e as boas-vindas mantêm o novo visual escuro e lilás.
- Mantidos nome Realm Origins, perfis, carteira, dados, integrações e protocolo 7.

## 0.5.5 — Realm Origins

- Chronicles RP passa a chamar-se **Realm Origins**; o JAR é `realm-origins-0.5.5.jar`.
- Novo visual escuro e lilás para criação, perfil, história, assinatura, carteira e câmbio.
- Quatro capítulos de criação com progresso visual, skin real em 3D, pedestal de luz e fundo estrelado discreto.
- Ecrã final «Um novo capítulo», com contagem visual dos fundos já atribuídos e botão para entrar no mundo.
- Botões, campos e assinatura com contraste e foco visíveis; adaptação a janelas pequenas.
- Mantidos os campos atuais, o dinheiro físico/digital, nomes RP, profissões, reputação, Origins e integrações.
- Não adiciona personalidade, atributos, novo sorteio de fortuna ou migração financeira.
- `cronicasrp`, os dados, os IDs e o protocolo 7 permanecem para compatibilidade. Retira o JAR antigo antes de instalar este.

# Chronicles RP — changelog

## 0.5.4 — Realm Notifications integration

- Optional styled notifications for successful player transfers and administrator grants, with the payment amount and reason.
- Cash withdrawals and deposits use conversion notices, without describing them as new income.
- Existing chat or action-bar feedback remains available without Realm Notifications or a supporting client.
- RP profiles, wallet calculations, public RP names and network protocol 7 retain their existing formats.

## 0.5.3 — Public RP names

- Created characters use their RP name above the player, in TAB and as the author of ordinary Minecraft chat messages.
- The Chronicles RP character book uses the same public identity. Players without a created character keep their account username.
- Public names synchronize on profile changes and when players join, respawn, change dimension or start tracking each other; only the public name and UUID are shared.
- Account usernames, UUIDs, skins and command targets remain unchanged. Team visibility and formatting remain in effect.
- Existing profiles, signature drawings, Origins choices, reputation and money keep their current storage format and values.
- Network protocol 7 requires Chronicles RP 0.5.3 on the server and every client.
- Full release notes: `CHANGELOG_0.5.3_EN.md` and `CHANGELOG_0.5.3_PT.md`. Actual test status: `VALIDACAO.md`.

## 0.5.2 — Character creation first

- RP creation opens before Origins; its lineage button starts race, then optional class selection.
- Native choices return to the same unfinished RP form, keeping the entered name and age.
- Server selection checks, existing-character flows, economy and the 0.5.1 power fix are retained.
- Full release notes: `CHANGELOG_0.5.2_EN.md` and `CHANGELOG_0.5.2_PT.md`.

## 0.5.1 — Origins compatibility fix

- Corrected the Elytrian claustrophobia JSON in Origins (NeoForge) 0.3.2 so Weakness and Slowness decode correctly.
- Added native registry and low-ceiling behavior regression tests.
- Dependency versions, RP storage and network protocol 6 remain unchanged.
- Full release notes: `CHANGELOG_0.5.1_EN.md` and `CHANGELOG_0.5.1_PT.md`.

## 0.5.0 — Origins integration

- Required integration with Origins (NeoForge) 0.3.2 by IAFEnvoy.
- Optional integration with Origins: Classes (NeoForge) 0.3.
- Character creation waits for the native Origins selections and uses the server-confirmed lineage.
- The character book displays the actual lineage and a separate Origins class field.
- Origins buttons open native lineage/class/powers information and return to the original book or unfinished draft.
- Server datapack origins are discovered through Origins registries, replacing the fixed four-race UI.
- Origin changes update the RP lineage without rerolling wealth or changing other character fields.
- RP occupation remains independent of Origins class and economic background.
- English and both Portuguese locales include the new interface labels.
- Expanded race identifiers in network packets; protocol version 6 requires updating all clients and the server.
- Existing 0.4.1 RP profiles, signature drawings, achievements and balances are preserved.

Tested with Minecraft 1.21.1, NeoForge 21.1.256, Java 21, Origins 0.3.2, Jupiter 2.3.7, with and without Classes 0.3.

# 0.4.1

- English and Portuguese menus, character labels, item tooltips, validation errors and server messages follow the Minecraft language setting.
- English commands are available alongside existing Portuguese aliases.
- Money and number formatting follows the selected language; EUR denominations and stored cents remain unchanged.
- Existing names, stories, signatures, canonical RP choices and balances are preserved.
- Network protocol 5 carries translatable components. Update server and clients together.
