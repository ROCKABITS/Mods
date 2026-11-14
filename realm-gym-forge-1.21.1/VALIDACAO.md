# Validação — Realm Gym 0.6.0 Forge 1.21.1

Ambiente: Minecraft 1.21.1, Forge 52.1.20, Java 21 e cliente Minecraft real sob Xvfb/Mesa.

## Lógica e servidor

- **91 testes JUnit partilhados** com a edição 0.6.0: oito minijogos, cargas e desbloqueios, fadiga, limites diários, permanência e partilha dos seis atributos, rotinas, persistência, recordes, apresentação suave e contactos dos membros com as máquinas.
- **22 GameTests num servidor Forge:** receitas e comandos, colocação das oito estruturas em todas as direções, âncoras, obstáculos, suporte, colisões, drops únicos, Criativo, layouts antigos, NBT e rejeição de dados inválidos, modificadores sem acumulação, configuração do painel, passageiros reais, desmontagem e exclusão das montagens temporárias da gravação.
- Dois cenários específicos Forge confirmam **movimento na água aumentado** pelo atributo sincronizado de natação e **dano de queda real reduzido**. O cenário de queda desativa a proteção temporária de nascimento da fixture para poder medir o dano; aplica as funções e o evento de produção. Um terceiro cenário específico rejeita jogadores sintéticos que não estejam registados no servidor, impedindo treinos com recompensas sem uma ligação real.

## Cliente real

O cenário gráfico coloca as oito máquinas, abre o perfil e o editor, grava uma rotina pelos canais C2S/S2C e executa os oito minijogos montado no equipamento, sem abrir um ecrã de configuração. Os eventos de teclado e rato entram nos métodos reais `KeyboardHandler.keyPress` e `MouseHandler.onPress`, atravessando os eventos Forge de produção. Os resultados são calculados pelo servidor; a fixture não envia pontuações finais.

Confirma carregamento das partes móveis, animação, HUD interpolado, uma interação a agachar para mudar a carga, resultados e avanço único do plano, objetivo/bónus diário e modificadores permanentes. Inclui saída por SHIFT no leg press, destruição da polia ocupada, fecho do mundo com uma montagem ativa e reabertura real com os oito resultados preservados e sem bónus acumulados.

A base controlada é 90 XP por atributo e cada exercício tem oito repetições. Para testar a mudança de carga, o supino recebe temporariamente 100 XP, que são repostos antes das séries. Estes parâmetros aceleram a observação da evolução e não substituem a simulação dos exercícios.

São produzidas **38 capturas**, oito vídeos e uma prévia GIF a partir do cliente Minecraft. Incluem os oito modelos individuais, as estruturas no mundo, os oito minijogos, menus, português, histórico e painel de recordes. O perfil também é verificado em 320 × 240 unidades GUI.

## Distribuição

Um teste adicional inicia o **JAR de distribuição com nomes oficiais Mojang** num servidor Forge normal, com o instalador oficial Forge 52.1.20, num mundo isolado com socket de loopback. Aguarda o arranque completo, executa `gym reload`, grava e encerra; o hash do JAR testado é comparado com o download a publicar.

O [workflow Forge](../.github/workflows/build-realm-gym-forge-1.21.1.yml) repete as verificações antes de publicar. Preserva o JAR de produção antes de compilar fixtures e recusa metadados de testes, referências NeoForge, classes de Minecraft ou de outros loaders incorporadas ou bytecode diferente de Java 21. Verifica os metadados Forge, modelos, receitas, colisões e traduções. O relatório `mod-pronto/realm-gym-0.6.0-forge-1.21.1.build.json` liga o download ao commit e à execução CI, com os hashes do JAR, ZIP e capturas.

O âmbito cobre a edição independente, servidor nativo e servidor integrado. Uma sessão remota com dois clientes autenticados e combinações com outros mods de atributos, renderização ou movimento exigem validação própria. O balanceamento continua sujeito a afinação com jogadores.
