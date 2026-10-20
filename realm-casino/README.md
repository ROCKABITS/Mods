# Realm Casino — Cassino do Reino

**Minecraft 1.21.1 · NeoForge 21.1.256+ · Java 21 · 0.3.0 Beta**

Constrói o teu casino com máquinas e mesas medievais funcionais. Esta versão reúne **doze jogos físicos: slots de três e cinco rolos, roleta europeia, blackjack contra a banca, Plinko, Roda da Fortuna, Travessia Real, corridas de cavalos em miniatura, Texas Hold’em, vinte e um multiplayer, dados e cara ou coroa**, com modelos próprios, animações, sons e menus em inglês e português.

## Blocos e utilização

- **Máquina de slots:** móvel de dois blocos de altura, madeira escura, molduras douradas, três rolos, joias e alavanca.
- **Mesa de roleta:** mesa de um bloco com roda, veludo e pilhas de fichas decorativas.
- **Mesa de blackjack:** mesa de um bloco com rebordo trabalhado, cartas, veludo e detalhes dourados.
- **Painel de Plinko:** móvel de dois blocos, 78 pinos de latão em relevo, treze caixas e bola animada.
- **Roda da Fortuna:** móvel de dois blocos com coroa, pedestal trabalhado, aro dourado e ponteiro de rubi.
- **Mesa da Travessia Real:** diorama de uma estrada medieval com dez vias, carroças, sebes, arco de chegada e galinha em miniatura.

- **Slots de cinco rolos:** armário alto com cinco janelas, molduras de latão e indicadores de joias.
- **Pista de corridas:** mesa com quatro pistas, cancelas, cercas, arco de chegada e cavalos 3D animados em miniatura.
- **Texas Hold’em:** mesa de veludo verde com seis lugares, fichas decorativas e espaços para as cinco cartas comunitárias.
- **Vinte e um multiplayer:** mesa própria de veludo azul, para jogar entre participantes.
- **Dados:** mesa com tabuleiro de veludo vinho, copo e dados 3D com pontos.
- **Cara ou coroa:** pedestal com moeda dourada animada, faces da coroa e da lua.

Fabrica os blocos na bancada de trabalho e consulta as receitas no livro do Minecraft. Também aparecem na aba criativa **Realm Casino** e na pesquisa. O jogador que coloca um bloco torna-se o seu proprietário. Abre os menus com o botão direito; não há tecla nem comando para jogar à distância. Um operador pode obter os doze itens com `/casino blocks`.

Instala o JAR no servidor e em todos os clientes. O funcionamento normal usa exclusivamente blocos e menus, incluindo num mundo aberto por LAN.

## Economia e propriedade

As apostas utilizam apenas o **dinheiro virtual da carteira do Chronicles RP**, em cêntimos. Não existe outra moeda, nem conversão para dinheiro real. As fichas visuais são decoração.

Sem Chronicles RP, os modelos e as regras continuam disponíveis, mas as operações económicas ficam desativadas. Com Chronicles, é necessário ter uma personagem criada e uma carteira disponível. A integração modifica apenas o saldo; mantém os restantes dados da personagem.

O administrador define os limites globais em `config/realmcasino-server.json`, em cêntimos: por defeito, `minBet: 1` e `maxBet: 100000` (0,01 € a 1000 €). Reinicia o servidor após editar o ficheiro. Cada máquina ou mesa tem proprietário, nome e limites de aposta próprios. Os oito jogos contra a casa usam banca; as quatro mesas multiplayer reservam apenas as entradas dos participantes. O dono pode abastecer a banca, escolher apostas mínimas/máximas dentro dos limites do servidor e consultar os resultados. Não pode escolher vencedores, cartas ou probabilidades.

Antes de cobrar uma aposta, o servidor confirma que a banca, incluindo essa aposta, cobre o maior prémio bruto possível. O dinheiro reservado numa mão ativa não pode ser levantado. A aposta recebida entra na banca e o prémio sai dela; um prémio não cria dinheiro.

Os prémios ficam guardados como **quantias por receber**. Recolhe-os para a tua carteira no menu de qualquer bloco do casino. O mesmo vale para dinheiro retirado da banca pelo dono. Se a carteira estiver cheia, o restante continua guardado; não é perdido ao fechar o menu, sair ou desmontar uma máquina já vazia.

Só o proprietário pode desmontar uma máquina vazia e sem apostas pendentes. Uma banca com dinheiro ou uma mesa com entradas reservadas impede a desmontagem, também em criativo. Explosões e pistões não podem destruir estas máquinas. A remoção forçada por edição do mundo conserva o registo financeiro, sem copiar dinheiro para o item.

## Regras e probabilidades

