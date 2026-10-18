# Validação — Laws & Justice 0.1.0 Beta

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39168508219), código `bb382705b2bc6ba1ce001fc1e6ef5a62e91339a1`. JAR e imagens publicados no commit `7228346a467ba509b3f04c04b497368a9b67eb8d`.

| Verificação | Resultado |
| --- | ---: |
| Testes unitários | 51 |
| GameTests independentes | 48 |
| GameTests com dez companheiros | 48 |
| Cliente Minecraft real | Aprovado, 13 capturas |

Zero falhas, erros ou testes unitários ignorados. A compilação e as suites também passaram localmente. Os GameTests condicionais verificam a ausência de integrações no modo independente; perfis RP e o canal de notificações são exercitados com os mods reais no modo integrado.

## Servidor e persistência

- Receitas, criativo/pesquisa, colocação nas quatro orientações, colisões e as duas metades da porta. Destruição, colocação e utilização de baldes pelo condenado são bloqueadas pelos eventos reais do servidor.
- Sessões físicas, revogação de operador, distância, dimensão, validade, pedidos repetidos, versões antigas e coordenadas inventadas. Jogadores comuns só recebem a própria pena.
- Capacidade das celas, penas offline, bloqueio de edição das prisões ocupadas, aplicação e libertação administrativa auditadas.
- Tempo contado através dos ticks online reais, pausa offline, regresso à prisão no login, teleporte, mudança de dimensão e evento real de respawn. Cumprimento e libertação são registados uma única vez.
- Ficheiro comprimido real, roundtrip de penas e libertações pendentes, corrupção, truncagem, campos desconhecidos/ausentes, números inválidos, UUIDs não canónicos e chaves repetidas.
- Um histórico com mais de 65 535 bytes confirma a gravação em UTF-8 num array NBT, evitando o limite de StringTag.
- Falha real de escrita conserva o estado anterior. Uma libertação já entregue não volta a teleportar nem notificar enquanto o checkpoint é repetido; o jogador pode afastar-se livremente.

Companheiros publicados verificados por SHA-256: Chronicles RP 0.5.4, Contracts & Bounties 0.3.2, Realm Council 0.2.2, Crown Ledger 0.2.1, Realm Markets 0.2.1, Realm Rewards 0.1.2, Realm Notifications 0.1.0, Realm Titles 0.1.0, Realm Identity 0.1.1 e Realm Casino 0.1.0. Origins, Jupiter e Origins: Classes reais também têm checksums fixados na proveniência.

## Cliente e LAN

O cliente real confirmou os cinco modelos de itens e as **76 variantes dos blocos**, sem sprites em falta. Os cinco separadores administrativos e o ecrã privado do condenado passaram nas verificações de controlos/scroll a 320 × 240. Os botões reais enviaram C2S para registar prisão, cela e pena; o servidor devolveu os estados S2C. Não foram injetados snapshots artificiais.

O anfitrião perdeu de facto a permissão de operador e passou a receber apenas os seus dados. Um teleporte para fora da área e uma morte/respawn Vanilla devolveram a mesma pena à prisão. O mundo foi realmente publicado por LAN, encerrado, deixado offline durante cinco segundos e reaberto com o mesmo UUID e identificador de pena. O ficheiro guardou **149 ticks cumpridos** e a leitura após reconexão mostrou **162**, correspondentes apenas aos novos ticks online.

Uma pena de um minuto terminou pelo relógio normal do servidor, sem avanço manual: o registo ativo desapareceu, ficou uma decisão COMPLETED e o jogador foi enviado para a saída definida. As [13 capturas reais](../media/laws-justice/README.md) usam uma construção e personagem de demonstração. O mod não gera essa prisão. Esta prova usa um cliente integrado e não estabelece uma sessão autenticada com dois clientes remotos.

## Publicação e limites

JAR, ZIP, 13 PNGs e ZIP das imagens foram novamente verificados depois de obter o commit publicado. Os downloads HTTP do JAR e ZIP têm os mesmos checksums. O JAR não inclui fixtures, templates nativos ou dependências externas.

- JAR: 203 613 bytes; SHA-256 `21cd60517db824614cda6e81793c8e57a199895eb88d8338fbcec001e252e840`.
- ZIP: 193 574 bytes; SHA-256 `92e3573bb67da1492096971447bbcddee090b6e60e8fe9875cb759963eaf9e76`.
- [JAR](../mod-pronto/laws-justice-0.1.0.jar) · [ZIP](../mod-pronto/laws-justice-0.1.0.zip) · [Proveniência e checksums das imagens](../mod-pronto/laws-justice-0.1.0.build.json).

Os testes correram em Linux, Java 21 e Minecraft 1.21.1/NeoForge. Não representam todas as falhas de hardware ou todas as combinações de mods; a sincronização de diretórios no Windows não foi testada. Um encerramento abrupto pode perder os últimos ticks desde o checkpoint. Conserva cópias de segurança conjuntas do mundo.

Esta é a base 0.1: quatro blocos, agrimensor, prisões, celas e penas. As decisões são administrativas nesta versão; Presidente, mandados, trabalhos e economia seguem o [plano por versões](PLANO-COMPLETO.md).
