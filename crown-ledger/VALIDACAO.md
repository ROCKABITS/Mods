# Validação — Crown Ledger 0.2.5

A publicação conjunta com Realm Economy exige 59 testes JUnit e 29 testes nativos próprios em modo independente e integrado, incluindo a API económica real, repetição de recibos e migração dos registos fiscais do formato 5 para 6. A suíte Economy verifica adicionalmente pagamentos de serviços com a carteira Chronicles real, impostos e recuperação.

O cliente abre o cofre real e consulta os impostos e aprovações produzidos pelos serviços. Os testes de administração delegada validam o contrato do Economy; não existe ainda um Realm Admin no repositório. Não é uma prova com dois clientes LAN autenticados.

[Validação da 0.2.4](VALIDACAO-0.2.4.md).
