> **0.2.7:** acrescenta a receção idempotente da taxa de casamento do Realm Marriage. O dinheiro já foi reservado pelos noivos; entra apenas uma vez no tesouro e aparece no histórico como taxa de casamento. Mantém o formato de dados 6 e as integrações anteriores.

# Crown Ledger — Tesouro da Coroa

A versão **0.2.6** recebe o imposto das **quests diárias do Contracts & Bounties 0.4.0**, com recibos idempotentes e categoria fiscal no histórico. Mantém as integrações de serviços, Villagers, Casino, justiça, mercados, contratos e salários. O formato 6 preserva as contas e recibos existentes. A política das quests é gerida no mural, por um Presidente empossado ou administrador, dentro dos limites configurados.

Tesouro público para Minecraft **1.21.1**, **NeoForge 21.1.256+** e **Java 21**. Instala o mesmo JAR no servidor e nos clientes. O identificador técnico é `kingdomtreasury`.

## Instalação e primeiro uso

1. Antes de atualizar um mundo existente, faz uma cópia de segurança do mundo completo e dos perfis do Chronicles. Esta versão migra o registo do tesouro para o formato 6; voltar ao JAR antigo exige restaurar a cópia anterior à atualização.
2. Coloca `crown-ledger-0.2.6.jar` na pasta `mods` de cada cliente e do servidor. Se usas Realm Markets, atualiza também para **Realm Markets 0.2.1+** em todos eles; substitui os JARs antigos e não deixes duas versões do mesmo mod.
3. Para integrar os restantes sistemas, usa **Contracts & Bounties 0.4.0+**, **Realm Council 0.2.2+** e, opcionalmente, **Chronicles RP 0.5.4** com as suas dependências.
4. Fabrica o **Cofre da Coroa** e clica nele com o botão direito. Também aparece na aba criativa **Crown Ledger** e na pesquisa.

Receita, de cima para baixo:

```text
Ferro       Papel       Ferro
Ouro        Baú         Ouro
Tábuas      Tábuas      Tábuas
```

Todos os cofres e dimensões consultam o mesmo tesouro do servidor. Partir um cofre devolve o bloco; o saldo, os salários e o histórico pertencem ao reino e permanecem guardados. Não existem atalhos de teclado nem comandos para abrir o menu à distância.

## Quem pode fazer o quê

Qualquer jogador pode consultar as contas e as regras, fazer donativos e receber os seus salários. Os donativos são contribuições públicas, sem direito a levantamento pessoal.

Com Realm Council, só o vencedor com **mandato ativo e juramento concluído** pode alterar regras, publicar/cancelar contratos públicos e recolher os materiais do reino. A autorização é verificada a cada operação. O sucessor recebe acesso ao mesmo tesouro; o antigo governante perde-o imediatamente.

Sem Realm Council, um operador nomeia o responsável com `/crown steward NomeDoJogador`. Este comando não substitui eleições quando Realm Council está instalado. `/crown chest` entrega um cofre a um operador. Nenhum destes comandos abre o menu.

## Impostos e salários

O governante escolhe entre dois sistemas de imposto:

- **Percentagem das recompensas:** 20 € a 5% dão 19 € ao trabalhador e 1 € ao tesouro. Requer Contracts. A taxa fica guardada quando o contrato é publicado; alterações posteriores não mudam esse contrato. Com esmeraldas, o imposto arredonda para baixo para uma esmeralda inteira.
- **Valor fixo por ciclo:** aplica-se aos jogadores que atingiram a atividade mínima desse ciclo. É descontado primeiro no salário. Se o imposto exceder o salário, o restante é cobrado uma única vez quando o jogador estiver online, até ao saldo disponível; a parte que não puder pagar é dispensada. Não existe dívida nem saldo negativo.

O salário sai dos fundos existentes. Exemplo: salário de 20 € e imposto fixo de 5 € reservam 15 € para cada jogador elegível; os outros 5 € ficam no tesouro. O jogador recebe o crédito ao abrir o cofre e carregar em **Receber dinheiro**. Jogadores offline mantêm o seu crédito guardado. Um inventário cheio permite receber apenas a parte que couber.

