# Realm Origins — 0.5.7

**Realm Origins** é o novo nome do Chronicles RP. A criação mantém o visual cinematográfico escuro e lilás, com quatro capítulos, skin em 3D, assinatura e boas-vindas. Depois de criar a personagem, **P abre novamente o livro original de pergaminho**, com Ficha, Feitos, História e Bolsa. O câmbio usa também o livro. A criação e as boas-vindas conservam o visual novo.

A ocupação passa a ser escrita livremente: usa uma palavra de até 24 caracteres, com acentos e hífen permitidos, como Alquimista ou Guarda-real. Os botões Ferreiro, Mercador e Bardo são sugestões que preenchem o campo. A regra é validada no servidor, tanto na criação como na edição. As profissões antigas mantêm-se.

Os restantes dados e regras mantêm-se. A confirmação mostra os fundos já guardados, sem sortear ou pagar dinheiro novamente.

O identificador interno continua a ser `cronicasrp`, tal como os itens, dados, configuração e pacote Java. O protocolo passa a **8**, porque clientes antigos só reconheciam a lista fixa de ocupações. Atualiza servidor e todos os clientes para 0.5.7. Isto preserva os perfis, carteiras e integrações existentes. A pasta do projeto mantém o nome `chronicles-rp/`. O novo JAR **substitui** o Chronicles RP; não é um segundo mod para instalar ao lado dele.

Personagens de fantasia e economia para Minecraft Java 1.21.1, NeoForge 21.1.256+ na série 21.1 e Java 21.

## Instalar

Faz uma cópia de segurança do mundo e coloca `realm-origins-0.5.7.jar` em `mods`, substituindo o JAR anterior do Chronicles RP. Instala a versão 0.5.7 no servidor e em todos os clientes; não deixes o JAR antigo e o novo Realm Origins na mesma pasta.

