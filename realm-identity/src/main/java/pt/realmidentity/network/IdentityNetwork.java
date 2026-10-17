package pt.realmidentity.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.realmidentity.service.IdentityService;

public final class IdentityNetwork {
    private IdentityNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(IdentityPackets.Action.TYPE, IdentityPackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                IdentityService.action(player, packet.operation(), packet.json());
        });
        registrar.playToClient(IdentityPackets.Snapshot.TYPE, IdentityPackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> pt.realmidentity.client.IdentityScreen.receive(packet.json())));
    }
    public static void send(ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new IdentityPackets.Snapshot(json));
    }
    public static void send(String operation, JsonObject payload) {
        PacketDistributor.sendToServer(new IdentityPackets.Action(operation, payload.toString()));
    }
}
