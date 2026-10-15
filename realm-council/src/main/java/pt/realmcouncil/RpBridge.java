package pt.realmcouncil;

import java.lang.reflect.*;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import pt.realmcouncil.domain.CouncilState.Identity;

/** Optional reflection adapter; no Crónicas RP classes are in the classpath. */
public final class RpBridge {
    private static boolean warned;
    private RpBridge(){}
    public static boolean present(){return ModList.get()!=null&&ModList.get().isLoaded("cronicasrp");}
    public static Identity identity(ServerPlayer player){
        var fallback=new Identity(player.getGameProfile().getName(),"",0,false);
        if(!present())return fallback;
        try{
            Object supplier=Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null);
            Object type=((Supplier<?>)supplier).get();
            Object profile=player.getData((AttachmentType<?>)type);
            Class<?> c=profile.getClass();
            if(!(Boolean)c.getMethod("created").invoke(profile))return fallback;
            return new Identity((String)c.getMethod("name").invoke(profile),(String)c.getMethod("race").invoke(profile),(Integer)c.getMethod("age").invoke(profile),true);
        }catch(ReflectiveOperationException|ClassCastException|LinkageError ex){
            if(!warned){RealmCouncil.LOG.warn("Crónicas RP adapter unavailable; using Minecraft identities.",ex);warned=true;}
            return fallback;
        }
    }
}
