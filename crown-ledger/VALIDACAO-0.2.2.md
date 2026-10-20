# Validação — Crown Ledger 0.2.2

**Aprovada.** [Execução conjunta com Laws & Justice 0.4.0](https://github.com/ROCKABITS/Mods/actions/runs/39253815267), fonte [`86b9398`](https://github.com/ROCKABITS/Mods/commit/86b93988a7ef5bfe9736f56085c6a861adddb66e). Minecraft 1.21.1, NeoForge 21.1.256 e Java 21.

- **52 testes unitários**, **25 GameTests independente** e **25 com os companheiros reais**, sem falhas ou testes ignorados.
- Três capturas no cliente Minecraft real: modelo original do cofre, interface pública e regras do governante. Os valores destas imagens são dados de demonstração.
- O JAR Laws & Justice 0.4.0 foi compilado na mesma revisão e utilizado como contrapartida nova. Os testes próprios do Crown mantêm o modo sem Chronicles; a suíte completa do Laws & Justice carrega o Chronicles, as dependências e a nova versão Crown.
- A sequência real do Laws & Justice pagou **20 € de fiança e 25 € de multa**: a carteira passou de **100 € para 55 €**, e a tesouraria recebeu exatamente **45 €**. Repetir os recibos antes e depois de uma paragem/reabertura efetiva do mundo LAN não alterou estes saldos.
- Os testes de ficheiros comprimidos e da fila de escrita NeoForge verificaram migração, recibos alterados/duplicados, gravações antigas em fila, falhas reais de substituição, recuperação e confirmação persistente dos recibos.
- O formato 3 conserva regras, saldos, atividade e salários dos formatos 1 e 2. A receita e as regras de imposto das bancas mantêm compatibilidade com Realm Markets 0.2.1.

O teste LAN usa o anfitrião real e jogadores de teste nativos nas suítes de servidor. Não houve uma ligação autenticada de dois clientes Minecraft independentes. A [validação completa](../laws-justice/VALIDACAO-0.4.0.md) descreve estes limites e os cenários utilizados.

| Ficheiro | SHA-256 |
| --- | --- |
| `crown-ledger-0.2.2.jar` | `a91afc03da4aae23cb584911bdcd88cf3b5a4fcf91bcc13b683ef6405fe6dab5` |
| `crown-ledger-0.2.2.zip` | `ffacf2862f3b00e9415669f449aa6125e27218a6b05dd681fe005c1dc1cc2e71` |

[Proveniência e companheiros](../mod-pronto/crown-ledger-0.2.2.build.json) · [Imagens](../media/crown-ledger/0.2.2/) · [Validações anteriores](VALIDACAO-0.2.1.md).
