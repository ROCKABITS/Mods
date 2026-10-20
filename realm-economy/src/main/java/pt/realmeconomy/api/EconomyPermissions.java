package pt.realmeconomy.api;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Server-only Realm Admin extension point. Register during mod setup; never trusts client roles. */
public final class EconomyPermissions {
    public static final int VERSION=1;
    public interface Provider { boolean canAdminister(ServerPlayer player); }
    private static volatile Provider provider;
    private EconomyPermissions(){}
    public static synchronized void register(Provider next){if(provider!=null)throw new IllegalStateException("Economy permission provider already registered");provider=Objects.requireNonNull(next);}
    public static boolean canAdminister(ServerPlayer player){
        Provider p=provider;
        if(p!=null){try{return p.canAdminister(player);}catch(RuntimeException|LinkageError failed){return false;}}
        return !ModList.get().isLoaded("realmadmin")&&player.hasPermissions(2);
    }
    public static boolean providerReady(){return provider!=null;}
}
