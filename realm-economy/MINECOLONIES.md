# MineColonies — economia das colónias

Integração opcional do **Realm Economy 0.5.0 Beta**, para Minecraft 1.21.1. Mantém a carteira pessoal do Realm Origins e acrescenta um orçamento coletivo independente por colónia. O tesouro global continua no Realm Treasury.

## Começar

1. Instala a mesma versão do Realm Economy nos clientes e servidor, com Realm Origins 0.5.7 e as suas dependências.
2. Instala MineColonies **1.1.1403-1.21.1** e as dependências próprias: Structurize, BlockUI e Domum Ornamentum; Multi-Piston integra o conjunto validado.
3. Fabrica o **Cofre da Colónia** e coloca-o dentro do território reclamado da cidade. Também aparece na aba criativa Realm Economy.
4. Abre o cofre, escreve um montante em euros e confirma o depósito. O dinheiro sai da tua carteira e entra no orçamento da colónia.
5. Consulta **Exportações**, entrega os produtos pedidos e aprova as autorizações em **Despesas**.

Receita do cofre:

```text
Lingote de ouro    Livro e pena       Lingote de ouro
Carvalho escuro    Baú                Carvalho escuro
Lingote de ferro   Carvalho escuro    Lingote de ferro
```

Os elementos de carvalho escuro são tábuas. Todos os cofres colocados na mesma colónia usam o mesmo saldo. Partir um cofre conserva a conta e os registos; outro cofre dentro dessa colónia dá acesso aos fundos.

## De quem é o dinheiro?

| Conta | Utilização |
| --- | --- |
| Carteira pessoal do Realm Origins | Dinheiro do jogador. |
| Cofre da colónia | Produção, desenvolvimento e lucros da cidade. |
| Realm Treasury | Tesouro público do reino, impostos, salários e contratos públicos. |

Depósitos e levantamentos transferem exatamente o mesmo valor entre carteira e colónia. Exportações para compradores externos criam dinheiro novo, sujeito a limites. As autorizações pagas de construção e recrutamento retiram dinheiro do orçamento e da economia. O Presidente não recebe estes custos na carteira pessoal.

O proprietário pode levantar lucro para a sua carteira e depois utilizar normalmente Realm Markets, Contracts & Bounties, a economia Vanilla adaptada e os restantes mods. Os membros podem contribuir com depósitos. Para apoiar uma colónia através do reino, o Presidente pode financiar contratos públicos; os jogadores recebem os pagamentos na carteira e depositam a contribuição no cofre da cidade. As bancas e os contratos existentes continuam a operar através das carteiras dos seus participantes.

## Permissões

A autoridade é consultada no MineColonies em cada operação:

- **Proprietário:** gestão do orçamento, depósitos, levantamentos, exportações e aprovação de despesas.
- **Oficiais e cargos personalizados:** gestão quando o cargo nativo tem `isColonyManager` e a permissão **Manage Huts**. A nomeação de um responsável dá-lhe acesso real ao orçamento; os movimentos identificam sempre o autor.
- **Outros membros não hostis:** consulta e depósitos.
- **Pessoas de fora:** acesso recusado.
- **Administração autorizada do Realm Economy:** acesso de emergência e configuração de limites.

Ser Presidente do Realm Council não concede automaticamente gestão das contas das colónias. O ranking do ColonyRank também não concede permissões financeiras. Mudanças de proprietário ou de cargo são aplicadas na operação seguinte; os fundos pertencem à colónia e não mudam de conta com o proprietário.

As operações normais exigem interação física com o cofre, distância máxima de oito blocos, sessão válida e personagem com carteira disponível. Não existem comandos de abertura financeira remota. Os limites globais são administrativos, seguindo o fornecedor opcional Realm Admin ou OP 2+ quando esse fornecedor não está instalado.

## Exportações

Cada colónia recebe **cinco encomendas diferentes por dia UTC**, escolhidas de um catálogo de vinte produtos. As encomendas permanecem iguais após reconexão ou reinício, com quantidades e valores fixados na geração. A renovação acontece quando se volta a consultar a colónia depois de 00:00 UTC. O relógio máximo observado é persistente e protege a geração anterior se o relógio recuar.

Alguns produtos de origem:

| Entrega | Receita da colónia |
| --- | ---: |
| 128 pães | 40 € |
| 192 cenouras | 30 € |
| 192 troncos de carvalho | 40 € |
| 256 pedras | 25 € |
| 32 livros | 45 € |
| 64 lingotes de ferro | 50 € |

