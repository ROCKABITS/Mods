package pt.contracts.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.contracts.ContractsService;

public final class ContractsNetwork {
    private ContractsNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(ContractsPackets.Action.TYPE, ContractsPackets.Action.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) ContractsService.action(player, packet);
        });
        registrar.playToClient(ContractsPackets.Snapshot.TYPE, ContractsPackets.Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> pt.contracts.client.ContractsScreen.receive(packet.json())));
    }
    public static void send(String operation, String id, String title, String item, int quantity, long reward, String currency) {
        PacketDistributor.sendToServer(new ContractsPackets.Action(operation, id, title, item, quantity, reward, currency));
    }
    public static void send(ServerPlayer player, String json) { PacketDistributor.sendToPlayer(player, new ContractsPackets.Snapshot(json)); }
}
