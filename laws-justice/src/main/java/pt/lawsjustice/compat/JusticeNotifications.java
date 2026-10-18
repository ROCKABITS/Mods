package pt.lawsjustice.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Optional notifications are sent after custody decisions commit. */
public final class JusticeNotifications {
    private JusticeNotifications(){}
    public static void send(ServerPlayer player,String key) {
        Component title=Component.translatable("lawsjustice.notification.title"),body=Component.translatable(key);
        if(ModList.get().isLoaded("realmnotifications"))try {
            Class<?> api=Class.forName("pt.realmnotifications.api.NotificationsApi");
            if(api.getField("VERSION").getInt(null)==1&&Boolean.TRUE.equals(api.getMethod("send",ServerPlayer.class,String.class,Component.class,Component.class)
                    .invoke(null,player,"info",title,body)))return;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
        player.displayClientMessage(title.copy().append(": ").append(body),false);
    }
}
