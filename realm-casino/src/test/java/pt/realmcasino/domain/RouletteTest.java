package pt.realmcasino.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouletteTest {
    @Test void eachStraightNumberWinsOneOfThirtySevenEqualPockets() {
        for (int selected = 0; selected <= 36; selected++) {
            Roulette.Bet bet = Roulette.Bet.number(selected);
            int wins = 0, returns = 0;
            for (int pocket = 0; pocket <= 36; pocket++) {
                if (bet.wins(pocket)) wins++;
                returns += bet.multiplier(pocket);
            }
            assertEquals(1, wins); assertEquals(36, returns);
            assertEquals(3_600, bet.payout(100, selected));
        }
    }
    @Test void allOutsideBetsHaveEighteenWinsAndZeroAlwaysLoses() {
        for (Roulette.Type type : Roulette.Type.values()) {
            if (type == Roulette.Type.NUMBER) continue;
            Roulette.Bet bet = Roulette.Bet.outside(type);
            int wins = 0, returns = 0;
            for (int pocket = 0; pocket <= 36; pocket++) {
                if (bet.wins(pocket)) wins++;
                returns += bet.multiplier(pocket);
            }
            assertEquals(18, wins); assertEquals(36, returns);
            assertFalse(bet.wins(0));
        }
    }
    @Test void publishedRedPocketsAreStandardEuropeanColors() {
        assertTrue(Roulette.isRed(1)); assertTrue(Roulette.isRed(12));
        assertTrue(Roulette.isRed(36)); assertFalse(Roulette.isRed(2));
        assertFalse(Roulette.isRed(11)); assertFalse(Roulette.isRed(0));
    }
    @Test void aServerDrawRequestsTheWholeThirtySevenPocketRange() {
        assertEquals(36, Roulette.draw(bound -> { assertEquals(37, bound); return 36; }));
        assertEquals(0, Roulette.draw(bound -> 0));
        assertThrows(DomainException.class, () -> Roulette.draw(bound -> 37));
    }
    @Test void invalidSelectionsAndOverflowAreRejected() {
        assertThrows(DomainException.class, () -> Roulette.Bet.number(37));
        assertThrows(DomainException.class, () -> Roulette.Bet.outside(Roulette.Type.NUMBER));
        assertThrows(DomainException.class, () -> new Roulette.Bet(Roulette.Type.RED, 1));
        assertThrows(DomainException.class, () -> Roulette.Bet.number(0).payout(Money.MAX_CENTS, 0));
    }
}
