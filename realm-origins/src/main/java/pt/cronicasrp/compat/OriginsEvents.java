package pt.cronicasrp.compat;

import com.iafenvoy.origins.event.GrantOriginEvent;
import com.iafenvoy.origins.event.RevokeOriginEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.ModAttachments;
import pt.cronicasrp.network.ProfileNetwork;
import pt.cronicasrp.network.ProfilePackets;

@EventBusSubscriber(modid = CronicasRP.MOD_ID)
public final class OriginsEvents {
    private OriginsEvents() {}
    @SubscribeEvent public static void granted(GrantOriginEvent event) { changed(event.getEntity()); }
    @SubscribeEvent public static void revoked(RevokeOriginEvent event) { changed(event.getEntity()); }
    private static void changed(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof ServerPlayer player && player.getData(ModAttachments.PROFILE).created())
            ProfileNetwork.send(player, ProfilePackets.SYNC, "");
    }
}

