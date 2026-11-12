package pt.contracts.daily;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
public final class DailyNetwork {
    private static final String VERSION="forge-1.20.1-1";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("contracts","daily"),()->VERSION,VERSION::equals,VERSION::equals);
    private DailyNetwork(){}
    public static void register(){
        CHANNEL.messageBuilder(DailyPackets.Request.class,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder(DailyPackets.Request::encode).decoder(DailyPackets.Request::decode)
            .consumerMainThread((p,c)->{ServerPlayer sender=c.get().getSender();if(sender!=null)DailyService.request(sender,p.operation(),p.json());}).add();
        CHANNEL.messageBuilder(DailyPackets.Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(DailyPackets.Snapshot::encode).decoder(DailyPackets.Snapshot::decode)
            .consumerMainThread((p,c)->pt.contracts.client.DailyScreen.receive(p.json())).add();
        CHANNEL.messageBuilder(DailyPackets.Finale.class,2,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(DailyPackets.Finale::encode).decoder(DailyPackets.Finale::decode)
            .consumerMainThread((p,c)->pt.contracts.client.DailyCinematic.receive(p.json())).add();
    }
    public static boolean connected(ServerPlayer p){return CHANNEL.isRemotePresent(p.connection.connection);}
    public static void request(String operation,com.google.gson.JsonObject json){CHANNEL.sendToServer(new DailyPackets.Request(operation,json.toString()));}
    public static void snapshot(ServerPlayer player,String json){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new DailyPackets.Snapshot(json));}
    public static void finale(ServerPlayer player,String json){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new DailyPackets.Finale(json));}
}
