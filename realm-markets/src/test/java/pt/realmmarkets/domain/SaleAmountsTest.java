package pt.realmmarkets.domain;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaleAmountsTest {
    @Test void twentyEuroPurchaseAtFivePercentKeepsTheBuyersPrice() {
        assertEquals(new SaleAmounts(2000, 100, 1900, 500), SaleAmounts.quote(2000, 500));
    }

    @Test void emeraldTaxRoundsDownOnceOnTheWholePurchase() {
        assertEquals(new SaleAmounts(19, 0, 19, 500), SaleAmounts.quote(19, 500));
        assertEquals(new SaleAmounts(20, 1, 19, 500), SaleAmounts.quote(20, 500));
        assertEquals(new SaleAmounts(21, 1, 20, 500), SaleAmounts.quote(21, 500));
    }

    @Test void zeroAndOneHundredPercentAreBothValid() {
        assertEquals(new SaleAmounts(2000, 0, 2000, 0), SaleAmounts.quote(2000, 0));
        assertEquals(new SaleAmounts(2000, 2000, 0, 10_000), SaleAmounts.quote(2000, 10_000));
    }

    @Test void largestSupportedWalletQuotesWithoutIntermediateOverflow() {
        assertEquals(new SaleAmounts(SaleAmounts.MAX_AMOUNT, SaleAmounts.MAX_AMOUNT, 0, 10_000),
                SaleAmounts.quote(SaleAmounts.MAX_AMOUNT, 10_000));
        Random random = new Random(58127);
        for (int sample = 0; sample < 1000; sample++) {
            long gross = 1 + Math.floorMod(random.nextLong(), SaleAmounts.MAX_AMOUNT);
            int basisPoints = random.nextInt(10_001);
            long expected = BigInteger.valueOf(gross).multiply(BigInteger.valueOf(basisPoints))
                    .divide(BigInteger.valueOf(10_000)).longValueExact();
            SaleAmounts quote = SaleAmounts.quote(gross, basisPoints);
            assertEquals(expected, quote.tax());
            assertEquals(gross, quote.tax() + quote.net());
        }
    }

    @Test void malformedRatesAmountsAndInconsistentRecordsAreRejected() {
        for (long gross : new long[]{Long.MIN_VALUE, -1, 0, SaleAmounts.MAX_AMOUNT + 1, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> SaleAmounts.quote(gross, 500));
        }
        assertThrows(IllegalArgumentException.class, () -> SaleAmounts.quote(20, -1));
        assertThrows(IllegalArgumentException.class, () -> SaleAmounts.quote(20, 10_001));
        assertThrows(IllegalArgumentException.class, () -> new SaleAmounts(20, 1, 20, 500));
        assertThrows(IllegalArgumentException.class, () -> new SaleAmounts(20, 0, 20, 500));
    }
}
