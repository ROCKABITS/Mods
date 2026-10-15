# Conselho do Reino / Realm Council — 0.2.1

Mod separado de eleições para Minecraft Java 1.21.1, NeoForge 21.1.256 ou superior da série 21.1 e Java 21. Instala `realmcouncil-0.2.1.jar` no servidor e em todos os clientes. A versão 0.2.1 exige a atualização dos dois lados.

Funciona sozinho. Se Crónicas RP 0.4.0+ também estiver instalado, lê o nome, raça e idade da personagem para as candidaturas. Usa o nome Minecraft quando não há personagem criada. Não altera o perfil, a assinatura nem o dinheiro do Crónicas RP. Os nomes e propostas são guardados nas candidaturas e continuam disponíveis se a integração deixar de ser usada.

## Começar no servidor, pelos menus

1. Instala o JAR no servidor e em todos os clientes. Ao atualizar, substitui o JAR antigo pelo novo. Os mundos e dados eleitorais da versão 0.1.0 continuam a ser utilizados.
2. Obtém um quadro eleitoral pela aba criativa ou pela receita, coloca-o e interage com ele. Como operador, abre **Gestão** e carrega em **Receber os blocos**. Recebes um quadro eleitoral, uma urna e uma mesa de juramento. Se o inventário estiver cheio, os blocos caem aos teus pés. Os três blocos estão disponíveis na aba criativa e pelas receitas.
3. Coloca os três blocos numa praça. Todos consultam a mesma eleição global; destruir um bloco não apaga os votos.
4. Em **Gestão → Resumo → Criar eleição**, escreve o cargo e define o tempo mínimo de jogo, participação mínima, máximo de candidatos e máximo de voltas. A página **Prazos** permite escolher a duração das candidaturas, campanha, votação, mandato e desempates, em minutos reais. **Rever e criar** mostra os valores; **Confirmar e selar** cria a eleição.
5. Em **Gestão → Eleitores**, pesquisa nomes Minecraft e inscreve ou retira jogadores durante as candidaturas. São incluídos jogadores offline que já tenham entrado desde a instalação. Retirar um eleitor retira também a sua candidatura, com confirmação. Em **Candidaturas**, podes retirar apenas a candidatura, mantendo o direito de votar, durante as candidaturas ou a campanha.
6. Para testar sem esperar pelos prazos, usa **Iniciar campanha**, **Abrir votação** e **Encerrar votação** no Resumo. Cada ação pede confirmação. **Cancelar eleição** encerra uma eleição em curso; **Terminar mandato** encerra o mandato ativo.

A página Gestão só aparece para operadores com nível de permissão 2. O servidor verifica a permissão em cada pedido, mesmo quando o menu já está aberto. As confirmações guardam a eleição, volta e fase; pedidos antigos não avançam uma fase nova por engano. Um governante eleito só recebe os poderes RP do cargo.

A lista eleitoral automática usa o tempo mínimo escolhido na criação. Por defeito são 120 minutos de jogo neste mundo. O jogador deve ter entrado pelo menos uma vez desde a instalação. As alterações manuais à lista ficam fechadas ao começar a campanha.

## Percurso do jogador

Interage com um quadro eleitoral para consultar o livro. As páginas são Eleição, Candidatos, Resultados, Governo e Histórico. Não existe atalho na tecla **O**, nem abertura pública por `/council` ou `/eleicoes`.

Cada leitura, atualização e ação do menu exige que continues na mesma dimensão, a um máximo de oito blocos do bloco com que interagiste, e que esse bloco ainda exista. Se te afastares, mudares de dimensão ou o bloco for destruído, o pedido é recusado e o menu fecha na próxima atualização. As sessões expiram ao fim de dez minutos; volta a interagir com o bloco para abrir uma nova sessão.

Na primeira fase, um eleitor pode apresentar uma candidatura, com lema, símbolo e propostas. A sua skin aparece ao lado do formulário. Um candidato já inscrito pode editar as propostas ou desistir durante a campanha. Novas inscrições acabam ao terminar a fase de candidaturas. Ao começar a votação, as candidaturas ficam fixas.

Para votar, interage com uma urna, escolhe um candidato ou voto em branco e confirma com o selo. O servidor verifica que continuas na mesma dimensão e a uma distância máxima de oito blocos da urna. Cada UUID pode votar uma vez por volta. A escolha é definitiva nessa volta. Sair, morrer ou recriar a personagem não dá outro voto.

