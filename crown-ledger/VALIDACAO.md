# Validação — Crown Ledger 0.2.5

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39410537589), código `e70c29deb9c661dceff2ebdbdc3ee44c90b714dc`. Passaram **59 testes JUnit**, **29 testes nativos independentes**, **29 com integrações** e o cliente Minecraft real.

- `crown-ledger-0.2.5.jar`: 147780 bytes; SHA-256 `661825b0a006066dc53a93ed90ef402b6cd25c678a038f3e5d7411c5e2d24afa`.
- `crown-ledger-0.2.5.zip`: 146371 bytes; SHA-256 `514be651604e023ea8de7c1bda149ccdeab48839fe3532bcbd2ab2e1f9fd06ca`.

[Proveniência](../mod-pronto/crown-ledger-0.2.5.build.json).

A publicação conjunta com Realm Economy exige 59 testes JUnit e 29 testes nativos próprios em modo independente e integrado, incluindo a API económica real, repetição de recibos e migração dos registos fiscais do formato 5 para 6. A suíte Economy verifica adicionalmente pagamentos de serviços com a carteira Chronicles real, impostos e recuperação.

O cliente abre o cofre real e consulta os impostos e aprovações produzidos pelos serviços. Os testes de administração delegada validam o contrato do Economy; não existe ainda um Realm Admin no repositório. Não é uma prova com dois clientes LAN autenticados.

[Validação da 0.2.4](VALIDACAO-0.2.4.md).
