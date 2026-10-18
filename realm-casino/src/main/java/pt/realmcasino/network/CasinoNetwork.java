package pt.realmcasino.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.realmcasino.service.CasinoService;

public final class CasinoNetwork {
    private CasinoNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(CasinoPackets.Action.TYPE, CasinoPackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                context.enqueueWork(() -> CasinoService.action(player, packet.operation(), packet.json()));
        });
        registrar.playToClient(CasinoPackets.Snapshot.TYPE, CasinoPackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> pt.realmcasino.client.CasinoScreen.receive(packet.json())));
    }
    public static void send(ServerPlayer player, JsonObject snapshot) {
        String json = snapshot.toString();
        if (json.length() > 65536) throw new IllegalStateException("error.realmcasino.invalid_request");
        PacketDistributor.sendToPlayer(player, new CasinoPackets.Snapshot(json));
    }
    public static void request(String operation, JsonObject payload) {
        PacketDistributor.sendToServer(new CasinoPackets.Action(operation, payload.toString()));
    }
}
