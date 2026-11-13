# Realm Fitness 0.3.0 Beta — validação

Validação local em 13 de novembro de 2026, com Minecraft 1.21.1, NeoForge 21.1.256, Temurin Java 21 e cliente gráfico real sob Xvfb/Mesa.

## Testes de lógica e servidor

- **46 testes JUnit:** os seis minijogos podem ser concluídos com os controlos documentados; ausência de inputs, spam de teclas, sequências antigas e séries incompletas não produzem uma conclusão válida. Inclui taps rápidos entre ticks, limites de experiência, bónus diário único, relógio recuado, fadiga/recuperação, planos ativos imutáveis, cargas bloqueadas, limites de rotinas, separação de jogadores, persistência JSON, dados inválidos e classificações comparáveis por categoria/zona/dimensão.
- **18 GameTests nativos:** equipamentos e receitas, colocação das seis máquinas e de todas as células nas quatro direções, resolução das âncoras, destruição por qualquer célula com um único drop, rejeição de obstáculos distantes, perda de suporte, compatibilidade com máquinas antigas de duas partes, colisões locais e aberturas, destruição em criativo, NBT dos atletas, rejeição de NBT inválido, modificadores sem acumulação e configuração persistente do painel, passageiros reais em entidades de montagem nas quatro direções, desmontagem ao destruir equipamento, estados visuais em uso e exclusão das montagens temporárias da gravação do mundo. Os testes de passageiros nativos usam entidades ArmorStand; a montagem por jogadores é exercitada no cliente real.

## Cliente e servidor integrado reais

O teste automatizado abre um mundo efetivo, coloca as máquinas, abre os ecrãs de produção, cria uma rotina pelo editor e guarda-a pelos canais C2S/S2C. Executa depois um plano controlado com os seis exercícios, usando as teclas e os botões de rato do cliente. A entrada usa o caminho real de interação com o bloco e as séries decorrem sem Screen. Uma interação real a agachar verifica o aumento da carga, sem montar e sem modificar a rotina guardada. Não envia pontuações finais nem injeta resultados.

Cada série verifica repetições válidas, precisão e avanço único do plano. O cenário confirma seis resultados, o objetivo diário e um único bónus, os modificadores permanentes, o histórico e a atualização do painel no mundo. Verifica cancelamento por SHIFT e destruição da máquina. Fecha o mundo durante uma montagem ativa e reabre efetivamente o mundo e volta a verificar o plano concluído, os resultados, os níveis e a ausência de acumulação dos modificadores.

Para o teste isolado de carga, o supino recebe temporariamente 100 XP para desbloquear 25 kg; o valor e a seleção são repostos antes das séries. A base das séries é **90 XP por atributo**, escolhida para que uma série real torne visível a subida de nível. A rotina de teste usa oito repetições por exercício e um descanso reduzido/controlado para acelerar a execução. Estes parâmetros são fixtures; os resultados das séries são calculados pela simulação de produção no servidor.

As **32 capturas** incluem os seis modelos no renderizador Minecraft, incluindo seis vistas individuais e seis vistas das estruturas montadas no mundo, perfil e personagem 3D, catálogo, rotinas, editor, seis minijogos montados, HUD compacto, atributos conquistados, histórico, recordes, calendário, perfil compacto, português e classificação no mundo. Seis vídeos curtos e uma prévia GIF são codificados a partir de 24 frames capturados em cada exercício; mostram animações e controlos de produção. O teste também confirma que os controlos visíveis do perfil cabem em 320 × 240 unidades GUI.

## Publicação reproduzível

O [workflow](../.github/workflows/build-realm-fitness.yml) repete build, testes JUnit, GameTests e cliente integrado antes de publicar. Conserva uma cópia do JAR de produção antes das execuções com código de teste e publica apenas se a main ainda estiver no commit validado.

O empacotador confirma metadados, bytecode Java 21, recursos, paridade de traduções e ausência de classes de teste e bibliotecas/jogos incorporados no JAR. `mod-pronto/realm-fitness-0.3.0.build.json` regista o commit, execução CI, contagens, verificações e hashes do JAR, ZIP e capturas. Os relatórios completos ficam nos artefactos da execução; o resultado do cliente acompanha a [galeria](../media/realm-fitness/0.3.0/README.md).

## Âmbito

Validada a edição independente no cliente e servidor integrado, além do servidor de testes nativos. Não se considera testada uma sessão remota com dois clientes autenticados, nem combinações com mods de stats, modificadores de movimento ou renderizadores alternativos. As poses procedurais usam a skin/camadas exteriores e omitem a renderização do equipamento durante o exercício.

Os indicadores interpolam as atualizações recebidas e usam uma previsão visual curta do esforço; o cálculo das repetições e recompensas continua exclusivamente no servidor. Testes de apresentação verificam movimento entre pacotes, continuidade ao receber correções e limites durante interrupções de rede.

Os testes confirmam os resultados das seis mecânicas com inputs automatizados. A dificuldade, os limites e as percentagens continuam sujeitos a afinação com jogadores. O [guia](README.md) descreve os valores, controlos, âmbito das zonas de ginásio e funcionalidades desta beta.
