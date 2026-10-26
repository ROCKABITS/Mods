# Realm Treasury 0.4.0 — Tesouro, votações e empréstimos

O **Crown Ledger passa a chamar-se Realm Treasury**. Mantém saldos, impostos, salários, contratos públicos e recibos existentes e acrescenta votações secretas no ecrã e benefícios financiados pelo tesouro.

Minecraft **1.21.1**, **NeoForge 21.1.256+**, **Java 21**. O mesmo JAR é necessário nos clientes e no servidor, incluindo LAN. Substitui o Crown Ledger anterior; ambos usam o identificador `kingdomtreasury` e não podem coexistir. A pasta de código `crown-ledger/`, os pacotes Java e as APIs antigas conservam os nomes para compatibilidade.

[Download JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-treasury-0.4.0.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-treasury-0.4.0.zip) · [Galeria](../media/realm-treasury/README.md) · [Validação](VALIDACAO-0.4.0.md) · [CurseForge](CURSEFORGE.md)

## Balcão de Empréstimos

A 0.4.0 acrescenta um bloco físico ao **mesmo mod e tesouro público**. O balcão tem modelo original de 66 elementos, madeira escura, ferragens de latão, superfície verde, livro aberto, contrato, pena, tinteiro, moedas e selo real. Tem receita, loot, orientação horizontal e entrada na aba criativa/pesquisa.

```text
Livro e pena    Papel          Ouro
Carvalho escuro Baú            Carvalho escuro
Carvalho escuro Carvalho escuro Carvalho escuro
```

### Pedir, aprovar e assinar

1. O jogador abre qualquer balcão e escolhe **Novo pedido**, indicando o valor e a finalidade.
2. O Presidente atual, com mandato ativo e juramento concluído, abre **Analisar pedidos**, escolhe juros fixos, número de prestações e intervalo em dias reais, dentro dos limites administrativos.
3. **Reservar e propor** retira exatamente o capital do saldo disponível do tesouro. O jogador ainda não recebe dinheiro; essa reserva já não pode financiar salários, contratos ou benefícios.
4. O requerente lê o contrato e confirma **Assinar e receber**. Recebe o capital na carteira do Realm Origins; o calendário começa na assinatura. A primeira prestação vence um intervalo depois.
5. **Pagar prestação** devolve a próxima prestação ao tesouro. **Liquidar tudo** paga o montante restante, incluindo os juros fixos acordados. Os pagamentos são manuais, através de qualquer balcão.

Exemplo: 100 € com juros fixos de 10%, em cinco prestações, originam 110 € de dívida e prestações de 22 €. O tesouro empresta 100 € e recebe 110 € se o jogador pagar tudo. O dinheiro dos juros sai da carteira do jogador, nunca de uma emissão de moeda nem de uma carteira pessoal do Presidente.

O menu mostra capital, total, juros, valores pagos e em falta, datas em UTC, plano completo e histórico. O contrato tem pergaminho próprio, selo de cera animado e confirmação sonora. O texto permanece acessível com deslocamento em interfaces pequenas. Os montantes usam cêntimos; os juros são arredondados uma única vez, para a unidade mínima, com metade arredondada para cima. Cêntimos/esmeraldas que sobrem são distribuídos pelas primeiras prestações: a soma é sempre exatamente o total.

### Regras do crédito

- Um pedido aberto ou empréstimo ativo por UUID, incluindo contratos em moedas diferentes. Só o próprio requerente assina e paga.
- O Presidente não aprova o seu próprio pedido. Um administrador OP 2+ pode aprovar em emergência, presencialmente e com registo. A autorização do Presidente é consultada em cada decisão; o sucessor herda a análise dos pedidos existentes.
- Sem Realm Council, o responsável nomeado por `/crown steward` e os administradores fazem a análise. Sem Realm Origins, os novos pedidos usam esmeraldas físicas. Dívidas antigas mantêm a moeda original; desinstalar o mod da carteira não converte dívidas digitais em esmeraldas.
- Os juros e as condições ficam fixos na proposta aprovada. Alterar limites, mandato ou relógio não reescreve contratos existentes. Um atraso é sinalizado; o valor acordado mantém-se fixo, sem juros compostos, saldo negativo automático ou outra pena.
- Recusar ou cancelar antes da assinatura devolve a reserva, quando exista, uma única vez. Pedidos expiram após sete dias reais e propostas após 48 horas, por defeito. A expiração é verificada ao reabrir um mundo LAN que tenha estado fechado. Não existe anulação administrativa de dívidas já assinadas.
- Pagar adiantado mantém o total de juros acordado. O calendário continua em tempo real, incluindo tempo offline; os pagamentos continuam manuais. O estado por pagar e as datas sobrevivem a reinícios.
- Partir, substituir ou colocar vários balcões mantém os contratos no registo do mundo. Não há dinheiro, inventário financeiro ou dívidas dentro do bloco. Todos consultam a mesma tesouraria, incluindo noutras dimensões.

