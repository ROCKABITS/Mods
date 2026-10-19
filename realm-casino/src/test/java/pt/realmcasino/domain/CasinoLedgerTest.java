package pt.realmcasino.domain;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CasinoLedgerTest {
    private static final UUID OWNER = new UUID(0, 1), PLAYER = new UUID(0, 2), OTHER = new UUID(0, 3), MACHINE = new UUID(1, 1), ROUND = new UUID(2, 1);
    private CasinoLedger empty(CasinoLedger.Kind kind) {
        return new CasinoLedger().register(MACHINE, OWNER, kind, "minecraft:overworld", 123, "The Crown", 2, 100);
    }
    private CasinoLedger funded() { return empty(CasinoLedger.Kind.SLOTS).deposit(MACHINE, OWNER, 100_000); }
    private CasinoLedger start(CasinoLedger ledger, long stake) {
        return ledger.startBet(MACHINE, PLAYER, stake, ROUND, ledger.machine(MACHINE).orElseThrow().version(), 1000);
    }
    private long total(CasinoLedger ledger) {
        return ledger.machines().values().stream().mapToLong(CasinoLedger.Machine::bank).sum()
                + ledger.credits().values().stream().mapToLong(Long::longValue).sum();
    }
    @Test void completeDepositBetWinWithdrawAndClaimsConserveWalletPlusLedgerCents() {
        long ownerWallet = 1_000_000, playerWallet = 10_000;
        CasinoLedger ledger = empty(CasinoLedger.Kind.SLOTS);
        ownerWallet -= 100_000; ledger = ledger.deposit(MACHINE, OWNER, 100_000);
        assertEquals(1_010_000, ownerWallet + playerWallet + total(ledger));
        playerWallet -= 100; ledger = start(ledger, 100);
        assertEquals(1_010_000, ownerWallet + playerWallet + total(ledger));
        ledger = ledger.settle(ROUND, 10_000, 1100);
        assertEquals(10_000, ledger.pending(PLAYER)); assertEquals(90_100, ledger.machine(MACHINE).orElseThrow().available());
        assertEquals(1_010_000, ownerWallet + playerWallet + total(ledger));
        ledger = ledger.withdraw(MACHINE, OWNER, 90_100);
        assertEquals(90_100, ledger.pending(OWNER)); assertEquals(0, ledger.machine(MACHINE).orElseThrow().bank());
        ownerWallet += 90_100; ledger = ledger.claim(OWNER, 90_100);
        playerWallet += 10_000; ledger = ledger.claim(PLAYER, 10_000);
        assertEquals(1_010_000, ownerWallet + playerWallet + total(ledger));
        assertTrue(ledger.credits().isEmpty()); assertTrue(ledger.rounds().isEmpty());
    }
    @Test void insufficientMaximumReturnFailsBeforeAnyPreparedStateChanges() {
        CasinoLedger ledger = empty(CasinoLedger.Kind.SLOTS).deposit(MACHINE, OWNER, 9_899);
        assertThrows(DomainException.class, () -> start(ledger, 100));
        assertEquals(9899, ledger.machine(MACHINE).orElseThrow().bank()); assertTrue(ledger.rounds().isEmpty());
        CasinoLedger exact = empty(CasinoLedger.Kind.SLOTS).deposit(MACHINE, OWNER, 9_900);
        CasinoLedger reserved = start(exact, 100);
        assertEquals(10_000, reserved.machine(MACHINE).orElseThrow().reserved());
        assertEquals(0, reserved.machine(MACHINE).orElseThrow().available());
    }
    @Test void eachGameReservesItsActualPublishedWorstCaseGrossReturn() {
        for (CasinoLedger.Kind kind : CasinoLedger.Kind.values()) {
            CasinoLedger ledger = empty(kind).deposit(MACHINE, OWNER, 100_000);
            CasinoLedger active = start(ledger, 100);
            long expected = switch (kind) { case SLOTS -> 10_000; case ROULETTE -> 3600; case BLACKJACK -> 250; case PLINKO -> 3200; case WHEEL -> 500; case CHICKEN -> 884; };
            assertEquals(expected, active.rounds().get(ROUND).maxPayout());
            assertEquals(expected, active.machine(MACHINE).orElseThrow().reserved());
        }
    }
    @Test void settlementAndRefundAreExactlyOnceAndCannotReuseRecentRoundIds() {
        CasinoLedger finished = start(funded(), 100).settle(ROUND, 0, 1100);
        assertThrows(DomainException.class, () -> finished.settle(ROUND, 10_000, 1200));
        assertThrows(DomainException.class, () -> finished.refund(ROUND, 1200));
        assertThrows(DomainException.class, () -> finished.startBet(MACHINE, PLAYER, 100, ROUND, finished.machine(MACHINE).orElseThrow().version(), 1300));
        CasinoLedger refund = start(funded(), 100).refund(ROUND, 1100);
        assertEquals(100, refund.pending(PLAYER)); assertEquals(100_000, refund.machine(MACHINE).orElseThrow().bank());
    }
    @Test void staleMachineVersionsAndConcurrentGamesAreRejected() {
        CasinoLedger ledger = funded();
        assertThrows(DomainException.class, () -> ledger.startBet(MACHINE, PLAYER, 100, ROUND, 0, 1000));
        CasinoLedger active = start(ledger, 100);
        assertThrows(DomainException.class, () -> active.startBet(MACHINE, OTHER, 100, UUID.randomUUID(), active.machine(MACHINE).orElseThrow().version(), 1001));
        assertEquals(2, active.machine(MACHINE).orElseThrow().version());
    }
    @Test void reservedMoneyCannotBeWithdrawnButAllAvailableMoneyCanMoveToOwnerPending() {
        CasinoLedger active = start(funded(), 100);
        assertThrows(DomainException.class, () -> active.withdraw(MACHINE, OWNER, 90_101));
        CasinoLedger withdrawn = active.withdraw(MACHINE, OWNER, 90_100);
        assertEquals(10_000, withdrawn.machine(MACHINE).orElseThrow().bank());
        assertEquals(10_000, withdrawn.machine(MACHINE).orElseThrow().reserved());
        assertEquals(90_100, withdrawn.pending(OWNER));
        CasinoLedger paid = withdrawn.settle(ROUND, 10_000, 1200);
        assertEquals(0, paid.machine(MACHINE).orElseThrow().bank());
        assertEquals(100_100, total(paid));
    }
    @Test void onlyTheOwnerCanFundWithdrawConfigureOrRemoveAMachine() {
        CasinoLedger ledger = funded();
        assertThrows(DomainException.class, () -> ledger.deposit(MACHINE, OTHER, 100));
        assertThrows(DomainException.class, () -> ledger.withdraw(MACHINE, OTHER, 100));
        assertThrows(DomainException.class, () -> ledger.configure(MACHINE, OTHER, "Mine", 2, 100));
        assertThrows(DomainException.class, () -> ledger.remove(MACHINE, OTHER));
    }
    @Test void fundedMachinesCannotDisappearAndEmptyOnesDoNotKeepAPhantomLocation() {
        CasinoLedger ledger = funded();
        assertThrows(DomainException.class, () -> ledger.remove(MACHINE, OWNER));
        CasinoLedger empty = ledger.withdraw(MACHINE, OWNER, 100_000).remove(MACHINE, OWNER);
        assertTrue(empty.machine(MACHINE).isEmpty()); assertTrue(empty.at("minecraft:overworld", 123).isEmpty());
        assertEquals(100_000, empty.pending(OWNER));
    }
    @Test void playerWorstCaseCreditCapacityIsReservedBeforeAcceptingTheStake() {
        CasinoLedger original = funded();
        CasinoLedger ledger = new CasinoLedger(original.machines().values(), List.of(), Map.of(PLAYER, Money.MAX_CENTS - 9_999), List.of());
        assertThrows(DomainException.class, () -> start(ledger, 100));
        assertEquals(Money.MAX_CENTS - 9_999, ledger.pending(PLAYER)); assertTrue(ledger.rounds().isEmpty());
    }
    @Test void bankOverflowAndInvalidPayoutsNeverModifyTheOriginal() {
        CasinoLedger original = funded();
        assertThrows(DomainException.class, () -> original.deposit(MACHINE, OWNER, Money.MAX_CENTS));
        CasinoLedger active = start(original, 100);
        assertThrows(DomainException.class, () -> active.settle(ROUND, 101, 1200));
        assertThrows(DomainException.class, () -> active.settle(ROUND, 10_001, 1200));
        assertEquals(100_000, original.machine(MACHINE).orElseThrow().bank());
        assertEquals(1, active.rounds().size());
    }
    @Test void immutableSnapshotsAndViewsCannotMutateLiveState() {
        ArrayList<CasinoLedger.Machine> supplied = new ArrayList<>(funded().machines().values());
        CasinoLedger ledger = new CasinoLedger(supplied, List.of(), Map.of(), List.of());
        supplied.clear(); assertEquals(1, ledger.machines().size());
        assertThrows(UnsupportedOperationException.class, () -> ledger.machines().clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.rounds().clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.credits().clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.history().clear());
    }
    @Test void strictSnapshotValidationRejectsDuplicateIdsLocationsAndOrphanReservations() {
        CasinoLedger.Machine machine = funded().machine(MACHINE).orElseThrow();
        assertThrows(DomainException.class, () -> new CasinoLedger(List.of(machine, machine), List.of(), Map.of(), List.of()));
        CasinoLedger.Machine duplicatePlace = new CasinoLedger.Machine(UUID.randomUUID(), OTHER, machine.kind(), machine.dimension(), machine.position(), "Other", 2, 100, 0, 0, 0);
        assertThrows(DomainException.class, () -> new CasinoLedger(List.of(machine, duplicatePlace), List.of(), Map.of(), List.of()));
        CasinoLedger.Round orphan = new CasinoLedger.Round(ROUND, UUID.randomUUID(), PLAYER, 100, 10_000, 1000);
        assertThrows(DomainException.class, () -> new CasinoLedger(List.of(machine), List.of(orphan), Map.of(), List.of()));
        CasinoLedger.Machine falseReserve = new CasinoLedger.Machine(MACHINE, OWNER, machine.kind(), machine.dimension(), machine.position(), machine.name(), 2, 100, 100_000, 10_000, 1);
        assertThrows(DomainException.class, () -> new CasinoLedger(List.of(falseReserve), List.of(), Map.of(), List.of()));
    }
    @Test void strictSnapshotValidationRejectsWrongLiabilityAndDuplicateSettlements() {
        CasinoLedger active = start(funded(), 100);
        CasinoLedger.Round wrong = new CasinoLedger.Round(ROUND, MACHINE, PLAYER, 100, 9999, 1000);
        assertThrows(DomainException.class, () -> new CasinoLedger(active.machines().values(), List.of(wrong), Map.of(), List.of()));
        CasinoLedger.History entry = new CasinoLedger.History(ROUND, MACHINE, PLAYER, 100, 0, 1200);
        assertThrows(DomainException.class, () -> new CasinoLedger(List.of(), List.of(), Map.of(), List.of(entry, entry)));
        assertThrows(DomainException.class, () -> new CasinoLedger(active.machines().values(), active.rounds().values(), Map.of(), List.of(entry)));
    }
    @Test void persistedRoundAndPendingSnapshotRestoreAllBalancesExactly() {
        CasinoLedger original = start(funded(), 100);
        CasinoLedger restored = new CasinoLedger(original.machines().values(), original.rounds().values(), original.credits(), original.history());
        assertEquals(original.machines(), restored.machines()); assertEquals(original.rounds(), restored.rounds());
        CasinoLedger settled = restored.settle(ROUND, 8000, 1200);
        assertEquals(8000, settled.pending(PLAYER)); assertEquals(92_100, settled.machine(MACHINE).orElseThrow().bank());
        assertEquals(100_100, total(settled));
    }
    @Test void boundedHistoryNeverTruncatesUnpaidMoney() {
        CasinoLedger ledger = funded();
        for (int round = 0; round < CasinoLedger.MAX_HISTORY + 5; round++) {
            UUID id = new UUID(9, round);
            ledger = ledger.startBet(MACHINE, PLAYER, 2, id, ledger.machine(MACHINE).orElseThrow().version(), round);
            ledger = ledger.settle(id, 2, round);
        }
        assertEquals(CasinoLedger.MAX_HISTORY, ledger.history().size());
        assertEquals((CasinoLedger.MAX_HISTORY + 5) * 2, ledger.pending(PLAYER));
        assertEquals(100_000, ledger.machine(MACHINE).orElseThrow().bank());
        assertEquals(5, ledger.history().getFirst().id().getLeastSignificantBits());
    }
    @Test void limitsNamesPartialClaimsAndStableMachineIdentityAreValidated() {
        CasinoLedger ledger = funded().configure(MACHINE, OWNER, "Ruby Hall", 10, 200);
        assertEquals("Ruby Hall", ledger.machine(MACHINE).orElseThrow().name());
        assertEquals(MACHINE, ledger.at("minecraft:overworld", 123).orElseThrow().id());
        assertThrows(DomainException.class, () -> ledger.configure(MACHINE, OWNER, "Bad\nName", 10, 200));
        assertThrows(DomainException.class, () -> ledger.configure(MACHINE, OWNER, "Good", 200, 10));
        CasinoLedger credited = ledger.withdraw(MACHINE, OWNER, 500);
        CasinoLedger partial = credited.claim(OWNER, 200);
        assertEquals(300, partial.pending(OWNER));
        assertThrows(DomainException.class, () -> partial.claim(OWNER, 301));
        assertThrows(DomainException.class, () -> partial.claim(OTHER, 1));
    }
    @Test void machineCapacityRefusesNewFurnitureWithoutDroppingExistingRegistrations() {
        ArrayList<CasinoLedger.Machine> machines = new ArrayList<>();
        for (int i = 0; i < CasinoLedger.MAX_MACHINES; i++)
            machines.add(new CasinoLedger.Machine(new UUID(50, i), OWNER, CasinoLedger.Kind.SLOTS,
                    "minecraft:overworld", i, "Crown", 2, 100, 0, 0, 0));
        CasinoLedger full = new CasinoLedger(machines, List.of(), Map.of(), List.of());
        assertThrows(DomainException.class, () -> full.register(MACHINE, OWNER, CasinoLedger.Kind.SLOTS,
                "minecraft:overworld", -1, "Extra", 2, 100));
        assertEquals(CasinoLedger.MAX_MACHINES, full.machines().size());
        assertEquals(new UUID(50, 0), full.at("minecraft:overworld", 0).orElseThrow().id());
    }
    @Test void creditorCapacityIsReservedBeforeBetAndCanBeFreedByAnExactClaim() {
        java.util.LinkedHashMap<UUID, Long> credits = new java.util.LinkedHashMap<>();
        for (int i = 0; i < CasinoLedger.MAX_CREDITORS; i++) credits.put(new UUID(60, i), 1L);
        CasinoLedger ledger = new CasinoLedger(funded().machines().values(), List.of(), credits, List.of());
        assertThrows(DomainException.class, () -> start(ledger, 100));
        assertThrows(DomainException.class, () -> ledger.withdraw(MACHINE, OWNER, 1));
        CasinoLedger freed = ledger.claim(new UUID(60, 0), 1);
        CasinoLedger active = start(freed, 100);
        CasinoLedger settled = active.settle(ROUND, 10_000, 1200);
        assertEquals(CasinoLedger.MAX_CREDITORS, settled.credits().size());
        assertEquals(10_000, settled.pending(PLAYER));
        assertEquals(0, settled.pending(new UUID(60, 0)));
    }
    @Test void activeRoundCapacityAndDuplicatePersistedGameIdsAreStrict() {
        ArrayList<CasinoLedger.Machine> machines = new ArrayList<>();
        ArrayList<CasinoLedger.Round> rounds = new ArrayList<>();
        for (int i = 0; i < CasinoLedger.MAX_ROUNDS; i++) {
            UUID machine = new UUID(70, i);
            machines.add(new CasinoLedger.Machine(machine, OWNER, CasinoLedger.Kind.SLOTS,
                    "minecraft:overworld", i, "Crown", 2, 100, 200, 200, 1));
            rounds.add(new CasinoLedger.Round(new UUID(71, i), machine, PLAYER, 2, 200, 1000));
        }
        CasinoLedger ledger = new CasinoLedger(machines, rounds, Map.of(), List.of());
        assertThrows(DomainException.class, () -> ledger.startBet(new UUID(70, 0), OTHER, 2,
                UUID.randomUUID(), 1, 1001));
        assertEquals(CasinoLedger.MAX_ROUNDS, ledger.rounds().size());
        assertEquals(200, ledger.machine(new UUID(70, 0)).orElseThrow().reserved());
        assertThrows(DomainException.class, () -> new CasinoLedger(machines,
                List.of(rounds.getFirst(), rounds.getFirst()), Map.of(), List.of()));
    }
}
