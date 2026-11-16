package pt.realmfitness;

import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class FitnessNetwork {
    private FitnessNetwork(){}
    public record Request(String operation,String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.parse("realmfitness:request_v7"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.operation,24);b.writeUtf(p.json,16384);},b->new Request(b.readUtf(24),b.readUtf(16384)));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.parse("realmfitness:snapshot_v7"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,196608),b->new Snapshot(b.readUtf(196608)));
        @Override public Type<Snapshot> type(){return TYPE;}
    }
    public static void register(){
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(Request.TYPE,Request.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(Snapshot.TYPE,Snapshot.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(payload,context)->context.server().execute(()->FitnessService.request(context.player(),payload.operation,payload.json)));
    }
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public static void request(String operation,Object data){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new Request(operation,pt.realmfitness.domain.FitnessLedger.JSON.toJson(data)));}
    public static void send(ServerPlayer player,Object data){if(player.connection!=null&&net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,Snapshot.TYPE))net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new Snapshot(pt.realmfitness.domain.FitnessLedger.JSON.toJson(data)));}
}
