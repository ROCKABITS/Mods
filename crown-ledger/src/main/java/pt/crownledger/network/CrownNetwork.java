package pt.crownledger.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.crownledger.service.CrownService;

public final class CrownNetwork {
    private CrownNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3");
        registrar.playToServer(CrownPackets.Action.TYPE, CrownPackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                CrownService.action(player, packet.operation(), packet.json());
        });
        registrar.playToClient(CrownPackets.Snapshot.TYPE, CrownPackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> pt.crownledger.client.ClientHooks.open(packet.json())));
    }
    public static void send(ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new CrownPackets.Snapshot(json));
    }
}
