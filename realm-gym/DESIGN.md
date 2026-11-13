# Realm Gym — resumo do conceito e plano de produto

Atualizado em **13 de novembro de 2026**. Nome de trabalho: **Realm Gym**.

**Documento de conceito original.** A implementação atual e o âmbito da primeira beta encontram-se no [guia](README.md). As decisões e propostas abaixo conservam o histórico de planeamento. Este documento conserva o contexto da conversa para retomar o desenvolvimento. As decisões do utilizador e os exemplos propostos estão identificados separadamente.

## 1. Objetivo e decisões principais

Criar um mod de ginásio para o servidor de RP, que motive os jogadores a fazer **um treino por dia**, com sessões divertidas e evolução da personagem ao longo das semanas.

Requisitos pedidos ou orientados pelo utilizador:

- Exercícios em máquinas, com **minijogos próprios**, alguma dificuldade e participação ativa.
- **Bónus permanentes** obtidos através do treino.
- Um ou mais **blocos físicos de leaderboard**, visíveis no ginásio.
- **Menu RPG com a personagem e os atributos ao lado**, incluindo bónus atuais e recordes de peso, seguindo o estilo do Realm Origins.
- **Catálogo de máquinas e criação de planos de treino**, inspirados no funcionamento da aplicação Hevy.
- Experiência organizada para incentivar a regularidade diária.

O utilizador inicialmente referiu um mod de stats semelhante a Elden Ring, mas retirou a necessidade de adaptação específica. A base planeada funciona de forma independente, com efeitos configuráveis; uma integração específica pode ser considerada mais tarde. O mod de stats não foi identificado e não deve ser tratado como dependência confirmada.

**Versão Minecraft e loader do Realm Gym: ainda por definir.** O repositório tem principalmente projetos 1.21.1/NeoForge/Java 21 e um port de Contracts & Bounties para 1.20.1/Forge/Java 17. A existência desse port não estabelece automaticamente a plataforma do novo mod.

## 2. Máquinas e minijogos

Foi proposta a seguinte coleção inicial. A existência dos minijogos faz parte do pedido; os controlos exatos, as máquinas da primeira entrega e os valores de dificuldade ainda precisam de afinação.

| Máquina/exercício | Minijogo proposto | Atributo desenvolvido |
| --- | --- | --- |
| Supino | Controlar a descida da barra e acertar o momento de subir. Pesos maiores estreitam a zona ideal. | Força |
| Passadeira | Acompanhar um ritmo dentro de uma zona variável, mantendo o controlo quando muda a velocidade. | Condição física |
| Saco de boxe | Responder a sequências de golpes e esquivas; combos corretos melhoram o resultado. | Coordenação |
| Barra de elevações | Coordenar subida, estabilização e descida; a fadiga aumenta a exigência das repetições. | Controlo corporal |
| Remo | Sincronizar puxada e recuperação para percorrer mais distância. | Resistência aquática |
| Agachamentos | Acompanhar uma marca para controlar profundidade, equilíbrio e velocidade. | Potência das pernas |

Apresentação proposta: equipamento 3D funcional e animações da personagem visíveis aos outros jogadores. A qualidade da execução deve contar para a experiência e para os resultados.

Séries de **20–40 segundos** foram sugeridas como ponto de partida, com feedback de técnica, ritmo e precisão. No fim, mostrar repetições válidas, carga, precisão e recordes alcançados. Estes tempos são exemplos para testar, não números finais.

## 3. Progressão e bónus permanentes

A opção mais recente é progressão permanente: ganhar experiência no atributo associado ao exercício e subir de nível. Foi sugerido aumentar gradualmente a experiência necessária e permitir configurar limites e intensidade dos efeitos.

**Os valores abaixo são uma proposta inicial de equilíbrio**, ainda não confirmada como configuração final:

| Exercício | Bónus permanente máximo proposto |
| --- | --- |
| Supino | +10% de dano corpo a corpo |
| Passadeira | +8% de velocidade de movimento |
| Saco de boxe | +8% de velocidade de ataque |
| Elevações | 20% menos dano de queda |
| Remo | +15% de velocidade a nadar |
| Agachamentos | +15% de altura de salto |

Foram sugeridos **20 níveis por exercício**. Os níveis permitiriam cargas, dificuldades e desafios mais avançados. A aplicação concreta dos atributos e a interação dos modificadores com o modpack precisam de validação durante a implementação.

A proposta permite desenvolver todos os atributos. **A ideia anterior de boosts de 15–30 minutos e de apenas dois boosts ativos foi substituída pela preferência por ganhos permanentes.**

Fadiga e recuperação regulam o treino e a eficiência dos ganhos. Foi proposto preservar os níveis conquistados quando o jogador falta a treinos; o descanso recupera a eficiência. Não confundir fadiga temporária com perda dos atributos permanentes.

## 4. Um treino por dia

A regularidade diária é o objetivo central. O treino diário pode ser uma **rotina criada pelo próprio jogador**, com planos sugeridos para quem está a começar.

Propostas discutidas para o funcionamento:

- Sessão de aproximadamente **5–10 minutos**, usando como exemplo três exercícios e duas séries por exercício.
- Experiência por cada série válida, melhorada pela qualidade da execução.
- Experiência adicional por concluir o plano diário.
- Limite diário de experiência configurável. Treino extra continua útil para praticar, competir e bater recordes.
- Fadiga reduz os ganhos de séries repetidas em excesso.
- Renovação diária à mesma hora para o servidor, configurável.
- Calendário e metas semanais, por exemplo três ou cinco treinos, para dar margem a quem falta um dia.

