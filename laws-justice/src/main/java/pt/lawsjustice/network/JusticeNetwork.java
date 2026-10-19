package pt.lawsjustice.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.lawsjustice.service.JusticeService;

public final class JusticeNetwork {
    private JusticeNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("4");
        registrar.playToServer(JusticePackets.Action.TYPE, JusticePackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                context.enqueueWork(() -> {
                    if(packet.operation().startsWith("court_"))pt.lawsjustice.service.CourtService.handle(player,packet.operation(),packet.json());
                    else if(packet.operation().startsWith("work_"))pt.lawsjustice.service.WorkService.handle(player,packet.operation(),packet.json());
                    else if(packet.operation().startsWith("finance_"))pt.lawsjustice.service.FinanceService.handle(player,packet.operation(),packet.json());
                    else JusticeService.handle(player, packet.operation(), packet.json());
                });
        });
        registrar.playToClient(JusticePackets.Snapshot.TYPE, JusticePackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    try {
                        var snapshot=com.google.gson.JsonParser.parseString(packet.json()).getAsJsonObject();
                        if(!pt.lawsjustice.client.JusticeClientSessions.accepts(snapshot))return;
                        String kind=snapshot.has("kind")?snapshot.get("kind").getAsString():"";
                        if(kind.equals("court")||kind.equals("warrant"))pt.lawsjustice.client.CourtScreen.receive(packet.json());
                        else if(kind.equals("work"))pt.lawsjustice.client.WorkScreen.receive(packet.json());
                        else if(kind.equals("finance"))pt.lawsjustice.client.FinanceScreen.receive(packet.json());
                        else pt.lawsjustice.client.JusticeScreen.receive(packet.json());
                    }catch(RuntimeException malformed){ /* Never open a screen from a malformed document. */ }
                }));
    }
    public static void send(ServerPlayer player, String json) {
        if (json == null || json.length() > 524288) throw new IllegalArgumentException("error.lawsjustice.invalid_request");
        PacketDistributor.sendToPlayer(player, new JusticePackets.Snapshot(json));
    }
    public static void send(ServerPlayer player, JsonObject snapshot) { send(player, snapshot.toString()); }
    public static void request(String operation, JsonObject payload) {
        PacketDistributor.sendToServer(new JusticePackets.Action(operation, payload.toString()));
    }
}
