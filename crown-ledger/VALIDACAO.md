# Validação — Crown Ledger

## Versão 0.2.0 — execução conjunta pendente

A versão 0.2.0 altera os registos persistentes e acrescenta impostos de mercado. A compilação Minecraft, os resultados e a publicação desta versão aguardam a execução do workflow conjunto; os resultados 0.1.1 abaixo são históricos e não aprovam a integração nova.

A publicação exige:

- Compilar primeiro os JARs de produção **Crown Ledger 0.2.0 e Realm Markets 0.2.0**, com todos os testes unitários aprovados.
- Executar os testes nativos de cada mod sozinho e novamente com o JAR novo do outro mod, compilado no mesmo commit, mais os JARs publicados e verificados de Contracts 0.3.1, Realm Council 0.2.1 e Realm Identity 0.1.0.
- Concluir as verificações reais da compra tributada, entrada exata do imposto no tesouro, repetição sem nova tributação e retenção da reserva se o tesouro exceder o limite de saldo. Um marcador de conclusão destas verificações é obrigatório no registo de integração; carregar apenas o outro mod não satisfaz esta condição.
- Aprovar os dois clientes Minecraft, incluindo a regra de imposto no cofre e a decomposição total/imposto/ganho líquido no mercado.
- Verificar os hashes dos dois JARs e publicar as duas famílias de ficheiros numa única operação do bot, apenas se o commit de origem ainda for o atual de `main`.

O imposto inicial de mercado é zero. O formato anterior migra para o formato 2, conservando regras e fundos; faz uma cópia de segurança **antes de atualizar**. Para regressar a 0.1.x depois da migração, restaura a cópia completa anterior, incluindo os registos de mercado e perfis. Não basta repor o JAR antigo.

O imposto em trânsito fica no registo do mercado até o tesouro o recolher, normalmente em até um segundo de servidor ativo ou ao atualizar o cofre. Inventários, registos e perfis mantêm a persistência normal do Minecraft, sem uma transação de disco única durante uma falha abrupta.

## Histórico aprovado — 0.1.1

[Execução aprovada](https://github.com/ROCKABITS/Mods/actions/runs/38779566016), código `47fc8659feca1cf652869fba5f09c80ada9303f3`.

- **42 testes unitários** aprovados, zero falhas/erros/ignorados: tesouro, regras, reservas de salários, elegibilidade, overflow, histórico, imposto fixo sem dívida, moedas separadas e persistência lógica. Os 12 testes do relógio verificam minutos completos, reconexões, tempo offline, limite do ciclo e ausência de atividade retroativa durante salários pendentes.
- **15 testes nativos independentes** aprovados em Minecraft 1.21.1/NeoForge 21.1.256.
- **15 testes nativos com os JARs reais de Contracts 0.3.1 e Realm Council 0.2.1** aprovados. A mesma bateria verifica os caminhos opcionais presentes ou ausentes. Cobre mandato jurado/ativo, sucessão, autorização de menus já abertos, 20 → 19 + 1, reembolsos, recolha de materiais públicos, repetição de operações, contratos abertos antigos e reversão de publicações inválidas.
- Cliente Minecraft real aprovado: cofre com **264 faces**, quatro direções, texturas presentes e três capturas de **1600 × 900**.
- A interface de produção foi exercitada pelos seus botões: donativos nas duas moedas, conversão exata de euros para cêntimos, rejeição de esmeraldas fracionadas, troca explícita da moeda fiscal com campos repostos a zero, créditos antigos preservados, recuperação após ausência do Chronicles e moeda dos contratos independente da moeda fiscal. Estes pedidos foram capturados localmente pelo teste; não simulam uma economia Chronicles real.
- JAR sem fixtures de GameTests/captura. Downloads públicos do JAR/ZIP verificados contra os checksums do CI.

[JAR](../mod-pronto/crown-ledger-0.1.1.jar) · [ZIP](../mod-pronto/crown-ledger-0.1.1.zip) · [Proveniência](../mod-pronto/crown-ledger-0.1.1.build.json) · [Imagens](../media/crown-ledger/0.1.1/README.md).

SHA-256 do JAR: `e82795f13e9f912f6729cebbad7a31dc242735b7a9e6396d6cbfb7eac02736aa`.

## Limites da validação

O runtime completo de **Chronicles RP/Origins/Jupiter** não foi iniciado nesta execução. O adaptador usa a API do JAR original fornecido, mas os testes com esmeraldas e os pedidos da interface não comprovam a sincronização de perfis com esses mods instalados. Testes manuais com dois clientes e um mundo de produção reiniciado continuam recomendados antes de abrir o servidor ao público.

O ambiente cloud local continua a receber HTTP 403 ao obter NeoForm no Maven NeoForge, antes da compilação Java. A compilação e os testes Minecraft acima foram feitos no GitHub Actions; não provam que a rede desta sessão cloud permita esses artefactos. Os testes independentes do domínio passaram também nesta máquina, com Java 21.

As instruções de instalação/arranque foram guardadas na configuração do ambiente. Guardar o rascunho não as aplica nem publica o ambiente. Não foram desativadas verificações TLS ou de checksums.
