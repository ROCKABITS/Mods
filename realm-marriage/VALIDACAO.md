# Validação — Realm Marriage 0.1.0

Minecraft **1.21.1**, NeoForge **21.1.256**, Java **21**. As automações ficam em `src/test`, `src/gametest` e `src/visualtest` e são excluídas do JAR de produção. A publicação conjunta executa `.github/scripts/marriage_release.py`; uma falha em qualquer verificação bloqueia a publicação dos três JARs.


## Resultado publicado — 20/10/2026

**Aprovado:** [GitHub Actions 39488185732](https://github.com/ROCKABITS/Mods/actions/runs/39488185732), origem `26ac0103890ab6db62f314ff3ea4ef06cd032ec0`, artefactos publicados em `31043148f630e74fa88f44adb93ae933b014e9d1`.

Passaram os **139 testes JUnit**, as suites nativas da tabela e a sessão com **quatro processos Minecraft ligados por TCP LAN, usando identidades offline**. As carteiras e o Crown conservaram os valores esperados depois do divórcio e da reabertura do mundo. Os três JARs e os ZIPs publicados foram descarregados novamente e verificados por SHA-256, integridade, versões, origem e ausência de fixtures. As dez capturas públicas também correspondem aos hashes do relatório.

O [ZIP com os três mods](../mod-pronto/realm-marriage-0.1.0-suite.zip) tem 481760 bytes e SHA-256 `9f592acf0d0077b4dea9f6cf8e90e377734e445354da11670f03812402c5c9a5`. [Galeria e ícone](../media/realm-marriage/0.1.0/README.md).

## Suites e critérios de publicação

| Projeto | JUnit | GameTests independentes | GameTests com integrações |
| --- | --- | --- | --- |
| Realm Marriage 0.1.0 | 45 | 23 | 23 |
| Crown Ledger 0.2.7 | 59 | 29 | Exercitado pelos testes de integração Marriage e pela sessão LAN. |
| Realm Identity 0.1.2 | 35 | 23 | Exercitado pela ponte de privacidade Marriage e pela certidão no cliente LAN. |

Os testes de domínio cobrem consentimento, exclusividade dos UUIDs, escolha conjunta dos papéis, convites, votos individuais, assinaturas, janela de objeção, suspensão, retoma com ambos, expiração, relógio recuado, cancelamento, três regimes, conservação monetária, divisão negociada, cêntimos cumulativos, créditos offline, novo casamento, reservas idempotentes e referências persistentes.

Em modo independente, os testes que dependem de uma integração ausente terminam sem executar esse ramo opcional. Os marcadores exigidos no perfil integrado confirmam a execução dos casos financeiros e governamentais; o contador independente não representa 23 cenários financeiros.

Os GameTests usam objetos reais de servidor e ficheiros NBT reais. Verificam colocação do altar em duas partes, estrutura incompleta, distância, perfil e carteira, documentos únicos, referências falsificadas, privacidade do cônjuge e ausência de tokens secretos nos menus. O contrato de permissões administrativas é autoritativo. Um teste específico confirma que um refresh não descarta a escolha seguinte e que a resposta traz o identificador do pedido.

Na integração são carregados os JARs novos do Crown e Identity, mais dez companheiros publicados e verificados por SHA-256: Chronicles RP 0.5.4, Realm Council 0.2.2, Contracts & Bounties 0.4.0, Realm Notifications 0.1.1, Realm Economy 0.4.0, Realm Casino 0.3.0, Laws & Justice 0.4.0, Realm Markets 0.2.1, Realm Rewards 0.1.2 e Realm Titles 0.1.0. Origins, Jupiter e Origins: Classes também têm checksums fixados.

Foram construídos cenários de falha de gravação do registo e da tesouraria: o caminho do ficheiro é temporariamente substituído por um diretório, causando uma falha real de escrita. A recuperação verifica que as duas contribuições não são debitadas outra vez, a taxa só entra uma vez e um rendimento legítimo recebido entretanto não é sobrescrito. Outro teste guarda efetivamente o recibo do jogador antes de recuperar o registo. Os testes governamentais usam o mandato real do Realm Council, incluindo falta de juramento, expiração e sucessão. O teste de privacidade chama a ponte real do Identity quando este está instalado.

## Quatro clientes Minecraft e TCP LAN

`MarriageLanQA` inicia **quatro processos Minecraft reais**, ligados por TCP a um servidor integrado publicado em LAN. São **identidades de teste offline**, sem autenticação de quatro contas Mojang. Os clientes utilizam botões nativos, pacotes C2S normais, interações com os itens/bloco e a tecla G. Os saldos iniciais, nomes, cenário e personagens são fixtures; os movimentos posteriores usam a carteira real, o registo real e a tesouraria real. Não são injetados snapshots financeiros para simular o resultado.

O percurso exige:

1. Pedido e aceitação pelo outro cliente; escolha de celebrante e testemunha, acordo aprovado por ambos e convites aceites.
2. Celebrante e convidados **sem permissões administrativas**; abertura da cerimónia por quem foi escolhido.
3. Desconexão real de um noivo, suspensão sem cobrança, reconexão e retoma com ambas as confirmações.
4. Objeção de um convidado pela tecla G, justificação, palavra concedida e nova retoma consentida.
5. Votos individuais, assinatura da testemunha, conclusão e taxa pública real.
6. **50000 + 30001 cêntimos → 75001 comuns + 5000 no Crown.** Levantamento de 1000, depósito de 500 e consolidação do restante preservam o total.
7. Emissão de documentos e inspeção efetiva da certidão numa `IdentityScreen`.
8. Divórcio com o outro cliente desligado, créditos de **37500 / 37501**, recolha uma vez, encerramento efetivo do mundo e reabertura com os mesmos saldos.
9. Movimento e câmara livres durante o HUD, controlos dentro dos limites em 320×240, 480×270 e 800×450, e preferências nativas guardadas em disco.

A publicação exige `result.json.success == true`, os indicadores de cada etapa, quatro processos, o marcador `MARRIAGE_LAN_CLIENT_PASSED`, imagens reais 1600×900 e a ausência de fixtures nos binários. Os resultados, origem Git, versões, checksums e endereço do workflow ficam nos `.build.json` publicados em `mod-pronto/`. A galeria conserva as capturas e o relatório do cliente.

## Limites comprováveis

- O teste de quatro processos comprova uma sessão TCP LAN com identidades offline. Não comprova autenticação Mojang, acesso pela Internet, proxies de servidor ou todos os modpacks.
- A suite nativa usa o runtime dedicado de GameTests. O percurso humano completo foi automatizado num servidor integrado; não foi repetido com quatro contas autenticadas num servidor dedicado de produção.
- Os três regimes financeiros são exercitados nativamente; a cerimónia completa com quatro clientes usa Dinheiro conjunto.
- O Realm Admin real ainda não está disponível. Foi testado o contrato do fornecedor, incluindo negação autoritativa, sem alegar integração com um mod inexistente.
- As falhas financeiras são injetadas em gravações e recibos reais; não foi feito um teste físico de corte de energia. A recuperação depende de ficheiros e backups coerentes. Corrupção ou divergência ambígua bloqueia a operação, em vez de adivinhar o saldo.
- As compras dos outros mods não são interceptadas. O consumidor da API deve manter o seu próprio recibo persistente de entrega. A integração não torna automaticamente atómica a entrega de itens feita por outro mod.
- As imagens mostram recursos próprios no cliente real. A construção usada no cenário de testes não é uma estrutura gerada pelo mod.
