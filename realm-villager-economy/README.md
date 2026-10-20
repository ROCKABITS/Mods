# Realm Villager Economy

Este projeto foi integrado no [Realm Economy 0.4.0](../realm-economy/README.md). A nova instalação substitui o JAR Villager Economy; a versão 0.1.0 permanece arquivada.

**Minecraft 1.21.1 · NeoForge 21.1.256+ · Java 21 · 0.1.0 Beta**

As compras aos aldeões utilizam a carteira digital do **Chronicles RP 0.5.4**. As vendas de materiais continuam a pagar **esmeraldas físicas**. O Presidente empossado pode acrescentar um imposto público, recebido pelo **Crown Ledger 0.2.4+**.

Instala o mesmo JAR nos clientes e no servidor, incluindo mundos abertos por LAN. Substitui versões antigas do mesmo mod. Para usar impostos, atualiza também Crown Ledger para 0.2.4. Realm Council 0.2.2+ é opcional para identificar o Presidente.

## Negociar

Clica num aldeão ou comerciante ambulante adulto. O livro de comércio mostra as ofertas reais, o resultado, a quantidade e o stock. Escolhe uma oferta e confirma uma troca de cada vez. Os materiais saem dos 36 espaços principais do inventário; armadura e mão secundária não são vendidas automaticamente.

Nas compras, o menu apresenta separadamente:

- **Preço base:** custo atual em esmeraldas × conversão configurada.
- **Imposto público:** percentagem efetiva sobre o preço base.
- **Total a pagar:** soma dos dois valores.
- **Materiais adicionais:** por exemplo, o livro necessário para comprar um livro encantado.

A conversão inicial é **1 esmeralda = 5 €**. Uma compra de 20 esmeraldas custa 100 € de base. Com imposto de 10%, pagas 110 €: 100 € saem da economia e 10 € ficam no tesouro público. O Presidente não recebe esse dinheiro na carteira.

As esmeraldas físicas do inventário não substituem a carteira nas compras. Sem Chronicles ou personagem criada, as compras digitais ficam indisponíveis; as vendas por esmeraldas e as trocas de materiais continuam disponíveis.

Se a oferta, os descontos, a conversão ou a taxa mudar após a consulta, o pedido antigo é recusado sem cobrança. O menu apresenta os valores novos e pede nova confirmação. Se faltar saldo, materiais, espaço ou stock, a operação não começa.

## Vendas, descontos e profissões

Vender materiais por esmeraldas não debita a carteira nem paga imposto. As esmeraldas recebidas continuam a ser itens físicos. Não há conversão automática dessas esmeraldas em dinheiro digital.

O preço usa `MerchantOffer.getCostA()` e o segundo custo Vanilla. Mantém descontos de reputação do aldeão, cura e Herói da Aldeia, aumentos por procura, componentes dos itens, profissões, experiência, níveis, stock e reposição Vanilla. Os materiais do segundo custo continuam necessários. As ofertas de outros mods que utilizem o sistema normal de `AbstractVillager` e `MerchantOffer` são lidas diretamente; NPCs com menus/sistemas próprios podem exigir integração adicional.

O aldeão reserva a interação para um jogador de cada vez. A sessão termina ao fechar, afastar-se mais de quatro blocos, desligar ou ficar inativo durante cinco minutos. O mod não gera aldeias nem altera as profissões ou a inteligência artificial dos aldeões.

## Definir o imposto

O separador **Imposto público**, no próprio menu do aldeão, aparece apenas ao Presidente com mandato ativo e juramento concluído, ou a um administrador com nível de permissão 2 para acesso de emergência.

- A taxa inicial é **0%**.
- O limite inicial do administrador é **25%**.
- Aceita percentagens com até duas casas decimais.
- O mandato é verificado a cada alteração. Um vencedor ainda não empossado ou um antigo Presidente não pode alterar a taxa.
- Sem Presidente, mantém-se a última taxa aprovada.
- Sem Realm Council, apenas administradores podem gerir o imposto.
- Sem Crown Ledger, a taxa efetiva é zero e cobra-se apenas a base. A aprovação permanece guardada.

