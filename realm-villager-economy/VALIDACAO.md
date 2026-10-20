# Validação — Realm Villager Economy 0.1.0 Beta

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39401374465), código `ae4923c97a26bd253a5168d653a89aeeb7ae01c2`. Passaram **9 testes JUnit**, **26 testes nativos independentes**, **26 com integrações** e o cliente Minecraft.

- `realm-villager-economy-0.1.0.jar`: 75845 bytes; SHA-256 `c1124018341b401dc71841c5bc830085d011944cba2e507d1426b1cc15669633`.
- `realm-villager-economy-0.1.0.zip`: 74705 bytes; SHA-256 `4bda1fcf5b9ce35fa25c3ff6e553f37ac6d4c382e8e2e1242d92b1cb5f3144e9`.

[Proveniência e companheiros verificados](../mod-pronto/realm-villager-economy-0.1.0.build.json).

A publicação conjunta com Crown Ledger 0.2.4 exige builds de produção, **9 testes JUnit**, **26 testes nativos independentes** e **26 com os companheiros verificados**, além do cliente Minecraft integrado sobre o mesmo commit. Crown Ledger passa adicionalmente 54 testes JUnit e 26 testes nativos em cada modo.

A cobertura inclui conversão exata, arredondamento, limites fiscais, vendas por esmeraldas, descontos, ingredientes adicionais, stock, componentes dos itens, pedidos repetidos, alteração de preço/taxa, inventário cheio, saldo insuficiente, autoridade do Presidente e acesso administrativo. Os testes de falha interrompem gravações reais e verificam a recuperação sem outro débito, item ou imposto.

O cliente usa personagens, ofertas e construção de demonstração. As operações atravessam os canais C2S/S2C reais e modificam a carteira Chronicles, inventário, ofertas e tesouro Crown. Compara os dois ficheiros de reinício do anfitrião e mostra o histórico real da tesouraria. Rendimentos independentes do Realm Rewards são contabilizados separadamente.

O ambiente validado é Linux, Java 21 e Minecraft 1.21.1/NeoForge 21.1.256. O servidor integrado não representa dois clientes autenticados por LAN. Não verifica todos os NPCs de outros mods nem todas as falhas de hardware. Efeitos secundários de conquistas/XP não fazem parte da transação atómica de dinheiro, itens e stock.
