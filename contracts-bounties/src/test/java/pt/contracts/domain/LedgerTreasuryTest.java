package pt.contracts.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pt.contracts.domain.Ledger.Currency.CHRONICLES;
import static pt.contracts.domain.Ledger.Currency.EMERALD;
import static pt.contracts.domain.Ledger.Funding.KINGDOM;
import static pt.contracts.domain.Ledger.Funding.PLAYER;
import static pt.contracts.domain.Ledger.KINGDOM_ACCOUNT;
import static pt.contracts.domain.Ledger.Status.*;

class LedgerTreasuryTest {
    private static final UUID AUTHOR = new UUID(0, 101);
    private static final UUID WORKER = new UUID(0, 102);
    private static final UUID OTHER = new UUID(0, 103);
    private static final String LOG = "minecraft:oak_log";
    private static final Ledger.Progress NONE = new Ledger.Progress(0, 0);

    @Test
    void legacyConstructorPublicationAndRestoreStayPrivateUntaxedAndWithoutProgress() {
        var legacy = new Ledger.Contract(id(1), AUTHOR, "Author", "Logs", LOG, 64,
                20, EMERALD, OPEN, null);
        var ledger = new Ledger();
        var published = ledger.publish(id(1), AUTHOR, "Author", "Logs", LOG, 64, 20, EMERALD);

        assertEquals(legacy, published);
        assertEquals(PLAYER, legacy.funding());
        assertEquals(AUTHOR, legacy.issuer());
        assertEquals(0, legacy.taxBasisPoints());
        assertEquals(0, legacy.tax());
        assertEquals(20, legacy.netReward());
        assertEquals(0, legacy.reputationReward());
        assertEquals(0, legacy.dailyReputationLimit());

        var restored = new Ledger(ledger.list(), Map.of(), Map.of());
        restored.complete(published.id(), WORKER, 100);
        assertEquals(20, restored.credits(WORKER, EMERALD));
        assertEquals(NONE, restored.pendingProgress(WORKER));
        assertTrue(restored.dailySnapshot().isEmpty());
    }

    @Test
    void completionSplitsTwentyUnitsIntoNineteenForWorkerAndOneForTreasury() {
        for (var currency : Ledger.Currency.values()) {
            var ledger = new Ledger();
            var contract = privateContract(ledger, 1, 20, currency, 500);

            ledger.complete(contract.id(), WORKER, 100);

            assertEquals(1, contract.tax());
            assertEquals(19, contract.netReward());
            assertEquals(19, ledger.credits(WORKER, currency));
            assertEquals(1, ledger.credits(KINGDOM_ACCOUNT, currency));
            assertEquals(0, ledger.credits(AUTHOR, currency));
            assertEquals(List.of(new Ledger.Delivery(LOG, 64)), ledger.deliveries(AUTHOR));
            assertTrue(ledger.deliveries(KINGDOM_ACCOUNT).isEmpty());
            assertEquals(NONE, ledger.pendingProgress(WORKER));
            assertEquals(500, ledger.list().getFirst().taxBasisPoints());
            assertEquals(COMPLETED, ledger.list().getFirst().status());
        }
    }

    @Test
    void taxRoundsDownAndHandlesZeroFullTaxAndMaximumRewardWithoutLosingUnits() {
        long[][] cases = {{19, 500, 0}, {21, 500, 1}, {4, 3333, 1}, {20, 0, 0},
                {20, 10000, 20}, {Ledger.MAX_REWARD, 9999, 999_900_000}};
        for (var values : cases) {
            var ledger = new Ledger();
            var contract = privateContract(ledger, 1, values[0], CHRONICLES, (int) values[1]);
            assertEquals(values[2], contract.tax());
            assertEquals(values[0] - values[2], contract.netReward());

            ledger.complete(contract.id(), WORKER, 100);

            assertEquals(values[0], ledger.credits(WORKER, CHRONICLES)
                    + ledger.credits(KINGDOM_ACCOUNT, CHRONICLES));
            assertEquals(values[0] - values[2], ledger.credits(WORKER, CHRONICLES));
            assertEquals(values[2], ledger.credits(KINGDOM_ACCOUNT, CHRONICLES));
            assertTrue(ledger.creditsSnapshot().values().stream()
                    .flatMap(balance -> balance.values().stream()).allMatch(amount -> amount > 0));
        }
    }

