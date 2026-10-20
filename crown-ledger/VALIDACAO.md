# Validação — Crown Ledger 0.2.3

A publicação conjunta com Realm Casino 0.3.0 exige o build de produção, 52 testes unitários, 26 testes nativos independentes e os mesmos testes com companheiros, além da execução do cliente real para o cofre e regras fiscais.

A suíte Casino exercita adicionalmente a API real do Crown com Chronicles: levantamento do capital, imposto do lucro, saldo do reino, gravação comprimida, falha de escrita, repetição do mesmo recibo e recuperação sem duplicação. A suíte nativa própria Crown usa os companheiros sem Chronicles para preservar a cobertura das esmeraldas; a integração com a carteira real é verificada no Casino.

O teste do cliente renderiza o modelo distribuído e o TreasuryScreen de produção com dados de demonstração, incluindo taxa do casino, valores decimais exatos, limites 0–100%, troca de moeda e controlos a 320 × 240. Não é uma prova de dois clientes autenticados. Permissões do Presidente, sucessão e formato 4 são verificados no servidor.

[Validação da 0.2.2](VALIDACAO-0.2.2.md) · [Proveniência](../mod-pronto/crown-ledger-0.2.3.build.json).
