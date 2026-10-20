# Validação — Realm Notifications 0.1.1 Beta

Correção da fila de notificações em modpacks. O mod continua a usar a API e o protocolo 1 e não altera recompensas, carteiras nem dados do mundo.

## Publicação

A [execução 39436047070](https://github.com/ROCKABITS/Mods/actions/runs/39436047070) terminou com sucesso em 20 de outubro de 2026, sobre o código [`a64cd02`](https://github.com/ROCKABITS/Mods/commit/a64cd023f02d8e35ebb80fe786ec05c341da3f5a). O GitHub Actions repetiu a compilação, os 18 testes JUnit, os dois modos de 15 GameTests e o ensaio real de 200 avisos no cliente antes de publicar os ficheiros.

[Descarregar JAR](../mod-pronto/realm-notifications-0.1.1.jar) · [ZIP](../mod-pronto/realm-notifications-0.1.1.zip) · [Proveniência](../mod-pronto/realm-notifications-0.1.1.build.json) · [Capturas e relatório do cliente](../media/realm-notifications/0.1.1/README.md).

## Verificação local e no CI

- 18 testes JUnit aprovados, sem falhas nem testes ignorados: 11 existentes e sete novos para rajadas, pausa entre rajadas, emissão contínua, limpeza de sessão e prazo de expiração.
- 15 GameTests aprovados sem companheiros e 15 com onze JARs verificados por SHA-256. Os casos dependentes de outros mods só exercitam essa integração na segunda execução.
- Carregamento conjunto com Chronicles RP 0.5.4, Contracts & Bounties 0.3.2, Realm Council 0.2.2, Crown Ledger 0.2.5, Realm Markets 0.2.1, Realm Rewards 0.1.2, Realm Identity 0.1.1, Realm Titles 0.1.0, Laws & Justice 0.4.0, Realm Casino 0.3.0 e Realm Economy 0.4.0, mais Origins e Jupiter reais.
- Os testes financeiros executam pagamentos e contratos reais e confirmam que falhas de apresentação não repetem nem perdem dinheiro.
- Cliente Minecraft real com mundo integrado e transporte S2C: 200 chamadas da API produzem um cartão, no máximo um som e as últimas 100 mensagens originais no diário.
- Verificação da expiração de cartões antigos através do método de produção com um instante futuro; não depende de esperar 30 segundos.
- Avisos repetidos intercalados, mudança de sessão, cinco posições, controlos em 320 × 240, preferências e deslocação do diário verificados no cliente real.
- Cinco capturas 1600 × 900, incluindo o resumo de 200 avisos, revistas visualmente.

## Limites

A rajada é uma reprodução controlada do problema, com textos de demonstração. Ainda não foi identificado o emissor exato do modpack do utilizador, nem executado o seu conjunto completo de mods. A correção protege a apresentação de qualquer mod que utilize a API; não desativa conquistas ou pagamentos. O diário guarda apenas as últimas 100 entradas da sessão, quando está ativo.

Não foi realizada uma sessão com dois clientes autenticados. Os GameTests simulam o suporte do canal; o ensaio visual utiliza um cliente real com servidor integrado.

[Validação histórica da 0.1.0](VALIDACAO-0.1.0.md).

## Ficheiros verificados depois da publicação

- `realm-notifications-0.1.1.jar`: 54,956 bytes; SHA-256 `2fb85c87eb296e49f3f68c0d7defd709ceca8913fa5a3f6ef9442d9b9548c99e`.
- `realm-notifications-0.1.1.zip`: 54,764 bytes; SHA-256 `eda059630dd2e7ee1f6764468881348baea0a64ba4b1a5a045a0a88caacf84b4`.

Os dois downloads públicos foram lidos novamente e comparados com os hashes da proveniência. O ZIP contém exatamente o JAR publicado; o JAR declara 0.1.1 e exclui testes e dependências externas. As cinco capturas publicadas também coincidem com os hashes registados.