| Mod | Versão usada nesta atualização | Necessário? |
| --- | --- | --- |
| [Origins (NeoForge), de IAFEnvoy](https://www.curseforge.com/minecraft/mc-mods/origins-neoforge/files/8758523) | 0.3.2 | Sim |
| [Jupiter para NeoForge 1.21.1](https://www.curseforge.com/minecraft/mc-mods/jupiter/files/7738312) | 2.3.7 | Sim, biblioteca do Origins |
| [Origins: Classes (NeoForge), de IAFEnvoy](https://www.curseforge.com/minecraft/mc-mods/origins-classes-neoforge/files/8758587) | 0.3 | Opcional; instala-o para ter classes |

Esta integração usa a API da versão NeoForge de IAFEnvoy. O JAR declara Origins 0.3.2 como obrigatório e Origins: Classes 0.3 como opcional. Os downloads acima correspondem à combinação de referência; o estado das verificações desta versão está em `VALIDACAO.md`. Não precisas de instalar os JARs externos dentro do projeto IntelliJ: o Gradle carrega-os nas execuções de desenvolvimento.

A [validação da versão 0.5.3 passou](https://github.com/ROCKABITS/Mods/actions/runs/38936862628): 66 testes unitários, 36 GameTests em cada uma de três combinações de mods e verificações no cliente real. Consulta [VALIDACAO.md](VALIDACAO.md) para os artefactos, o âmbito e os limites destes testes.

## Nomes de personagem no mundo

Depois de criares a personagem, o nome RP substitui o username acima da cabeça, na lista TAB, no nome do autor das mensagens do chat normal e na identificação apresentada pelo diário do Realm Origins. Por exemplo, uma personagem chamada **Aetherion** é apresentada como **Aetherion**, mesmo que a conta Minecraft se chame **Ricardo123**. Sem personagem criada, ou se um perfil antigo tiver um nome inválido, aparece o username habitual.

A sincronização usa apenas o UUID e o nome público da personagem; não transmite histórias, assinaturas, saldo ou os restantes dados privados aos outros jogadores. O nome é atualizado quando o perfil muda e é enviado a quem entra ou começa a acompanhar o jogador. As regras de visibilidade das equipas e a formatação das equipas continuam a aplicar-se. No chat, a identificação ao passar o rato e o texto inserido com Shift+clique usam também o nome RP; o clique para enviar uma mensagem privada conserva o alvo real da conta.

Esta é uma substituição visual: o UUID e o `GameProfile` originais mantêm a identidade da conta. Os nomes RP podem repetir-se; usa o username verdadeiro nos comandos que pedem um jogador e na administração. Mods externos que consultem diretamente o username podem continuar a mostrá-lo. Esta funcionalidade não cria títulos, não altera a skin e não requer o futuro Realm Titles.

## Compatibilidade mantida da versão 0.5.1

Mantém-se a correção da definição de claustrofobia do Elytrian fornecida pelo Origins 0.3.2, para que os efeitos de fraqueza e lentidão sejam carregados. A correção é um recurso incluído no mod; não precisas de editar ficheiros nem instalar outro datapack. Os valores e a condição do poder são os do Origins. Consulta `CHANGELOG_0.5.1_PT.md` e `VALIDACAO.md`.

## Criar e consultar uma personagem

1. Na primeira entrada, abre primeiro a criação por capítulos. Podes começar pelo nome e pela idade.
2. Carrega no botão **Linhagem**: abre a escolha de raça do Origins.
3. Depois da raça, escolhe a classe se Origins: Classes estiver instalado. Sem o add-on, este passo é dispensado.
4. Regressas automaticamente ao mesmo formulário, com nome e idade conservados. Completa profissão, história e assinatura desenhada e confirma o juramento. O primeiro passo só avança depois de terminares as escolhas do Origins.
5. O servidor atribui a fortuna inicial uma única vez. A animação mostra esse resultado já guardado.
6. Carrega em **P** para consultar a ficha. **Origins** abre a consulta de linhagem, classe e poderes. **Voltar** regressa ao livro ou ao rascunho. Depois das escolhas, o botão da linhagem também permite consultar esses dados.

A linhagem já não é uma lista fixa de Humano, Elfo, Anão e Orc. Vem da origem selecionada no Origins e acompanha mudanças feitas pelo Orb of Origin ou pela administração. As origens personalizadas do servidor também aparecem; não é necessário alterar o código do Chronicles RP para cada raça. Os datapacks têm de ser compatíveis com este port do Origins: o formato é diferente do port Fabric.

A classe do Origins e a profissão de RP são campos separados. Por exemplo, podes ter a classe Warrior e ser Ferreiro no RP. A classe e os poderes são geridos pelo Origins. A profissão continua a ser escolhida no pergaminho e em Editar memórias. A origem económica — Humilde, Nobre, etc. — também permanece independente.

Sem Origins: Classes, o mod continua a funcionar com Origins; a ficha mostra que o add-on de classes está ausente. Se o servidor tiver Classes, instala-o também nos clientes.

## Personagens existentes

Ao atualizar de 0.4.1, preserva a pasta do mundo e troca os JARs. Nome, idade, história, desenho da assinatura, profissão, reputação, missões, medalhas, fortuna inicial e saldo são mantidos. A raça passa a acompanhar a origem que escolheres no Origins. Escolher uma origem ou classe não volta a sortear nem atribui dinheiro.

O protocolo do Realm Origins passa a **8** na versão 0.5.7. Atualiza o servidor e todos os clientes; as versões 0.5.6 e anteriores não são compatíveis com este canal. O formato do perfil de RP mantém os campos anteriores; a linhagem guarda o identificador da origem. A escolha da classe fica nos dados do próprio Origins. Esta atualização não reinicia as personagens, não volta a sortear a fortuna e não converte o dinheiro.

## Livro e economia

O livro mantém as páginas Ficha, Feitos, História e Bolsa, o retrato com a skin atual, as estatísticas reais do Minecraft e a assinatura desenhada. Nome e idade são definidos na criação; história, profissão e assinatura podem ser editadas. A raça segue o Origins.

Vida, armadura, experiência, coordenadas, tempo de jogo e abates vêm do Minecraft. Reputação, missões e medalhas são dados de RP atribuídos por operadores. A reputação determina a classificação, de Carvão a Netherite. O Contracts & Bounties pode atribuir progresso e recompensas através das suas quests.

Em **P → Bolsa → Moedas, notas e câmbio**, converte saldo digital em dinheiro físico ou deposita-o de volta com troco exato.

| Peças | Valores |
| --- | --- |
| Moedas de cêntimos | 1, 2, 5, 10, 20 e 50 |
| Moedas de euros | 1 € e 2 € |
| Notas | 5, 10, 20, 50, 100, 200 e 500 € |

Os valores usam cêntimos inteiros. Não há taxas nem receitas de sobrevivência para fabricar dinheiro. Se faltarem fundos ou espaço, a operação é rejeitada inteira. O total físico conta as 36 posições do inventário e a mão secundária. Baús e mochilas externas não entram nessa soma. O saldo digital permanece ao morrer; o físico segue as regras normais do inventário.

Comandos principais: `/rp ficha`, `/rp saldo`, `/rp destino`, `/rp cambio`, `/rp levantar 12,50`, `/rp depositar 7,25`, `/rp depositar tudo`, `/rp pagar Jogador 12,50`. Os aliases ingleses continuam disponíveis. Administração, com nível de operador 2: `/rp admin dar`, `reputacao`, `missao` e `medalha`.

Menus, moedas e mensagens do Chronicles RP seguem o idioma do Minecraft: inglês, português de Portugal e português do Brasil. Os nomes das origens/classes usam as traduções fornecidas pelo Origins, pelos add-ons e pelos resource packs. Nomes, histórias e assinaturas escritos pelo jogador permanecem como foram escritos.

## Desenvolvimento

Abre no IntelliJ a pasta `chronicles-rp`, que contém `build.gradle` e `gradlew.bat`. Usa JDK 21 tanto no projeto como no Gradle JVM. A primeira execução necessita de internet.

```powershell
.\gradlew.bat runClient
.\gradlew.bat test
.\gradlew.bat runGameTestServer -PrpGameTests
.\gradlew.bat clean build
```

Para testar apenas com Origins, sem Classes, acrescenta `-PrpWithoutClasses` às execuções. O JAR final está em `build/libs/cronicasrp-0.5.4.jar`. Os GameTests e as suas origens de teste são excluídos do JAR, incluindo após uma compilação incremental.

Se já tens um projeto IntelliJ, substitui `build.gradle`, `gradle.properties` e a pasta `src` pelos desta versão. Mantém a tua pasta `run`, que contém mundos e configurações. No repositório, o projeto está em `chronicles-rp`; os ZIPs de instalação contêm o JAR pronto e não são necessários para importar o projeto.

As configurações de fortuna inicial continuam em `cronicasrp-server.toml`. Os dados de moedas, saldos e RP das versões anteriores mantêm a migração existente. Mundos convertidos de 0.3.0 ou anterior devem usar uma cópia anterior do mundo para regressar a essas versões.

A fonte e os assets mantêm as licenças e créditos incluídos no projeto. As dependências externas são descarregadas separadamente e não estão incorporadas no JAR. Consulta `README_EN.md`, `CHANGELOG.md` e `VALIDACAO.md`.
