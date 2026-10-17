package pt.realmnotifications.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import pt.realmnotifications.domain.NotificationRules;
import pt.realmnotifications.network.NotificationNetwork;
import pt.realmnotifications.network.NotificationPackets;

/**
 * Optional presentation API. Call after a successful server-side operation, on the server thread.
 * true means the connected client negotiated this channel and a bounded payload was sent.
 * false means callers should retain their normal chat feedback. Never changes player data.
 */
public final class NotificationsApi {
    public static final int VERSION = 1;
    private NotificationsApi() {}

    public static boolean send(ServerPlayer player, String kind, Component body) {
        String selected = NotificationRules.kind(kind);
        return send(player, selected, Component.translatable("notification.realmnotifications.kind." + selected), body);
    }

    public static boolean send(ServerPlayer player, String kind, Component title, Component body) {
        try {
            if (player == null || player.getServer() == null || !player.getServer().isSameThread() || !NotificationNetwork.supported(player)) return false;
            NotificationNetwork.send(player, new NotificationPackets.Show(kind, title, body));
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            // An optional visual feature must never break a transaction or suppress its fallback.
            return false;
        }
    }

    /** Number of connected clients which accepted the notification; does not send chat itself. */
    public static int broadcast(MinecraftServer server, String kind, Component body) {
        if (server == null || !server.isSameThread()) return 0;
        int sent = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) if (send(player, kind, body)) sent++;
        return sent;
    }
}
