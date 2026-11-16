package pt.realmfitness;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import pt.realmfitness.domain.FitnessLedger;

/** Bounded legacy Fabric play channels; authoritative scoring stays on the server. */
public final class FitnessNetwork {
    private FitnessNetwork() {}
    public static final ResourceLocation REQUEST=new ResourceLocation("realmfitness","request_v7");
    public static final ResourceLocation SNAPSHOT=new ResourceLocation("realmfitness","snapshot_v7");
    public static void register(){
        ServerPlayNetworking.registerGlobalReceiver(REQUEST,(server,player,handler,buffer,sender)->{
            try{
                String operation=buffer.readUtf(24),json=buffer.readUtf(16384);
                if(buffer.readableBytes()!=0)return;
                server.execute(()->FitnessService.request(player,operation,json));
            }catch(RuntimeException invalid){FitnessMod.LOG.debug("Rejected malformed Realm Gym packet");}
        });
    }
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public static void request(String operation,Object data){
        var buffer=PacketByteBufs.create();buffer.writeUtf(operation,24);buffer.writeUtf(FitnessLedger.JSON.toJson(data),16384);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(REQUEST,buffer);
    }
    public static void send(ServerPlayer player,Object data){
        if(player.connection==null||!ServerPlayNetworking.canSend(player,SNAPSHOT))return;
        var buffer=PacketByteBufs.create();buffer.writeUtf(FitnessLedger.JSON.toJson(data),196608);ServerPlayNetworking.send(player,SNAPSHOT,buffer);
    }
}
