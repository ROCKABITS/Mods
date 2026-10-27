package pt.realmeconomy.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.realmeconomy.service.TradingService;

public final class TradeNetwork {
    private TradeNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(TradePackets.Action.TYPE, TradePackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                context.enqueueWork(() -> dispatch(player,packet.operation(),packet.json()));
        });
        registrar.playToClient(TradePackets.Snapshot.TYPE, TradePackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> receive(packet.json())));
    }
    private static void dispatch(ServerPlayer player,String operation,String json){if(operation.startsWith("cln"))pt.realmeconomy.colony.ColonyService.action(player,operation,json);else if(operation.startsWith("svc"))pt.realmeconomy.service.Services.action(player,operation,json);else TradingService.action(player,operation,json);}
    private static void receive(String json){if(com.google.gson.JsonParser.parseString(json).getAsJsonObject().has("screen")&&com.google.gson.JsonParser.parseString(json).getAsJsonObject().get("screen").getAsString().equals("colony"))pt.realmeconomy.client.ColonyScreen.receive(json);else if(com.google.gson.JsonParser.parseString(json).getAsJsonObject().has("screen"))pt.realmeconomy.client.ServicesScreen.receive(json);else pt.realmeconomy.client.TradingScreen.receive(json);}
    public static void send(ServerPlayer player, JsonObject snapshot) {
        String json = snapshot.toString();
        if (json.length() > 262144) throw new IllegalStateException("error.realmeconomy.invalid_request");
        PacketDistributor.sendToPlayer(player, new TradePackets.Snapshot(json));
    }
    public static void request(String operation, JsonObject payload) {
        PacketDistributor.sendToServer(new TradePackets.Action(operation, payload.toString()));
    }
}
