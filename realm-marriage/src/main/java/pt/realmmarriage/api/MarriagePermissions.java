package pt.realmmarriage.api;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
public final class MarriagePermissions {
    public static final int VERSION=1;
    public interface Provider{boolean canAdminister(ServerPlayer player);}
    private static volatile Provider provider;
    private MarriagePermissions(){}
    public static synchronized void register(Provider p){if(provider!=null)throw new IllegalStateException();provider=Objects.requireNonNull(p);}
    public static synchronized void unregister(Provider p){if(provider!=p||p==null)throw new IllegalStateException();provider=null;}
    public static boolean admin(ServerPlayer p){if(provider!=null){try{return provider.canAdminister(p);}catch(RuntimeException|LinkageError e){return false;}}return!ModList.get().isLoaded("realmadmin")&&p.hasPermissions(2);}
}
