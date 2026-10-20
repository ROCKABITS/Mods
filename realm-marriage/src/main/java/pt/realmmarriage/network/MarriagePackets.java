package pt.realmmarriage.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public final class MarriagePackets {
    private MarriagePackets(){}
    public record Action(String operation,String json)implements CustomPacketPayload{
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.parse("realmmarriage:action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.operation,24);b.writeUtf(p.json,8192);},b->new Action(b.readUtf(24),b.readUtf(8192)));
        @Override public Type<Action> type(){return TYPE;}
    }
    public record View(String kind,String json)implements CustomPacketPayload{
        public static final Type<View> TYPE=new Type<>(ResourceLocation.parse("realmmarriage:view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,View> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.kind,16);b.writeUtf(p.json,65536);},b->new View(b.readUtf(16),b.readUtf(65536)));
        @Override public Type<View> type(){return TYPE;}
    }
}
