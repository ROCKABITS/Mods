# Validação — Chronicles RP 0.5.0

Ambiente: Minecraft Java 1.21.1, NeoForge 21.1.256, Java 21, Origins (NeoForge) 0.3.2, Jupiter 2.3.7; Origins: Classes 0.3 presente e ausente.

- 56 testes JUnit: validação de RP, assinatura, economia/câmbio, armazenamento, tradução e pacotes de rede.
- 14 GameTests nativos com Classes e os mesmos 14 sem Classes: inventário, comandos/câmbio e seis cenários específicos de integração Origins.
- Cliente nativo com servidor integrado: duas execuções completas, com e sem Classes. A interface do Origins escolheu Avian e, quando disponível, Warrior; o pergaminho esperou pelas escolhas e registou a personagem através dos pacotes reais.
- Verificadas a assinatura desenhada, a animação de fortuna, a conservação do rascunho ao consultar os poderes e regressar, a ficha em inglês/português e uma mudança de Avian para Shulk sem alterar a fortuna ou os restantes dados de RP.
- Interfaces inspecionadas em 1100×700 e 854×480. As imagens em `preview` são capturas nativas de personagens de teste.
- O JAR de distribuição exclui GameTests, o automatismo de cliente, origens de teste e dependências externas.

## Limites da verificação

A integração usa a API do port NeoForge de IAFEnvoy, nas versões indicadas. Outros ports e versões do Origins não foram certificados. Os datapacks precisam do formato deste port.

Os testes confirmam a leitura, seleção, apresentação e sincronização dos dados; não certificam todas as habilidades do Origins ou de add-ons. No arranque do Origins 0.3.2 houve mensagens de erro do próprio port ao descodificar definições de efeitos de lentidão/fraqueza. Isso não impediu os cenários de integração, mas o comportamento dessas habilidades requer verificação no Origins. A página do autor identifica o port como estando em desenvolvimento.

O ambiente de teste não tem dispositivo de som; o Minecraft desativou o motor de áudio. Não foi feita validação auditiva. O modo de teste usa renderização gráfica por software.

JAR de distribuição: `cronicasrp-0.5.0.jar`. SHA-256: `11391fc92010449de4778e14ccbad77318c375faeb2eb9272fdd89665bb81a83`.
