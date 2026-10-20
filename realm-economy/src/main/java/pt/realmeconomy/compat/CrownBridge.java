package pt.realmeconomy.compat;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Optional treasury. An installed but broken API blocks taxation instead of silently waiving it. */
public final class CrownBridge {
    private CrownBridge(){}
    public static boolean installed(){return ModList.get().isLoaded("kingdomtreasury");}
    private static Class<?> api(){try{Class<?> api=Class.forName("pt.crownledger.api.CrownVillagerApi");if(api.getField("VERSION").getInt(null)!=1)throw new IllegalStateException();return api;}catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("error.realmeconomy.treasury",error);}}
    public static void preflight(MinecraftServer server,UUID id,UUID payer,long amount){call("preflight",new Class<?>[]{MinecraftServer.class,UUID.class,UUID.class,long.class},server,id,payer,amount);}
    public static void credit(MinecraftServer server,UUID id,UUID payer,long amount){call("credit",new Class<?>[]{MinecraftServer.class,UUID.class,UUID.class,long.class},server,id,payer,amount);}
    public static void policy(MinecraftServer server,UUID id,UUID actor,int rate,long at){call("policy",new Class<?>[]{MinecraftServer.class,UUID.class,UUID.class,int.class,long.class},server,id,actor,rate,at);}
    private static void economics(String name,Class<?>[] signature,Object...args){if(!installed())throw new IllegalStateException("error.realmeconomy.treasury");try{var api=Class.forName("pt.crownledger.api.CrownEconomyApi");if(api.getField("VERSION").getInt(null)!=1)throw new IllegalStateException();api.getMethod(name,signature).invoke(null,args);}catch(ReflectiveOperationException|LinkageError failed){throw new IllegalStateException("error.realmeconomy.treasury",failed);}}
    public static void preflightService(MinecraftServer server,UUID id,UUID payer,long amount,String kind){economics("preflight",new Class<?>[]{MinecraftServer.class,UUID.class,UUID.class,long.class,String.class},server,id,payer,amount,kind);}
    public static void creditService(MinecraftServer server,UUID id,UUID payer,long amount,String kind){economics("credit",new Class<?>[]{MinecraftServer.class,UUID.class,UUID.class,long.class,String.class},server,id,payer,amount,kind);}
    public static void economicPolicy(MinecraftServer server,UUID id,UUID actor,String kind,String old,String next,long at){economics("policy",new Class<?>[]{MinecraftServer.class,UUID.class,UUID.class,String.class,String.class,String.class,long.class},server,id,actor,kind,old,next,at);}
    private static void call(String name,Class<?>[] signature,Object...args){if(!installed())throw new IllegalStateException("error.realmeconomy.treasury");try{api().getMethod(name,signature).invoke(null,args);}catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("error.realmeconomy.treasury",error);}}
}
