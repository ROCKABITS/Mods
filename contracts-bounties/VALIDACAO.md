# Validação atual — Contracts & Bounties 0.3.2

**Aprovada.** A [execução conjunta 39009308013](https://github.com/ROCKABITS/Mods/actions/runs/39009308013), sobre o código [`0d9e459`](https://github.com/ROCKABITS/Mods/commit/0d9e45962ae298e4fac98f46c6c9b5823e0ffc8d), compilou o JAR de produção e aprovou **46 testes unitários** e **38 GameTests nativos em modo independente**, com Minecraft 1.21.1, NeoForge 21.1.256 e Java 21. Os [metadados desta compilação](../mod-pronto/contracts-bounties-0.3.2.build.json) registam as contagens, versões e hashes.

O JAR novo desta versão foi também carregado com as outras cinco versões novas dos companheiros, Origins, Jupiter e Realm Notifications 0.1.0 na integração de **15 GameTests do Realm Notifications**; estes não substituem nem repetem a bateria completa de cada companheiro em modo integrado. A ponte opcional foi exercitada com o payload real e com fallback de chat para clientes sem o canal.

A integração executou publicação com reserva real da recompensa, entrega de itens, conclusão com crédito retido e recolha para a carteira. Verificou ainda repetição sem novo pagamento e o fallback de chat para clientes sem o canal opcional.

O cliente Minecraft real aprovou o HUD, as preferências e o diário do Realm Notifications; as [capturas desta integração](../media/realm-notifications/0.1.0/README.md) mostram essa interface. Não houve nova validação gráfica da interface própria deste companheiro. As verificações e galerias das versões anteriores abaixo permanecem históricas.

## Histórico de validação

## Atualização 0.3.1 — transferências do Crown Ledger

[Execução aprovada 38751670912](https://github.com/ROCKABITS/Mods/actions/runs/38751670912), código `e6ec71d`: **46 testes unitários**, **38 testes nativos** e cliente Minecraft real aprovados. A API 2 acrescenta transferências exatas entre o tesouro e a conta dos contratos, restritas à identidade do fornecedor registado e ao thread principal. O novo teste rejeita fornecedores estranhos, transferências sem fundos, zero e operações fora do thread principal.

[JAR 0.3.1](../mod-pronto/contracts-bounties-0.3.1.jar) · [Proveniência](../mod-pronto/contracts-bounties-0.3.1.build.json).

# Validação desta entrega

## Versão 0.3.0: contratos preparados para a Tesouraria

[Execução 38747778023](https://github.com/ROCKABITS/Mods/actions/runs/38747778023), código `aa281b24ced8e15a50241eae4f19947fa64c0a2f`:

- Compilação e empacotamento aprovados com Java 21, Minecraft 1.21.1 e NeoForge 21.1.256.
- **46 testes unitários aprovados**, sem falhas, erros ou ignorados: 23 testes existentes e 23 novos testes de impostos, conta pública, progresso, limites e snapshots.
- **Todos os 37 testes obrigatórios de servidor aprovados**, incluindo os 25 anteriores dos blocos, inventários e pagamentos e 12 novos testes da atualização.
- Os novos testes verificam: sessões físicas, comandos, taxa guardada na publicação, conservação 20 → 19 + 1, reembolso integral, reputação mínima, depósitos com dinheiro real, sucessão do governante, autorização da conta pública, progresso pendente, limite diário, armazenamento v2 e migração de um NBT v1 construído com os campos originais.
- Os testes da Tesouraria usam um fornecedor de regras e autorização exclusivo de teste. Validam a API num servidor real; não representam um mod Tesouraria já desenvolvido.
- Cliente Minecraft real aprovado: geometria e sprites das três variantes e estados colocados, publicação bloqueada com pouca reputação, entregas/recolhas ainda disponíveis, fecho de sessão sem fechar ecrãs alheios e seis capturas de 1600 × 900.
- Os dois novos menus foram inspecionados: reputação atual/mínima, contratos públicos/particulares, total/imposto/líquido, e prévia de publicação com 20 esmeraldas a 5%.
- JAR de produção verificado sem fixtures de servidor ou cliente; download público confirmado e checksum coincidente.

SHA-256 do JAR 0.3.0: `74ef1869b280d3f5318f5f57d965e25f59fa8007c5ff422763debe9c9d781bef`.

[JAR](../mod-pronto/contracts-bounties-0.3.0.jar) · [ZIP](../mod-pronto/contracts-bounties-0.3.0.zip) · [Proveniência](../mod-pronto/contracts-bounties-0.3.0.build.json) · [Capturas](../media/0.3.0/README.md).

O adaptador de progresso foi ainda verificado isoladamente contra o **JAR original Crónicas RP 0.5.2**, confirmando assinaturas de métodos, limites e preservação dos campos dos records reais. Esta verificação não iniciou o mod completo: os caminhos de attachment e sincronização com Chronicles/Origins/Jupiter instalados continuam por validar em conjunto. Os GameTests desta execução correram em modo independente; os ramos condicionais que exigem o mod real não foram executados.

A tesouraria foi implementada no mod separado [Crown Ledger](../crown-ledger/README.md), cujo CI testa também os JARs reais de Contracts e Realm Council. Sem fornecedor registado, os contratos particulares funcionam com imposto 0% e reputação mínima 0. Os contratos existentes migram sem taxas ou reputação retroativas. O formato 2 exige cópia do mundo para regressar à 0.2.0.

## Histórico — preparação inicial e testes da base

- JDK Temurin 21 e Gradle 9.2.1 instalados com os checksums oficiais verificados.
- Gradle `--version` e `help`; resolução do plugin ModDevGradle 2.0.148.
- `python3 tools/test_domain.py`: **23 testes encontrados, 23 executados, 23 aprovados, zero ignorados**, com JDK 21 e JUnit 5.
- Testes cobrem reserva lógica, conclusão única, trabalhadores simultâneos, autorizações, reembolsos, recolhas parciais, moedas separadas, limites, overflow, snapshots e rotação de histórico sem perder créditos.
- 25 ficheiros JSON de recursos válidos e 69 traduções com as mesmas chaves em português e inglês.

## Versão 0.2.0: murais 3D aprovados

[Execução 38724104015](https://github.com/ROCKABITS/Mods/actions/runs/38724104015), código `22ef0e4`:

- Compilação e empacotamento aprovados.
- 23 testes unitários aprovados, sem falhas, erros ou testes ignorados.
- Todos os 25 testes obrigatórios de servidor aprovados. Incluem receitas, criativo/pesquisa, colocação real das três variantes nas quatro direções, rejeição de obstruções/teto do mundo, destruição de cada parte sem duplicação, colisões e interação pelo bloco.
- Cliente Minecraft real iniciado com as texturas do mod. Os três modelos de inventário e os estados válidos dos blocos colocados carregaram com geometria e sem sprites ausentes.
- Captura de 1600 × 900 produzida pelo cliente e inspecionada: frente dos murais, papéis, telhas e vegetação visíveis.
- JAR de produção verificado sem classes de GameTests ou de captura visual; publicado diretamente em `mod-pronto`.

[Descarregar JAR 0.2.0](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/contracts-bounties-0.2.0.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/contracts-bounties-0.2.0.zip) · [Captura real do cliente](../mod-pronto/contracts-boards-0.2.0.png).

SHA-256 do JAR: `ec6f0f790e552962661cbb8c3253f04898c0e37d007318577aa65954b52effbb`. A origem da compilação, checksums do ZIP/captura e contagens dos testes estão em `mod-pronto/contracts-bounties-0.2.0.build.json`.

A geometria e a colocação foram validadas em modo independente. A integração real com Crónicas RP/Origins e testes com vários jogadores continuam por executar.

## Histórico: correção 0.1.1 aprovada no GitHub

[Execução 38718980393](https://github.com/ROCKABITS/Mods/actions/runs/38718980393), código `f023faa`:

- Compilação e empacotamento aprovados.
- 23 testes unitários executados, sem falhas, erros ou testes ignorados.
- 17 testes obrigatórios de servidor iniciados e concluídos, todos aprovados.
- Novo teste `boardIsVisibleInCreativeInventoryAndSearch` constrói a aba registada e confirma o mural nas listas de itens visíveis e de pesquisa; cobre a falha observada na 0.1.0.
- Publicação do artefacto `contracts-bounties-0.1.1` aprovada. Contagens e resultados disponíveis nas anotações públicas desta execução.

[Descarregar a versão 0.1.1](https://github.com/ROCKABITS/Mods/actions/runs/38718980393/artifacts/11964257195), com sessão iniciada no GitHub. SHA-256 do ZIP, conforme metadados do GitHub: `534c7803bffd73ee2023504c75558bbc9db46b983eedda7106f5d94839cc6d9d`.

## Histórico: compilação 0.1.0 aprovada no GitHub

[Execução 38717364055](https://github.com/ROCKABITS/Mods/actions/runs/38717364055), código `5fc7e5b`:

- `build` com compilação Java, empacotamento e testes unitários: aprovado.
- `-PcontractGameTests runGameTestServer`: aprovado, com o mod instalado no servidor de testes e sem Crónicas RP.
- Carregamento do JAR para o artefacto `contracts-bounties-0.1.0`: aprovado.

[Descarregar o artefacto](https://github.com/ROCKABITS/Mods/actions/runs/38717364055/artifacts/11963405690) com sessão iniciada no GitHub. O ZIP contém o JAR instalável; os relatórios e logs estão no artefacto separado `contracts-validation-38717364055` da mesma execução. A vista pública confirma o sucesso dos passos, mas exige autenticação para os logs completos e contagens detalhadas dos GameTests.

SHA-256 do ZIP do artefacto, conforme metadados do GitHub: `028e5ef3fede7a43eea8fb7c556e5f79de6622cce6333142432c24d12f49233d`.

## Limitação do ambiente cloud

`compileJava test build` e a preparação dos GameTests param antes da compilação Java, ao obter `net.neoforged:neoform-runtime:2.0.31`: o proxy devolve HTTP 403 para `maven.neoforged.net`.

Também foi confirmada recusa de acesso a `libraries.minecraft.net`, `piston-meta.mojang.com` e `piston-data.mojang.com`. `resources.download.minecraft.net` é necessário para os recursos do cliente. Os cinco domínios constam da configuração guardada, mas o proxy desta sessão ainda recusa as ligações. A compilação aprovada foi executada no GitHub Actions; não estabelece que esta sessão cloud consiga compilar.

Não se desativou TLS, assinatura ou verificação de checksums para contornar este bloqueio.

## Validação adicional

1. Quando a rede desta sessão permitir os downloads, repetir `python3 tools/cloud_gradle.py build --console=plain` e `python3 tools/cloud_gradle.py -PcontractGameTests runGameTestServer --console=plain` para validar o ambiente cloud local.
2. Repetir os GameTests com `-PcontractsCompat` e os mods originais/dependências em `compat-libs/`, para testar a API real do Crónicas RP.
3. Testar a interface de contratos com dois jogadores e reiniciar um mundo com pagamentos e entregas pendentes. O carregamento/renderização dos modelos e as receitas já foram verificados automaticamente.

O adaptador de Crónicas RP foi escrito a partir do código-fonte fornecido, mas ainda não foi validado dentro do Minecraft com esse mod instalado. A aprovação dos testes de servidor e de modelos em modo independente não substitui a validação com o Crónicas RP instalado nem um teste da interface de contratos com vários jogadores.
