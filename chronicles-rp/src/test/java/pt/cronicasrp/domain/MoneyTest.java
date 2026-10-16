package pt.cronicasrp.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void amountsAreExactWithPortugueseOrDotDecimals() {
        assertEquals(1, Money.positive("0,01")); assertEquals(2, Money.positive("0.02"));
        assertEquals(1250, Money.positive("12,50")); assertEquals(1250, Money.positive("12.5"));
        assertEquals(200, Money.parse("2")); assertEquals(0, Money.parse("0"));
        assertEquals("1.234,56 €", Money.format(123456)); assertEquals("0,01 €", Money.format(1));
        assertEquals(30, Money.parse("0,10") + Money.parse("0,20"));
    }
    @Test void malformedAndFractionalCentsCannotCreateMoney() {
        for (String input : new String[]{"-1", "+1", "0,001", "0.999", "1e2", "NaN", "Infinity", "1.000,50", "1 000", "", "1,", ",50", "1/2"})
            assertThrows(IllegalArgumentException.class, () -> Money.parse(input), input);
        assertThrows(IllegalArgumentException.class, () -> Money.positive("0,00"));
    }
    @Test void maximumAndLegacyConversionStayExactAndRejectOverflow() {
        assertEquals(Money.MAX_CENTS, Money.fromEuros(9_000_000_000_000_000L));
        assertEquals(Money.MAX_CENTS, Money.parse("9000000000000000,00"));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("9000000000000000,01"));
        assertThrows(IllegalArgumentException.class, () -> Money.fromEuros(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("999999999999999999999999999"));
    }
    @Test void newCharactersReceiveEurosFromTheExistingRngSettings() {
        byte[] ink = new byte[SignatureDrawing.BYTES]; SignatureDrawing.pixel(ink, 1, 1, true);
        var settings = new EconomySettings(java.util.List.of(new EconomySettings.Band("Comum", 1, 110, 110)));
        var created = CharacterRules.create(CharacterProfile.empty(), new CharacterDraft("Ari", 24, "Elfo", "", "Ari", SignatureDrawing.encode(ink)), settings, new java.util.Random(1));
        assertEquals(11_000, created.balance()); assertEquals("110,00 €", Money.format(created.initialBalance()));
    }
}
