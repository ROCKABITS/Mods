# Validação — Crown Ledger 0.2.4

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39401374465), código `ae4923c97a26bd253a5168d653a89aeeb7ae01c2`. Passaram **54 testes JUnit**, **26 testes nativos independentes**, **26 com integrações** e o cliente Minecraft.

- `crown-ledger-0.2.4.jar`: 144215 bytes; SHA-256 `b2a35c7d39615b0ee943b96a29055406cbea5a059b82e0b49b2a0787392e67ec`.
- `crown-ledger-0.2.4.zip`: 142544 bytes; SHA-256 `c5eda8bd4c6c94c075e8bc8435b0b76644d792f75dd619aba6e0dc512c02cf54`.

[Proveniência e companheiros verificados](../mod-pronto/crown-ledger-0.2.4.build.json).

A publicação conjunta com Realm Villager Economy exige a compilação de produção, 54 testes JUnit, 26 testes nativos independentes e 26 com integrações. A suíte Villager Economy valida adicionalmente a carteira real do Chronicles RP, impostos financiados, repetição de recibos, falhas de escrita e recuperação.

O cliente Minecraft abre o comerciante e o Cofre da Coroa reais num servidor integrado, efetua compras e vendas por C2S/S2C e consulta os pagamentos e a aprovação fiscal no histórico público. Verifica os dois ficheiros de reinício do anfitrião: `UUID.dat` e `level.dat/Data/Player`. Estes testes não estabelecem uma sessão com dois clientes autenticados.

O formato 5 lê os formatos 1–4 e mantém as APIs de casino e justiça. As permissões do Presidente, sucessão, persistência e recibos são exercitados no servidor nativo.

[Validação da 0.2.3](VALIDACAO-0.2.3.md).
