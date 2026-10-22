> **0.1.2:** integração opcional com Realm Marriage: estado civil e cônjuge consultados no servidor, respeitando a privacidade do titular. Usa uma certidão com Shift para validar os nomes, celebrante, testemunhas, data e regime. Documentos de identidade existentes continuam válidos, sem segunda via; protocolo 2 preservado.

# Realm Identity — Documento de Personagem

Documentos físicos de roleplay para **Minecraft 1.21.1**, **NeoForge 21.1.256+** e **Java 21**. Instala `realm-identity-0.1.1.jar` no servidor e em todos os clientes. Identificador: `realmidentity`.

## Criar o documento

Fabrica uma **Escrivaninha de Identidade** e coloca-a no mundo. Também aparece na aba criativa **Realm Identity**.

```text
Papel       Pena        Saco de tinta
Livro       Tábuas      Livro
Tábuas      Tábuas      Tábuas
```

Clica na escrivaninha com o botão direito. A interface mostra os dados e permite emitir o documento por **1 papel + 1 saco de tinta**, retirados do inventário principal. É preciso espaço para o documento; se não couber ou faltarem materiais, nada é consumido e o documento anterior mantém-se válido.

Com **Realm Origins 0.5.7**, lê a personagem já criada: nome, idade, raça, profissão, assinatura escrita e desenho da assinatura. O adaptador é apenas de leitura e não altera a personagem, o dinheiro, a reputação ou o progresso. Se o Realm Origins estiver instalado e não houver personagem válida, a emissão fica bloqueada.

Sem Realm Origins, funciona com o nome e UUID reais da conta Minecraft. Os dados de RP e a assinatura ficam por preencher; não inventa uma personagem nem permite editar esses campos no menu.

## Consultar e mostrar

- **Botão direito no ar com o documento na mão:** abre a consulta do documento físico.
- **Botão direito noutro jogador com o teu documento:** envia-lhe um convite no chat. O destinatário escolhe **Ver documento**; o menu não abre automaticamente.
- O convite dura **30 segundos**. Ao aceitar, os dois jogadores têm de continuar vivos, próximos, na mesma dimensão, com linha de vista, e o titular tem de continuar a segurar o documento válido.
- É possível ler um documento emprestado, com indicação de que o portador não é o titular. Só o titular pode apresentá-lo como seu a outro jogador.

A consulta distingue a autenticidade da atualidade dos dados. O documento guarda os dados **à data da emissão**. Se a personagem online tiver mudado, mostra-os como desatualizados; com o titular offline, mantém a verificação do registo de emissão e informa que não confirmou o perfil atual.

## Títulos e honras públicas

Com **Realm Titles 0.1.0** instalado, a consulta do documento e a pré-visualização na escrivaninha mostram os títulos públicos atuais do titular: nome, descrição e validade permanente ou data de expiração. A secção tem a sua própria data de consulta, em UTC, e admite até 16 títulos com texto completo e deslocação vertical.

Os títulos são lidos novamente no servidor a cada nova consulta. Atribuir, editar, revogar ou deixar expirar uma honra atualiza a próxima abertura do **mesmo documento**, sem segunda via, sem alterar o número de série nem consumir materiais. Um título temporário também desaparece do menu aberto quando atinge a expiração, calculada a partir da hora de consulta do servidor e do tempo decorrido no cliente. Para receber novas atribuições ou revogações durante uma consulta, fecha e reabre o documento; a escrivaninha atualiza a pré-visualização automaticamente.

As honras funcionam com o titular offline. Se o Realm Titles estiver indisponível ou tiver uma API incompatível, apenas a secção das honras mostra indisponibilidade; o documento autenticado continua legível. Sem Realm Titles, a consulta mantém o funcionamento anterior.

