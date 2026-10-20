# Validação — Realm Notifications 0.1.1 Beta

Correção da fila de notificações em modpacks. O mod continua a usar a API e o protocolo 1 e não altera recompensas, carteiras nem dados do mundo.

## Verificação local

- 18 testes JUnit aprovados, sem falhas nem testes ignorados: 11 existentes e sete novos para rajadas, pausa entre rajadas, emissão contínua, limpeza de sessão e prazo de expiração.
- 15 GameTests aprovados sem companheiros e 15 com onze JARs verificados por SHA-256. Os casos dependentes de outros mods só exercitam essa integração na segunda execução.
- Integração com Chronicles RP 0.5.4, Contracts & Bounties 0.3.2, Realm Council 0.2.2, Crown Ledger 0.2.5, Realm Markets 0.2.1, Realm Rewards 0.1.2, Realm Identity 0.1.1, Realm Titles 0.1.0, Laws & Justice 0.4.0, Realm Casino 0.3.0 e Realm Economy 0.4.0, mais Origins e Jupiter reais.
- Os testes financeiros executam pagamentos e contratos reais e confirmam que falhas de apresentação não repetem nem perdem dinheiro.
- Cliente Minecraft real com mundo integrado e transporte S2C: 200 chamadas da API produzem um cartão, no máximo um som e as últimas 100 mensagens originais no diário.
- Verificação da expiração de cartões antigos através do método de produção com um instante futuro; não depende de esperar 30 segundos.
- Avisos repetidos intercalados, mudança de sessão, cinco posições, controlos em 320 × 240, preferências e deslocação do diário verificados no cliente real.
- Cinco capturas 1600 × 900, incluindo o resumo de 200 avisos, revistas visualmente.

## Limites

A rajada é uma reprodução controlada do problema, com textos de demonstração. Ainda não foi identificado o emissor exato do modpack do utilizador, nem executado o seu conjunto completo de mods. A correção protege a apresentação de qualquer mod que utilize a API; não desativa conquistas ou pagamentos. O diário guarda apenas as últimas 100 entradas da sessão, quando está ativo.

Não foi realizada uma sessão com dois clientes autenticados. Os GameTests simulam o suporte do canal; o ensaio visual utiliza um cliente real com servidor integrado.

[Validação histórica da 0.1.0](VALIDACAO-0.1.0.md).