### Limites administrativos

Secção `[loans]` no `serverconfig/kingdomtreasury-server.toml` do mundo:

| Configuração | Valor inicial |
| --- | --- |
| `enabled` | `true` |
| `maximumPrincipalCents` | `100000` — 1 000 € |
| `maximumPrincipalEmeralds` | `256` |
| `maximumInterestBasisPoints` | `2500` — 25% |
| `maximumInstallments` | `12` |
| `maximumIntervalDays` | `30` |
| `offerExpiresAfterHours` | `48` |
| `requestExpiresAfterDays` | `7` |
| `requestCooldownSeconds` | `60` |

O Presidente escolhe condições para cada proposta dentro destes limites. Desativar novos empréstimos impede pedidos e novas aprovações; propostas emitidas conservam as condições e empréstimos ativos continuam a aceitar pagamentos. O livro suporta até 4096 contratos persistentes e 512 acontecimentos recentes, com listas paginadas. Ao atingir a capacidade, novos pedidos são recusados; as dívidas existentes continuam a aceitar pagamentos.

### Gravação e recuperação

Antes de iniciar uma transferência, o mod confirma que a carteira e o inventário de partida estão guardados. Depois grava uma intenção financeira no mesmo `crown_ledger.dat` dos saldos e contratos, altera a carteira com recibo único, confirma os ficheiros nativos do jogador e, no anfitrião LAN, também `level.dat/Data/Player`. Só então conclui o contrato ou pagamento no livro público. Não edita carteiras offline.

Uma falha conserva a intenção para recuperação pelo próprio requerente, ao voltar a ligar ou abrir o balcão. Operações públicas ficam suspensas enquanto existir uma transferência por reconciliar, para preservar o saldo que a financia. Um estado ambíguo é recusado e registado, em vez de inventar dinheiro ou apagar dívida. Guarda mundo, perfis e tesouraria na mesma cópia de segurança. O formato 9 migra formatos 1–8; JARs antigos não conseguem ler empréstimos.

## Votações e benefícios

No **Cofre da Coroa**, o Presidente empossado abre **Votações públicas → Propor**. A opção principal é **Votação livre**, para qualquer assunto do reino: escrever um **título até 120 caracteres** e uma **descrição até 1000 caracteres**, com várias linhas. Por exemplo, um título «Nova ponte da vila» e uma descrição com o local, os materiais e as condições de acesso. Os jogadores leem o texto completo e votam **A favor / Contra**.

O aviso no ecrã mostra o título e uma pré-visualização da descrição; **F8** abre o boletim completo, com deslocamento para textos longos. No histórico, **Ler** mostra a descrição original e o resultado. O texto fica fixado ao anunciar e é guardado mesmo após reiniciar o mundo. A votação livre é consultiva: não cobra dinheiro nem aplica alterações automáticas ao mundo.

**Benefício do reino** é uma opção adicional no mesmo menu. As votações e os benefícios continuam no cofre; o crédito utiliza o Balcão de Empréstimos.

A proposta aparece num aviso discreto no topo do ecrã. **F8**, configurável nos controlos, abre o boletim. Não abre menus automaticamente nem bloqueia movimentos. O aviso grande recolhe após 12 segundos; fechar o boletim dispensa o aviso nessa ligação. O dinheiro e os resultados são sempre decididos no servidor.

Cada jogador online e fora de espectador no momento do anúncio tem um voto secreto e pode mudá-lo até ao prazo. Quem entrar depois espera pela próxima votação; quem voltar a entrar com o mesmo UUID conserva o voto. Não aparecem escolhas individuais nem resultados parciais. A participação é pública e os totais Sim/Não são publicados no fim.

