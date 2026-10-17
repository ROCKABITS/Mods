# Contracts & Bounties — 0.3.2

Mod para Minecraft **1.21.1**, **NeoForge 21.1.256** e **Java 21**, instalado no cliente e no servidor.

[Descarregar JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/contracts-bounties-0.3.2.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/contracts-bounties-0.3.2.zip) · [Changelog em inglês](CHANGELOG.md) · [Validação](VALIDACAO.md).

A 0.3.2 mantém os três murais e acrescenta acesso físico obrigatório, menus de reputação e imposto, contratos públicos e uma API para o Crown Ledger — Tesouro da Coroa.

**A tesouraria está disponível no mod separado [Crown Ledger](../crown-ledger/README.md).** As suas regras ficam ativas quando esse mod está instalado. Sem integração, os contratos particulares continuam disponíveis com **0% de imposto e reputação mínima 0**. O Crónicas RP 0.5.2 fornece dinheiro e reputação quando instalado; sem ele, os pagamentos usam esmeraldas.

Com o **[Realm Notifications](../realm-notifications/README.md) 0.1.0+** no servidor e no cliente, os avisos aparecem no HUD medieval. Sem o canal opcional, continuam a ser apresentados no chat. A integração apenas apresenta acontecimentos confirmados; não modifica saldos, regras ou permissões.

## Atualizar e abrir

1. Guardar uma cópia do mundo antes de atualizar. Fechar o jogo e o servidor e substituir o JAR antigo, mantendo uma única versão do mod.
2. Atualizar cliente e servidor juntos para a 0.3.2: o protocolo de rede mantém-se em **2**.
3. Colocar um mural e interagir com ele. A abertura remota por `/contracts` foi retirada. O jogador precisa de permanecer na mesma dimensão e a oito blocos da estrutura intacta; o menu verifica a sessão a cada cinco segundos e as ações são verificadas no servidor. Ao fim de cinco minutos, voltar a interagir com o mural.

Os três murais aparecem na aba **Contracts & Bounties** e na pesquisa. Operadores podem obter o pequeno com `/contracts board` ou os três com `/contracts boards`. Estes comandos entregam itens e não abrem o menu.

| Mural | Identificador | Largura × altura |
| --- | --- | --- |
| Pequeno | `contracts:contract_board` | 1 × 1 bloco |
| Com telhado | `contracts:tall_contract_board` | 1 × 2 blocos |
| Grande | `contracts:wide_contract_board` | 2 × 2 blocos |

Os murais cobertos precisam de chão firme e espaço para a estrutura. Todas as partes abrem o mesmo mural; partir uma secção remove a estrutura e devolve um único item em sobrevivência. As receitas e os identificadores da 0.2.0 mantêm-se.

O pequeno usa três papéis na linha superior e seis tábuas de carvalho nas duas inferiores. O coberto usa três lajes de tijolos no topo, papel–mural pequeno–papel ao centro e dois troncos de carvalho nos cantos inferiores. Dois murais cobertos lado a lado dão um grande.

## Contratos, impostos e reembolsos

O autor publica um pedido por identificador de item, quantidade e recompensa. A recompensa total é retirada e fica reservada. Outro jogador entrega todos os materiais de uma vez e recebe um crédito para levantar no mural; o autor recolhe os materiais. O autor de um contrato particular não pode concluí-lo.

Quando a Tesouraria fornecer uma taxa, esta fica guardada na publicação. Exemplo: **20 € com imposto de 5% = 19 € de crédito do trabalhador e 1 € de crédito do reino**. O menu mostra recompensa total, imposto e valor líquido antes da entrega. Levantar o crédito não cobra novo imposto.

A taxa é calculada em unidades inteiras, arredondada por defeito: cêntimos no Crónicas RP e esmeraldas inteiras no modo independente. Cancelar um contrato aberto devolve o valor total reservado, sem imposto.

A migração conserva os contratos antigos com imposto zero e sem atribuição retroativa de reputação. Uma nova regra de reputação só afeta novas publicações; concluir, cancelar contratos próprios e recolher continuam disponíveis.

