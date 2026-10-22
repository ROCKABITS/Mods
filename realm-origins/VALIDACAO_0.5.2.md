# Validação — Chronicles RP 0.5.2

Ambiente: Minecraft Java 1.21.1, NeoForge 21.1.256, Java 21, Origins (NeoForge) 0.3.2 e Jupiter 2.3.7. Origins: Classes 0.3 presente e ausente.

## Percurso gráfico

Duas sessões completas no cliente nativo, com servidor integrado e mundos de teste novos: uma com Classes e outra sem o add-on.

- O primeiro ecrã é o pergaminho RP, enquanto as escolhas do Origins ainda estão por concluir. O teste espera neste ecrã e verifica que o seletor nativo não aparece sozinho.
- O botão da linhagem inicia a escolha nativa. Com Classes, a raça vem antes da classe; sem Classes, basta a raça.
- O fim das escolhas regressa ao mesmo objeto de formulário. Nome e idade preenchidos antes do seletor são conservados. A continuação está bloqueada enquanto houver escolhas por concluir.
- Foram submetidos os pacotes reais para criar uma personagem Avian de 128 anos, com classe Warrior quando disponível, assinatura desenhada e fortuna inicial guardada uma vez.
- Foram verificados o livro de RP, os textos em inglês e português, a consulta de poderes e o regresso ao formulário, e uma mudança de origem para Shulk sem alterações dos outros dados ou do dinheiro.
- Interfaces inspecionadas em 1100×700 e 854×480. As imagens em `preview` correspondem às sessões nativas desta versão.

## Testes e distribuição

- 56 testes JUnit: zero falhas, erros ou testes ignorados.
- 17 GameTests nativos com Classes e os mesmos 17 sem Classes, todos aprovados. Incluem ordem das escolhas, exclusão de escolhas concluídas, perfil existente, proteção contra raça falsificada, datapacks, inventário, economia e a correção de claustrofobia da versão 0.5.1.
- `clean build` concluído. O JAR foi aberto, todas as entradas verificadas e a versão 0.5.2 confirmada.
- As três traduções contêm as mesmas 250 chaves. A distribuição inclui `OriginsCreationFlow`, o recurso de compatibilidade e os créditos/licença; exclui o automatismo de cliente, GameTests, origens de teste e dependências externas.
- O formato dos perfis, o saldo guardado e o protocolo de rede 6 mantêm-se. As escolhas e a elegibilidade continuam a ser confirmadas pelo Origins no servidor.

## Âmbito

A verificação gráfica usa o cliente com servidor integrado; os GameTests usam o servidor nativo. Esta atualização foi verificada com as versões indicadas, sem certificar todos os poderes, add-ons ou combinações de modpacks. O ambiente gráfico de teste usa renderização por software e não tem dispositivo de áudio.

JAR: `cronicasrp-0.5.2.jar`.

SHA-256: `35262eed644261a8a896c364bed02c300a65632652c5fca586a8517c2c144690`.

Resultados: `qa-results/0.5.2-checks.json`. Os relatórios das versões anteriores são mantidos separadamente.
