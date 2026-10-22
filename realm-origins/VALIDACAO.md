# Validação atual — Realm Origins 0.5.7

Campo de ocupação livre, com uma palavra até 24 caracteres, acentos/hífen e sugestões rápidas. A regra é validada no servidor antes de criar a personagem ou guardar alterações. O livro de pergaminho no P mantém-se. O formato dos dados mantém os campos anteriores; o protocolo passa a 8 para impedir que clientes da lista fixa recebam uma ocupação que não sabem descodificar. Atualizar servidor e clientes.

**Testes e publicação aprovados.** Os passos da [execução 39794991947](https://github.com/ROCKABITS/Mods/actions/runs/39794991947) aprovaram o código [`e407cd6`](https://github.com/ROCKABITS/Mods/commit/e407cd69444e6b97472760ac8bed66d20908864d), publicado em [`cf81db8`](https://github.com/ROCKABITS/Mods/commit/cf81db8). O [manifesto](../mod-pronto/realm-origins-0.5.7.build.json) regista versões, hashes, protocolo 8 e `custom_occupations_verified: true`.

- 70 testes JUnit aprovados, sem falhas nem testes ignorados: Unicode/NFC, acentos, hífen, limites, rejeição de frases/controlos, criação antes de sortear dinheiro, edição, armazenamento e pacotes.
- 38 GameTests aprovados em cada combinação: Origins/Jupiter/Classes, Origins/Jupiter sem Classes, e os doze companheiros publicados. São 38 casos em três combinações, 114 execuções. O novo teste invoca os handlers reais: frases são rejeitadas sem criar perfil ou alterar dinheiro; Alquimista e Cartógrafo são aceites e devolvidos ao cliente.
- Três clientes reais aprovados: nomes RP/TAB/chat com companheiros; criação com Classes; criação sem Classes. A criação escreve Alquimista, confirma o rascunho ao regressar de Origins, rejeita uma frase, usa a sugestão de Bardo e confirma a palavra guardada pelo servidor e apresentada no livro aberto por P. Inclui assinatura, boas-vindas, quatro páginas, levantamento/depósito de 1 cêntimo e janelas de 1600×900 e 854×480.

O [relatório gráfico](../media/realm-origins/0.5.7/rework-validation.json) confirma `custom_occupation: true` e `journal_key: true`. A [galeria](../media/realm-origins/0.5.7/README.md) inclui o campo e os atalhos. Os downloads públicos de JAR, ZIP e 23 imagens foram obtidos novamente e comparados com os hashes; o ZIP contém o mesmo JAR. A distribuição exclui fixtures.

| Artefacto | Bytes | SHA-256 |
| --- | --- | --- |
| [realm-origins-0.5.7.jar](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-origins-0.5.7.jar) | 6,444,120 | `0b90c49b9e3c49d2f0343301ac3662b21d6ce5551ea343fb7fc8ebdab9e09684` |
| [realm-origins-0.5.7.zip](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-origins-0.5.7.zip) | 6,432,780 | `4bf4b71361a3a1157e8ea2371cd9b06e2198911b4511a9b2466d9454932600e8` |

Âmbito: servidor integrado/cliente reais para as interfaces e GameTests nativos para handlers e regras. Não é um novo ensaio manual com vários clientes autenticados, nem certifica todas as GPUs/resource packs ou funcionalidades de cada companheiro. A sessão de desenvolvimento não dispõe de certificado autenticado de chat assinado. Atualiza servidor e todos os clientes para 0.5.7; os dados antigos mantêm-se, mas as versões anteriores não descodificam ocupações fora da lista fixa.

## Organização do GitHub

O projeto está em `realm-origins/`, com workflow e empacotamento renomeados e galeria em `media/realm-origins/README.md`. O arquivo de imagens 0.5.3 foi reunido na mesma pasta de galerias. Guias, descrição para CurseForge e instruções de instalação usam o nome Realm Origins.

A compilação na nova pasta passou e os **70 testes JUnit foram executados novamente, sem falhas nem testes ignorados**. As 121 entradas do JAR recompilado são idênticas às do JAR publicado: SHA-256 `0b90c49b9e3c49d2f0343301ac3662b21d6ce5551ea343fb7fc8ebdab9e09684`. Os 23 PNGs mantêm os hashes publicados. Foram verificados os links locais, imagens, paths do workflow e correspondência dos nomes de futuras capturas com o empacotamento.

Esta reorganização não publica uma versão nova do mod nem substitui os JARs, ZIPs ou relatórios de lançamento existentes. O namespace `cronicasrp`, os dados, os pacotes e o protocolo 8 são os mesmos. Não foram repetidos os clientes Minecraft ou as suítes nativas nesta mudança de paths; os resultados de jogo continuam documentados na validação 0.5.7 acima.

## Histórico — Realm Origins 0.5.6

Atualização visual: criação e boas-vindas cinematográficas; P abre novamente o livro original de pergaminho. Ficha, Feitos, História, Bolsa e câmbio usam a aparência anterior. Dados, economia e protocolo 7 mantidos.

**Publicado e verificado.** O [job de validação 119847161109](https://github.com/ROCKABITS/Mods/actions/runs/39785384916/job/119847161109) terminou com `conclusion: success`: compilação, 66 testes JUnit, 37 GameTests em cada uma das três combinações e três clientes reais. O código testado é [`3a1b441`](https://github.com/ROCKABITS/Mods/commit/3a1b441095370a961a83c6b7c74dc8d37eaeea03) e o commit de publicação é [`3fb8b1e`](https://github.com/ROCKABITS/Mods/commit/3fb8b1e). O [manifesto](../mod-pronto/realm-origins-0.5.6.build.json) conserva o source commit, hashes, dependências e resultados. A conclusão global da execução aparece como `cancelled` após o cancelamento de uma execução duplicada; todos os passos do job indicado, incluindo testes e publicação, constam como `success`.

- O cliente termina a primeira criação, entra no mundo e usa a keybinding P através do caminho normal de pedido ao servidor. O ecrã recebido é o livro de pergaminho.
- São exercitadas as páginas Ficha, Feitos, História e Bolsa, seguidas de um levantamento e depósito de 1 cêntimo, confirmando o mesmo saldo e perfil no final.
- O mesmo fluxo passou com Origins: Classes e sem esse complemento, em inglês e português, incluindo janelas de 1600×900 e 854×480. A criação e as boas-vindas conservam o estilo cinematográfico.
- O cliente com os doze mods companheiros verifica nomes RP, TAB, chat normal e perfil. Os GameTests usam Origins/Classes, Origins sem Classes e a combinação com todos os companheiros; são 37 casos em três combinações, 111 execuções.
- Foram inspecionadas as capturas reais do livro e do câmbio. A [galeria](../media/realm-origins/0.5.6/README.md) e o [relatório do cliente](../media/realm-origins/0.5.6/rework-validation.json) registam a aparência e `journal_key: true`.
- Os downloads públicos de JAR/ZIP e as 22 imagens foram obtidos novamente e comparados com os hashes publicados. As 36 classes comparadas de domínio, attachments, carteira e pacotes são idênticas às de Chronicles RP 0.5.4. O ZIP contém o mesmo JAR validado e a distribuição exclui fixtures.

| Artefacto | Bytes | SHA-256 |
| --- | --- | --- |
| [realm-origins-0.5.6.jar](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-origins-0.5.6.jar) | 6 442 292 | `3a47eff8af6ff159c946765e491d7e9ea4f5a5bfd6bfdec02c92d591b4a3adb5` |
| [realm-origins-0.5.6.zip](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-origins-0.5.6.zip) | 6 430 121 | `36e3573fb458f30148c4d776c7e61f563ce236aef5acd6a7472b1f8d649a7fc3` |

Âmbito: clientes e servidor integrado reais para as interfaces; GameTests nativos para regras e integrações. Não é um novo ensaio manual de vários clientes autenticados, nem certifica todas as GPUs, resource packs ou funcionalidades de cada companheiro. A sessão de desenvolvimento não dispõe de certificado autenticado de chat assinado. Mantém-se a instalação de Origins/Jupiter e a substituição do JAR anterior no servidor e nos clientes.

## Histórico — Realm Origins 0.5.5

Rework visual do Chronicles RP. Minecraft 1.21.1 / NeoForge 21.1.256 / Java 21, Origins (NeoForge) 0.3.2 e Jupiter 2.3.7; Origins: Classes 0.3 é opcional. Identificador interno `cronicasrp`, armazenamento e protocolo 7 mantidos.

**Aprovada.** A [execução 39703773699](https://github.com/ROCKABITS/Mods/actions/runs/39703773699), sobre o código [`4e7b298`](https://github.com/ROCKABITS/Mods/commit/4e7b2980796e7b467421f63a8a3316155096fffd), concluiu a compilação, testes e publicação. O commit de artefactos é [`4599cf5`](https://github.com/ROCKABITS/Mods/commit/4599cf5). O [manifesto de proveniência](../mod-pronto/realm-origins-0.5.5.build.json) regista as dependências reais, hashes e resultados.

| Verificação executada | Resultado |
| --- | --- |
| JUnit | 66 aprovados, sem falhas nem testes ignorados |
| GameTests com Origins, Jupiter e Classes | 37 aprovados |
| GameTests com Origins e Jupiter, sem Classes | 37 aprovados |
| GameTests com os doze companheiros publicados | 37 aprovados |
| Cliente real com companheiros | Nomes RP acima da cabeça, TAB, chat normal e perfil aprovados |
| Cliente real de criação com Classes | Fluxo gráfico completo aprovado |
| Cliente real de criação sem Classes | Mesmo fluxo aprovado, com ausência do add-on verificada |

São 37 casos nativos em três combinações, totalizando 111 execuções. Os clientes verificaram Origins a partir do botão da linhagem, regresso ao mesmo rascunho, quatro capítulos, assinatura desenhada, confirmação, boas-vindas, quatro separadores do perfil, traduções EN/PT e janelas de 1600×900 e 854×480. O teste efetua um levantamento e depósito de 1 cêntimo, confirmando o mesmo saldo e perfil no final. Os controlos são verificados quanto a limites e sobreposições; as capturas foram também inspecionadas visualmente. A repetição das boas-vindas e o botão/Enter para entrar no mundo não alteram os fundos nem a identidade.

A [galeria](../media/realm-origins/0.5.5/README.md) contém 21 capturas reais e o novo ícone. A captura de identidade foi repetida localmente depois de detetada uma imagem da transição; as 71 classes de produção desse runtime foram confirmadas byte a byte iguais às do JAR publicado, e o fluxo gráfico completo passou novamente. O [relatório adicional](../media/realm-origins/0.5.5/media-correction-validation.json) e a proveniência identificam esta correção. JAR e ZIP de instalação permanecem iguais. Os dois resultados gráficos da criação, com e sem Classes, constam da proveniência/artefacto de validação. As 36 classes comparadas de dados de domínio, carteiras, attachments e pacotes no JAR publicado são byte a byte iguais às de `cronicasrp-0.5.4.jar`. O identificador interno e o protocolo 7 permanecem.

Os downloads públicos foram obtidos novamente e os respetivos hashes, conteúdo do ZIP, metadados do JAR e todas as 22 imagens foram confirmados após a publicação. A distribuição exclui fixtures, GameTests e automatismos gráficos; mantém os avisos de licença dos recursos.

| Artefacto publicado | Bytes | SHA-256 |
| --- | --- | --- |
| [realm-origins-0.5.5.jar](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-origins-0.5.5.jar) | 6 437 734 | `f83029a3a2d966de8c11e2a2673751d709b942f25b074baadb75b4f9f3536dc0` |
| [realm-origins-0.5.5.zip](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-origins-0.5.5.zip) | 6 425 445 | `c2efdcfc37d37dfafe98ee3e42a037ddf6fa3912150279dc5494c14ae91cddb7` |

Âmbito: servidor integrado real para as interfaces, servidor nativo para GameTests; não é um ensaio manual de vários clientes autenticados. A presença dos doze companheiros verifica carregamento e os fluxos de RP exercitados, não todas as funcionalidades de cada mod. O chat da sessão de desenvolvimento não dispõe de certificado autenticado. A renderização usa software e não certifica todas as GPUs, escalas de interface, resource packs ou mods gráficos. As interfaces nativas de poderes e seleção pertencem ao mod externo Origins e são preservadas.

Atualização: retirar o JAR antigo do Chronicles RP, instalar `realm-origins-0.5.5.jar` no servidor e nos clientes e manter Origins/Jupiter. Não apagar o mundo, perfis ou configurações. Não instalar os dois JARs lado a lado. Não foram acrescentados campos de personalidade nem atributos, e a animação mostra fundos previamente gravados sem gerar outro pagamento.

## Histórico — Chronicles RP 0.5.4

**Aprovada.** A [execução conjunta 39009308013](https://github.com/ROCKABITS/Mods/actions/runs/39009308013), sobre o código [`0d9e459`](https://github.com/ROCKABITS/Mods/commit/0d9e45962ae298e4fac98f46c6c9b5823e0ffc8d), compilou o JAR de produção e aprovou **66 testes unitários** e **37 GameTests nativos em modo independente**, com Minecraft 1.21.1, NeoForge 21.1.256 e Java 21. Os [metadados desta compilação](../mod-pronto/cronicasrp-0.5.4.build.json) registam as contagens, versões e hashes.

O JAR novo desta versão foi também carregado com as outras cinco versões novas dos companheiros, Origins, Jupiter e Realm Notifications 0.1.0 na integração de **15 GameTests do Realm Notifications**; estes não substituem nem repetem a bateria completa de cada companheiro em modo integrado. A ponte opcional foi exercitada com o payload real e com fallback de chat para clientes sem o canal.

A integração executou os comandos reais de atribuição administrativa e conversão de dinheiro físico, verificando os saldos e o envio das notificações sem duplicação no chat.

O cliente Minecraft real aprovou o HUD, as preferências e o diário do Realm Notifications; as [capturas desta integração](../media/realm-notifications/0.1.0/README.md) mostram essa interface. Não houve nova validação gráfica da interface própria deste companheiro. As verificações e galerias das versões anteriores abaixo permanecem históricas.

## Histórico de validação

# Validação — Chronicles RP 0.5.3

**Estado: aprovado.** A [execução de CI 38936862628](https://github.com/ROCKABITS/Mods/actions/runs/38936862628) compilou e verificou esta versão em 7 min 27 s. O código testado é o commit [`9454231e59e679c26f14b21d21c4d7e612507e51`](https://github.com/ROCKABITS/Mods/commit/9454231e59e679c26f14b21d21c4d7e612507e51); os artefactos resultantes foram publicados no commit [`859a5f016fcb5bc2dcc5d1069d2e0281113e6b3a`](https://github.com/ROCKABITS/Mods/commit/859a5f016fcb5bc2dcc5d1069d2e0281113e6b3a).

Ambiente: Minecraft Java 1.21.1, NeoForge 21.1.256 e Java 21. Foram carregados os JARs reais de Origins (NeoForge) 0.3.2, Jupiter 2.3.7 e, nas combinações indicadas, Origins: Classes 0.3. Não foram usados substitutos destas dependências.

## Testes executados

| Verificação | Resultado |
| --- | --- |
| Compilação do JAR de produção e testes JUnit | 66 testes aprovados |
| Servidor nativo com Origins, Jupiter e Classes | 36 GameTests aprovados |
| Servidor nativo com Origins e Jupiter, sem Classes | Os mesmos 36 GameTests aprovados |
| Servidor nativo com Origins, Jupiter, Classes e os cinco mods publicados abaixo | Os mesmos 36 GameTests aprovados |
| Cliente Minecraft com os cinco mods, Origins, Jupiter e Classes | Nomes RP, TAB, chat e livro aprovados; quatro capturas reais |
| Regressão gráfica da criação de personagem e integração Origins | Aprovada com Classes e sem Classes |

São 36 casos nativos executados em três combinações, num total de 108 execuções; não são 108 casos diferentes. Os cinco mods adicionais foram carregados nas versões e com os SHA-256 registados no [manifesto da compilação](../mod-pronto/cronicasrp-0.5.3.build.json):

- Contracts & Bounties 0.3.1.
- Realm Council 0.2.1.
- Crown Ledger 0.2.0.
- Realm Identity 0.1.0.
- Realm Markets 0.2.0.

Os testes unitários cobrem nomes Unicode e nomes inválidos, limites e conteúdo dos novos pacotes públicos, traduções, armazenamento do perfil e as regras existentes de criação, Origins, carteira e dinheiro físico. Os GameTests exercitam os hooks nativos de nome e TAB, a decoração normal do chat, formatação das equipas, hover e inserção, criação, mudanças de perfil, remoção de nome, entrada tardia, saída e nova entrada com o mesmo UUID, clone após morte, mudança de dimensão e acompanhamento de jogadores. Verificam também que o UUID, o username real e os alvos de comandos permanecem intactos e que os pacotes públicos não incluem os dados privados do perfil.

A regressão da economia verifica moedas registadas, troco, depósitos, inventário cheio, pedidos inválidos, limites de saldo e comandos reais. O formato do perfil e as regras económicas não foram alterados por esta atualização. A presença dos outros cinco mods nesta execução verifica o carregamento conjunto e o comportamento do Chronicles RP testado; não certifica todos os fluxos financeiros, políticos ou de documentos desses mods.

## Verificação no cliente real

A execução gráfica utiliza o cliente Minecraft e um servidor integrado reais. A galeria usa entidades remotas de demonstração para mostrar um nome RP e o fallback de uma conta sem personagem, e apresenta os componentes reais da lista TAB. Foram verificados os nomes acima da cabeça, o TAB, o livro próprio, a atualização de um nome já em cache, a remoção de um nome e a limpeza/reposição do registo público pelos pacotes de rede reais.

A mensagem de chat foi enviada pelo caminho normal `sendChat` do cliente, processada pelo servidor integrado e recebida com a identidade RP. O mod não cancela essa mensagem para a reenviar como mensagem de sistema. A sessão de desenvolvimento não possui um certificado autenticado de chat assinado: este teste confirma o transporte e a apresentação do chat, não a validação criptográfica de uma sessão autenticada.

As quatro [capturas desta versão](../media/realm-origins/0.5.3/README.md) e o [resultado da verificação gráfica](../media/realm-origins/0.5.3/validation.json) foram publicados juntamente com o JAR:

- `chronicles-rp-names-world.png` — nomes acima das personagens e fallback para username.
- `chronicles-rp-names-tab.png` — nomes na lista vanilla de jogadores.
- `chronicles-rp-names-chat.png` — nome RP no autor do chat normal.
- `chronicles-rp-names-profile.png` — nome da personagem no livro.

Os testes de ciclo de vida usam jogadores e ligações de teste no servidor nativo. Não foi realizado um ensaio manual com dois clientes autenticados independentes nem um reinício manual do servidor com reconexão desses clientes. A persistência é coberta pelos testes de armazenamento do perfil; a limpeza e a reposição dos nomes públicos são cobertas pelos testes de eventos e pacotes. A renderização gráfica de CI usa software e não certifica todas as GPUs, resource packs ou interfaces de outros mods.

## Artefactos publicados

| Ficheiro | Tamanho | SHA-256 |
| --- | --- | --- |
| [cronicasrp-0.5.3.jar](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/cronicasrp-0.5.3.jar) | 6 273 677 bytes | `2cc100c2572a4338771213e3f38bdded3057fe0ff3c12fa82e79211658340f4e` |
| [cronicasrp-0.5.3.zip](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/cronicasrp-0.5.3.zip) | 6 256 729 bytes | `e854332f2e3383a6011dbb13576f0144b90e6fa7a460f6f1cefe209a4f78ddde` |

O JAR de produção foi preservado antes dos testes e o seu hash foi confirmado após as diferentes configurações de QA. A inspeção da distribuição verifica a versão 0.5.3, o mixin de apresentação de nomes e a respetiva declaração efetiva, as classes públicas de perfil utilizadas pelos mods associados e os créditos/licenças dos recursos incluídos. GameTests, automatismos gráficos, estruturas e origens de teste e dependências externas são excluídos do JAR.

O manifesto regista os IDs exatos dos ficheiros de dependências obtidos por HTTPS e os SHA-256 observados nesses downloads; não os apresenta como checksums publicados pelos autores. Origins, Jupiter e Classes continuam a ser instalados separadamente.

## Atualização e compatibilidade

Faz uma cópia de segurança do mundo e substitui o JAR antigo por `cronicasrp-0.5.3.jar` no servidor e em todos os clientes. O protocolo passa de 6 para **7**: não mistures clientes 0.5.2 com servidor 0.5.3, nem mantenhas dois JARs do Chronicles RP na mesma pasta.

A identidade real da conta mantém o UUID, username, skin e alvos de comandos. O nome RP é público acima da cabeça, no TAB, no autor do chat normal e no livro; perfis sem personagem criada ou com nome antigo inválido usam o username normal. Mods que consultem diretamente o `GameProfile` ou implementem interfaces próprias podem continuar a apresentar o username. Os nomes RP não são identificadores únicos e não substituem o username nos comandos de administração.

Os resultados de 0.5.2 foram preservados separadamente em [VALIDACAO_0.5.2.md](VALIDACAO_0.5.2.md); não são usados como prova de validação desta atualização.
