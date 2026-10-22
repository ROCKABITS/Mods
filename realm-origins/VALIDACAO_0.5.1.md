# Validação — Chronicles RP 0.5.1

Ambiente: Minecraft Java 1.21.1, NeoForge 21.1.256, Java 21, Origins (NeoForge) 0.3.2 e Jupiter 2.3.7. Origins: Classes 0.3 presente e ausente.

## Verificação desta atualização

- Reproduzida a falha da versão anterior: o Origins não descodificava as entradas de fraqueza/lentidão de claustrofobia e substituía o poder por `EmptyPower`. Os dois novos testes falharam nesse cenário; os outros 14 passaram.
- Com a correção, passaram os 16 GameTests nativos com Classes e os mesmos 16 sem Classes. A mensagem `Failed to decode origins:power_type` desapareceu nas duas execuções.
- O poder foi lido do registo real do Origins, com ambos os efeitos, os ícones visíveis e as partículas ocultas. Os valores e a condição originais foram conservados.
- Um Elytrian de teste recebeu fraqueza e lentidão através do código nativo do Origins sob um teto baixo. Em espaço aberto os efeitos não foram aplicados; depois de retirar o teto e limpar os efeitos, não foram reaplicados.
- 56 testes JUnit sem falhas, erros ou testes ignorados: perfil, assinatura, RP, economia, armazenamento, traduções e pacotes. A compilação final `clean build` terminou com sucesso e reutilizou os resultados válidos da cache do Gradle.
- O JAR foi aberto e todas as entradas verificadas. Contém o recurso corrigido, os créditos e a licença do recurso. Exclui classes de QA, origens de teste e dependências externas.
- As 61 classes de produção têm os mesmos bytes da versão 0.5.0. Esta atualização altera o recurso de compatibilidade e a versão, sem alterar o código da interface ou os dados guardados.

## Âmbito

Não foi repetida a sessão gráfica completa nesta correção de dados. As capturas em `preview` e a validação gráfica em `VALIDACAO_0.5.0.md` correspondem à versão 0.5.0. Os testes desta atualização cobrem especificamente a claustrofobia e os cenários de integração/economia existentes; não certificam todas as habilidades do Origins nem de outros add-ons.

A correção usa o formato do port NeoForge de IAFEnvoy 0.3.2, que continua fixado nas dependências. O protocolo continua na versão 6. O recurso é carregado automaticamente junto com o mod.

JAR de distribuição: `cronicasrp-0.5.1.jar`.

SHA-256: `b27d71f2b0215c7e3abc90b96c692d8ea6412ec10cc48fd4c9dc63a230b5b3df`.

Resumo verificável: `qa-results/0.5.1-checks.json`.
