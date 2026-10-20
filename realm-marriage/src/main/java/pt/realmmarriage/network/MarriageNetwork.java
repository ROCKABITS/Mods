package pt.realmmarriage.network;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pt.realmmarriage.service.MarriageService;
public final class MarriageNetwork {
    private MarriageNetwork(){}
    public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("1");r.playToServer(MarriagePackets.Action.TYPE,MarriagePackets.Action.CODEC,(p,c)->c.enqueueWork(()->{if(c.player()instanceof ServerPlayer player)MarriageService.request(player,p.operation(),p.json());}));r.playToClient(MarriagePackets.View.TYPE,MarriagePackets.View.CODEC,(p,c)->c.enqueueWork(()->pt.realmmarriage.client.MarriageClient.receive(p.kind(),p.json())));}
    private static void send(ServerPlayer p,String kind,String json){if(p.connection!=null&&p.connection.isAcceptingMessages()&&p.connection.hasChannel(MarriagePackets.View.TYPE.id()))PacketDistributor.sendToPlayer(p,new MarriagePackets.View(kind,json));}
    public static void open(ServerPlayer p,String json){send(p,"open",json);}
    public static void snapshot(ServerPlayer p,String json){send(p,"snapshot",json);}
    public static void scene(ServerPlayer p,String json){send(p,"scene",json);}
    public static void document(ServerPlayer p,String json){send(p,"document",json);}
    public static void changed(ServerPlayer p){send(p,"changed","{}");}
    public static void request(String op,JsonObject json){PacketDistributor.sendToServer(new MarriagePackets.Action(op,json.toString()));}
}