Por defeito, a votação dura **3 minutos**, exige **50% de participação**, arredondada para cima, e aprova com mais Sim do que Não. Empates rejeitam. Há **10 minutos de intervalo após o prazo** e uma votação em curso de cada vez.

| Benefício | Efeito inicial |
| --- | --- |
| Colheita Próspera | Mais 20% de oportunidade de crescimento nos eventos elegíveis de culturas. |
| Obras do Reino | Mineração em sobrevivência 20% mais rápida, sem substituir poções. |
| Mesa Farta | Menos 20% de desgaste da fome nas atividades de sobrevivência. |
| Saber do Reino | Mais 20% de XP recebida; frações são distribuídas probabilisticamente. |

O custo inicial é **2 000 €** com a moeda Chronicles ou **400 esmeraldas** no modo independente, por benefício. Os valores são alternativas conforme a moeda fiscal. O custo é retirado do saldo disponível e reservado ao anunciar: não pode pagar salários ou contratos ao mesmo tempo. Se for rejeitado ou não tiver participação suficiente, volta ao tesouro. Ao aprovar, é consumido como despesa pública, sem pagamento à carteira do Presidente. Consultas gerais nunca consomem dinheiro.

Os benefícios duram **24 horas reais**, contam a partir do prazo da votação e continuam a expirar com o mundo fechado. Até **dois benefícios diferentes** podem coexistir. Não se acumula nem renova o mesmo benefício enquanto estiver ativo. Preço, duração, potência, quórum, eleitores e prazo ficam fixados ao anunciar. Alterar as configurações não muda propostas existentes.

O administrador configura estes valores em `serverconfig/kingdomtreasury-server.toml`, dentro do mundo, e pode desativar categorias. Tem acesso de emergência para propor e anular através do cofre, com histórico; a anulação devolve a reserva. O Presidente não altera preços nem limites. A mudança de mandato não apaga votos existentes; só o Presidente atual pode criar novas propostas.

O menu permite consultar benefícios ativos, tempo restante e as últimas 64 decisões. As configurações iniciais não criam dinheiro: o reino precisa de receitas para financiar os benefícios.

## Instalação e primeiro uso

1. Antes de atualizar um mundo existente, faz uma cópia de segurança do mundo completo e dos perfis do Realm Origins. Esta versão migra o registo do tesouro para o formato 9; voltar ao JAR antigo exige restaurar a cópia anterior à atualização.
2. Coloca `realm-treasury-0.4.0.jar` na pasta `mods` de cada cliente e do servidor. Se usas Realm Markets, atualiza também para **Realm Markets 0.2.1+** em todos eles; substitui os JARs antigos e não deixes duas versões do mesmo mod.
3. Para integrar os restantes sistemas, usa **Contracts & Bounties 0.4.0+**, **Realm Council 0.2.2+** e, opcionalmente, **Realm Origins 0.5.7** com as suas dependências.
4. Fabrica o **Cofre da Coroa** e clica nele com o botão direito. Também aparece na aba criativa **Realm Treasury** e na pesquisa.

Receita, de cima para baixo:

```text
Ferro       Papel       Ferro
Ouro        Baú         Ouro
Tábuas      Tábuas      Tábuas
```

Todos os cofres e dimensões consultam o mesmo tesouro do servidor. Partir um cofre devolve o bloco; o saldo, os salários e o histórico pertencem ao reino e permanecem guardados. A gestão financeira e a criação de propostas exigem o cofre. **F8** abre apenas o boletim público de voto e os benefícios, sem permitir gastar à distância.

## Quem pode fazer o quê

Qualquer jogador pode consultar as contas e as regras, fazer donativos e receber os seus salários. Os donativos são contribuições públicas, sem direito a levantamento pessoal.

Com Realm Council, só o vencedor com **mandato ativo e juramento concluído** pode alterar regras, publicar/cancelar contratos públicos e recolher os materiais do reino. A autorização é verificada a cada operação. O sucessor recebe acesso ao mesmo tesouro; o antigo governante perde-o imediatamente.

Sem Realm Council, um operador nomeia o responsável com `/crown steward NomeDoJogador`. Este comando não substitui eleições quando Realm Council está instalado. `/crown chest` entrega um cofre a um operador. Nenhum destes comandos abre o menu.

## Impostos e salários

O governante escolhe entre dois sistemas de imposto:

