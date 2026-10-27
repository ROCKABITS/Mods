# Realm Economy

**Minecraft 1.21.1 · NeoForge 21.1.256+ · Java 21 · 0.5.0 Beta**

Realm Economy dá utilidade à carteira do **Realm Origins 0.5.7** durante a sobrevivência: compras aos aldeões, bigorna, encantamentos, melhorias, poções e ativações temporárias de beacons. A **Mesa da Economia** permite ao Presidente empossado governar os preços e impostos, respeitando os limites administrativos.

A **0.5.0** acrescenta a [economia opcional do MineColonies](MINECOLONIES.md): Cofre da Colónia 3D, orçamento independente por cidade, depósitos e levantamentos, exportações diárias limitadas e autorizações pagas de construção e recrutamento. [Validação da atualização](VALIDACAO-0.5.0.md).

## Instalação e atualização

1. Guarda uma cópia conjunta do mundo, perfis e tesouraria.
2. **Retira `realm-villager-economy-0.1.0.jar`. Realm Economy substitui esse mod.** Os dois não devem estar instalados juntos.
3. Instala `realm-economy-0.5.0.jar` nos clientes e servidor. Para impostos, utiliza **Realm Treasury 0.4.0** ou Crown Ledger 0.2.5+. Instala a mesma versão da tesouraria nos clientes e servidor.
4. Usa Realm Origins 0.5.7 e as suas dependências. Realm Council 0.2.2+ identifica o Presidente. Realm Notifications 0.1.1 é opcional.

Foi desenvolvido para servidor integrado, LAN e servidor dedicado. A atualização inclui validação com dois clientes TCP LAN e os addons MineColonies indicados pelo utilizador; os resultados ficam em [VALIDACAO-0.5.0.md](VALIDACAO-0.5.0.md). A correção dos livros encantados está documentada em [VALIDACAO-0.4.1.md](VALIDACAO-0.4.1.md).

A migração lê `data/realm_villager_economy.dat` quando ainda não existe `data/realm_economy.dat`. Conserva taxa, stock, transferências pendentes e intenções interrompidas. Lê também a conversão inicial de `config/realmvillagereconomy-server.json`, se existir. O ficheiro anterior permanece intacto como referência. Depois da migração, os registos ativos usam o formato 3 do Realm Economy; a tesouraria conserva o formato da sua própria versão. Regressar aos mods antigos exige restaurar a cópia anterior completa.

## Mesa da Economia

A mesa tem modelo próprio com 38 elementos: quatro pernas de madeira escura, ferragens, superfície azul, livro aberto, pena, tinteiro e moedas. Aparece na aba criativa **Realm Economy** e na pesquisa.

Receita:

```text
Livro         Lingote de ouro    Livro
Tábuas        Tábuas             Tábuas
Tronco                           Tronco
```

Usa tábuas e troncos de carvalho escuro. Clica na mesa para consultar os preços públicos e recolher os teus lotes de poções. O Presidente e os administradores autorizados também consultam o histórico. A mesa não guarda dinheiro nem perde a política quando é partida: as regras pertencem ao mundo.

## Preços, impostos e administração

O **Presidente eleito, empossado e dentro do mandato** pode alterar o preço e imposto de cada atividade. Perde esse acesso imediatamente quando o mandato termina. O sucessor herda os valores. Sem Presidente, mantêm-se as regras; sem Realm Council, a gestão fica administrativa.

Um administrador com OP 2+ mantém acesso de emergência quando não existe um fornecedor de permissões Realm Admin. Define mínimos, máximos, teto fiscal e ativação dos sistemas na mesa. Pode também corrigir preços. As alterações guardam data, UUID, nome, valores anteriores e novos. Passa o rato sobre uma linha do histórico para consultar os limites completos.

Preços iniciais:

| Atividade | Unidade cobrada | Preço base inicial |
| --- | --- | --- |
| Aldeões | Uma esmeralda do custo Vanilla | 5 € |
| Reparação na bigorna | Nível calculado pela bigorna | 1 € |
| Combinação na bigorna | Nível calculado pela bigorna | 1,50 € |
| Renomeação | Nível calculado pela bigorna | 1 € |
| Mesa de encantamentos | Nível apresentado pela opção escolhida | 1 € |
| Mesa de ferraria | Uma melhoria ou ornamento | 10 € |
| Poções | Lote de até três frascos | 5 € |
| Beacon | Período de ativação | 50 € |