O limite inicial é **250 € por colónia/dia** e **250 € por jogador exportador/dia**, partilhado por todas as colónias em que esse jogador exporta. Ambos são limites de receita de exportações, independentes das quests diárias do Contracts & Bounties. O conjunto pode render menos do que o teto.

Retira do armazém os produtos que queres vender e leva-os no inventário ao cofre. A confirmação mostra os materiais que vão ser consumidos e o valor que entra na colónia. Usa os 36 espaços principais; armadura e mão secundária ficam fora da entrega. Itens com componentes personalizados, como nomes especiais, são preservados. O modo criativo não pode concluir exportações.

**Os armazéns e os pedidos dos trabalhadores mantêm o funcionamento nativo.** Esta versão usa entrega presencial pelo inventário, permitindo escolher os excedentes sem retirar automaticamente materiais reservados para obras. Cada encomenda pode ser entregue uma única vez por conjunto. Encomendas não entregues são substituídas na renovação.

O ficheiro `config/realm-economy-minecolonies.json` permite alterar o catálogo e adicionar itens de outros mods. Cada produto indica `item`, `quantity` e `cents`; 4000 cêntimos correspondem a 40 €. Itens ausentes não são escolhidos. O catálogo deve conter produtos suportados suficientes para a quantidade de encomendas configurada. Reinicia o servidor depois de editar o catálogo.

## Construções e recrutamento

Novas ordens nativas de **construção e melhoria de edifícios** aparecem em **Despesas**. Enquanto a autorização estiver por pagar, os construtores aguardam. A aprovação usa apenas o saldo da colónia; os requisitos de materiais, trabalhadores, distância, investigação e níveis continuam a ser determinados pelo MineColonies.

Custo inicial: **25 € × nível pretendido²**.

| Nível pretendido | Autorização |
| --- | ---: |
| 1 | 25 € |
| 2 | 100 € |
| 3 | 225 € |
| 4 | 400 € |
| 5 | 625 € |

A autorização paga cobre o mesmo local, tipo de edifício e nível na colónia. Cancelar e voltar a pedir essa obra reutiliza a autorização, preservando o valor aprovado; um nível seguinte ou outro local exige a sua própria autorização. A confirmação é definitiva. Autorizações por pagar de ordens canceladas deixam de poder ser aprovadas. Reparações, remoções, decorações e operações de mineração próprias dos trabalhadores conservam o comportamento nativo nesta versão. Ordens antigas, anteriores à instalação desta atualização e sem identificador financeiro, continuam válidas.

Os visitantes da taberna aparecem também nas despesas, com autorização inicial de **25 € por visitante**. Aprova no cofre e depois usa o diálogo normal do visitante para recrutar. Mantêm-se os **materiais nativos de recrutamento**, a capacidade da colónia e as regras do MineColonies, incluindo a possibilidade nativa de um visitante fugir. A autorização financeira não garante o sucesso do recrutamento. Os colonos iniciais, nascimentos e ressurreições não são tributados por este mecanismo.

Definir um destes preços em zero dispensa a respetiva cobrança. Os valores e requisitos visíveis das autorizações já emitidas ficam congelados. Esta atualização trabalha com despesas aprovadas individualmente; salários periódicos dos colonos ficam para uma fase posterior.

## Produção do alquimista

Os suportes de poções registados num **alquimista construído e com trabalhador atribuído** mantêm a produção e o inventário nativos do MineColonies. O alquimista pode consumir ingredientes/combustível e produzir as poções normalmente, incluindo a aceleração do seu trabalho.

Essa exceção aplica-se aos postos registados desse edifício. Suportes pessoais noutros locais, incluindo dentro da mesma colónia, continuam sujeitos ao serviço pago de poções do Realm Economy. Uma cabana por construir ou sem trabalhador não concede a exceção. As permissões nativas da colónia continuam a governar o acesso ao inventário da oficina.

## Configurar

Em **Configuração** no cofre, a administração pode alterar:

- Teto diário de exportações por colónia e por jogador.
- Quantidade de encomendas diárias, de 1 a 12; o catálogo tem de suportar essa quantidade.
- Custo base das construções e custo do recrutamento.
- Ativação da integração.

