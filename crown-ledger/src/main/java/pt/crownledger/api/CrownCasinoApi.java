package pt.crownledger.api;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import pt.crownledger.data.TreasuryData;

/** Optional casino-profit taxation. Receipts are shared with the proven durable income API. */
public final class CrownCasinoApi {
    public static final int VERSION=1;
    private CrownCasinoApi(){}
    public static int rate(MinecraftServer server){if(server==null||!server.isSameThread())throw new IllegalStateException("Casino tax requires the server thread");return TreasuryData.get(server).treasury.policy().casinoTaxBasisPoints();}
    public static void preflight(MinecraftServer server,UUID receipt,UUID owner,long amount){CrownIncomeApi.preflight(server,receipt,"CHRONICLES",amount,owner,"realmcasino.profit-tax");}
    public static boolean credit(MinecraftServer server,UUID receipt,UUID owner,long amount){return CrownIncomeApi.credit(server,receipt,"CHRONICLES",amount,owner,"realmcasino.profit-tax");}
}
