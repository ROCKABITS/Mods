package pt.realmnotifications.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NotificationNetwork {
    private NotificationNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        // No mandatory handshake, no client-to-server channel and no gameplay dependency.
        event.registrar("1").optional().playToClient(NotificationPackets.Show.TYPE, NotificationPackets.Show.CODEC,
                (packet, context) -> context.enqueueWork(() -> pt.realmnotifications.client.NotificationsClient.receive(packet.kind(), packet.title(), packet.body())));
    }
    public static boolean supported(ServerPlayer player) {
        return player != null && player.connection != null && player.connection.isAcceptingMessages()
                && player.connection.hasChannel(NotificationPackets.Show.TYPE.id());
    }
    public static void send(ServerPlayer player, NotificationPackets.Show packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
