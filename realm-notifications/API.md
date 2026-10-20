# API de notificações — versão 1

Classe: `pt.realmnotifications.api.NotificationsApi`.

```java
public static final int VERSION = 1;
public static boolean send(ServerPlayer player, String kind, Component body);
public static boolean send(ServerPlayer player, String kind, Component title, Component body);
public static int broadcast(MinecraftServer server, String kind, Component body);
```

Chama a API na thread principal do servidor, depois de a ação ter sido concluída. O envio não confirma pagamentos: apenas apresenta o resultado de uma ação que o mod de origem já validou e guardou.

`send` devolve `true` quando o jogador suporta o canal opcional e o aviso foi enviado; devolve `false` quando o aviso não pode ser enviado. Não confirma que o jogador o viu ou que tem os avisos ativos nas suas preferências. `broadcast` devolve o número de envios bem-sucedidos.

Tipos disponíveis: `money`, `reward`, `contract`, `election`, `market`, `salary`, `treasury`, `info` e `warning`. Um tipo desconhecido usa `info`. A variante sem título usa um título traduzido de acordo com a categoria.

Os componentes podem usar texto literal e traduções. A API limita o tamanho e a complexidade e remove interações e estilos recebidos. Não envies saldos privados, biografias, assinaturas ou escolhas de votos para outros jogadores. Para eventos públicos, envia apenas a informação pública do evento.

## Integração opcional

Os mods desta coleção usam reflexão para manter o Realm Notifications opcional. Um exemplo mínimo para a variante de três argumentos:

```java
boolean sent = false;
try {
    Class<?> api = Class.forName("pt.realmnotifications.api.NotificationsApi");
    if (api.getField("VERSION").getInt(null) == 1) {
        sent = Boolean.TRUE.equals(api.getMethod("send", ServerPlayer.class,
            String.class, Component.class).invoke(null, player, "reward", message));
    }
} catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
    // A apresentação não pode fazer falhar uma operação já concluída.
}
if (!sent) player.sendSystemMessage(message);
```

Não alteres o saldo com base no resultado desta chamada. Não repitas uma operação financeira quando uma notificação falhar.

O protocolo só permite avisos do servidor para o cliente. Não existe um pedido do cliente para fabricar avisos ou receber dinheiro. A fila e o histórico são limitados e não se guardam em disco; não constituem um registo financeiro nem uma caixa de correio offline.

Desde a 0.1.1, o cliente pode agrupar rajadas num resumo e descartar cartões antigos, mantendo os detalhes recentes no histórico se estiver ativo. O retorno `true` continua a confirmar apenas o envio do pacote. Não uses o HUD como comprovativo de entrega individual, nem como histórico financeiro.