O ciclo inicial dura **168 horas**, com **10 minutos online** de atividade mínima. O governante pode alterar a duração entre 1 e 720 horas e configurar os restantes valores. A atividade conta minutos completos de presença online fora do modo espectador; não tenta detetar AFK. O tempo offline não conta, e frações inferiores a um minuto são descartadas ao terminar sessão. Se Chronicles estiver instalado, exige também uma personagem criada.

No fecho do ciclo, os participantes e os valores são fixados. Se faltar dinheiro para pagar a **todos**, o ciclo inteiro aguarda financiamento; não há prioridade para quem chegar primeiro. Um donativo ou rendimento dos contratos pode desbloqueá-lo. O governante pode cancelar explicitamente esse ciclo pendente, deixando registo. Só depois começa o ciclo seguinte. O tempo com o servidor desligado não cria vários salários retroativos.

As alterações ao imposto dos contratos e à reputação mínima aplicam-se às novas publicações. Os valores dos salários e o requisito de atividade são lidos no fecho do ciclo atual; um ciclo já congelado conserva os seus valores. Alterar a duração aplica-se ao próximo ciclo, sem antecipar o prazo do atual.

Por defeito, imposto e salário começam em **zero**. O reino precisa de donativos ou impostos para financiar despesas. Não cria moeda automaticamente.

## Imposto das bancas de mercado — 0.2.0

Com **Realm Markets 0.2.1+**, o governante pode definir no Cofre da Coroa uma percentagem de imposto sobre as vendas entre **0% e 100%**, inicialmente **0%**. Esta regra é independente da percentagem dos contratos e do imposto fixo por ciclo: pode haver imposto de mercado mesmo com o sistema fiscal dos contratos em modo fixo.

Numa compra de **20 € a 5%**, o comprador paga **20 €**, o vendedor ganha **19 €** e o tesouro recebe **1 €**. O imposto sai do valor da venda, sem acrescentar uma cobrança ao comprador. O cálculo usa o total da compra e arredonda para baixo à unidade mínima da moeda: cêntimos no Chronicles e esmeraldas inteiras no modo independente. Por exemplo, uma compra de uma esmeralda a 5% gera imposto zero; uma compra de 20 esmeraldas gera uma. A 100%, o vendedor recebe zero, com o valor indicado antes da compra.

A regra atual é verificada pelo servidor ao concluir cada compra. Se o governante alterar a taxa depois da consulta do comprador, o pedido antigo é recusado sem cobrança e o menu é atualizado. Os menus mostram o total, o imposto e o valor líquido para o vendedor.

O imposto fica reservado no registo do mercado até ser recolhido automaticamente para o tesouro, normalmente em até um segundo de servidor ativo ou ao atualizar o cofre. Se a transferência estiver temporariamente indisponível ou o tesouro atingir o limite de saldo, a quantia permanece no mercado e volta a ser tentada; não é criada moeda nem perdida a reserva. As moedas mantêm-se separadas. O recebimento de impostos pode financiar salários pendentes.

Só uma venda paga gera imposto. Abastecer, retirar artigos, recolher ganhos ou transferir o imposto para o tesouro não volta a tributar esse dinheiro. Os ganhos existentes antes da atualização permanecem intactos. Sem Crown Ledger, Realm Markets continua a funcionar sem imposto; se ambos estiverem instalados mas a integração não estiver disponível, novas compras são suspensas até ser recuperada, mantendo disponíveis as operações de gestão e recolha.

## Imposto do casino — 0.2.3

Com **Realm Casino 0.3.0+**, o governante escolhe no Cofre da Coroa a percentagem **Imposto do lucro do casino**, entre 0% e 100%, com até duas casas decimais. A taxa inicial é 0% e é independente de contratos, imposto fixo por ciclo, vendas e moeda fiscal.

