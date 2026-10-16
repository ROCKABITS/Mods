# Validação — Realm Markets 0.1.0

O projeto está em desenvolvimento. Os totais e os links de uma execução aprovada serão registados aqui **depois** da compilação e dos testes efetivos. A existência do workflow, das capturas de teste ou de uma implementação não constitui aprovação por si só.

## Verificações exigidas para publicar

- Compilação Java 21, JAR de produção e testes JUnit com pelo menos um teste executado, sem falhas, erros ou testes ignorados.
- Testes nativos em Minecraft 1.21.1/NeoForge 21.1.256, primeiro sem outros mods de RP.
- A mesma bateria nativa com os JARs publicados de Contracts & Bounties 0.3.1, Realm Council 0.2.1, Crown Ledger 0.1.1 e Realm Identity 0.1.0, verificados contra os seus SHA-256 conhecidos. Esta execução verifica coexistência; não contém Chronicles RP.
- Cliente Minecraft real, com os modelos e as texturas distribuídos, as duas bancas, as direções de colocação e o menu de produção.
- Três capturas reais do cliente: bancas, gestão do proprietário e compra. Os artigos e saldos dos menus são dados de demonstração.
- Inspeção do JAR: versão, identificador, classes de produção, modelos e receitas presentes; classes e estruturas de teste ausentes.
- Proveniência com commit, execução do CI, totais efetivos, hashes dos JARs usados e checksums dos ficheiros publicados.

O bot publica `mod-pronto/realm-markets-0.1.0.jar`, o ZIP e as imagens apenas depois de todas as etapas aprovadas e se o ramo principal continuar no commit que foi testado.

## Limites

A integração completa de **Chronicles RP 0.5.2, Origins e Jupiter** dentro do Minecraft não está incluída neste CI. Não se redistribuem os JARs originais para simular essa cobertura. Os testes de classes isoladas e a coexistência com os restantes mods não equivalem à execução do conjunto completo.

Uma sessão com dois clientes reais e um reinício manual do mundo constituem validação adicional. Os testes automatizados usam jogadores de teste do servidor e verificações de persistência; as capturas de menus não demonstram transferências entre contas reais do Chronicles.

Os registos do mundo, os inventários e os perfis usam a persistência normal do Minecraft. As verificações no thread principal não criam uma transação de disco única entre ficheiros numa falha abrupta.

## Ambiente cloud

A preparação do ambiente reutiliza o JDK 21, o Gradle e o truststore verificados dos outros projetos. A última tentativa local de descarregar NeoForm 2.0.31 recebeu HTTP 403 no Maven NeoForge antes da compilação Minecraft. Os testes independentes do domínio não precisam desse download; os testes completos devem ser confirmados no CI ou depois de desbloquear o acesso local. Não se desativa TLS nem se alteram checksums para contornar o bloqueio.