Alterações são registadas no histórico administrativo da Mesa da Economia com UUID, nome, data e política anterior/nova. Novos limites de receita aplicam-se às próximas entregas; os valores das encomendas e despesas existentes mantêm-se. A nova quantidade de encomendas entra na geração seguinte. O `initialPolicy` do JSON só inicializa a política quando o mundo cria a primeira conta colonial; as configurações de um mundo já iniciado pertencem aos seus dados persistentes.

Desativar a integração suspende exportações e cobranças, mantendo o acesso ao orçamento para depósitos e levantamentos. Os sistemas normais de aldeões, bigorna, encantamentos, ferraria, poções e beacons do Realm Economy mantêm as suas configurações próprias. Sem MineColonies, esta integração fica indisponível e o resto do Realm Economy funciona normalmente. Sem Realm Origins, os mecanismos monetários coloniais ficam indisponíveis e as obras/recrutamento seguem as regras nativas.

## Addons indicados pelo utilizador

| Projeto | Ficheiro usado na validação |
| --- | --- |
| MineColonies | `minecolonies-1.1.1403-1.21.1.jar` |
| Bits and Bobs | `bits-and-bobs-1.21.1-1.0.6.jar` — metadados internos 0.1.1 |
| Byzantine Styles | `Byzantine-1.21.1-51.1.jar` |
| ColonyRank | `ColonyRank-1.21.1-2.0.1.jar` |
| MapColonies | `mapcolonies-1.0.0.jar` |
| SmallColonies | `smallcolonies 1.21.1 v1.8.jar` |
| SmallColonies ME | `smallcoloniesme 1.21.1 v1.9.jar` |

O **MapColonies 1.0.0 requer NeoForge 21.1.262+**. ColonyRank requer Fzzy Config; foram usados Fzzy Config 0.7.7+fix1 e Kotlin for Forge 5.12.0. A integração base mantém o requisito 21.1.256 do Realm Economy. Os packs de esquemas usam o sistema nativo de edifícios, por isso as autorizações seguem a obra e o nível. Decorações continuam sem custo monetário adicional.

A validação cobre a coexistência destes addons, o carregamento dos seus packs e o ciclo económico com dois clientes LAN. Não constitui um teste de todos os esquemas, de todas as combinações de addons ou da apresentação de fronteiras com versões arbitrárias do Xaero. Os JARs de terceiros não são incluídos no download do Realm Economy.

## Persistência e limites

`data/realm_economy.dat`, formato **3**, conserva contas, encomendas, quotas por jogador, autorizações, históricos e intenções financeiras. A migração lê os formatos 1 e 2 e preserva preços, comerciantes, stock, impostos pendentes, poções, combustível e beacons. Para regressar a uma versão antiga, restaura uma cópia conjunta anterior do mundo, jogadores e dados dos mods.

As transferências e entregas usam o diário financeiro existente, com estado anterior/seguinte verificado e recibo na mesma gravação da carteira e inventário. O anfitrião LAN guarda e verifica o recibo em `UUID.dat` e `level.dat/Data/Player`. Uma recuperação confirma o recibo e conclui o saldo da colónia, sem repetir um crédito ou consumir novamente uma entrega. As aprovações de despesas alteram a autorização e o orçamento na mesma substituição atómica do ficheiro económico. O trabalho dos NPCs e os edifícios continuam a usar o armazenamento nativo do MineColonies.

Uma intenção pendente suspende outras alterações financeiras até ser recuperada pelo jogador responsável. Estados ambíguos conservam os dados para inspeção, em vez de inventar um reembolso. Não edita carteiras offline. Mantém uma cópia conjunta do mundo e dos dados antes de atualizar.

Não é permitido apagar uma colónia com dinheiro no cofre: levanta primeiro os fundos. A exclusão de uma conta vazia arquiva o histórico; uma futura colónia com o mesmo número começa com saldo zero. Os registos permitem até 2048 contas incluindo arquivos, 2048 autorizações por conta, 256 movimentos recentes por conta e 10000 quotas de jogadores. O estado financeiro codificado tem um limite de 12 MB; atingir uma capacidade recusa novas alterações e conserva os recibos existentes.

As entregas verificam os itens reais e o modo de jogo atual. O Minecraft não fornece uma proveniência universal das pilhas: itens criados anteriormente por comandos ou criativo e transferidos para sobrevivência não podem ser distinguidos com certeza de recursos legítimos. Addons que criem edifícios fora das ordens nativas do MineColonies precisam de um adaptador próprio; não se promete um bloqueio universal desses sistemas.