Apenas o lucro levantado pelo proprietário de uma banca é tributado, em cêntimos Chronicles. Primeiro recupera o capital que depositou. Exemplo: depósito de 100 €, banca final de 120 €, levantamento total e imposto de 5% resultam em 119 € para o dono e 1 € para o tesouro. Depósitos, capital devolvido, prémios dos jogadores e potes entre jogadores não voltam a pagar imposto. Os valores anteriores à atualização contam como capital.

O Casino guarda a reserva e um recibo; a tesouraria grava receita e recibo em conjunto. Se a confirmação falhar, a mesma transferência pode ser repetida sem duplicação. A reserva mantém-se até à confirmação, também após reinício. As receitas aparecem no histórico público e podem financiar salários pendentes. A API judicial anterior continua disponível com o mesmo contrato.

Atualiza Casino e Crown no servidor e em todos os clientes. Sem a API desta versão, o Casino continua a operar com taxa zero.

## Serviços do Realm Economy — 0.2.5

Realm Economy 0.4.0 substitui Realm Villager Economy. A Mesa da Economia reúne preços de referência, reparações, combinações, nomes, encantamentos, ferraria, poções e períodos de beacon. O Presidente aprova preços e taxas e a administração define limites e ativação.

Cada pagamento envia apenas o imposto público ao Crown, com moeda Chronicles e recibo ligado ao serviço. O preço base é retirado da economia. As decisões económicas têm recibos sem dinheiro, com os valores anteriores e novos; não criam fundos nem pagam salários. As APIs de Villagers, casino e justiça permanecem compatíveis. O formato 6 lê os formatos 1–5 e conserva os recibos antigos.

## Compras aos aldeões — 0.2.4

Com Realm Villager Economy 0.1.0, o Presidente empossado define o imposto no menu físico de um aldeão, sujeito ao teto do administrador (25% por defeito). Administradores mantêm acesso de emergência. A taxa inicial é 0% e conserva-se após o mandato.

Numa compra com base de 100 € e imposto de 10%, o jogador paga 110 €: 100 € saem da economia e 10 € entram no tesouro público. Vender materiais por esmeraldas não paga este imposto. O imposto é adicional à base e aparece antes da confirmação. Não é pago ao Presidente nem depende da moeda fiscal dos salários.

A carteira, os itens e o stock são confirmados pelo registo durável do Villager Economy. O imposto fica reservado até Crown guardar saldo e recibo em conjunto. Uma repetição confirma o recibo sem voltar a creditar. As aprovações fiscais têm recibos sem valor monetário e aparecem no histórico com a data de aprovação, autor e percentagem. Sem Crown, o Villager Economy cobra apenas a base e conserva as aprovações para futura integração.

## Contratos e reputação

O separador do governante publica contratos pagos pelo tesouro. A recompensa fica reservada no Contracts, onde os trabalhadores fazem a entrega pelo mural. Os materiais pertencem ao reino e são recolhidos no cofre pelo governante atual. Cancelar devolve a recompensa total ao reino, incluindo após mudança de governante.

Impostos e reembolsos entram no tesouro ao atualizar/abrir o cofre ou no processamento periódico do servidor. O dinheiro reservado para contratos abertos continua no Contracts e não pode financiar salários ao mesmo tempo.

O governante define a reputação necessária para publicar contratos particulares, a reputação ganha nos contratos públicos e o limite diário. Os valores iniciais são **0** de requisito, **+1** por contrato público e **3** por dia UTC. A reputação exige Chronicles RP. Quem está abaixo do requisito pode cumprir contratos, receber recompensas e gerir os seus contratos já publicados. Concluir contratos públicos publicados pelo próprio não atribui reputação nem missões.

## Moedas e persistência

Sem Chronicles, a moeda normal é a esmeralda inteira. Com Chronicles, os valores digitais usam cêntimos e o perfil original do jogador. Cada moeda tem saldo e créditos separados; não existe conversão automática. O separador de donativos permite escolher a moeda. Nas regras, o governante pode escolher a moeda fiscal; mudar essa escolha repõe os campos de salário e imposto fixo em zero, sem converter saldos nem créditos anteriores. Os pagamentos de personagens offline ficam pendentes, sem editar os seus ficheiros de perfil.