- **Percentagem das recompensas:** 20 € a 5% dão 19 € ao trabalhador e 1 € ao tesouro. Requer Contracts. A taxa fica guardada quando o contrato é publicado; alterações posteriores não mudam esse contrato. Com esmeraldas, o imposto arredonda para baixo para uma esmeralda inteira.
- **Valor fixo por ciclo:** aplica-se aos jogadores que atingiram a atividade mínima desse ciclo. É descontado primeiro no salário. Se o imposto exceder o salário, o restante é cobrado uma única vez quando o jogador estiver online, até ao saldo disponível; a parte que não puder pagar é dispensada. Não existe dívida nem saldo negativo.

O salário sai dos fundos existentes. Exemplo: salário de 20 € e imposto fixo de 5 € reservam 15 € para cada jogador elegível; os outros 5 € ficam no tesouro. O jogador recebe o crédito ao abrir o cofre e carregar em **Receber dinheiro**. Jogadores offline mantêm o seu crédito guardado. Um inventário cheio permite receber apenas a parte que couber.

O ciclo inicial dura **168 horas**, com **10 minutos online** de atividade mínima. O governante pode alterar a duração entre 1 e 720 horas e configurar os restantes valores. A atividade conta minutos completos de presença online fora do modo espectador; não tenta detetar AFK. O tempo offline não conta, e frações inferiores a um minuto são descartadas ao terminar sessão. Se Realm Origins estiver instalado, exige também uma personagem criada.

No fecho do ciclo, os participantes e os valores são fixados. Se faltar dinheiro para pagar a **todos**, o ciclo inteiro aguarda financiamento; não há prioridade para quem chegar primeiro. Um donativo ou rendimento dos contratos pode desbloqueá-lo. O governante pode cancelar explicitamente esse ciclo pendente, deixando registo. Só depois começa o ciclo seguinte. O tempo com o servidor desligado não cria vários salários retroativos.

As alterações ao imposto dos contratos e à reputação mínima aplicam-se às novas publicações. Os valores dos salários e o requisito de atividade são lidos no fecho do ciclo atual; um ciclo já congelado conserva os seus valores. Alterar a duração aplica-se ao próximo ciclo, sem antecipar o prazo do atual.

Por defeito, imposto e salário começam em **zero**. O reino precisa de donativos ou impostos para financiar despesas. Não cria moeda automaticamente.

## Imposto das bancas de mercado — 0.2.0

Com **Realm Markets 0.2.1+**, o governante pode definir no Cofre da Coroa uma percentagem de imposto sobre as vendas entre **0% e 100%**, inicialmente **0%**. Esta regra é independente da percentagem dos contratos e do imposto fixo por ciclo: pode haver imposto de mercado mesmo com o sistema fiscal dos contratos em modo fixo.

Numa compra de **20 € a 5%**, o comprador paga **20 €**, o vendedor ganha **19 €** e o tesouro recebe **1 €**. O imposto sai do valor da venda, sem acrescentar uma cobrança ao comprador. O cálculo usa o total da compra e arredonda para baixo à unidade mínima da moeda: cêntimos no Realm Origins e esmeraldas inteiras no modo independente. Por exemplo, uma compra de uma esmeralda a 5% gera imposto zero; uma compra de 20 esmeraldas gera uma. A 100%, o vendedor recebe zero, com o valor indicado antes da compra.

A regra atual é verificada pelo servidor ao concluir cada compra. Se o governante alterar a taxa depois da consulta do comprador, o pedido antigo é recusado sem cobrança e o menu é atualizado. Os menus mostram o total, o imposto e o valor líquido para o vendedor.

O imposto fica reservado no registo do mercado até ser recolhido automaticamente para o tesouro, normalmente em até um segundo de servidor ativo ou ao atualizar o cofre. Se a transferência estiver temporariamente indisponível ou o tesouro atingir o limite de saldo, a quantia permanece no mercado e volta a ser tentada; não é criada moeda nem perdida a reserva. As moedas mantêm-se separadas. O recebimento de impostos pode financiar salários pendentes.

Só uma venda paga gera imposto. Abastecer, retirar artigos, recolher ganhos ou transferir o imposto para o tesouro não volta a tributar esse dinheiro. Os ganhos existentes antes da atualização permanecem intactos. Sem Realm Treasury, Realm Markets continua a funcionar sem imposto; se ambos estiverem instalados mas a integração não estiver disponível, novas compras são suspensas até ser recuperada, mantendo disponíveis as operações de gestão e recolha.