O adaptador opcional usa apenas `pt.realmtitles.api.TitlesApi.VERSION = 1` e `activeTitlesJson(MinecraftServer, UUID)`, depois da autenticação do documento e das verificações de apresentação e consentimento. Não consulta biografias nem escreve perfis. O UUID identifica a conta/personagem neste conjunto de mods; não existe um identificador independente por personagem. Os títulos nunca são gravados no item, no perfil emitido nem no registo `realm_identity.dat`.

## Segunda via e autenticidade

Cada jogador tem **um documento ativo por mundo**, com identificador aleatório e número de série. Pedir uma segunda via na escrivaninha custa novamente papel e tinta e invalida o documento anterior e todas as suas cópias. Serve também para atualizar os dados depois de alterar a personagem no Realm Origins.

O servidor verifica o documento no seu registo. Alterar o nome, a descrição ou dados personalizados do item não altera a identidade que o menu apresenta. Um documento sem registo, de outro mundo ou substituído por uma segunda via aparece como inválido.

O item de documento disponível em criativo ou por `/give` não tem emissão válida. A escrivaninha é a forma normal de obter o documento autenticado. Não existem atalhos de teclado para emitir ou abrir documentos à distância.

## Comandos

- `/identity desk`: dá uma escrivaninha a um operador; não abre o menu.
- `/identity inspect <token>`: é o comando do convite clicável, restrito ao destinatário e à apresentação física válida. Não permite procurar documentos por UUID ou abrir registos à distância.

Os jogadores comuns podem fabricar a escrivaninha, emitir os seus documentos, consultá-los e aceitar convites sem permissões de administrador.

## Compatibilidade e dados

**Atualização 0.1.1:** instala a mesma versão do Realm Identity no servidor e em todos os clientes. O protocolo de rede passou para a versão 2; clientes 0.1.0 têm de ser atualizados. Documentos já emitidos continuam válidos e os dados do mundo não precisam de migração. Realm Titles é opcional e não é necessário para emitir ou ler documentos.

Realm Origins 0.5.7 é opcional; o intervalo declarado é `>=0.5.2 e <0.6`. Se instalado, requer também as dependências do próprio Realm Origins. Contracts, Realm Council e Crown Ledger não são necessários para usar o Realm Identity.

Todas as escrivaninhas e dimensões usam `data/realm_identity.dat`, que guarda o selo do mundo, o próximo número de série e um documento atual por jogador. Inclui este ficheiro nas cópias de segurança do mundo. Se o registo estiver corrompido, o mod recusa substituí-lo silenciosamente por um registo vazio. A emissão verifica inventário e registo no thread do servidor e usa o mecanismo normal de persistência do Minecraft; não oferece uma transação de disco única durante uma falha abrupta do processo.

[Resultados e limites da validação](VALIDACAO.md) · [Changelog](CHANGELOG.md) · [JAR pronto](../mod-pronto/realm-identity-0.1.1.jar) · [ZIP](../mod-pronto/realm-identity-0.1.1.zip) · [Imagens](../media/realm-identity/0.1.0/README.md).

## Desenvolvimento

```bash
./gradlew build
./gradlew -PidentityGameTests runGameTestServer
# Compatibilidade com os JARs publicados em compat-libs/:
./gradlew -PidentityGameTests -PintegrationTests runGameTestServer
./gradlew -PidentityVisualTests runClient
```

Na máquina cloud usa `python3 tools/cloud_gradle.py` em vez de `./gradlew`, mantendo o proxy e a verificação TLS. O domínio e o leitor de perfis podem ser testados sem Minecraft com `python3 tools/test_domain.py`.

Os testes nativos e visuais são excluídos do JAR de produção. A publicação automatizada exige compilação, testes unitários, servidor independente, compatibilidade com os JARs publicados de Contracts/Realm Council/Crown Ledger e cliente visual aprovados. As capturas da interface usam dados de demonstração e uma assinatura de exemplo. A verificação isolada dos records do JAR original de Realm Origins não equivale a iniciar o conjunto completo de Realm Origins/Origins/Jupiter dentro do Minecraft.
