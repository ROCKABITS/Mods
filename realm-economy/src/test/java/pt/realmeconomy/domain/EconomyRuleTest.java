package pt.realmeconomy.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EconomyRuleTest {
 private final EconomyRule r=new EconomyRule(500,100,10000,1000,2500,true);
 @Test void hundredEuroExampleConservesBaseTaxAndTotal(){assertEquals(new TradePrice(10000,1000,11000,1000),r.quote(20,true));}
 @Test void absentTreasuryBurnsOnlyTheBase(){assertEquals(new TradePrice(10000,0,10000,0),r.quote(20,false));}
 @Test void lowerBoundsClampAnExistingPriceAndTaxTogether(){assertEquals(new EconomyRule(300,200,300,500,500,false),r.limits(200,300,500,false));}
 @Test void higherMinimumClampsTheExistingPriceUp(){assertEquals(700,r.limits(700,2000,2500,true).value());}
 @Test void aPresidentCannotBreakAdministrativeBounds(){assertThrows(IllegalArgumentException.class,()->r.price(99,0));assertThrows(IllegalArgumentException.class,()->r.price(10001,0));assertThrows(IllegalArgumentException.class,()->r.price(500,2501));}
 @Test void zeroPriceDoesNotCreateMoney(){assertEquals(new TradePrice(0,0,0,2500),new EconomyRule(0,0,100,2500,2500,true).quote(3,true));}
 @Test void taxRoundsDownOncePerOperation(){assertEquals(1,new EconomyRule(1,0,100,5000,10000,true).quote(3,true).tax());}
 @Test void snapshotsRoundTripEveryBoundAndFlag(){assertEquals(r,EconomyRule.decode(r.encode()));assertEquals(r.limits(0,900,2000,false),EconomyRule.decode(r.limits(0,900,2000,false).encode()));}
 @Test void malformedPoliciesAreRejected(){for(String s:new String[]{"500,100,10000,1000,2500,2","-1,0,10,0,0,1","5,10,100,0,0,1","1,0,100,2501,2500,1","1,0,100,0,0,1:extra"})assertThrows(IllegalArgumentException.class,()->EconomyRule.decode(s));}
 @Test void negativeAndExcessiveQuantitiesFailBeforePayment(){assertThrows(IllegalArgumentException.class,()->r.quote(-1,true));assertThrows(IllegalArgumentException.class,()->r.quote(1000001,true));}
 @Test void invalidLimitsAndArithmeticOverflowCannotBecomePrices(){assertThrows(IllegalArgumentException.class,()->r.limits(10,5,10,true));assertThrows(IllegalArgumentException.class,()->new EconomyRule(EconomyRule.MAX_VALUE,0,EconomyRule.MAX_VALUE,10000,10000,true).quote(1000000,true));}
 @Test void policyNeverHidesASignedFractionOrScientificNotation(){assertThrows(IllegalArgumentException.class,()->EconomyRule.decode("1e2,0,1000,0,2500,1"));assertThrows(IllegalArgumentException.class,()->EconomyRule.decode("1.5,0,1000,0,2500,1"));}
}
