# Validação desta entrega

## Executado e aprovado

- JDK Temurin 21 e Gradle 9.2.1 instalados com os checksums oficiais verificados.
- Gradle `--version` e `help`; resolução do plugin ModDevGradle 2.0.148.
- `python3 tools/test_domain.py`: **23 testes encontrados, 23 executados, 23 aprovados, zero ignorados**, com JDK 21 e JUnit 5.
- Testes cobrem reserva lógica, conclusão única, trabalhadores simultâneos, autorizações, reembolsos, recolhas parciais, moedas separadas, limites, overflow, snapshots e rotação de histórico sem perder créditos.
- Sete ficheiros JSON de recursos válidos e 65 traduções com as mesmas chaves em português e inglês.

## Compilação no GitHub aprovada

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
3. Abrir cliente, verificar interface e receita, e testar dois jogadores e reinício do mundo com pagamentos e entregas pendentes.

O adaptador de Crónicas RP foi escrito a partir do código-fonte fornecido, mas ainda não foi validado dentro do Minecraft com esse mod instalado. A aprovação dos testes de servidor em modo independente não substitui essa validação nem um teste visual do cliente.