Todos os prémios abaixo são **retornos brutos: incluem a aposta inicial**. Por exemplo, apostar 1 € e receber 2× significa receber 2 € no total, com lucro de 1 €.

### Slots de três rolos

Oito símbolos equiprováveis em cada rolo, com 512 combinações possíveis. Cada tripla específica tem probabilidade de 1/512.

| Resultado | Retorno bruto |
| --- | ---: |
| Três coroas | 100× |
| Três rubis | 80× |
| Três safiras | 50× |
| Três esmeraldas | 32× |
| Três sinos | 24× |
| Três luas | 16× |
| Três ferraduras | 12× |
| Três cerejas | 6× |
| Exatamente um par | 1×, devolve a aposta |
| Três símbolos diferentes | 0× |

Há 168 combinações com exatamente um par e 336 sem prémio. O retorno teórico é **488/512 = 95,3125%**. É uma média matemática de muitas jogadas, não uma promessa sobre a próxima aposta. Os modelos e as animações não alteram estas probabilidades.

### Roleta europeia

Uma única casa zero, números 0–36, cada um com probabilidade de 1/37.

| Aposta | Probabilidade de ganhar | Retorno bruto |
| --- | ---: | ---: |
| Um número, incluindo zero | 1/37 | 36× |
| Vermelho ou preto | 18/37 | 2× |
| Par ou ímpar | 18/37 | 2× |

O zero perde nas apostas em cor/paridade. O retorno teórico é **36/37 ≈ 97,2973%** para estas apostas.

### Blackjack contra a banca

Uma pessoa de cada vez por mesa, com um baralho novo de 52 cartas baralhado pelo servidor em cada mão. Ás vale 1 ou 11; figuras valem 10. A banca para em todos os 17, incluindo soft 17.

- Blackjack natural: 3:2 de lucro, ou 2,5× de retorno bruto.
- Vitória normal: 2×; empate: 1×; derrota: 0×.
- Apostas em quantias pares de cêntimos para pagar 3:2 sem arredondamentos.
- Podes pedir carta ou parar. Não há dividir pares, dobrar, seguro ou rendição nesta fase.
- A carta oculta da banca e o resto do baralho permanecem no servidor.
- Fechar o menu, sair ou esgotar o prazo de 60 segundos faz a mão **parar automaticamente**, usando as mesmas cartas. Não volta a baralhar nem devolve uma aposta já jogada.

O blackjack depende das decisões do jogador; não é anunciado um RTP fixo.

### Plinko

Doze decisões independentes, com 50% de probabilidade de esquerda/direita. O servidor guarda o percurso completo antes de cobrar a aposta e apresentar a animação. As treze caixas têm retornos brutos, da esquerda para a direita:

**32× · 12× · 4× · 2× · 1× · 0,5× · 0,25× · 0,5× · 1× · 2× · 4× · 12× · 32×**

Os 4096 percursos são equiprováveis; as caixas seguem uma distribuição binomial. Por exemplo, cada extremo corresponde a 1 percurso, enquanto a caixa central corresponde a 924. O retorno teórico bruto é **92,1142578125%**, antes de arredondar os prémios para baixo ao cêntimo. A banca reserva o prémio máximo de 32× antes de aceitar a aposta.

### Roda da Fortuna

Vinte setores equiprováveis: seis de 0×, seis de 0,5×, três de 1×, dois de 1,5×, um de 2×, um de 3× e um de 5×. O retorno teórico bruto é **95%** antes de arredondar para baixo ao cêntimo. A roda física e a interface mostram o setor confirmado pelo servidor; o ponteiro para sobre esse setor.

### Travessia Real — a galinha

Aposta uma vez e ajuda a galinha a atravessar até dez vias. **Cada avanço tem 80% de sucesso e 20% de risco de atropelamento.** Podes parar entre avanços e recolher o valor atingido. Se falhares, o retorno é zero; não se cobra uma nova aposta a cada passo.

O retorno bruto após `n` sucessos é `aposta × 0,95 / 0,8^n`, arredondado para baixo ao cêntimo. Para uma aposta de 1 €:

| Travessias seguras | Valor a recolher |
| --- | ---: |
| 1 | 1,18 € |
| 2 | 1,48 € |
| 3 | 1,85 € |
| 5 | 2,89 € |
| 10 | 8,84 € |

Ao décimo sucesso, a recolha é automática. Fechar o menu, afastar-te do bloco, sair do mundo ou atingir o prazo de 60 segundos recolhe automaticamente o valor já alcançado. Parar antes do primeiro avanço devolve apenas a aposta. As quantias ficam por receber no casino, usando a recolha habitual para a carteira. O prazo permanece guardado após reiniciar o mundo.

