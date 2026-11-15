# Mapa técnico e revisão — Mods

Revisão iniciada em **12 de novembro de 2026**, a partir de `f9dbae3a6183937c90af5741bd1ca8f1e0e78118`. Este documento descreve a estrutura e as verificações desta revisão; não substitui as validações de lançamento de cada mod.

## Estrutura e versões

O repositório é um monorepositório de **16 projetos Gradle independentes**: 15 mods atuais e o Realm Villager Economy arquivado. Não existe um build Gradle único na raiz. A base analisada contém 2 617 ficheiros versionados, incluindo 449 ficheiros Java de produção, 705 ficheiros em `media/` e 229 em `mod-pronto/`.

Plataforma comum: **Minecraft Java 1.21.1, NeoForge 21.1.256+ e Java 21**. Os builds usam ModDevGradle. Verificar sempre os intervalos de dependências declarados pelo projeto antes de alterar uma integração.

| Pasta | Versão publicada | ID interno | Responsabilidade |
| --- | --- | --- | --- |
| [realm-origins](realm-origins/README.md) | 0.5.7 | `cronicasrp` | Personagem, ocupação, nomes RP, carteira, câmbio e reputação. |
| [realm-council](realm-council/README.md) | 0.2.2 | `realmcouncil` | Eleições, candidatura, voto, mandato e juramento. |
| [crown-ledger](crown-ledger/README.md) | 0.4.0 | `kingdomtreasury` | Realm Treasury: tesouro público, impostos, salários, contratos, votações e empréstimos. |
| [contracts-bounties](contracts-bounties/README.md) | 0.4.0 | `contracts` | Murais, materiais, recompensas reservadas e quests diárias. |
| [realm-markets](realm-markets/README.md) | 0.2.1 | `realmmarkets` | Bancas, ofertas, stock, vendas offline e recolhas. |
| [realm-economy](realm-economy/README.md) | 0.5.0 | `realmeconomy` | Preços, impostos, serviços e orçamento das colónias MineColonies. |
| [realm-casino](realm-casino/README.md) | 0.3.0 | `realmcasino` | Doze jogos, banca, reservas, prémios e mesas multiplayer. |
| [laws-justice](laws-justice/README.md) | 0.4.0 | `lawsjustice` | Prisões, tribunal, mandados, trabalhos, multas, fianças e registos. |
| [realm-marriage](realm-marriage/README.md) | 0.1.0 | `realmmarriage` | Cerimónias, regimes, conta comum, certidões e divórcio. |
| [realm-identity](realm-identity/README.md) | 0.1.2 | `realmidentity` | Documento físico, assinatura, consentimento, títulos e estado civil. |
| [realm-titles](realm-titles/README.md) | 0.1.0 | `realmtitles` | Catálogo de títulos e nomeações permanentes ou temporárias. |
| [realm-rewards](realm-rewards/README.md) | 0.1.2 | `realmrewards` | Recompensas únicas por avanço e pagamentos pendentes. |
| [realm-notifications](realm-notifications/README.md) | 0.1.1 | `realmnotifications` | HUD opcional, agrupamento de rajadas, preferências e diário da sessão. |
| [realm-progression](realm-progression/README.md) | 0.1.0 | `realmprogression` | Desbloqueios permanentes de crafts ao atingir metas públicas. |
| [season-eight](season-eight/README.md) | 0.1.0 | `seasoneight` | Menu, carregamento e marca da janela, exclusivamente no cliente. |
| [realm-villager-economy](realm-villager-economy/README.md) | 0.1.0, arquivo | `realmvillagereconomy` | Antecessor substituído pelo Realm Economy. Não instalar ambos. |

As versões da tabela são as publicadas na base da revisão. As alterações de apresentação desta revisão ainda não constituem um novo lançamento.

## Integrações que condicionam alterações

