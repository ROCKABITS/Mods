# Laws & Justice — 0.4.0 Beta

Sistema de prisões construídas pelos jogadores, com celas registadas, penas que contam apenas enquanto o condenado está online e libertação automática. Para **Minecraft 1.21.1, NeoForge 21.1.256 ou superior e Java 21**, em servidor dedicado ou mundo aberto por LAN.

Inclui prisões, processos, provas, audiências, decisões do Presidente, mandados físicos, algemas, seis trabalhos prisionais, multas, fianças e um Livro de Registos público. O [plano completo](PLANO-COMPLETO.md) conserva o âmbito das quatro fases implementadas.

## Instalação

1. Instala Minecraft 1.21.1 com NeoForge e Java 21.
2. Coloca o JAR de Laws & Justice na pasta `mods` do servidor e de cada jogador. Num mundo LAN, instala-o também no cliente que abre o mundo.
3. Reinicia o jogo ou o servidor. Mantém a mesma versão do mod em todos os participantes.

O mod funciona sozinho. **Chronicles RP** é opcional para apresentar os nomes das personagens; sem ele, aparecem os nomes Minecraft. **Realm Notifications** é opcional para os avisos de condenação e libertação; sem ele, são usadas mensagens do próprio jogo. **Realm Council 0.2.2+** é opcional para reconhecer o Presidente eleito e empossado. Sem esse mod, apenas os administradores têm autoridade judicial de emergência. **Chronicles RP 0.5.4+** permite pagar com a carteira digital existente; sem ele, são usadas esmeraldas reais do inventário. **Crown Ledger 0.2.2+** recebe os pagamentos confirmados; sem tesouraria compatível, o tribunal guarda-os num cofre persistente. **Realm Identity 0.1.1+** permite ao Presidente consultar um documento autêntico apresentado na mão por um acusado a até seis blocos. Nenhuma integração é obrigatória.

## Blocos e ferramenta

| Objeto | Utilização |
| --- | --- |
| **Porta de Cela** | Porta de ferro com grades e duas metades. Um condenado não a pode abrir manualmente. |
| **Grades de Prisão** | Painel de ferro para construir as paredes e janelas da prisão. |
| **Fechadura Reforçada** | Bloco auxiliar que abre o livro de controlo. Nesta versão, não associa fechaduras a portas nem cria cargos ou chaves de acesso. |
| **Controlo da Prisão** | Abre a interface física para registar prisões e celas, administrar penas e consultar o histórico de custódia. |
| **Agrimensor Prisional** | Seleciona dois cantos e um ponto de referência no mundo. Só um administrador pode utilizar a seleção. |

A **Mesa de Justiça** abre o tribunal físico. As **Algemas Reais** permitem conduzir um acusado com mandado válido. O **Mandado Selado** é emitido pelo tribunal e não tem receita.

Os blocos e ferramentas fabricáveis têm modelos 3D próprios, receitas e uma aba criativa **Laws & Justice**, com traduções em português e inglês. Os blocos são recolhidos com uma picareta de pedra ou superior. A porta de duas metades deixa cair apenas um item.

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

O jogador condenado pode consultar a sua própria pena ao interagir com o controlo ou com a fechadura. O menu apresenta a pena original, o tempo cumprido online e o tempo restante. O Trabalho Comunitário permite reduzir a pena, por defeito até 50% da sua duração original. O tempo normal continua a decorrer enquanto o jogador trabalha.

- Desligar-se pausa a contagem; voltar a entrar retoma a pena guardada.
- Morrer, renascer, usar um portal ou teleportar não elimina a condenação. O servidor volta a colocar o jogador na prisão quando necessário.
- Fechar o menu não pausa a pena. A contagem continua enquanto o jogador estiver ligado e o servidor estiver a processar o mundo.
- A contagem usa os ticks do servidor: a 20 ticks por segundo, 1 200 ticks correspondem a um minuto. Uma pausa do mundo ou um servidor com atraso pode prolongar o tempo real necessário.
- Ao chegar a zero, a pena termina automaticamente e o jogador é enviado para o ponto de libertação. Se esse ponto deixar de ser utilizável, o servidor procura uma alternativa.

