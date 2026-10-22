package pt.crownledger.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class GovernancePackets {
    public record Request(String operation,String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("kingdomtreasury","governance_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.operation,16);b.writeUtf(p.json,2048);},b->new Request(b.readUtf(16),b.readUtf(2048)));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("kingdomtreasury","governance_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,98304),b->new State(b.readUtf(98304)));
        @Override public Type<State> type(){return TYPE;}
    }
    private GovernancePackets(){}
}