A banca reserva o prémio de dez sucessos desde o início. Para uma etapa escolhida antecipadamente, o retorno teórico é 95% antes do arredondamento. O risco de cada avanço mantém-se em 20%; a probabilidade de chegar às últimas etapas diminui. As carroças ilustram os resultados: escolher o momento do clique não altera as probabilidades.

## Slots de cinco rolos

Oito símbolos equiprováveis por rolo, numa linha horizontal. Pagam **três, quatro ou cinco símbolos iguais consecutivos, a começar no primeiro rolo da esquerda**. Conta apenas a maior sequência, uma vez. Não há pagamento por pares nem por sequências começadas noutro rolo.

| Símbolo | 3 iguais | 4 iguais | 5 iguais |
| --- | ---: | ---: | ---: |
| Coroa | 100× | 200× | 1000× |
| Rubi | 80× | 160× | 800× |
| Safira | 60× | 120× | 500× |
| Esmeralda | 50× | 100× | 300× |
| Sino | 40× | 80× | 200× |
| Lua | 30× | 60× | 160× |
| Ferradura | 24× | 48× | 100× |
| Cerejas | 16× | 32× | 68× |

As 32768 combinações são equiprováveis. O retorno teórico bruto é **31128/32768 = 94,9951171875%**. A banca tem de cobrir o prémio de 1000× antes de aceitar a aposta.

## Corridas de cavalos

Escolhe um dos quatro cavalos e aposta. O servidor sorteia uma das **24 ordens de chegada equiprováveis**; cada cavalo tem 25% de probabilidade de vencer. Acertar paga **3,8× de retorno bruto**, arredondado para baixo ao cêntimo. O retorno teórico é **95%**, antes desse arredondamento. A pista e o menu apresentam a mesma ordem confirmada. São peças em miniatura; não movimentam nem alteram as criaturas do mundo.

## Mesas entre jogadores

Senta-te através do bloco, escolhe a tua entrada e aguarda outro jogador. Com pelo menos dois participantes, qualquer um dos jogadores sentados pode iniciar. Uma mão não aceita novas entradas depois de começar. O dono configura nome e limites, sem escolher cartas ou resultados. Não cobra comissão nesta versão.

O dinheiro de cada entrada sai da carteira uma vez e fica reservado na mesa. Todo o montante é distribuído ou devolvido no fim. Os prémios e as fichas não utilizadas ficam por receber, com recolha no casino. Sair da sala de espera devolve a entrada; uma sala sem início expira após dois minutos.

### Texas Hold’em — 2 a 6 jogadores

Cada jogador recebe duas cartas privadas. São reveladas três cartas no flop, uma no turn e outra no river. Ganha a melhor combinação de cinco cartas entre as duas privadas e as cinco comunitárias; as categorias, os desempates e o ás baixo são avaliados no servidor.

- Entradas podem ter valores diferentes dentro dos limites da mesa. As fichas já existentes não mudam quando o dono altera esses limites.
- Small blind: 1% da primeira entrada, com mínimo de um cêntimo; big blind: o dobro. O botão de dealer roda entre mãos; as blinds são retiradas da entrada já reservada.
- Ações: desistir, passar quando não há valor a acompanhar, acompanhar, aumentar e all-in. O campo de aumento é o **total pretendido nessa ronda**, não o incremento.
- Um aumento normal tem de atingir o aumento mínimo. Um all-in menor não reabre o direito de aumentar dos jogadores que já atuaram.
- All-in com valores diferentes cria potes laterais. Cada jogador disputa apenas a parte que financiou; excedentes sem adversário regressam ao jogador.
- Empates dividem o pote. Cêntimos indivisíveis seguem a ordem dos lugares à esquerda do dealer.
- Só vês as tuas cartas durante a mão. No fim revelam-se as mãos que não desistiram; o baralho e cartas futuras nunca são enviados ao cliente.
- Fechar, afastar-se ou desligar durante a mão significa desistir, conservando as fichas que ainda não foram apostadas. Após 60 segundos sem ação, o servidor passa quando é gratuito ou desiste quando há aposta a acompanhar.

### Vinte e um multiplayer — 2 a 6 jogadores

Esta mesa é separada do blackjack contra a banca. Todos financiam entradas iguais e partilham um baralho novo de 52 cartas. Cada jogador pede cartas ou para na sua vez. A melhor mão até 21 ganha o pote; um blackjack natural supera outro 21. Empates dividem o prémio. Se todos ultrapassarem 21, as entradas são devolvidas. Não há dealer da casa, seguro, divisão de pares nem dobrar. Fechar, sair ou atingir o prazo faz parar com as cartas existentes.

