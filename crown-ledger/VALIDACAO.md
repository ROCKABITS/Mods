# Realm Treasury 0.3.1

[Current validation scope](VALIDACAO-0.3.1.md).

# Realm Treasury 0.3.0

See [current validation scope and reproduction](VALIDACAO-0.3.0.md). The material below records earlier Crown Ledger releases.

# Validação — Crown Ledger 0.2.6

[Execução aprovada 39458831632](https://github.com/ROCKABITS/Mods/actions/runs/39458831632), código [`e67578e`](https://github.com/ROCKABITS/Mods/commit/e67578e5d38f8e450023f3f91b839b7de916aaf0). **Publicação concluída; downloads públicos verificados por SHA-256.**

A compilação local aprovou **59 testes JUnit** e **29 GameTests próprios**. A suíte integrada de 29 testes diários do Contracts & Bounties verifica a nova CrownDailyApi v1 com o tesouro real: conservação bruto = carteira + imposto, classificação fiscal, recibo repetido e falha de escrita provocada no ficheiro do Crown, seguida de recuperação sem voltar a pagar à carteira.

O cliente Minecraft real concluiu cinco quests e confirmou a carteira Chronicles, o tesouro e os recibos dos dois ficheiros do anfitrião. O servidor integrado foi realmente aberto à LAN e o mundo fechado/reaberto. Não é uma validação com dois clientes autenticados nem uma nova inspeção gráfica de todas as interfaces do Crown. O formato 6 e as restantes APIs foram preservados.

A publicação conjunta voltou a executar e aprovou estes testes no commit publicado. As contagens, hashes e execução estão em [crown-ledger-0.2.6.build.json](../mod-pronto/crown-ledger-0.2.6.build.json). Os testes do novo imposto estão em `contracts-bounties/src/dailygametest/` e o workflow em [build-daily-quests.yml](../.github/workflows/build-daily-quests.yml).

- `crown-ledger-0.2.6.jar`: 148687 bytes; SHA-256 `966f67d556a9911127fd8aa271e20debe1166370c5d0ea33aabd66bbc1106daf`.
- `crown-ledger-0.2.6.zip`: 147470 bytes; SHA-256 `9d225338510a974857668e5c2206019de24c53cdf6dbe5280b085385301e4bf1`.

---

## Validação histórica 0.2.5

# Validação — Crown Ledger 0.2.5

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/39410537589), código `e70c29deb9c661dceff2ebdbdc3ee44c90b714dc`. Passaram **59 testes JUnit**, **29 testes nativos independentes**, **29 com integrações** e o cliente Minecraft real.

- `crown-ledger-0.2.5.jar`: 147780 bytes; SHA-256 `661825b0a006066dc53a93ed90ef402b6cd25c678a038f3e5d7411c5e2d24afa`.
- `crown-ledger-0.2.5.zip`: 146371 bytes; SHA-256 `514be651604e023ea8de7c1bda149ccdeab48839fe3532bcbd2ab2e1f9fd06ca`.

[Proveniência](../mod-pronto/crown-ledger-0.2.5.build.json).

A publicação conjunta com Realm Economy exige 59 testes JUnit e 29 testes nativos próprios em modo independente e integrado, incluindo a API económica real, repetição de recibos e migração dos registos fiscais do formato 5 para 6. A suíte Economy verifica adicionalmente pagamentos de serviços com a carteira Chronicles real, impostos e recuperação.

O cliente abre o cofre real e consulta os impostos e aprovações produzidos pelos serviços. Os testes de administração delegada validam o contrato do Economy; não existe ainda um Realm Admin no repositório. Não é uma prova com dois clientes LAN autenticados.

[Validação da 0.2.4](VALIDACAO-0.2.4.md).

## 0.2.7 — Realm Marriage

A suite local preservou os **59 testes JUnit e 29 GameTests** existentes. O novo CrownMarriageApi também foi exercitado pelos 23 GameTests integrados do Realm Marriage: taxa positiva, recibo repetido, falha real de gravação da tesouraria e recuperação sem novo débito das carteiras. A sessão de quatro clientes TCP LAN verificou a taxa pública de 5000 cêntimos e a conservação após divórcio e reabertura do mundo. A publicação conjunta exige repetir estas verificações no CI; o `.build.json` do artefacto conserva a origem e os resultados. [Âmbito e limites](../realm-marriage/VALIDACAO.md).
