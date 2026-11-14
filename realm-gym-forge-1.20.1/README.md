# Realm Gym — Forge 1.20.1

Port independente do **Realm Gym 0.5.1 Beta** para **Minecraft 1.20.1, Forge 47.4.0+ e Java 17**. Inclui as seis máquinas, montagem física, animações, minijogos com HUD discreto, progressão permanente, perfil RPG, planos de treino, histórico e painéis de recordes.

[Descarregar JAR](../mod-pronto/realm-gym-0.5.1-forge-1.20.1.jar) · [ZIP](../mod-pronto/realm-gym-0.5.1-forge-1.20.1.zip) · [Validação](VALIDACAO.md) · [Galeria](../media/realm-gym/0.5.1-forge-1.20.1/README.md) · [CurseForge](CURSEFORGE.md)

## Instalação

Instala `realm-gym-0.5.1-forge-1.20.1.jar` na pasta `mods` do servidor e de todos os clientes, com **Forge 47.4.0 ou superior da série 47**. Não exige outros mods Realm nem bibliotecas de animação. Usa apenas um JAR do Realm Gym/Realm Fitness por instalação.

A edição [1.21.1/NeoForge](../realm-gym/README.md) continua disponível separadamente. Este port não converte mundos de Minecraft 1.21.1 para 1.20.1.

## Jogar

1. Fabrica as máquinas, o painel de recordes e o cartão de atleta. `/gym equipment` entrega o equipamento a operadores.
2. Usa **K**, o cartão ou `/gym` para abrir o perfil, consultar atributos e recordes ou criar um plano de treino.
3. **Botão direito na máquina:** monta e inicia o minijogo, sem menu intermédio. Usa a próxima série do plano ativo ou uma série livre de oito repetições. Requer Sobrevivência e uma máquina intacta próxima.
4. **Agachar + botão direito antes de entrar:** aumenta a carga dentro do limite desbloqueado. No supino/agachamento aumenta 5 kg; nas outras máquinas aumenta um nível de resistência. A carga volta ao mínimo ao chegar ao limite.
5. **SHIFT durante o treino:** sai e cancela a série incompleta. **F5** muda a perspetiva.

| Máquina | Minijogo | Bónus máximo por defeito |
| --- | --- | --- |
| Supino | Tocar repetidamente em Espaço para vencer a pressão. Segurar não levanta. | +10% dano corpo a corpo |
| Passadeira | Alternar A/D para manter ritmo e fôlego. | +8% velocidade de movimento |
| Saco de boxe | M1/M2 no centro do marcador móvel; acertos perfeitos prolongam a sequência. | +8% velocidade de ataque |
| Elevações | Espaço para subir, largar para descer; A/D para equilibrar a pega. | 20% menos dano de queda |
| Remo | W para carregar, largar na zona verde e S para recuperar. | +15% velocidade de natação |
| Agachamentos | W para descer, largar para pausar e S para subir. | Até +15% altura de salto |

Cada atributo tem 20 níveis permanentes. Cargas baixas tornam-se mais fáceis com a evolução; cargas elevadas e fadiga continuam a exigir mais. Supino/agachamentos vão de 20 a 120 kg, em passos de 5 kg. As restantes máquinas usam resistência de 1 a 6.

Por defeito, o objetivo diário pede seis séries válidas de pelo menos oito repetições previstas e três exercícios diferentes. O limite é 240 XP/dia, incluindo até 30 XP de bónus diário. A renovação é à meia-noite UTC e os níveis não expiram por faltar. Séries incompletas não dão prémios. O servidor calcula os resultados a partir dos controlos, sem aceitar pontuações enviadas pelo cliente.

As máquinas reservam três blocos de altura e uma base de 2 × 3 blocos (supino, passadeira e remo) ou 2 × 2 (boxe, elevações e agachamentos). Quebrar qualquer peça desmonta a máquina com um único drop em Sobrevivência; em Criativo não deixa itens.

## Dados e configuração

Mantêm-se o identificador `realmfitness`, `data/realm_fitness.dat`, `config/realm-fitness.json` e o alias `/fitness`. O plano ativo, cargas escolhidas, níveis e recordes são guardados no mundo. O painel permite comparar recordes do servidor ou da zona de ginásio. `/gym reload` recarrega a configuração para operadores.

As regras completas de planos, fadiga, limites e classificações são partilhadas com a edição NeoForge; consulta o [guia de jogo](../realm-gym/README.md). Nesta edição, o salto usa um atributo próprio sincronizado e o evento de salto do Forge, preservando a progressão sem acumular modificadores. Compatibilidade com mods externos de stats/movimento depende dos modificadores desses mods.

## Desenvolvimento

A pasta `shared` conserva a base verificada da 0.5.1: regras de treino, matemática das animações, contactos/colisões, testes JUnit, modelos, texturas e traduções. O Gradle adapta as conveniências Java 21 e os formatos de recursos. Esta base permite desenvolver máquinas novas na edição 1.21.1 sem alterar o conteúdo publicado para 1.20.1. O JAR final é independente da pasta de fontes e não inclui Minecraft, Forge nem classes de teste.

```sh
cd realm-gym-forge-1.20.1
./gradlew --no-daemon build
./gradlew --no-daemon runGameTestServer
./gradlew --no-daemon -PfitnessVisualTests runClient
```

O último comando exige um ambiente gráfico e executa uma sessão de QA controlada, incluindo seis séries reais e reabertura do mundo. O [workflow](../.github/workflows/build-realm-gym-forge.yml) repete a validação com Java 17 e publica o JAR reobfuscado em `mod-pronto` apenas quando todas as verificações passam.
