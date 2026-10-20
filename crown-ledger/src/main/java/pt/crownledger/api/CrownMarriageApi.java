package pt.crownledger.api;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
/** Receives only the agreed fee already funded by both spouses' durable wallet receipts. */
public final class CrownMarriageApi {
    public static final int VERSION=1;
    private CrownMarriageApi(){}
    public static void preflight(MinecraftServer server,UUID receipt,UUID payer,long amount){if(amount<=0)throw new IllegalArgumentException("Positive marriage fee required");CrownIncomeApi.preflight(server,receipt,"CHRONICLES",amount,payer,"realmmarriage.wedding-fee");}
    public static boolean credit(MinecraftServer server,UUID receipt,UUID payer,long amount){if(amount<=0)throw new IllegalArgumentException("Positive marriage fee required");return CrownIncomeApi.credit(server,receipt,"CHRONICLES",amount,payer,"realmmarriage.wedding-fee");}
}
