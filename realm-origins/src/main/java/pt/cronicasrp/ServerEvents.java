package pt.cronicasrp;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import pt.cronicasrp.network.ProfileNetwork;
import pt.cronicasrp.network.ProfilePackets;

public final class ServerEvents {
    private ServerEvents() {}
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ProfileNetwork.send(player,
                player.getData(ModAttachments.PROFILE).created() ? ProfilePackets.SYNC : ProfilePackets.CREATE, "");
    }
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { synchronize(event); }
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) { synchronize(event); }
    private static void synchronize(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ProfileNetwork.send(player,
                player.getData(ModAttachments.PROFILE).created() ? ProfilePackets.SYNC : ProfilePackets.CREATE, "");
    }
}
