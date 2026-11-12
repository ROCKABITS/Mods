package pt.contracts.network;
import net.minecraft.network.FriendlyByteBuf;
public final class ContractsPackets {
    private ContractsPackets(){}
    public record Action(String operation,String contractId,String title,String item,int quantity,long reward,String currency){
        public static void encode(Action p,FriendlyByteBuf b){b.writeUtf(p.operation,20);b.writeUtf(p.contractId,36);b.writeUtf(p.title,64);b.writeUtf(p.item,128);b.writeVarInt(p.quantity);b.writeVarLong(p.reward);b.writeUtf(p.currency,16);}
        public static Action decode(FriendlyByteBuf b){return new Action(b.readUtf(20),b.readUtf(36),b.readUtf(64),b.readUtf(128),b.readVarInt(),b.readVarLong(),b.readUtf(16));}
    }
    public record Snapshot(String json){
        public static void encode(Snapshot p,FriendlyByteBuf b){b.writeUtf(p.json,196608);}
        public static Snapshot decode(FriendlyByteBuf b){return new Snapshot(b.readUtf(196608));}
    }
}
