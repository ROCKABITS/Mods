package pt.realmtitles.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.realmtitles.service.TitleService;

public final class TitleNetwork {
    private TitleNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TitlePackets.Action.TYPE, TitlePackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player)
                context.enqueueWork(() -> TitleService.action(player, packet.operation(), packet.json()));
        });
        registrar.playToClient(TitlePackets.Snapshot.TYPE, TitlePackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> pt.realmtitles.client.TitleScreen.receive(packet.json())));
    }
    public static void send(ServerPlayer player, JsonObject snapshot) {
        String json = snapshot.toString();
        if (json.length() > 65536) throw new IllegalStateException("gui.realmtitles.invalid_request");
        PacketDistributor.sendToPlayer(player, new TitlePackets.Snapshot(json));
    }
    public static void request(String operation, JsonObject payload) {
        PacketDistributor.sendToServer(new TitlePackets.Action(operation, payload.toString()));
    }
}
