# Validação — Realm Economy 0.5.1

Correção da geometria declarada dos ecrãs ao Minecraft, usada pelo JEI para posicionar a lista de itens. O problema da captura corresponde ao **Cofre da Colónia do Realm Economy**, cuja interface mostra «Municipal Ledger».

## Causa e alteração

`ColonyScreen`, `TradingScreen` e `ServicesScreen` desenhavam painéis com coordenadas próprias (`x`, `y`, `w`, `h`) sem atualizar `leftPos`, `topPos`, `imageWidth` e `imageHeight` herdados de `AbstractContainerScreen`. O JEI via um inventário pequeno e considerava livre parte do painel desenhado.

Os quatro campos passam a acompanhar o painel em cada inicialização, mudança de separador e redimensionamento. A correção usa a geometria nativa do Minecraft; não acrescenta uma dependência de JEI ao JAR de produção.

## Verificação

- Build e testes JUnit existentes do Realm Economy com Java 21, Minecraft 1.21.1 e NeoForge 21.1.256.
- Suite nativa de servidor independente, sem alterações às regras financeiras.
- Cliente Minecraft real com **JEI 19.70.0.469**, num mundo integrado.
- Controlo positivo: o inventário Vanilla mostra efetivamente a lista do JEI antes do teste.
- Controlo de reprodução: o ensaio restaura temporariamente os antigos limites de 176 × 166 sobre o painel atual e verifica ingredientes reais do JEI por cima da área desenhada. Esta captura é uma simulação dos metadados antigos, não uma execução do JAR 0.5.0.
- Depois de reinicializar o ecrã com a correção, o `IScreenHelper` real do JEI informa exatamente a posição e o tamanho pintados. A lista deixa de aparecer por cima dos painéis do cofre, comércio e serviços.
- Com espaço lateral, o JEI volta a apresentar os ingredientes ao lado do painel. O teste percorre a área interior com a API real de ingrediente sob o rato e confirma que não há ingredientes do JEI sobre o painel.
- Depois de redimensionar e fechar os ecrãs do Realm, o inventário Vanilla continua a apresentar o JEI. Nenhuma preferência global do JEI é alterada.

O workflow [Build Realm Economy GUI patch](../.github/workflows/build-realm-economy-gui.yml) repete a compilação, a suite nativa e o ensaio JEI antes de publicar JAR, ZIP, proveniência e [oito capturas](../media/realm-economy/0.5.1/README.md). A proveniência em `mod-pronto/realm-economy-0.5.1.build.json` identifica o commit, a execução e os totais efetivamente publicados.

## Limites

Os valores e menus financeiros do ensaio gráfico são snapshots de apresentação, com os pedidos financeiros desativados. Não se transferiu dinheiro. O JEI, o cliente, o mundo integrado, a API de geometria, os ingredientes e as verificações de sobreposição são reais.

Esta revisão não repetiu a integração financeira com Realm Origins/Realm Treasury, a criação e gestão reais de colónias MineColonies, os seis addons, o cenário TCP LAN nem dois clientes autenticados. As funcionalidades e validações anteriores mantêm-se documentadas em [VALIDACAO-0.5.0.md](VALIDACAO-0.5.0.md). Foi testada a versão de JEI indicada; o mecanismo corrigido é a interface nativa de limites de contentores, sem usar APIs privadas do JEI.
