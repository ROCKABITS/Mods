# Validação — Realm Economy 0.4.1 Beta

[Execução aprovada no GitHub](https://github.com/ROCKABITS/Mods/actions/runs/40098516877), código `ffaecad5d142b7315c95496e46078c9ccca69005`. Passaram 21 testes JUnit, 62 testes nativos independentes, 62 com integrações e o cliente Minecraft real. O JAR publicado tem 156 897 bytes e SHA-256 `505412288af4ec079a47a87350a019aaa1b348052037796b3831c8d992f4cff3`, igual ao JAR de produção validado localmente.

Correção da pré-visualização dos livros encantados no menu de aldeões. Os componentes já eram enviados e entregues pelo servidor; o cliente mostrava apenas `getHoverName()` e um tooltip com esse nome genérico. A 0.4.1 apresenta os encantamentos guardados no resumo e usa o tooltip nativo do `ItemStack` completo.

## Verificações locais executadas

Ambiente: Java 21, Minecraft 1.21.1, NeoForge 21.1.256, Linux/X11 e renderização por software.

- `python3 tools/cloud_gradle.py build --console=plain`: compilação de produção e **21 testes JUnit** aprovados.
- `python3 tools/cloud_gradle.py -PeconomyGameTests runGameTestServer --console=plain`: **62 testes nativos independentes** aprovados.
- `python3 tools/cloud_gradle.py -PeconomyGameTests -PintegrationTests runGameTestServer --console=plain`: **62 testes nativos com integrações** aprovados.
- `DISPLAY=:98 LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null python3 tools/cloud_gradle.py -PeconomyVisualTests -PintegrationTests runClient --console=plain`: cliente Minecraft real e servidor integrado aprovados, com **31 capturas**.

O teste de cliente abre os menus de produção com pedidos C2S/S2C reais. Confirma livros com Mending e lore, Sharpness V e um livro com três encantamentos, incluindo Curse of Vanishing. Verifica o resumo, o conteúdo do tooltip nativo e a passagem efetiva pelo evento de renderização. Testa o tooltip na linha da oferta, no resultado selecionado e no layout compacto com GUI scale 3. A compra consome o livro exigido, cobra o valor confirmado e entrega exatamente os componentes apresentados, incluindo Mending e lore.

Também voltaram a passar as compras, vendas por esmeraldas, descontos Vanilla, impostos, Mesa da Economia, bigorna, encantamentos, ferraria, poções e beacons. Os recibos financeiros foram confirmados no ficheiro do jogador e no `level.dat/Data/Player` do anfitrião integrado.

## Integrações utilizadas

JARs de produção publicados, verificados por SHA-256: Realm Origins 0.5.7, Realm Treasury 0.3.1, Contracts & Bounties 0.4.0, Realm Council 0.2.2, Realm Markets 0.2.1, Realm Rewards 0.1.2, Realm Notifications 0.1.1, Realm Titles 0.1.0, Realm Identity 0.1.2, Realm Marriage 0.1.0, Realm Casino 0.3.0 e Laws & Justice 0.4.0. Origins, Jupiter e Origins: Classes são carregados como dependências reais.

## Publicação e limites

O pipeline independente do Economy repete as verificações e só publica o JAR, ZIP, hashes, proveniência e capturas depois de todas passarem. A versão da tesouraria usada é um binário publicado, sem republicar nem substituir o Realm Treasury. O JAR de produção é guardado antes dos testes e não inclui classes ou recursos dos fixtures.

As capturas usam ofertas, personagens e dinheiro de demonstração. O cliente é um anfitrião integrado; esta execução não estabelece dois clientes autenticados em LAN. Não cobre todos os encantamentos ou extensões de tooltip de terceiros. A apresentação usa os registos e o pipeline nativo para permitir esses conteúdos. Não houve alteração do protocolo, do formato de dados do Economy ou das regras financeiras.

O relatório definitivo da publicação, commit de origem, contagens e hashes ficam em [realm-economy-0.4.1.build.json](../mod-pronto/realm-economy-0.4.1.build.json). A validação histórica da 0.4.0 permanece em [VALIDACAO.md](VALIDACAO.md).
