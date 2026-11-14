# Realm Gym — validação Forge 1.20.1

Validação local em **14 de novembro de 2026**, com **Minecraft 1.20.1, Forge 47.4.0 e Temurin Java 17**, incluindo cliente gráfico real sob Xvfb/Mesa.

## Resultados

- **74 testes JUnit aprovados:** usam as mesmas fontes da edição NeoForge, adaptadas apenas para as conveniências Java 17. Abrangem os seis minijogos, dificuldade relativa ao nível/carga/fadiga, inputs inválidos, limites e bónus diário, rotinas, persistência, classificações, contactos das mãos/pés e interpolação entre snapshots.
- **20 GameTests nativos aprovados:** receitas, colocação/rotação e colisões das seis máquinas, todas as células ocupadas, destruição sem duplicação de itens, suporte, máquinas antigas, comandos/permissões, NBT, painel, modificadores e montagem real. Incluem um teste específico do salto Forge: atributo sincronizável, progressão máxima, aplicação repetida sem acumulação e impulso vertical sem alterar a velocidade horizontal. Os passageiros destes testes nativos são ArmorStand; jogadores reais são exercitados na sessão gráfica.
- **Sessão real de cliente e servidor integrado aprovada:** abriu o perfil, catálogo e editor, guardou um plano pelos canais Forge e jogou uma série de cada exercício pelos controlos do cliente. Resultados locais: supino **8/8, 96%**; passadeira **8/8, 75%**; boxe **8/8, 80%**; elevações **8/8, 84%**; remo **8/8, 90%**; agachamentos **8/8, 93%**.
- Confirmou aumento de carga com agachar + botão direito sem montar, preservação do plano guardado, bónus diário único, níveis permanentes, sincronização do salto para o cliente, recordes no mundo, cancelamento por SHIFT e destruição da máquina, e fecho/reabertura do mundo durante uma montagem sem deixar veículos temporários ou duplicar modificadores.
- **32 capturas**, incluindo equipamentos, personagem, perfil, planos, minijogos, português, classificação e disposição compacta. Os oito grupos de modelos animados foram verificados no ModelManager Forge. Foram capturados 24 frames por exercício para prévias de movimento.
- O HUD local desenhou **1 274 frames interpolados entre snapshots**, em 2 578 frames de treino, com resultados calculados exclusivamente no servidor. Estes números são observações da execução local, não requisitos fixos de desempenho.

## Fixtures e alcance

O jogador de QA começa com **90 XP por atributo** para tornar visível uma subida de nível após cada série. O teste de carga usa temporariamente 100 XP no supino para desbloquear 25 kg e repõe a seleção antes das séries. O plano usa oito repetições por exercício e descanso curto/controlado. Nenhuma pontuação final ou resultado de série é injetado: o servidor calcula-os a partir dos controlos efetivos.

Esta validação cobre o mod independente em servidor de testes e cliente com servidor integrado. Não cobre uma sessão remota com dois clientes autenticados, combinações com mods externos de stats/renderização, nem a conversão de mundos de Minecraft 1.21.1 para 1.20.1.

## Publicação reproduzível

O [workflow Forge](../.github/workflows/build-realm-gym-forge.yml) repete build, JUnit, GameTests e cliente integrado antes de publicar. Guarda o JAR de produção antes dos testes e confirma que o `main` ainda corresponde ao commit validado.

O [empacotador](../.github/scripts/gym_forge_release.py) verifica metadados Forge/Minecraft, bytecode **Java 17**, pack format 15, receitas/loot/tags 1.20.1, modelos, colisões e traduções. Rejeita classes de teste, bibliotecas/jogos incorporados e recursos com caminhos exclusivos de 1.21. Os artefactos `mod-pronto/realm-gym-0.5.1-forge-1.20.1.*` incluem JAR, ZIP, hashes e metadados com commit/execução testados. A [galeria](../media/realm-gym/0.5.1-forge-1.20.1/README.md) conserva as capturas e o resultado do cliente da publicação CI.
