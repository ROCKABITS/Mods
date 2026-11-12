package pt.contracts.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pt.contracts.ContractsService;
/** Exact port handshake; all inventory mutation executes on the server thread. */
public final class ContractsNetwork {
    private static final String VERSION="forge-1.20.1-1";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("contracts","contracts"),()->VERSION,VERSION::equals,VERSION::equals);
    private ContractsNetwork(){}
    public static void register(){
        CHANNEL.messageBuilder(ContractsPackets.Action.class,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder(ContractsPackets.Action::encode).decoder(ContractsPackets.Action::decode)
            .consumerMainThread((p,c)->{ServerPlayer sender=c.get().getSender();if(sender!=null)ContractsService.action(sender,p);}).add();
        CHANNEL.messageBuilder(ContractsPackets.Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ContractsPackets.Snapshot::encode).decoder(ContractsPackets.Snapshot::decode)
            .consumerMainThread((p,c)->pt.contracts.client.ContractsScreen.receive(p.json())).add();
    }
    public static void send(String operation,String id,String title,String item,int quantity,long reward,String currency){CHANNEL.sendToServer(new ContractsPackets.Action(operation,id,title,item,quantity,reward,currency));}
    public static void send(ServerPlayer player,String json){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new ContractsPackets.Snapshot(json));}
}
