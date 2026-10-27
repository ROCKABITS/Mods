package pt.realmprogression.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public final class ProgressPackets {
    public record Action(String op,String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("realmprogression","action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.op,16);b.writeUtf(p.json,2048);},b->new Action(b.readUtf(16),b.readUtf(2048)));
        public Type<Action> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("realmprogression","snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,65536),b->new Snapshot(b.readUtf(65536)));
        public Type<Snapshot> type(){return TYPE;}
    }
    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("realmprogression","state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,131072),b->new State(b.readUtf(131072)));
        public Type<State> type(){return TYPE;}
    }
    public record Unlock(String name) implements CustomPacketPayload {
        public static final Type<Unlock> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("realmprogression","unlock"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Unlock> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.name,100),b->new Unlock(b.readUtf(100)));
        public Type<Unlock> type(){return TYPE;}
    }
}
