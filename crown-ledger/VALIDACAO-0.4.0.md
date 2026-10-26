# Validação — Realm Treasury 0.4.0

A atualização acrescenta o Balcão de Empréstimos ao mod existente, com crédito financiado pelo tesouro, aprovação presencial, assinatura pessoal e pagamentos manuais. Mantém os identificadores públicos, as APIs e o cofre anterior. O formato de dados passa de 8 para 9 e o protocolo de rede de 5 para 6.

## Verificações

Java 21, Minecraft 1.21.1, NeoForge 21.1.256, Linux/X11, renderização por software. O pipeline guarda o JAR de produção antes das compilações de fixtures, exclui classes/estruturas de teste e só publica depois destas verificações:

| Verificação | Contagem |
| --- | --- |
| JUnit — regras, valores, impostos, votos e empréstimos | 102 |
| Testes nativos anteriores, independentes | 40 |
| Testes nativos anteriores, com companheiros publicados | 40 |
| Crédito nativo, esmeraldas | 14 |
| Crédito nativo, carteira Realm Origins e integrações reais | 14 |

São **210 execuções de testes**, além dos cenários de cliente real.

Os testes de crédito cobrem reserva do capital, juros fixos, arredondamento, soma exata das prestações, liquidação antecipada, falta de saldo/espaço, rejeição de repetições, autorização do Presidente, fim do mandato, sessões físicas, privacidade, receitas fiscais concorrentes, preservação das votações e migração de formatos antigos.

As falhas de recuperação são injetadas no caminho nativo de serialização do jogador e na substituição atómica real do ficheiro do tesouro. Os testes voltam a carregar o ficheiro nativo anterior ou posterior à operação e recuperam a intenção duas vezes, verificando que o capital ou a prestação só são transferidos uma vez. A carteira inicial é confirmada em disco antes de preparar uma intenção, incluindo perfis que ainda não tenham sido guardados por um autosave normal.

## Clientes Minecraft reais

- Regressão do cofre, contas e política: interface de produção com fundos de demonstração.
- Votações: dois processos Minecraft ligados por TCP LAN, votos secretos, benefício financiado, consulta com título/descrição, histórico e encerramento/reabertura do mundo.
- Crédito: dois processos Minecraft ligados por TCP LAN, Realm Origins 0.5.7 e Realm Council 0.2.2 reais. O requerente e ambos os Presidentes não têm OP. O primeiro empréstimo reserva 100 €, entrega 100 € após a assinatura, cobra uma prestação de 22 € e liquida os 88 € restantes. O Presidente não recebe o dinheiro na sua carteira.
- A mudança de mandato retira os poderes do antigo Presidente. O novo Presidente aprova 50 € de crédito ao antigo; a carteira e o mesmo recibo chegam ao ficheiro UUID do anfitrião e ao `level.dat/Data/Player`.
- O mundo LAN é efetivamente fechado e reaberto com esse empréstimo ativo. A dívida e os saldos permanecem e a liquidação é feita noutro balcão, depois de retirar o original.
- Os controlos e os textos com deslocamento são verificados em 320×240, 480×270 e 800×450; o cliente também captura o contrato em GUI scale 3. O layout compacto mantém as ações de assinatura e pagamento acessíveis.

Os modelos e menus são capturados dentro do Minecraft. O balcão tem 66 elementos e texturas originais. Personagens, ofertas e fundos iniciais são fixtures identificados; os menus, pacotes, carteiras e ficheiros utilizados são os de produção. As identidades dos dois processos são de QA offline, não duas contas Microsoft autenticadas.

## Integrações e limites

Os companheiros publicados são verificados por SHA-256: Contracts & Bounties 0.4.0, Realm Council 0.2.2, Realm Identity 0.1.2, Realm Markets 0.2.1, Realm Notifications 0.1.1, Realm Rewards 0.1.2, Realm Titles 0.1.0, Realm Casino 0.3.0, Laws & Justice 0.4.0, Realm Economy 0.4.1 e Realm Marriage 0.1.0. Os cenários da carteira acrescentam Realm Origins 0.5.7, Origins, Jupiter e Origins: Classes, com binários verificados.

Não foram testados todos os mods de terceiros, todos os sistemas operativos nem falhas arbitrárias de hardware. Na recuperação, um perfil ou inventário incompatível mantém o recibo e suspende as operações públicas; não há ferramenta que apague automaticamente a dívida. O relógio persistente evita recuar prazos já observados, mas não permite conhecer o tempo real que passou com o mundo fechado se o relógio do sistema tiver sido alterado. O histórico recente é limitado; os contratos permanecem no registo persistente.

O [relatório publicado](../mod-pronto/realm-treasury-0.4.0.build.json) identifica o commit exato, o run GitHub, as contagens, os hashes e os cenários de cliente. As imagens e os relatórios anteriores permanecem nas respetivas versões.
