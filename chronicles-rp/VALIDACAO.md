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

As quatro [capturas desta versão](../media/chronicles-rp/0.5.3/README.md) e o [resultado da verificação gráfica](../media/chronicles-rp/0.5.3/validation.json) foram publicados juntamente com o JAR:

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
