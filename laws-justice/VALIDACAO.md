# Validação — Laws & Justice 0.2.0 Beta

A fase dois foi compilada e validada localmente com Java 21, Minecraft 1.21.1 e NeoForge 21.1.256. A publicação do JAR fica condicionada à repetição dos testes no GitHub Actions; a execução e os checksums serão registados depois dessa publicação.

| Verificação local | Resultado |
| --- | ---: |
| Testes unitários | 85, sem falhas ou ignorados |
| GameTests independentes | 73 |
| GameTests com dez mods companheiros | 73 |
| Cliente Minecraft real integrado | Aprovado, 20 capturas |

A [validação arquivada da 0.1](VALIDACAO-0.1.0.md) conserva a proveniência da base publicada.

## Tribunal, autoridade e segurança

O servidor verificou mandatos reais do Realm Council: Presidente eleito e empossado, ausência de Presidente, mandato futuro, expirado ou sem juramento, dados indisponíveis e troca imediata de titular com um menu aberto. As intervenções administrativas ficam identificadas como ADMIN_EMERGENCY. Não foram criados cargos adicionais.

As provas ficam limitadas às partes e à autoridade judicial atual. Uma audiência é obrigatória; absolvições e condenações não podem ser reaplicadas. Uma condenação e a respetiva pena são guardadas na mesma substituição atómica do ficheiro. Falhas reais de escrita e tentativas de guardar apenas metade da decisão são rejeitadas. Um ficheiro da fase um migra sem reiniciar a pena.

Os testes usam os itens físicos de mandado e algemas: UUIDs inventados, nomes falsificados, outro acusado, validade futura/expirada, revogação, cópias executadas, distância, ausência de algemas, modo criativo e autoalgemagem são rejeitados. Uma detenção válida reduz a velocidade sem alterar o modo de jogo ou as capacidades. A saída do acompanhante, a expiração e a libertação autorizada removem os efeitos; o mandado continua consumido.

A abertura de outro menu de justiça encerra a sessão anterior, evitando que atualizações periódicas substituam o ecrã atual. Sessões físicas, tokens, versões, limites, persistência, libertação e contenção da fase um continuam cobertos.

## Cliente real e imagens

O cliente carregou os modelos e texturas dos blocos da prisão, da Mesa de Justiça, das algemas e do mandado. A Mesa de Justiça tem 141 elementos originais; as algemas têm 64 e o mandado 25. Os recursos da fase um foram conservados. Os menus e o documento passaram nas verificações de limites e scroll a 320 × 240.

Os botões reais e o transporte C2S/S2C registaram prisão, cela, pena, acusação, depoimento, audiência, mandado e absolvição. O anfitrião sem poderes de operador recebeu autoridade através de um mandato empossado real; substituí-lo retirou o acesso judicial. O pergaminho foi entregue no inventário, colocado na mão e usado através da interação normal do cliente, sendo validado pelo servidor. As capturas do tribunal verificam também o separador realmente mostrado. Não foram injetados snapshots artificiais.

A pena conservou o mesmo identificador após teleporte, morte/respawn e fecho/reabertura real do mundo LAN. O tempo offline não contou. A libertação ocorreu pelo relógio normal do servidor, sem avanço manual, com uma única decisão COMPLETED e teleporte para a saída definida.

A prisão, personagem, mandato e processo usados nas imagens são demonstrações de teste. O mod não gera prisões. Esta validação usa um cliente integrado e não estabelece uma sessão autenticada entre dois clientes remotos.

## Limites desta fase

Trabalhos e zonas prisionais pertencem à fase 0.3; multas, fianças e integrações financeiras à fase 0.4. O desenvolvimento continua segundo o [plano completo](PLANO-COMPLETO.md). A fechadura permanece um bloco de controlo auxiliar; não associa chaves a portas.

Foram testadas as versões publicadas de Chronicles RP 0.5.4, Contracts & Bounties 0.3.2, Realm Council 0.2.2, Crown Ledger 0.2.1, Realm Markets 0.2.1, Realm Rewards 0.1.2, Realm Notifications 0.1.0, Realm Titles 0.1.0, Realm Identity 0.1.1 e Realm Casino 0.1.0, com dependências Origins/Jupiter reais verificadas por SHA-256. Não foi testado Windows nem todas as combinações de mods. Conserva cópias de segurança do mundo; um encerramento abrupto pode perder os últimos ticks desde o checkpoint.
