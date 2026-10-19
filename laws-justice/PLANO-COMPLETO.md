# Laws & Justice — plano completo aprovado

Este documento conserva o pedido funcional completo do projeto. É um **plano por versões**, não uma lista de funcionalidades já presentes no JAR 0.1.0. O [README](README.md) descreve o conteúdo efetivo da versão atual, incluindo as quatro fases até 0.4.0. Este plano conserva o pedido original.

## 1. Objetivo e sistema de prisões

Criar **Laws & Justice** como mod separado no repositório `ROCKABITS/Mods`, para Minecraft 1.21.1, NeoForge e Java 21. Acrescentar justiça medieval com prisões, algemas, mandados de captura, julgamentos, multas, fianças, registos criminais e trabalho prisional para reduzir penas.

Deve funcionar em multiplayer, incluindo mundos abertos por **LAN**, sem exigir servidor dedicado. A prisão é uma construção dos jogadores: pode ser uma pequena masmorra, um castelo ou uma penitenciária com minas, hortas e oficinas. **Não gerar automaticamente uma prisão completa.**

Fornecer blocos 3D próprios para Porta de Cela, Grades de Prisão, Fechadura Reforçada, Controlo da Prisão, Quadro de Trabalhos, Caixa de Entrega, Postos de Trabalho, Mesa de Justiça e Livro de Registos.

Um administrador regista a construção, delimita a prisão, configura celas e estabelece zonas de trabalho. Um condenado é enviado para a cela e permanece na prisão até cumprir a pena. A contagem avança **apenas enquanto estiver online**. Morte, teleporte, desconexão ou reinício normal do mundo não anulam a pena. A libertação é automática quando o tempo restante chega a zero.

## 2. Presidente como juiz

O juiz é **o Presidente eleito e empossado através do Realm Council**. Não criar cargos adicionais de juiz, guarda ou chefe da prisão.

O Presidente pode consultar acusações e provas, realizar julgamentos, emitir mandados, aplicar multas, condenar à prisão, definir a duração das penas e autorizar fianças ou libertações. Todas as decisões devem ficar registadas num histórico.

Quando termina o mandato, perde imediatamente os poderes judiciais. O novo Presidente recebe-os apenas depois da tomada de posse. Sem Presidente, novas decisões judiciais ficam suspensas, mas as penas existentes continuam a decorrer. Os administradores mantêm acesso de emergência.

A autorização deve ser verificada pelo servidor em cada decisão, incluindo o mandato ativo e o juramento concluído. Sem Realm Council, apenas administradores podem autorizar decisões judiciais.

## 3. Algemas e mandados

Criar algemas com modelo 3D original. Permitem imobilizar parcialmente e conduzir um jogador até à prisão **apenas com autorização válida**.

Os mandados são documentos físicos em estilo pergaminho, emitidos pelo Presidente, contendo:

- Nome RP do acusado.
- Motivo da captura.
- Data de emissão.
- Prazo de validade.
- Identificação de quem autorizou.

O servidor valida a identidade e a validade do mandado para impedir falsificações, documentos revogados e detenções sem autorização. Possuir um item com nome ou texto alterado não deve conceder poderes de detenção.

## 4. Trabalho Comunitário

Os trabalhos prisionais são opcionais: o jogador pode cumprir a pena normalmente. Um prisioneiro interage com um Quadro de Trabalhos dentro da sua prisão, aceita uma tarefa e recebe objetivos que podem reduzir a duração restante.

| Trabalho | Objetivo de exemplo | Redução de exemplo |
| --- | --- | --- |
| Mineração | Minerar 64 pedras | 3 minutos |
| Agricultura | Colher 32 trigos | 4 minutos |
| Carpintaria | Produzir 16 tábuas | 2 minutos |
| Limpeza | Limpar 10 pontos | 5 minutos |
| Ferraria | Produzir 8 barras de ferro | 5 minutos |
| Transporte | Entregar materiais entre postos | 3 minutos |

Os objetivos e valores são exemplos e devem ser configuráveis pelo administrador. Por defeito, a soma das reduções obtidas por trabalho fica limitada a **50% da pena original**. O tempo normal continua a decorrer enquanto o condenado trabalha.

## 5. Zonas de trabalho

O administrador define os limites de cada zona pela interface, selecionando dois pontos no mundo. Cada zona pertence a uma prisão específica.

| Tipo de zona | Atividades |
| --- | --- |
| **Mina** | Recolha de pedra e minérios. |
| **Horta** | Cultivo e colheita. |
| **Oficina** | Artesanato, fabricação e produção de materiais. |
| **Armazém** | Transporte e entrega de itens entre postos. |
| **Pátio** | Manutenção, limpeza e reparação. |

Uma atividade só conta se for realizada na zona correta, por um prisioneiro dessa prisão que tenha aceite a tarefa correspondente. Os jogadores constroem e organizam os espaços; o mod fornece a delimitação e a validação.

## 6. Postos de trabalho

Criar blocos 3D para organizar as atividades:

- **Quadro de Trabalhos:** mostra tarefas, objetivos e redução oferecida.
- **Caixa de Entrega:** recebe e consome com segurança os materiais necessários para concluir a tarefa.
- **Posto de Mineração:** identifica a zona de mineração.
- **Posto Agrícola:** identifica a zona de cultivo e colheita.
- **Mesa de Oficina:** permite realizar as tarefas de artesanato e produção.
- **Posto de Manutenção:** disponibiliza atividades de limpeza e reparação.

Os postos devem adaptar-se à construção dos jogadores. Não devem criar automaticamente edifícios, plantações, minas ou uma prisão inteira.

## 7. Funcionamento e apresentação das tarefas

Exemplo de fluxo:

