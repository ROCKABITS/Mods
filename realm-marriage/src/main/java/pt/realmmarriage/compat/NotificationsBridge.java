package pt.realmmarriage.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Optional notification delivery follows a committed registry update. */
public final class NotificationsBridge {
    private NotificationsBridge() {}
    public static void send(ServerPlayer target, String key, String titleName) {
        Component heading = Component.translatable("marriage.title");
        Component body = Component.translatable(key, Component.literal(titleName));
        if (ModList.get().isLoaded("realmnotifications")) {
            try {
                Class<?> api = Class.forName("pt.realmnotifications.api.NotificationsApi");
                if (api.getField("VERSION").getInt(null) == 1 && Boolean.TRUE.equals(api.getMethod("send",
                        ServerPlayer.class, String.class, Component.class, Component.class)
                        .invoke(null, target, "info", heading, body))) return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Chat remains available when the optional API or client channel is unavailable.
            }
        }
        target.displayClientMessage(heading.copy().append(Component.literal(": ")).append(body), false);
    }
}
