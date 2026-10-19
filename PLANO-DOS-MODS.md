# Plano dos mods de roleplay

Referências: guião enviado pelo utilizador em `Pasted text.txt` e decisões posteriores na conversa. Esta página distingue as funcionalidades existentes dos projetos futuros.

Plataforma: **Minecraft 1.21.1, NeoForge e Java 21**. Os mods mantêm responsabilidades próprias e integram-se quando instalados em conjunto. A identidade e a economia do Crónicas RP devem ser reutilizadas, evitando contas ou personagens concorrentes.

## Base existente

| Mod | Responsabilidade | Situação neste repositório |
| --- | --- | --- |
| Crónicas RP / Chronicles RP | Personagens, profissões, reputação, dinheiro e progressão | Projeto importado em `chronicles-rp/` para a atualização de nomes RP autorizada pelo utilizador. O JAR original 0.5.2 continua apenas como referência local. |
| Realm Council 0.2.1 | Eleições, mandatos, governantes e decisões públicas | Projeto incluído para a atualização de acesso através dos blocos. |
| Contracts & Bounties 0.3.1 | Pedidos de materiais, reserva de recompensas, entregas e recolhas | JAR disponível, integrado com as políticas do Crown Ledger. |

**Correção ao guião original:** o ZIP fornecido do Realm Council contém o projeto Java. Não é necessário reconstruí-lo a partir do JAR.

## Ordem de desenvolvimento

| Etapa | Projeto | Objetivo do guião | Estado |
| --- | --- | --- | --- |
| 1 | Contracts & Bounties | Murais físicos, contratos de materiais e recompensas reservadas. | 0.3.1 disponível; integrado com as políticas e o financiamento do Crown Ledger. Runtime completo do Chronicles por validar. |
| 2 | Crown Ledger — Tesouro da Coroa | Orçamento, impostos, salários e contratos pagos pelo reino, geridos pelo governante. | Implementado; [guia e downloads](crown-ledger/README.md). |
| 3 | Realm Identity | Documento físico de RP, assinatura e apresentação a outros jogadores com verificação do servidor. | Implementado; [guia e downloads](realm-identity/README.md). |
| 4 | Realm Markets | Bancas com stock real, preços por unidade e vendas quando o proprietário está offline. | Implementado; [guia e downloads](realm-markets/README.md). |
| — | Guildas | Membros, cargos, sedes, cofres e contratos de grupo. | O utilizador decidiu não desenvolver nesta fase. |
| — | Correio de fantasia | Cartas e encomendas entre jogadores. | Ideia sugerida; sem desenvolvimento agendado. |

O quadro abaixo distingue as funcionalidades já implementadas das ideias ainda futuras.

## Novos projetos do guião

| Projeto | Objetivo | Estado |
| --- | --- | --- |
| Realm Rewards | Recompensar conquistas Vanilla e de outros mods com dinheiro do Chronicles RP. | Implementado e atualizado para 0.1.2; [guia](realm-rewards/README.md). |
| Realm Notifications | Notificações no ecrã e API comum para os mods de RP. | Implementado na 0.1.0; [guia](realm-notifications/README.md). |
| Realm Titles | Títulos de personagem atribuídos pelo governante eleito. | Implementado na 0.1.0 Beta; [guia](realm-titles/README.md). |
| Realm Casino | Casino medieval com máquinas e mesas, banca própria de cada proprietário e dinheiro virtual do Chronicles RP. | Atualizado para 0.2.0 Beta, com seis jogos e três novos blocos. [Guia](realm-casino/README.md). |
| Laws & Justice | Prisões construídas pelos jogadores, justiça pelo Presidente, mandados, trabalho comunitário e economia judicial, por fases. | 0.1.0 Beta em validação final: prisões/celas, penas online, libertação e administração física. Publicação pendente. [Guia](laws-justice/README.md) · [plano completo](laws-justice/PLANO-COMPLETO.md). |
| Chronicles RP — Nomes RP | Mostrar o nome da personagem acima da cabeça, no TAB e no chat. | Implementado na 0.5.3; [guia e downloads](chronicles-rp/README.md). |
| Xaero Map Regions + Xaero's World Map | Desenhar e consultar territórios no mapa. | Solução externa indicada pelo utilizador; não desenvolver um mod próprio. |

O imposto de vendas foi implementado no Crown Ledger e Realm Markets 0.2.0; os resultados de execução ficam nos guias e ficheiros de validação dos dois projetos. A atualização dos nomes RP foi concluída no Chronicles 0.5.3. O utilizador escolheu desenvolver Realm Rewards primeiro, seguido de Realm Notifications e Realm Titles. O Rewards está implementado e atualizado para 0.1.2. O Realm Notifications está implementado na 0.1.0, com integração nos seis mods existentes. O Realm Titles está implementado na 0.1.0 Beta, acompanhado pelo Realm Identity 0.1.1 para mostrar os títulos atuais nos documentos.

