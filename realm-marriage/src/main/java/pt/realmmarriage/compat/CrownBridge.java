package pt.realmmarriage.compat;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
public final class CrownBridge {
    private CrownBridge(){}
    public static boolean loaded(){return ModList.get().isLoaded("kingdomtreasury");}
    public static void transfer(MinecraftServer server,UUID id,UUID payer,long amount){if(amount==0)return;try{Class<?> api=Class.forName("pt.crownledger.api.CrownMarriageApi");if(api.getField("VERSION").getInt(null)!=1)throw new IllegalStateException();api.getMethod("credit",MinecraftServer.class,UUID.class,UUID.class,long.class).invoke(null,server,id,payer,amount);}catch(ReflectiveOperationException|LinkageError e){throw new IllegalStateException("marriage.error.crown",e);}}
    public static void preflight(MinecraftServer server,UUID id,UUID payer,long amount){if(amount==0)return;try{Class<?> api=Class.forName("pt.crownledger.api.CrownMarriageApi");if(api.getField("VERSION").getInt(null)!=1)throw new IllegalStateException();api.getMethod("preflight",MinecraftServer.class,UUID.class,UUID.class,long.class).invoke(null,server,id,payer,amount);}catch(ReflectiveOperationException|LinkageError e){throw new IllegalStateException("marriage.error.crown",e);}}
}
