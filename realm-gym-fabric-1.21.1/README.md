# Realm Gym 0.6.0 Beta — Fabric 1.21.1

Edição Fabric das oito máquinas, minijogos, animações articuladas, atributos permanentes, planos de treino e recordes do Realm Gym 0.6.0. Inclui **leg press e tríceps com corda**.

**Requisitos:** Minecraft **1.21.1**, **Java 21**, **Fabric Loader 0.16.14+** e **Fabric API 0.116.18+1.21.1**. Instala o mesmo Realm Gym e o Fabric API no servidor e nos clientes. Cada loader tem o seu ficheiro de instalação.

[Descarregar JAR](../mod-pronto/realm-gym-0.6.0-fabric-1.21.1.jar) · [ZIP](../mod-pronto/realm-gym-0.6.0-fabric-1.21.1.zip) · [Galeria e animações](../media/realm-gym/0.6.0-fabric-1.21.1/README.md) · [Validação](VALIDACAO.md) · [Publicar no CurseForge](CURSEFORGE.md)

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

A edição Fabric partilha as regras, modelos, pontos de contacto e ecrãs com a edição NeoForge. O [guia das mecânicas](../realm-gym/README.md#exercícios-e-progressão) descreve os níveis, cargas, fadiga, objetivo diário e limites. O leg press partilha pernas/fadiga com agachamentos; tríceps partilha força/fadiga com supino. Os recordes e as cargas preferidas são separados por máquina.

Os seis atributos continuam a dar dano corpo a corpo, velocidade de movimento, velocidade de ataque, proteção contra quedas, velocidade de natação e altura de salto. A natação usa o atributo próprio sincronizado `realmfitness:swim_speed`; a queda usa a redução calculada no servidor. Não requer Realm Origins nem um mod de stats.

A configuração mantém-se em `config/realm-fitness.json` e a progressão em `data/realm_fitness.dat`, com o mesmo formato da 0.6.0. Os treinos e recordes pertencem ao mundo. As montagens temporárias não são gravadas e uma série interrompida não atribui XP.

## Desenvolvimento

```sh
./gradlew build
mkdir -p qa-results build/gametest
echo 'eula=true' > build/gametest/eula.txt
./gradlew -PfitnessGameTests runGametest
./gradlew -PfitnessVisualTests runClient
```

O JAR para distribuir é o resultado **remapeado** de `build/libs/realm-gym-0.6.0-fabric-1.21.1.jar`. O build usa Loom 1.7.4 e mappings oficiais Mojang; os nomes de Minecraft são remapeados para intermediary na distribuição. Os testes são excluídos do JAR.

O núcleo de domínio, geometrias, recursos, poses, apresentação e vários ecrãs são compilados diretamente de `../realm-gym`. Os adaptadores Fabric ficam neste projeto: registos, ciclo de vida, rede, montagem, efeitos físicos, renderização e controlos. A edição Forge 1.20.1 mantém a base independente 0.5.1.
