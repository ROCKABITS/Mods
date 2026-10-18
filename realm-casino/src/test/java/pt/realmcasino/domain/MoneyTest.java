package pt.realmcasino.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void amountsAreExactIntegerCentsWithEitherDecimalSeparator() {
        assertEquals(1234, Money.parse("12,34")); assertEquals(1234, Money.parse("12.34"));
        assertEquals(1200, Money.parse("12")); assertEquals(10, Money.parse("0.1"));
    }
    @Test void overflowAndNegativeMoneyAreRejected() {
        assertThrows(DomainException.class, () -> Money.add(Money.MAX_CENTS, 1));
        assertThrows(DomainException.class, () -> Money.checked(-1));
        assertThrows(DomainException.class, () -> Money.multiply(Money.MAX_CENTS, 100));
        assertThrows(DomainException.class, () -> Money.positive(0));
    }
    @Test void nonDecimalAndFractionalCentInputsCannotEnterTheLedger() {
        for (String text : new String[]{"-1", "1.001", "1e3", "NaN", "1 000", "+2", "", "1,2.3"})
            assertThrows(DomainException.class, () -> Money.parse(text), text);
    }
    @Test void upperBoundMatchesTheChroniclesWalletWithoutFloatingPointRounding() {
        assertEquals(Money.MAX_CENTS, Money.parse("9000000000000000.00"));
        assertThrows(DomainException.class, () -> Money.parse("9000000000000000.01"));
    }
}
