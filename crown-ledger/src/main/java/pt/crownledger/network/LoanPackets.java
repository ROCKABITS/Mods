package pt.crownledger.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public final class LoanPackets {
    public record Request(String operation,String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("kingdomtreasury","loan_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.operation,16);b.writeUtf(p.json,4096);},b->new Request(b.readUtf(16),b.readUtf(4096)));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("kingdomtreasury","loan_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,262144),b->new State(b.readUtf(262144)));
        @Override public Type<State> type(){return TYPE;}
    }
    private LoanPackets(){}
}