### Realm Rewards

- Detetar a conclusão de Advancements normais do Minecraft Vanilla e dos outros mods.
- Configurar o valor de cada recompensa num menu de administração.
- Reutilizar a carteira do Chronicles RP e atribuir dinheiro por conquista concluída.
- Guardar o pagamento de cada conquista por jogador para impedir recompensas repetidas.
- Reconhecer conquistas de novos mods que utilizem o sistema normal de Advancements.

### Realm Notifications

- Mostrar o montante recebido e o motivo, por exemplo, `+50 € por completar uma conquista`.
- Suportar avisos de contratos, eleições, vendas e salários.
- Permitir escolher a posição, incluindo canto superior e centro superior.
- Configurar animações, sons e duração.
- Prever histórico e disponibilizar uma API para os restantes mods.

### Realm Titles

- Permitir ao governante eleito pelo Realm Council criar, atribuir e remover títulos personalizados.
- Associar vários títulos à personagem, como Cavaleiro, Duque, Barão e Conselheiro.
- Suportar títulos permanentes ou temporários.
- Gerir nomeações na Mesa do Selo Real e mostrar os títulos atuais na consulta do Realm Identity 0.1.1, sem reemitir o documento.

### Realm Casino — primeira fase

- Slots de três rolos, roleta europeia e blackjack contra a banca, cada um com bloco, modelos, animações, sons e interface próprios.
- Proprietário, nome, banca e limites de aposta independentes por máquina ou mesa. Os menus abrem pela interação com os blocos.
- Usar apenas a carteira virtual do Chronicles RP; sem ela, permitir consultar modelos e regras, mantendo as operações económicas desativadas.
- Confirmar os resultados no servidor, reservar o maior prémio possível antes da aposta e guardar os prémios como quantias por receber, recolhidas presencialmente.
- Realm Notifications opcional. Impostos do Crown Ledger e os restantes jogos do guião ficam para fases futuras; esta versão não inclui poker nem jogos entre jogadores.

A **0.1.0 Beta está publicada**, com [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-casino-0.1.0.zip) e [galeria](media/realm-casino/0.1.0/README.md). Os resultados dessa fase estão em [VALIDACAO-0.1.0.md](realm-casino/VALIDACAO-0.1.0.md) e na proveniência do JAR.

#### Realm Casino — atualização 0.2.0

Publicados na **0.2.0 Beta** ([JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-casino-0.2.0.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-casino-0.2.0.zip)): Plinko, Roda da Fortuna e Travessia Real (galinha), com três blocos próprios, interfaces, animações, integração com a carteira, reservas e recolha automática da travessia.

#### Realm Casino — próximas atualizações

Sem ordem nem versões definidas:

- Slots de cinco rolos.
- Poker e jogos entre jogadores, dados e cara ou coroa.
- Corridas de cavalos com apostas.
- Integração de impostos com o Crown Ledger.

### Laws & Justice — desenvolvimento por versões

A fase **0.3.0 Beta está publicada**, incluindo a base das prisões, o Presidente como juiz e os seis trabalhos prisionais. Os [links do ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/laws-justice-0.3.0.zip) e [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/laws-justice-0.3.0.jar) permitem descarregar a versão testada. O [guia](laws-justice/README.md) explica a utilização e a [galeria](media/laws-justice/README.md) reúne os modelos e menus reais do cliente.

- **0.1.0:** prisões e celas delimitadas pelo administrador em construções dos jogadores, penas que contam apenas online, regresso à prisão após morte/teleporte e libertação automática. Quatro blocos — Porta de Cela, Grades de Prisão, Fechadura Reforçada e Controlo da Prisão — e um Agrimensor Prisional. Menus físicos; só administradores autorizam decisões nesta fase. Chronicles e Notifications são opcionais para nomes RP e avisos.
- **0.2.0:** Presidente eleito e empossado pelo Realm Council como juiz, Mesa de Justiça, algemas e mandados físicos validados no servidor. Não criar cargos adicionais de juiz, guarda ou chefe da prisão. O mandato determina a autoridade, mantendo acesso administrativo de emergência.
- **0.3.0:** Trabalho Comunitário opcional, zonas de mina, horta, oficina, armazém e pátio, postos e entregas validadas. Redução máxima padrão de 50% da pena original, com progresso persistente e prevenção de repetição de blocos/entregas ou materiais de criativo.
- **0.4.0:** multas, fianças, registos criminais, documentos e integrações adicionais. Carteira do Chronicles ou esmeraldas quando ausente; pagamentos para Crown Ledger quando instalado. A falta de dinheiro não gera automaticamente uma nova pena.