As contagens são publicadas quando termina cada volta. Até lá, só a participação é pública. O mais votado vence. Empatados vão a nova votação; os votos são reiniciados apenas nessa nova volta. Após o máximo de voltas configurado, um empate persistente termina sem eleito. Falta de participação mínima, ausência de candidatos ou apenas votos em branco também podem terminar sem eleito.

O vencedor deve interagir com uma mesa de juramento para tomar posse. Durante o mandato, pode publicar até vinte decisões, com até 500 caracteres, através da página Governo. Todos podem ler as decisões completas. O cargo não concede OP, permissões de administração, impostos nem acesso automático a dinheiro. O mandato começa ao apurar o vencedor; o juramento deve ser feito antes do prazo terminar.

## Prazos e configuração

Os valores do ficheiro `world/serverconfig/realmcouncil-server.toml` preenchem o formulário de criação. Podes escolher todas as regras e prazos no próprio menu para cada eleição. São minutos reais. Para mudar os valores iniciais do formulário, fecha o servidor antes de editar o ficheiro.

| Opção | Valor inicial |
|---|---:|
| `minimumPlayMinutes` | 120 |
| `nominationMinutes` | 1440 — 1 dia |
| `campaignMinutes` | 2880 — 2 dias |
| `votingMinutes` | 2880 — 2 dias |
| `termMinutes` | 20160 — 14 dias |
| `runoffMinutes` | 1440 — 1 dia |
| `minimumVotes` | 1 |
| `maximumCandidates` | 12 |
| `maximumRounds` | 3 |

As durações e limites escolhidos no formulário são copiados ao criar a eleição. Alterações nos valores iniciais aplicam-se aos formulários seguintes. A participação mínima inclui votos em branco, mas um candidato tem de receber pelo menos um voto para vencer.

Os prazos usam o relógio real. Ao reabrir um servidor que esteve desligado, as fases vencidas avançam. Um desempate começa com uma nova janela completa de votação; um mandato começa quando o vencedor é apurado pelo servidor.

## Comandos opcionais

Todas as ações de organização descritas acima estão disponíveis na página Gestão. Os comandos continuam disponíveis para administração pela consola e compatibilidade:

- `/council admin create Nome do cargo`
- `/council admin enrol NomeMinecraft` — durante candidaturas
- `/council admin remove NomeMinecraft` — retira uma candidatura antes de votar
- `/council admin advance` — avança a fase ou apura a volta atual
- `/council admin cancel` — cancela uma eleição em curso
- `/council admin endterm` — termina o mandato atual
- `/council admin status`

A página Gestão e os comandos administrativos exigem nível de permissão 2. Os comandos `/council admin …` e `/eleicoes admin …` continuam disponíveis à distância e pela consola. Consultar o quadro, candidatar-se e participar não exige OP; os menus exigem o respetivo bloco próximo. Uma eleição nova exige que a anterior tenha acabado e que não exista um mandato ativo. Novas eleições são convocadas pelo operador; não são criadas automaticamente.

Os votos são secretos para os outros jogadores e não aparecem nos menus de administração. O dono dos ficheiros do mundo pode ler os dados guardados. Uma conta por UUID não elimina contas alternativas; a lista eleitoral e o requisito de tempo ajudam a limitar essa prática.

## Idiomas e dados

Português e inglês seguem a opção Language do Minecraft. English (US) ativa inglês. O título do cargo, os nomes, lemas e propostas escritos pelos jogadores mantêm o texto original.

Os dados eleitorais ficam em `world/data/realmcouncil_elections.dat`, separados dos dados do Crónicas RP. Os votos são guardados após cada ação confirmada. O histórico mantém vinte eleições anteriores e o livro mostra as dez mais recentes, além da eleição atual.

## Desenvolvimento

No Windows, usa Java 21 e `gradlew.bat runClient`; em Linux/macOS, `./gradlew runClient`. O projeto carrega o código diretamente; o JAR não deve ser copiado para `run/mods` durante o desenvolvimento.

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer -PrpGameTests
.\gradlew.bat clean build
```

Para testar a integração, coloca apenas o JAR do Crónicas RP em `run/mods` e repete os GameTests. O teste do adaptador cobre tanto a ausência do mod como um perfil RP real quando presente.

A prévia nativa opcional é `runClient -PrpClientPreview`: abre interfaces com dados de teste, guarda capturas e termina. Esta prévia não faz parte do JAR de distribuição. Compila a versão final sem propriedades de teste e com `clean`.
