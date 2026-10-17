# Validação — Realm Identity 0.1.1

[Execução conjunta aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39029117475), código `a9de43fdc55325ff9f3aaedaf4ffc61608b8dc16`.

- **35 testes unitários aprovados**, sem falhas/erros/ignorados; a mesma bateria passou no auxiliar local Java 21.
- **23 GameTests aprovados em modo independente** e **23 com Realm Titles 0.1.0 e os sete companheiros publicados**, incluindo Chronicles RP 0.5.4 com Origins/Jupiter/Classes reais. Os três casos novos verificam ausência do Titles no modo independente e a API real no modo integrado.
- Novas provas: atribuição e revogação sem reemitir o documento; titulares offline, expiração e descrição atual; API opcional indisponível afeta só os títulos; documento falso não consulta o registo. Item, selo, número de série e perfil emitido mantêm-se.
- O cliente real do Realm Titles emitiu e consultou um documento autêntico, mostrou dois títulos e confirmou apenas um após revogação. Controlos verificados em 320 × 240. Duas capturas desse percurso fazem parte da [galeria conjunta](../media/realm-titles/README.md); não foi repetida nesta atualização a galeria independente de quatro demonstrações do Identity.
- Protocolo **2**: instala a 0.1.1 no servidor e em todos os clientes. Os documentos da 0.1.0 continuam válidos.
- JAR/ZIP publicados e o JAR dentro do pacote conjunto foram novamente verificados por SHA-256. O JAR não inclui os fixtures nativos/visuais nem bibliotecas externas.

SHA-256 do JAR: `4096147ac74847d31ee733291ad85d8d154ff7340e8d6f0874dd7cb7364823b0`.

[JAR](../mod-pronto/realm-identity-0.1.1.jar) · [ZIP](../mod-pronto/realm-identity-0.1.1.zip) · [Proveniência](../mod-pronto/realm-identity-0.1.1.build.json) · [Âmbito completo e limites](../realm-titles/VALIDACAO.md).

O cliente visual foi integrado, com personagens de demonstração, e não comprova uma sessão autenticada de dois clientes. A compilação Minecraft local permaneceu bloqueada por HTTP 403 no download do NeoForm; as provas nativas e visuais foram executadas no GitHub Actions.

---

# Histórico — Realm Identity 0.1.0

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/38812039209), código `e0a1d3ee8b65d89a39d1b4d0136b8cf89d0890af`.

- **28 testes unitários aprovados**, zero falhas/erros/ignorados: 8 de registo de documentos, 7 de perfis/Unicode, 8 de assinaturas bitmap e 5 de compatibilidade do leitor de perfis. A mesma bateria independente passou nesta máquina com o JDK 21 completo.
- **20 testes nativos aprovados** em Minecraft 1.21.1/NeoForge 21.1.256, sem outros mods de RP.
- **20 testes nativos aprovados** com os JARs publicados de Contracts 0.3.1, Realm Council 0.2.1 e Crown Ledger 0.1.1 carregados. Esta execução verifica coexistência; não contém o Chronicles.
- Testes nativos cobrem receita/criativo, colocação e interação pelo bloco, papel/tinta/inventário principal, rejeição sem espaço, segunda via, NBT falsificado, cópia de outro mundo, documento em branco, nomes/lore alterados, consulta do portador, titulares offline, convite pelo clique real no jogador, destinatário, uso único, distância, linha de vista, dimensão, vida, espectador, expiração e documento ainda na mão.
- O cliente Minecraft real validou a escrivaninha com **276 faces**, o documento com **31 faces**, as quatro direções e os sprites sem texturas em falta.
- A interface real foi exercitada: emissão/segunda via/atualização enviam apenas pedidos vazios; não existem campos editáveis de personagem; consulta é só de leitura; estados inválidos escondem identidades; desenho ink1 é descodificado e renderizado pelo código de produção; os controlos cabem em 320 × 240.
- Três capturas reais de **1600 × 900**, com dados de demonstração e uma assinatura de exemplo, foram inspecionadas.
- JAR e ZIP públicos verificados contra os checksums do CI. O JAR não inclui os testes nativos, o NBT de teste nem o capturador de imagens.

[JAR](../mod-pronto/realm-identity-0.1.0.jar) · [ZIP](../mod-pronto/realm-identity-0.1.0.zip) · [Proveniência](../mod-pronto/realm-identity-0.1.0.build.json) · [Imagens](../media/realm-identity/0.1.0/README.md).

SHA-256 do JAR: `9332d133320066d42bedb08fe129dee3da0de41f261f459140dea2a12ebf1606`.

## Chronicles e limites da 0.1.0

Foi executada separadamente uma verificação do leitor contra os records do **JAR original Chronicles RP 0.5.2** fornecido pelo utilizador. Confirmou os dados de identidade, profissão, assinatura escrita, bits do desenho, rejeição de personagem não criada/assinatura inválida e preservação de biografia, origem, saldo e progresso. O programa de verificação próprio está em `tools/OriginalChroniclesCheck.java`; o JAR original não é redistribuído.

Esta verificação não iniciou **Chronicles/Origins/Jupiter** dentro do Minecraft nem validou a inicialização dos seus attachments em conjunto. Os ramos condicionais dos GameTests que exigem o Chronicles real não foram executados. Testar o conjunto original e dois clientes reais continua pendente antes de tratar a integração completa como validada. As capturas não são um teste de perfis reais do Chronicles.

O registo e o inventário usam a persistência normal do mundo. A verificação no thread principal não equivale a uma transação de disco entre ficheiros durante uma falha abrupta.

## Ambiente cloud

A obtenção local de NeoForm 2.0.31 continua a devolver HTTP 403 no Maven NeoForge, antes da compilação Minecraft. A compilação Java completa e os testes nativos/visuais acima correram no GitHub Actions. Não foi desativada a verificação TLS nem alterado qualquer checksum.
