# Realm Rewards — 0.1.1 Beta

Recompensas automáticas por conquistas para **Minecraft 1.21.1, NeoForge 21.1.256+ e Java 21**. Instala o mesmo JAR no servidor e nos clientes.

O servidor descobre os Advancements Vanilla e de outros mods pelo sistema normal do Minecraft. Ao completar uma conquista nova, guarda a conclusão pelo UUID do jogador e pelo identificador da conquista. Só paga uma vez, incluindo depois de morrer, mudar de nome, revogar/reconquistar ou reinstalar o mod com os dados do mundo preservados.

## Valores e administração

Um operador de nível 2 abre **`/rewards`**. O menu permite procurar pelo identificador ou título conhecido do servidor, navegar na lista, definir o valor individual de uma conquista, desativá-la com zero e voltar ao valor padrão. Para títulos traduzidos, a pesquisa por ID funciona em qualquer idioma. No separador de valores padrão, altera os pagamentos por tipo:

| Tipo | Valor inicial |
| --- | --- |
| Conquista normal | 2 € |
| Objetivo | 5 € |
| Desafio | 10 € |
| Receitas e avanços sem apresentação | 0 € |

As receitas e os avanços internos também aparecem na pesquisa; só pagam se lhes atribuíres explicitamente um valor. Os valores aceitam ponto ou vírgula e até duas casas decimais, de 0 a 1 000 000 €. As permissões são verificadas novamente em cada pedido. Não há bloco nem atalho de teclado: o menu é de administração; os jogadores recebem automaticamente.

Os valores novos aplicam-se às próximas conclusões. Uma recompensa já reservada mantém o montante original. Uma conquista concluída com valor zero fica registada e não paga se for revogada/reconquistada depois de aumentar o valor. Conquistas já completas na primeira entrada são registadas sem pagamento retroativo.

## Carteira e pagamentos pendentes

A integração económica usa a carteira do **Chronicles RP 0.5.2–0.5.x**, com os mesmos cêntimos inteiros. Para servidores atualizados, recomenda-se **Chronicles RP 0.5.3**, **Origins (NeoForge) 0.3.2** e **Jupiter 2.3.7**. As dependências do Chronicles são instaladas separadamente.

Sem Chronicles, sem personagem criada ou com a integração indisponível, a quantia fica pendente no registo do Rewards. Quando a carteira fica disponível, o servidor tenta pagar automaticamente a cada cinco segundos e na entrada. Se atingir o limite de saldo, paga apenas o que couber e conserva o restante. Não edita perfis offline nem cria uma moeda alternativa. Os pagamentos usam mensagens de chat; a integração com o futuro Realm Notifications fica para depois.

Estas recompensas introduzem dinheiro novo na economia; não saem da tesouraria do reino e não são tributadas como vendas ou contratos.

## Persistência e compatibilidade

O ficheiro global **`data/realm_rewards.dat`** guarda regras, conclusões e quantias pendentes, por UUID e ID da conquista, em todas as dimensões. Não o apagues para atualizar: isso perde a proteção contra repetições. Faz cópias do mundo completo, incluindo os avanços Vanilla e os perfis do Chronicles. Um registo inválido é recusado, sem ser substituído por um registo vazio.

A alteração da carteira e do registo ocorre no thread do servidor, com validação prévia e reposição do perfil se falhar a sincronização. A gravação continua a usar a persistência normal do Minecraft; não existe uma transação de disco única entre ficheiros durante uma falha abrupta. Plugins que alterem progressos diretamente sem os eventos normais podem exigir adaptação.

Chronicles é opcional para carregar/configurar o mod, mas é necessário para receber as recompensas na carteira. Os restantes mods do reino são opcionais. O mod não substitui o menu de conquistas e não adiciona títulos ou reputação.

## Desenvolvimento

`./gradlew build` executa os testes unitários. `./gradlew -PrewardsGameTests runGameTestServer` verifica conquistas nativas sem Chronicles. A opção `-PintegrationTests` carrega os JARs de `compat-libs/` e as dependências reais Origins/Jupiter. `-PrewardsVisualTests runClient` verifica o menu no cliente real. Classes de teste e dependências externas são excluídas do JAR.

No ambiente cloud usa `python3 tools/cloud_gradle.py` para preservar proxy e TLS. Os testes de domínio independentes correm com `python3 tools/test_domain.py`. A compilação local pode continuar bloqueada pela política de rede; o GitHub Actions executa a validação Minecraft.

[Changelog](CHANGELOG.md) · [Validação](VALIDACAO.md).