**A prisão é sempre construída pelos jogadores.** O mod fornece blocos, seleção de áreas e gestão, sem gerar edifícios. Penas e dados persistem em mundos dedicados e LAN; não há contagem offline. O [plano completo das 13 secções](laws-justice/PLANO-COMPLETO.md) conserva os requisitos do utilizador, incluindo as seis tarefas de exemplo, as zonas, os mandados, as permissões e as integrações futuras.

### Chronicles RP — Nomes RP

- Substituir visualmente o username pelo nome da personagem acima da cabeça, no TAB e no chat.
- Sem personagem criada, manter o username habitual.
- Preservar o username real e o UUID usados internamente pelo Minecraft.
- Manter esta funcionalidade no Chronicles RP, separada dos títulos.
- Verificar a compatibilidade das interfaces de outros mods; a alteração visual não garante que todas usem automaticamente o nome RP.

## 1. Contratos: entrega atual e diferenças face ao guião

A versão 0.3.0 inclui três murais 3D, interface em inglês e português, pedidos por identificador de item, quantidade e recompensa, reserva do pagamento, entrega integral, cancelamento e recolha de materiais. Funciona com esmeraldas e tem um adaptador opcional para Crónicas RP 0.5.2.

Ao concluir um contrato, a recompensa passa a crédito do trabalhador. O jogador levanta esse crédito no mural; o saldo da carteira não é creditado automaticamente no momento da conclusão. Esta diferença face ao objetivo de pagamentos automáticos deve ser considerada numa próxima evolução.

Os créditos de jogadores offline ficam guardados no mod até à recolha. Os ficheiros offline do Crónicas RP não são alterados. A integração económica foi implementada a partir do código fornecido, mas ainda não foi executada com Crónicas RP e as suas dependências instalados.

Antes de alargar a economia a outros mods, validar o ciclo com dois jogadores, créditos e entregas pendentes após reiniciar, e o funcionamento com Crónicas RP instalado. A compilação, os testes de lógica/servidor e as capturas do cliente em modo independente estão documentados em [VALIDACAO.md](contracts-bounties/VALIDACAO.md).

## 2. Crown Ledger: tesouraria implementada

A implementação e as regras finais estão no [guia do Crown Ledger](crown-ledger/README.md). A descrição abaixo conserva o guião de produto original; despesas arbitrárias para destinatários individuais continuam fora desta versão, que financia salários e contratos públicos.

Um mod separado para uma **tesouraria global do reino**. O Realm Council atual tem uma eleição e um mandato globais; várias cidades com governos e cofres independentes exigem um desenvolvimento posterior.

Proposta de funcionamento:

- Um cofre público físico e uma interface para consultar saldo, entradas e despesas.
- Depósitos voluntários dos jogadores, usando a economia do Crónicas RP quando disponível. Em modo independente, propor esmeraldas, seguindo o padrão dos contratos.
- Permissão de gestão ligada ao governante do Realm Council com mandato ativo e juramento prestado. Sem Realm Council, propor um responsável designado por operador.
- Despesas com valor, destino, justificação, autor e data, consultáveis num histórico público.
- Contratos de obras e fornecimentos publicados pelo governante e pagos pela tesouraria, através de integração com Contracts & Bounties.
- Saldo e histórico pertencem ao reino e mantêm-se quando muda o governante. As permissões do anterior governante terminam com o mandato.

Para contratos públicos, o sistema deve identificar a origem do dinheiro: a recompensa sai da tesouraria e um cancelamento devolve-a à mesma tesouraria. Os materiais entregues ficam disponíveis para recolha autorizada pelo reino. Não devem ser tratados como dinheiro ou materiais pessoais do governante.

A 0.3.0 acrescenta a [API de contratos](contracts-bounties/TREASURY_API.md), com conta do reino, origem de financiamento, emissor e destinatário de entregas distintos. O Crown Ledger regista o fornecedor das regras e permissões. Sem ele, os contratos particulares usam imposto 0% e mínimo de reputação 0. As validações de mandato, fundos e inventários ocorrem no servidor, com proteção contra repetição de operações.

### Impostos e salários discutidos

