package pt.crownledger;

import net.neoforged.neoforge.common.ModConfigSpec;
import pt.crownledger.domain.Loans;
import pt.crownledger.domain.Treasury.Currency;

/** Administrator bounds. Offered contracts keep their original signed terms. */
public final class LoanConfig {
    public static ModConfigSpec.BooleanValue ENABLED;
    public static ModConfigSpec.LongValue MAX_CENTS,MAX_EMERALDS;
    public static ModConfigSpec.IntValue MAX_INTEREST,MAX_INSTALLMENTS,MAX_INTERVAL_DAYS,OFFER_HOURS,REQUEST_DAYS,COOLDOWN_SECONDS;
    public static void define(ModConfigSpec.Builder b){
        b.push("loans").comment("Public loan desk. Interest is fixed, never compounded. Dates are real UTC time.");
        ENABLED=b.define("enabled",true);MAX_CENTS=b.defineInRange("maximumPrincipalCents",100_000L,1,Loans.MAX_PRINCIPAL);MAX_EMERALDS=b.defineInRange("maximumPrincipalEmeralds",256L,1,Loans.MAX_PRINCIPAL);
        MAX_INTEREST=b.defineInRange("maximumInterestBasisPoints",2500,0,10000);MAX_INSTALLMENTS=b.defineInRange("maximumInstallments",12,1,36);MAX_INTERVAL_DAYS=b.defineInRange("maximumIntervalDays",30,1,365);
        OFFER_HOURS=b.defineInRange("offerExpiresAfterHours",48,1,720);REQUEST_DAYS=b.defineInRange("requestExpiresAfterDays",7,1,30);COOLDOWN_SECONDS=b.defineInRange("requestCooldownSeconds",60,0,86400);b.pop();
    }
    public static Loans.Limits limits(Currency currency){return new Loans.Limits(currency==Currency.CHRONICLES?MAX_CENTS.get():MAX_EMERALDS.get(),MAX_INTEREST.get(),MAX_INSTALLMENTS.get(),MAX_INTERVAL_DAYS.get(),OFFER_HOURS.get()*3_600_000L,REQUEST_DAYS.get()*Loans.DAY,COOLDOWN_SECONDS.get()*1000L);}
    private LoanConfig(){}
}
