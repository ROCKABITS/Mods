package pt.lawsjustice.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.lawsjustice.service.JusticeService;

public final class JusticeNetwork {
    private JusticeNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(JusticePackets.Action.TYPE, JusticePackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                context.enqueueWork(() -> JusticeService.handle(player, packet.operation(), packet.json()));
        });
        registrar.playToClient(JusticePackets.Snapshot.TYPE, JusticePackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> pt.lawsjustice.client.JusticeScreen.receive(packet.json())));
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
