package pt.realmrewards.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Optional presentation only: a notification failure must never change a completed payment. */
public final class NotificationsBridge {
    private NotificationsBridge() {}

    /** False preserves the caller's ordinary message when either mod or client support is absent. */
    public static boolean send(ServerPlayer player, String kind, Component message) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread() || message == null) return false;
        try {
            Class<?> api = Class.forName("pt.realmnotifications.api.NotificationsApi");
            if (api.getField("VERSION").getInt(null) != 1) return false;
            return Boolean.TRUE.equals(api.getMethod("send", ServerPlayer.class, String.class, Component.class)
                    .invoke(null, player, kind, message));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /** The optional HUD and the fallback chat are mutually exclusive and cannot fail the transaction. */
    public static void notify(ServerPlayer player, String kind, Component message) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread() || message == null) return;
        if (send(player, kind, message)) return;
        try { if (player != null && message != null) player.sendSystemMessage(message); }
        catch (RuntimeException | LinkageError disconnected) { /* Financial state was committed before this notice. */ }
    }
}
