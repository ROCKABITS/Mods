# Validação — Crown Ledger 0.2.0

A [execução conjunta 38924972884](https://github.com/ROCKABITS/Mods/actions/runs/38924972884) compilou, testou e publicou Crown Ledger e Realm Markets **0.2.0**, a partir do mesmo commit `1a6825f00d37ec07c194e96a4b05c003f892d8d1`. A publicação foi concluída no commit do bot `e9991c8` depois de todas as verificações obrigatórias.

| Verificação | Resultado |
| --- | --- |
| Compilação de produção | Java 21, Minecraft 1.21.1 e NeoForge 21.1.256; aprovada |
| Testes unitários Crown Ledger | **46 aprovados**, sem falhas, erros ou testes ignorados |
| Testes nativos Crown Ledger independente | **19 obrigatórios aprovados** |
| Testes nativos Crown Ledger com os companheiros reais | **19 obrigatórios aprovados** |
| Outro JAR novo | Realm Markets 0.2.0 do mesmo commit: **45 unitários, 45 nativos independente e 45 com os companheiros aprovados** |
| Cliente Minecraft real | Cofre com 264 faces, quatro direções, texturas e três capturas de 1600 × 900 aprovados |
| Menus de produção | Donativos, moedas, salários e imposto de mercado independente, incluindo controlos a 320 × 240, aprovados |
| Empacotamento | Versão e recursos obrigatórios presentes; classes e estruturas de teste ausentes |

Os testes novos verificam a taxa de mercado separada do imposto fixo e da percentagem dos contratos, pedidos inteiros e limites válidos, preservação da taxa em pedidos antigos que omitem o campo e autorização exclusiva do governante atual com mandato jurado. Um menu já aberto pelo antigo governante perde essa autorização após a sucessão.

As verificações de persistência aprovam a migração nativa do formato 1 para o 2 com taxa inicial zero, preservando saldo, créditos de salários, histórico, regras e responsável. O formato 2 rejeita taxas ausentes, mal tipadas ou fora dos limites.

## Integração fiscal efetivamente executada

Os dois JARs de produção foram compilados **antes** dos testes nativos. Crown Ledger carregou o JAR novo de **Realm Markets 0.2.0**, mais os JARs publicados e verificados de **Contracts & Bounties 0.3.1**, **Realm Council 0.2.1** e **Realm Identity 0.1.0**. A execução do outro projeto carregou este mesmo Crown Ledger novo; não se aprovou a integração usando a versão antiga de nenhum dos dois.

Os testes fizeram compras reais no servidor, com itens e esmeraldas: **20 pagos = 19 de ganho líquido para o proprietário offline + 1 de imposto reservado**. Verificaram a entrada exata desse imposto no tesouro e no histórico, repetição de recolhas e sincronizações sem nova tributação, retenção da reserva quando o tesouro está cheio e transferência única após recuperar capacidade. Verificaram também imposto de 100%, com ganho líquido zero. Os dois registos de integração emitiram os marcadores obrigatórios de conclusão destas verificações.

A interface de produção guardou 5% e 5,25% em pontos base exatos, manteve a taxa ao mudar a moeda fiscal e alterou-a independentemente do modo fixo/percentagem dos contratos. Os pedidos dos botões foram registados localmente pelo teste visual; as transferências entre mods foram verificadas separadamente nos testes nativos.

## Ficheiros publicados

- [JAR](../mod-pronto/crown-ledger-0.2.0.jar): **126 447 bytes**.
- [ZIP com JAR, instruções e changelog](../mod-pronto/crown-ledger-0.2.0.zip): **123 235 bytes**.
- [Checksums](../mod-pronto/crown-ledger-0.2.0.sha256) e [proveniência com resultados e hashes dos companheiros](../mod-pronto/crown-ledger-0.2.0.build.json).
- [Três capturas Minecraft](../media/crown-ledger/0.2.0/README.md) e [resultado do cliente](../media/crown-ledger/0.2.0/validation.json). Os saldos e regras das imagens são dados de demonstração.

```text
278826d3ed182e7ea33bcb57cbd8358cd9d39b5f5fb582b7029923f3fdd3ee77  crown-ledger-0.2.0.jar
5766c4051b5ec450c281ff5ce206c4e579736e8f29ab1c6889ff722bf9a35009  crown-ledger-0.2.0.zip
b57021c9efa3c7a6bd017d9b53d552963d10270da70bde5dfacad68a5f2e6866  realm-markets-0.2.0.jar (usado nos testes)
```

## Atualização e limites

Atualiza **os dois mods para 0.2.0 ou superior no servidor e em todos os clientes** quando os usas juntos. Faz uma cópia de segurança do mundo completo e dos perfis do Chronicles **antes de atualizar**. O formato 1 migra para o 2 conservando valores anteriores e iniciando o imposto de mercado em zero. Para regressar a 0.1.x depois da migração, restaura a cópia completa anterior; não basta repor o JAR antigo.

O imposto em trânsito fica em `data/realm_markets.dat` até o tesouro o recolher, normalmente em até um segundo de servidor ativo ou ao atualizar o cofre. Os registos do tesouro, mercado, inventários e perfis usam a persistência normal do Minecraft, sem uma transação de disco única durante uma falha abrupta.

O runtime completo de **Chronicles RP/Origins/Jupiter não foi executado** neste CI. A matemática em cêntimos, os testes com esmeraldas e os pedidos da interface não comprovam a sincronização de perfis com esses mods instalados. Uma sessão com dois clientes reais e um reinício manual do mundo constituem validação adicional.

A compilação Minecraft foi feita no GitHub Actions. O êxito do CI não comprova que o acesso ao Maven NeoForge desta sessão cloud tenha sido desbloqueado; os 46 testes independentes do domínio passaram também localmente com Java 21. Não se desativaram verificações TLS ou de checksums.

## Histórico preservado — 0.1.1

A [execução 38779566016](https://github.com/ROCKABITS/Mods/actions/runs/38779566016), sobre `47fc8659feca1cf652869fba5f09c80ada9303f3`, aprovou **42 unitários, 15 nativos independente, 15 com Contracts 0.3.1/Realm Council 0.2.1 e o cliente Minecraft**. Cobre o relógio de atividade, salários, imposto fixo sem dívida, contratos públicos e governação. Os [artefactos, hashes e proveniência 0.1.1](../mod-pronto/crown-ledger-0.1.1.build.json) e as [imagens antigas](../media/crown-ledger/0.1.1/README.md) permanecem preservados; essa versão não inclui imposto de mercado.