O governante poderá escolher um imposto percentual sobre recompensas de missões/contratos ou um valor fixo periódico, dentro de limites configurados pelo administrador. Exemplo de divisão: recompensa de 20 €, taxa de 5%, 19 € para o trabalhador e 1 € para a tesouraria. O jogador vê a divisão antes da entrega e a taxa fica registada na publicação do contrato. O levantamento posterior desse dinheiro não volta a tributar a mesma recompensa.

O governante poderá também definir salários periódicos para jogadores ativos. Os pagamentos saem dos fundos do reino; a interface deve mostrar o custo dos salários e o orçamento disponível. O sistema não deve prometer salários sem financiamento nem criar dívida fiscal ou saldos negativos automaticamente.

A isenção baseada em manter pouco dinheiro na carteira foi afastada, porque permite evitar a contribuição mantendo o saldo baixo. A franquia de rendimento semanal foi discutida como alternativa, mas os exemplos de 100 € isentos e 5% não constituem valores finais nem uma regra confirmada.

### Reputação e publicação de missões

Foi aprovada a preparação do Contracts antes de desenvolver a Tesouraria. A 0.3.0 permite que o fornecedor da Tesouraria defina a reputação mínima para publicar contratos particulares; o governante configura essa regra no Cofre da Coroa. Todos podem continuar a cumprir contratos, recolher recompensas e cancelar os próprios pedidos, mesmo abaixo do mínimo.

Os contratos públicos podem atribuir reputação e incrementar o contador de missões do Chronicles. A política inicial da API usa +1 de reputação e limite de 3 pontos por dia UTC; os valores podem ser fornecidos pela integração. Contratos particulares e trabalhos do próprio emissor não dão este progresso. Perdas por abandono ficam para uma fase com aceitação, reservas e prazos explícitos.

### Importância dos blocos

O utilizador pediu que os blocos tenham utilidade real. No Realm Council 0.2.1, o menu público passa a abrir por interação com o quadro, a urna ou a mesa de juramento; o atalho O e os comandos de abertura remota são retirados. A tesouraria deverá seguir o mesmo princípio, usando o cofre para abrir os seus menus. Os comandos de administração do servidor continuam disponíveis a operadores.

## 3. Documento de personagem

Item físico que apresenta a identidade do Crónicas RP e pode ser mostrado a outro jogador. Na implementação, definir os dados públicos, a ligação ao personagem, a emissão e a atualização do documento. O item não deve conceder acesso à edição da identidade de outra pessoa.

## 4. Bancas de mercado

O [Realm Markets 0.2.0](realm-markets/README.md) implementa duas bancas medievais de quatro partes, com toldos carmesim e verde-salva, oito ofertas por banca e preços por unidade. O proprietário deposita artigos reais e recolhe os ganhos presencialmente; as compras continuam disponíveis enquanto está offline. O servidor valida pagamento, espaço no inventário, stock e a correspondência do produto e preço apresentados ao comprador.

Funciona com esmeraldas de forma independente e tem integração opcional com a carteira do Chronicles. A 0.2.0 permite ao governante definir no Crown Ledger uma percentagem própria sobre as vendas, independente do imposto dos contratos. O preço bruto pago pelo comprador é dividido entre o líquido do vendedor e a tesouraria. A taxa começa em 0%, e ganhos anteriores e recolhas não são tributados novamente. O cálculo usa unidades inteiras, arredondando para baixo o imposto do total de cada compra. As duas versões devem ser atualizadas juntas quando usadas no mesmo servidor. Esta versão vende itens sem dados personalizados.

## 5. Guildas

Organizações com membros, cargos, sede, cofre e contratos de grupo. Definir permissões por cargo e a titularidade dos fundos e entregas da guilda antes de ligar o sistema aos contratos e às lojas.

## 6. Correio de fantasia

Cartas e encomendas entre jogadores, incluindo pagamentos. Prever destinatários offline, materiais pendentes e recolhas com inventário cheio. Definir primeiro se os pagamentos são envios de dinheiro, cobrança na entrega ou ambos.

## Critérios comuns

- O servidor decide permissões, pagamentos, inventários e conclusões; o cliente apresenta a interface.
- Dinheiro e materiais pendentes persistem quando os jogadores saem ou o servidor reinicia normalmente.
- Cada operação só pode produzir um pagamento ou uma entrega.
- Ausência de uma integração opcional tem comportamento definido; falhas numa integração carregada não devem mudar silenciosamente de moeda.
- Recursos gráficos originais, com receitas, inventário criativo, pesquisa e traduções em inglês e português.
- Cada JAR publicado deve identificar claramente as funcionalidades disponíveis e as integrações efetivamente testadas.

[Voltar ao repositório](README.md) · [Contratos](contracts-bounties/README.md) · [Galeria](media/README.md)
