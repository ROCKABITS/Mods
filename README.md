# Mods de Minecraft

Projetos de ROCKABITS para **Minecraft 1.21.1**, **NeoForge 21.1.256+** e **Java 21**. Instala a mesma versão de cada mod no servidor e nos clientes; substitui os JARs antigos.

| Mod | Download | Funcionalidades |
| --- | --- | --- |
| **Realm Identity 0.1.0** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-identity-0.1.0.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realm-identity-0.1.0.zip) | Documento físico, escrivaninha, assinatura do Chronicles e apresentação com consentimento. |
| **Crown Ledger — Tesouro da Coroa 0.1.1** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/crown-ledger-0.1.1.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/crown-ledger-0.1.1.zip) | Cofre físico, contas públicas, governante eleito, impostos, salários financiados e contratos públicos. |
| **Contracts & Bounties 0.3.1** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/contracts-bounties-0.3.1.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/contracts-bounties-0.3.1.zip) | Três murais 3D, entregas de materiais, recompensas reservadas, impostos e reputação. |
| **Realm Council 0.2.1** | [JAR](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realmcouncil-0.2.1.jar) · [ZIP](https://github.com/ROCKABITS/Mods/raw/refs/heads/main/mod-pronto/realmcouncil-0.2.1.zip) | Eleições, mandatos, juramentos e decisões públicas, através dos blocos. |

O ZIP é uma alternativa ao download direto do JAR. Extrai-o antes de instalar.

- [Guia do Realm Identity](realm-identity/README.md): emissão, segunda via, autenticidade e apresentação entre jogadores.
- [Guia do Crown Ledger](crown-ledger/README.md): receita do cofre, permissões, impostos, salários e ciclos.
- [Guia do Contracts](contracts-bounties/README.md) e [API de integração](contracts-bounties/TREASURY_API.md).
- [Guia do Realm Council](realm-council/README.md).
- [Plano dos próximos mods](PLANO-DOS-MODS.md).
- [Galerias para CurseForge](media/README.md).

Cada mod funciona independentemente nas suas funcionalidades base. O Crown Ledger usa esmeraldas sem Chronicles RP. Com **Chronicles RP 0.5.2**, pode usar a carteira digital e a reputação existentes. Com Realm Council, só o governante de um mandato ativo e jurado pode gerir o tesouro; sem ele, um operador designa um responsável. Com Contracts, o governante publica contratos pagos pelo reino.

A compilação e os testes nativos/visuais são executados pelo GitHub Actions, com proveniência e checksums em `mod-pronto/*.build.json`. O Crown Ledger foi testado com os JARs reais de Contracts e Realm Council. O Realm Identity usa a identidade já existente no Chronicles e oferece identidade básica da conta Minecraft sem esse mod. O runtime completo de Chronicles/Origins/Jupiter continua por validar; o código-fonte e o JAR original fornecidos para análise não são redistribuídos aqui.