### Dados — 2 a 6 jogadores

Entradas iguais. Cada participante lança dois dados independentes de seis faces; a soma mais alta ganha. Empates dividem o pote, incluindo todos os cêntimos. O servidor decide os valores uma vez; os dados animados apresentam esse resultado.

### Cara ou coroa — 2 jogadores

O primeiro escolhe coroa ou lua; o segundo recebe a face oposta. Entradas iguais. Cada face tem 50% de probabilidade e o vencedor recebe as duas entradas. O giro não altera o sorteio.

## Imposto do lucro do casino

Com **Crown Ledger 0.2.3+**, o governante eleito e empossado define no Cofre da Coroa uma taxa de **0% a 100%**, inicialmente 0%, independente dos impostos de contratos e mercado.

O imposto incide apenas no **lucro que o dono levanta da banca**, depois de recuperar o capital depositado. Exemplo: deposita 100 €, a banca cresce para 120 € e levanta tudo. Com taxa de 5%, recupera os 100 € e recebe 19 € de lucro; 1 € segue para o tesouro. Depósitos e prémios dos jogadores não são tributados. Perdas mantêm o capital por recuperar antes de reconhecer novo lucro; o controlo é por máquina. Os saldos anteriores à atualização são tratados como capital, sem imposto retroativo.

O cálculo arredonda para baixo ao cêntimo. A taxa aplicada é a que vigora ao confirmar o levantamento. A parte do tesouro fica reservada com recibo único até a transferência ser gravada; repetir a tentativa não duplica a receita. Uma falha anterior à reserva impede o levantamento, sem cobrança. Sem Crown ou com uma versão anterior à API do casino, a taxa é zero. As mesas PvP distribuem as entradas diretamente e não geram lucro do proprietário para tributar.

## Animações e integrações

Os rolos, as rodas, a bola, a galinha e as cartas apresentam resultados que o servidor já confirmou. Fechar uma animação não repete uma aposta nem muda um prémio. O som pode ser silenciado nas preferências da interface.

**Realm Notifications** é opcional e mostra resultados e recolhas após a confirmação económica. Sem o canal disponível, existe uma alternativa no chat. O **Crown Ledger 0.2.3+** recebe o imposto sobre o lucro realizado do dono, segundo a regra acima.

## Persistência

O registo financeiro e as mãos ficam em `data/realm_casino.dat`. Para o anfitrião singleplayer/LAN, a transferência confirma também a cópia da personagem em `level.dat`, que o Minecraft pode preferir ao reabrir o mundo. O formato 3 lê os formatos 1 e 2, mantendo bancas, prémios e mãos e acrescentando mesas multiplayer, capital e recibos fiscais. Não voltes aos JARs 0.1.0/0.2.0 depois de guardar o mundo com a 0.3.0; conserva uma cópia de segurança anterior se precisares de regressar. As transferências da carteira usam um registo de intenção persistente e um recibo guardado com os dados do jogador, para confirmar uma operação uma única vez. O mod não altera ficheiros de perfis offline. Reiniciar conserva cartas, entradas e prazos; participantes sem sessão presente ficam sujeitos às mesmas regras de abandono na retoma do servidor.

Se uma falha de armazenamento ou um estado ambíguo impedir provar a transferência, as operações económicas ficam bloqueadas até à recuperação. O mod conserva os dados e evita repetir uma cobrança às cegas. Os ficheiros são sincronizados para disco antes de confirmar a transferência. A sincronização do diretório depende do sistema operativo; no Windows, o Java utiliza a substituição atómica e a sincronização do ficheiro, sem sincronizar um handle de diretório. Não se promete recuperação de qualquer falha de hardware ou restauro parcial. Mantém as cópias de segurança habituais do mundo, incluindo `playerdata` e o registo do casino em conjunto.

## Desenvolvimento e âmbito

```sh
python3 tools/bootstrap_cloud.py
bash tools/run-domain-tests.sh
python3 tools/cloud_gradle.py build --console=plain
python3 tools/cloud_gradle.py -PcasinoGameTests runGameTestServer --console=plain
```

`-PintegrationTests` carrega os companheiros verificados em `compat-libs/` e as dependências fixadas do Origins/Jupiter/Classes. `-PcasinoVisualTests -PintegrationTests runClient` ativa os fixtures visuais, excluídos do JAR distribuído.

O guião original do casino está implementado nesta versão, com os limites de regras descritos acima. O protocolo de rede passou para 3: atualiza servidor e todos os clientes. [Âmbito e decisões](PLANO-COMPLETO.md). [Plano dos mods](../PLANO-DOS-MODS.md) · [Resultados e limites da validação](VALIDACAO.md).