## Imposto do casino — 0.2.3

Com **Realm Casino 0.3.0+**, o governante escolhe no Cofre da Coroa a percentagem **Imposto do lucro do casino**, entre 0% e 100%, com até duas casas decimais. A taxa inicial é 0% e é independente de contratos, imposto fixo por ciclo, vendas e moeda fiscal.

Apenas o lucro levantado pelo proprietário de uma banca é tributado, em cêntimos Realm Origins. Primeiro recupera o capital que depositou. Exemplo: depósito de 100 €, banca final de 120 €, levantamento total e imposto de 5% resultam em 119 € para o dono e 1 € para o tesouro. Depósitos, capital devolvido, prémios dos jogadores e potes entre jogadores não voltam a pagar imposto. Os valores anteriores à atualização contam como capital.

O Casino guarda a reserva e um recibo; a tesouraria grava receita e recibo em conjunto. Se a confirmação falhar, a mesma transferência pode ser repetida sem duplicação. A reserva mantém-se até à confirmação, também após reinício. As receitas aparecem no histórico público e podem financiar salários pendentes. A API judicial anterior continua disponível com o mesmo contrato.

Atualiza Casino e Crown no servidor e em todos os clientes. Sem a API desta versão, o Casino continua a operar com taxa zero.

## Serviços do Realm Economy — 0.2.5

Realm Economy 0.4.0 substitui Realm Villager Economy. A Mesa da Economia reúne preços de referência, reparações, combinações, nomes, encantamentos, ferraria, poções e períodos de beacon. O Presidente aprova preços e taxas e a administração define limites e ativação.

Cada pagamento envia apenas o imposto público ao Crown, com moeda Realm Origins e recibo ligado ao serviço. O preço base é retirado da economia. As decisões económicas têm recibos sem dinheiro, com os valores anteriores e novos; não criam fundos nem pagam salários. As APIs de Villagers, casino e justiça permanecem compatíveis. O formato 9 lê os formatos 1–8 e conserva os recibos antigos.

## Compras aos aldeões — 0.2.4

Com Realm Villager Economy 0.1.0, o Presidente empossado define o imposto no menu físico de um aldeão, sujeito ao teto do administrador (25% por defeito). Administradores mantêm acesso de emergência. A taxa inicial é 0% e conserva-se após o mandato.

Numa compra com base de 100 € e imposto de 10%, o jogador paga 110 €: 100 € saem da economia e 10 € entram no tesouro público. Vender materiais por esmeraldas não paga este imposto. O imposto é adicional à base e aparece antes da confirmação. Não é pago ao Presidente nem depende da moeda fiscal dos salários.

A carteira, os itens e o stock são confirmados pelo registo durável do Villager Economy. O imposto fica reservado até Crown guardar saldo e recibo em conjunto. Uma repetição confirma o recibo sem voltar a creditar. As aprovações fiscais têm recibos sem valor monetário e aparecem no histórico com a data de aprovação, autor e percentagem. Sem Crown, o Villager Economy cobra apenas a base e conserva as aprovações para futura integração.

## Contratos e reputação

O separador do governante publica contratos pagos pelo tesouro. A recompensa fica reservada no Contracts, onde os trabalhadores fazem a entrega pelo mural. Os materiais pertencem ao reino e são recolhidos no cofre pelo governante atual. Cancelar devolve a recompensa total ao reino, incluindo após mudança de governante.

Impostos e reembolsos entram no tesouro ao atualizar/abrir o cofre ou no processamento periódico do servidor. O dinheiro reservado para contratos abertos continua no Contracts e não pode financiar salários ao mesmo tempo.

O governante define a reputação necessária para publicar contratos particulares, a reputação ganha nos contratos públicos e o limite diário. Os valores iniciais são **0** de requisito, **+1** por contrato público e **3** por dia UTC. A reputação exige Realm Origins. Quem está abaixo do requisito pode cumprir contratos, receber recompensas e gerir os seus contratos já publicados. Concluir contratos públicos publicados pelo próprio não atribui reputação nem missões.

## Moedas e persistência

