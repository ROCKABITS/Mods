# Validação — Contracts & Bounties 0.4.1 / Forge 1.20.1

Validação local em 12 de novembro de 2026, com Minecraft 1.20.1, Forge 47.4.0, Temurin Java 17 e cliente gráfico real sob Xvfb/Mesa. O port parte da edição 0.4.1 em `contracts-bounties`, preservada para 1.21.1/NeoForge.

- **91 testes JUnit**, sem falhas, erros ou testes ignorados: reserva/reembolso de dinheiro, entregas, concorrência, limites, persistência, regras das missões, rotação diária, recibos, pesquisa e disposição do seletor visual. Inclui a verificação de que as missões em esmeraldas não prometem reputação de uma carteira indisponível.
- **38 GameTests nativos** num servidor Forge: registos e receitas, inventário criativo, colocação e destruição nas quatro direções, variantes multibloco, loot único, sessões físicas, pagamentos em esmeraldas, proteção de itens com NBT, API pública com fornecedor de teste e leitura estrita dos dados guardados.
- **11 capturas no cliente Minecraft**, com os modelos e ecrãs de produção: três variantes de quadros, contratos, formulário, catálogo de itens, pesquisa, seleção exata sem escrever IDs, preservação do formulário, renovação/fecho da sessão, dimensão mínima e apresentação em português. Os valores financeiros destas onze capturas são dados de apresentação.
- **Percurso num servidor integrado real**, com um jogador real e os canais Forge C2S/S2C: publicar/cancelar/reclamar devolveu exatamente as 64 esmeraldas iniciais; cinco entregas e um bónus controlado produziram exatamente seis esmeraldas; repetir a última entrega não consumiu outro item nem duplicou o pagamento. Os recibos foram confirmados em `playerdata` e no jogador do `level.dat`. A celebração chegou pelo canal Forge e foi renderizada no HUD. Depois de fechar e reabrir efetivamente o mundo, mantiveram-se os seis recibos pagos e o saldo de 70 esmeraldas.

O build final gera bytecode Java 17 (major 61) e aplica `reobfJar`. O empacotador verifica metadados Forge, Minecraft 1.20.1, formato dos recursos/receitas, 57 modelos de missões e ausência de classes de testes, classes Minecraft/Forge incorporadas e referências NeoForge no JAR.

## Repetição em CI e proveniência

[Workflow Forge](../.github/workflows/build-contracts-forge.yml): repete o build, JUnit, GameTests, cliente visual e percurso integrado. Só publica os downloads depois de todos passarem e se a `main` continuar no commit testado. Guarda uma cópia do JAR reofuscado antes dos testes de runtime, para publicar exatamente o artefacto construído.

O ficheiro `mod-pronto/contracts-bounties-0.4.1-forge-1.20.1.build.json` regista o commit de origem, execução CI, versões, contagens e hashes do JAR/ZIP e capturas. Os relatórios completos e logs ficam nos artefactos da execução; os relatórios dos clientes também acompanham a [galeria Forge](../media/contracts-bounties/0.4.1-forge-1.20.1/README.md).

## Limites

Este port fornece a edição independente em esmeraldas. Não valida nem fornece ports do Realm Origins, Treasury, Council, Admin ou Notifications. A API de contratos públicos foi exercitada com um fornecedor de teste; isso não constitui uma integração publicada do Treasury para 1.20.1. A carteira digital falha de forma explícita sem alterar saldos.

O percurso de cliente valida entregas das missões com quantidades e recompensas controladas e inclui reabertura do mundo. Não equivale a um ensaio de todos os objetivos de sobrevivência ou de servidores remotos com dois clientes autenticados. Não foram testados Fabric, NeoForge 1.20.1, outras versões Minecraft/Forge ou downgrade de mundos 1.21.1.
