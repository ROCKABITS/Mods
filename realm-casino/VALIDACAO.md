# Validação — Realm Casino 0.2.0 Beta

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39264214894), código `9958b05808e82242671c8a75db77fd169c21cb43`. O JAR publicado passou a compilação, 58 testes JUnit, 56 testes nativos independentes e 56 com integrações, além do cliente real. A galeria inclui **31 capturas de 1600 × 900**.

- JAR: 346428 bytes; SHA-256 `25cbf35950be8f021ec1d1d40381c6562c6a3ba476d1b567afcd5ec91aa895d5`.
- ZIP: 325466 bytes; SHA-256 `b89e0757f986531a181ff0734e69ecaedc2c8876135d24da554c4a2a08f0e1fa`.
- [Proveniência e dependências](../mod-pronto/realm-casino-0.2.0.build.json) · [galeria](../media/realm-casino/0.2.0/README.md).
- Animação física da Roda da Fortuna: 34145 píxeis alterados na região observada, com a mesma câmara e o mesmo resultado.

A atualização acrescenta Plinko, Roda da Fortuna e Travessia Real aos três jogos existentes. A publicação automática só disponibiliza o JAR depois de repetir a compilação, os testes unitários, os dois modos do servidor e a verificação do cliente real sobre o mesmo commit.

## Cobertura

- **58 testes JUnit:** enumeração dos 4096 percursos de Plinko, distribuição binomial, retornos da roda, decisões e falhas da galinha, arredondamento exato, limites, reservas, conservação de dinheiro e rejeição de estados inválidos, além das regras e do registo existentes.
- **56 testes nativos sem integrações:** registo dos seis blocos, receitas e pesquisa criativa, quatro orientações, proteção dos móveis, desmontagem de ambas as metades dos três móveis altos, dados e indisponibilidade das apostas sem carteira.
- **56 testes nativos com integrações:** carteira real do Chronicles, prémios exatos, reservas máximas, rejeição de repetições, perda de sessão, prazo, recolha automática da galinha, conservação, gravação e leitura NBT, recuperação do diário e notificações.
- **Cliente Minecraft real:** seis modelos e respetivos itens sem texturas em falta, interfaces de jogo/regras/proprietário, controlos a 320 × 240, apostas e recolhas por C2S/S2C reais, dados dos blocos sincronizados e capturas a 1600 × 900. As rodas são fotografadas em movimento e paradas, com câmara e resultado constantes, verificando alterações nos píxeis da superfície.
- **Persistência do anfitrião:** após apostas e pagamentos, comparar saldo e recibo em `playerdata/UUID.dat` e `level.dat/Data/Player`; carregar a tag real do anfitrião num jogador novo do servidor confirma a origem usada pelo Minecraft ao reabrir o mundo.

O cliente usa personagens, construção e fundos de demonstração. Os resultados são produzidos pelo gerador real do servidor, sem substituir os dados financeiros dos ecrãs. Os testes nativos usam atores de teste e resultados controlados para verificar vitórias e derrotas específicas.

## Limites

Validação em Linux, Java 21 e Minecraft 1.21.1/NeoForge. O cliente utiliza servidor integrado; esta prova não representa uma ligação autenticada com dois clientes nem verifica todas as combinações de mods ou todas as falhas de hardware. A sincronização de diretórios no Windows não foi validada. Mantém cópias de segurança conjuntas do mundo e dos perfis.

O formato 2 lê os dados existentes do formato 1. Não carregar um mundo já atualizado à 0.2.0 com o JAR antigo da 0.1.0. O imposto do Crown Ledger e os jogos restantes continuam planeados.

[Validação da versão anterior](VALIDACAO-0.1.0.md).
