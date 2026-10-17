# Validação atual — Realm Rewards 0.1.2

**Aprovada.** A [execução conjunta 39009308013](https://github.com/ROCKABITS/Mods/actions/runs/39009308013), sobre o código [`0d9e459`](https://github.com/ROCKABITS/Mods/commit/0d9e45962ae298e4fac98f46c6c9b5823e0ffc8d), compilou o JAR de produção e aprovou **12 testes unitários** e **17 GameTests nativos em modo independente**, com Minecraft 1.21.1, NeoForge 21.1.256 e Java 21. Os [metadados desta compilação](../mod-pronto/realm-rewards-0.1.2.build.json) registam as contagens, versões e hashes.

O JAR novo desta versão foi também carregado com as outras cinco versões novas dos companheiros, Origins, Jupiter e Realm Notifications 0.1.0 na integração de **15 GameTests do Realm Notifications**; estes não substituem nem repetem a bateria completa de cada companheiro em modo integrado. A ponte opcional foi exercitada com o payload real e com fallback de chat para clientes sem o canal.

A integração concluiu conquistas Vanilla pelo `PlayerAdvancements` real, verificou pagamentos na carteira, repetição sem pagamento duplicado, liquidação conjunta de recompensas pendentes e preservação do pagamento com fallback de chat quando o canal falha.

O cliente Minecraft real aprovou o HUD, as preferências e o diário do Realm Notifications; as [capturas desta integração](../media/realm-notifications/0.1.0/README.md) mostram essa interface. Não houve nova validação gráfica da interface própria deste companheiro. As verificações e galerias das versões anteriores abaixo permanecem históricas.

## Histórico de validação

# Validação — Realm Rewards 0.1.1

**Estado: aprovado.** A [execução 38989952338](https://github.com/ROCKABITS/Mods/actions/runs/38989952338), sobre o commit `44e9a9a75b4cf2ade84b803328221a55adf12ecf`, compilou e publicou esta versão em 2 min 8 s. O commit de publicação é `69e1148`.

Ambiente: Minecraft **1.21.1**, NeoForge **21.1.256**, Java **21**.

| Verificação | Resultado |
| --- | --- |
| Compilação de produção | Aprovada |
| Testes unitários | 12 aprovados, sem falhas nem testes ignorados |
| GameTests sem Chronicles | 15 obrigatórios aprovados |
| GameTests com a carteira real e os companheiros | Os mesmos 15 obrigatórios aprovados |
| Cliente Minecraft | Duas páginas do menu de produção, pedidos dos botões, pesquisa e navegação a 320 × 240 aprovados |
| Distribuição | Versão correta, JAR de produção preservado durante os testes e ausência de classes/estruturas de teste ou dependências incorporadas |

São 15 casos nativos em dois modos, num total de 30 execuções. Os casos económicos verificam a ausência de carteira no primeiro modo e os pagamentos reais no segundo; não são 30 testes diferentes.

## Conquistas e pagamentos

Os testes usam o verdadeiro `PlayerAdvancements` do Minecraft, incluindo atribuição e revogação de critérios, com uma conquista Vanilla e avanços de um datapack de teste noutro namespace. Exercitam tarefas, objetivos, desafios e um avanço sem apresentação com dois critérios. Um avanço incompleto não paga; o avanço interno só fica concluído no critério final. Revogar e voltar a concluir não duplica a recompensa. O registo de conquistas antigas, as permissões atuais de operador, a pesquisa e a persistência NBT também passaram.

O NeoForge 1.21.1 só dispara `AdvancementEarnEvent` para avanços com apresentação. Para receitas e avanços internos, o mod trata o evento normal do último critério concedido. A proteção por UUID e ID aplica-se aos dois percursos.

O segundo modo carregou os JARs reais de **Chronicles RP 0.5.3**, **Origins 0.3.2**, **Jupiter 2.3.7**, **Contracts & Bounties 0.3.1**, **Realm Council 0.2.1**, **Crown Ledger 0.2.0**, **Realm Identity 0.1.0** e **Realm Markets 0.2.0**. Os hashes dos seis mods do repositório estão na [proveniência](../mod-pronto/realm-rewards-0.1.1.build.json). O workflow verificou também os downloads fixados de Origins e Jupiter contra os SHA-256 registados na validação anterior do Chronicles.

Foram executados créditos reais no perfil do Chronicles, verificação dos restantes campos do perfil, rejeição de pagamentos repetidos e conservação exata de uma recompensa parcialmente paga quando o saldo atinge o limite. Outro teste manteve um jogador de teste na lista real do servidor durante 105 ticks: depois de criar o perfil, o processamento periódico pagou a quantia pendente automaticamente. Os marcadores obrigatórios `REWARDS_CHRONICLES_PAYMENTS_PASSED` e `REWARDS_AUTOMATIC_RETRY_PASSED` só são emitidos depois dessas verificações.

## Interface e limites

As [capturas](../media/realm-rewards/0.1.1/README.md) são do cliente Minecraft e do ecrã de produção, com dados de demonstração. Os pedidos dos widgets foram capturados localmente no teste visual; os pagamentos reais foram verificados separadamente nos GameTests. A revisão visual confirmou os rótulos legíveis, corrigindo o desfoque observado na 0.1.0.

Os testes nativos usam jogadores/transportes de teste e não substituem um ensaio manual com dois clientes autenticados e reinício do servidor. A persistência foi verificada por gravação e leitura NBT. A carteira e o registo continuam a usar os ficheiros normais do Minecraft, sem uma transação de disco única durante uma falha abrupta. Carregar os outros mods confirma a compatibilidade observada deste percurso, sem certificar todos os seus restantes sistemas.

Os 12 testes independentes do domínio passaram também localmente. A compilação Minecraft nesta máquina cloud foi bloqueada por HTTP 403 no Maven NeoForge antes de compilar Java; o sucesso do GitHub Actions não comprova que a rede local tenha sido desbloqueada. Não foram desativadas verificações TLS ou de checksums.

## Distribuição

| Ficheiro | Tamanho | SHA-256 |
| --- | --- | --- |
| [JAR](../mod-pronto/realm-rewards-0.1.1.jar) | 42 848 bytes | `a3d93234c247f1b6b1bc4edb97da38cc33e47470620c20055315ce76915eb5ee` |
| [ZIP](../mod-pronto/realm-rewards-0.1.1.zip) | 42 347 bytes | `cb018d66311b570f1676fe3017395ae4e3c15b8a304481afbd676f9a111b9f1e` |

O download público foi comparado com estes hashes e o ZIP contém o mesmo JAR verificado. Instala a mesma versão no servidor e nos clientes. As dependências externas não estão incorporadas no JAR.

A [execução 38989557963 da 0.1.0](https://github.com/ROCKABITS/Mods/actions/runs/38989557963) passou os mesmos 12 testes unitários e 15 testes nativos em cada modo. Os [artefactos e hashes anteriores](../mod-pronto/realm-rewards-0.1.0.build.json) continuam preservados; a 0.1.1 corrige apenas a apresentação do menu, mantendo o formato do registo e o protocolo.