Sem Realm Origins, a moeda normal é a esmeralda inteira. Com Realm Origins, os valores digitais usam cêntimos e o perfil original do jogador. Cada moeda tem saldo e créditos separados; não existe conversão automática. O separador de donativos permite escolher a moeda. Nas regras, o governante pode escolher a moeda fiscal; mudar essa escolha repõe os campos de salário e imposto fixo em zero, sem converter saldos nem créditos anteriores. Os pagamentos de personagens offline ficam pendentes, sem editar os seus ficheiros de perfil.

O ficheiro `data/crown_ledger.dat` guarda regras, saldo disponível, salários reservados, atividade, ciclo pendente e os últimos 256 movimentos, incluindo receitas de imposto de mercado. Os contratos usam também `data/contracts_ledger.dat`; as bancas e os impostos ainda reservados usam `data/realm_markets.dat`. Os recibos judiciais são guardados no mesmo ficheiro que o saldo da tesouraria e não dependem da rotação dos últimos movimentos. O formato 9 migra automaticamente os formatos 1–8, conservando valores, regras e recibos; mundos anteriores ao formato 4 iniciam a taxa do casino a zero. As cópias de segurança devem incluir o mundo completo e os perfis do Realm Origins. As transferências entre mods são verificadas no mesmo thread do servidor. Os pagamentos judiciais usam gravações confirmadas e recibos únicos para conciliar ficheiros separados; as restantes operações mantêm os mecanismos existentes.

[Changelog](CHANGELOG.md) · [Resultados e limites da validação](VALIDACAO.md) · [JAR](../mod-pronto/crown-ledger-0.2.5.jar) · [ZIP](../mod-pronto/crown-ledger-0.2.5.zip) · [Imagens](../media/crown-ledger/0.2.5/README.md).

## Desenvolvimento e validação

```bash
./gradlew build
GITHUB_SHA=$(git rev-parse HEAD) python3 tools/treasury_release.py snapshot
./gradlew -PcrownGameTests runGameTestServer
python3 tools/treasury_release.py install
./gradlew -PcrownGameTests -PintegrationTests runGameTestServer
./gradlew -PcrownVisualTests runClient
./gradlew -PtreasuryClientTests runClient
```

Na máquina cloud, usa `python3 tools/cloud_gradle.py` em vez de `./gradlew`; mantém o proxy e a verificação TLS configurados. Os testes e os automatismos de cliente são excluídos do JAR de produção.

A versão **0.4.0** passou **210 execuções de testes** (102 JUnit, 40 + 40 testes nativos anteriores e 14 + 14 testes de crédito), a regressão visual e os cenários reais de votações e empréstimos com dois clientes TCP LAN. Foram confirmados carteira Realm Origins, Presidente Realm Council, reservas, prestações, falhas de gravação, ficheiros nativos do anfitrião e reabertura do mundo. [Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/40333786847) · [Validação e limites](VALIDACAO-0.4.0.md).

A versão 0.3.1 foi validada com **80 testes unitários**, **40 GameTests independentes**, **40 GameTests com os companheiros publicados**, a regressão visual do cofre e das interfaces anteriores e **dois clientes Minecraft reais ligados por TCP LAN**. A sequência LAN confirmou votos alteráveis, aprovação de um benefício financiado, rejeição de uma consulta empatada e reabertura efetiva do mundo com o saldo e decisões conservados. As capturas dos novos menus usam esses dados reais; as três capturas anteriores de contas e impostos usam dados de demonstração.

[Resultados, reprodução e limites](VALIDACAO-0.4.0.md). A publicação automática volta a executar os testes antes de guardar o JAR, o ZIP, os hashes, os relatórios e a galeria. O histórico das validações anteriores continua em [VALIDACAO.md](VALIDACAO.md).

## Pagamentos judiciais — 0.2.2

Laws & Justice 0.4.0 envia multas e fianças já pagas para a tesouraria na moeda original. Cada transferência guarda um recibo único no mesmo ficheiro que o saldo. Repetir o recibo, incluindo após reiniciar um mundo LAN, não cria outra entrada de dinheiro. Se a gravação ou o limite de saldo impedir a transferência, o tribunal conserva os fundos até poder entregá-los.

A atualização migra automaticamente o formato 1 ou 2 para o formato 9, conservando as contas, os impostos e os salários. Usa Realm Treasury 0.4.0 no servidor e em todos os clientes. Realm Markets 0.2.1 continua compatível. A API é exclusiva do servidor e não dá aos clientes uma forma de criar dinheiro.