As alterações guardam data, UUID e nome do autor e percentagem. O registo local conserva as últimas 512 aprovações; o histórico público do Crown mostra as aprovações e os pagamentos confirmados. Quando Crown está temporariamente indisponível, os registos e pagamentos pendentes ficam guardados para repetição segura.

## Configuração do administrador

`config/realmvillagereconomy-server.json`:

```json
{
  "centsPerEmerald": 500,
  "maximumTaxBasisPoints": 2500
}
```

`500` significa 5 €. `2500` pontos base significa 25%; `1000` significa 10%. O limite pode variar entre 0 e 10000 (100%). A conversão é um número inteiro positivo de cêntimos. Uma configuração inválida bloqueia as trocas em vez de aplicar silenciosamente outros preços.

Depois de editar, um operador pode usar `/villagereconomy reload`, ou reiniciar o servidor. O comando apenas atualiza a configuração; não abre menus nem autoriza jogadores comuns. Reduzir o limite aplica o novo teto à taxa efetiva, conservando a última percentagem aprovada e o seu histórico.

Todos os cálculos são inteiros. O imposto é arredondado **para baixo ao cêntimo**, uma única vez sobre a base da troca confirmada. Exemplo: 0,03 € a 50% produz 0,01 € de imposto.

## Segurança e persistência

`data/realm_villager_economy.dat` guarda a taxa, aprovações, revisões dos aldeões, transferências pendentes e uma intenção persistente quando existe uma compra interrompida.

O servidor prepara o saldo e o inventário completos, grava a intenção, confirma inventário, carteira e recibo nos dados do jogador e só depois conclui a revisão do stock. O anfitrião singleplayer/LAN confirma também `level.dat/Data/Player`, além de `playerdata/UUID.dat`. Um aldeão carregado de um chunk antigo recebe a revisão confirmada, impedindo restaurar o stock de uma compra já paga. As reposições posteriores do Vanilla são preservadas.

O imposto financiado fica reservado até Crown confirmar o mesmo recibo. Repetir a entrega à tesouraria não repete o débito, a entrega ou a receita. Uma falha ou estado ambíguo conserva a intenção e suspende as trocas; não tenta adivinhar o saldo nem entregar outra vez. Pode ser necessário recuperar o armazenamento e voltar a entrar. Não edita inventários nem carteiras de jogadores offline.

Receitas, conquistas, estatísticas, sons e avisos são processados depois da confirmação financeira. Realm Rewards pode pagar uma conquista legítima decorrente da troca; esse rendimento é independente do preço. Efeitos secundários, como orbes de experiência ou avisos, não são repetidos durante a recuperação de uma interrupção. As gravações usam substituição atómica e sincronização de ficheiros; a sincronização de diretórios depende do sistema operativo.

Conserva cópias conjuntas do mundo, perfis, registo dos aldeões e tesouro. Não voltes a Crown 0.2.3 após guardar o formato 5 da tesouraria sem restaurar uma cópia anterior. Os registos têm limites de capacidade; ao atingi-los, o mod protege as compras bloqueando novas operações que não consiga registar.

## Desenvolvimento

```sh
python3 tools/bootstrap_cloud.py
bash tools/run-domain-tests.sh
python3 tools/cloud_gradle.py build --console=plain
python3 tools/cloud_gradle.py -PvillagerGameTests runGameTestServer --console=plain
```

A suíte integrada adiciona `-PintegrationTests` e os JARs verificados definidos em `.github/scripts/villager_release.py`. `-PvillagerVisualTests -PintegrationTests runClient` abre o cliente real de demonstração. Os fixtures são excluídos dos JARs publicados.

[Changelog](CHANGELOG.md) · [Validação](VALIDACAO.md) · [JAR](../mod-pronto/realm-villager-economy-0.1.0.jar) · [ZIP](../mod-pronto/realm-villager-economy-0.1.0.zip) · [Imagens](../media/realm-villager-economy/README.md).
