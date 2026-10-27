# Validação — Realm Economy 0.5.0

Plataforma base: Minecraft 1.21.1, NeoForge 21.1.256 e Java 21. O cenário com os seis addons indicados pelo utilizador usa **NeoForge 21.1.262**, exigido pelo MapColonies 1.0.0.

Estado: **validação local e publicação no GitHub aprovadas**. As 223 execuções de lógica/servidor, o cliente económico e os dois clientes TCP LAN passaram no [workflow 40548960095](https://github.com/ROCKABITS/Mods/actions/runs/40548960095). O JAR, o ZIP e as 48 capturas estão publicados.

Código validado: `82f410a78fbef0f9bcecb24b2af01c5569f62b26`. JAR de produção SHA-256: `f1dbb1ae42caa1a6982a2e27843baf3776f0c5b146bf415a6290c097883ed439`. O artefacto publicado é idêntico ao snapshot de produção validado localmente. [Metadados da compilação](../mod-pronto/realm-economy-0.5.0.build.json) · [Galeria publicada](../media/realm-economy/0.5.0/README.md).

| Suite | Execuções | Evidência |
| --- | ---: | --- |
| JUnit: preços, serviços e domínio financeiro colonial | 45 | `build.log`, `build/test-results/test/TEST-*.xml` |
| Regressão nativa sem os mods opcionais | 62 | `gametests.log` |
| Regressão nativa com Realm Origins, Realm Treasury e os outros companheiros | 62 | `integration.log` |
| MineColonies: contas, carteira, permissões, exportações, autorizações e recuperação | 27 | `colony-native.log` |
| Mesma suite com os seis addons e dependências reais | 27 | `colony-addons.log` |

Total das suites de lógica/servidor: **223 execuções**, sem contar os cenários dos clientes.

## O que a integração nativa verifica

- Proprietário sem OP deposita e levanta dinheiro da carteira real do Realm Origins, conservando o montante total.
- Um membro pode contribuir, mas não retirar fundos; um oficial autorizado utiliza a conta; uma pessoa de fora não pode abrir o menu.
- Alterações de proprietário/membro invalidam a autoridade anterior. Nonces repetidos, campos financeiros extra, montantes negativos/fracionários/fora dos limites e pedidos após partir o cofre são recusados.
- Uma exportação consome os itens exatos e credita a colónia. Não paga à carteira pessoal, não aceita produtos em falta ou com nome especial, nem repete a entrega.
- Os dados sobrevivem à leitura do ficheiro NBT comprimido. Uma conta em crescimento com mais de 65 535 bytes continua válida através da codificação por byte array.
- Um `WorkOrderBuilding` real, transformado pelo mixin de produção, aguarda a autorização paga. O orçamento é debitado uma vez; o identificador e a aprovação mantêm-se na serialização nativa da ordem.
- Cancelar uma ordem por pagar impede a cobrança; repetir uma obra com autorização já paga reutiliza a aprovação. Preço zero deixa os construtores trabalhar normalmente.
- O diálogo nativo `RecruitmentInteraction` não recruta sem autorização; depois do pagamento cria o colono e consome os materiais de recrutamento nativos.
- Um alquimista nativo construído e com trabalhador prepara efetivamente uma poção de água em poção estranha através de 405 chamadas do tick nativo do suporte, consumindo verruga e combustível sem cobrar a carteira. Suportes não registados na mesma colónia permanecem bloqueados pelo serviço pago; oficinas vazias ou por construir não concedem exceção.
- Apagar uma colónia financiada é recusado; a migração preserva os serviços do formato 2.
- Falha na gravação do jogador depois da intenção: recarrega o ficheiro anterior real e recupera o levantamento uma vez.
- Falha na substituição final do ficheiro económico depois de guardar carteira/recibo: restaura a intenção, recarrega e confirma o crédito sem o repetir.

Os atores da suite de servidor são subclasses de `ServerPlayer` com ligações de teste que capturam pacotes e ficheiros nativos. A suite do cliente acrescenta duas instâncias Minecraft realmente ligadas por TCP LAN.

## Clientes reais

A suite económica anterior volta a executar as compras/vendas, livros Mending/Sharpness/múltiplos encantamentos e lore, descrições nativas, menu compacto, preços/limites, bigorna, encantamentos sem XP, ferraria, poções com espera real, beacon financiado/expiração e receitas dos dois ficheiros do anfitrião.

A suite colonial cria um mundo de demonstração e abre-o à LAN. O proprietário e o convidado começam sem OP. O cenário cobre depósito do dono, depósito do membro, recusa de levantamento no menu do membro, consumo de uma exportação, duas autorizações, levantamento do proprietário, registos e layouts a 320×240, 480×270 e 800×450. O anfitrião verifica carteira, inventário e recibo tanto em `UUID.dat` como em `level.dat/Data/Player`.

O mundo é efetivamente encerrado e reaberto. Os saldos e a entrega mantêm-se; o cofre original é removido e a mesma conta abre através de outro cofre. Depois, a administração é concedida explicitamente ao anfitrião de teste para validar os controlos de configuração, limites, confirmação, autor da auditoria e conservação dos valores das autorizações anteriores.

A validação confirma os ganchos nativos de autorização e as operações financeiras; o cenário não executa um ciclo completo de produção pelos NPCs nem termina uma casa de cada estilo.

Os relatórios ficam em `qa-results/economy-client/result.json` e `qa-results/colony-client/result.json`, com os pedidos C2S, versões carregadas e capturas reais. A galeria publicada conserva estes relatórios. As identidades LAN são de teste offline, não duas contas Microsoft autenticadas. O cenário fornece fundos, materiais e uma colónia de demonstração; as operações financeiras e os menus usam o código de produção e os ficheiros reais.

## Dependências e limites da conclusão

As versões dos seis addons estão no [guia](MINECOLONIES.md#addons-indicados-pelo-utilizador). A suite carrega todos, mas não constrói cada esquema de cada pack. A apresentação das fronteiras do MapColonies com versões arbitrárias do Xaero não está abrangida. Não existe um mod Realm Admin executável neste repositório; o contrato de fornecedor de permissões tem testes nativos próprios.

Os limites antiabuso verificam ações e itens no servidor. Não existe uma proveniência universal retroativa para recursos criados em criativo/comandos e depois transferidos para sobrevivência. As autorizações abrangem as ordens nativas de construção/melhoria e o recrutamento normal de visitantes; sistemas paralelos de outros addons exigem adaptação própria. O progresso dos NPCs mantém a gravação nativa do MineColonies.

O JAR de produção é compilado e guardado antes das suites de teste. O empacotamento recusa fixtures, estruturas GameTest e bibliotecas de terceiros, verifica os hashes dos companheiros e exige todos os relatórios aprovados. O hash e o commit de origem publicados ficam em `mod-pronto/realm-economy-0.5.0.build.json` e no ficheiro `.sha256`.

## Reproduzir

```sh
./gradlew build --console=plain
./gradlew -PeconomyGameTests runGameTestServer --console=plain
python3 ../.github/scripts/economy_release.py install
./gradlew -PeconomyGameTests -PintegrationTests runGameTestServer --console=plain
./gradlew -PcolonyGameTests -PminecoloniesTests -PintegrationTests runGameTestServer --console=plain
python3 ../.github/scripts/colony_validation.py install
./gradlew -Pneo_version=21.1.262 -PcolonyGameTests -PminecoloniesTests -PcolonyAddonsTests -PintegrationTests runGameTestServer --console=plain
./gradlew -PeconomyVisualTests -PintegrationTests runClient --console=plain
./gradlew -Pneo_version=21.1.262 -PcolonyVisualTests -PminecoloniesTests -PcolonyAddonsTests -PintegrationTests runClient --console=plain
```

O instalador dos companheiros requer um snapshot de produção do commit em validação; o workflow [Build Realm Economy](../.github/workflows/build-realm-economy.yml) prepara esse estado e fixa os binários. No ambiente cloud, usa `python3 tools/cloud_gradle.py` em vez do comando direto `./gradlew`, preservando o proxy e o CA da sessão.
