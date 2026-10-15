# Validação desta entrega

## Executado e aprovado

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
