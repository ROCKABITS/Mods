package pt.crownledger.domain;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class EconomicAuditTest {
 private String memo(){return "realmeconomy.policy:ENCHANT:1000:100,0,10000,0,2500,1:200,0,10000,1000,2500,1";}
 @Test void zeroValueAuditBindsEveryPreviousAndNewPolicyValue(){assertTrue(IncomeLedger.validEconomicPolicy(memo()));var r=new IncomeLedger.Receipt(UUID.randomUUID(),Treasury.Currency.CHRONICLES,0,UUID.randomUUID(),memo());assertNotNull(r);}
 @Test void anAuditCannotBeUsedToCreateFundedMoneyOrSwitchCurrencies(){assertThrows(IllegalArgumentException.class,()->new IncomeLedger.Receipt(UUID.randomUUID(),Treasury.Currency.CHRONICLES,1,UUID.randomUUID(),memo()));assertThrows(IllegalArgumentException.class,()->new IncomeLedger.Receipt(UUID.randomUUID(),Treasury.Currency.EMERALD,0,UUID.randomUUID(),memo()));}
 @Test void unknownServicesAndUnboundedPricesOrTaxesAreRejected(){for(String s:new String[]{memo().replace("ENCHANT","BOGUS"),memo().replace("1000,2500,1","2501,2500,1"),memo().replace("200,0,10000","200,0,1000000000001"),memo()+":extra"})assertFalse(IncomeLedger.validEconomicPolicy(s));}
 @Test void beaconPeriodAuditsHaveTheSameStableIdempotencyRules(){assertTrue(IncomeLedger.validEconomicPolicy("realmeconomy.policy:BEACON_PERIOD:1000:600:900"));assertFalse(IncomeLedger.validEconomicPolicy("realmeconomy.policy:BEACON_PERIOD:1000:0:900"));assertFalse(IncomeLedger.validEconomicPolicy("realmeconomy.policy:BEACON_PERIOD:1000:600:86401"));}
 @Test void repeatedAuditKeysRequireTheEntirePayloadToMatch(){var id=UUID.randomUUID();var actor=UUID.randomUUID();var r=new IncomeLedger.Receipt(id,Treasury.Currency.CHRONICLES,0,actor,memo());var ledger=new IncomeLedger().add(r);assertSame(ledger,ledger.add(r));assertThrows(IllegalArgumentException.class,()->ledger.add(new IncomeLedger.Receipt(id,Treasury.Currency.CHRONICLES,0,actor,memo().replace("200,0","300,0"))));}
}