    @Test
    void kingdomOwnsPublicMaterialsAndFundsWhileIssuerOnlyRecordsWhoPublished() {
        var ledger = new Ledger();
        var contract = publicContract(ledger, 1, 20, 500, 3, 10);

        ledger.complete(contract.id(), WORKER, 100);

        var completed = ledger.list().getFirst();
        assertEquals(KINGDOM_ACCOUNT, completed.author());
        assertEquals(AUTHOR, completed.issuer());
        assertEquals(KINGDOM, completed.funding());
        assertEquals(19, ledger.credits(WORKER, CHRONICLES));
        assertEquals(1, ledger.credits(KINGDOM_ACCOUNT, CHRONICLES));
        assertEquals(0, ledger.credits(AUTHOR, CHRONICLES));
        assertEquals(List.of(new Ledger.Delivery(LOG, 64)), ledger.deliveries(KINGDOM_ACCOUNT));
        assertTrue(ledger.deliveries(AUTHOR).isEmpty());
        assertEquals(new Ledger.Progress(3, 1), ledger.pendingProgress(WORKER));
        assertEquals(new Ledger.DailyProgress(100, 3), ledger.dailySnapshot().get(WORKER));
        assertEquals(3, completed.reputationReward());
        assertEquals(10, completed.dailyReputationLimit());
    }

