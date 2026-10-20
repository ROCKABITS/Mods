package pt.realmidentity.compat;
import java.util.*;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
/** Live status is consulted after the identity document's normal access validation. */
public final class MarriageBridge {
    private MarriageBridge(){}
    public static void addTo(Map<String,Object> view,MinecraftServer server,UUID owner,UUID viewer){
        if(!ModList.get().isLoaded("realmmarriage"))return;
        try{Class<?> api=Class.forName("pt.realmmarriage.api.MarriageApi");if(api.getField("VERSION").getInt(null)!=1)throw new IllegalStateException();String json=(String)api.getMethod("statusJson",MinecraftServer.class,UUID.class,UUID.class).invoke(null,server,owner,viewer);if(json==null||json.length()>2048)throw new IllegalStateException();JsonObject result=JsonParser.parseString(json).getAsJsonObject();if(!Set.of("SINGLE","ENGAGED","MARRIED","DIVORCED").contains(result.get("status").getAsString()))throw new IllegalStateException();view.put("marriage",result);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){view.put("marriage",Map.of("status","UNAVAILABLE"));}
    }
}
