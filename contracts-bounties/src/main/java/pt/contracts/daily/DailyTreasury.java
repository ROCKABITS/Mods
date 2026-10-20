package pt.contracts.daily;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Optional funded public tax. Neither the President nor an administrator is a recipient. */
public final class DailyTreasury {
    private DailyTreasury(){}
    public static boolean installed(){return ModList.get().isLoaded("kingdomtreasury");}
    private static Class<?> api()throws ReflectiveOperationException{Class<?> c=Class.forName("pt.crownledger.api.CrownDailyApi");if(c.getField("VERSION").getInt(null)!=1)throw new IllegalStateException("daily.contracts.treasury_error");return c;}
    public static boolean available(){if(!installed())return false;try{api();return true;}catch(ReflectiveOperationException|RuntimeException|LinkageError e){return false;}}
    private static void call(String method,MinecraftServer server,DailyLedger.Reward reward){
        if(reward.tax==0)return;
        try{long units=reward.currency.equals("EMERALD")?reward.tax/100:reward.tax;api().getMethod(method,MinecraftServer.class,java.util.UUID.class,java.util.UUID.class,String.class,long.class).invoke(null,server,reward.id,reward.player,reward.currency,units);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError e){throw new IllegalStateException("daily.contracts.treasury_error",e);}
    }
    public static void preflight(MinecraftServer server,DailyLedger.Reward reward){call("preflight",server,reward);}
    public static void credit(MinecraftServer server,DailyLedger.Reward reward){call("credit",server,reward);}
}
