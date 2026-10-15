# Crown Ledger — Tesouro da Coroa

Tesouro público para Minecraft **1.21.1**, **NeoForge 21.1.256+** e **Java 21**. Instala o mesmo JAR no servidor e nos clientes. O identificador técnico é `kingdomtreasury`.

## Instalação e primeiro uso

1. Coloca `crown-ledger-0.1.1.jar` na pasta `mods` de cada cliente e do servidor.
2. Para integrar os restantes sistemas, usa **Contracts & Bounties 0.3.1+**, **Realm Council 0.2.1+** e, opcionalmente, **Chronicles RP 0.5.2** com as suas dependências. Substitui os JARs antigos; não deixes duas versões do mesmo mod.
3. Fabrica o **Cofre da Coroa** e clica nele com o botão direito. Também aparece na aba criativa **Crown Ledger** e na pesquisa.

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

## Contratos e reputação

O separador do governante publica contratos pagos pelo tesouro. A recompensa fica reservada no Contracts, onde os trabalhadores fazem a entrega pelo mural. Os materiais pertencem ao reino e são recolhidos no cofre pelo governante atual. Cancelar devolve a recompensa total ao reino, incluindo após mudança de governante.

Impostos e reembolsos entram no tesouro ao atualizar/abrir o cofre ou no processamento periódico do servidor. O dinheiro reservado para contratos abertos continua no Contracts e não pode financiar salários ao mesmo tempo.

O governante define a reputação necessária para publicar contratos particulares, a reputação ganha nos contratos públicos e o limite diário. Os valores iniciais são **0** de requisito, **+1** por contrato público e **3** por dia UTC. A reputação exige Chronicles RP. Quem está abaixo do requisito pode cumprir contratos, receber recompensas e gerir os seus contratos já publicados. Concluir contratos públicos publicados pelo próprio não atribui reputação nem missões.

## Moedas e persistência

Sem Chronicles, a moeda normal é a esmeralda inteira. Com Chronicles, os valores digitais usam cêntimos e o perfil original do jogador. Cada moeda tem saldo e créditos separados; não existe conversão automática. O separador de donativos permite escolher a moeda. Nas regras, o governante pode escolher a moeda fiscal; mudar essa escolha repõe os campos de salário e imposto fixo em zero, sem converter saldos nem créditos anteriores. Os pagamentos de personagens offline ficam pendentes, sem editar os seus ficheiros de perfil.

O ficheiro `data/crown_ledger.dat` guarda regras, saldo disponível, salários reservados, atividade, ciclo pendente e os últimos 256 movimentos. Os contratos usam também `data/contracts_ledger.dat`. As cópias de segurança devem incluir o mundo completo e os perfis do Chronicles. As transferências entre mods são verificadas no mesmo thread do servidor; estes ficheiros continuam a ser guardados pelo mecanismo normal de mundos do Minecraft, sem uma transação de disco única entre mods.

## Desenvolvimento e validação

```bash
./gradlew build
./gradlew -PcrownGameTests runGameTestServer
mkdir -p compat-libs
# Copiar os JARs testados de Contracts 0.3.1 e Realm Council 0.2.1 para compat-libs.
./gradlew -PcrownGameTests -PintegrationTests runGameTestServer
./gradlew -PcrownVisualTests runClient
```

Na máquina cloud, usa `python3 tools/cloud_gradle.py` em vez de `./gradlew`; mantém o proxy e a verificação TLS configurados. O domínio independente corre com `python3 tools/test_domain.py` sem descarregar o Minecraft. Os testes nativos e visuais são opcionais no desenvolvimento e excluídos do JAR distribuído. A publicação automatizada exige que todos passem.

O CI testa também os JARs reais de Contracts e Realm Council, não imitações dessas APIs. As imagens são capturas do cliente Minecraft com o modelo distribuído e os menus reais; os saldos das imagens são dados de demonstração. A integração completa com o runtime de Chronicles/Origins requer os respetivos JARs em `compat-libs` e não está incluída neste CI.
