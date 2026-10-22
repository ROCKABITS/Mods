package pt.crownledger;

import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
import pt.crownledger.domain.Governance;
import pt.crownledger.domain.Treasury;

public final class GovernanceConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue VOTING_SECONDS,COOLDOWN_SECONDS,QUORUM,MAX_ACTIVE,DURATION_HOURS;
    private record Settings(ModConfigSpec.BooleanValue enabled,ModConfigSpec.LongValue cents,ModConfigSpec.LongValue emeralds,ModConfigSpec.IntValue strength){}
    private static final Map<Governance.Benefit,Settings> OFFERS=new EnumMap<>(Governance.Benefit.class);
    static {
        var b=new ModConfigSpec.Builder();
        b.comment("Realm Treasury public votes. Prices and durations are server administrator settings.");
        ENABLED=b.define("enabled",true);
        VOTING_SECONDS=b.defineInRange("votingSeconds",180,10,1800);
        COOLDOWN_SECONDS=b.defineInRange("cooldownSeconds",600,0,86400);
        QUORUM=b.defineInRange("minimumParticipationPercent",50,1,100);
        MAX_ACTIVE=b.defineInRange("maximumActiveBenefits",2,1,4);
        DURATION_HOURS=b.defineInRange("benefitDurationHours",24,1,168);
        for(var benefit:Governance.Benefit.values()){
            b.push(benefit.name().toLowerCase(java.util.Locale.ROOT));
            OFFERS.put(benefit,new Settings(b.define("enabled",true),b.defineInRange("priceCents",200_000L,1,1_000_000_000L),
                    b.defineInRange("priceEmeralds",400L,1,1_000_000_000L),b.defineInRange("strengthPercent",20,1,50)));
            b.pop();
        }
        SPEC=b.build();
    }
    public static Governance.Offer offer(Governance.Benefit kind,Treasury.Currency currency){
        var s=OFFERS.get(kind);if(!ENABLED.get()||!s.enabled.get())throw new IllegalStateException("realm.treasury.error.disabled");
        return new Governance.Offer(kind,currency,currency==Treasury.Currency.CHRONICLES?s.cents.get():s.emeralds.get(),DURATION_HOURS.get()*3_600_000L,s.strength.get());
    }
    private GovernanceConfig(){}
}
