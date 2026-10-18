package pt.realmcasino.domain;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SlotsTest {
    @Test void exactEnumerationProvesPublishedReturnAndOutcomeCounts() {
        int returns = 0, triples = 0, pairs = 0, losses = 0;
        for (int a = 0; a < 8; a++) for (int b = 0; b < 8; b++) for (int c = 0; c < 8; c++) {
            Slots.Result result = Slots.result(a, b, c);
            returns += result.multiplier();
            if (a == b && b == c) triples++;
            else if (result.multiplier() == 1) pairs++;
            else losses++;
        }
        assertEquals(488, returns);
        assertEquals(Slots.RETURN_NUMERATOR, returns);
        assertEquals(512, triples + pairs + losses);
        assertEquals(8, triples); assertEquals(168, pairs); assertEquals(336, losses);
        assertEquals("95.3125", Slots.RTP_PERCENT);
        assertEquals(0.953125, (double) returns / Slots.RETURN_DENOMINATOR);
    }
    @Test void allTriplesUseThePublishedTableAndGrossReturns() {
        int[] expected = {100,80,50,32,24,16,12,6};
        for (int symbol = 0; symbol < 8; symbol++) {
            assertEquals(expected[symbol], Slots.result(symbol, symbol, symbol).multiplier());
            assertEquals(expected[symbol] * 50L, Slots.result(symbol, symbol, symbol).payout(50));
        }
        assertEquals(100, Slots.MAX_MULTIPLIER);
    }
    @Test void aPairOnlyReturnsTheOriginalStake() {
        assertEquals(73, Slots.result(1, 1, 2).payout(73));
        assertEquals(73, Slots.result(1, 2, 1).payout(73));
        assertEquals(73, Slots.result(2, 1, 1).payout(73));
        assertEquals(0, Slots.result(0, 1, 2).payout(73));
    }
    @Test void entropyIsRequestedIndependentlyWithTheExactReelBound() {
        AtomicInteger calls = new AtomicInteger();
        Slots.Result result = Slots.draw(bound -> { assertEquals(8, bound); return calls.getAndIncrement(); });
        assertEquals(3, calls.get()); assertEquals(java.util.List.of(0, 1, 2), result.reels());
    }
    @Test void invalidOrForgedReelsCannotSupplyADifferentReturn() {
        assertThrows(DomainException.class, () -> Slots.result(-1, 0, 0));
        assertThrows(DomainException.class, () -> Slots.draw(bound -> bound));
        assertThrows(DomainException.class, () -> new Slots.Result(0, 1, 2, 100));
        assertThrows(DomainException.class, () -> Slots.result(0, 0, 0).payout(Money.MAX_CENTS));
        assertThrows(UnsupportedOperationException.class, () -> Slots.TRIPLE_RETURNS.set(0, 0));
    }
}
