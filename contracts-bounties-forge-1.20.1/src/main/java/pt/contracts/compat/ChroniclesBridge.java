package pt.contracts.compat;
import net.minecraft.server.level.ServerPlayer;
/** Realm Origins currently has no verified Forge 1.20.1 wallet adapter. */
public final class ChroniclesBridge {
    public static final long MAX_CENTS=900_000_000_000_000_000L;
    private ChroniclesBridge(){}
    public static boolean loaded(){return false;}
    public static boolean available(ServerPlayer p){return false;}
    private static IllegalStateException unavailable(){return new IllegalStateException("Realm Origins wallet requires a compatible Forge 1.20.1 port.");}
    public static long balance(ServerPlayer p){throw unavailable();}
    public static void debit(ServerPlayer p,long amount){throw unavailable();}
    public static void credit(ServerPlayer p,long amount){throw unavailable();}
    public static int reputation(ServerPlayer p){throw unavailable();}
    public static void applyProgress(ServerPlayer p,int reputation,int missions){throw unavailable();}
}
