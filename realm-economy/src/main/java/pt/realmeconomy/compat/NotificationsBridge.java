package pt.realmeconomy.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Optional delivery only after a durable economic commit. */
public final class NotificationsBridge {
    private NotificationsBridge() {}
    public static void send(ServerPlayer target, String key, long cents) {
        Component heading = Component.translatable("notification.realmeconomy.title");
        Component body = Component.translatable(key, java.math.BigDecimal.valueOf(cents, 2).toPlainString());
        if (ModList.get().isLoaded("realmnotifications")) {
            try {
                Class<?> api = Class.forName("pt.realmnotifications.api.NotificationsApi");
                if (api.getField("VERSION").getInt(null) == 1 && Boolean.TRUE.equals(api.getMethod("send",
                        ServerPlayer.class, String.class, Component.class, Component.class)
                        .invoke(null, target, "info", heading, body))) return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        }
        target.displayClientMessage(heading.copy().append(Component.literal(": ")).append(body), false);
    }
}