- **Carteira pessoal:** Realm Origins conserva o ID `cronicasrp` e o pacote `pt.cronicasrp`. Mudar apenas o nome comercial não autoriza renomear dados, IDs ou APIs. Origins NeoForge 0.3.2 e Jupiter 2.3.7 são obrigatórios; Origins: Classes 0.3 é opcional.
- **Tesouro:** Realm Treasury conserva a pasta `crown-ledger` e o ID `kingdomtreasury`. A carteira pessoal, o tesouro do reino e os cofres das colónias são contas distintas. A ausência de uma integração opcional não deve provocar uma troca silenciosa de moeda depois de uma falha.
- **Autoridade pública:** Realm Council fornece o governante com mandato ativo e juramento. Ser Presidente não equivale a operador nem a gestor de uma colónia. As permissões têm de ser reavaliadas pelo servidor em cada operação.
- **Conta de casamento:** a [API explícita](realm-marriage/API.md) reserva pagamentos com recibos e consumidor identificado. Casar não faz os outros mods passarem automaticamente a cobrar à conta conjunta.
- **Notificações:** o canal é opcional, apenas servidor → cliente. É apresentação de acontecimentos já confirmados; não decide pagamentos. O diário é privado, limitado e apenas da sessão.
- **Progressão:** requer Treasury 0.4.x e consulta saldo em cêntimos, sem gastar dinheiro. O desbloqueio é permanente e o âmbito é o fabrico suportado, não a desativação de mods carregados.
- **MineColonies:** a [integração](realm-economy/MINECOLONIES.md) usa cofres por colónia, permissões nativas, exportações presenciais e autorizações de despesas. Não retira automaticamente materiais dos armazéns nem muda o funcionamento nativo dos trabalhadores.

## Onde trabalhar no código

A organização recorrente é `src/main/java/pt/<mod>/`: `domain` contém regras e máquinas de estados; `data` trata persistência; `service` valida operações; `network` limita e transporta pedidos; `compat` adapta companheiros opcionais; `client` desenha interfaces. Nem todos os projetos têm todas estas pastas.

Para alterações financeiras, seguir a operação desde o pacote de rede até ao serviço, ao recibo persistido e à integração externa. Os serviços consultados incluem verificações de sessão, distância ao bloco, dimensão, permissões e limites dos pedidos. Testes de domínio isolados não verificam por si só todas estas condições no Minecraft.

Para alterações visuais, verificar texto em português e inglês, limites dos controlos, foco, tooltips e redimensionamento. Preservar os estilos existentes: pergaminho, madeira, verde/dourado e, na criação de personagem e Season 8, escuro/lilás. As capturas em `media/` documentam clientes reais das versões publicadas.

## Compilar e validar

Executar dentro do projeto pretendido, com JDK 21:

```bash
./gradlew --no-daemon --console=plain test
./gradlew --no-daemon --console=plain build
```

Vários projetos têm `tools/test_domain.py`, útil para uma verificação rápida sem descarregar o Minecraft. Esses scripts nem sempre abrangem toda a suite: o teste Gradle continua a ser a referência para rede, recursos e classes dependentes do Minecraft.

As suites nativas e visuais são ativadas por propriedades específicas de cada projeto. Por exemplo, no Notifications:

```bash
./gradlew --no-daemon --console=plain -PnotificationsGameTests runGameTestServer
./gradlew --no-daemon --console=plain -PnotificationsVisualTests runClient
```

O ensaio visual precisa de um servidor gráfico/OpenGL; nesta revisão foi preparado Xvfb com renderização por software. Os utilitários `tools/cloud_gradle.py` dependem de um JDK e truststore preparados em `/workspace/.tools`; esses caminhos são específicos do ambiente cloud.

Os workflows em `.github/workflows/` compilam, executam validações e podem publicar JARs, ZIPs, capturas e proveniência em `mod-pronto/`. Os pipelines atuais são principalmente de publicação em `main` ou execução manual; abrir um PR não implica executar automaticamente todos eles. Conferir o workflow do projeto antes de o acionar. Não substituir os ficheiros publicados por um build de desenvolvimento.

## Problemas tratados nesta revisão