Durante a detenção, o servidor impede a destruição e colocação de blocos pelo condenado e limita interações que poderiam permitir uma fuga. A área da prisão continua a ser uma construção normal que os jogadores livres podem organizar; não é um sistema geral de proteção de terrenos ou de combate.

## Libertação administrativa e histórico

Um administrador pode selecionar uma pena ativa, introduzir um motivo e confirmar uma **libertação de emergência**. Se o condenado estiver offline, a libertação fica guardada e é entregue quando voltar a entrar.

O separador **Histórico** apresenta os registos recentes de aplicação de penas, cumprimento e libertação de emergência. Guarda o nome do jogador, a autoridade, o motivo, a data e os tempos da pena. Os dados persistem quando os jogadores saem do mundo. O histórico de custódia tem um limite de **2 000 decisões recentes**. Os processos judiciais finais também aparecem no Livro de Registos, com o veredito, autoridade, data, multa ou duração original da pena. Os processos guardam até 256 entradas por mundo.

## Tribunal e Presidente

Qualquer jogador pode interagir com uma **Mesa de Justiça**, apresentar uma acusação e juntar depoimentos nos processos em que participa. Cada depoimento guarda o autor e a data; as partes e a autoridade judicial podem consultar as provas. Uma acusação não aplica uma pena automaticamente.

O **Presidente eleito e empossado**, com mandato ativo no Realm Council, abre a audiência, analisa as provas e decide entre absolvição, multa e prisão. A decisão fundamentada e a pena são guardadas na mesma operação. Pode condenar um acusado que entretanto tenha ficado offline. Não existem cargos adicionais de juiz ou guarda. Ao perder o mandato, perde imediatamente os poderes, mesmo com o menu aberto; as penas já existentes continuam a decorrer.

O controlo da prisão continua reservado aos administradores para registar construções e realizar intervenções de emergência. No tribunal, essas decisões ficam identificadas como **ADMIN_EMERGENCY**, separadas das decisões do Presidente.

## Mandados e algemas

O Presidente emite mandados físicos na Mesa de Justiça, com acusado, motivo, autor, data e validade entre **1 e 168 horas**. Pode revogá-los ou recuperar uma cópia em papel. Usar o pergaminho abre o registo validado pelo servidor; nomes e texto do item não concedem autoridade.

Qualquer jogador em sobrevivência pode conduzir o acusado se transportar um mandado válido para esse jogador e usar as algemas a uma distância máxima de **três blocos**. O mandado autoriza **uma única detenção**, de até **cinco minutos**, e reduz a velocidade em 60%. O acompanhamento utiliza movimento com colisões normais. Não altera o modo de jogo nem concede uma condenação à prisão.

Agachar e usar as algemas permite ao acompanhante original ou a um administrador retirar as algemas. A detenção termina quando expira, quando o acompanhante sai ou deixa de poder conduzir, quando o acusado é absolvido no processo associado ou quando entra em custódia prisional. Sair e voltar a entrar como acusado conserva a detenção dentro da validade original. Cópias de um mandado executado, revogado ou expirado não permitem uma segunda detenção.

O tribunal guarda até **256 processos**, com **16 depoimentos por processo**, e até **1024 mandados**. Não elimina provas antigas silenciosamente ao atingir esses limites.

## Trabalho Comunitário

Os trabalhos são opcionais e só contam para um prisioneiro vivo em **Sobrevivência**, com uma tarefa aceite, dentro da prisão e da zona corretas. O Presidente não recebe permissões para configurar objetivos, recursos ou progresso. As regras pertencem ao administrador; os objetivos e a recompensa de uma tarefa já aceite ficam fixados.

| Trabalho | Objetivo padrão | Redução padrão | Como se realiza |
| --- | --- | --- | --- |
| Mineração | 64 pedras | 3 minutos | Minerar recursos previamente validados, com uma picareta. |
| Agricultura | 32 trigos | 4 minutos | Colher trigo validado e maduro. |
| Carpintaria | 16 tábuas | 2 minutos | Recolher troncos validados com um machado e processá-los na Mesa de Oficina; quatro tábuas por tronco. |
| Limpeza | 10 pontos | 5 minutos | Utilizar uma escova nos pontos de manutenção validados e permanecer perto durante dois segundos por ponto. |
| Ferraria | 8 barras de ferro | 5 minutos | Recolher minério de ferro e carvão validados na oficina e processá-los na mesa; um carvão fornece oito unidades de combustível. |
| Transporte | 16 unidades de carga | 3 minutos | Recolher carga num Posto de Armazém e descarregar noutro da mesma zona, a pelo menos seis blocos de distância. |

