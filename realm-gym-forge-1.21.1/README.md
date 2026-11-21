# Realm Gym 0.7.2 Beta — Forge 1.21.1

Edição Forge das oito máquinas, minijogos, animações articuladas, atributos permanentes, planos de treino e recordes do Realm Gym 0.7.2. Inclui **leg press e tríceps com corda**.

**Requisitos:** Minecraft **1.21.1**, **Java 21**, **Forge 52.1.20+**. Instala o mesmo Realm Gym no servidor e nos clientes. Cada loader tem o seu ficheiro de instalação.

[Descarregar JAR](../mod-pronto/realm-gym-0.7.2-forge-1.21.1.jar) · [ZIP](../mod-pronto/realm-gym-0.7.2-forge-1.21.1.zip) · [Galeria e animações](../media/realm-gym/0.7.2-forge-1.21.1/README.md) · [Validação](VALIDACAO.md) · [Publicar no CurseForge](CURSEFORGE.md)

**Supino a dois.** Clica com o botão direito num banco ocupado para ser spotter. O atleta pede ajuda com **W** quando vê o aviso, e o spotter segura a barra com **Espaço**. **Shift** sai do posto. [Changelog 0.7.2](../realm-gym/CHANGELOG.md#072--bench-press-spotter).

**New in 0.7.1:** choose real-time or Minecraft-day physical condition with `/gym conditioning days real 7` or `/gym conditioning days minecraft 7`. Commands save and apply immediately. [Clock configuration guide](../realm-gym/CONDITIONING.md).

## Jogar

- **Botão direito:** montar a máquina e começar uma série com HUD discreto.
- **Agachar + botão direito antes de montar:** aumentar a carga dentro do limite desbloqueado.
- **SHIFT:** sair e cancelar a série incompleta. **F5:** mudar a perspetiva.
- **K**, cartão de atleta ou **`/gym`**: abrir o perfil, catálogo, planos, histórico e recordes.
- **`/gym equipment`**: obter todas as máquinas e acessórios como operador. **`/gym reload`**: aplicar a configuração. **`/fitness`** mantém-se como alias.

| Equipamento | Controlos |
| --- | --- |
| Supino | Tocar repetidamente em Espaço para vencer a pressão. |
| Passadeira | Alternar A/D e manter o ritmo na faixa verde. |
| Saco de boxe | Clicar M1/M2 quando o marcador passa no centro; acertos perfeitos prolongam a sequência. |
| Elevações | Segurar Espaço para subir, largar para descer; A/D corrige o equilíbrio. |
| Remo | W carrega a remada; largar no verde e usar S para recuperar. |
| Agachamentos | W desce, largar faz a pausa, S sobe. |
| Leg press | A/D controlam a pressão de cada perna; equilibrar no verde e largar ambas para regressar. |
| Tríceps na polia | Segurar Espaço para puxar, clicar no verde, manter a contração curta e largar para regressar. |

A edição Forge partilha as regras, modelos, pontos de contacto e ecrãs com a edição NeoForge. O [guia das mecânicas](../realm-gym/README.md#exercícios-e-progressão) descreve os níveis, cargas, fadiga, objetivo diário e limites. O leg press partilha pernas/fadiga com agachamentos; tríceps partilha força/fadiga com supino. Os recordes e as cargas preferidas são separados por máquina.

Os seis atributos continuam a dar dano corpo a corpo, velocidade de movimento, velocidade de ataque, proteção contra quedas, velocidade de natação e altura de salto. A natação usa o atributo `forge:swim_speed`; a queda usa a redução calculada no servidor. Não requer Realm Origins nem um mod de stats.

A configuração mantém-se em `config/realm-fitness.json` e a progressão em `data/realm_fitness.dat`, com o mesmo formato da 0.7.1. Os treinos e recordes pertencem ao mundo. As montagens temporárias não são gravadas e uma série interrompida não atribui XP.

## Desenvolvimento

```sh
./gradlew build
mkdir -p qa-results run
echo 'eula=true' > run/eula.txt
./gradlew -PfitnessGameTests runGameTestServer
./gradlew -PfitnessVisualTests runClient
```

O JAR para distribuir é `build/libs/realm-gym-0.7.2-forge-1.21.1.jar`. Usa ForgeGradle 7.0.3, Gradle 9.3.1 e os mappings oficiais Mojang, também usados pelo runtime Forge 52 atual. O JAR final é testado numa instalação oficial de servidor, além dos testes de desenvolvimento. As fixtures são excluídas da distribuição.

Para desenvolver, instala JDKs **8, 21 e 25**, detetáveis pelo Gradle ou pelas variáveis `JAVA_HOME_8_X64`, `JAVA_HOME_21_X64` e `JAVA_HOME_25_X64`. O Mavenizer e o launcher usam ferramentas separadas; a compilação e o jogo usam Java 21. **Os jogadores só precisam de Java 21.**

O núcleo de domínio, geometrias, recursos, poses, apresentação e vários ecrãs são compilados diretamente de `../realm-gym`. Os adaptadores Forge ficam neste projeto: registos, ciclo de vida, rede, montagem, efeitos físicos, renderização e controlos. A edição Forge 1.20.1 conserva as seis máquinas e recebe a mesma condição física na 0.7.1.
