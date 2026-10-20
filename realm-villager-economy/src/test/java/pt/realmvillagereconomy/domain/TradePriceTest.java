package pt.realmvillagereconomy.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradePriceTest {
    @Test void oneEmeraldStartsAtFiveEuros(){assertEquals(new TradePrice(500,0,500,0),TradePrice.of(1,500,0,2500,true));}
    @Test void theRequestedHundredEuroExampleAddsTenToTheTreasury(){assertEquals(new TradePrice(10000,1000,11000,1000),TradePrice.of(20,500,1000,2500,true));}
    @Test void anAbsentTreasuryNeverChargesTax(){assertEquals(new TradePrice(10000,0,10000,0),TradePrice.of(20,500,10000,2500,false));}
    @Test void administratorsCapTheEffectiveRateWithoutDestroyingTheApprovedPolicy(){assertEquals(new TradePrice(10000,2500,12500,2500),TradePrice.of(20,500,4000,2500,true));}
    @Test void taxRoundsDownOnceOnTheWholeBasePrice(){assertEquals(1,TradePrice.of(3,1,5000,10000,true).tax());assertEquals(0,TradePrice.of(1,1,2500,2500,true).tax());}
    @Test void zeroMonetaryCostNeverProducesATax(){assertEquals(0,TradePrice.of(0,500,2500,2500,true).total());}
    @Test void conversionIsConfigurableInExactCents(){assertEquals(248,TradePrice.of(2,123,100,2500,true).total());}
    @Test void allRatesConserveBaseAndTaxAcrossAllowedVanillaQuantities(){for(int rate=0;rate<=10000;rate++)for(int n:new int[]{1,3,20,64,128}){var p=TradePrice.of(n,499,rate,10000,true);assertEquals(p.base()+p.tax(),p.total());assertTrue(p.tax()<=p.base());assertEquals((long)n*499*rate/10000,p.tax());}}
    @Test void invalidAndOverflowingPricesAreRejected(){assertThrows(IllegalArgumentException.class,()->TradePrice.of(-1,500,0,2500,true));assertThrows(IllegalArgumentException.class,()->TradePrice.of(129,500,0,2500,true));assertThrows(IllegalArgumentException.class,()->TradePrice.of(1,0,0,2500,true));assertThrows(IllegalArgumentException.class,()->TradePrice.of(1,500,-1,2500,true));assertThrows(IllegalArgumentException.class,()->TradePrice.of(1,500,10001,2500,true));assertThrows(IllegalArgumentException.class,()->TradePrice.of(128,TradePrice.MAX,2500,2500,true));}
}
