package pt.contracts.daily;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class DailyNetwork {
    private DailyNetwork(){}
    public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("3");
        r.playToServer(DailyPackets.Request.TYPE,DailyPackets.Request.CODEC,(p,c)->c.enqueueWork(()->{if(c.player()instanceof ServerPlayer player)DailyService.request(player,p.operation(),p.json());}));
        r.playToClient(DailyPackets.Snapshot.TYPE,DailyPackets.Snapshot.CODEC,(p,c)->c.enqueueWork(()->pt.contracts.client.DailyScreen.receive(p.json())));
        r.playToClient(DailyPackets.Finale.TYPE,DailyPackets.Finale.CODEC,(p,c)->c.enqueueWork(()->pt.contracts.client.DailyCinematic.receive(p.json())));
    }
    public static void request(String operation,com.google.gson.JsonObject json){PacketDistributor.sendToServer(new DailyPackets.Request(operation,json.toString()));}
    public static void snapshot(ServerPlayer p,String json){PacketDistributor.sendToPlayer(p,new DailyPackets.Snapshot(json));}
    public static void finale(ServerPlayer p,String json){PacketDistributor.sendToPlayer(p,new DailyPackets.Finale(json));}
}
