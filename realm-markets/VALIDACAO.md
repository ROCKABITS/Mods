# Validação atual — Realm Markets 0.2.1

**Aprovada.** A [execução conjunta 39009308013](https://github.com/ROCKABITS/Mods/actions/runs/39009308013), sobre o código [`0d9e459`](https://github.com/ROCKABITS/Mods/commit/0d9e45962ae298e4fac98f46c6c9b5823e0ffc8d), compilou o JAR de produção e aprovou **45 testes unitários** e **45 GameTests nativos em modo independente**, com Minecraft 1.21.1, NeoForge 21.1.256 e Java 21. Os [metadados desta compilação](../mod-pronto/realm-markets-0.2.1.build.json) registam as contagens, versões e hashes.

O JAR novo desta versão foi também carregado com as outras cinco versões novas dos companheiros, Origins, Jupiter e Realm Notifications 0.1.0 na integração de **15 GameTests do Realm Notifications**; estes não substituem nem repetem a bateria completa de cada companheiro em modo integrado. A ponte opcional foi exercitada com o payload real e com fallback de chat para clientes sem o canal.

A ponte opcional do Markets enviou o payload real `Show` e preservou o fallback sem o canal. Esta integração não percorreu uma compra completa no mercado com o cliente Realm Notifications.

O cliente Minecraft real aprovou o HUD, as preferências e o diário do Realm Notifications; as [capturas desta integração](../media/realm-notifications/0.1.0/README.md) mostram essa interface. Não houve nova validação gráfica da interface própria deste companheiro. As verificações e galerias das versões anteriores abaixo permanecem históricas.

## Histórico de validação

# Validação — Realm Markets 0.2.0

A [execução conjunta 38924972884](https://github.com/ROCKABITS/Mods/actions/runs/38924972884) compilou, testou e publicou Realm Markets e Crown Ledger **0.2.0**, a partir do mesmo commit `1a6825f00d37ec07c194e96a4b05c003f892d8d1`. A publicação foi concluída no commit do bot `e9991c8` depois de todas as verificações obrigatórias.

| Verificação | Resultado |
| --- | --- |
| Compilação de produção | Java 21, Minecraft 1.21.1 e NeoForge 21.1.256; aprovada |
| Testes unitários Realm Markets | **45 aprovados**, sem falhas, erros ou testes ignorados |
| Testes nativos Realm Markets independente | **45 obrigatórios aprovados** |
| Testes nativos Realm Markets com os companheiros reais | **45 obrigatórios aprovados** |
| Outro JAR novo | Crown Ledger 0.2.0 do mesmo commit: **46 unitários, 19 nativos independente e 19 com os companheiros aprovados** |
| Cliente Minecraft real | Dois modelos de item, os 32 estados das bancas e três capturas de 1600 × 900 aprovados |
| Menus de produção | Proprietário/comprador, total, imposto e ganho líquido, pedidos dos botões e deslocação a 320 × 240 aprovados |
| Empacotamento | Versão, API fiscal e recursos obrigatórios presentes; classes e estruturas de teste ausentes |

A bateria mantém as verificações de colocação nas quatro direções, propriedade, stock real, compras e ganhos com o dono offline, capacidade do inventário, compras sequenciais, preços alterados, validade das sessões, remoção protegida, explosões, rejeição de itens personalizados/contentores e persistência NBT estrita.

Os testes novos verificam imposto sobre o total da compra, arredondamento em esmeraldas, 100% com ganho líquido zero, rejeição de cotações antigas ou malformadas sem cobrança, integração indisponível sem bloquear a recuperação de artigos/ganhos, permissões exatas do fornecedor fiscal e rejeição de transferências de outro thread, moeda ou quantia inválida. Uma reserva fiscal cheia recusa a compra antes de mover dinheiro ou stock.

## Integração fiscal efetivamente executada

Os dois JARs de produção foram compilados **antes** dos testes nativos. Realm Markets carregou o JAR novo de **Crown Ledger 0.2.0**, mais os JARs publicados e verificados de **Contracts & Bounties 0.3.1**, **Realm Council 0.2.1** e **Realm Identity 0.1.0**. A execução do outro projeto carregou este mesmo Realm Markets novo; não se aprovou a integração usando a versão antiga de nenhum dos dois.

Os testes fizeram compras reais no servidor: **20 esmeraldas pagas = 19 de ganho líquido para o dono offline + 1 de imposto reservado**. Crown Ledger recebeu esse imposto exatamente uma vez. Recolher ganhos, retirar stock ou repetir a sincronização não voltou a tributá-lo. Com o tesouro cheio, a reserva manteve-se no mercado; após recuperar capacidade, foi transferida uma única vez. As verificações confirmaram também a taxa de mercado de 5% independente do modo fixo e da percentagem de contratos de 7%. Os dois registos de integração emitiram os marcadores obrigatórios de conclusão.

A interface mostrou **20,00 € = 1,00 € de imposto + 19,00 € líquidos**, atualizou a taxa enviada após mudar as regras e suspendeu compras quando o fornecedor fiscal ficou indisponível. Os botões do proprietário continuaram a permitir retirar stock e recolher ganhos. O teste visual registou os pedidos localmente, sem os enviar a um servidor; as transferências reais foram verificadas pelos testes nativos. As capturas usam dados de demonstração claramente identificados.

## Ficheiros publicados

- [JAR](../mod-pronto/realm-markets-0.2.0.jar): **145 202 bytes**.
- [ZIP com JAR, instruções, changelog e licença](../mod-pronto/realm-markets-0.2.0.zip): **136 552 bytes**.
- [Checksums](../mod-pronto/realm-markets-0.2.0.sha256) e [proveniência com resultados e hashes dos companheiros](../mod-pronto/realm-markets-0.2.0.build.json).
- [Três capturas Minecraft](../media/realm-markets/0.2.0/README.md) e [resultado do cliente](../media/realm-markets/0.2.0/validation.json).

```text
b57021c9efa3c7a6bd017d9b53d552963d10270da70bde5dfacad68a5f2e6866  realm-markets-0.2.0.jar
85adb9881aa1ae42676437908300d55544a79eee763c9660dcddd527a73462b6  realm-markets-0.2.0.zip
278826d3ed182e7ea33bcb57cbd8358cd9d39b5f5fb582b7029923f3fdd3ee77  crown-ledger-0.2.0.jar (usado nos testes)
```

## Atualização e limites

Atualiza **os dois mods para 0.2.0 ou superior no servidor e em todos os clientes** quando os usas juntos. Faz uma cópia de segurança do mundo completo e dos perfis do Chronicles **antes de atualizar**. A migração nativa aprovada do formato 1 para o 2 conserva proprietários, lojas, stock e ganhos anteriores, com reserva fiscal inicialmente vazia. A persistência da reserva fiscal no formato 2 e a rejeição de dados ausentes, duplicados ou negativos também passaram. Depois da migração, regressar à versão antiga exige restaurar a cópia completa anterior; não basta trocar o JAR.

O imposto fica em `data/realm_markets.dat` até Crown Ledger o recolher, normalmente em até um segundo de servidor ativo ou ao atualizar o cofre. Os registos dos dois mods, inventários e perfis usam a persistência normal do Minecraft. As verificações no thread principal não criam uma transação de disco única durante uma falha abrupta.

O runtime completo de **Chronicles RP 0.5.2, Origins e Jupiter não foi executado** neste CI. A matemática em cêntimos, a rejeição de uma moeda Chronicles indisponível e os preços da interface não equivalem a executar esse conjunto completo. Não se redistribuem os JARs originais. Uma sessão com dois clientes reais e um reinício manual do mundo constituem validação adicional.

A compilação Minecraft foi feita no GitHub Actions. O êxito do CI não comprova que o acesso ao Maven NeoForge desta sessão cloud tenha sido desbloqueado; os 45 testes independentes do domínio passaram também localmente com Java 21. Não se desativaram verificações TLS ou de checksums.

## Histórico preservado

- **0.1.1:** a [execução 38887747972](https://github.com/ROCKABITS/Mods/actions/runs/38887747972), sobre `6e8f8ab186f9ba1ba6eace7af3dab1cfb0475633`, aprovou **32 unitários, 37 nativos independente, 37 com os quatro companheiros publicados e o cliente Minecraft**. Corrigiu o ângulo dos modelos de item. Os [artefactos, hashes e proveniência](../mod-pronto/realm-markets-0.1.1.build.json) e as [imagens](../media/realm-markets/0.1.1/README.md) permanecem preservados.
- **0.1.0:** a [execução 38887031968](https://github.com/ROCKABITS/Mods/actions/runs/38887031968), sobre `43654c451d32cf5e11cdbe8da943f3a0d406dcbe`, aprovou também **32 unitários, 37 nativos independente, 37 com os companheiros e o cliente**. A [proveniência e os artefactos antigos](../mod-pronto/realm-markets-0.1.0.build.json) continuam disponíveis.

As versões 0.1.x não incluem imposto de mercado.
