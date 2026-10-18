# Laws & Justice — 0.1.0 Beta

Sistema de prisões construídas pelos jogadores, com celas registadas, penas que contam apenas enquanto o condenado está online e libertação automática. Para **Minecraft 1.21.1, NeoForge 21.1.256 ou superior e Java 21**, em servidor dedicado ou mundo aberto por LAN.

Esta primeira versão inclui a administração prisional. A justiça exercida pelo Presidente, os mandados, as algemas, os trabalhos prisionais, as multas e as fianças pertencem às próximas versões. O [plano completo](PLANO-COMPLETO.md) conserva todas essas funcionalidades.

## Instalação

1. Instala Minecraft 1.21.1 com NeoForge e Java 21.
2. Coloca o JAR de Laws & Justice na pasta `mods` do servidor e de cada jogador. Num mundo LAN, instala-o também no cliente que abre o mundo.
3. Reinicia o jogo ou o servidor. Mantém a mesma versão do mod em todos os participantes.

O mod funciona sozinho. **Chronicles RP** é opcional para apresentar os nomes das personagens; sem ele, aparecem os nomes Minecraft. **Realm Notifications** é opcional para os avisos de condenação e libertação; sem ele, são usadas mensagens do próprio jogo. Esta versão não cobra dinheiro nem exige qualquer mod de economia.

## Blocos e ferramenta

| Objeto | Utilização na versão 0.1.0 |
| --- | --- |
| **Porta de Cela** | Porta de ferro com grades e duas metades. Um condenado não a pode abrir manualmente. |
| **Grades de Prisão** | Painel de ferro para construir as paredes e janelas da prisão. |
| **Fechadura Reforçada** | Bloco auxiliar que abre o livro de controlo. Nesta versão, não associa fechaduras a portas nem cria cargos ou chaves de acesso. |
| **Controlo da Prisão** | Abre a interface física para registar prisões e celas, administrar penas e consultar o histórico de custódia. |
| **Agrimensor Prisional** | Seleciona dois cantos e um ponto de referência no mundo. Só um administrador pode utilizar a seleção. |

Todos têm modelos 3D próprios, receitas e uma aba criativa **Laws & Justice**, com traduções em português e inglês. Os quatro blocos são recolhidos com uma picareta de pedra ou superior. A porta de duas metades deixa cair apenas um item.

A prisão é construída pelos jogadores com os blocos que escolherem. **O mod não gera edifícios, celas ou masmorras automaticamente.**

## Registar uma prisão

É necessário ser administrador com nível de permissão 2 ou superior. Num mundo local/LAN, ativa os comandos se precisares de obter essa permissão. Construir um bloco ou possuir o agrimensor não concede poderes administrativos.

1. **Constrói a prisão e as celas.** Deixa chão sólido e espaço suficiente para um jogador de pé nos pontos de chegada e de saída.
2. **Coloca um Controlo da Prisão ou uma Fechadura Reforçada.** Os menus são abertos ao interagir com estes blocos. Não existe uma tecla que abra a administração à distância.
3. **Seleciona a área exterior com o Agrimensor Prisional.** Clica com o botão direito num canto A e depois no canto oposto B. A seleção inclui os blocos dos dois cantos e toda a altura entre eles; deve conter as áreas onde os prisioneiros poderão circular. Um terceiro clique inicia uma nova seleção A/B.
4. **Marca a saída.** Fora da prisão, agacha-te e clica com o botão direito num bloco de chão seguro. O ponto de referência fica **um bloco acima do bloco clicado**, onde o jogador ficará de pé ao ser libertado. Todos os pontos devem pertencer à mesma dimensão.
5. **Abre o livro do bloco de controlo**, escolhe **Prisões**, cria uma prisão, dá-lhe um nome e guarda a seleção. O servidor verifica os limites, a sobreposição com outras prisões e o ponto de saída.

Os limites de cada área podem ter até 1 024 blocos por lado e um volume de 16 777 216 blocos. Estes são limites máximos de registo; não é necessário construir uma prisão grande.

## Registar uma cela

1. Com o agrimensor, seleciona novamente dois cantos, agora para a área da cela. A cela deve ficar totalmente dentro da prisão.
2. Agacha-te e clica no chão **dentro da cela** para definir o ponto de chegada. Garante que há espaço livre acima do ponto.
3. No livro de controlo, abre **Celas**, seleciona a prisão, cria a cela, escolhe o nome e a capacidade entre **1 e 16 jogadores**, e guarda a seleção.

