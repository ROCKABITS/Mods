package pt.contracts.daily;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
/** Digital rewards stay pending until a verified Forge wallet adapter is available. */
public final class DailyWallet {
    private DailyWallet(){}
    private static IllegalStateException unavailable(){return new IllegalStateException("daily.contracts.wallet_error");}
    public static CompoundTag current(ServerPlayer p){throw unavailable();}
    public static CompoundTag awarded(ServerPlayer p,DailyLedger.Reward reward){throw unavailable();}
    public static void install(ServerPlayer p,CompoundTag expected,CompoundTag next){throw unavailable();}
    public static void sync(ServerPlayer p){}
    public static String name(ServerPlayer p){return p.getGameProfile().getName();}
}
