package pt.realmfitness;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Direction-checked Forge channel; only the server evaluates inputs and rewards. */
public final class FitnessNetwork {
    private static final String VERSION="forge-1.20.1-4";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("realmfitness","training"),()->VERSION,VERSION::equals,VERSION::equals);
    public record Request(String operation,String json) {}
    public record Snapshot(String json) {}
    private FitnessNetwork() {}
    public static void register(){
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{b.writeUtf(p.operation(),24);b.writeUtf(p.json(),16384);})
            .decoder(b->new Request(b.readUtf(24),b.readUtf(16384)))
            .consumerMainThread((p,c)->{ServerPlayer sender=c.get().getSender();if(sender!=null)FitnessService.request(sender,p.operation(),p.json());}).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->b.writeUtf(p.json(),196608))
            .decoder(b->new Snapshot(b.readUtf(196608)))
            .consumerMainThread((p,c)->pt.realmfitness.client.FitnessClient.receive(p.json())).add();
    }
    public static void request(String operation,Object data){CHANNEL.sendToServer(new Request(operation,pt.realmfitness.domain.FitnessLedger.JSON.toJson(data)));}
    public static void send(ServerPlayer player,Object data){if(player.connection!=null)CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(pt.realmfitness.domain.FitnessLedger.JSON.toJson(data)));}
}
