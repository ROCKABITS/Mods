# Realm Markets — Bancas de Mercado

Bancas físicas para **Minecraft 1.21.1**, **NeoForge 21.1.256+** e **Java 21**. Instala o mesmo `realm-markets-0.2.0.jar` no servidor e em todos os clientes. Identificador técnico: `realmmarkets`.

A versão **0.2.0 Beta** acrescenta imposto sobre vendas configurado pelo governante no Crown Ledger. Antes de atualizar, faz uma cópia de segurança do mundo completo e dos perfis do Chronicles: o registo do mercado migra para o formato 2 e voltar ao JAR antigo exige restaurar a cópia anterior. Se usas Crown Ledger, atualiza também para **0.2.0+** no servidor e em todos os clientes; remove os JARs antigos. Consulta o estado, os resultados e os limites das verificações em [VALIDACAO.md](VALIDACAO.md).

## Abrir uma banca

Fabrica uma **Banca de Mercado Carmesim** ou uma **Banca de Mercado Verde** e coloca-a numa superfície livre. Cada banca ocupa **dois blocos de largura, dois de altura e um de profundidade**, com balcão, armação de madeira e toldo de tecido. As variantes têm as mesmas funções e aparecem na aba criativa **Realm Markets** e na pesquisa.

Receita, de cima para baixo:

```text
Lã          Lã          Lã
Tábuas      Barril      Tábuas
Pau                     Pau
```

Usa lã vermelha para a banca carmesim e lã verde para a banca verde. O jogador que coloca a banca torna-se o proprietário. Clica numa das suas partes com o botão direito para abrir o menu. A utilização exige continuar perto da banca física; não existem atalhos de teclado nem comandos para abrir lojas à distância.

## Abastecer e vender

O proprietário dispõe de **oito ofertas** por banca:

1. Segura na mão principal o item que queres vender, escolhe uma oferta e define o **preço de uma unidade**.
2. Usa **Abastecer** para transferir a quantidade escolhida do inventário principal para a banca.
3. Os outros jogadores escolhem a oferta e a quantidade; o menu mostra o preço unitário, o total, o imposto e o valor líquido para o vendedor antes da compra.
4. O dinheiro das vendas fica guardado na banca. O proprietário recebe-o com **Recolher ganhos**, quando estiver online e junto dela.

Cada oferta guarda até **4096 itens**. Só se vendem itens normais, sem alterações aos componentes originais: itens renomeados, encantados, com conteúdos ou outros dados personalizados são rejeitados. Contentores que usam entidades de bloco, como baús, barris e caixas de shulker, são rejeitados mesmo vazios, assim como sacos e livros escritos ou editáveis, mesmo sem conteúdo. Os livros normais podem ser vendidos. Esta versão não permite vender equipamento personalizado. Para trocar o tipo de item de uma oferta, retira primeiro todo o stock dessa oferta. O proprietário pode alterar o preço e o nome da banca e retirar os seus artigos.

Uma banca continua a vender com o proprietário **offline**, enquanto o servidor está ligado e o comprador está junto dela. O servidor guarda stock e ganhos líquidos; não edita o perfil de dinheiro de um proprietário offline. Não há stock infinito nem criação automática de moeda.

## Imposto sobre vendas

Sem Crown Ledger, as vendas mantêm imposto zero. Com **Crown Ledger 0.2.0+**, o governante define no Cofre da Coroa uma percentagem entre **0% e 100%**, inicialmente **0%**. A taxa de mercado é independente das regras dos contratos e do imposto fixo periódico.

Numa compra de **20 € com imposto de 5%**, o comprador paga **20 €**, o vendedor acumula **19 €** e o reino recebe **1 €**. O imposto é retirado ao valor da venda, sem aumentar a despesa do comprador. A 100%, o ganho líquido do vendedor é zero. O cálculo usa o **total da compra**, arredondando para baixo ao cêntimo ou à esmeralda inteira: uma compra de uma esmeralda a 5% paga imposto zero; uma de 20 esmeraldas paga uma.

Cada compra valida a taxa atual no servidor. Se a taxa mudar desde a consulta, o pedido antigo é recusado sem cobrar nem retirar stock, e o menu mostra a regra nova. Se Crown Ledger estiver instalado mas a integração estiver indisponível, novas compras ficam suspensas com uma explicação; o dono continua a poder gerir artigos e recolher ganhos.

O imposto fica reservado no registo global do mercado, separado dos ganhos do vendedor e preservado mesmo após desmontar uma banca vazia. Crown Ledger recolhe-o normalmente em até um segundo de servidor ativo ou ao atualizar o cofre. Uma transferência recusada por falta de disponibilidade ou limite de saldo deixa a reserva intacta para nova tentativa. Não se tributa abastecimento, retirada de stock ou recolha de ganhos, e os ganhos existentes antes da atualização não recebem imposto retroativo.

## Moeda e pagamentos

**Sem Chronicles RP**, cada preço é um número inteiro de esmeraldas. O comprador entrega esmeraldas reais e o proprietário recolhe esmeraldas; se o inventário só permitir receber uma parte dos ganhos, o restante fica guardado.

