# Validação — Realm Economy 0.4.0 Beta

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39410537589), código `e70c29deb9c661dceff2ebdbdc3ee44c90b714dc`. Passaram **21 testes JUnit**, **62 testes nativos independentes**, **62 com integrações** e o cliente Minecraft real.

- `realm-economy-0.4.0.jar`: 155833 bytes; SHA-256 `eb7b25c11b64e842297d5ed174c067749989b6d5440fbc858834bd0922f55ddf`.
- `realm-economy-0.4.0.zip`: 150200 bytes; SHA-256 `223ae7fe3702eb379c4d457c517398e413b70d98693e7a2cc403b5ae675d8edb`.

[Proveniência](../mod-pronto/realm-economy-0.4.0.build.json).

A publicação conjunta com Crown Ledger 0.2.5 exige os JARs de produção, 21 testes JUnit do Economy e 59 do Crown, 62 testes nativos do Economy e 29 do Crown em modo independente e com os companheiros verificados, e o cliente Minecraft real no mesmo commit.

Os testes cobrem os pagamentos reais, receita de impostos, papéis do Presidente, limites, autorização delegada sem OP, pedidos repetidos, preços alterados, materiais, componentes, espaço, stock, semente, migração e falhas reais de gravação. Interrompem também um início de produção de poções e verificam um único débito, lote e consumo de combustível.

O cliente usa uma construção, personagens, fundos e materiais de demonstração. Os menus e as operações são os de produção, com C2S/S2C reais. Compra e vende aos aldeões, altera preços e limites, paga os cinco serviços, conserva XP e materiais nativos, espera 20 segundos pelas poções e verifica um beacon pago até à expiração. Confirma carteira, inventário e semente nos dois ficheiros de reinício do anfitrião. A duração de beacon é configurada em 10 segundos apenas no mundo de teste; o padrão do mod é 600 segundos.

O Realm Admin não está disponível: foi testado o contrato real de fornecedores de permissões do Economy, incluindo jogadores sem OP e recusa quando o fornecedor falha. Não foi testado um mod Realm Admin real.

O ambiente é Linux, Java 21, Minecraft 1.21.1 e NeoForge 21.1.256. O cliente integrado não estabelece dois clientes autenticados por LAN. Não testa todos os sistemas de NPCs/receitas de terceiros nem falhas arbitrárias de hardware. Conquistas, efeitos de reparação, sons e avisos são secundários ao recibo financeiro e não são repetidos na recuperação.
