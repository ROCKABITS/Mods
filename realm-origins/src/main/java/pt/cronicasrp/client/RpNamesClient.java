package pt.cronicasrp.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.RpNames;
import pt.cronicasrp.network.RpNamePackets;

/** A session-scoped UUID -> public RP-name cache, independent of private local profile state. */
@EventBusSubscriber(modid = CronicasRP.MOD_ID, value = Dist.CLIENT)
public final class RpNamesClient {
    private RpNamesClient() {}

    public static String publicName(UUID playerId) { return RpNames.clientName(playerId); }

    public static void accept(RpNamePackets.Update packet) {
        clientThread();
        RpNames.updateClientName(packet.playerId(), packet.name());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getUUID().equals(packet.playerId())) refresh(minecraft.player);
        if (minecraft.level != null) {
            Player player = minecraft.level.getPlayerByUUID(packet.playerId());
            if (player != null && player != minecraft.player) refresh(player);
        }
    }

    public static void reset() {
        clientThread();
        RpNames.clearClientNames();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) refresh(minecraft.player);
        if (minecraft.level != null)
            for (Player player : minecraft.level.players()) if (player != minecraft.player) refresh(player);
    }

    public static void refresh(Player player) {
        clientThread();
        if (player.level().isClientSide()) player.refreshDisplayName();
    }

    @SubscribeEvent public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!event.getEntity().level().isClientSide()) return;
        String name = publicName(event.getEntity().getUUID());
        if (!name.isEmpty()) event.setDisplayname(RpNames.nameComponent(event.getEntity(), name));
    }

    @SubscribeEvent public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Player player) refresh(player);
    }

    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

    private static void clientThread() {
        if (!Minecraft.getInstance().isSameThread())
            throw new IllegalStateException("Public character names require the client thread.");
    }
}