O ficheiro `data/crown_ledger.dat` guarda regras, saldo disponível, salários reservados, atividade, ciclo pendente e os últimos 256 movimentos, incluindo receitas de imposto de mercado. Os contratos usam também `data/contracts_ledger.dat`; as bancas e os impostos ainda reservados usam `data/realm_markets.dat`. Os recibos judiciais são guardados no mesmo ficheiro que o saldo da tesouraria e não dependem da rotação dos últimos movimentos. O formato 6 migra automaticamente os formatos 1, 2, 3, 4 e 5, conservando valores, regras e recibos; mundos anteriores ao formato 4 iniciam a taxa do casino a zero. As cópias de segurança devem incluir o mundo completo e os perfis do Chronicles. As transferências entre mods são verificadas no mesmo thread do servidor. Os pagamentos judiciais usam gravações confirmadas e recibos únicos para conciliar ficheiros separados; as restantes operações mantêm os mecanismos existentes.

[Changelog](CHANGELOG.md) · [Resultados e limites da validação](VALIDACAO.md) · [JAR](../mod-pronto/crown-ledger-0.2.5.jar) · [ZIP](../mod-pronto/crown-ledger-0.2.5.zip) · [Imagens](../media/crown-ledger/0.2.5/README.md).

## Desenvolvimento e validação

```bash
./gradlew build
./gradlew -PcrownGameTests runGameTestServer
mkdir -p compat-libs
# Copiar o JAR Realm Casino 0.3.0 compilado a partir do mesmo commit,
# mais os companheiros publicados definidos em .github/scripts/casino_release.py.
# A suíte nativa própria Crown usa estes companheiros sem Chronicles;
# o runtime completo da carteira é verificado pela suíte Laws & Justice.
./gradlew -PcrownGameTests -PintegrationTests runGameTestServer
./gradlew -PcrownVisualTests runClient
```

Na máquina cloud, usa `python3 tools/cloud_gradle.py` em vez de `./gradlew`; mantém o proxy e a verificação TLS configurados. O domínio independente corre com `python3 tools/test_domain.py` sem descarregar o Minecraft. Os testes nativos e visuais são opcionais no desenvolvimento e excluídos do JAR distribuído. A publicação automatizada exige que todos passem.

O [workflow conjunto aprovado](https://github.com/ROCKABITS/Mods/actions/runs/39253815267) compilou Laws & Justice 0.4.0 e Crown Ledger 0.2.2 a partir da mesma revisão, guardou os JARs de produção antes dos testes e utilizou as contrapartes novas nas integrações. O Crown passou **52 testes unitários, 25 GameTests independente e 25 com os companheiros**, além das três capturas do modelo e das interfaces no cliente real.

A suíte completa do Laws & Justice comprovou os débitos na carteira real do Chronicles e os créditos únicos na tesouraria, incluindo a reabertura efetiva de um mundo LAN. As imagens próprias do Crown usam dados de demonstração; a prova financeira vem dessa sequência integrada. [Resultados, hashes e limites da evidência](VALIDACAO.md).

## Pagamentos judiciais — 0.2.2

Laws & Justice 0.4.0 envia multas e fianças já pagas para a tesouraria na moeda original. Cada transferência guarda um recibo único no mesmo ficheiro que o saldo. Repetir o recibo, incluindo após reiniciar um mundo LAN, não cria outra entrada de dinheiro. Se a gravação ou o limite de saldo impedir a transferência, o tribunal conserva os fundos até poder entregá-los.

A atualização migra automaticamente o formato 1 ou 2 para o formato 6, conservando as contas, os impostos e os salários. Usa Crown Ledger 0.2.5 no servidor e em todos os clientes. Realm Markets 0.2.1 continua compatível. A API é exclusiva do servidor e não dá aos clientes uma forma de criar dinheiro.
