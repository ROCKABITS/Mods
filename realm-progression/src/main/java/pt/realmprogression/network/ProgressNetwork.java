package pt.realmprogression.network;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.realmprogression.service.ProgressService;
public final class ProgressNetwork {
    public static void register(RegisterPayloadHandlersEvent e){
        var r=e.registrar("1");
        r.playToServer(ProgressPackets.Action.TYPE,ProgressPackets.Action.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer s)ProgressService.action(s,p.op(),p.json());}));
        r.playToClient(ProgressPackets.Snapshot.TYPE,ProgressPackets.Snapshot.CODEC,(p,c)->c.enqueueWork(()->pt.realmprogression.client.ProgressScreen.receive(p.json())));
        r.playToClient(ProgressPackets.State.TYPE,ProgressPackets.State.CODEC,(p,c)->c.enqueueWork(()->pt.realmprogression.client.ClientProgress.receive(p.json())));
        r.playToClient(ProgressPackets.Unlock.TYPE,ProgressPackets.Unlock.CODEC,(p,c)->c.enqueueWork(()->pt.realmprogression.client.ClientProgress.announce(p.name())));
    }
    public static void state(ServerPlayer p,JsonObject state){PacketDistributor.sendToPlayer(p,new ProgressPackets.State(state.toString()));}
    public static void snapshot(ServerPlayer p,JsonObject state){PacketDistributor.sendToPlayer(p,new ProgressPackets.Snapshot(state.toString()));}
    public static void announce(MinecraftServer s,String name){for(var p:s.getPlayerList().getPlayers())PacketDistributor.sendToPlayer(p,new ProgressPackets.Unlock(name));}
    public static void request(String op,JsonObject body){PacketDistributor.sendToServer(new ProgressPackets.Action(op,body.toString()));}
    private ProgressNetwork(){}
}
