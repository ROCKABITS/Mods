# Validação — Realm Casino 0.1.0 Beta

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39145123134), código `da09bf63e9e2054927e57af5ee9928cad84dfafa`. O JAR e as imagens foram publicados no commit `f511c5ce3e57023bd996eb5e7012017a46b590d0`.

| Verificação | Resultado |
| --- | ---: |
| Testes unitários | 48 |
| GameTests sem companheiros | 44 |
| GameTests integrados | 44 |
| Cliente Minecraft real | Aprovado, 15 capturas |

Zero falhas, erros ou testes unitários ignorados. A compilação e as três suites também passaram localmente. Sem os companheiros, os testes condicionais verificam a ausência das integrações; os pagamentos reais, recibos do Chronicles e notificações são exercitados na suite integrada.

## Economia e servidor

- Os três blocos têm receitas, presença no criativo/pesquisa e quatro orientações; a máquina de slots ocupa duas alturas. Colocação, colisões, desmontagem e proteção de propriedade foram verificadas no servidor.
- Depósitos, apostas, prémios e recolhas conservam a soma da carteira, bancas e quantias por receber. A banca insuficiente rejeita a aposta antes da cobrança; levantar fundos não consome a reserva de uma mão ativa.
- O servidor rejeita pedidos repetidos, versões antigas, resultados enviados pelo cliente, UUIDs de terceiros, ações remotas e configurações de probabilidades pelo proprietário.
- O blackjack conserva o baralho e a reserva; pedir carta usa a próxima carta persistida. Fecho do menu, desconexão e prazo esgotado param a mão existente uma única vez. A carta oculta e o resto do baralho não seguem para o cliente.
- NBT inválido, registos de transferência incoerentes e falha real na substituição do ficheiro bloqueiam as operações económicas. Um recibo válido permite concluir sem repetir a cobrança, conservando alterações legítimas posteriores na carteira. Saldos ambíguos e recibos falsificados são rejeitados.
- As notificações reais usam o canal opcional ou uma mensagem privada alternativa, após confirmar a operação.

Companheiros publicados verificados por SHA-256: Chronicles RP 0.5.4, Contracts & Bounties 0.3.2, Realm Council 0.2.2, Crown Ledger 0.2.1, Realm Markets 0.2.1, Realm Rewards 0.1.2, Realm Notifications 0.1.0, Realm Titles 0.1.0 e Realm Identity 0.1.1. Origins, Jupiter e Origins: Classes reais também foram carregados e verificados por checksum.

## Cliente e persistência do anfitrião

O cliente real confirmou os modelos dos três itens e as 16 variantes dos blocos, sem texturas em falta. Os três jogos e os separadores de regras/proprietário passaram nas verificações de controlos e scroll em 320 × 240. Depósitos, levantamentos, recolhas, configuração e apostas viajaram por C2S/S2C reais, usando a carteira do Chronicles e resultados aleatórios do servidor; não foram injetados snapshots financeiros nos ecrãs nem substituída a fonte de aleatoriedade do servidor.

A roleta foi fotografada em movimento e parada, com a mesma câmara e identificador de ronda: a superfície renderizada mudou em 2723 píxeis. Os resultados atualizam também os blocos através dos pacotes nativos. As [15 capturas de 1600 × 900](../media/realm-casino/README.md) incluem detalhes individuais dos móveis e menus; uma seleção dos modelos e resultados foi inspecionada visualmente após a publicação.

Em cinco operações reais — depósito, recolha e uma aposta de cada jogo — foram verificados o saldo e o mesmo recibo em `playerdata/UUID.dat` e em `level.dat/Data/Player`. Cada tag de reinício do anfitrião foi carregada num novo jogador do servidor e restaurou o saldo correto. Esta prova cobre a fonte de dados que o Minecraft prefere ao reabrir um mundo singleplayer/LAN.

## Ficheiros e limites

O [JAR](../mod-pronto/realm-casino-0.1.0.jar), o [ZIP](../mod-pronto/realm-casino-0.1.0.zip), os 15 PNGs e o ZIP das imagens foram novamente verificados após obter o commit publicado. Os downloads HTTP do JAR e ZIP têm os mesmos checksums. O JAR não contém fixtures, templates de teste ou bibliotecas dos companheiros.

- JAR: 263 634 bytes; SHA-256 `8f4410f7469da0c60ec35b97976aae62f31873c76459c52c46f7717f6526587b`.
- ZIP: 246 835 bytes; SHA-256 `1a59a59501c588a8737670b77cc8cb83d4cf47cb4ae9ac29ab32c3751772efe9`.
- [Proveniência, dependências e checksums das imagens](../mod-pronto/realm-casino-0.1.0.build.json).

Os testes correram em Linux, com Java 21 e Minecraft 1.21.1/NeoForge. O cliente usou um servidor integrado com cenário, personagens e fundos de demonstração; não prova uma sessão autenticada com dois clientes nem todas as combinações de outros mods. As falhas de armazenamento testadas não representam todas as falhas de hardware; a sincronização de diretórios no Windows não foi validada. Mantém cópias de segurança conjuntas do mundo e dos perfis.

Esta primeira fase inclui apenas slots de três rolos, roleta europeia e blackjack contra a banca. Os restantes jogos e o imposto do Crown Ledger ficam para atualizações posteriores. Sem Chronicles e uma personagem criada, as operações económicas ficam desativadas.
