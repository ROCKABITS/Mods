# Validação — Realm Casino 0.3.0 Beta

A publicação conjunta com Crown Ledger 0.2.3 exige compilação, testes unitários, testes nativos independentes e com integrações, e execução do cliente Minecraft sobre o mesmo commit. Os JARs de produção são guardados antes de carregar fixtures e verificados antes de publicar.

- **82 testes JUnit:** incluem as 32768 combinações das slots de cinco rolos, as 24 ordens de chegada, avaliação de poker, blinds, aumentos, all-in, potes laterais, empates, conservação de fundos, capital e imposto.
- **75 testes nativos por modo:** doze blocos, crafting, criativo, proteção, persistência NBT, cartas privadas, operações com nonce/revisão, mesas financiadas, carteira Chronicles e recuperação do diário. Os casos opcionais só exercem a integração quando o respetivo mod está presente.
- **Cliente real:** doze modelos e itens, regras/jogo/proprietário, controlos a 320 × 240, apostas e recolhas por C2S/S2C, servidor integrado, animações, poker com cartas privadas e entradas diferentes e quatro mesas multiplayer.
- **Carteira do anfitrião:** comparar saldo e recibo em playerdata/UUID.dat e level.dat/Data/Player, carregando a tag real num jogador novo do servidor.
- **Crown real:** imposto de lucro, devolução de capital sem imposto, recibos, gravação em disco, falha de escrita e repetição sem duplicação.

As capturas usam o gerador real do servidor e a carteira Chronicles. O segundo participante das mesas é um ator de teste do servidor com carteira própria; **não são dois clientes autenticados**. As capturas Crown usam dados de demonstração no menu de produção, com permissões e transferências verificadas separadamente nos testes nativos. A validação Linux não cobre todas as combinações de mods nem todas as falhas de hardware.

Formato de dados 3, leitura de formatos 1/2 e protocolo 3. Atualiza clientes e servidor e guarda uma cópia conjunta do mundo, casino, tesouro e perfis para eventual regresso.

[Validação da 0.2.0](VALIDACAO-0.2.0.md) · [Proveniência](../mod-pronto/realm-casino-0.3.0.build.json).
