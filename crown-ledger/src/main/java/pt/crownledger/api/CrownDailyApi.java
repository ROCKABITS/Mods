package pt.crownledger.api;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Idempotent public share of a server-reserved daily quest reward, never a personal payment. */
public final class CrownDailyApi {
    public static final int VERSION=1;
    private CrownDailyApi(){}
    public static void preflight(MinecraftServer server,UUID id,UUID recipient,String currency,long amount){
        if(amount<1)throw new IllegalArgumentException("Positive daily tax required");
        CrownIncomeApi.preflight(server,id,currency,amount,recipient,"contracts.daily-tax");
    }
    public static boolean credit(MinecraftServer server,UUID id,UUID recipient,String currency,long amount){
        if(amount<1)throw new IllegalArgumentException("Positive daily tax required");
        return CrownIncomeApi.credit(server,id,currency,amount,recipient,"contracts.daily-tax");
    }
}
