package pt.realmrewards.network;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.realmrewards.service.RewardService;

public final class RewardNetwork {
    private RewardNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("1");
        registrar.playToServer(RewardPackets.Action.TYPE,RewardPackets.Action.CODEC,(packet,context)->{
            if (context.player() instanceof ServerPlayer player) RewardService.action(player,packet.operation(),packet.json());
        });
        registrar.playToClient(RewardPackets.Snapshot.TYPE,RewardPackets.Snapshot.CODEC,
                (packet,context)->context.enqueueWork(()->pt.realmrewards.client.RewardScreen.receive(packet.json())));
    }
    public static void send(ServerPlayer player,JsonObject json) { PacketDistributor.sendToPlayer(player,new RewardPackets.Snapshot(json.toString())); }
    public static void request(String operation,JsonObject payload) { PacketDistributor.sendToServer(new RewardPackets.Action(operation,payload.toString())); }
}
