# Realm Casino — Cassino do Reino

**Minecraft 1.21.1 · NeoForge 21.1.256+ · Java 21 · 0.1.0 Beta**

Constrói o teu casino com máquinas e mesas medievais funcionais. Esta primeira fase inclui **slots de três rolos, roleta europeia e blackjack contra a banca**, com modelos próprios, animações, sons e menus em inglês e português.

## Blocos e utilização

- **Máquina de slots:** móvel de dois blocos de altura, madeira escura, molduras douradas, três rolos, joias e alavanca.
- **Mesa de roleta:** mesa de um bloco com roda, veludo e pilhas de fichas decorativas.
- **Mesa de blackjack:** mesa de um bloco com rebordo trabalhado, cartas, veludo e detalhes dourados.

Fabrica os blocos na bancada de trabalho e consulta as receitas no livro do Minecraft. Também aparecem na aba criativa **Realm Casino** e na pesquisa. O jogador que coloca um bloco torna-se o seu proprietário. Abre os menus com o botão direito; não há tecla nem comando para jogar à distância. Um operador pode obter os três itens com `/casino blocks`.

Instala o JAR no servidor e em todos os clientes. O funcionamento normal usa exclusivamente blocos e menus, incluindo num mundo aberto por LAN.

## Economia e propriedade

As apostas utilizam apenas o **dinheiro virtual da carteira do Chronicles RP**, em cêntimos. Não existe outra moeda, nem conversão para dinheiro real. As fichas visuais são decoração.

Sem Chronicles RP, os modelos e as regras continuam disponíveis, mas as operações económicas ficam desativadas. Com Chronicles, é necessário ter uma personagem criada e uma carteira disponível. A integração modifica apenas o saldo; mantém os restantes dados da personagem.

O administrador define os limites globais em `config/realmcasino-server.json`, em cêntimos: por defeito, `minBet: 1` e `maxBet: 100000` (0,01 € a 1000 €). Reinicia o servidor após editar o ficheiro. Cada máquina ou mesa tem proprietário, nome, banca e limites de aposta próprios. O dono pode abastecer a banca, escolher apostas mínimas/máximas dentro dos limites do servidor e consultar os resultados. Não pode escolher vencedores, cartas ou probabilidades.

Antes de cobrar uma aposta, o servidor confirma que a banca, incluindo essa aposta, cobre o maior prémio bruto possível. O dinheiro reservado numa mão ativa não pode ser levantado. A aposta recebida entra na banca e o prémio sai dela; um prémio não cria dinheiro.

Os prémios ficam guardados como **quantias por receber**. Recolhe-os para a tua carteira no menu de qualquer bloco do casino. O mesmo vale para dinheiro retirado da banca pelo dono. Se a carteira estiver cheia, o restante continua guardado; não é perdido ao fechar o menu, sair ou desmontar uma máquina já vazia.

Só o proprietário pode desmontar uma máquina vazia e sem apostas pendentes. Uma banca com dinheiro ou uma mão ativa impede a desmontagem, também em criativo. Explosões e pistões não podem destruir estas máquinas. A remoção forçada por edição do mundo conserva o registo financeiro, sem copiar dinheiro para o item.

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

## Animações e integrações

Os rolos, a roleta e as cartas apresentam resultados que o servidor já confirmou. Fechar uma animação não repete uma aposta nem muda um prémio. O som pode ser silenciado nas preferências da interface.

**Realm Notifications** é opcional e mostra resultados e recolhas após a confirmação económica. Sem o canal disponível, existe uma alternativa no chat. O **Crown Ledger** não cobra imposto sobre este casino nesta versão; essa integração fica para uma fase futura.

## Persistência

O registo financeiro e as mãos ficam em `data/realm_casino.dat`. Para o anfitrião singleplayer/LAN, a transferência confirma também a cópia da personagem em `level.dat`, que o Minecraft pode preferir ao reabrir o mundo. As transferências da carteira usam um registo de intenção persistente e um recibo guardado com os dados do jogador, para confirmar uma operação uma única vez. O mod não altera ficheiros de perfis offline.

Se uma falha de armazenamento ou um estado ambíguo impedir provar a transferência, as operações económicas ficam bloqueadas até à recuperação. O mod conserva os dados e evita repetir uma cobrança às cegas. Os ficheiros são sincronizados para disco antes de confirmar a transferência. A sincronização do diretório depende do sistema operativo; no Windows, o Java utiliza a substituição atómica e a sincronização do ficheiro, sem sincronizar um handle de diretório. Não se promete recuperação de qualquer falha de hardware ou restauro parcial. Mantém as cópias de segurança habituais do mundo, incluindo `playerdata` e o registo do casino em conjunto.

## Desenvolvimento e âmbito

```sh
python3 tools/bootstrap_cloud.py
bash tools/run-domain-tests.sh
python3 tools/cloud_gradle.py build --console=plain
python3 tools/cloud_gradle.py -PcasinoGameTests runGameTestServer --console=plain
```

`-PintegrationTests` carrega os companheiros verificados em `compat-libs/` e as dependências fixadas do Origins/Jupiter/Classes. `-PcasinoVisualTests -PintegrationTests runClient` ativa os fixtures visuais, excluídos do JAR distribuído.

Esta fase não inclui slots de cinco rolos, Plinko, roda da sorte, poker, jogos PvP, dados, cara ou coroa ou corridas de cavalos. O guião completo conserva esses jogos para atualizações posteriores. [Resultados e limites da validação](VALIDACAO.md).
