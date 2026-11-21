package pt.realmfitness;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Required, direction-checked Forge 52 channel. Inputs are evaluated on the server. */
public final class FitnessNetwork {
    private static final SimpleChannel CHANNEL=ChannelBuilder.named("realmfitness:training").networkProtocolVersion(8).simpleChannel();
    public record Request(String operation,String json) {}
    public record Snapshot(String json) {}
    private FitnessNetwork(){}
    public static void register(){
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder((message,buffer)->{buffer.writeUtf(message.operation(),24);buffer.writeUtf(message.json(),16384);})
            .decoder(buffer->new Request(buffer.readUtf(24),buffer.readUtf(16384)))
            .consumerMainThread((message,context)->{ServerPlayer player=context.getSender();if(player!=null)FitnessService.request(player,message.operation(),message.json());}).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((message,buffer)->buffer.writeUtf(message.json(),196608))
            .decoder(buffer->new Snapshot(buffer.readUtf(196608)))
            .consumerMainThread((message,context)->pt.realmfitness.client.FitnessClient.receive(message.json())).add();
        CHANNEL.build();
    }
    public static void request(String operation,Object data){CHANNEL.send(new Request(operation,pt.realmfitness.domain.FitnessLedger.JSON.toJson(data)),PacketDistributor.SERVER.noArg());}
    public static void send(ServerPlayer player,Object data){if(player.connection!=null&&player.getServer().getPlayerList().getPlayer(player.getUUID())==player)CHANNEL.send(new Snapshot(pt.realmfitness.domain.FitnessLedger.JSON.toJson(data)),PacketDistributor.PLAYER.with(player));}
}
