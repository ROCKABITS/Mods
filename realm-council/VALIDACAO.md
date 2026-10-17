## Atualização 0.2.2 — Realm Notifications

Integração opcional de apresentação; validação desta atualização pendente da execução conjunta do CI. As verificações de versões anteriores indicadas abaixo são históricas e não certificam automaticamente esta compilação.

# Validação — Realm Council 0.2.1

[Execução aprovada no GitHub Actions](https://github.com/ROCKABITS/Mods/actions/runs/38742667375), código `90e2c6abeb74aedca0be26253b7fd819ff5defe2`.

- Compilação e empacotamento do mod aprovados com Java 21, Minecraft 1.21.1 e NeoForge 21.1.256.
- **49 testes unitários aprovados**, sem falhas, erros ou testes ignorados: 33 de eleições, 7 de administração e 9 de recursos/pacotes.
- **24 GameTests obrigatórios aprovados** num servidor Minecraft real.
- Testes verificam interação efetiva com o quadro por jogador sem OP, candidatura e desistência, voto pela urna, juramento pela mesa e decisões do governante.
- Aberturas remotas, o antigo pedido da tecla O, leituras sem sessão, afastamento e blocos destruídos ou substituídos são recusados.
- Os comandos administrativos continuam a funcionar com permissão de operador e ambos os aliases.
- Recursos, receitas, traduções e armazenamento de dados eleitorais continuam validados. O sinal de fecho sem snapshot também tem teste de serialização.
- O JAR de produção foi verificado sem as classes de GameTests ou de pré-visualização.

O atalho foi retirado do registo de teclas e os comandos públicos deixaram de abrir o livro. As respostas a sessões inválidas fecham o menu e não enviam os dados eleitorais. O protocolo passou para **3**, pelo que clientes e servidor devem atualizar juntos para a 0.2.1. O formato dos dados do mundo não mudou.

Esta execução validou o modo independente. A integração com Crónicas RP instalado e um percurso manual num cliente de jogo não foram executados nesta atualização.

[JAR](../mod-pronto/realmcouncil-0.2.1.jar) · [ZIP](../mod-pronto/realmcouncil-0.2.1.zip) · [Proveniência da compilação](../mod-pronto/realmcouncil-0.2.1.build.json).

SHA-256 do JAR: `3e407c391f5e2f919e246d1d889323c57ea40b30e0387e9614ae32e0e9251a0a`.
