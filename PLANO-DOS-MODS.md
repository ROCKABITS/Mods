# Plano dos mods de roleplay

Referência: guião enviado pelo utilizador em `Pasted text.txt`. Esta página organiza a ordem de desenvolvimento e distingue as funcionalidades existentes das propostas para os próximos mods.

Plataforma: **Minecraft 1.21.1, NeoForge e Java 21**. Os mods mantêm responsabilidades próprias e integram-se quando instalados em conjunto. A identidade e a economia do Crónicas RP devem ser reutilizadas, evitando contas ou personagens concorrentes.

## Base existente

| Mod | Responsabilidade | Situação neste repositório |
| --- | --- | --- |
| Crónicas RP / Chronicles RP 0.5.2 | Personagens, profissões, reputação, dinheiro e progressão | Código-fonte e JAR fornecidos para análise; não redistribuídos aqui. |
| Realm Council 0.2.1 | Eleições, mandatos, governantes e decisões públicas | Projeto incluído para a atualização de acesso através dos blocos. |
| Contracts & Bounties 0.2.0 | Pedidos de materiais, reserva de recompensas, entregas e recolhas | Implementado neste repositório, com JAR e imagens disponíveis. |

**Correção ao guião original:** o ZIP fornecido do Realm Council contém o projeto Java. Não é necessário reconstruí-lo a partir do JAR.

## Ordem de desenvolvimento

| Etapa | Projeto | Objetivo do guião | Estado |
| --- | --- | --- | --- |
| 1 | Contracts & Bounties | Murais físicos, contratos de materiais e recompensas reservadas. | Base disponível na 0.2.0; integração real com Crónicas RP por validar. |
| 2 | Tesouraria do Reino | Orçamento, despesas públicas e contratos pagos pelo reino, geridos pelo governante. | Próximo projeto previsto; ainda sem implementação. |
| 3 | Documento de personagem | Documento físico de RP que pode ser mostrado a outros jogadores. | Planeado. |
| 4 | Bancas de mercado | Lojas com stock e vendas quando o proprietário está offline. | Planeado. |
| 5 | Guildas | Membros, cargos, sedes, cofres e contratos de grupo. | Planeado. |
| 6 | Correio de fantasia | Cartas, encomendas e pagamentos entre jogadores. | Planeado. |

As etapas seguintes representam o guião de produto; não são funcionalidades anunciadas como disponíveis no JAR atual.

## 1. Contratos: entrega atual e diferenças face ao guião

A versão 0.2.0 inclui três murais 3D, interface em inglês e português, pedidos por identificador de item, quantidade e recompensa, reserva do pagamento, entrega integral, cancelamento e recolha de materiais. Funciona com esmeraldas e tem um adaptador opcional para Crónicas RP 0.5.2.

Ao concluir um contrato, a recompensa passa a crédito do trabalhador. O jogador levanta esse crédito no mural; o saldo da carteira não é creditado automaticamente no momento da conclusão. Esta diferença face ao objetivo de pagamentos automáticos deve ser considerada numa próxima evolução.

Os créditos de jogadores offline ficam guardados no mod até à recolha. Os ficheiros offline do Crónicas RP não são alterados. A integração económica foi implementada a partir do código fornecido, mas ainda não foi executada com Crónicas RP e as suas dependências instalados.

Antes de alargar a economia a outros mods, validar o ciclo com dois jogadores, créditos e entregas pendentes após reiniciar, e o funcionamento com Crónicas RP instalado. A compilação, os testes de lógica/servidor e as capturas do cliente em modo independente estão documentados em [VALIDACAO.md](contracts-bounties/VALIDACAO.md).

## 2. Tesouraria do Reino: proposta para a primeira versão

Um mod separado para uma **tesouraria global do reino**. O Realm Council atual tem uma eleição e um mandato globais; várias cidades com governos e cofres independentes exigem um desenvolvimento posterior.

Proposta de funcionamento:

- Um cofre público físico e uma interface para consultar saldo, entradas e despesas.
- Depósitos voluntários dos jogadores, usando a economia do Crónicas RP quando disponível. Em modo independente, propor esmeraldas, seguindo o padrão dos contratos.
- Permissão de gestão ligada ao governante do Realm Council com mandato ativo e juramento prestado. Sem Realm Council, propor um responsável designado por operador.
- Despesas com valor, destino, justificação, autor e data, consultáveis num histórico público.
- Contratos de obras e fornecimentos publicados pelo governante e pagos pela tesouraria, através de integração com Contracts & Bounties.
- Saldo e histórico pertencem ao reino e mantêm-se quando muda o governante. As permissões do anterior governante terminam com o mandato.

Para contratos públicos, o sistema deve identificar a origem do dinheiro: a recompensa sai da tesouraria e um cancelamento devolve-a à mesma tesouraria. Os materiais entregues ficam disponíveis para recolha autorizada pelo reino. Não devem ser tratados como dinheiro ou materiais pessoais do governante.

A integração exige uma API de contratos com origem de financiamento e destinatário de entregas explícitos; a 0.2.0 só tem contratos de jogadores. Essa API e a tesouraria ainda não existem. As validações de mandato, fundos e inventários devem ocorrer no servidor, com proteção contra repetição de operações.

### Impostos e salários discutidos

O governante poderá escolher um imposto percentual sobre recompensas de missões/contratos ou um valor fixo periódico, dentro de limites configurados pelo administrador. Exemplo de divisão: recompensa de 20 €, taxa de 5%, 19 € para o trabalhador e 1 € para a tesouraria. O jogador vê a divisão antes da entrega e a taxa fica registada na publicação do contrato. O levantamento posterior desse dinheiro não volta a tributar a mesma recompensa.

O governante poderá também definir salários periódicos para jogadores ativos. Os pagamentos saem dos fundos do reino; a interface deve mostrar o custo dos salários e o orçamento disponível. O sistema não deve prometer salários sem financiamento nem criar dívida fiscal ou saldos negativos automaticamente.

A isenção baseada em manter pouco dinheiro na carteira foi afastada, porque permite evitar a contribuição mantendo o saldo baixo. A franquia de rendimento semanal foi discutida como alternativa, mas os exemplos de 100 € isentos e 5% não constituem valores finais nem uma regra confirmada.

### Importância dos blocos

O utilizador pediu que os blocos tenham utilidade real. No Realm Council 0.2.1, o menu público passa a abrir por interação com o quadro, a urna ou a mesa de juramento; o atalho O e os comandos de abertura remota são retirados. A tesouraria deverá seguir o mesmo princípio, usando o cofre para abrir os seus menus. Os comandos de administração do servidor continuam disponíveis a operadores.

## 3. Documento de personagem

Item físico que apresenta a identidade do Crónicas RP e pode ser mostrado a outro jogador. Na implementação, definir os dados públicos, a ligação ao personagem, a emissão e a atualização do documento. O item não deve conceder acesso à edição da identidade de outra pessoa.

## 4. Bancas de mercado

Blocos de loja com proprietário, stock, preços e recolha de receitas. As vendas devem funcionar com o proprietário offline. Compra, pagamento, entrega e atualização do stock precisam de validação conjunta no servidor, incluindo inventário cheio e compras simultâneas.

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