1. **Preferências de Notifications em alturas reduzidas:** a geometria antiga, num ecrã lógico 320 × 180, colocava controlos das últimas opções sobre o rodapé. A disposição passa a calcular quantas opções cabem, apresentar navegação quando necessário e limitar a página após redimensionar. As seis opções continuam numa página em 320 × 240 e nas janelas normais. 320 × 180 é um ensaio adicional de disposição forçada; o escalonamento normal do Minecraft pode impor um mínimo maior.
2. **Leitura do menu:** linhas alternadas discretas, indicação dourada do separador selecionado, estados desativados distinguíveis e tooltips com o nome e o valor da preferência. Textos compridos não invadem a coluna dos controlos.
3. **Instalação do Identity:** o README recomendava Identity 0.1.1 através do pacote antigo de Titles. O pacote foi inspecionado: contém efetivamente 0.1.1. A recomendação passa a apontar para os JARs atuais, incluindo Identity 0.1.2, e identifica o pacote como histórico.
4. **Plano desatualizado:** Laws & Justice já está publicado na 0.4.0, embora uma tabela ainda o desse como pendente. Atualizados os estados de Council, Contracts e Notifications e distinguido o roteiro histórico do catálogo atual.

## Verificações e limites

- Analisados os 526 JSON existentes na base da revisão: sem erros de sintaxe.
- Verificados os destinos locais das ligações Markdown: sem ficheiros em falta na base da revisão.
- Comparadas as chaves de português com inglês nos catálogos existentes: sem chaves inglesas em falta nos catálogos portugueses presentes. Identity e Markets não têm catálogo separado `pt_br` nesta base; isso não foi contado como tradução existente.
- Comparados por SHA-256 os 53 JARs com `jar_sha256` nos respetivos ficheiros de proveniência, incluindo versões históricas: todos conferem. Comparados também os ZIPs correspondentes quando a proveniência fornece `zip_sha256`. Isto confirma integridade em relação aos metadados do repositório, não todos os comportamentos dos binários.

A revisão abrange inventário de todos os projetos, documentação, metadados, recursos e inspeção orientada de interfaces, rede, serviços e regras. Não é uma auditoria linha a linha de todo o código nem uma sessão no modpack completo do utilizador. Resultados de lançamento anteriores continuam nos `VALIDACAO*.md` de cada projeto; não são apresentados como testes repetidos agora.

O ensaio visual local produziu `success: true`, seis grupos de verificações e cinco capturas de 1600 × 900, disponíveis na [galeria desta revisão](media/realm-notifications/review-2026-11-12/README.md). Incluiu controlos reais em 320 × 240, paginação forçada em 320 × 180, persistência das preferências, redimensionamento, diário e 200 chamadas reais pela API/S2C. O cliente era de desenvolvimento, sem autenticação e sem os companheiros instalados. Após concluir as verificações, o processo ficou na gravação/descarregamento de chunks do mundo de ensaio e foi terminado; o `success` do relatório não equivale a uma saída normal de `runClient`. Não foi executada novamente a suite financeira integrada nem a matriz multiplayer dos lançamentos anteriores.

## Testes executados nesta revisão

**803 testes JUnit aprovados nos 16 projetos, sem falhas nem erros.** Foi executada a tarefa Gradle `test` de cada projeto com Java 21; no Notifications foi executado também `build`, incluindo os quatro testes novos de disposição.

| Projeto | Testes aprovados |
| --- | ---: |
| `contracts-bounties` | 82 |
| `crown-ledger` | 102 |
| `laws-justice` | 130 |
| `realm-casino` | 82 |
| `realm-council` | 49 |
| `realm-economy` | 45 |
| `realm-identity` | 35 |
| `realm-markets` | 45 |
| `realm-marriage` | 45 |
| `realm-notifications` | 22 |
| `realm-origins` | 70 |
| `realm-progression` | 25 |
| `realm-rewards` | 12 |
| `realm-titles` | 48 |
| `realm-villager-economy` | 9 |
| `season-eight` | 2 |

O JAR de desenvolvimento do Notifications foi inspecionado: inclui `SettingsLayout` e exclui as classes de testes e de ensaio visual. As operações desta revisão não criam um novo download em `mod-pronto/`.
