# Mods de Minecraft

Projetos de ROCKABITS para **Minecraft 1.21.1**, **NeoForge 21.1.256+** e **Java 21**. Instala a mesma versão de cada mod no servidor e nos clientes; substitui os JARs antigos.

| Mod | Download | Funcionalidades |
| --- | --- | --- |
| **Realm Notifications 0.1.0** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-notifications-0.1.0.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-notifications-0.1.0.zip) | Avisos medievais animados, selos próprios, preferências e diário da sessão. |
| **Realm Rewards 0.1.2** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-rewards-0.1.2.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-rewards-0.1.2.zip) | Recompensas por conquistas Vanilla e de outros mods, carteira do Chronicles e configuração por conquista. |
| **Chronicles RP 0.5.4** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/cronicasrp-0.5.4.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/cronicasrp-0.5.4.zip) | Personagens, economia e nome RP acima da cabeça, no TAB e no chat. Requer Origins e Jupiter. |
| **Realm Markets 0.2.1** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-markets-0.2.1.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-markets-0.2.1.zip) | Duas bancas medievais, stock real, vendas com o dono offline e imposto para a tesouraria. |
| **Realm Identity 0.1.0** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-identity-0.1.0.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-identity-0.1.0.zip) | Documento físico, escrivaninha, assinatura do Chronicles e apresentação com consentimento. |
| **Crown Ledger — Tesouro da Coroa 0.2.1** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/crown-ledger-0.2.1.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/crown-ledger-0.2.1.zip) | Cofre físico, contas públicas, governante eleito, impostos, salários financiados e contratos públicos. |
| **Contracts & Bounties 0.3.2** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/contracts-bounties-0.3.2.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/contracts-bounties-0.3.2.zip) | Três murais 3D, entregas de materiais, recompensas reservadas, impostos e reputação. |
| **Realm Council 0.2.2** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realmcouncil-0.2.2.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realmcouncil-0.2.2.zip) | Eleições, mandatos, juramentos e decisões públicas, através dos blocos. |

O ZIP é uma alternativa ao download direto do JAR. Extrai-o antes de instalar. O [pacote de notificações e atualizações](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-notifications-0.1.0-suite.zip) reúne o novo mod e os seis mods atualizados; escolhe apenas os que usas e substitui os JARs antigos.

Se usas Crown Ledger e Realm Markets juntos, atualiza **ambos para 0.2.1** no servidor e em todos os clientes, substituindo os JARs antigos. Faz uma cópia de segurança do mundo e dos perfis do Chronicles antes de atualizar: os dados migram para o formato 2 e regressar à versão anterior exige restaurar essa cópia.

- [Guia do Realm Notifications](realm-notifications/README.md): avisos no ecrã, tecla N, preferências, diário e API opcional.
- [Guia do Realm Rewards](realm-rewards/README.md): pagamentos por conquistas, valores padrão, administração e dinheiro pendente.
- [Guia do Chronicles RP](chronicles-rp/README.md): personagens, nomes RP, dependências e economia.
- [Guia do Realm Markets](realm-markets/README.md): receitas, abastecimento, preços por unidade e recolha dos ganhos.
- [Guia do Realm Identity](realm-identity/README.md): emissão, segunda via, autenticidade e apresentação entre jogadores.
- [Guia do Crown Ledger](crown-ledger/README.md): receita do cofre, permissões, impostos, salários e ciclos.
- [Guia do Contracts](contracts-bounties/README.md) e [API de integração](contracts-bounties/TREASURY_API.md).
- [Guia do Realm Council](realm-council/README.md).
- [Plano dos próximos mods](PLANO-DOS-MODS.md).
- [Galerias para CurseForge](media/README.md).

Cada mod funciona independentemente nas suas funcionalidades base. O Crown Ledger usa esmeraldas sem Chronicles RP. Com **Chronicles RP 0.5.4**, pode usar a carteira digital e a reputação existentes. Com Realm Council, só o governante de um mandato ativo e jurado pode gerir o tesouro; sem ele, um operador designa um responsável. Com Contracts, o governante publica contratos pagos pelo reino.

O [Chronicles RP 0.5.4](chronicles-rp/README.md) mantém os perfis e a economia e acrescenta os nomes públicos de personagem. Substitui o JAR anterior no servidor e em todos os clientes; o protocolo mantém a versão 7 introduzida na 0.5.3. Não mistures a 0.5.2 com estas versões. Origins 0.3.2 e Jupiter 2.3.7 são necessários; Origins: Classes 0.3 é opcional.

O Realm Notifications mostra os eventos dos seis mods atualizados e deixa o chat como alternativa para clientes sem o canal opcional. A tecla **N** abre o diário e as preferências; os menus de contratos, lojas e governação continuam a usar os seus blocos. O histórico é privado e pertence à sessão.

O Realm Rewards paga conquistas novas uma única vez por jogador. Um operador configura os valores com `/rewards`. A carteira do Chronicles recebe o pagamento; sem carteira disponível, a quantia fica pendente. Receitas e avanços internos começam com valor zero. Conquistas anteriores à instalação não pagam retroativamente.

O Realm Markets usa esmeraldas em modo independente ou a carteira digital do Chronicles nas bancas novas quando esse mod está instalado. Os ganhos ficam guardados na banca até o proprietário os recolher. As vendas podem pagar uma percentagem própria definida pelo governante no Crown Ledger; o comprador paga o preço anunciado e o vendedor recolhe o líquido. A taxa começa em 0%, e os ganhos anteriores mantêm-se. Esta versão vende itens sem dados personalizados.

A compilação e os testes nativos/visuais são executados pelo GitHub Actions, com proveniência e checksums em `mod-pronto/*.build.json`. Cada projeto documenta em `VALIDACAO.md` os resultados efetivos e os respetivos limites. Os testes anteriores do Crown Ledger, Realm Markets e Realm Identity não incluíram o runtime do Chronicles; a atualização 0.5.3 tem uma validação própria com as dependências reais. O projeto do Chronicles foi importado para esta atualização a pedido do utilizador; o JAR original 0.5.2 continua apenas como referência local.

A [validação conjunta das notificações](https://github.com/ROCKABITS/Mods/actions/runs/39009308013) aprovou os sete JARs novos, **275 testes unitários**, as suites nativas individuais e os 15 GameTests do Notifications com os companheiros novos, Origins e Jupiter. O HUD, as preferências e o diário passaram no cliente Minecraft real. Consulta o [âmbito e os limites](realm-notifications/VALIDACAO.md), incluindo quais as operações financeiras exercitadas.
