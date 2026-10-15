package pt.contracts.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static pt.contracts.domain.Ledger.Currency.CHRONICLES;
import static pt.contracts.domain.Ledger.Currency.EMERALD;
import static pt.contracts.domain.Ledger.Status.*;

class LedgerTest {
    private static final UUID AUTHOR = uuid(1);
    private static final UUID WORKER = uuid(2);
    private static final UUID OTHER = uuid(3);
    private static final String LOG = "minecraft:oak_log";

    @Test
    void completionReleasesTheEscrowAndStoresMaterialsForAnOfflineAuthor() {
        Ledger ledger = new Ledger();
        Ledger.Contract contract = publish(ledger, 10, EMERALD, 20, 64);

        ledger.complete(contract.id(), WORKER);

        assertEquals(COMPLETED, ledger.list().getFirst().status());
        assertEquals(WORKER, ledger.list().getFirst().worker());
        assertEquals(20, ledger.credits(WORKER, EMERALD));
        assertEquals(0, ledger.credits(AUTHOR, EMERALD));
        assertEquals(List.of(new Ledger.Delivery(LOG, 64)), ledger.deliveries(AUTHOR));
        assertTrue(ledger.deliveries(WORKER).isEmpty());
    }

    @Test
    void onlyFirstCompletionWinsAndNeitherCompletionNorCancellationCanPayAgain() {
        Ledger ledger = new Ledger();
        Ledger.Contract contract = publish(ledger, 10, CHRONICLES, 2000, 64);
        ledger.complete(contract.id(), WORKER);

        assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), OTHER));
        assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), WORKER));
        assertThrows(IllegalStateException.class, () -> ledger.cancel(contract.id(), AUTHOR));
        assertEquals(2000, ledger.credits(WORKER, CHRONICLES));
        assertEquals(0, ledger.credits(OTHER, CHRONICLES));
        assertEquals(64, ledger.deliveries(AUTHOR).getFirst().quantity());
    }

    @Test
    void simultaneousWorkersCannotBothCollectTheSameReward() throws Exception {
        Ledger ledger = new Ledger();
        Ledger.Contract contract = publish(ledger, 10, EMERALD, 20, 64);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> raceToComplete(ledger, contract.id(), WORKER, ready, start));
            var second = executor.submit(() -> raceToComplete(ledger, contract.id(), OTHER, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(20, ledger.credits(WORKER, EMERALD) + ledger.credits(OTHER, EMERALD));
        assertEquals(List.of(new Ledger.Delivery(LOG, 64)), ledger.deliveries(AUTHOR));
    }

    @Test
    void cancellationRefundsAuthorOnceAndNeverCreatesMaterials() {
        Ledger ledger = new Ledger();
        Ledger.Contract contract = publish(ledger, 10, CHRONICLES, 400, 3);
        ledger.cancel(contract.id(), AUTHOR);

        assertEquals(CANCELLED, ledger.list().getFirst().status());
        assertNull(ledger.list().getFirst().worker());
        assertEquals(400, ledger.credits(AUTHOR, CHRONICLES));
        assertTrue(ledger.deliveries(AUTHOR).isEmpty());
        assertThrows(IllegalStateException.class, () -> ledger.cancel(contract.id(), AUTHOR));
        assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), WORKER));
        assertEquals(400, ledger.credits(AUTHOR, CHRONICLES));
    }

    @Test
    void authorsCannotSelfCompleteAndOtherPlayersCannotCancel() {
        Ledger ledger = new Ledger();
        Ledger.Contract contract = publish(ledger, 10, EMERALD, 20, 2);

        assertThrows(IllegalArgumentException.class, () -> ledger.complete(contract.id(), AUTHOR));
        assertThrows(IllegalArgumentException.class, () -> ledger.cancel(contract.id(), OTHER));
        assertEquals(OPEN, ledger.list().getFirst().status());
        assertTrue(ledger.creditsSnapshot().isEmpty());
        assertTrue(ledger.deliveriesSnapshot().isEmpty());
    }

    @Test
    void creditsSupportExactPartialClaimsAndSeparateCurrenciesAndOwners() {
        Ledger ledger = new Ledger();
        ledger.complete(publish(ledger, 10, EMERALD, 20, 2).id(), WORKER);
        ledger.complete(publish(ledger, 11, CHRONICLES, 300, 3).id(), WORKER);

        ledger.takeCredit(WORKER, EMERALD, 7);
        assertEquals(13, ledger.credits(WORKER, EMERALD));
        assertEquals(300, ledger.credits(WORKER, CHRONICLES));
        assertThrows(IllegalStateException.class, () -> ledger.takeCredit(OTHER, EMERALD, 1));
        assertThrows(IllegalStateException.class, () -> ledger.takeCredit(WORKER, EMERALD, 14));
        assertThrows(IllegalArgumentException.class, () -> ledger.takeCredit(WORKER, EMERALD, 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.takeCredit(WORKER, EMERALD, -1));
        assertEquals(13, ledger.credits(WORKER, EMERALD));

        ledger.takeCredit(WORKER, EMERALD, 13);
        assertEquals(0, ledger.credits(WORKER, EMERALD));
        assertThrows(IllegalStateException.class, () -> ledger.takeCredit(WORKER, EMERALD, 1));
        ledger.takeCredit(WORKER, CHRONICLES, 300);
        assertTrue(ledger.creditsSnapshot().isEmpty());
    }

    @Test
    void deliveriesAggregateAndCanBeClaimedExactlyOnceInInventorySizedParts() {
        Ledger ledger = new Ledger();
        ledger.complete(publish(ledger, 10, EMERALD, 1, 64).id(), WORKER);
        ledger.complete(publish(ledger, 11, EMERALD, 1, 64).id(), OTHER);
        assertEquals(List.of(new Ledger.Delivery(LOG, 128)), ledger.deliveries(AUTHOR));

        ledger.takeDelivery(AUTHOR, LOG, 64);
        assertEquals(List.of(new Ledger.Delivery(LOG, 64)), ledger.deliveries(AUTHOR));
        assertThrows(IllegalStateException.class, () -> ledger.takeDelivery(OTHER, LOG, 1));
        assertThrows(IllegalStateException.class, () -> ledger.takeDelivery(AUTHOR, LOG, 65));
        assertThrows(IllegalStateException.class, () -> ledger.takeDelivery(AUTHOR, "minecraft:stone", 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.takeDelivery(AUTHOR, LOG, 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.takeDelivery(AUTHOR, LOG, -1));
        assertEquals(64, ledger.deliveries(AUTHOR).getFirst().quantity());

        ledger.takeDelivery(AUTHOR, LOG, 64);
        assertTrue(ledger.deliveries(AUTHOR).isEmpty());
        assertTrue(ledger.deliveriesSnapshot().isEmpty());
        assertThrows(IllegalStateException.class, () -> ledger.takeDelivery(AUTHOR, LOG, 1));
    }

    @Test
    void mixedCompletionCancellationAndClaimsConserveEveryReservedCurrencyUnit() {
        Ledger ledger = new Ledger();
        long reservedEmerald = 0;
        long reservedChronicles = 0;
        for (int i = 0; i < 12; i++) {
            Ledger.Currency currency = i % 2 == 0 ? EMERALD : CHRONICLES;
            long reward = (i + 1) * 17L;
            Ledger.Contract contract = publish(ledger, 100 + i, currency, reward, i + 1);
            if (currency == EMERALD) reservedEmerald += reward;
            else reservedChronicles += reward;
            if (i % 3 == 0) ledger.cancel(contract.id(), AUTHOR);
            else if (i % 3 == 1) ledger.complete(contract.id(), WORKER);
        }
        long claimedEmerald = 5;
        long claimedChronicles = 7;
        ledger.takeCredit(WORKER, EMERALD, claimedEmerald);
        ledger.takeCredit(AUTHOR, CHRONICLES, claimedChronicles);

        assertEquals(reservedEmerald, outstanding(ledger, EMERALD) + claimedEmerald);
        assertEquals(reservedChronicles, outstanding(ledger, CHRONICLES) + claimedChronicles);
    }

    @Test
    void completionCreditOverflowLeavesContractAndBothEscrowsUntouched() {
        Ledger seed = new Ledger();
        Ledger.Contract contract = publish(seed, 10, EMERALD, 2, 3);
        Ledger ledger = new Ledger(seed.list(), Map.of(WORKER, Map.of(EMERALD, Long.MAX_VALUE)), Map.of());

        assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), WORKER));

        assertEquals(OPEN, ledger.list().getFirst().status());
        assertEquals(Long.MAX_VALUE, ledger.credits(WORKER, EMERALD));
        assertTrue(ledger.deliveriesSnapshot().isEmpty());
    }

    @Test
    void completionDeliveryOverflowDoesNotCreateARewardCredit() {
        Ledger seed = new Ledger();
        Ledger.Contract contract = publish(seed, 10, EMERALD, 20, 3);
        Ledger ledger = new Ledger(seed.list(), Map.of(WORKER, Map.of(EMERALD, 12L)),
                Map.of(AUTHOR, List.of(new Ledger.Delivery(LOG, Integer.MAX_VALUE - 1))));

        assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), WORKER));

        assertEquals(OPEN, ledger.list().getFirst().status());
        assertEquals(12, ledger.credits(WORKER, EMERALD));
        assertEquals(Integer.MAX_VALUE - 1, ledger.deliveries(AUTHOR).getFirst().quantity());
    }

    @Test
    void cancellationOverflowDoesNotCloseOrLoseTheContract() {
        Ledger seed = new Ledger();
        Ledger.Contract contract = publish(seed, 10, CHRONICLES, 20, 3);
        Ledger ledger = new Ledger(seed.list(), Map.of(AUTHOR, Map.of(CHRONICLES, Long.MAX_VALUE)), Map.of());

        assertThrows(IllegalStateException.class, () -> ledger.cancel(contract.id(), AUTHOR));

        assertEquals(OPEN, ledger.list().getFirst().status());
        assertEquals(Long.MAX_VALUE, ledger.credits(AUTHOR, CHRONICLES));
    }

    @Test
    void restorationRoundTripPreservesCompletedCancelledOpenAndPartiallyClaimedState() {
        Ledger original = new Ledger();
        original.complete(publish(original, 10, EMERALD, 20, 70).id(), WORKER);
        original.cancel(publish(original, 11, CHRONICLES, 250, 1).id(), AUTHOR);
        publish(original, 12, EMERALD, 10, 5);
        original.takeCredit(WORKER, EMERALD, 3);
        original.takeDelivery(AUTHOR, LOG, 64);

        Ledger restored = new Ledger(original.list(), original.creditsSnapshot(), original.deliveriesSnapshot());

        assertEquals(original.list(), restored.list());
        assertEquals(original.creditsSnapshot(), restored.creditsSnapshot());
        assertEquals(original.deliveriesSnapshot(), restored.deliveriesSnapshot());
        restored.takeCredit(AUTHOR, CHRONICLES, 250);
        assertEquals(250, original.credits(AUTHOR, CHRONICLES));
        restored.complete(uuid(12), OTHER);
        assertEquals(OPEN, original.list().get(2).status());
    }

    @Test
    void snapshotsAndRestorationDoNotExposeOrRetainMutableCollections() {
        Ledger seed = new Ledger();
        Ledger.Contract contract = publish(seed, 10, EMERALD, 20, 3);
        List<Ledger.Contract> contracts = new ArrayList<>(seed.list());
        Map<Ledger.Currency, Long> balances = new HashMap<>(Map.of(EMERALD, 20L));
        Map<UUID, Map<Ledger.Currency, Long>> credits = new HashMap<>(Map.of(WORKER, balances));
        List<Ledger.Delivery> items = new ArrayList<>(List.of(new Ledger.Delivery(LOG, 3)));
        Map<UUID, List<Ledger.Delivery>> deliveries = new HashMap<>(Map.of(AUTHOR, items));
        Ledger ledger = new Ledger(contracts, credits, deliveries);

        contracts.clear();
        balances.clear();
        credits.clear();
        items.clear();
        deliveries.clear();
        assertEquals(List.of(contract), ledger.list());
        assertEquals(20, ledger.credits(WORKER, EMERALD));
        assertEquals(3, ledger.deliveries(AUTHOR).getFirst().quantity());
        assertThrows(UnsupportedOperationException.class, () -> ledger.list().clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.deliveries(AUTHOR).clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.creditsSnapshot().clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.creditsSnapshot().get(WORKER).clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.deliveriesSnapshot().clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.deliveriesSnapshot().get(AUTHOR).clear());
    }

    @Test
    void invalidRestoredBalancesAndDuplicateDeliveriesAreRejected() {
        for (long balance : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Ledger(List.of(), Map.of(WORKER, Map.of(EMERALD, balance)), Map.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> new Ledger(List.of(), Map.of(),
                Map.of(AUTHOR, List.of(new Ledger.Delivery(LOG, 1), new Ledger.Delivery(LOG, 2)))));
        Map<UUID, Map<Ledger.Currency, Long>> nullRecipient = new HashMap<>();
        nullRecipient.put(null, Map.of(EMERALD, 1L));
        assertThrows(IllegalArgumentException.class, () -> new Ledger(List.of(), nullRecipient, Map.of()));
        Map<Ledger.Currency, Long> nullCurrency = new HashMap<>();
        nullCurrency.put(null, 1L);
        assertThrows(IllegalArgumentException.class,
                () -> new Ledger(List.of(), Map.of(WORKER, nullCurrency), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Ledger(null, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Ledger(List.of(), null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Ledger(List.of(), Map.of(), null));
    }

    @Test
    void duplicateSavedContractIdsAndInvalidWorkerStatusAreRejected() {
        Ledger seed = new Ledger();
        Ledger.Contract contract = publish(seed, 10, EMERALD, 20, 3);
        assertThrows(IllegalArgumentException.class,
                () -> new Ledger(List.of(contract, contract), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> contract(10, AUTHOR, OPEN, WORKER));
        assertThrows(IllegalArgumentException.class, () -> contract(10, AUTHOR, CANCELLED, WORKER));
        assertThrows(IllegalArgumentException.class, () -> contract(10, AUTHOR, COMPLETED, null));
        assertThrows(IllegalArgumentException.class, () -> contract(10, AUTHOR, COMPLETED, AUTHOR));
    }

    @Test
    void publishingChecksMetadataBeforeReservingAnArchiveSlot() {
        Ledger ledger = new Ledger();
        for (String item : new String[]{"oak_log", "minecraft:Oak_Log", "minecraft:", ":stone", "a:é", "a:b c", "a:b\n", "a:" + "b".repeat(127)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ledger.publish(uuid(10), AUTHOR, "Author", "Logs", item, 1, 1, EMERALD));
        }
        for (int amount : new int[]{0, -1, 4097}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ledger.publish(uuid(10), AUTHOR, "Author", "Logs", LOG, amount, 1, EMERALD));
        }
        for (long reward : new long[]{0, -1, 1_000_000_001L}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ledger.publish(uuid(10), AUTHOR, "Author", "Logs", LOG, 1, reward, EMERALD));
        }
        for (String title : new String[]{"", "   ", "x".repeat(65), "bad\ntitle", "title\n", "§4Title"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ledger.publish(uuid(10), AUTHOR, "Author", title, LOG, 1, 1, EMERALD));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ledger.publish(uuid(10), AUTHOR, "x".repeat(65), "Logs", LOG, 1, 1, EMERALD));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.publish(uuid(10), AUTHOR, "", "Logs", LOG, 1, 1, EMERALD));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.publish(uuid(10), AUTHOR, "Author\n", "Logs", LOG, 1, 1, EMERALD));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.publish(uuid(10), AUTHOR, "§4Author", "Logs", LOG, 1, 1, EMERALD));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.publish(null, AUTHOR, "Author", "Logs", LOG, 1, 1, EMERALD));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.publish(uuid(10), null, "Author", "Logs", LOG, 1, 1, EMERALD));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.publish(uuid(10), AUTHOR, "Author", "Logs", LOG, 1, 1, null));
        assertTrue(ledger.list().isEmpty());
    }

    @Test
    void boundaryValuesAndCustomNamespacedItemsAreValidAndTextIsTrimmed() {
        Ledger ledger = new Ledger();
        Ledger.Contract contract = ledger.publish(uuid(10), AUTHOR, " Author ", " " + "t".repeat(64) + " ",
                "mod_name:wood/oak.log-1", 4096, 1_000_000_000L, CHRONICLES);
        assertEquals("Author", contract.authorName());
        assertEquals("t".repeat(64), contract.title());
        ledger.complete(contract.id(), WORKER);
        assertEquals(1_000_000_000L, ledger.credits(WORKER, CHRONICLES));
        assertEquals(4096, ledger.deliveries(AUTHOR).getFirst().quantity());
    }

    @Test
    void duplicatePublishDoesNotOverwriteOrCreateAdditionalEscrow() {
        Ledger ledger = new Ledger();
        Ledger.Contract original = publish(ledger, 10, EMERALD, 20, 64);
        assertThrows(IllegalArgumentException.class, () -> publish(ledger, 10, CHRONICLES, 900, 1));
        assertEquals(List.of(original), ledger.list());
        ledger.cancel(original.id(), AUTHOR);
        assertEquals(20, ledger.credits(AUTHOR, EMERALD));
        assertEquals(0, ledger.credits(AUTHOR, CHRONICLES));
    }

    @Test
    void openLimitIsPerAuthorAndClosingAContractFreesAnOpenSlot() {
        Ledger ledger = new Ledger();
        for (int i = 0; i < 16; i++) publish(ledger, 100 + i, EMERALD, 1, 1);
        assertThrows(IllegalStateException.class, () -> publish(ledger, 200, EMERALD, 1, 1));
        ledger.publish(uuid(201), OTHER, "Other", "Logs", LOG, 1, 1, EMERALD);
        ledger.cancel(uuid(100), AUTHOR);
        publish(ledger, 200, EMERALD, 1, 1);
        assertEquals(18, ledger.list().size());
    }

    @Test
    void restoredOpenLimitAndTotalArchiveLimitAreEnforced() {
        List<Ledger.Contract> tooManyOpen = new ArrayList<>();
        for (int i = 0; i < 17; i++) tooManyOpen.add(contract(100 + i, AUTHOR, OPEN, null));
        assertThrows(IllegalArgumentException.class, () -> new Ledger(tooManyOpen, Map.of(), Map.of()));

        List<Ledger.Contract> fullArchive = new ArrayList<>();
        for (int i = 0; i < 2048; i++) fullArchive.add(contract(100 + i, uuid(5000 + i / 16), OPEN, null));
        Ledger ledger = new Ledger(fullArchive, Map.of(), Map.of());
        assertThrows(IllegalStateException.class, () -> publish(ledger, 9999, EMERALD, 1, 1));
        fullArchive.add(contract(9999, AUTHOR, CANCELLED, null));
        assertThrows(IllegalArgumentException.class, () -> new Ledger(fullArchive, Map.of(), Map.of()));
    }

    @Test
    void fullArchiveRotatesOldestClosedContractsWithoutLosingOpenContractsOrClaims() {
        List<Ledger.Contract> history = new ArrayList<>();
        history.add(contract(100, AUTHOR, OPEN, null));
        history.add(contract(101, AUTHOR, COMPLETED, WORKER));
        for (int i = 0; i < 2046; i++) history.add(contract(102 + i, AUTHOR, CANCELLED, null));
        Ledger ledger = new Ledger(history,
                Map.of(WORKER, Map.of(EMERALD, 20L), AUTHOR, Map.of(EMERALD, 2L)),
                Map.of(AUTHOR, List.of(new Ledger.Delivery(LOG, 64))));

        publish(ledger, 9999, EMERALD, 10, 3);

        assertEquals(2048, ledger.list().size());
        assertEquals(uuid(100), ledger.list().getFirst().id());
        assertTrue(ledger.list().stream().noneMatch(c -> c.id().equals(uuid(101))));
        assertEquals(20, ledger.credits(WORKER, EMERALD));
        assertEquals(2, ledger.credits(AUTHOR, EMERALD));
        assertEquals(List.of(new Ledger.Delivery(LOG, 64)), ledger.deliveries(AUTHOR));
        ledger.takeCredit(WORKER, EMERALD, 20);
        ledger.takeDelivery(AUTHOR, LOG, 64);

        publish(ledger, 10000, EMERALD, 5, 1);
        assertTrue(ledger.list().stream().noneMatch(c -> c.id().equals(uuid(102))));
        assertEquals(2048, ledger.list().size());
    }

    @Test
    void rejectedPublicationDoesNotEvictHistory() {
        List<Ledger.Contract> history = new ArrayList<>();
        history.add(contract(100, AUTHOR, CANCELLED, null));
        for (int i = 0; i < 16; i++) history.add(contract(200 + i, AUTHOR, OPEN, null));
        for (int i = 0; i < 2031; i++) history.add(contract(300 + i, AUTHOR, CANCELLED, null));
        Ledger ledger = new Ledger(history, Map.of(), Map.of());

        assertThrows(IllegalStateException.class, () -> publish(ledger, 9999, EMERALD, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> publish(ledger, 100, EMERALD, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> publish(ledger, 9999, EMERALD, 0, 1));

        assertEquals(history, ledger.list());
    }

    @Test
    void unknownContractAndInvalidRecipientsNeverMutateState() {
        Ledger ledger = new Ledger();
        assertThrows(IllegalArgumentException.class, () -> ledger.complete(uuid(123), WORKER));
        assertThrows(IllegalArgumentException.class, () -> ledger.cancel(uuid(123), AUTHOR));
        assertThrows(IllegalArgumentException.class, () -> ledger.complete(null, WORKER));
        assertThrows(IllegalArgumentException.class, () -> ledger.complete(uuid(123), null));
        assertThrows(IllegalArgumentException.class, () -> ledger.cancel(uuid(123), null));
        assertThrows(IllegalArgumentException.class, () -> ledger.credits(null, EMERALD));
        assertThrows(IllegalArgumentException.class, () -> ledger.credits(AUTHOR, null));
        assertThrows(IllegalArgumentException.class, () -> ledger.deliveries(null));
        assertThrows(IllegalArgumentException.class, () -> ledger.takeDelivery(AUTHOR, "bad", 1));
        assertThrows(IllegalArgumentException.class, () -> new Ledger.Delivery(LOG, 0));
        assertTrue(ledger.list().isEmpty());
        assertTrue(ledger.creditsSnapshot().isEmpty());
        assertTrue(ledger.deliveriesSnapshot().isEmpty());
    }

    private static Ledger.Contract publish(Ledger ledger, int id, Ledger.Currency currency, long reward, int quantity) {
        return ledger.publish(uuid(id), AUTHOR, "Author", "Deliver logs", LOG, quantity, reward, currency);
    }

    private static boolean raceToComplete(Ledger ledger, UUID id, UUID worker,
                                         CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }
        try {
            ledger.complete(id, worker);
            return true;
        } catch (IllegalStateException expected) {
            return false;
        }
    }

    private static Ledger.Contract contract(int id, UUID author, Ledger.Status status, UUID worker) {
        return new Ledger.Contract(uuid(id), author, "Author", "Logs", LOG, 1, 1, EMERALD, status, worker);
    }

    private static long outstanding(Ledger ledger, Ledger.Currency currency) {
        long openEscrow = ledger.list().stream().filter(c -> c.currency() == currency && c.status() == OPEN)
                .mapToLong(Ledger.Contract::reward).sum();
        long claimable = ledger.creditsSnapshot().values().stream().mapToLong(m -> m.getOrDefault(currency, 0L)).sum();
        return openEscrow + claimable;
    }

    private static UUID uuid(int value) {
        return new UUID(0, value);
    }
}
