package pt.crownledger.api;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;

/** Funded purchase tax and zero-value policy audit receipts; never pays a governor's wallet. */
public final class CrownVillagerApi {
    public static final int VERSION=1;
    private CrownVillagerApi(){}
    public static void preflight(MinecraftServer server,UUID id,UUID payer,long amount){if(amount<1)throw new IllegalArgumentException("Positive funded tax required");CrownIncomeApi.preflight(server,id,"CHRONICLES",amount,payer,"realmvillagereconomy.purchase-tax");}
    public static boolean credit(MinecraftServer server,UUID id,UUID payer,long amount){if(amount<1)throw new IllegalArgumentException("Positive funded tax required");return CrownIncomeApi.credit(server,id,"CHRONICLES",amount,payer,"realmvillagereconomy.purchase-tax");}
    public static boolean policy(MinecraftServer server,UUID id,UUID actor,int rate,long at){if(rate<0||rate>10000||at<0)throw new IllegalArgumentException("Invalid fiscal approval");return CrownIncomeApi.credit(server,id,"CHRONICLES",0,actor,"realmvillagereconomy.policy:"+rate+":"+at);}
}
