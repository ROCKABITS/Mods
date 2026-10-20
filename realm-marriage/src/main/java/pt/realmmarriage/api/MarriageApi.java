package pt.realmmarriage.api;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import pt.realmmarriage.data.MarriageData;
import pt.realmmarriage.domain.MarriageLedger;
import pt.realmmarriage.domain.MarriageLedger.*;
import pt.realmmarriage.service.MarriageService;

/** Live civil status. Identity callers must establish document access before requesting a view. */
public final class MarriageApi {
    public static final int VERSION=1;
    private MarriageApi(){}
    public static String statusJson(MinecraftServer server,UUID owner,UUID viewer){MarriageData d=MarriageData.get(server);Citizen c=d.ledger.citizens.get(owner);if(c==null)return "{\"status\":\"SINGLE\"}";Union u=d.ledger.current(owner);Map<String,Object> result=new LinkedHashMap<>();result.put("status",d.ledger.status(owner));if(u!=null&&u.phase==Phase.MARRIED&&(c.publicSpouse||owner.equals(viewer))){result.put("spouse",u.spouse(owner).toString());result.put("name",d.ledger.citizen(u.spouse(owner)).name);result.put("marriage",u.id.toString());}return MarriageLedger.JSON.toJson(result);}
    public static String documentJson(ServerPlayer holder,ItemStack stack){return MarriageLedger.JSON.toJson(MarriageService.document(holder,stack));}
}
