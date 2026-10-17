# Validação — Realm Notifications 0.1.0 Beta

A [execução 39009308013](https://github.com/ROCKABITS/Mods/actions/runs/39009308013) terminou com **sucesso**, em 17 de outubro de 2026, sobre o código [`0d9e45962ae298e4fac98f46c6c9b5823e0ffc8d`](https://github.com/ROCKABITS/Mods/commit/0d9e45962ae298e4fac98f46c6c9b5823e0ffc8d). A publicação dos sete mods foi feita num único commit, após a aprovação dos testes. Os JARs anteriores permanecem preservados.

## Compilação e testes de servidor

Os sete JARs de produção foram compilados antes de adicionar fixtures ao ambiente de teste. As suites unitárias completas passaram, sem falhas ou testes unitários ignorados: **275 testes no total**.

| Mod | JUnit | GameTests no modo individual |
| --- | --- | --- |
| Chronicles RP 0.5.4 | 66 | 37 |
| Contracts & Bounties 0.3.2 | 46 | 38 |
| Realm Council 0.2.2 | 49 | 25 |
| Crown Ledger 0.2.1 | 46 | 20 |
| Realm Markets 0.2.1 | 45 | 45 |
| Realm Rewards 0.1.2 | 12 | 17 |
| Realm Notifications 0.1.0 | 11 | 15 |

No Realm Notifications, os 15 GameTests registados passaram nos dois modos. **Sem companheiros, seis casos de integração terminam sem executar a parte dependente dos outros mods**; os nove restantes verificam o protocolo, a API e os limites. Na segunda execução, os **15 casos** correram com os seis JARs novos, Realm Identity 0.1.0 e as dependências reais Origins 0.3.2 e Jupiter 2.3.7. Origins: Classes 0.3 foi usado na suite individual do Chronicles; não foi carregado na integração do Notifications.

As verificações cobrem:

- Canal opcional, cliente sem suporte, desconexão, chamadas fora da thread do servidor e falhas de apresentação.
- Transporte de texto traduzido, argumentos, Unicode e enquadramento do pacote; limites de tamanho e complexidade; rejeição de conteúdo dinâmico e remoção de estilos/interações/hover privados.
- Adaptadores dos seis mods novos a enviar o pacote real, com alternativa de chat quando não há suporte.
- Conquista Vanilla real a pagar uma vez na carteira do Chronicles, reconquista sem repetição, pagamento agregado de pendências e falha do canal sem perda do dinheiro.
- Comandos reais de atribuição administrativa, levantamento/depósito físico e doação ao Crown Ledger.
- Contrato real: reserva pelo autor, entrega de materiais, recompensa por recolher e pagamento apenas na recolha; repetição vazia e alternativa para cliente sem o canal.

Os GameTests usam o servidor Minecraft real e os serviços/perfis reais dos mods. Os jogadores de teste simulam o suporte do canal e capturam os pacotes emitidos. A integração exige os marcadores de aprovação dos seis adaptadores e dos acontecimentos financeiros/de contratos. Não se usaram versões antigas destes seis mods para aprovar os adaptadores novos.

## Cliente e revisão visual

O cliente Minecraft real criou um mundo integrado e recebeu **três chamadas genuínas da API pelo canal S2C**. O relatório [validation.json](../media/realm-notifications/0.1.0/validation.json) confirma:

- HUD com três cartões, posições superior esquerda e central, preferências e diário.
- Persistência das preferências, botão de som e ausência de pedidos de som no modo silenciado.
- As cinco posições e controlos em 320 × 240, histórico com 100 entradas longas e deslocação/limpeza.
- Fila limitada a 32, incluindo redução de três cartões para um, e limpeza dos dados ao terminar a sessão.

As quatro capturas têm **1600 × 900** e mostram o renderer de produção. O texto dos acontecimentos é **demonstração**, incluindo o exemplo de 50 €; não representa um pagamento nessa sessão nem altera o valor padrão de 2 € do Rewards. As operações financeiras foram verificadas separadamente nos GameTests. As imagens do HUD, preferências e diário foram também revistas visualmente.

Os testes unitários de layout verificam alturas entre 120 e 1080 e quatro larguras nas cinco posições, incluindo as alturas intermédias em que uma pilha inferior podia sair do ecrã.

[Galeria](../media/realm-notifications/0.1.0/README.md) · [ZIP de imagens](../media/realm-notifications/0.1.0/realm-notifications-0.1.0-media.zip).

## Ficheiros publicados

- [JAR](../mod-pronto/realm-notifications-0.1.0.jar): **50 572 bytes**.
- [ZIP do mod](../mod-pronto/realm-notifications-0.1.0.zip): **47 652 bytes**.
- [ZIP com o mod e seis atualizações opcionais](../mod-pronto/realm-notifications-0.1.0-suite.zip): **7 528 569 bytes**, sem dependências externas.
- [Proveniência, resultados e hashes dos companheiros](../mod-pronto/realm-notifications-0.1.0.build.json).

```text
5aa70ac5a7696ce626ffa40e2e530038e90c06819dbeea6f43be70d42310822a  realm-notifications-0.1.0.jar
1e022dbebf6070600fdb26ea6fa22055a1fcc174b41503a94062d71f5e5481b5  realm-notifications-0.1.0.zip
9bc9a927e4475f7aa202cd9c4d806d4d11490fa1e7ca44b2d83beee3d5530f70  realm-notifications-0.1.0-suite.zip
```

Depois do download do Git, foram verificados os hashes dos sete JARs e ZIPs, o conteúdo do pacote conjunto e as dimensões das quatro imagens. Os JARs de produção não incluem fixtures nem dependências externas.

## Limites

Não foi feita uma sessão com dois clientes autenticados, nem uma verificação visual de cada acontecimento de todos os companheiros. Os testes de compra das bancas e de eleições pertencem às suites individuais; nesta integração, os respetivos adaptadores foram exercitados, sem simular todos esses fluxos no HUD. Não há teste de persistência após corte abrupto de energia, nem histórico de notificações offline.

A compilação local nesta máquina continua bloqueada por uma resposta 403 ao artefacto NeoForge `neoform-runtime:2.0.31`. A compilação Minecraft e as verificações acima foram executadas pelo GitHub Actions, preservando a verificação TLS e os hashes fixados das dependências. Os testes locais independentes também passaram; não substituem as suites completas do CI.
