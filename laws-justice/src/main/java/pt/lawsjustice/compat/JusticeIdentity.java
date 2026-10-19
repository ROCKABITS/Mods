package pt.lawsjustice.compat;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Inspect only a genuine document the nearby accused is visibly holding. No owner lookup. */
public final class JusticeIdentity {
    private JusticeIdentity(){}
    public static boolean loaded(){return ModList.get().isLoaded("realmidentity");}
    public static void inspect(ServerPlayer judge,ServerPlayer holder){
        if(!loaded())throw error();if(holder==null||!holder.isAlive()||holder.level()!=judge.level()||holder.distanceToSqr(judge)>36)throw error();
        try{var inspect=Class.forName("pt.realmidentity.service.IdentityService").getMethod("inspection",ServerPlayer.class,ItemStack.class);
            for(var stack:java.util.List.of(holder.getMainHandItem(),holder.getOffhandItem())){
                Object result=inspect.invoke(null,holder,stack);if(!(result instanceof Map<?,?> view))continue;
                // Identity's authenticated view supplies the document UUID and live registry record.
                if(!Boolean.TRUE.equals(view.get("authentic"))||!holder.getUUID().toString().equals(String.valueOf(view.get("ownerUuid"))))continue;
                Object profile=view.get("profile");if(profile==null)continue;Object name=profile.getClass().getMethod("name").invoke(profile);
                judge.displayClientMessage(Component.translatable("lawsjustice.message.identity_document",String.valueOf(name),String.valueOf(view.get("serial"))),false);return;
            }
        }catch(InvocationTargetException e){throw new IllegalStateException("lawsjustice.error.identity",e.getCause());}catch(ReflectiveOperationException|LinkageError e){throw new IllegalStateException("lawsjustice.error.identity",e);}
        throw error();
    }
    private static IllegalStateException error(){return new IllegalStateException("lawsjustice.error.identity");}
}