São aceites itens comuns por ID, como `minecraft:oak_log`, sem nomes, encantamentos ou outros componentes personalizados. Usam-se os 36 espaços principais do inventário, sem consumir equipamento ou a mão secundária. Recolhas parciais preservam o que não couber. Limites atuais: 16 contratos abertos por autor, 1–4096 materiais por contrato e 2048 entradas no arquivo; a rotação do histórico preserva entregas e créditos pendentes.

## Reputação e missões do Crónicas RP

A regra de publicação é fornecida pelo Crown Ledger, de 0 a 100 de reputação. Quando for positiva, exige um perfil Crónicas RP válido; uma integração em falta ou incompatível não autoriza a publicação por engano. O menu apresenta a reputação atual, o mínimo e o motivo de um bloqueio, mantendo disponíveis as entregas e recolhas.

Contratos públicos podem atribuir reputação e aumentar o contador de missões do Chronicles. Os valores ficam guardados no contrato; a política inicial da API usa **+1 de reputação e limite de 3 pontos por dia UTC**. O contador de missões continua a aumentar quando o limite diário de reputação já foi atingido. Os limites do perfil são 100 de reputação e um milhão de missões.

Contratos particulares e trabalhos públicos concluídos pelo próprio emissor não atribuem este progresso. Cada conclusão só é processada uma vez. O limite diário persiste após reiniciar e usa o dia real UTC, independente do comando `/time`.

Quando o perfil ou a integração estiverem indisponíveis, o progresso fica pendente no servidor. É tentado novamente na abertura e atualização do mural; uma falha de progresso não retira um pagamento já concluído. A atualização do perfil preserva identidade, dinheiro, profissão e medalhas.

Esta versão prepara ganhos de reputação. Aceitação, reservas de missões, prazos e penalizações por abandono pertencem a uma evolução posterior.

## Contratos públicos e conta do reino

A API separa o reino dos jogadores. O valor reservado sai da conta pública; cancelamentos devolvem-no ao reino e os materiais ficam guardados nessa conta. A autorização é consultada em cada pedido, permitindo que o sucessor recolha ou cancele contratos de outro mandato.

A publicação e a gestão públicas estão disponíveis no menu do Cofre da Coroa. O mural comum permite ver e cumprir esses contratos, mas não transfere a propriedade para o governante nem permite cancelar contratos públicos como particulares.

A API usa apenas depósitos debitados de inventários/carteiras reais, créditos de impostos e reembolsos existentes. Não há uma operação pública para criar saldo ou levantar fundos do reino para a carteira pessoal. Ver [API da Tesouraria](TREASURY_API.md).

## Compilar e validar

Com Java 21:

```bash
./gradlew build
./gradlew -PcontractGameTests runGameTestServer
./gradlew -PboardVisualTests runClient
```

O último comando precisa de ambiente gráfico; o cliente verifica os modelos e a interface, captura seis imagens e termina. Os fixtures de teste não entram no JAR de produção.

Testes de domínio sem descarregar Minecraft:

```bash
python3 tools/test_domain.py
```

Os helpers `tools/bootstrap_cloud.py` e `tools/cloud_gradle.py` preparam o JDK e a ligação segura do proxy deste ambiente. O proxy atual continua a impedir downloads NeoForge/Mojang; a compilação completa é executada no GitHub Actions. As verificações e limitações estão em [VALIDACAO.md](VALIDACAO.md).

## Dados e recuperação

O ficheiro continua a ser `data/contracts_ledger.dat` no mundo, agora com formato **2**. O formato 1 é migrado sem alterar os contratos existentes; a 0.2.0 não lê o formato 2, pelo que regressar à versão anterior exige recuperar a cópia do mundo.

Saldos, impostos, entregas, progresso pendente e limites diários são persistentes. Ficheiros inválidos não são substituídos por um arquivo vazio. Os salvamentos normais do Minecraft são usados; não existe transação atómica de disco entre o arquivo de contratos e os dados dos jogadores. Backups continuam a ser a forma de recuperação de interrupções abruptas durante gravações.

Os modelos e texturas são originais e usam JSON nativo. `tools/generate_board_assets.py` reproduz os recursos; `tools/render_board_preview.py` produz uma prévia externa com Pillow e NumPy. As imagens da galeria são capturas do cliente real, com contratos e regras de demonstração na interface de teste.
