# Realm Markets — Bancas de Mercado

Bancas físicas para **Minecraft 1.21.1**, **NeoForge 21.1.256+** e **Java 21**. Instala o mesmo `realm-markets-0.1.0.jar` no servidor e em todos os clientes. Identificador técnico: `realmmarkets`.

## Abrir uma banca

Fabrica uma **Banca Carmesim** ou uma **Banca Salva** e coloca-a numa superfície livre. Cada banca ocupa **dois blocos de largura, dois de altura e um de profundidade**, com balcão, armação de madeira e toldo de tecido. As variantes têm as mesmas funções e aparecem na aba criativa **Realm Markets** e na pesquisa.

Receita, de cima para baixo:

```text
Lã          Lã          Lã
Tábuas      Barril      Tábuas
Pau                     Pau
```

Usa lã vermelha para a banca carmesim e lã verde para a banca salva. O jogador que coloca a banca torna-se o proprietário. Clica numa das suas partes com o botão direito para abrir o menu. A utilização exige continuar perto da banca física; não existem atalhos de teclado nem comandos para abrir lojas à distância.

## Abastecer e vender

O proprietário dispõe de **oito ofertas** por banca:

1. Segura na mão principal o item que queres vender, escolhe uma oferta e define o **preço de uma unidade**.
2. Usa **Abastecer** para transferir a quantidade escolhida do inventário principal para a banca.
3. Os outros jogadores escolhem a oferta e a quantidade; o menu mostra o preço unitário e o total antes da compra.
4. O dinheiro das vendas fica guardado na banca. O proprietário recebe-o com **Recolher ganhos**, quando estiver online e junto dela.

Cada oferta guarda até **4096 itens**. Só se vendem itens normais, sem alterações aos componentes originais: itens renomeados, encantados, com conteúdos ou outros dados personalizados são rejeitados. Contentores que usam entidades de bloco, como baús, barris e caixas de shulker, são rejeitados mesmo vazios, assim como sacos e livros escritos ou editáveis, mesmo sem conteúdo. Os livros normais podem ser vendidos. Esta versão não permite vender equipamento personalizado. Para trocar o tipo de item de uma oferta, retira primeiro todo o stock dessa oferta. O proprietário pode alterar o preço e o nome da banca e retirar os seus artigos.

Uma banca continua a vender com o proprietário **offline**, enquanto o servidor está ligado e o comprador está junto dela. O servidor guarda stock e ganhos; não edita o perfil de dinheiro de um proprietário offline. Não há stock infinito, criação automática de moeda nem imposto sobre estas vendas na primeira versão.

## Moeda e pagamentos

**Sem Chronicles RP**, cada preço é um número inteiro de esmeraldas. O comprador entrega esmeraldas reais e o proprietário recolhe esmeraldas; se o inventário só permitir receber uma parte dos ganhos, o restante fica guardado.

**Com Chronicles RP 0.5.2**, as novas bancas usam a moeda digital do Chronicles. Os preços são guardados em cêntimos: por exemplo, `2,50 €` corresponde a 250 cêntimos. Os débitos e créditos exigem uma personagem criada e válida. Se o Chronicles estiver instalado mas o perfil ou a integração não estiver disponível, a transação é recusada; não muda silenciosamente para esmeraldas.

A moeda fica definida quando a banca é colocada. Adicionar ou remover Chronicles não converte stock, preços ou ganhos de bancas existentes. Recolhe os ganhos antes de mudar o conjunto de mods do servidor. Cada compra verifica o saldo, o stock, o espaço do comprador e o limite dos ganhos antes de efetuar a transferência. Uma compra recusada não cobra dinheiro nem retira stock.

## Proteção e persistência

Só o proprietário pode configurar, abastecer, retirar stock ou recolher os ganhos da sua banca. Os restantes jogadores podem comprar. O proprietário não pode comprar à própria banca.

Para desmontar a banca, retira primeiro todo o stock e todos os ganhos. A destruição normal é recusada a outros jogadores e a bancas com valores guardados, incluindo em criativo. Pistões e explosões não podem deslocá-las nem destruir o conteúdo. Uma banca vazia desmontada devolve um único item, sem duplicar as quatro partes.

O ficheiro global `data/realm_markets.dat` guarda proprietário, posição, moeda, ofertas, stock e ganhos de cada banca, em todas as dimensões. Inclui-o nas cópias de segurança do mundo. A verificação decorre no thread do servidor e a gravação usa a persistência normal do Minecraft; não existe uma transação de disco única entre o inventário, o registo do mercado e os perfis do Chronicles durante uma falha abrupta do processo. Um registo existente inválido não é substituído silenciosamente por uma loja vazia.

## Comandos e compatibilidade

- `/markets stall`: dá os dois modelos de banca a um operador; não abre menus nem altera o proprietário de bancas existentes.
- Os jogadores normais podem fabricar, colocar e gerir as suas bancas sem serem administradores.

Chronicles RP é opcional; o intervalo declarado é **`>=0.5.2 e <0.6`**. Se instalado, requer também as dependências do próprio Chronicles. Contracts & Bounties, Realm Council, Crown Ledger e Realm Identity não são necessários para usar Realm Markets. A primeira versão não usa reputação ou decisões do governante para restringir o comércio.

[Changelog](CHANGELOG.md) · [Resultados e limites da validação](VALIDACAO.md) · [JAR](../mod-pronto/realm-markets-0.1.0.jar) · [ZIP](../mod-pronto/realm-markets-0.1.0.zip) · [Imagens](../media/realm-markets/0.1.0/README.md).

## Desenvolvimento

```bash
./gradlew build
./gradlew -PmarketGameTests runGameTestServer
# Copiar os JARs publicados de Contracts 0.3.1, Realm Council 0.2.1
# Crown Ledger 0.1.1 e Realm Identity 0.1.0 para compat-libs/ antes desta execução:
./gradlew -PmarketGameTests -PintegrationTests runGameTestServer
./gradlew -PmarketVisualTests runClient
```

Na máquina cloud usa `python3 tools/cloud_gradle.py` em vez de `./gradlew`, mantendo o proxy, os checksums e a verificação TLS. O domínio pode ser testado sem descarregar Minecraft com `python3 tools/test_domain.py`.

A publicação automatizada exige testes unitários, testes nativos independentes, uma execução nativa com os JARs publicados dos outros mods e validação do cliente Minecraft real. Os testes e as estruturas de teste ficam fora do JAR de produção. As capturas da interface usam artigos e saldos de demonstração. A integração completa com o runtime de Chronicles/Origins/Jupiter necessita de validação separada; os seus JARs originais não são redistribuídos.
