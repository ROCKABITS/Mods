package pt.realmmarkets.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketLedgerTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String DIMENSION = "minecraft:overworld";

    private static Stall create(MarketLedger ledger) {
        return ledger.create(OWNER, DIMENSION, 12, "Aveline's Produce", Currency.EMERALD);
    }

    private static Stall stocked(MarketLedger ledger, int amount, long price) {
        Stall stall = create(ledger);
        ledger.configure(stall.id(), OWNER, 0, "minecraft:apple", price);
        ledger.stock(stall.id(), OWNER, 0, amount);
        return ledger.find(stall.id()).orElseThrow();
    }

    @Test void saleRemovesExactStockAndRetainsExactProceeds() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 12, 7);
        assertEquals(35, ledger.purchase(stall.id(), BUYER, 0, 5));
        Stall result = ledger.find(stall.id()).orElseThrow();
        assertEquals(7, result.listing(0).orElseThrow().stock());
        assertEquals(35, result.earnings());
        assertEquals(Currency.EMERALD, result.currency());
    }

    @Test void successiveBuyersCannotOversellTheLastItem() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 1, 20);
        ledger.purchase(stall.id(), BUYER, 0, 1);
        List<Stall> before = ledger.stalls();
        assertThrows(IllegalStateException.class, () -> ledger.purchase(stall.id(), UUID.randomUUID(), 0, 1));
        assertEquals(before, ledger.stalls());
    }

    @Test void oneSaleCannotExceedTheRealStock() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 3, 20);
        List<Stall> before = ledger.stalls();
        assertThrows(IllegalStateException.class, () -> ledger.purchase(stall.id(), BUYER, 0, 4));
        assertEquals(before, ledger.stalls());
    }

    @Test void ownerCannotCreateSalesToTheirOwnStall() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 3, 20);
        List<Stall> before = ledger.stalls();
        assertThrows(SecurityException.class, () -> ledger.purchase(stall.id(), OWNER, 0, 1));
        assertEquals(before, ledger.stalls());
    }

    @Test void retainedMoneyCanBeClaimedInPartialAmountsWithoutRounding() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 4, 123);
        ledger.purchase(stall.id(), BUYER, 0, 3);
        ledger.claim(stall.id(), OWNER, 100);
        assertEquals(269, ledger.find(stall.id()).orElseThrow().earnings());
        ledger.claim(stall.id(), OWNER, 269);
        assertEquals(0, ledger.find(stall.id()).orElseThrow().earnings());
    }

    @Test void excessiveClaimCannotDestroyOrCreateMoney() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 4, 123);
        ledger.purchase(stall.id(), BUYER, 0, 1);
        List<Stall> before = ledger.stalls();
        assertThrows(IllegalStateException.class, () -> ledger.claim(stall.id(), OWNER, 124));
        assertThrows(IllegalArgumentException.class, () -> ledger.claim(stall.id(), OWNER, 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.claim(stall.id(), OWNER, -1));
        assertThrows(IllegalArgumentException.class, () -> ledger.claim(stall.id(), OWNER, Long.MAX_VALUE));
        assertEquals(before, ledger.stalls());
    }

    @Test void earningsCapRejectsTheWholeSaleBeforeRemovingStock() {
        Stall full = new Stall(UUID.randomUUID(), OWNER, DIMENSION, 12, "Full", Currency.CHRONICLES,
                MarketLedger.MAX_EARNINGS - 1, List.of(new Listing(0, "minecraft:apple", 2, 2)));
        MarketLedger ledger = new MarketLedger(List.of(full));
        assertThrows(IllegalStateException.class, () -> ledger.purchase(full.id(), BUYER, 0, 1));
        assertEquals(full, ledger.find(full.id()).orElseThrow());
    }

    @Test void saleCanReachTheExactEarningsCap() {
        Stall almostFull = new Stall(UUID.randomUUID(), OWNER, DIMENSION, 12, "Full", Currency.CHRONICLES,
                MarketLedger.MAX_EARNINGS - 2, List.of(new Listing(0, "minecraft:apple", 2, 2)));
        MarketLedger ledger = new MarketLedger(List.of(almostFull));
        assertEquals(2, ledger.purchase(almostFull.id(), BUYER, 0, 1));
        assertEquals(MarketLedger.MAX_EARNINGS, ledger.find(almostFull.id()).orElseThrow().earnings());
    }

    @Test void maximumStockTimesMaximumPriceUsesLongArithmetic() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, MarketLedger.MAX_STOCK, MarketLedger.MAX_UNIT_PRICE);
        assertEquals(4_096_000_000_000L, ledger.purchase(stall.id(), BUYER, 0, 4_096));
        assertEquals(4_096_000_000_000L, ledger.find(stall.id()).orElseThrow().earnings());
    }

    @Test void offersMustBeConfiguredBeforeStockWithdrawalOrPurchase() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = create(ledger);
        assertThrows(IllegalStateException.class, () -> ledger.stock(stall.id(), OWNER, 0, 1));
        assertThrows(IllegalStateException.class, () -> ledger.withdraw(stall.id(), OWNER, 0, 1));
        assertThrows(IllegalStateException.class, () -> ledger.purchase(stall.id(), BUYER, 0, 1));
        assertTrue(ledger.find(stall.id()).orElseThrow().listings().isEmpty());
    }

    @Test void stockCapacityIsPerOfferAndRejectsTheEntireExcess() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 4_095, 1);
        ledger.stock(stall.id(), OWNER, 0, 1);
        assertThrows(IllegalStateException.class, () -> ledger.stock(stall.id(), OWNER, 0, 1));
        assertEquals(4_096, ledger.find(stall.id()).orElseThrow().listing(0).orElseThrow().stock());
        ledger.configure(stall.id(), OWNER, 1, "minecraft:carrot", 1);
        ledger.stock(stall.id(), OWNER, 1, 4_096);
        assertEquals(8_192, ledger.find(stall.id()).orElseThrow().listings().stream().mapToInt(Listing::stock).sum());
    }

    @Test void withdrawingStockPreservesItemAndPriceForRestocking() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 12, 7);
        ledger.withdraw(stall.id(), OWNER, 0, 12);
        assertEquals(new Listing(0, "minecraft:apple", 0, 7), ledger.find(stall.id()).orElseThrow().listing(0).orElseThrow());
        ledger.stock(stall.id(), OWNER, 0, 2);
        assertEquals(2, ledger.find(stall.id()).orElseThrow().listing(0).orElseThrow().stock());
    }

    @Test void withdrawalCannotRemoveMoreThanTheStoredStock() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 2, 7);
        List<Stall> before = ledger.stalls();
        assertThrows(IllegalStateException.class, () -> ledger.withdraw(stall.id(), OWNER, 0, 3));
        assertEquals(before, ledger.stalls());
    }

    @Test void changingTheItemRequiresAnEmptyOfferButRepricingDoesNot() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 2, 7);
        assertThrows(IllegalStateException.class, () -> ledger.configure(stall.id(), OWNER, 0, "minecraft:carrot", 3));
        assertEquals(new Listing(0, "minecraft:apple", 2, 7), ledger.find(stall.id()).orElseThrow().listing(0).orElseThrow());
        ledger.configure(stall.id(), OWNER, 0, "minecraft:apple", 9);
        assertEquals(new Listing(0, "minecraft:apple", 2, 9), ledger.find(stall.id()).orElseThrow().listing(0).orElseThrow());
        ledger.withdraw(stall.id(), OWNER, 0, 2);
        ledger.configure(stall.id(), OWNER, 0, "minecraft:carrot", 3);
        assertEquals(new Listing(0, "minecraft:carrot", 0, 3), ledger.find(stall.id()).orElseThrow().listing(0).orElseThrow());
    }

    @Test void onlyTheOwnerCanPerformEveryManagementOperation() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 2, 7);
        ledger.purchase(stall.id(), BUYER, 0, 1);
        List<Stall> before = ledger.stalls();
        assertThrows(SecurityException.class, () -> ledger.configure(stall.id(), BUYER, 0, "minecraft:apple", 1));
        assertThrows(SecurityException.class, () -> ledger.stock(stall.id(), BUYER, 0, 1));
        assertThrows(SecurityException.class, () -> ledger.withdraw(stall.id(), BUYER, 0, 1));
        assertThrows(SecurityException.class, () -> ledger.claim(stall.id(), BUYER, 1));
        assertThrows(SecurityException.class, () -> ledger.rename(stall.id(), BUYER, "Stolen"));
        assertThrows(SecurityException.class, () -> ledger.remove(stall.id(), BUYER));
        assertEquals(before, ledger.stalls());
    }

    @Test void removalRequiresBothStockAndRetainedMoneyToBeEmpty() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 2, 7);
        assertThrows(IllegalStateException.class, () -> ledger.remove(stall.id(), OWNER));
        ledger.purchase(stall.id(), BUYER, 0, 2);
        assertThrows(IllegalStateException.class, () -> ledger.remove(stall.id(), OWNER));
        ledger.claim(stall.id(), OWNER, 14);
        ledger.remove(stall.id(), OWNER);
        assertTrue(ledger.find(stall.id()).isEmpty());
        assertTrue(ledger.at(DIMENSION, 12).isEmpty());
        assertEquals(12, create(ledger).position());
    }

    @Test void locationIdentityIncludesDimensionAndOneOwnerMayOwnMultipleStalls() {
        MarketLedger ledger = new MarketLedger();
        Stall first = create(ledger);
        Stall second = ledger.create(OWNER, "minecraft:the_nether", 12, "Nether shop", Currency.CHRONICLES);
        assertNotEquals(first.id(), second.id());
        assertEquals(first, ledger.at(DIMENSION, 12).orElseThrow());
        assertEquals(second, ledger.at("minecraft:the_nether", 12).orElseThrow());
        assertThrows(IllegalStateException.class, () -> create(ledger));
        assertEquals(2, ledger.stalls().size());
    }

    @Test void copiesAreIndependentPreflightTransactionsAndSnapshotsNeverChange() {
        MarketLedger ledger = new MarketLedger();
        Stall original = stocked(ledger, 2, 7);
        List<Stall> snapshot = ledger.stalls();
        MarketLedger copy = ledger.copy();
        copy.purchase(original.id(), BUYER, 0, 1);
        copy.rename(original.id(), OWNER, "Changed");
        assertEquals(original, ledger.find(original.id()).orElseThrow());
        assertEquals(List.of(original), snapshot);
        assertEquals(1, copy.find(original.id()).orElseThrow().listing(0).orElseThrow().stock());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> original.listings().clear());
    }

    @Test void ledgerRestoreCopiesItsInputAndPreservesAllState() {
        MarketLedger original = new MarketLedger();
        Stall stall = stocked(original, 15, 123);
        original.purchase(stall.id(), BUYER, 0, 5);
        List<Stall> input = new ArrayList<>(original.stalls());
        MarketLedger restored = new MarketLedger(input);
        input.clear();
        assertEquals(original.stalls(), restored.stalls());
        restored.claim(stall.id(), OWNER, 615);
        assertEquals(615, original.find(stall.id()).orElseThrow().earnings());
    }

    @Test void malformedRestoreRejectsDuplicateIdsAndDuplicateLocations() {
        Stall first = new Stall(UUID.randomUUID(), OWNER, DIMENSION, 12, "First", Currency.EMERALD, 0, List.of());
        Stall duplicateId = new Stall(first.id(), BUYER, DIMENSION, 13, "Other", Currency.EMERALD, 0, List.of());
        Stall duplicateLocation = new Stall(UUID.randomUUID(), BUYER, DIMENSION, 12, "Other", Currency.EMERALD, 0, List.of());
        assertThrows(IllegalArgumentException.class, () -> new MarketLedger(List.of(first, duplicateId)));
        assertThrows(IllegalArgumentException.class, () -> new MarketLedger(List.of(first, duplicateLocation)));
    }

    @Test void capacityNeverEvictsExistingStallsAndCopyPreservesCapacity() {
        MarketLedger ledger = new MarketLedger(1);
        Stall existing = create(ledger);
        assertThrows(IllegalStateException.class, () -> ledger.create(BUYER, DIMENSION, 13, "New", Currency.EMERALD));
        MarketLedger copy = ledger.copy();
        assertThrows(IllegalStateException.class, () -> copy.create(BUYER, DIMENSION, 13, "New", Currency.EMERALD));
        assertEquals(List.of(existing), ledger.stalls());
        assertThrows(IllegalArgumentException.class, () -> new MarketLedger(0));
        assertThrows(IllegalArgumentException.class, () -> new MarketLedger(MarketLedger.MAX_STALLS + 1));
    }

    @Test void currenciesAreIndependentUnitsAndCannotChangeDuringTrading() {
        Stall emerald = new Stall(UUID.randomUUID(), OWNER, DIMENSION, 12, "Emeralds", Currency.EMERALD, 7,
                List.of(new Listing(0, "minecraft:apple", 3, 123)));
        Stall chronicles = new Stall(UUID.randomUUID(), OWNER, DIMENSION, 13, "Cents", Currency.CHRONICLES, 7,
                List.of(new Listing(0, "minecraft:apple", 3, 123)));
        MarketLedger ledger = new MarketLedger(List.of(emerald, chronicles));
        assertEquals(123, ledger.purchase(emerald.id(), BUYER, 0, 1));
        assertEquals(123, ledger.purchase(chronicles.id(), BUYER, 0, 1));
        ledger.rename(emerald.id(), OWNER, "Renamed");
        ledger.claim(chronicles.id(), OWNER, 1);
        assertEquals(Currency.EMERALD, ledger.find(emerald.id()).orElseThrow().currency());
        assertEquals(Currency.CHRONICLES, ledger.find(chronicles.id()).orElseThrow().currency());
        assertEquals(130, ledger.find(emerald.id()).orElseThrow().earnings());
        assertEquals(129, ledger.find(chronicles.id()).orElseThrow().earnings());
    }

    @Test void invalidQuantitiesRejectAllStockWithdrawalAndPurchasePaths() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 2, 7);
        List<Stall> before = ledger.stalls();
        for (int quantity : new int[]{Integer.MIN_VALUE, -1, 0, 4_097, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> ledger.stock(stall.id(), OWNER, 0, quantity));
            assertThrows(IllegalArgumentException.class, () -> ledger.withdraw(stall.id(), OWNER, 0, quantity));
            assertThrows(IllegalArgumentException.class, () -> ledger.purchase(stall.id(), BUYER, 0, quantity));
        }
        assertEquals(before, ledger.stalls());
    }

    @Test void exactlyEightIndependentOffersMayBeConfiguredInAnyOrder() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = create(ledger);
        for (int slot = 7; slot >= 0; slot--) ledger.configure(stall.id(), OWNER, slot, "minecraft:apple", slot + 1);
        List<Listing> offers = ledger.find(stall.id()).orElseThrow().listings();
        assertEquals(8, offers.size());
        for (int slot = 0; slot < 8; slot++) assertEquals(slot, offers.get(slot).slot());
        assertThrows(IllegalArgumentException.class, () -> ledger.configure(stall.id(), OWNER, 8, "minecraft:apple", 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.purchase(stall.id(), BUYER, -1, 1));
    }

    @Test void failedConfigurationAndRenameLeaveExistingStateUntouched() {
        MarketLedger ledger = new MarketLedger();
        Stall stall = stocked(ledger, 2, 7);
        List<Stall> before = ledger.stalls();
        assertThrows(IllegalArgumentException.class, () -> ledger.configure(stall.id(), OWNER, 0, "minecraft:apple", 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.configure(stall.id(), OWNER, 1, "Invalid", 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.rename(stall.id(), OWNER, "Bad\nname"));
        assertEquals(before, ledger.stalls());
    }
}
