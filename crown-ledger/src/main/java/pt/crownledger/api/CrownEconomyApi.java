package pt.crownledger.api;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import pt.crownledger.domain.IncomeLedger;
/** Receipt-bound service income and zero-value, fully bound economic approvals. */
public final class CrownEconomyApi {
 public static final int VERSION=1;
 private CrownEconomyApi(){}
 private static String tax(String kind){if(!IncomeLedger.ECONOMIC_SERVICES.contains(kind))throw new IllegalArgumentException("Unknown economic service");return "realmeconomy.service-tax:"+kind;}
 public static void preflight(MinecraftServer server,UUID id,UUID payer,long cents,String kind){if(cents<1)throw new IllegalArgumentException("Positive funded tax required");CrownIncomeApi.preflight(server,id,"CHRONICLES",cents,payer,tax(kind));}
 public static boolean credit(MinecraftServer server,UUID id,UUID payer,long cents,String kind){if(cents<1)throw new IllegalArgumentException("Positive funded tax required");return CrownIncomeApi.credit(server,id,"CHRONICLES",cents,payer,tax(kind));}
 public static boolean policy(MinecraftServer server,UUID id,UUID actor,String kind,String previous,String next,long at){String memo="realmeconomy.policy:"+kind+":"+at+":"+previous+":"+next;if(!IncomeLedger.validEconomicPolicy(memo))throw new IllegalArgumentException("Invalid economic audit");return CrownIncomeApi.credit(server,id,"CHRONICLES",0,actor,memo);}
}
