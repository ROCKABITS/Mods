package pt.realmmarkets.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.realmmarkets.service.MarketService;

public final class MarketNetwork {
    private MarketNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(MarketPackets.Action.TYPE, MarketPackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                MarketService.action(player, packet.operation(), packet.json());
        });
        registrar.playToClient(MarketPackets.Snapshot.TYPE, MarketPackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> pt.realmmarkets.client.MarketScreen.receive(packet.json())));
    }
    public static void send(ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new MarketPackets.Snapshot(json));
    }
    public static void send(String operation, JsonObject payload) {
        PacketDistributor.sendToServer(new MarketPackets.Action(operation, payload.toString()));
    }
}
