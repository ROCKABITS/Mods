package pt.realmvillagereconomy.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.realmvillagereconomy.service.TradingService;

public final class TradeNetwork {
    private TradeNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TradePackets.Action.TYPE, TradePackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                context.enqueueWork(() -> TradingService.action(player, packet.operation(), packet.json()));
        });
        registrar.playToClient(TradePackets.Snapshot.TYPE, TradePackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> pt.realmvillagereconomy.client.TradingScreen.receive(packet.json())));
    }
    public static void send(ServerPlayer player, JsonObject snapshot) {
        String json = snapshot.toString();
        if (json.length() > 262144) throw new IllegalStateException("error.realmvillagereconomy.invalid_request");
        PacketDistributor.sendToPlayer(player, new TradePackets.Snapshot(json));
    }
    public static void request(String operation, JsonObject payload) {
        PacketDistributor.sendToServer(new TradePackets.Action(operation, payload.toString()));
    }
}
