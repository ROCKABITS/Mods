# 💰 Realm Economy — Sistema Económico do Reino

## Objetivo

Criar um mod chamado **Realm Economy** para Minecraft 1.21.1, NeoForge 21.1.256+ e Java 21.

O objetivo é tornar o dinheiro do Chronicles RP essencial para várias atividades do Minecraft, substituindo certos custos Vanilla por pagamentos em euros.

O mod deve integrar-se com:
- Chronicles RP — carteira digital.
- Realm Council — Presidente eleito.
- Crown Ledger — tesouraria e impostos.
- Realm Notifications — notificações.
- Realm Admin — administração e permissões, quando disponível.

Deve funcionar em singleplayer, multiplayer LAN e servidores dedicados.

## 1. Comércio com Villagers

Alterar o sistema de comércio dos Villagers.

**Quando o jogador compra:**
- Paga com dinheiro da carteira do Chronicles RP.
- O preço original em esmeraldas é convertido para euros.
- Recebe os itens normalmente.
- É cobrado o imposto definido pelo Presidente.

**Quando o jogador vende:**
- Entrega os materiais pedidos pelo Villager.
- Recebe esmeraldas, exatamente como no Minecraft Vanilla.
- Não recebe euros.

Manter as quantidades originais das trocas Vanilla.

## 2. Valor das esmeraldas

Criar uma taxa de referência:

**1 esmeralda = 5 € por defeito.**

O Presidente pode alterar esse valor através da Mesa da Economia, respeitando limites definidos pelo administrador.

Exemplo:

20 esmeraldas × 5 € = 100 €.

Esta taxa serve apenas para calcular preços. Os jogadores não podem converter diretamente esmeraldas em euros.

Preservar os descontos, aumentos de preços, profissões, níveis e reabastecimentos dos Villagers.

## 3. Serviços pagos

Adicionar custos monetários configuráveis às seguintes funcionalidades:

**Bigorna**
- Reparação de equipamentos.
- Combinação de encantamentos.
- Renomeação de itens.

**Mesa de Encantamentos**
- Custo monetário proporcional ao nível do encantamento.
- Manter a utilização de lápis-lazúli.

**Smithing Table**
- Pagamento para melhorar equipamentos.
- Manter os materiais exigidos pelo Minecraft.

**Brewing Stand**
- Custo de produção de poções.
- Manter os ingredientes e o combustível necessários.

**Beacon**
- Pagamento para ativar efeitos durante períodos configuráveis.
- Manter a construção normal da pirâmide.

Cada sistema deve poder ser ativado ou desativado pelo administrador.

## 4. Presidente e Mesa da Economia

Criar um bloco 3D chamado **Mesa da Economia**, com uma interface medieval.

O Presidente eleito e empossado através do Realm Council pode:

- Definir o valor monetário de uma esmeralda.
- Definir os preços das reparações.
- Definir os preços dos encantamentos.
- Definir os preços das melhorias de equipamento.
- Definir os custos das poções.
- Definir os preços de ativação dos Beacons.
- Alterar as taxas de imposto de cada serviço.

Os preços devem respeitar limites mínimos e máximos definidos pelos administradores.

Quando termina o mandato, o Presidente perde imediatamente as permissões de gestão económica.

O novo Presidente herda os preços existentes e pode alterá-los.

As alterações devem ficar registadas com data, responsável e valores anteriores e novos.

## 5. Impostos do reino

Integrar com o Crown Ledger.

O Presidente define a percentagem de imposto aplicável a cada atividade.

Valores iniciais:
- Imposto padrão: 0%.
- Imposto máximo: 25%, configurável pelo administrador.

Exemplo:

Um jogador compra um item ao Villager cujo preço convertido é 100 €.

O Presidente definiu um imposto de 10%.

- Preço base: 100 €.
- Imposto: 10 €.
- Total pago: 110 €.
- Dinheiro retirado da economia: 100 €.
- Dinheiro enviado ao Crown Ledger: 10 €.

O dinheiro base desaparece da economia, enquanto o imposto financia o reino.

As vendas de materiais aos Villagers, que oferecem esmeraldas, não são tributadas.

## 6. Interface

Adaptar as interfaces necessárias para mostrar:

- Preço base em euros.
- Imposto aplicável.
- Preço total.
- Saldo do Chronicles RP.
- Aviso de saldo insuficiente.

O jogador deve conhecer o custo total antes de confirmar uma operação.

O pagamento e a entrega dos itens devem ser validados pelo servidor.

## 7. Segurança

Implementar:
- Validação de pagamentos no servidor.
- Proteção contra duplicação de itens.
- Proteção contra cobranças repetidas.
- Verificação do saldo disponível.
- Persistência após reinícios do mundo.
- Registos de alterações económicas.
- Processamento seguro de pagamentos e impostos.
- Proteção contra manipulação de preços pelo cliente.

Se Chronicles RP não estiver disponível, as funcionalidades que exigem euros ficam indisponíveis, sem substituir automaticamente o pagamento por esmeraldas.

Se Crown Ledger não estiver instalado, os impostos ficam desativados.

## 8. Integração com Realm Admin

Quando Realm Admin estiver instalado, os Owners e Administradores autorizados podem:

- Configurar limites mínimos e máximos.
- Ativar ou desativar serviços pagos.
- Corrigir configurações económicas.
- Consultar históricos administrativos.

O Presidente continua responsável pelos preços e impostos durante o seu mandato.

A administração do mod não depende de OP Vanilla quando as permissões forem atribuídas através do Realm Admin.

## 9. Desenvolvimento

Desenvolver no repositório ROCKABITS/Mods.

Incluir código-fonte, bloco 3D da Mesa da Economia, texturas, interfaces, receitas, traduções PT/EN, testes e JAR compilado.

Priorizar o desenvolvimento por fases:

**v0.1.0:** comércio dos Villagers com dinheiro e conversão de esmeraldas.

**v0.2.0:** integração com Realm Council, Mesa da Economia e impostos do Crown Ledger.

**v0.3.0:** bigorna, encantamentos, Smithing Table e poções pagas.

**v0.4.0:** Beacons, integração com Realm Admin e melhorias administrativas.

## Resultado pretendido

Criar uma economia em que o dinheiro do Chronicles RP seja gasto frequentemente durante a sobrevivência.

O Presidente governa os preços e impostos, enquanto os administradores definem os limites de segurança.

As esmeraldas continuam a ser obtidas normalmente através das vendas aos Villagers, mas deixam de ser usadas como pagamento nas compras convertidas.

**O objetivo é dar utilidade real ao dinheiro, controlar a quantidade de moeda em circulação e permitir que as decisões económicas do Presidente tenham impacto no reino.**