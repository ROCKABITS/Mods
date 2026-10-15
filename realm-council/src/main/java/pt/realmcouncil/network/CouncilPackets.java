package pt.realmcouncil.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import pt.realmcouncil.RealmCouncil;
public final class CouncilPackets {
    public record Action(int action,String election,int round,String choice,String text,String extra) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RealmCouncil.ID,"action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.action);b.writeUtf(p.election,36);b.writeVarInt(p.round);b.writeUtf(p.choice,36);b.writeUtf(p.text,80);b.writeUtf(p.extra,1000);},b->new Action(b.readVarInt(),b.readUtf(36),b.readVarInt(),b.readUtf(36),b.readUtf(80),b.readUtf(1000)));
        @Override public Type<Action> type(){return TYPE;}
    }
    public record State(String json,boolean open,String message) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RealmCouncil.ID,"state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.json,196608);b.writeBoolean(p.open);b.writeUtf(p.message,40);},b->new State(b.readUtf(196608),b.readBoolean(),b.readUtf(40)));
        @Override public Type<State> type(){return TYPE;}
    }
}
