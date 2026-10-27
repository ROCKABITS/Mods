package pt.realmprogression.api;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Realm Admin can register a server authority provider. The President has no implicit administrator rights. */
public final class ProgressionPermissions {
    public interface Provider {boolean canAdminister(ServerPlayer player);}
    private static volatile Provider provider;
    public static synchronized void register(Provider next){if(provider!=null)throw new IllegalStateException("Provider already registered");provider=Objects.requireNonNull(next);}
    public static boolean canAdminister(ServerPlayer player){
        if(player==null||!player.getServer().isSameThread())return false;
        if(provider!=null)try{return provider.canAdminister(player);}catch(RuntimeException|LinkageError failure){return false;}
        return !ModList.get().isLoaded("realmadmin")&&player.hasPermissions(2);
    }
    private ProgressionPermissions(){}
}
