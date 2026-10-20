package pt.realmcasino.compat;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Versioned optional API; funded tax receipts are replayable without another treasury credit. */
public final class CrownBridge {
    private CrownBridge(){}
    private static Class<?> api(){if(!ModList.get().isLoaded("kingdomtreasury"))return null;try{Class<?> api=Class.forName("pt.crownledger.api.CrownCasinoApi");if(api.getField("VERSION").getInt(null)!=1)throw failure(null);return api;}catch(ClassNotFoundException oldVersion){return null;}catch(ReflectiveOperationException|LinkageError problem){throw failure(problem);}}
    public static boolean available(){return api()!=null;}
    public static int rate(MinecraftServer server){Class<?> api=api();if(api==null)return 0;try{int rate=(Integer)api.getMethod("rate",MinecraftServer.class).invoke(null,server);if(rate<0||rate>10000)throw failure(null);return rate;}catch(ReflectiveOperationException|LinkageError problem){throw failure(problem);}}
    public static void preflight(MinecraftServer server,UUID receipt,UUID owner,long amount){invoke("preflight",server,receipt,owner,amount);}
    public static void credit(MinecraftServer server,UUID receipt,UUID owner,long amount){invoke("credit",server,receipt,owner,amount);}
    private static void invoke(String name,MinecraftServer server,UUID receipt,UUID owner,long amount){Class<?> api=api();if(api==null)throw failure(null);try{api.getMethod(name,MinecraftServer.class,UUID.class,UUID.class,long.class).invoke(null,server,receipt,owner,amount);}catch(ReflectiveOperationException|LinkageError problem){throw failure(problem);}}
    private static IllegalStateException failure(Throwable cause){return new IllegalStateException("error.realmcasino.treasury_unavailable",cause);}
}
