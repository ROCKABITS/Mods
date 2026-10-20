package pt.contracts.daily;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Optional Realm Admin provider; an installed but unbound provider fails closed. */
public final class DailyPermissions {
    public static final int VERSION=1;
    public interface Provider { boolean canAdminister(ServerPlayer player); }
    private static volatile Provider provider;
    private DailyPermissions(){}
    public static synchronized void register(Provider value){if(provider!=null)throw new IllegalStateException("Daily quest permissions already registered");provider=Objects.requireNonNull(value);}
    public static synchronized void unregister(Provider value){if(value==null||provider!=value)throw new IllegalStateException("Only the registered permission provider may unregister");provider=null;}
    public static boolean administrator(ServerPlayer p){if(provider!=null){try{return provider.canAdminister(p);}catch(RuntimeException|LinkageError e){return false;}}return !ModList.get().isLoaded("realmadmin")&&p.hasPermissions(2);}
    public static void requireAdmin(ServerPlayer p){if(!administrator(p))throw new IllegalStateException("daily.contracts.forbidden");}
    public static boolean governor(ServerPlayer p){
        if(administrator(p))return true;
        try{var governor=DailyCouncil.governor(p.getServer(),Math.max(DailyData.get(p.getServer()).ledger.highWater,System.currentTimeMillis()));return governor!=null&&governor.id().equals(p.getUUID());}catch(RuntimeException|LinkageError failed){return false;}
    }
    public static void requireGovernor(ServerPlayer p){if(!governor(p))throw new IllegalStateException("daily.contracts.forbidden");}
}
