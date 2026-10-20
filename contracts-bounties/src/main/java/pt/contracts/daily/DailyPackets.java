package pt.contracts.daily;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class DailyPackets {
    private DailyPackets(){}
    public record Request(String operation,String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.parse("contracts:daily_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.operation,24);b.writeUtf(p.json,16384);},b->new Request(b.readUtf(24),b.readUtf(16384)));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.parse("contracts:daily_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,65536),b->new Snapshot(b.readUtf(65536)));
        @Override public Type<Snapshot> type(){return TYPE;}
    }
    public record Finale(String json) implements CustomPacketPayload {
        public static final Type<Finale> TYPE=new Type<>(ResourceLocation.parse("contracts:daily_finale"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Finale> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,4096),b->new Finale(b.readUtf(4096)));
        @Override public Type<Finale> type(){return TYPE;}
    }
}
