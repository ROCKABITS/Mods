# Realm Fitness 0.2.0 Beta

Ginásio físico para **Minecraft 1.21.1, NeoForge 21.1.256+ e Java 21**. Máquinas 3D, seis minijogos, atributos permanentes, rotinas personalizadas, calendário e painéis de recordes. Funciona de forma independente.

[Descarregar JAR](../mod-pronto/realm-fitness-0.2.0.jar) · [ZIP](../mod-pronto/realm-fitness-0.2.0.zip) · [Galeria](../media/realm-fitness/0.2.0/README.md) · [Validação](VALIDACAO.md) · [Conceito original](DESIGN.md)

Instala o mesmo JAR no servidor e em todos os clientes. Esta edição é para 1.21.1/NeoForge. Identificador: `realmfitness`.

## Começar

1. Fabrica as máquinas, o painel e o cartão de atleta. Um operador pode obter o conjunto com `/fitness equipment`.
2. Abre o perfil com **K**, com o cartão ou com `/fitness`. A tecla do perfil pode ser alterada nos controlos do Minecraft.
3. Em **Treinos**, escolhe uma das rotinas iniciais ou cria a tua. O editor permite escolher exercícios, ordem, séries, repetições, carga, descanso e dia da semana.
4. Inicia a rotina e interage com a máquina do próximo exercício. O treino físico exige modo Sobrevivência e uma máquina intacta próxima.
5. Segue o minijogo. A série começa após três segundos; a câmara passa temporariamente para terceira pessoa e F5 permite alternar a perspetiva. Ao sair, recupera a perspetiva anterior.

O plano guarda o progresso entre sessões e reinícios. Pode ser terminado no menu para começar outro. Editar ou apagar uma rotina guardada não altera a cópia de um treino já iniciado. Os dias associados às rotinas são uma indicação de calendário; o jogador escolhe qual iniciar.

## Equipamento redesenhado na 0.2.0

As seis máquinas usam modelos e texturas originais: aço escuro, estofos verdes com costuras, detalhes em cobre, discos recortados, apoios em borracha e comandos próprios. Os bancos, a passadeira e o remo têm **2 × 3 blocos** de base; boxe, elevações e agachamentos têm **2 × 2 blocos**. Todas reservam **três blocos de altura**. Coloca-as numa superfície sólida, com toda a área livre; a orientação depende da direção do jogador. As colisões acompanham a estrutura e deixam aberturas nos espaços vazios.

Quebrar uma peça desmonta a máquina e devolve um único item em Sobrevivência. Em Criativo não deixa itens. As máquinas colocadas na 0.1.0 continuam com a base antiga e um modelo compacto; recolhe-as e volta a colocá-las para usar o novo tamanho. A progressão, rotinas e recordes são preservados.

## Exercícios e progressão

| Máquina | Controlos | Atributo | Bónus máximo por defeito |
| --- | --- | --- | --- |
| Supino | Segurar/largar Espaço para acompanhar a zona verde. | Força | +10% dano corpo a corpo |
| Passadeira | Alternar A/D no ritmo dos indicadores. | Condição física | +8% velocidade de movimento |
| Saco de boxe | Cliques esquerdo/direito para socos; A/D para esquivas. | Coordenação | +8% velocidade de ataque |
| Elevações | Espaço para subir/largar para descer e recuperar a pega; A/D para equilíbrio. | Controlo corporal | 20% menos dano de queda |
| Remo | W para puxar e S para recuperar, sincronizados com os indicadores. | Resistência aquática | +15% velocidade de natação |
| Agachamentos | W para descer e S para subir, acompanhando a profundidade indicada. | Potência das pernas | Até +15% altura de salto |

Cada atributo tem **20 níveis permanentes**. Supino e agachamentos começam nos 20 kg, desbloqueando mais 5 kg por nível, até 120 kg. As restantes máquinas usam resistência de 1 a 6, desbloqueada a cada quatro níveis. Carga e fadiga tornam o controlo mais exigente.

Uma série tem 4–20 tentativas, com 2,4 segundos por repetição, além da contagem inicial. Para ganhar XP e avançar o plano exige pelo menos 60% de repetições válidas e 50% de precisão. Resultados inferiores ficam no histórico como prática. O servidor calcula a técnica a partir dos inputs e do seu próprio relógio; o cliente não envia uma pontuação final.