**Com Chronicles RP 0.5.2**, as novas bancas usam a moeda digital do Chronicles. Os preços são guardados em cêntimos: por exemplo, `2,50 €` corresponde a 250 cêntimos. Os débitos e créditos exigem uma personagem criada e válida. Se o Chronicles estiver instalado mas o perfil ou a integração não estiver disponível, a transação é recusada; não muda silenciosamente para esmeraldas.

A moeda fica definida quando a banca é colocada. Adicionar ou remover Chronicles não converte stock, preços ou ganhos de bancas existentes. Recolhe os ganhos antes de mudar o conjunto de mods do servidor. Cada compra verifica o saldo, o stock, o espaço do comprador e o limite dos ganhos antes de efetuar a transferência. Uma compra recusada não cobra dinheiro nem retira stock.

## Proteção e persistência

Só o proprietário pode configurar, abastecer, retirar stock ou recolher os ganhos da sua banca. Os restantes jogadores podem comprar. O proprietário não pode comprar à própria banca.

Para desmontar a banca, retira primeiro todo o stock e todos os ganhos. A destruição normal é recusada a outros jogadores e a bancas com valores guardados, incluindo em criativo. Pistões e explosões não podem deslocá-las nem destruir o conteúdo. Uma banca vazia desmontada devolve um único item, sem duplicar as quatro partes.

O ficheiro global `data/realm_markets.dat` guarda proprietário, posição, moeda, ofertas, stock e ganhos de cada banca, em todas as dimensões, mais os impostos reservados para o reino. O formato 1 migra para o 2, conservando lojas, artigos e ganhos anteriores com uma reserva inicial de imposto vazia. Inclui-o nas cópias de segurança do mundo, juntamente com `data/crown_ledger.dat` se usas o tesouro. A verificação decorre no thread do servidor e a gravação usa a persistência normal do Minecraft; não existe uma transação de disco única entre o inventário, os registos dos dois mods e os perfis do Chronicles durante uma falha abrupta do processo. Um registo existente inválido não é substituído silenciosamente por uma loja vazia.

## Comandos e compatibilidade

- `/markets stall`: dá os dois modelos de banca a um operador; não abre menus nem altera o proprietário de bancas existentes.
- Os jogadores normais podem fabricar, colocar e gerir as suas bancas sem serem administradores.

Chronicles RP é opcional; o intervalo declarado é **`>=0.5.2 e <0.6`**. Se instalado, requer também as dependências do próprio Chronicles. Contracts & Bounties, Realm Council, Crown Ledger e Realm Identity não são necessários para usar Realm Markets. Quando Crown Ledger está instalado, requer **0.2.0+** para o imposto de mercado. O comércio não exige um nível de reputação.

[Changelog](CHANGELOG.md) · [Resultados e limites da validação](VALIDACAO.md) · [JAR](../mod-pronto/realm-markets-0.2.0.jar) · [ZIP](../mod-pronto/realm-markets-0.2.0.zip) · [Imagens](../media/realm-markets/0.2.0/README.md).

## Desenvolvimento

```bash
./gradlew build
./gradlew -PmarketGameTests runGameTestServer
# Copiar para compat-libs o JAR de produção Crown Ledger 0.2.0 compilado
# a partir do mesmo commit, mais Contracts 0.3.1, Realm Council 0.2.1
# e Realm Identity 0.1.0 publicados, antes desta execução:
./gradlew -PmarketGameTests -PintegrationTests runGameTestServer
./gradlew -PmarketVisualTests runClient
```

Na máquina cloud usa `python3 tools/cloud_gradle.py` em vez de `./gradlew`, mantendo o proxy, os checksums e a verificação TLS. O domínio pode ser testado sem descarregar Minecraft com `python3 tools/test_domain.py`.

O workflow conjunto compila primeiro os JARs de produção dos dois mods e executa os testes unitários. Depois testa cada um sozinho e com o JAR novo do outro mod, compilado no mesmo commit, mais os JARs publicados e verificados de Contracts, Realm Council e Realm Identity. As duas validações de cliente também têm de passar antes de uma única publicação conjunta. A proveniência identifica o commit e o hash exato do outro JAR usado. O estado e os resultados da versão atual ficam em [VALIDACAO.md](VALIDACAO.md); a evidência da versão 0.1.1 permanece nesse histórico.

A [execução conjunta aprovada da versão 0.2.0](https://github.com/ROCKABITS/Mods/actions/runs/38924972884), sobre `1a6825f00d37ec07c194e96a4b05c003f892d8d1`, passou **45 testes unitários Realm Markets, 45 testes nativos independente e 45 com os companheiros reais**. O Crown Ledger 0.2.0 compilado no mesmo commit passou 46 unitários e 19 nativos em cada modo. A integração verificou vendas com imposto, entrada exata na tesouraria, ausência de dupla tributação e recuperação do imposto reservado após o tesouro cheio. Os dois clientes Minecraft passaram, incluindo o total/imposto/ganho líquido da compra e a taxa do governante. A [proveniência](../mod-pronto/realm-markets-0.2.0.build.json) identifica os hashes e os resultados.

Os testes e as estruturas de teste ficam fora do JAR de produção. As capturas da interface usam artigos e saldos de demonstração; os pedidos dos botões foram registados localmente no teste visual, enquanto as transferências reais de itens e esmeraldas foram verificadas nos testes nativos do servidor. A integração completa com o runtime de Chronicles/Origins/Jupiter necessita de validação separada; os seus JARs originais não são redistribuídos.
