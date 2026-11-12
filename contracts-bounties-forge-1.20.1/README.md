# Contracts & Bounties — Minecraft 1.20.1 / Forge

Port da edição 0.4.1 para **Minecraft 1.20.1, Forge 47.4.0 ou posterior da série 47 e Java 17**. Instala o mesmo JAR no servidor e em todos os clientes. O identificador continua a ser `contracts`.

[Descarregar JAR](../mod-pronto/contracts-bounties-0.4.1-forge-1.20.1.jar) · [Pacote ZIP](../mod-pronto/contracts-bounties-0.4.1-forge-1.20.1.zip) · [Validação](VALIDACAO.md)

Inclui os três murais físicos, contratos com reserva de esmeraldas, cancelamento e reembolsos, recolha de materiais, catálogo visual de itens com pesquisa por nome e ícone, cinco missões diárias, histórico, administração e celebrações no HUD. A pesquisa por ID, namespace ou `#tag` continua disponível como opção.

Nesta edição independente, as missões diárias usam **esmeraldas por defeito** (`emeraldFallback: true`). Os valores internos usam centésimos: 100 unidades internas equivalem a uma esmeralda. As recompensas são arredondadas a esmeraldas inteiras e a interface apresenta a quantidade de esmeraldas. As missões deste modo não atribuem reputação de uma personagem Realm Origins.

Abre um mural para aceder aos contratos e às missões. Os murais têm receitas; um operador também pode usar `/contracts boards` para obter as três variantes. As configurações e os modelos personalizados das missões ficam em `config/contracts-daily-quests/`; o estado e os recibos ficam nos dados do mundo.

## Integrações e mundos

O Realm Origins, Realm Treasury, Realm Council, Realm Admin e Realm Notifications publicados neste repositório são atualmente para 1.21.1/NeoForge. Esses JARs não funcionam neste port. Carteira digital e reputação estão indisponíveis; impostos, contratos públicos e autoridade do Council dependem de ports e integrações próprios, ainda não publicados. A API de contratos públicos e o fornecedor de permissões mantêm-se disponíveis para futuras integrações Forge, mas não representam compatibilidade já validada com esses mods.

A edição [1.21.1/NeoForge](../contracts-bounties/README.md) continua num projeto separado. Usa apenas uma edição de `contracts` por instalação. Este port destina-se a mundos 1.20.1; não faz downgrade nem migração de mundos 1.21.1.

## Desenvolvimento

O código Java específico desta versão está neste projeto. Arte, traduções e catálogo de missões vêm de `../contracts-bounties/src/main/resources`: o build converte as pastas de dados e os resultados de receitas para o formato 1.20.1. Mantém os dois projetos lado a lado ao compilar a partir de um checkout do repositório.

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew -PboardVisualTests runClient
./gradlew -PforgeIntegrationTests runClient
```

Os testes de cliente precisam de um ambiente gráfico; em CI é usado Xvfb com renderização por software. Os testes vivem em source sets separados e não entram no JAR distribuído. O build final aplica a reofuscação necessária ao Forge.

Ao corrigir lógica comum, revê também a edição 1.21.1 e executa os testes das duas versões. Alterações de eventos, rede, NBT e registos devem respeitar a API de cada plataforma. Não alargues apenas os intervalos de versões nos metadados.
