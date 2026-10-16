package pt.realmmarkets.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SalesTaxLedgerTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000012");

    private static Stall stall(Currency currency, long earnings, int stock, long price, long position) {
        return new Stall(UUID.randomUUID(), OWNER, "minecraft:overworld", position, "Medieval shop", currency,
                earnings, List.of(new Listing(0, "minecraft:apple", stock, price)));
    }

    @Test void paidSaleSplitsGrossBetweenNetProceedsAndFundedTax() {
        Stall stall = stall(Currency.EMERALD, 0, 3, 20, 1);
        MarketLedger ledger = new MarketLedger(List.of(stall));
        assertEquals(new SaleAmounts(20, 1, 19, 500), ledger.purchase(stall.id(), BUYER, 0, 1, 500));
        assertEquals(19, ledger.find(stall.id()).orElseThrow().earnings());
        assertEquals(2, ledger.find(stall.id()).orElseThrow().listing(0).orElseThrow().stock());
        assertEquals(1, ledger.taxBalance(Currency.EMERALD));
        assertEquals(0, ledger.taxBalance(Currency.CHRONICLES));
    }

    @Test void oneHundredPercentMaySellWithZeroSellerEarningsEvenAtItsCap() {
        Stall stall = stall(Currency.CHRONICLES, MarketLedger.MAX_EARNINGS, 1, 2000, 1);
        MarketLedger ledger = new MarketLedger(List.of(stall));
        assertEquals(0, ledger.purchase(stall.id(), BUYER, 0, 1, 10_000).net());
        assertEquals(MarketLedger.MAX_EARNINGS, ledger.find(stall.id()).orElseThrow().earnings());
        assertEquals(2000, ledger.taxBalance(Currency.CHRONICLES));
    }

    @Test void taxEscrowSurvivesStallRemovalAndIsNotTaxedAgainWhenClaimed() {
        Stall stall = stall(Currency.EMERALD, 0, 1, 20, 1);
        MarketLedger ledger = new MarketLedger(List.of(stall));
        ledger.purchase(stall.id(), BUYER, 0, 1, 500);
        ledger.claim(stall.id(), OWNER, 19);
        ledger.remove(stall.id(), OWNER);
        assertTrue(ledger.stalls().isEmpty());
        assertEquals(1, ledger.taxBalance(Currency.EMERALD));
        ledger.debitTax(Currency.EMERALD, 1);
        assertEquals(0, ledger.taxBalance(Currency.EMERALD));
        assertTrue(ledger.taxBalances().isEmpty());
        assertThrows(IllegalStateException.class, () -> ledger.debitTax(Currency.EMERALD, 1));
    }

    @Test void totalPurchaseRoundsOnceAndCurrenciesRemainIndependent() {
        Stall emeralds = stall(Currency.EMERALD, 0, 20, 1, 1);
        Stall cents = stall(Currency.CHRONICLES, 0, 1, 2000, 2);
        MarketLedger ledger = new MarketLedger(List.of(emeralds, cents));
        ledger.purchase(emeralds.id(), BUYER, 0, 20, 500);
        ledger.purchase(cents.id(), BUYER, 0, 1, 500);
        assertEquals(Map.of(Currency.EMERALD, 1L, Currency.CHRONICLES, 100L), ledger.taxBalances());
    }

    @Test void taxOverflowRejectsEveryMutationWhileAnExactCapSaleSucceeds() {
        Stall stall = stall(Currency.CHRONICLES, 0, 3, 20, 1);
        MarketLedger ledger = new MarketLedger(List.of(stall), Map.of(Currency.CHRONICLES, MarketLedger.MAX_EARNINGS - 1));
        ledger.purchase(stall.id(), BUYER, 0, 1, 500);
        List<Stall> before = ledger.stalls();
        assertThrows(IllegalStateException.class, () -> ledger.purchase(stall.id(), BUYER, 0, 1, 500));
        assertEquals(before, ledger.stalls());
        assertEquals(MarketLedger.MAX_EARNINGS, ledger.taxBalance(Currency.CHRONICLES));
    }

    @Test void sellerOverflowAndInvalidRateDoNotLeaveTaxCreditBehind() {
        Stall full = stall(Currency.EMERALD, MarketLedger.MAX_EARNINGS - 18, 3, 20, 1);
        MarketLedger ledger = new MarketLedger(List.of(full));
        assertThrows(IllegalStateException.class, () -> ledger.purchase(full.id(), BUYER, 0, 1, 500));
        assertThrows(IllegalArgumentException.class, () -> ledger.purchase(full.id(), BUYER, 0, 1, 10_001));
        assertEquals(List.of(full), ledger.stalls());
        assertTrue(ledger.taxBalances().isEmpty());
    }

    @Test void restoreAndCopiesPreserveOldEarningsWithoutApplyingTaxRetroactively() {
        Stall old = stall(Currency.EMERALD, 2000, 3, 20, 1);
        Map<Currency, Long> input = new HashMap<>(Map.of(Currency.EMERALD, 7L));
        MarketLedger ledger = new MarketLedger(List.of(old), input, 1);
        input.clear();
        MarketLedger copy = ledger.copy();
        copy.purchase(old.id(), BUYER, 0, 1, 500);
        copy.debitTax(Currency.EMERALD, 3);
        assertEquals(2000, ledger.find(old.id()).orElseThrow().earnings());
        assertEquals(7, ledger.taxBalance(Currency.EMERALD));
        assertEquals(2019, copy.find(old.id()).orElseThrow().earnings());
        assertEquals(5, copy.taxBalance(Currency.EMERALD));
        assertThrows(UnsupportedOperationException.class, () -> ledger.taxBalances().clear());
        assertThrows(IllegalStateException.class, () -> copy.create(OWNER, "minecraft:overworld", 2, "Other", Currency.EMERALD));
    }

    @Test void malformedRestoreAndInvalidExactTransfersLeaveFundedMoneyUnchanged() {
        assertThrows(IllegalArgumentException.class, () -> new MarketLedger(List.of(), Map.of(Currency.EMERALD, -1L)));
        assertThrows(IllegalArgumentException.class, () -> new MarketLedger(List.of(), Map.of(Currency.EMERALD, MarketLedger.MAX_EARNINGS + 1)));
        MarketLedger ledger = new MarketLedger(List.of(), Map.of(Currency.EMERALD, 7L));
        for (long amount : new long[]{Long.MIN_VALUE, -1, 0, MarketLedger.MAX_EARNINGS + 1}) {
            assertThrows(IllegalArgumentException.class, () -> ledger.debitTax(Currency.EMERALD, amount));
        }
        assertThrows(IllegalStateException.class, () -> ledger.debitTax(Currency.EMERALD, 8));
        assertThrows(IllegalStateException.class, () -> ledger.debitTax(Currency.CHRONICLES, 1));
        assertEquals(Map.of(Currency.EMERALD, 7L), ledger.taxBalances());
    }
}