Os tempos, a quantidade de séries, os limites e as metas semanais são propostas. **Ainda falta definir como um plano personalizado cumpre os requisitos do treino diário e recebe o bónus de conclusão.** O sistema não deve obrigar todos a seguir o mesmo plano sugerido.

## 5. Perfil de Atleta — menu RPG

Referência visual pedida: **Realm Origins**, mantendo coerência de paleta, molduras, tipografia e botões.

Consultar o [projeto Realm Origins](../realm-origins/README.md) e a [galeria existente](../media/realm-origins/README.md) antes de desenhar a interface. O Origins tem criação de personagem e livro de perfil com apresentações diferentes; a referência exata deve ser resolvida no desenho do menu.

Disposição discutida:

- **Personagem à esquerda:** modelo 3D com a skin, rotação com o rato e identificação do atleta.
- **Atributos à direita:** nível, barra de experiência e bónus permanente efetivamente conquistado em cada atributo.
- **Estado físico:** fadiga, recuperação e eficiência prevista para o próximo treino.
- **Recordes:** carga máxima, repetições válidas, precisão, pontuação e data, conforme o exercício.
- **Treino de hoje:** séries concluídas, exercícios previstos e acesso ao calendário.

Exemplo de apresentação, sem constituir um valor obrigatório:

> Força — nível 12/20 · +6% de dano corpo a corpo  
> Experiência: 340/600  
> Recorde de supino: 85 kg × 8 repetições  
> Melhor técnica: 96%

Tecla configurável e cartão de atleta foram sugeridos como formas de acesso. O menu deve identificar os ganhos concedidos pelo ginásio; consultar todos os atributos de outro mod não faz parte de uma integração já acordada.

## 6. Área Treinos — inspiração Hevy

O utilizador quer conseguir conhecer as máquinas e construir o seu próprio plano.

### Catálogo e rotinas

- Catálogo de máquinas com imagem ou modelo 3D, explicação do exercício, atributo desenvolvido e indicação de disponibilidade naquele ginásio.
- Criar, nomear, editar e guardar rotinas.
- Escolher e ordenar exercícios.
- Definir séries, repetições, carga e descanso quando aplicável.
- Associar rotinas a dias da semana.
- Oferecer planos sugeridos para iniciantes.

Exemplo:

> Treino A — Força  
> Supino · 2 × 8 · 40 kg  
> Elevações · 2 × 6  
> Agachamentos · 2 × 10 · 30 kg  
> Iniciar treino

As unidades e os objetivos devem fazer sentido para cada máquina: peso não é uma medida obrigatória para todos os exercícios.

### Sessão, histórico e evolução

- Iniciar uma rotina e acompanhar o exercício e a série atuais.
- Reconhecer na máquina o exercício previsto para a sessão.
- Ao terminar o minijogo, registar automaticamente repetições válidas, carga e precisão, marcar a série e iniciar o descanso.
- Consultar sessões anteriores e preencher os valores da última sessão para facilitar uma pequena melhoria no treino seguinte.
- Mostrar evolução de atributos, frequência de treino e recordes através de gráficos simples.

A inspiração no Hevy é funcional. A apresentação deve manter a identidade visual pretendida para o mod e a utilização dentro do Minecraft.

## 7. Painel físico de leaderboard

Pedido do utilizador: um bloco que mostre a classificação no mundo.

Detalhes propostos:

- Painel 3D de parede ou de chão; possível variante maior com vários blocos.
- Cabeça do jogador, nome, posição e resultado visíveis.
- Atualização automática quando termina uma série com um resultado relevante.
- Ao interagir, escolher exercício, período e âmbito.
- Períodos sugeridos: semanal, mensal e recordes de sempre.
- Âmbito sugerido: aquele ginásio ou o servidor inteiro.
- Comparar resultados em condições equivalentes de exercício, carga e dificuldade.

O menu pessoal e os painéis devem consultar os mesmos registos. A fórmula de classificação e a definição de um ginásio/local ainda estão por decidir.

## 8. Ideias secundárias mencionadas

Ainda sem confirmação de âmbito: ginásios geridos por jogadores, mensalidades e passes, personal trainers, desafios entre jogadores, títulos de atleta e evolução visual opcional do corpo preservando a skin.

Estas ideias não devem atrasar a base de treino diário, minijogos, progressão permanente, planos e recordes.

## 9. Ao retomar o desenvolvimento

1. Ler este documento e consultar o estado atual do repositório.
2. Definir a plataforma inicial, o conjunto da primeira entrega e o desenho visual concreto.
3. Afinar a duração dos treinos, a dificuldade, a curva de experiência, os limites diários e os bónus. Os números deste resumo são exemplos ajustáveis.
4. Definir a relação entre rotinas personalizadas, treino diário e metas semanais.
5. Guardar progressão, planos, sessões e recordes por jogador, com persistência após saídas e reinícios. O servidor valida resultados e aplica os ganhos.
6. Validar as máquinas, os minijogos, os menus, os painéis e a persistência dentro do Minecraft antes de publicar um JAR.

Prioridade de produto: **voltar ao ginásio, escolher o plano, jogar os exercícios e ver a evolução permanente da personagem**.

[Voltar ao plano dos mods](../PLANO-DOS-MODS.md) · [Repositório](../README.md)
