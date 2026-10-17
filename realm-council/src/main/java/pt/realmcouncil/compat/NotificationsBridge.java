package pt.realmcouncil.compat;

import java.lang.reflect.Method;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Optional presentation only. A notification failure can never undo or retry a committed action. */
public final class NotificationsBridge {
    private NotificationsBridge() {}
    private static final Method SEND = resolve();

    private static Method resolve() {
        try {
            Class<?> api = Class.forName("pt.realmnotifications.api.NotificationsApi");
            if (api.getField("VERSION").getInt(null) != 1) return null;
            return api.getMethod("send", ServerPlayer.class, String.class, Component.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    /** True only if this player actually supports the optional notification payload. */
    public static boolean send(ServerPlayer player, String kind, Component message) {
        if (SEND == null || player == null || player.getServer() == null || !player.getServer().isSameThread()) return false;
        try { return Boolean.TRUE.equals(SEND.invoke(null, player, kind, message)); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
    }

    /** Preserve readable feedback for clients without Realm Notifications; never duplicate HUD and chat. */
    public static void notify(ServerPlayer player, String kind, Component message) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) return;
        if (!send(player, kind, message)) {
            try { player.sendSystemMessage(message); }
            catch (RuntimeException | LinkageError disconnected) { /* Presentation must not fail a committed transaction. */ }
        }
    }
}
