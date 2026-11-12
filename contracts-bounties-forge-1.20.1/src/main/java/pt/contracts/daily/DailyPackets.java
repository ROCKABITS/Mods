package pt.contracts.daily;
import net.minecraft.network.FriendlyByteBuf;
public final class DailyPackets {
    private DailyPackets(){}
    public record Request(String operation,String json){
        public static void encode(Request p,FriendlyByteBuf b){b.writeUtf(p.operation,24);b.writeUtf(p.json,16384);}
        public static Request decode(FriendlyByteBuf b){return new Request(b.readUtf(24),b.readUtf(16384));}
    }
    public record Snapshot(String json){
        public static void encode(Snapshot p,FriendlyByteBuf b){b.writeUtf(p.json,65536);}
        public static Snapshot decode(FriendlyByteBuf b){return new Snapshot(b.readUtf(65536));}
    }
    public record Finale(String json){
        public static void encode(Finale p,FriendlyByteBuf b){b.writeUtf(p.json,4096);}
        public static Finale decode(FriendlyByteBuf b){return new Finale(b.readUtf(4096));}
    }
}