A configuração JSON serve como referência inicial para novos mundos; as regras em utilização pertencem ao mundo e são alteradas na Mesa da Economia.

Os sistemas pagos começam ativos. O imposto inicial é **0%**, com teto administrativo inicial de **25%**. O valor inicial máximo de cada preço unitário é 10 000 €; o mínimo da conversão é 0,01 € e os restantes começam em zero. Estes limites podem ser alterados na mesa, até aos limites técnicos. A ativação da bigorna aplica-se em conjunto a reparações, combinações e nomes.

O preço base sai da economia. Apenas o imposto entra no **tesouro público**, nunca na carteira do Presidente. Exemplo: 100 € de base e 10% de imposto custam 110 €; o Crown recebe 10 €. O imposto é calculado em cêntimos e arredondado para baixo uma vez por operação. O menu apresenta base, percentagem, imposto, total e saldo antes da confirmação.

Sem Crown Ledger, o imposto efetivo é zero. As aprovações continuam guardadas. Se a API instalada falhar, os pagamentos fiscais pendentes conservam o seu recibo para nova tentativa. Sem Realm Origins ou personagem criada, os serviços em euros ficam indisponíveis. Desativar administrativamente um sistema pago repõe o funcionamento Vanilla desse sistema, incluindo os seus custos normais.

## Aldeões

As compras usam o custo atual da oferta × valor de referência da esmeralda. Conservam quantidades, materiais adicionais, descontos, procura, profissão, níveis e reposições Vanilla. Os materiais adicionais continuam necessários, como um livro para comprar um livro encantado.

Desde a 0.4.1, os livros encantados mostram os nomes e níveis dos encantamentos na lista e na pré-visualização. Passa o rato sobre qualquer oferta ou sobre o resultado selecionado para consultar a descrição nativa completa: todos os encantamentos, maldições, nome personalizado, lore e extensões de outros mods. A informação usa os componentes reais enviados pelo servidor. O menu compacto conserva a identificação do livro; resumos longos aparecem completos na descrição ao passar o rato.

Vender materiais continua a dar **esmeraldas físicas**, sem imposto nem euros. A referência de 5 € não oferece conversão direta de esmeraldas em dinheiro. O imposto dos aldeões também pode ser alterado no separador fiscal do seu menu, com as mesmas permissões e limites.

## Bigorna, encantamentos e ferraria

Interage com o bloco normal. No livro de serviços, escolhe uma entrada numerada e depois um item do inventário. Os ícones são seleções dos materiais; os itens ficam no teu inventário até confirmares. Só utiliza os 36 espaços principais. A interface pequena permite percorrer o inventário em duas páginas.

- **Bigorna:** utiliza o cálculo nativo, as compatibilidades, os componentes, a quantidade de material e o desgaste do bloco. Paga euros em vez de XP. A opção de renomear é explícita; uma reparação conserva o nome existente. Mantém as limitações das combinações Vanilla, incluindo o teto «Demasiado caro».
- **Encantamentos:** escolhe uma das três runas. Usa os cálculos nativos das estantes, opções e encantamentos. Mantém o lápis-lazúli exigido e muda a semente após o pagamento. Não exige nem consome níveis de XP. O preço usa o nível apresentado pela opção, não a quantidade de lápis-lazúli.
- **Ferraria:** escolhe molde, equipamento e material. Consome os materiais da receita Vanilla e conserva os componentes do resultado, incluindo nomes e encantamentos. Suporta melhorias e ornamentos que utilizem o sistema normal de receitas de ferraria.

Conquistas, estatísticas e notificações são processadas depois do recibo financeiro. O Realm Rewards pode pagar uma conquista resultante da operação; esse rendimento é independente do débito confirmado.

## Poções e combustível

O modo pago trabalha por lotes pessoais. Escolhe até três frascos e um ingrediente do inventário. Um **pó de blaze permite 20 lotes**; as cargas restantes pertencem ao jogador e ficam guardadas no mundo. O sistema usa as misturas de `PotionBrewing`, consome um ingrediente por lote e preserva os recipientes devolvidos pela receita.

Ao confirmar, paga-se uma vez e os materiais ficam reservados. O lote demora **20 segundos reais**, incluindo tempo em que o mundo está encerrado. As poções ficam guardadas até serem recolhidas pelo dono, através de qualquer suporte pago ou Mesa da Economia. Partir o suporte não destrói o lote. Se o inventário estiver cheio, a recolha é recusada e as poções permanecem guardadas. Cada jogador pode ter até 64 lotes pendentes.

