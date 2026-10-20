# Validação — Crown Ledger 0.2.3

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39347074971), código `d7d367d362905fe3ef2763bdcc9066c3d7f34928`. O JAR publicado passou a compilação, **52 testes JUnit**, **26 testes nativos independentes** e **26 com integrações**, além do cliente Minecraft.

- crown-ledger-0.2.3.jar: 141295 bytes; SHA-256 `85f8ea2006d73302d34e609a884bd8831e9769ab6b66a2ef2e59de02e6cf0847`.
- crown-ledger-0.2.3.zip: 139193 bytes; SHA-256 `1d4115f6a7998d908786aeb9dd0310eb17dbe9fe945bb29c02ac41c1310187b5`.

A publicação conjunta com Realm Casino 0.3.0 exige o build de produção, 52 testes unitários, 26 testes nativos independentes e os mesmos testes com companheiros, além da execução do cliente real para o cofre e regras fiscais.

A suíte Casino exercita adicionalmente a API real do Crown com Chronicles: levantamento do capital, imposto do lucro, saldo do reino, gravação comprimida, falha de escrita, repetição do mesmo recibo e recuperação sem duplicação. A suíte nativa própria Crown usa os companheiros sem Chronicles para preservar a cobertura das esmeraldas; a integração com a carteira real é verificada no Casino.

O teste do cliente renderiza o modelo distribuído e o TreasuryScreen de produção com dados de demonstração, incluindo taxa do casino, valores decimais exatos, limites 0–100%, troca de moeda e controlos a 320 × 240. Não é uma prova de dois clientes autenticados. Permissões do Presidente, sucessão e formato 4 são verificados no servidor.

[Validação da 0.2.2](VALIDACAO-0.2.2.md) · [Proveniência](../mod-pronto/crown-ledger-0.2.3.build.json).