Os níveis não expiram por faltar ao ginásio e persistem após morte, saída e reinício. Os bónus usam modificadores próprios, reaplicados sem acumulação a cada sincronização. A altura de salto usa uma correção da velocidade de impulso; outros modificadores e a física podem afetar o resultado final.

## Treino diário

Por defeito:

- Objetivo: **seis séries válidas de pelo menos oito repetições previstas**, com pelo menos **três exercícios diferentes**.
- Bónus diário: até **30 XP adicionais**, distribuídos pelos atributos treinados.
- Limite: **240 XP por jogador e por dia**, incluindo o bónus. Treino adicional permite praticar e bater recordes.
- Fadiga: cada série válida acrescenta 12 pontos percentuais nesse exercício. Uma escala completa de fadiga recupera em 30 minutos reais, incluindo tempo offline; uma série isolada recupera mais depressa.
- Renovação: meia-noite UTC, configurável.
- Descanso livre: 15 segundos entre séries válidas. Num plano, aplica-se o descanso escolhido para a série concluída, entre 5 e 180 segundos.

Qualidade, repetições válidas e fadiga determinam o XP. O calendário destaca os dias com objetivo cumprido. O histórico apresenta carga, repetições, precisão, XP e data; a página de evolução mostra 28 dias e os níveis atuais. O perfil e o editor seguem a paleta escura/lilás do criador de personagem do Realm Origins, com recursos locais e sem dependência desse mod.

## Recordes no mundo

O painel mostra os cinco primeiros da categoria, com nome, pontuação e cabeça do jogador. O menu consulta até dez posições e permite escolher exercício, carga/resistência, repetições, período e âmbito.

- **Período:** sempre, semana atual ou mês atual. A semana começa à segunda-feira.
- **Âmbito:** servidor ou zona fixa de **32 × 32 blocos**, determinada pelas coordenadas do painel. Nesta beta, a zona é a definição prática de ginásio.
- Comparam-se resultados com o mesmo exercício, carga e repetições. Pontuação: repetições válidas × precisão. Cada jogador ocupa uma posição por categoria.
- Todos podem consultar filtros pessoais. Um **operador** pode usar **Aplicar ao painel** para alterar a categoria pública.
- Os resultados atualizam automaticamente enquanto o painel está carregado.

Os resultados detalhados conservam-se durante 70 dias, sujeitos aos limites de armazenamento; os melhores globais e por zona guardam-se separadamente para a classificação de sempre. As cabeças usam a skin disponível no cliente para jogadores online e a skin padrão por UUID quando essa informação não está disponível.

## Configuração e âmbito

O servidor gera `config/realm-fitness.json`. Permite alterar hora de renovação, XP diário, requisitos do objetivo, bónus diário, fadiga/recuperação e percentagens máximas. Usa `/fitness reload` como operador para validar e aplicar alterações. Percentagem 0 desativa o respetivo efeito, preservando a progressão.

Limites: 12 rotinas por jogador, até oito exercícios e 24 séries por rotina, 4.096 resultados recentes e 4.096 melhores categorias por atleta, 10.000 atletas e 100.000 resultados recentes no mundo. Ao atingir um limite, o servidor recusa novas operações em vez de eliminar progresso silenciosamente.

O estado pertence ao mundo, em `data/realm_fitness.dat`, com gravação atómica. Dados inválidos não são substituídos automaticamente por atletas vazios. Mover-se durante a série, morrer, mudar de dimensão, sair ou interromper cancela a execução incompleta.

Os minijogos usam Espaço, WASD e os botões do rato nesta beta. As animações são poses procedurais da skin e camadas exteriores; durante a renderização especial do exercício, equipamento e armadura não são desenhados. Continuam equipados e com os seus efeitos.

Não há dependência de um mod de stats. Combinações específicas com outros sistemas de atributos ou renderização precisam de validação no respetivo modpack. Mensalidades, personal trainers, alterações musculares da skin, gestão de ginásios e metas semanais com prémios continuam nas ideias do conceito original.

## Desenvolvimento

```sh
./gradlew build
./gradlew -PfitnessGameTests runGameTestServer
./gradlew -PfitnessVisualTests runClient
```

O teste de cliente exige um ambiente gráfico. O [workflow](../.github/workflows/build-realm-fitness.yml) executa build, testes de lógica, testes nativos e cliente real com Xvfb; publica apenas depois das verificações. Código e estruturas de teste são excluídos do JAR.

`tools/generate_resources.py` regenera geometrias, materiais pixel e traduções. Os recursos gerados já acompanham o projeto: o build normal não precisa de Python/Pillow.