1. Um jogador é condenado a 30 minutos de prisão e enviado para a cela.
2. Abre o Quadro de Trabalhos e aceita uma tarefa de agricultura.
3. Desloca-se à horta da prisão e recolhe os materiais pedidos na zona válida.
4. Entrega os materiais na Caixa de Entrega.
5. O servidor valida a origem, os objetivos e a entrega, consome os materiais uma única vez e atribui uma redução de 4 minutos, respeitando o limite da pena.
6. A contagem normal continua em paralelo. Quando o tempo restante chega a zero, o jogador é libertado.

O menu deve mostrar **pena original, tempo cumprido, descontos recebidos e tempo restante**, bem como os objetivos e o progresso da tarefa aceite.

## 8. Segurança dos trabalhos e dos recursos

Toda a validação acontece no servidor. Guardar o progresso após desconexão e encerramento do mundo, sem permitir repetir recompensas por voltar a entrar ou reiniciar.

É necessário:

- Impedir duplicações de materiais, entregas e reduções de pena.
- Não contar o ciclo de colocar e partir repetidamente o mesmo bloco para simular mineração.
- Não aceitar itens obtidos em criativo como produção válida da tarefa.
- Garantir que uma entrega só é contabilizada uma vez e que o consumo de materiais é seguro.
- Verificar a prisão, a zona e a tarefa de cada participante.
- Aplicar o limite total de reduções, sem permitir que uma tarefa reduza mais do que o tempo restante.
- Impedir que o Presidente altere arbitrariamente o progresso dos trabalhos.
- Libertar sempre o jogador quando cumprir a condenação.

Mineração e agricultura precisam de mecanismos de renovação controlada dos recursos. A renovação não pode transformar o trabalho numa fonte ilimitada de materiais nem criar recompensas repetíveis sem objetivos reais.

## 9. Multas e fianças

Integrar os pagamentos com a carteira digital do **Chronicles RP**. Permitir aplicar multas, consultar montantes pendentes, pagar por interfaces, definir e pagar fianças e consultar os respetivos registos de transações.

Quando **Crown Ledger** estiver instalado, enviar os pagamentos para a tesouraria do reino através da integração adequada. Não aceitar valores negativos, cobranças duplicadas ou repetição de pagamentos por reenvio de pedidos.

**A falta de dinheiro nunca deve gerar automaticamente uma nova pena de prisão.** Sem Chronicles RP, a alternativa prevista para esta fase é pagamento em esmeraldas, com os mesmos cuidados de validação e consumo. Nenhum destes pagamentos faz parte da versão 0.1.0.

## 10. Julgamentos e registo criminal

Criar uma **Mesa de Justiça** física onde o Presidente consulta processos, acusações e provas apresentadas pelos jogadores. Os julgamentos podem terminar em absolvição, multa ou prisão.

Criar um **Livro de Registos** para consultar o histórico das decisões, com nome RP, motivo, data, pena aplicada e resultado. Os dados devem permanecer disponíveis mesmo quando os jogadores envolvidos estiverem offline.

O histórico administrativo de custódia da versão 0.1.0 é a base inicial; o sistema completo de processos, provas e registo criminal será acrescentado nas fases seguintes.

## 11. Integrações opcionais

| Mod | Integração prevista |
| --- | --- |
| **Realm Council** | Identificar o Presidente com mandato ativo e juramento concluído. Sem este mod, decisões judiciais ficam reservadas aos administradores. |
| **Chronicles RP** | Apresentar nomes RP e utilizar a carteira digital. Sem este mod, usar os nomes Minecraft e, na fase financeira, esmeraldas. |
| **Realm Identity** | Consultar os documentos de identificação dos jogadores. |
| **Crown Ledger** | Receber os pagamentos de multas e fianças na tesouraria. |
| **Realm Notifications** | Avisar sobre condenações, multas, mandados, reduções de pena e libertações. |

As integrações são opcionais sempre que possível. A ausência de um mod não deve impedir as funcionalidades independentes do Laws & Justice. A versão 0.1.0 utiliza apenas os nomes RP e os avisos opcionais; não antecipa os poderes do Presidente nem a economia.

## 12. Interfaces, modelos e utilização

Manter a estética medieval dos restantes mods de `ROCKABITS/Mods`: modelos 3D próprios, ferro trabalhado, madeira, pedra, pergaminho e interfaces personalizadas coerentes.

Incluir receitas de crafting, aba criativa e traduções em português e inglês. O uso normal deve acontecer através de blocos, itens e interfaces físicas, sem depender de comandos ou de um atalho que substitua os blocos.

O resultado de cada versão deve incluir projeto compilável, código-fonte, modelos, texturas, receitas, traduções e interfaces correspondentes às funcionalidades realmente entregues.

## 13. Desenvolvimento por versões

| Versão | Âmbito |
| --- | --- |
| **0.1.0** | Prisões construídas pelos jogadores, áreas e celas registadas, administração física, controlo de penas online, persistência e libertação automática. Quatro blocos iniciais e ferramenta de seleção. |
| **0.2.0** | Presidente como juiz, validação do mandato e posse, Mesa de Justiça, algemas e mandados. Administradores mantêm acesso de emergência; não criar cargos adicionais. |
| **0.3.0** | Trabalho Comunitário, cinco tipos de zonas, postos, entregas, progresso persistente e reduções de pena limitadas. |
| **0.4.0** | Multas, fianças, registos criminais e integrações adicionais de documentos, tesouraria e notificações. |

A prioridade é um sistema medieval funcional, com compatibilidade LAN, persistência, detenções autorizadas pelo servidor e prevenção de abusos. As versões devem ser compiladas e testadas de acordo com o seu âmbito antes da publicação. Este plano não declara aprovação de testes ou publicação de qualquer versão.
