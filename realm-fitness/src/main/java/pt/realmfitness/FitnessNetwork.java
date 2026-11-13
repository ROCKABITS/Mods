package pt.realmfitness;

import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class FitnessNetwork {
    private FitnessNetwork(){}
    public record Request(String operation,String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.parse("realmfitness:request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.operation,24);b.writeUtf(p.json,16384);},b->new Request(b.readUtf(24),b.readUtf(16384)));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.parse("realmfitness:snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,196608),b->new Snapshot(b.readUtf(196608)));
        @Override public Type<Snapshot> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("4");r.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer sp)FitnessService.request(sp,p.operation,p.json);}));r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,c)->c.enqueueWork(()->pt.realmfitness.client.FitnessClient.receive(p.json)));}
    public static void request(String operation,Object data){PacketDistributor.sendToServer(new Request(operation,pt.realmfitness.domain.FitnessLedger.JSON.toJson(data)));}
    public static void send(ServerPlayer p,Object data){if(p.connection!=null)PacketDistributor.sendToPlayer(p,new Snapshot(pt.realmfitness.domain.FitnessLedger.JSON.toJson(data)));}
}
