# Validação — Realm Gym 0.7.1 Fabric 1.21.1

Ambiente: Minecraft 1.21.1, Fabric Loader 0.16.14, Fabric API 0.116.18+1.21.1, Java 21 e cliente Minecraft real sob Xvfb/Mesa.

## Lógica e servidor

- **148 testes JUnit partilhados** com a edição 0.7.1: oito minijogos, cargas e desbloqueios, fadiga, limites diários, permanência e partilha dos seis atributos, rotinas, persistência, recordes, apresentação suave e contactos dos membros com as máquinas.
- **25 GameTests num servidor Fabric:** receitas e comandos, colocação das oito estruturas em todas as direções, âncoras, obstáculos, suporte, colisões, drops únicos, Criativo, layouts antigos, NBT e rejeição de dados inválidos, modificadores sem acumulação, configuração do painel, passageiros reais, desmontagem e exclusão das montagens temporárias da gravação.
- Dois cenários específicos Fabric confirmam **movimento na água aumentado** pelo atributo sincronizado de natação e **dano de queda real reduzido**. O cenário de queda desativa a proteção temporária de nascimento da fixture para poder medir o dano; aplica as funções e o hook de produção.

## Cliente real

O cenário gráfico coloca as oito máquinas, abre o perfil e o editor, grava uma rotina pelos canais C2S/S2C e executa os oito minijogos montado no equipamento, sem abrir um ecrã de configuração. Os eventos de teclado e rato entram nos métodos reais `KeyboardHandler.keyPress` e `MouseHandler.onPress`, atravessando os mixins de produção. Os resultados são calculados pelo servidor; a fixture não envia pontuações finais.

Confirma carregamento das partes móveis, animação, HUD interpolado, uma interação a agachar para mudar a carga, resultados e avanço único do plano, objetivo/bónus diário e modificadores permanentes. Inclui saída por SHIFT no leg press, destruição da polia ocupada, fecho do mundo com uma montagem ativa e reabertura real com os oito resultados preservados e sem bónus acumulados.

A base controlada é 90 XP por atributo e cada exercício tem oito repetições. Para testar a mudança de carga, o supino recebe temporariamente 100 XP, que são repostos antes das séries. Estes parâmetros aceleram a observação da evolução e não substituem a simulação dos exercícios.

São produzidas **38 capturas**, oito vídeos e uma prévia GIF a partir do cliente Minecraft. Incluem os oito modelos individuais, as estruturas no mundo, os oito minijogos, menus, português, histórico e painel de recordes. O perfil também é verificado em 320 × 240 unidades GUI.

## Distribuição

Um teste adicional inicia o **JAR remapeado de distribuição** num servidor Fabric normal, com a versão oficial do launcher e do Fabric API, num mundo isolado com socket de loopback. Aguarda o arranque completo, executa `gym reload`, grava e encerra; o hash do JAR testado é comparado com o download a publicar.

O [workflow Fabric](../.github/workflows/build-realm-gym-fabric.yml) repete as verificações antes de publicar. Preserva o JAR remapeado de produção antes de compilar fixtures e recusa metadados de testes, referências NeoForge, classes Minecraft/Fabric incorporadas ou bytecode diferente de Java 21. Verifica os mixins/refmap, modelos, receitas, colisões e traduções. O relatório `mod-pronto/realm-gym-0.7.1-fabric-1.21.1.build.json` liga o download ao commit e à execução CI, com os hashes do JAR, ZIP e capturas.

O âmbito cobre a edição independente, servidor nativo e servidor integrado. Uma sessão remota com dois clientes autenticados e combinações com outros mods de atributos, renderização ou movimento exigem validação própria. O balanceamento continua sujeito a afinação com jogadores.

## Condição física na 0.7.1

A condição é opcional e desativada por defeito. A suite de domínio inclui a tolerância de sete dias, perda de cinco pontos por dia, mínimo de 60%, recuperação de 20 pontos por duas séries válidas, limite diário, contagem por atributo, treino com limite de XP atingido, relógio a recuar, pausa offline, reativação e migração de perfis antigos.

Dois novos GameTests verificam modificadores reais de dano, movimento e proteção contra quedas, preservação de níveis/cargas, ausência de acumulação, SavedData nativo e desativação com reposição dos bónus completos. O cliente gráfico ativa a condição no mundo de teste, começa com força/pernas a 80% e confirma o valor no perfil, a manutenção pelos inputs reais, o bónus aplicado e a persistência ao reabrir.

O horário de renovação do cenário é colocado a doze horas do arranque. Esta configuração evita que a execução gráfica atravesse o seu próprio reinício diário; os testes de domínio continuam a verificar as mudanças de dia. A configuração e o estado de condição são preservados na reabertura do mundo de teste.

## Comandos de condição na 0.7.1

Os GameTests executam os comandos e o alias `/fitness` no dispatcher real, confirmam permissões de operador, consulta sem permissões elevadas, gravação e recarregamento do estado, preservação de outras opções, repetição idempotente de `on` e recusa de configurações inválidas sem alterar as regras ativas. O cenário de cliente verifica a alteração imediata dos atributos do jogador ligado e a conservação da condição ao alternar o modo.

Os testes de ficheiros cobrem criação, configuração antiga, campos desconhecidos, tamanho, JSON inválido, falhas de escrita e substituição atómica. Nos testes de distribuição Forge/Fabric 1.21.1, a consola ativa o modo, o servidor encerra e reinicia; a consulta confirma que a opção foi mantida, antes de voltar a desativá-la.

## Real-time and Minecraft-day clocks

The clock tests cover exact 24,000-tick boundaries, real-time independence, offline settings, paused worlds, rollback, grace-period changes, selected-day recovery and legacy profiles. Native tests run real operator commands, check saved mode/day values, reject visitors and invalid counts, apply Minecraft-day condition to actual damage, and confirm daylight changes do not charge inactivity. The real client runs in Minecraft-day mode, verifies the profile payload and reloads the world with its selected period intact. The production-server smoke scenarios boot three times to verify both Minecraft and real-time settings after restarting.