    @Test
    void cancellingTaxedPrivateOrPublicContractRefundsFullEscrowWithoutTaxOrProgress() {
        for (var funding : Ledger.Funding.values()) {
            var ledger = new Ledger();
            var contract = funding == PLAYER
                    ? privateContract(ledger, 1, 20, CHRONICLES, 500)
                    : publicContract(ledger, 1, 20, 500, 3, 10);
            var owner = contract.author();

            ledger.cancel(contract.id(), owner);

            assertEquals(20, ledger.credits(owner, CHRONICLES));
            assertEquals(20, totalCredits(ledger, CHRONICLES));
            assertTrue(ledger.deliveriesSnapshot().isEmpty());
            assertTrue(ledger.progressSnapshot().isEmpty());
            assertTrue(ledger.dailySnapshot().isEmpty());
            assertEquals(CANCELLED, ledger.list().getFirst().status());
            assertEquals(contract.funding(), ledger.list().getFirst().funding());
            assertEquals(contract.issuer(), ledger.list().getFirst().issuer());
            assertThrows(IllegalStateException.class, () -> ledger.cancel(contract.id(), owner));
            assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), WORKER, 100));
            assertEquals(20, totalCredits(ledger, CHRONICLES));
        }
    }

    @Test
    void issuerCannotClaimOwnershipOfPublicRefundsOrMaterials() {
        var ledger = new Ledger();
        var contract = publicContract(ledger, 1, 20, 500, 3, 10);

        assertThrows(IllegalArgumentException.class, () -> ledger.cancel(contract.id(), AUTHOR));
        assertEquals(OPEN, ledger.list().getFirst().status());
        assertTrue(ledger.creditsSnapshot().isEmpty());

        ledger.cancel(contract.id(), KINGDOM_ACCOUNT);
        assertEquals(20, ledger.credits(KINGDOM_ACCOUNT, CHRONICLES));
        assertEquals(0, ledger.credits(AUTHOR, CHRONICLES));
    }

    @Test
    void publicIssuerCompletingTheirOwnOrderReceivesNoReputationOrMissionProgress() {
        var ledger = new Ledger();
        var contract = publicContract(ledger, 1, 20, 500, 7, 10);

        ledger.complete(contract.id(), AUTHOR, 100);

        assertEquals(19, ledger.credits(AUTHOR, CHRONICLES));
        assertEquals(List.of(new Ledger.Delivery(LOG, 64)), ledger.deliveries(KINGDOM_ACCOUNT));
        assertEquals(NONE, ledger.pendingProgress(AUTHOR));
        assertTrue(ledger.progressSnapshot().isEmpty());
        assertTrue(ledger.dailySnapshot().isEmpty());
    }

    @Test
    void dailyReputationCapIsPerWorkerAndMissionsContinueAfterCap() {
        var ledger = new Ledger();
        for (int index = 0; index < 3; index++) {
            ledger.complete(publicContract(ledger, index + 1, 20, 0, 7, 10).id(), WORKER, 100);
        }
        ledger.complete(publicContract(ledger, 4, 20, 0, 7, 10).id(), OTHER, 100);

        assertEquals(new Ledger.Progress(10, 3), ledger.pendingProgress(WORKER));
        assertEquals(new Ledger.Progress(7, 1), ledger.pendingProgress(OTHER));
        assertEquals(new Ledger.DailyProgress(100, 10), ledger.dailySnapshot().get(WORKER));
        assertEquals(new Ledger.DailyProgress(100, 7), ledger.dailySnapshot().get(OTHER));
    }

    @Test
    void dailyBudgetResetsOnlyForwardAndBackwardsDatesCannotResetIt() {
        var ledger = new Ledger();
        ledger.complete(publicContract(ledger, 1, 20, 0, 7, 10).id(), WORKER, 100);
        ledger.complete(publicContract(ledger, 2, 20, 0, 7, 10).id(), WORKER, 101);
        ledger.complete(publicContract(ledger, 3, 20, 0, 7, 10).id(), WORKER, 100);
        ledger.complete(publicContract(ledger, 4, 20, 0, 7, 10).id(), WORKER, 99);

        assertEquals(new Ledger.Progress(17, 4), ledger.pendingProgress(WORKER));
        assertEquals(new Ledger.DailyProgress(101, 10), ledger.dailySnapshot().get(WORKER));

        ledger.complete(publicContract(ledger, 5, 20, 0, 7, 10).id(), WORKER, 102);
        assertEquals(new Ledger.Progress(24, 5), ledger.pendingProgress(WORKER));
        assertEquals(new Ledger.DailyProgress(102, 7), ledger.dailySnapshot().get(WORKER));
    }

    @Test
    void contractSpecificCapsShareTheWorkersSpentBudgetAndZeroRewardsStillCountMissions() {
        var ledger = new Ledger();
        ledger.complete(publicContract(ledger, 1, 20, 0, 8, 10).id(), WORKER, 100);
        ledger.complete(publicContract(ledger, 2, 20, 0, 8, 5).id(), WORKER, 100);
        ledger.complete(publicContract(ledger, 3, 20, 0, 0, 10).id(), WORKER, 100);
        ledger.complete(publicContract(ledger, 4, 20, 0, 8, 0).id(), WORKER, 100);

        assertEquals(new Ledger.Progress(8, 4), ledger.pendingProgress(WORKER));
        assertEquals(new Ledger.DailyProgress(100, 8), ledger.dailySnapshot().get(WORKER));
    }

    @Test
    void completedPublicContractCannotRepeatMoneyMaterialsReputationOrMissions() {
        var ledger = new Ledger();
        var contract = publicContract(ledger, 1, 20, 500, 7, 10);
        ledger.complete(contract.id(), WORKER, 100);
        var before = snapshot(ledger);

        assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), WORKER, 101));
        assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), OTHER, 101));
        assertThrows(IllegalStateException.class, () -> ledger.cancel(contract.id(), KINGDOM_ACCOUNT));

        assertEquals(before, snapshot(ledger));
    }

    @Test
    void progressionClaimRequiresExactCurrentSnapshotAndNeverResetsTheDailyBudget() {
        var ledger = new Ledger();
        ledger.complete(publicContract(ledger, 1, 20, 0, 3, 10).id(), WORKER, 100);
        var stale = ledger.pendingProgress(WORKER);
        ledger.complete(publicContract(ledger, 2, 20, 0, 3, 10).id(), WORKER, 100);

        assertThrows(IllegalStateException.class, () -> ledger.takeProgress(WORKER, stale));
        assertEquals(new Ledger.Progress(6, 2), ledger.pendingProgress(WORKER));
        assertThrows(IllegalStateException.class,
                () -> ledger.takeProgress(WORKER, new Ledger.Progress(6, 1)));

        var exact = ledger.pendingProgress(WORKER);
        ledger.takeProgress(WORKER, exact);
        assertEquals(NONE, ledger.pendingProgress(WORKER));
        assertTrue(ledger.progressSnapshot().isEmpty());
        assertEquals(new Ledger.DailyProgress(100, 6), ledger.dailySnapshot().get(WORKER));
        assertThrows(IllegalStateException.class, () -> ledger.takeProgress(WORKER, exact));

        ledger.complete(publicContract(ledger, 3, 20, 0, 7, 10).id(), WORKER, 100);
        assertEquals(new Ledger.Progress(4, 1), ledger.pendingProgress(WORKER));
    }

    @Test
    void overflowingWorkerCreditOrTreasuryTaxCannotPartiallyCompleteAContract() {
        for (var recipient : List.of(WORKER, KINGDOM_ACCOUNT)) {
            var seed = new Ledger();
            var contract = publicContract(seed, 1, 20, 500, 3, 10);
            var ledger = new Ledger(seed.list(), Map.of(recipient, Map.of(CHRONICLES, Long.MAX_VALUE)),
                    Map.of(), Map.of(), Map.of());
            var before = snapshot(ledger);

            assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), WORKER, 100));

            assertEquals(before, snapshot(ledger));
        }
    }

    @Test
    void overflowingPublicMaterialStorageCannotCreateTaxWorkerCreditOrProgress() {
        var seed = new Ledger();
        var contract = publicContract(seed, 1, 20, 500, 3, 10);
        var ledger = new Ledger(seed.list(), Map.of(),
                Map.of(KINGDOM_ACCOUNT, List.of(new Ledger.Delivery(LOG, Integer.MAX_VALUE))),
                Map.of(), Map.of());
        var before = snapshot(ledger);

        assertThrows(IllegalStateException.class, () -> ledger.complete(contract.id(), WORKER, 100));

        assertEquals(before, snapshot(ledger));
    }

    @Test
    void pendingReputationAndMissionsSaturateWithoutBlockingFundedCompletion() {
        for (var progress : List.of(new Ledger.Progress(99, 1),
                new Ledger.Progress(1, Ledger.MAX_PENDING_MISSIONS))) {
            var seed = new Ledger();
            var contract = publicContract(seed, 1, 20, 500, 3, 10);
            var ledger = new Ledger(seed.list(), Map.of(), Map.of(), Map.of(WORKER, progress),
                    Map.of(WORKER, new Ledger.DailyProgress(99, 1)));
            ledger.complete(contract.id(), WORKER, 100);

            assertEquals(new Ledger.Progress(Math.min(100, progress.reputation() + 3),
                    Math.min(Ledger.MAX_PENDING_MISSIONS, progress.missions() + 1)), ledger.pendingProgress(WORKER));
            assertEquals(new Ledger.DailyProgress(100, 3), ledger.dailySnapshot().get(WORKER));
            assertEquals(19, ledger.credits(WORKER, CHRONICLES));
            assertEquals(1, ledger.credits(KINGDOM_ACCOUNT, CHRONICLES));
            assertEquals(List.of(new Ledger.Delivery(LOG, 64)), ledger.deliveries(KINGDOM_ACCOUNT));
            assertEquals(COMPLETED, ledger.list().getFirst().status());
        }
    }

    @Test
    void missionsContinueAtMaximumReputationAndSaturateAtTheSupportedLimit() {
        var seed = new Ledger();
        var contract = publicContract(seed, 1, 20, 500, 3, 0);
        var ledger = new Ledger(seed.list(), Map.of(), Map.of(),
                Map.of(WORKER, new Ledger.Progress(100, Ledger.MAX_PENDING_MISSIONS - 1)), Map.of());

        ledger.complete(contract.id(), WORKER, 100);
        assertEquals(new Ledger.Progress(100, Ledger.MAX_PENDING_MISSIONS), ledger.pendingProgress(WORKER));
        ledger.complete(publicContract(ledger, 2, 20, 500, 3, 0).id(), WORKER, 100);
        assertEquals(new Ledger.Progress(100, Ledger.MAX_PENDING_MISSIONS), ledger.pendingProgress(WORKER));
        assertEquals(38, ledger.credits(WORKER, CHRONICLES));
        assertEquals(2, ledger.credits(KINGDOM_ACCOUNT, CHRONICLES));
        assertEquals(List.of(new Ledger.Delivery(LOG, 128)), ledger.deliveries(KINGDOM_ACCOUNT));
    }

    @Test
    void creditsCanBeAddedAndTransferredWithoutMixingCurrenciesOrCreatingMoney() {
        var ledger = new Ledger();
        ledger.addCredit(AUTHOR, CHRONICLES, 20);
        ledger.addCredit(AUTHOR, EMERALD, 5);
        ledger.addCredit(KINGDOM_ACCOUNT, CHRONICLES, 2);

        ledger.transferCredit(AUTHOR, KINGDOM_ACCOUNT, CHRONICLES, 7);

        assertEquals(13, ledger.credits(AUTHOR, CHRONICLES));
        assertEquals(9, ledger.credits(KINGDOM_ACCOUNT, CHRONICLES));
        assertEquals(5, ledger.credits(AUTHOR, EMERALD));
        assertEquals(22, totalCredits(ledger, CHRONICLES));
        ledger.transferCredit(AUTHOR, WORKER, CHRONICLES, 13);
        assertEquals(0, ledger.credits(AUTHOR, CHRONICLES));
        assertEquals(13, ledger.credits(WORKER, CHRONICLES));
        assertEquals(Map.of(EMERALD, 5L), ledger.creditsSnapshot().get(AUTHOR));
    }

    @Test
    void failingCreditAddsAndTransfersLeaveBothAccountsUntouched() {
        var ledger = new Ledger();
        ledger.addCredit(AUTHOR, CHRONICLES, 20);
        ledger.addCredit(KINGDOM_ACCOUNT, CHRONICLES, Long.MAX_VALUE);
        var before = snapshot(ledger);

        assertThrows(IllegalStateException.class, () -> ledger.addCredit(KINGDOM_ACCOUNT, CHRONICLES, 1));
        assertThrows(IllegalStateException.class,
                () -> ledger.transferCredit(AUTHOR, KINGDOM_ACCOUNT, CHRONICLES, 1));
        assertThrows(IllegalStateException.class,
                () -> ledger.transferCredit(AUTHOR, WORKER, CHRONICLES, 21));
        assertThrows(IllegalStateException.class,
                () -> ledger.transferCredit(AUTHOR, WORKER, EMERALD, 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.addCredit(AUTHOR, CHRONICLES, -1));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.transferCredit(AUTHOR, WORKER, CHRONICLES, -1));

        assertEquals(before, snapshot(ledger));
    }

    @Test
    void zeroCreditOperationsDoNotCreateEntriesAndPersistedZerosAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ledger(List.of(), Map.of(AUTHOR, Map.of(CHRONICLES, 0L)), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Ledger(List.of(), Map.of(AUTHOR, Map.of(CHRONICLES, 0L)),
                        Map.of(), Map.of(), Map.of()));
        var ledger = new Ledger();
        ledger.addCredit(AUTHOR, CHRONICLES, 0);
        ledger.transferCredit(AUTHOR, WORKER, CHRONICLES, 0);
        assertTrue(ledger.creditsSnapshot().isEmpty());
        assertEquals(0, ledger.credits(AUTHOR, CHRONICLES));
        assertEquals(0, ledger.credits(WORKER, CHRONICLES));
    }

    @Test
    void copyAndFullRestorationKeepAllFiveKindsOfStateIndependent() {
        var original = new Ledger();
        original.complete(publicContract(original, 1, 20, 500, 7, 10).id(), WORKER, 100);
        publicContract(original, 2, 20, 500, 7, 10);
        var before = snapshot(original);
        var copied = original.copy();
        var restored = new Ledger(original.list(), original.creditsSnapshot(), original.deliveriesSnapshot(),
                original.progressSnapshot(), original.dailySnapshot());

        assertEquals(before, snapshot(copied));
        assertEquals(before, snapshot(restored));
        for (var independent : List.of(copied, restored)) {
            independent.takeCredit(WORKER, CHRONICLES, 19);
            independent.takeDelivery(KINGDOM_ACCOUNT, LOG, 64);
            independent.takeProgress(WORKER, independent.pendingProgress(WORKER));
            independent.complete(id(2), WORKER, 101);
        }

        assertEquals(before, snapshot(original));
        assertEquals(snapshot(copied), snapshot(restored));
        assertEquals(new Ledger.Progress(7, 1), restored.pendingProgress(WORKER));
        assertEquals(new Ledger.DailyProgress(101, 7), restored.dailySnapshot().get(WORKER));
    }

    @Test
    void fullRestorationCopiesInputMapsAndProgressSnapshotsAreImmutableAndDetached() {
        var contracts = new ArrayList<Ledger.Contract>();
        var balances = new HashMap<Ledger.Currency, Long>(Map.of(CHRONICLES, 10L));
        var credits = new HashMap<UUID, Map<Ledger.Currency, Long>>(Map.of(WORKER, balances));
        var materials = new ArrayList<>(List.of(new Ledger.Delivery(LOG, 64)));
        var deliveries = new HashMap<UUID, List<Ledger.Delivery>>(Map.of(KINGDOM_ACCOUNT, materials));
        var progress = new HashMap<UUID, Ledger.Progress>(Map.of(WORKER, new Ledger.Progress(7, 1)));
        var daily = new HashMap<UUID, Ledger.DailyProgress>(Map.of(WORKER, new Ledger.DailyProgress(100, 7)));
        var ledger = new Ledger(contracts, credits, deliveries, progress, daily);
        var before = snapshot(ledger);

        contracts.clear();balances.clear();credits.clear();materials.clear();deliveries.clear();
        progress.clear();daily.clear();

        assertEquals(before, snapshot(ledger));
        var progressSnapshot = ledger.progressSnapshot();
        var dailySnapshot = ledger.dailySnapshot();
        assertThrows(UnsupportedOperationException.class, progressSnapshot::clear);
        assertThrows(UnsupportedOperationException.class, dailySnapshot::clear);
        ledger.takeProgress(WORKER, ledger.pendingProgress(WORKER));
        ledger.complete(publicContract(ledger, 1, 20, 0, 7, 10).id(), WORKER, 101);
        assertEquals(new Ledger.Progress(7, 1), progressSnapshot.get(WORKER));
        assertEquals(new Ledger.DailyProgress(100, 7), dailySnapshot.get(WORKER));
    }

    @Test
    void invalidFundingTaxAndProgressMetadataCannotCreateContracts() {
        var ledger = new Ledger();
        assertThrows(IllegalArgumentException.class, () -> ledger.publish(id(1), AUTHOR, "Author", "Logs",
                LOG, 64, 20, CHRONICLES, KINGDOM, AUTHOR, 500, 3, 10));
        assertThrows(IllegalArgumentException.class, () -> ledger.publish(id(1), KINGDOM_ACCOUNT, "Kingdom", "Logs",
                LOG, 64, 20, CHRONICLES, KINGDOM, KINGDOM_ACCOUNT, 500, 3, 10));
        assertThrows(IllegalArgumentException.class, () -> ledger.publish(id(1), AUTHOR, "Author", "Logs",
                LOG, 64, 20, CHRONICLES, PLAYER, OTHER, 500, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.publish(id(1), AUTHOR, "Author", "Logs",
                LOG, 64, 20, CHRONICLES, PLAYER, AUTHOR, 500, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.publish(id(1), AUTHOR, "Author", "Logs",
                LOG, 64, 20, CHRONICLES, PLAYER, AUTHOR, 500, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> privateContract(ledger, 1, 20, CHRONICLES, -1));
        assertThrows(IllegalArgumentException.class, () -> privateContract(ledger, 1, 20, CHRONICLES, 10001));
        assertThrows(IllegalArgumentException.class, () -> publicContract(ledger, 1, 20, 500, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> publicContract(ledger, 1, 20, 500, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> publicContract(ledger, 1, 20, 500, 101, 10));
        assertThrows(IllegalArgumentException.class, () -> publicContract(ledger, 1, 20, 500, 1, 101));
        assertTrue(ledger.list().isEmpty());
    }

    @Test
    void invalidProgressRecordsOrRestoreBindingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Ledger.Progress(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Ledger.Progress(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Ledger.Progress(101, 0));
        assertThrows(IllegalArgumentException.class, () -> new Ledger.Progress(0, Ledger.MAX_PENDING_MISSIONS + 1));
        assertThrows(IllegalArgumentException.class, () -> new Ledger.DailyProgress(100, -1));
        assertThrows(IllegalArgumentException.class, () -> new Ledger.DailyProgress(100, 101));
        assertThrows(IllegalArgumentException.class,
                () -> new Ledger(List.of(), Map.of(), Map.of(), null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Ledger(List.of(), Map.of(), Map.of(), Map.of(), null));
        var nullProgress = new HashMap<UUID, Ledger.Progress>();
        nullProgress.put(WORKER, null);
        assertThrows(IllegalArgumentException.class,
                () -> new Ledger(List.of(), Map.of(), Map.of(), nullProgress, Map.of()));
        var nullDailyOwner = new HashMap<UUID, Ledger.DailyProgress>();
        nullDailyOwner.put(null, new Ledger.DailyProgress(100, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Ledger(List.of(), Map.of(), Map.of(), Map.of(), nullDailyOwner));
    }

    private static Ledger.Contract privateContract(Ledger ledger, int value, long reward,
                                                    Ledger.Currency currency, int tax) {
        return ledger.publish(id(value), AUTHOR, "Author", "Logs", LOG, 64, reward, currency,
                PLAYER, AUTHOR, tax, 0, 0);
    }

    private static Ledger.Contract publicContract(Ledger ledger, int value, long reward,
                                                   int tax, int reputation, int dailyLimit) {
        return ledger.publish(id(value), KINGDOM_ACCOUNT, "Kingdom", "Public works", LOG, 64,
                reward, CHRONICLES, KINGDOM, AUTHOR, tax, reputation, dailyLimit);
    }

    private static List<Object> snapshot(Ledger ledger) {
        return List.of(ledger.list(), ledger.creditsSnapshot(), ledger.deliveriesSnapshot(),
                ledger.progressSnapshot(), ledger.dailySnapshot());
    }

    private static long totalCredits(Ledger ledger, Ledger.Currency currency) {
        return ledger.creditsSnapshot().values().stream().mapToLong(b -> b.getOrDefault(currency, 0L)).sum();
    }

    private static UUID id(int value) {
        return new UUID(7, value);
    }
}