A Mesa de Oficina demora **dois segundos por lote de madeira** e **quatro segundos por barra de ferro**, medidos em ticks online. Afastar-se mais de quatro blocos, sair do mundo ou deixar de poder trabalhar interrompe essa ação sem consumir os materiais reservados. Os materiais já recolhidos e o progresso concluído continuam guardados.

### Preparar as zonas

1. Constrói os espaços e coloca um **Quadro de Trabalhos**. Um administrador usa o Agrimensor Prisional para selecionar dois cantos de cada zona, incluindo o chão e a altura onde os jogadores trabalham.
2. No separador **Zonas**, escolhe a prisão, dá um nome e seleciona **Mina, Horta, Oficina, Armazém ou Pátio**. A zona tem de ficar totalmente dentro da prisão, sem sobreposição com outras zonas. O volume máximo de cada zona é 65 536 blocos.
3. Coloca os recursos reais: pedra e variantes aceites na mina; trigo na horta; troncos, minério de ferro e minério de carvão na oficina; pontos sólidos expostos no pátio. A inspeção não gera blocos nem recursos.
4. Seleciona a zona registada e carrega em **Inspecionar e validar os recursos**. Cada ponto fica registado no servidor. Existem até 256 zonas e 8192 pontos de recursos por mundo.
5. Coloca uma **Caixa de Entrega dentro de cada zona**. Acrescenta o respetivo Posto de Mineração, Posto Agrícola, Mesa de Oficina ou Posto de Manutenção. Para o armazém, coloca dois Postos de Armazém a pelo menos seis blocos de distância.
6. No separador **Regras**, ajusta a quantidade, os minutos de recompensa e a percentagem máxima da pena original. O padrão é 50%; zero desativa novas recompensas. A percentagem também é verificada na entrega.

As zonas de oficina aceitam carpintaria e ferraria. As restantes zonas correspondem ao trabalho do mesmo tipo. Os postos servem para consultar as instruções e organizar os espaços; a mesa valida a produção e os postos do armazém validam o percurso. Uma zona registada não é deslocada para reiniciar os recursos consumidos.

### Aceitar, trabalhar e entregar

1. O prisioneiro abre o Quadro de Trabalhos e aceita uma tarefa para a sua prisão.
2. Recebe um **Saco de Trabalho Selado**. O item é uma referência: os materiais e os contadores ficam guardados no servidor.
3. Realiza as ações na zona indicada. Os recursos validados passam para o saco; não são largados como itens normais. Materiais trazidos de fora ou obtidos em criativo não acrescentam progresso.
4. No trabalho de produção, utiliza a Mesa de Oficina e permanece perto até terminar cada lote. No transporte, utiliza os dois postos indicados.
5. Com o objetivo completo e o saco no inventário, abre a Caixa de Entrega **da mesma zona** e entrega-o. O servidor consome a entrega uma única vez e guarda a redução juntamente com o registo do trabalho.

Podes recuperar a referência ao saco num posto se o perderes. As cópias partilham o mesmo progresso e não dão outra recompensa. Abandonar uma tarefa descarta o seu progresso sem descontar tempo. O menu apresenta a pena original, o tempo online, os descontos, o restante e o histórico recente.

Uma recompensa grande é limitada à percentagem disponível e ao tempo que ainda falta. Se a redução completar a pena, o jogador é libertado pelo mesmo sistema normal de saída segura. Quando a pena termina por outro motivo, a tarefa deixa de autorizar qualquer entrega.

### Renovar recursos sem duplicação

Cada ponto validado só conta uma vez. Voltar a inspecionar a mesma zona ou colocar e partir repetidamente um bloco não reinicia esse ponto. Durante os trabalhos, não são gerados itens transferíveis nem recursos automáticos.

Para renovar um ponto consumido, um **administrador em Sobrevivência** tem de substituir fisicamente o recurso e voltar a inspecionar a zona **depois de terminarem ou serem abandonadas as tarefas ativas dessa zona**. A substituição normal consome os blocos ou sementes usados pelo administrador. Colocações de jogadores comuns ou em criativo não autorizam essa renovação. Na horta, a planta também tem de estar madura antes de poder contar de novo.

