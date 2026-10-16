# Chronicles RP 0.5.3 — Nomes RP no mundo

- O nome da personagem substitui o username acima da cabeça, na lista TAB, no autor das mensagens do chat normal e na identificação apresentada pelo livro do Chronicles RP.
- Quem ainda não criou personagem, ou tem um nome inválido num perfil antigo, continua a aparecer com o username Minecraft.
- O hover e a inserção de texto com Shift+clique no nome do chat mostram o nome RP; o clique para mensagem privada mantém o destinatário real.
- Os nomes públicos são sincronizados ao alterar o perfil e nos percursos de entrada, reaparecimento, mudança de dimensão e acompanhamento de outros jogadores.
- Esta sincronização partilha apenas UUID e nome público; história, assinatura, saldo e os restantes dados privados não são enviados aos outros jogadores.
- Mantêm-se as regras de visibilidade e a formatação das equipas. O username original não é acrescentado ao nome RP.
- UUID, identidade da conta, skin e alvos dos comandos mantêm-se. Os nomes RP podem repetir-se; usa o username real nos comandos que pedem um jogador.
- Perfis existentes, escolhas do Origins, assinatura desenhada, reputação, missões, medalhas, fortuna inicial e saldos são conservados. O formato de armazenamento dos perfis não muda.
- O protocolo de rede passa a **7**: instala **0.5.3 no servidor e em todos os clientes**, substituindo o JAR anterior.

Para Minecraft **1.21.1**, NeoForge **21.1.256+** e Java **21**. Requer Origins (NeoForge) **0.3.2** e Jupiter **2.3.7**. Origins: Classes (NeoForge) **0.3** continua opcional.

Esta funcionalidade pertence ao Chronicles RP e não depende do Realm Titles. Interfaces de mods externos que consultem diretamente o username podem continuar a mostrá-lo. Consulta `VALIDACAO.md` para os resultados efetivamente verificados nesta versão.
