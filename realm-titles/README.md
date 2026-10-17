# Realm Titles — Títulos do Reino

Títulos públicos criados pelo governante e atribuídos na **Mesa do Selo Real**, para Minecraft **1.21.1**, **NeoForge 21.1.256+** e **Java 21**. Versão **0.1.0 Beta**. Instala no servidor e em todos os clientes.

O mod acrescenta uma mesa medieval de dois blocos de altura: madeira escura, detalhes dourados, tecido verde, estandarte com brasão, registo aberto, pena e selo. Abre o menu ao interagir com a mesa. Não existe tecla nem comando de abertura remota.

## O que podes fazer

- Consultar o registo público de habitantes e respetivos títulos.
- Criar títulos como Cavaleiro, Duque, Barão e Conselheiro, com nome e descrição próprios.
- Atribuir vários títulos à mesma personagem, até 16 em simultâneo.
- Escolher validade permanente ou entre 1 e 365 dias reais.
- Retirar uma nomeação e editar as definições existentes. Para eliminar uma definição, retira primeiro as nomeações ativas.
- Pesquisar e percorrer o catálogo e os habitantes. O catálogo admite até 256 títulos.

Os títulos continuam atribuídos quando muda o governante. A mudança de mandato altera quem pode gerir as nomeações. Os títulos não alteram salários, impostos, reputação, atributos de combate, nomes RP nem permissões de outros mods.

## Receita e acesso

Na bancada de trabalho:

| Livro | Pena | Lingote de ouro |
| --- | --- | --- |
| Tábuas | Lã verde | Tábuas |
| Tábuas | Tábuas | Tábuas |

Deixa dois blocos de altura livres sobre uma base sólida. A mesa está também na aba criativa **Realm Titles** e na pesquisa. Um operador pode obter o item com `/titles desk`.

Todos os jogadores podem consultar o registo. Com **Realm Council 0.2.2+**, só o governante do mandato atual, depois de prestar juramento, pode criar e atribuir títulos. Ser operador não contorna esta regra. Mandatos futuros, expirados ou sem juramento não permitem gerir.

Sem Realm Council, um operador designa um responsável com `/titles steward <jogador_online>`. Esse responsável utiliza a mesma mesa. Se o Council estiver instalado mas indisponível, não se ativa este modo alternativo.

As ações exigem uma mesa inteira na mesma dimensão, a uma distância máxima de oito blocos. A sessão dura até cinco minutos; volta a interagir com a mesa para a renovar. As permissões são verificadas em cada alteração.

## Integrações opcionais

**Chronicles RP:** utiliza o nome público e verifica que a personagem foi criada. Sem Chronicles, utiliza o nome Minecraft. Só são guardados UUID, nome público e elegibilidade; não são lidos saldos, assinaturas ou biografias. Habitantes conhecidos podem receber títulos enquanto estão offline. Com o formato atual do Chronicles, a associação usa o UUID da conta, pois não existe um identificador independente de personagem.

**Realm Identity 0.1.1+:** a consulta de um documento autêntico mostra os títulos atuais. Não é necessário emitir outro documento. Uma nova consulta reflete alterações, revogações e expirações; o perfil emitido e o número de série mantêm-se. Atualiza o Identity no servidor e em todos os clientes: esta versão usa protocolo 2.

**Realm Notifications 0.1.0+:** mostra avisos de atribuição, revogação e expiração aos destinatários online. Se o canal não estiver disponível, utiliza o chat. Não existe caixa de avisos offline; o registo de títulos continua disponível na mesa. O autor vê a confirmação no próprio menu, evitando repetir a sua própria nomeação numa notificação.

## Dados e API

O registo global fica em `data/realm_titles.dat` no mundo e conserva definições, nomeações e habitantes conhecidos. As expirações usam tempo real do servidor e são verificadas mesmo para jogadores offline. Um ficheiro existente danificado não é substituído por um registo vazio. Mantém cópias de segurança habituais do mundo.

API pública de leitura, versão 1: `pt.realmtitles.api.TitlesApi.activeTitlesJson(MinecraftServer, UUID)`. Deve ser chamada na thread do servidor; devolve uma lista JSON limitada aos títulos ativos desse UUID. Não permite alterar nomeações.

## Desenvolvimento

```sh
python3 tools/bootstrap_cloud.py
bash tools/run-domain-tests.sh
python3 tools/cloud_gradle.py build --console=plain
python3 tools/cloud_gradle.py -PtitlesGameTests runGameTestServer --console=plain
```

A integração usa `-PintegrationTests`, as versões verificadas dos companheiros em `compat-libs/` e as dependências reais fixadas no Gradle. A captura do cliente usa `-PtitlesVisualTests -PintegrationTests runClient`. Estes fixtures só entram nos source sets quando pedidos e são excluídos dos JARs distribuídos.

Resultados e limites: [VALIDACAO.md](VALIDACAO.md). O workflow conjunto só publica o Realm Titles e o Identity depois das compilações, testes unitários, suites nativas em ambos os modos e captura do cliente real.
