# Validação do Realm Titles 0.1.0

A lógica do registo passou nos 48 testes unitários locais, sem testes ignorados. A integração de leitura do Realm Identity passou nos 35 testes unitários desse projeto.

A compilação Minecraft local foi tentada com o wrapper e a distribuição Gradle verificados, Java 21, proxy e certificados do ambiente. O download do NeoForm Runtime 2.0.31 foi recusado com HTTP 403 antes da compilação Java. O workflow `build-realm-titles.yml` efetua a compilação e validação nativa num runner com acesso às dependências.

A publicação conjunta exige os testes unitários completos, as duas suites GameTest em modo independente e com os companheiros reais, um cliente Minecraft real com capturas dos quatro sentidos da mesa e menus, e a inspeção de um documento autêntico com títulos antes e depois de revogação. Os ficheiros `.build.json` publicados registam os resultados efetivos, o commit de origem, o run de testes e os checksums.

Os fixtures usam personagens e nomeações de demonstração num mundo integrado. Não equivalem a uma sessão multiplayer autenticada. O modelo e as imagens do cliente são recursos originais; nenhuma imagem promocional gerada substitui as capturas do Minecraft.
