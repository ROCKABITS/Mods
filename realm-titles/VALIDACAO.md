# Validação do Realm Titles 0.1.0 Beta

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39029117475), código `a9de43fdc55325ff9f3aaedaf4ffc61608b8dc16`. Os dois JARs foram publicados juntos no commit `408c79677f67eff1b28d6cfd63a0ff8b017a8631`.

| Projeto | Unitários | GameTests independente | GameTests integrado |
| --- | ---: | ---: | ---: |
| Realm Titles 0.1.0 | 48 | 22 | 22 |
| Realm Identity 0.1.1 | 35 | 23 | 23 |

Zero falhas, erros ou testes unitários ignorados. Os auxiliares independentes também passaram localmente nos 48 + 35 testes. Nos GameTests sem companheiros, os ramos de integração verificam a ausência e o comportamento independente; as provas do Council, Notifications e títulos no Identity são executadas com os mods carregados na suite integrada.

## Servidor real

- Registo e receita reais, presença no criativo/pesquisa, quatro orientações, colisões limitadas a cada metade e bloqueio dos pistões.
- Colocação pelo item, chão/altura/espaço superior, destruição das duas metades em sobrevivência/criativo, perda de suporte e explosão sem duplicações ou órfãos.
- Sessões físicas: distância, dimensão, estrutura, relógio, ticks, pedidos remotos e pacotes malformados. Leitores públicos e operadores sem autoridade não conseguem alterar o registo.
- Governante atual e jurado: rejeição de mandatos futuros, expirados, sem juramento e juramento futuro; perda de permissão pelo antecessor; Council indisponível não ativa o responsável alternativo.
- Vários títulos offline, permanência, expiração exata, renovação com novo identificador, revogação antiga rejeitada, limites de catálogo/nomeações, paginação e NBT estrito.
- Notificações reais pelo canal opcional e alternativa privada no chat; só depois de confirmar a alteração, sem avisos duplicados em pedidos rejeitados. Expiração notifica uma vez o destinatário online.
- Documentos autênticos do Identity consultam os títulos atuais, incluindo titulares offline. Nomeações, alteração de descrição, revogação e expiração preservam item, selo, número de série e perfil emitido. Uma API de títulos indisponível afeta só essa secção; documentos falsificados não consultam títulos.

Companheiros publicados verificados por SHA-256: Chronicles RP 0.5.4, Contracts 0.3.2, Realm Council 0.2.2, Crown Ledger 0.2.1, Realm Markets 0.2.1, Realm Rewards 0.1.2 e Realm Notifications 0.1.0, além do outro JAR novo desta publicação. Origins 0.3.2, Jupiter 2.3.7 e Origins: Classes 0.3 reais foram carregados; os três artefactos também têm checksums fixados na proveniência.

## Cliente e ficheiros publicados

O cliente Minecraft 1.21.1/NeoForge real confirmou as oito variantes da mesa, com 485 faces na metade inferior e 239 na superior, sem sprites em falta. Capturou as quatro orientações e recebeu os dados dos menus por S2C real. A edição e pesquisa viajaram por C2S e foram confirmadas pelo servidor; não foram injetados snapshots artificiais nos ecrãs.

Os três separadores do governante e o registo público passaram nas verificações de controlos/scroll em 320 × 240. A mudança de governante removeu os controlos de gestão; partir a mesa fechou a sessão. O documento real mostrou dois títulos e, depois de revogar um, mostrou apenas o restante, conservando o mesmo item e número de série.

Foram produzidas **11 capturas de 1600 × 900**; uma amostra da mesa, dos três menus e dos dois estados do documento foi inspecionada visualmente. São capturas de QA com personagens e títulos de demonstração; a primeira captura ainda mostra parte do cenário a carregar e existem avisos de tutorial Vanilla. A [seleção visual](../media/realm-titles/README.md) destaca a mesa com o cenário carregado.

Os JARs/ZIPs e todas as capturas foram novamente verificados após obter o commit publicado. Não incluem fixtures, templates nativos ou bibliotecas de outros mods. O pacote conjunto contém apenas os dois JARs, instruções e checksums.

- Realm Titles JAR, 147 297 bytes: `65d854ffd92805f6e8cb0891454f80ba5a6f33394cc0375cf23f64098af2bd16`.
- Realm Identity JAR, 95 549 bytes: `4096147ac74847d31ee733291ad85d8d154ff7340e8d6f0874dd7cb7364823b0`.
- [JAR Titles](../mod-pronto/realm-titles-0.1.0.jar) · [ZIP Titles](../mod-pronto/realm-titles-0.1.0.zip) · [Pacote dos dois mods](../mod-pronto/realm-titles-0.1.0-suite.zip) · [Proveniência](../mod-pronto/realm-titles-0.1.0.build.json).

## Âmbito e ambiente

O cliente usou um mundo integrado e dados de demonstração criados pelas APIs reais. Isto não prova uma sessão autenticada com dois clientes nem todas as interfaces de outros mods. Os títulos são públicos, associados ao UUID da conta no formato atual do Chronicles; não existe identificador independente de personagem. A persistência é a normal do mundo, sem promessa de transação de disco em caso de falha abrupta.

A compilação Minecraft local foi tentada com o wrapper/distribuição verificados, Java 21, proxy e certificados do ambiente. O download do NeoForm Runtime 2.0.31 devolveu HTTP 403 antes do Java. As compilações e provas nativas/visuais acima correram no GitHub Actions, sem desativar TLS ou alterar checksums.