As celas da mesma prisão não podem sobrepor-se. Cada prisão admite até 64 celas. O servidor impede a alteração ou remoção de uma prisão e das suas celas enquanto houver penas ativas nessa prisão.

A cela é o ponto de chegada e de regresso. Durante a pena, o jogador pode circular por **toda a área registada da prisão**; não fica limitado ao cubículo da cela.

## Aplicar e acompanhar uma pena

No separador **Penas**, um administrador escolhe um jogador online e vivo, a prisão, uma cela com vaga, a duração em minutos e o motivo. A duração pode ir de **1 a 43 200 minutos**. A decisão fica guardada e o jogador é enviado para a cela.

O jogador condenado pode consultar a sua própria pena ao interagir com o controlo ou com a fechadura. O menu apresenta a pena original, o tempo cumprido online e o tempo restante. As reduções de pena permanecem a zero nesta versão; o Trabalho Comunitário será acrescentado na versão 0.3.

- Desligar-se pausa a contagem; voltar a entrar retoma a pena guardada.
- Morrer, renascer, usar um portal ou teleportar não elimina a condenação. O servidor volta a colocar o jogador na prisão quando necessário.
- Fechar o menu não pausa a pena. A contagem continua enquanto o jogador estiver ligado e o servidor estiver a processar o mundo.
- A contagem usa os ticks do servidor: a 20 ticks por segundo, 1 200 ticks correspondem a um minuto. Uma pausa do mundo ou um servidor com atraso pode prolongar o tempo real necessário.
- Ao chegar a zero, a pena termina automaticamente e o jogador é enviado para o ponto de libertação. Se esse ponto deixar de ser utilizável, o servidor procura uma alternativa.

Durante a detenção, o servidor impede a destruição e colocação de blocos pelo condenado e limita interações que poderiam permitir uma fuga. A área da prisão continua a ser uma construção normal que os jogadores livres podem organizar; não é um sistema geral de proteção de terrenos ou de combate.

## Libertação administrativa e histórico

Um administrador pode selecionar uma pena ativa, introduzir um motivo e confirmar uma **libertação de emergência**. Se o condenado estiver offline, a libertação fica guardada e é entregue quando voltar a entrar.

O separador **Histórico** apresenta os registos recentes de aplicação de penas, cumprimento e libertação de emergência. Guarda o nome do jogador, a autoridade, o motivo, a data e os tempos da pena. Os dados persistem quando os jogadores saem do mundo. O histórico tem um limite de **2 000 decisões recentes**; não é ainda o sistema completo de processos e registos criminais da versão 0.4.

Nesta versão, **apenas administradores autorizam penas e libertações**. O Presidente do Realm Council ainda não recebe poderes judiciais. A integração prevista para a versão 0.2 usará o Presidente eleito e empossado, sem criar cargos adicionais de juiz, guarda ou chefe da prisão.

## Dados do mundo

Prisões, celas, penas, tempo cumprido, histórico e libertações pendentes ficam no ficheiro **`<mundo>/data/laws_justice.dat`**. Não são guardados no item da porta, no agrimensor ou apenas na memória do cliente.

As decisões administrativas são guardadas imediatamente. O progresso das penas recebe pontos de gravação periódicos e é gravado ao sair e ao encerrar normalmente o mundo. Não há contagem de tempo offline. Um encerramento abrupto pode perder os últimos ticks desde o ponto de gravação, tal como outras alterações recentes do mundo.

Inclui este ficheiro nas cópias de segurança do mundo. Se os dados estiverem danificados, o mod informa o erro em vez de substituir silenciosamente os registos por uma prisão vazia.

## Utilização sem comandos

As receitas, a aba criativa, o agrimensor e os menus físicos permitem preparar e usar o sistema. O comando administrativo opcional **`/justice blocks`** entrega os quatro blocos e o agrimensor para facilitar a preparação; não aplica penas nem abre um menu remoto.

## Próximas versões

| Versão | Funcionalidades previstas |
| --- | --- |
| **0.1.0** | Prisões e celas registadas, administração física, penas online e libertação automática. |
| **0.2.0** | Presidente como juiz, Mesa de Justiça, algemas e mandados físicos validados pelo servidor. |
| **0.3.0** | Trabalho Comunitário, zonas e postos de trabalho, entregas e redução de pena limitada. |
| **0.4.0** | Multas, fianças, processos, registos criminais e integrações adicionais. |

Os modelos e as texturas originais podem ser regenerados com `python3 tools/generate_assets.py`, usando Pillow. O código-fonte, as receitas e as traduções acompanham o projeto no repositório `ROCKABITS/Mods`.
