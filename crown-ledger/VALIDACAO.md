# Validação — Crown Ledger 0.2.4

A publicação conjunta com Realm Villager Economy exige a compilação de produção, 54 testes JUnit, 26 testes nativos independentes e 26 com integrações. A suíte Villager Economy valida adicionalmente a carteira real do Chronicles RP, impostos financiados, repetição de recibos, falhas de escrita e recuperação.

O cliente Minecraft abre o comerciante e o Cofre da Coroa reais num servidor integrado, efetua compras e vendas por C2S/S2C e consulta os pagamentos e a aprovação fiscal no histórico público. Verifica os dois ficheiros de reinício do anfitrião: `UUID.dat` e `level.dat/Data/Player`. Estes testes não estabelecem uma sessão com dois clientes autenticados.

O formato 5 lê os formatos 1–4 e mantém as APIs de casino e justiça. As permissões do Presidente, sucessão, persistência e recibos são exercitados no servidor nativo.

A execução aprovada e os hashes dos artefactos serão acrescentados depois da publicação automática.

[Validação da 0.2.3](VALIDACAO-0.2.3.md).
