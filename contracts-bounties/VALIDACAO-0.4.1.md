# Validação — Contracts & Bounties 0.4.1

Atualização da apresentação do mural e da seleção de materiais. O cliente escolhe um item concreto do registo e envia o mesmo identificador no pedido de publicação existente. O protocolo mantém-se em 3; formatos de dados, recompensas, reservas, impostos e regras de entrega mantêm-se.

## Verificações locais

- Build com Java 21 / Minecraft 1.21.1 / NeoForge 21.1.256.
- **90 testes JUnit**, incluindo oito novos testes de pesquisa e disposição: nomes localizados, acentos/maiúsculas, palavras múltiplas, identificadores opcionais, tags que devolvem itens concretos, filtro de inventário, resultados vazios, paginação e limites dos controlos entre 320 × 240 e 1280 × 720.
- **38 GameTests de servidor** em modo independente: publicação, materiais, reservas, conclusão, recolhas, permissões, sessões e integrações opcionais nos ramos que não exigem companheiros.
- Cliente Minecraft real, com recursos e ícones carregados, para os três modelos de mural e a interface de produção. Onze capturas: quatro dos modelos, mural, formulário, catálogo, pesquisa, material selecionado, janela compacta e português.
- A pesquisa por `contracts:` encontra os três itens de mural do próprio mod. A pesquisa por nome encontra os troncos de carvalho; clicar no ícone devolve `minecraft:oak_log`, sem digitar esse identificador no formulário.
- Verificados resultados vazios, alternância do filtro de inventário, preservação do título/quantidade/recompensa, atualização de política sem fechar o catálogo, encerramento da sessão e revogação da permissão de publicação.
- Formulário e catálogo em 320 × 240, sem controlos fora da janela ou sobrepostos. A seleção continua disponível e o botão Voltar preserva o rascunho.

O workflow [Build Contracts & Bounties](../.github/workflows/build-mod.yml) repete build, testes de servidor e cliente antes de publicar JAR, ZIP, checksums, proveniência e [galeria](../media/0.4.1/README.md). O ficheiro `mod-pronto/contracts-bounties-0.4.1.build.json` identifica o commit e a execução efetivamente publicados quando essa fase termina.

## Âmbito e limites

As capturas utilizam o cliente real com snapshots financeiros de demonstração, sem fabricar pagamentos. Este ensaio visual não tem um jogador ligado: o filtro de inventário verifica o estado vazio nesse ensaio, e a filtragem por quantidades positivas é exercitada pelos testes de pesquisa. O código conta apenas os 36 espaços principais e compara cada stack com o item normal, seguindo a regra do servidor.

As tags são filtros opcionais sobre o catálogo sincronizado. Uma tag não transforma um contrato num pedido genérico de todos os itens desse grupo. O nome localizado e o identificador do mod permitem pesquisar os itens registados; esta revisão não testa todos os modpacks nem todos os nomes de mods de terceiros.

Não foram repetidos os cenários financeiros com Realm Origins/Realm Treasury, as quests diárias, dois clientes autenticados ou as sessões TCP LAN da 0.4.0. As verificações e os limites históricos dessas funcionalidades continuam em [VALIDACAO.md](VALIDACAO.md).