Os suportes registados num alquimista MineColonies construído e com trabalhador atribuído mantêm o inventário e a produção nativos, incluindo o consumo de ingredientes e combustível. Os outros suportes seguem o serviço pessoal pago.

Enquanto este sistema está ativo, a mistura automática Vanilla por suportes pessoais e funis fica suspensa: não identifica um jogador responsável pelo pagamento. O botão **Armazenamento do suporte** permite recuperar ou guardar os conteúdos físicos anteriores. Desativar o sistema pago repõe a preparação Vanilla. Os lotes já pagos continuam disponíveis na mesa. Não existe uma segunda cobrança ao recolher.

## Beacons

Constrói a pirâmide normal e mantém o feixe desimpedido. O menu permite escolher efeitos desbloqueados pelo número de camadas, efeito principal II ou regeneração quando a pirâmide tem quatro camadas.

O período inicial é **600 segundos reais**. O administrador pode escolher entre 10 e 86 400 segundos. O tempo decorre também com o mundo encerrado. Cada ativação ou extensão tem confirmação e pagamento próprios; não existe renovação automática.

Os efeitos são públicos e usam a área Vanilla. Podem ser renovados com os mesmos efeitos, até 24 horas de tempo antecipado. Enquanto vigora um período pago, outras combinações aguardam o seu fim. Uma pirâmide insuficiente deixa de fornecer os efeitos correspondentes. A duração dos efeitos aplicados é limitada pelo período financiado.

## Realm Admin

O Realm Admin ainda não existe neste repositório. Esta publicação fornece um **contrato de integração**, não uma afirmação de compatibilidade com um mod ainda indisponível.

Um fornecedor servidor pode registar `EconomyPermissions.Provider` através de `pt.realmeconomy.api.EconomyPermissions.register(...)`, versão 1. A decisão é consultada em cada pedido e permite administrar sem OP. Não recebe cargos do cliente. Se o fornecedor falhar, o pedido administrativo é recusado. Com o identificador `realmadmin` instalado mas sem fornecedor registado, não aplica um fallback de OP; é necessário o adaptador. O Presidente continua a usar o Realm Council independentemente deste fornecedor.

## Persistência e segurança

`data/realm_economy.dat` conserva políticas, histórico, stock, lotes, combustível, ativações e transferências pendentes. As operações verificam o bloco, distância, sessão, nonce, orçamento, inventário e política no servidor.

O servidor grava a intenção, confirma carteira, inventário e recibo no ficheiro do jogador e, no anfitrião integrado/LAN, também em `level.dat/Data/Player`. Só depois conclui a alteração de stock ou lotes. A semente de encantamento faz parte do mesmo recibo do jogador. Recuperar uma intenção não volta a cobrar, criar o mesmo lote ou entregar o mesmo item.

Um erro de armazenamento ou estado ambíguo conserva a intenção e suspende operações financeiras; não inventa saldos. Os impostos são entregues através de recibos únicos, confirmados pelo Crown. Não edita carteiras ou inventários de jogadores offline. Os ficheiros usam substituição atómica e sincronização; a sincronização de diretórios depende do sistema operativo.

Os limites de capacidade protegem os recibos: 16 384 aldeões, 65 536 lotes/fundos pendentes, 16 384 beacons e 512 alterações locais recentes. A tesouraria conserva até 65 536 recibos de integrações sem expirar as chaves. Ao atingir uma capacidade, recusa operações que não possa registar. Inclui mundo, perfis e tesouraria na mesma cópia de segurança.

## Desenvolvimento

```sh
python3 tools/bootstrap_cloud.py
python3 tools/cloud_gradle.py build --console=plain
python3 tools/cloud_gradle.py -PeconomyGameTests runGameTestServer --console=plain
```

A integração utiliza `-PintegrationTests`, os JARs verificados em `.github/scripts/economy_release.py` e as dependências reais de Origins. `-PeconomyVisualTests -PintegrationTests runClient` executa a demonstração no cliente Minecraft. Os fixtures são excluídos do JAR.

[Changelog](CHANGELOG.md) · [Validação](VALIDACAO.md) · [Plano original](PLANO-REALM-ECONOMY.md).

[Download JAR](../mod-pronto/realm-economy-0.5.0.jar) · [Download ZIP](../mod-pronto/realm-economy-0.5.0.zip) · [Galeria](../media/realm-economy/README.md).
