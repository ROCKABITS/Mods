# Validação desta entrega

## Executado e aprovado

- JDK Temurin 21 e Gradle 9.2.1 instalados com os checksums oficiais verificados.
- Gradle `--version` e `help`; resolução do plugin ModDevGradle 2.0.148.
- `python3 tools/test_domain.py`: **23 testes encontrados, 23 executados, 23 aprovados, zero ignorados**, com JDK 21 e JUnit 5.
- Testes cobrem reserva lógica, conclusão única, trabalhadores simultâneos, autorizações, reembolsos, recolhas parciais, moedas separadas, limites, overflow, snapshots e rotação de histórico sem perder créditos.
- Sete ficheiros JSON de recursos válidos e 65 traduções com as mesmas chaves em português e inglês.

## Bloqueado pela rede

`compileJava test build` e a preparação dos GameTests param antes da compilação Java, ao obter `net.neoforged:neoform-runtime:2.0.31`: o proxy devolve HTTP 403 para `maven.neoforged.net`.

Também foi confirmada recusa de acesso a `libraries.minecraft.net`, `piston-meta.mojang.com` e `piston-data.mojang.com`. `resources.download.minecraft.net` é necessário para os recursos do cliente. Os cinco domínios foram guardados no rascunho das definições do ambiente; guardar o rascunho não aplica nem publica a configuração.

Não se desativou TLS, assinatura ou verificação de checksums para contornar este bloqueio.

## Por executar depois de guardar as permissões de rede

1. `python3 tools/cloud_gradle.py build --console=plain`
2. `python3 tools/cloud_gradle.py -PcontractGameTests runGameTestServer --console=plain` — **16 GameTests preparados, ainda não executados**.
3. Repetir os GameTests com `-PcontractsCompat` e os mods originais/dependências em `compat-libs/`, para testar a API real do Crónicas RP.
4. Abrir cliente, verificar interface e receita, e testar dois jogadores e reinício do mundo com pagamentos e entregas pendentes.

O adaptador de Crónicas RP foi escrito a partir do código-fonte fornecido, mas ainda não foi validado dentro do Minecraft. Não há JAR instalável nesta entrega nem confirmação de funcionamento no jogo.