## Multas, fianças e registos públicos

Na **Mesa de Justiça**, o separador de julgamento permite aplicar uma multa depois de abrir a audiência e registar um motivo. O valor fica ligado ao veredito e ao UUID do acusado, mesmo que esteja offline. O condenado paga voluntariamente através de **Multas, Fianças e Registos**. Não há cobranças automáticas a jogadores offline, valores enviados pelo cliente ou nova pena por falta de saldo.

No separador **Autoridade**, o Presidente atual escolhe uma pena ativa, introduz um valor e um motivo e confirma uma fiança. O prisioneiro **ou um amigo** pode pagá-la no mesmo menu. A cobrança termina apenas a condenação a que foi associada e regista a libertação. Se o beneficiário estiver offline, a saída é entregue quando voltar. As fianças já autorizadas continuam válidas após a mudança de Presidente até serem pagas, revogadas ou terminar a pena. O Presidente pode revogar cobranças ainda sem pagamento ou autorizar uma libertação fundamentada; isso não altera os créditos dos trabalhos.

Os valores em euros aceitam ponto ou vírgula e até duas casas decimais. As esmeraldas são contadas em unidades inteiras. A moeda fica fixada quando a cobrança é criada; remover Chronicles depois não converte uma multa em esmeraldas. Se o mod estiver instalado mas a personagem ou carteira não estiver disponível, o pagamento fica indisponível.

Cada pagamento segue um recibo persistente: tentativa guardada → carteira/inventário e recibo gravados no jogador → cobrança paga e cofre financiado → recibo único na tesouraria. Nos anfitriões LAN, a confirmação verifica também os dados do jogador em `level.dat`, utilizados pelo Minecraft ao reabrir o mundo. Uma tentativa pendente pode ser repetida com o mesmo recibo; só é possível cancelá-la se não existir cobrança aplicada. Os fundos pagos permanecem guardados quando a tesouraria estiver ausente, indisponível ou cheia. Ao instalar Crown Ledger 0.2.2+, a transferência é retomada automaticamente. O tribunal não permite levantar ou criar esse dinheiro.

O **Livro de Registos Judiciais** é um bloco fabricável próprio, com livro aberto, encadernação, cantos de latão e pedestal. Qualquer jogador pode consultar os vereditos finais e o histórico público de penas. O livro não permite pagar nem decidir à distância, e não publica depoimentos privados ou acusações ainda por resolver. As novas condenações conservam a duração no processo; na migração, a duração das antigas é recuperada da custódia quando ainda estiver disponível.

## Dados do mundo

Prisões, celas, penas, tempo cumprido, processos, provas, mandados, detenções temporárias, zonas, recursos, tarefas, créditos de trabalho, histórico, multas, fianças, recibos e libertações pendentes ficam no ficheiro **`<mundo>/data/laws_justice.dat`**. Não são guardados no item da porta, no agrimensor ou apenas na memória do cliente.

Os mundos das versões 0.1, 0.2 e 0.3 são migrados automaticamente sem reiniciar as penas. As decisões administrativas e judiciais são guardadas imediatamente. O progresso das penas recebe pontos de gravação periódicos e é gravado ao sair e ao encerrar normalmente o mundo. Não há contagem de tempo offline. Um encerramento abrupto pode perder os últimos ticks desde o ponto de gravação, tal como outras alterações recentes do mundo.

Inclui este ficheiro nas cópias de segurança do mundo. Se os dados estiverem danificados, o mod informa o erro em vez de substituir silenciosamente os registos por uma prisão vazia.

## Utilização sem comandos

As receitas, a aba criativa, o agrimensor e os menus físicos permitem preparar e usar o sistema. O comando administrativo opcional **`/justice blocks`** entrega os blocos, o agrimensor e as algemas para facilitar a preparação; não aplica penas nem abre um menu remoto.

## Fases incluídas

A versão 0.4 inclui as quatro fases: prisões; tribunal eleito, algemas e mandados; trabalhos prisionais; multas, fianças, registos e integrações financeiras. Continua identificada como **Beta**. A [validação](VALIDACAO.md) identifica os testes e os limites da evidência disponível.
