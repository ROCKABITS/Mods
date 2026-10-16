package pt.crownledger.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pt.crownledger.domain.Policy.TaxMode.*;
import static pt.crownledger.domain.Treasury.Currency.*;
import static pt.crownledger.domain.Treasury.Kind.*;

class TreasuryTest {
    private static final UUID MANAGER = new UUID(0, 100);
    private static final UUID ALICE = new UUID(0, 101);
    private static final UUID BOB = new UUID(0, 102);
    private static final UUID CAROL = new UUID(0, 103);
    private static final long START = 1_700_000_000_000L;
    private static final long HOUR = 3_600_000L;

    @Test
    void firstCycleStartsAtCurrentTimeAndNeverPaysHistoricalPeriods() {
        var treasury = new Treasury();
        assertEquals(-1, treasury.periodStartedAt());
        assertEquals(-1, treasury.nextCycleAt());

        assertTrue(treasury.cycle(START));

        assertEquals(START, treasury.periodStartedAt());
        assertEquals(START + 168 * HOUR, treasury.nextCycleAt());
        assertFalse(treasury.cycle(START));
        assertFalse(treasury.cycle(START + HOUR));
        assertNull(treasury.pendingBatch());
        assertTrue(treasury.claimsSnapshot().isEmpty());
        assertTrue(treasury.taxDueSnapshot().isEmpty());
    }

    @Test
    void fundingAndSpendingAreAuditedAndNeverMixCurrencies() {
        var treasury = new Treasury();
        treasury.addFunds(EMERALD, 20, DEPOSIT, ALICE, "Voluntary deposit", START);
        treasury.addFunds(CHRONICLES, 90, DEPOSIT, BOB, "Coin deposit", START + 1);
        treasury.removeFunds(EMERALD, 7, CONTRACT_FUNDING, MANAGER, "Public road", START + 2);

        assertEquals(13, treasury.balance(EMERALD));
        assertEquals(90, treasury.balance(CHRONICLES));
        var latest = treasury.history().getFirst();
        assertEquals(CONTRACT_FUNDING, latest.kind());
        assertEquals(7, latest.amount());
        assertEquals(EMERALD, latest.currency());
        assertEquals(MANAGER, latest.actor());
        assertEquals("Public road", latest.details());
        assertEquals(START + 2, latest.time());
    }

    @Test
    void zeroAmountsDoNotCreateHistoryAndInvalidSpendingDoesNotMutateState() {
        var treasury = new Treasury();
        treasury.addFunds(EMERALD, 5, DEPOSIT, ALICE, "Deposit", START);
        var before = treasury.snapshot();

        treasury.addFunds(EMERALD, 0, DEPOSIT, ALICE, "Zero", START + 1);
        treasury.removeFunds(EMERALD, 0, CONTRACT_FUNDING, MANAGER, "Zero", START + 1);
        assertThrows(IllegalArgumentException.class,
                () -> treasury.addFunds(EMERALD, -1, DEPOSIT, ALICE, "Invalid", START + 1));
        assertThrows(IllegalArgumentException.class,
                () -> treasury.removeFunds(EMERALD, -1, CONTRACT_FUNDING, MANAGER, "Invalid", START + 1));
        assertThrows(IllegalStateException.class,
                () -> treasury.removeFunds(EMERALD, 6, CONTRACT_FUNDING, MANAGER, "Unfunded", START + 1));
        assertThrows(IllegalStateException.class,
                () -> treasury.removeFunds(CHRONICLES, 1, CONTRACT_FUNDING, MANAGER, "Wrong currency", START + 1));

        assertEquals(before, treasury.snapshot());
    }

    @Test
    void payrollUsesAccumulatedActivityAndReservesEveryoneBeforeAnyClaim() {
        var treasury = initialized(policy(EMERALD, PERCENTAGE, 20, 0));
        treasury.recordActivity(ALICE, "Alice", 4, START + 1);
        treasury.recordActivity(ALICE, "Alice", 6, START + 2);
        treasury.recordActivity(BOB, "Bob", 10, START + 3);
        treasury.recordActivity(CAROL, "Carol", 9, START + 4);
        treasury.addFunds(EMERALD, 40, DEPOSIT, MANAGER, "Payroll budget", START + 5);

        assertEquals(10, treasury.activitySnapshot().get(ALICE).minutes());
        assertTrue(treasury.cycle(START + HOUR));

        assertEquals(20, treasury.claims(ALICE, EMERALD));
        assertEquals(20, treasury.claims(BOB, EMERALD));
        assertEquals(0, treasury.claims(CAROL, EMERALD));
        assertEquals(0, treasury.balance(EMERALD));
        assertTrue(treasury.activitySnapshot().isEmpty());
        assertTrue(treasury.taxDueSnapshot().isEmpty());
        assertNull(treasury.pendingBatch());
        assertEquals(START + HOUR, treasury.periodStartedAt());
        assertEquals(START + 2 * HOUR, treasury.nextCycleAt());
    }

    @Test
    void insufficientBudgetFreezesWholePayrollWithoutPartiallyPayingOrTaxingAnyone() {
        var treasury = initialized(policy(EMERALD, FIXED, 20, 5));
        eligible(treasury);
        treasury.addFunds(EMERALD, 29, DEPOSIT, MANAGER, "Almost funded", START + 1);

        assertTrue(treasury.cycle(START + HOUR));

        var batch = treasury.pendingBatch();
        assertNotNull(batch);
        assertEquals(Map.of(ALICE, "Alice", BOB, "Bob"), batch.eligible());
        assertEquals(15, batch.netSalary());
        assertEquals(0, batch.walletTax());
        assertEquals(30, batch.totalNet());
        assertEquals(29, treasury.balance(EMERALD));
        assertTrue(treasury.claimsSnapshot().isEmpty());
        assertTrue(treasury.taxDueSnapshot().isEmpty());
        assertFalse(treasury.settlePending(START + HOUR + 1));
        assertFalse(treasury.cycle(START + HOUR + 2));

        treasury.addFunds(EMERALD, 1, DEPOSIT, CAROL, "Complete budget", START + HOUR + 3);
        assertTrue(treasury.settlePending(START + 3 * HOUR));
        assertEquals(15, treasury.claims(ALICE, EMERALD));
        assertEquals(15, treasury.claims(BOB, EMERALD));
        assertEquals(0, treasury.balance(EMERALD));
        assertEquals(START + 3 * HOUR, treasury.periodStartedAt());
        assertEquals(START + 4 * HOUR, treasury.nextCycleAt());
    }

    @Test
    void frozenPayrollIgnoresLaterPolicyCurrencyActivityAndRecipientChanges() {
        var treasury = initialized(policy(CHRONICLES, FIXED, 20, 5));
        eligible(treasury);
        treasury.cycle(START + HOUR);
        var frozen = treasury.pendingBatch();

        treasury.setPolicy(policy(EMERALD, FIXED, 100, 80), MANAGER, START + HOUR + 1);
        treasury.recordActivity(CAROL, "Carol", 100, START + HOUR + 2);
        treasury.recordActivity(ALICE, "Renamed Alice", 100, START + HOUR + 3);
        treasury.addFunds(EMERALD, 1000, DEPOSIT, MANAGER, "Other currency", START + HOUR + 4);
        assertFalse(treasury.settlePending(START + HOUR + 5));
        assertEquals(frozen, treasury.pendingBatch());
        assertThrows(UnsupportedOperationException.class, () -> frozen.eligible().clear());

        treasury.addFunds(CHRONICLES, 30, DEPOSIT, MANAGER, "Frozen payroll", START + 2 * HOUR);
        assertTrue(treasury.settlePending(START + 2 * HOUR));
        assertEquals(15, treasury.claims(ALICE, CHRONICLES));
        assertEquals(15, treasury.claims(BOB, CHRONICLES));
        assertEquals(0, treasury.claims(CAROL, CHRONICLES));
        assertEquals(0, treasury.claims(ALICE, EMERALD));
        assertEquals(1000, treasury.balance(EMERALD));
        assertTrue(treasury.activitySnapshot().isEmpty());
    }

    @Test
    void fixedTaxIsWithheldFromSalaryAndConservesTheTreasuryBudget() {
        var treasury = initialized(policy(CHRONICLES, FIXED, 20, 3));
        eligible(treasury);
        treasury.addFunds(CHRONICLES, 40, DEPOSIT, MANAGER, "Gross wages", START + 1);

        assertTrue(treasury.cycle(START + HOUR));

        assertEquals(17, treasury.claims(ALICE, CHRONICLES));
        assertEquals(17, treasury.claims(BOB, CHRONICLES));
        assertEquals(6, treasury.balance(CHRONICLES));
        assertEquals(40, treasury.balance(CHRONICLES) + claimTotal(treasury, CHRONICLES));
        assertTrue(treasury.taxDueSnapshot().isEmpty());
    }

    @Test
    void taxBeyondSalaryBecomesOneWalletDueAndPartialCollectionWaivesTheRest() {
        var treasury = initialized(policy(CHRONICLES, FIXED, 5, 12));
        eligible(treasury);

        assertTrue(treasury.cycle(START + HOUR));

        assertEquals(0, treasury.claims(ALICE, CHRONICLES));
        assertEquals(7, treasury.taxDue(ALICE, CHRONICLES));
        assertEquals(7, treasury.taxDue(BOB, CHRONICLES));
        assertEquals(0, treasury.balance(CHRONICLES));

        treasury.settleTax(ALICE, CHRONICLES, 3, "Alice", START + HOUR + 1);
        assertEquals(0, treasury.taxDue(ALICE, CHRONICLES));
        assertEquals(7, treasury.taxDue(BOB, CHRONICLES));
        assertEquals(3, treasury.balance(CHRONICLES));
        treasury.settleTax(BOB, CHRONICLES, 0, "Bob", START + HOUR + 2);
        assertTrue(treasury.taxDueSnapshot().isEmpty());
        assertEquals(3, treasury.balance(CHRONICLES));
    }

    @Test
    void salaryClaimsAreReservedAcrossLaterSpendingAndSupportExactPartialWithdrawal() {
        var treasury = initialized(policy(EMERALD, PERCENTAGE, 20, 0));
        treasury.recordActivity(ALICE, "Alice", 10, START + 1);
        treasury.addFunds(EMERALD, 25, DEPOSIT, MANAGER, "Wages", START + 2);
        treasury.cycle(START + HOUR);
        treasury.removeFunds(EMERALD, 5, CONTRACT_FUNDING, MANAGER, "Spend remainder", START + HOUR + 1);

        treasury.takeSalary(ALICE, EMERALD, 7, "Alice", START + HOUR + 2);
        assertEquals(13, treasury.claims(ALICE, EMERALD));
        assertEquals(0, treasury.balance(EMERALD));
        var before = treasury.snapshot();
        assertThrows(IllegalStateException.class,
                () -> treasury.takeSalary(ALICE, EMERALD, 14, "Alice", START + HOUR + 3));
        assertThrows(IllegalStateException.class,
                () -> treasury.takeSalary(BOB, EMERALD, 1, "Bob", START + HOUR + 3));
        assertThrows(IllegalStateException.class,
                () -> treasury.takeSalary(ALICE, CHRONICLES, 1, "Alice", START + HOUR + 3));
        assertEquals(before, treasury.snapshot());

        treasury.takeSalary(ALICE, EMERALD, 13, "Alice", START + HOUR + 4);
        assertTrue(treasury.claimsSnapshot().isEmpty());
        assertEquals(0, treasury.balance(EMERALD));
    }

    @Test
    void settlementAndRepeatedCyclesCannotAllocateTheSamePayrollTwice() {
        var treasury = initialized(policy(EMERALD, PERCENTAGE, 20, 0));
        eligible(treasury);
        treasury.addFunds(EMERALD, 100, DEPOSIT, MANAGER, "Budget", START + 1);
        treasury.cycle(START + HOUR);
        var before = treasury.snapshot();

        assertFalse(treasury.settlePending(START + HOUR + 1));
        assertFalse(treasury.cycle(START + HOUR));
        assertFalse(treasury.cycle(START + HOUR + 1));

        assertEquals(before, treasury.snapshot());
        assertEquals(40, claimTotal(treasury, EMERALD));
        assertEquals(60, treasury.balance(EMERALD));
    }

    @Test
    void cancelledPendingPayrollCreatesNoClaimsOrTaxesAndRestartsWithoutBackpay() {
        var treasury = initialized(policy(EMERALD, FIXED, 20, 5));
        eligible(treasury);
        treasury.addFunds(EMERALD, 1, DEPOSIT, MANAGER, "Insufficient", START + 1);
        treasury.cycle(START + HOUR);

        treasury.cancelPending(MANAGER, START + 100 * HOUR);

        assertNull(treasury.pendingBatch());
        assertEquals(1, treasury.balance(EMERALD));
        assertTrue(treasury.claimsSnapshot().isEmpty());
        assertTrue(treasury.taxDueSnapshot().isEmpty());
        assertTrue(treasury.activitySnapshot().isEmpty());
        assertEquals(START + 100 * HOUR, treasury.periodStartedAt());
        assertEquals(START + 101 * HOUR, treasury.nextCycleAt());
        assertEquals(PAYROLL_CANCELLED, treasury.history().getFirst().kind());
        assertFalse(treasury.cycle(START + 100 * HOUR + 1));
    }

    @Test
    void changingPolicyPreservesCurrentDeadlineAndAppliesNewPeriodAfterClosingIt() {
        var treasury = new Treasury();
        treasury.cycle(START);
        var oldDeadline = treasury.nextCycleAt();
        var replacement = new Policy(0, PERCENTAGE, 500, 0, 0, 2, 10, CHRONICLES, 2, 6);

        treasury.setPolicy(replacement, MANAGER, START + HOUR);

        assertEquals(oldDeadline, treasury.nextCycleAt());
        assertEquals(START, treasury.periodStartedAt());
        assertFalse(treasury.cycle(START + 2 * HOUR));
        assertTrue(treasury.cycle(oldDeadline));
        assertEquals(oldDeadline, treasury.periodStartedAt());
        assertEquals(oldDeadline + 2 * HOUR, treasury.nextCycleAt());
        assertEquals(replacement, treasury.policy());
    }

    @Test
    void downtimeClosesOneActivityPeriodAndStartsNextAtActualSettlementTime() {
        var treasury = initialized(policy(EMERALD, PERCENTAGE, 20, 0));
        treasury.recordActivity(ALICE, "Alice", 10, START + 1);
        treasury.addFunds(EMERALD, 100, DEPOSIT, MANAGER, "Budget", START + 2);

        assertTrue(treasury.cycle(START + 1000 * HOUR));

        assertEquals(20, treasury.claims(ALICE, EMERALD));
        assertEquals(80, treasury.balance(EMERALD));
        assertEquals(START + 1000 * HOUR, treasury.periodStartedAt());
        assertEquals(START + 1001 * HOUR, treasury.nextCycleAt());
        assertFalse(treasury.cycle(START + 1000 * HOUR));
    }

    @Test
    void fullSnapshotRestartPreservesFrozenUnfundedPayrollAndSettlesItOnce() {
        var original = initialized(policy(CHRONICLES, FIXED, 20, 5));
        eligible(original);
        original.cycle(START + HOUR);
        var before = original.snapshot();

        var restored = new Treasury(before);
        assertEquals(before, restored.snapshot());
        restored.addFunds(CHRONICLES, 30, DEPOSIT, MANAGER, "After restart", START + 2 * HOUR);
        assertTrue(restored.settlePending(START + 2 * HOUR));
        assertFalse(restored.settlePending(START + 2 * HOUR + 1));

        assertEquals(15, restored.claims(ALICE, CHRONICLES));
        assertEquals(15, restored.claims(BOB, CHRONICLES));
        assertEquals(before, original.snapshot());
        var secondRestart = new Treasury(restored.snapshot());
        assertFalse(secondRestart.settlePending(START + 2 * HOUR + 2));
        assertEquals(restored.snapshot(), secondRestart.snapshot());
    }

    @Test
    void copyKeepsBalancesReservedClaimsTaxesActivityAndHistoryIndependent() {
        var original = initialized(policy(CHRONICLES, FIXED, 20, 5));
        original.recordActivity(ALICE, "Alice", 10, START + 1);
        original.addFunds(CHRONICLES, 30, DEPOSIT, MANAGER, "Budget", START + 2);
        original.cycle(START + HOUR);
        original.recordActivity(BOB, "Bob", 4, START + HOUR + 1);
        var before = original.snapshot();

        var copy = original.copy();
        assertEquals(before, copy.snapshot());
        copy.takeSalary(ALICE, CHRONICLES, 5, "Alice", START + HOUR + 2);
        copy.removeFunds(CHRONICLES, 1, CONTRACT_FUNDING, MANAGER, "Copy only", START + HOUR + 3);
        copy.recordActivity(BOB, "Bob", 6, START + HOUR + 4);
        copy.setPolicy(policy(EMERALD, PERCENTAGE, 100, 0), MANAGER, START + HOUR + 5);

        assertEquals(before, original.snapshot());
        assertEquals(15, original.claims(ALICE, CHRONICLES));
        assertEquals(4, original.activitySnapshot().get(BOB).minutes());
        assertEquals(10, copy.claims(ALICE, CHRONICLES));
        assertEquals(10, copy.activitySnapshot().get(BOB).minutes());
    }

    @Test
    void balanceClaimsActivityHistoryAndSnapshotMapsAreImmutableAndDetached() {
        var treasury = initialized(policy(EMERALD, PERCENTAGE, 20, 0));
        treasury.recordActivity(ALICE, "Alice", 10, START + 1);
        treasury.addFunds(EMERALD, 30, DEPOSIT, MANAGER, "Budget", START + 2);
        treasury.cycle(START + HOUR);
        treasury.recordActivity(BOB, "Bob", 4, START + HOUR + 1);
        var balances = treasury.balances();
        var claims = treasury.claimsSnapshot();
        var activity = treasury.activitySnapshot();
        var history = treasury.history();
        var snapshot = treasury.snapshot();

        assertThrows(UnsupportedOperationException.class, balances::clear);
        assertThrows(UnsupportedOperationException.class, claims::clear);
        assertThrows(UnsupportedOperationException.class, () -> claims.get(ALICE).clear());
        assertThrows(UnsupportedOperationException.class, activity::clear);
        assertThrows(UnsupportedOperationException.class, history::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.balances().clear());
        treasury.takeSalary(ALICE, EMERALD, 20, "Alice", START + HOUR + 2);
        treasury.addFunds(EMERALD, 1, DEPOSIT, BOB, "Later", START + HOUR + 3);
        treasury.recordActivity(BOB, "Bob", 6, START + HOUR + 4);

        assertEquals(10, balances.get(EMERALD));
        assertEquals(20, claims.get(ALICE).get(EMERALD));
        assertEquals(4, activity.get(BOB).minutes());
        assertEquals(snapshot.history(), history);
        assertEquals(20, snapshot.claims().get(ALICE).get(EMERALD));
    }

    @Test
    void restorationCopiesMutableInputMapsAndRetainsStandaloneManager() {
        var balances = new HashMap<Treasury.Currency, Long>(Map.of(EMERALD, 50L));
        var salary = new HashMap<Treasury.Currency, Long>(Map.of(EMERALD, 20L));
        var claims = new HashMap<UUID, Map<Treasury.Currency, Long>>(Map.of(ALICE, salary));
        var walletTax = new HashMap<Treasury.Currency, Long>(Map.of(CHRONICLES, 7L));
        var taxes = new HashMap<UUID, Map<Treasury.Currency, Long>>(Map.of(BOB, walletTax));
        var activity = new HashMap<UUID, Treasury.Activity>(Map.of(ALICE, new Treasury.Activity("Alice", 10)));
        var history = new ArrayList<Treasury.History>();
        var input = new Treasury.Snapshot(Policy.DEFAULT, balances, claims, taxes, activity, history,
                null, START, START + 168 * HOUR, MANAGER);
        var treasury = new Treasury(input);
        var before = treasury.snapshot();

        balances.clear();salary.clear();claims.clear();walletTax.clear();taxes.clear();activity.clear();history.clear();

        assertEquals(before, treasury.snapshot());
        assertEquals(MANAGER, treasury.snapshot().standaloneManager());
        assertEquals(20, treasury.claims(ALICE, EMERALD));
        assertEquals(7, treasury.taxDue(BOB, CHRONICLES));
        treasury.takeSalary(ALICE, EMERALD, 10, "Alice", START + 1);
        treasury.settleTax(BOB, CHRONICLES, 3, "Bob", START + 2);
        assertEquals(20, before.claims().get(ALICE).get(EMERALD));
        assertEquals(7, before.taxDue().get(BOB).get(CHRONICLES));
    }

    @Test
    void auditHistoryKeepsLatestTwoHundredFiftySixEntriesInNewestFirstOrder() {
        var treasury = new Treasury();
        for (int index = 0; index < 300; index++) {
            treasury.addFunds(EMERALD, 1, DEPOSIT, ALICE, "Deposit " + index, START + index);
        }

        assertEquals(300, treasury.balance(EMERALD));
        assertEquals(256, treasury.history().size());
        assertEquals("Deposit 299", treasury.history().getFirst().details());
        assertEquals("Deposit 44", treasury.history().getLast().details());
    }

    @Test
    void depositOrTaxCollectionOverflowPreservesMoneyAssessmentsAndAuditHistory() {
        var treasury = restored(Policy.DEFAULT, Map.of(CHRONICLES, Long.MAX_VALUE), Map.of(),
                Map.of(ALICE, Map.of(CHRONICLES, 7L)), null);
        var before = treasury.snapshot();

        assertThrows(IllegalStateException.class,
                () -> treasury.addFunds(CHRONICLES, 1, DEPOSIT, ALICE, "Overflow", START + 1));
        assertThrows(IllegalStateException.class,
                () -> treasury.settleTax(ALICE, CHRONICLES, 1, "Alice", START + 2));

        assertEquals(before, treasury.snapshot());
        assertEquals(7, treasury.taxDue(ALICE, CHRONICLES));
    }

    @Test
    void salaryAllocationOverflowCannotPayEarlierCitizensOrConsumeTheFrozenBudget() {
        for (var overflowing : List.of(ALICE, BOB)) {
            var pending = new Treasury.PendingBatch(new UUID(1, 1), START + HOUR, EMERALD, 20, 0,
                    Map.of(ALICE, "Alice", BOB, "Bob"));
            var treasury = restored(policy(EMERALD, PERCENTAGE, 20, 0), Map.of(EMERALD, 40L),
                    Map.of(overflowing, Map.of(EMERALD, Long.MAX_VALUE)), Map.of(), pending);
            var before = treasury.snapshot();

            assertThrows(IllegalStateException.class, () -> treasury.settlePending(START + HOUR));

            assertEquals(before, treasury.snapshot());
            assertEquals(40, treasury.balance(EMERALD));
            assertEquals(pending, treasury.pendingBatch());
        }
    }

    @Test
    void assessmentOverflowCannotCreateOtherCitizensTaxesOrClearTheFrozenBatch() {
        for (var overflowing : List.of(ALICE, BOB)) {
            var pending = new Treasury.PendingBatch(new UUID(1, 1), START + HOUR, CHRONICLES, 0, 1,
                    Map.of(ALICE, "Alice", BOB, "Bob"));
            var treasury = restored(policy(CHRONICLES, FIXED, 0, 1), Map.of(), Map.of(),
                    Map.of(overflowing, Map.of(CHRONICLES, Long.MAX_VALUE)), pending);
            var before = treasury.snapshot();

            assertThrows(IllegalStateException.class, () -> treasury.settlePending(START + HOUR));

            assertEquals(before, treasury.snapshot());
        }
    }

    @Test
    void payrollProductAndDeadlineOverflowRejectBeforeChangingActivityOrPeriod() {
        var treasury = initialized(policy(EMERALD, PERCENTAGE, Long.MAX_VALUE, 0));
        eligible(treasury);
        var before = treasury.snapshot();
        assertThrows(IllegalArgumentException.class, () -> treasury.cycle(START + HOUR));
        assertEquals(before, treasury.snapshot());

        var uninitialized = new Treasury();
        var empty = uninitialized.snapshot();
        assertThrows(IllegalStateException.class, () -> uninitialized.cycle(Long.MAX_VALUE));
        assertEquals(empty, uninitialized.snapshot());
    }

    @Test
    void activitySaturatesAtIntegerMaximumAndBackwardsClockCannotAddMinutes() {
        var treasury = initialized(policy(EMERALD, PERCENTAGE, 20, 0));
        treasury.recordActivity(ALICE, "Alice", Integer.MAX_VALUE, START + 1);
        treasury.recordActivity(ALICE, "New Alice", Integer.MAX_VALUE, START + 2);
        assertEquals(new Treasury.Activity("New Alice", Integer.MAX_VALUE), treasury.activitySnapshot().get(ALICE));
        var before = treasury.snapshot();

        treasury.recordActivity(BOB, "Bob", 10, START - 1);
        assertFalse(treasury.cycle(START - 1));

        assertEquals(before, treasury.snapshot());
        assertThrows(IllegalArgumentException.class, () -> treasury.recordActivity(BOB, "Bob", 0, START + 3));
        assertThrows(IllegalArgumentException.class, () -> treasury.recordActivity(BOB, "Bob", -1, START + 3));
    }

    @Test
    void invalidRestoredBalancesClaimsAssessmentsAndPeriodsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> restored(Policy.DEFAULT, Map.of(EMERALD, -1L), Map.of(), Map.of(), null));
        for (var amount : new long[]{0, -1}) {
            assertThrows(IllegalArgumentException.class,
                    () -> restored(Policy.DEFAULT, Map.of(), Map.of(ALICE, Map.of(EMERALD, amount)), Map.of(), null));
            assertThrows(IllegalArgumentException.class,
                    () -> restored(Policy.DEFAULT, Map.of(), Map.of(), Map.of(ALICE, Map.of(EMERALD, amount)), null));
        }
        assertEquals(0, restored(Policy.DEFAULT, Map.of(EMERALD, 0L), Map.of(), Map.of(), null).balance(EMERALD));
        assertThrows(IllegalArgumentException.class, () -> new Treasury(new Treasury.Snapshot(Policy.DEFAULT,
                Map.of(), Map.of(), Map.of(), Map.of(), List.of(), null, START, START, null)));
        assertThrows(IllegalArgumentException.class, () -> new Treasury(new Treasury.Snapshot(Policy.DEFAULT,
                Map.of(), Map.of(), Map.of(), Map.of(ALICE, new Treasury.Activity("Alice", 1)), List.of(),
                null, -1, -1, null)));
        var future = new Treasury.PendingBatch(new UUID(1, 1), START + HOUR - 1, EMERALD, 20, 0, Map.of(ALICE, "Alice"));
        assertThrows(IllegalArgumentException.class,
                () -> restored(policy(EMERALD, PERCENTAGE, 20, 0), Map.of(), Map.of(), Map.of(), future));
    }

    @Test
    void pendingBatchCopiesRecipientsAndRejectsAnUnrepresentableTotalPayroll() {
        var eligible = new HashMap<UUID, String>(Map.of(ALICE, "Alice", BOB, "Bob"));
        var batch = new Treasury.PendingBatch(new UUID(1, 1), START, CHRONICLES, 100, 20, eligible);
        eligible.clear();

        assertEquals(2, batch.eligible().size());
        assertEquals(80, batch.netSalary());
        assertEquals(0, batch.walletTax());
        assertEquals(160, batch.totalNet());
        assertThrows(UnsupportedOperationException.class, () -> batch.eligible().clear());
        assertThrows(IllegalArgumentException.class, () -> new Treasury.PendingBatch(new UUID(1, 1), START,
                CHRONICLES, Long.MAX_VALUE, 0, Map.of(ALICE, "Alice", BOB, "Bob")));
        var taxOnly = new Treasury.PendingBatch(new UUID(1, 2), START, CHRONICLES, 5, Long.MAX_VALUE,
                Map.of(ALICE, "Alice"));
        assertEquals(Long.MAX_VALUE - 5, taxOnly.walletTax());
        assertEquals(0, taxOnly.totalNet());
    }

    @Test
    void standaloneManagementCanChangeAndClearWithoutChangingFundsOrReservedClaims() {
        var treasury = new Treasury();
        treasury.addFunds(EMERALD, 20, DEPOSIT, ALICE, "Public funds", START);
        treasury.setStandaloneManager(ALICE, MANAGER, START + 1);
        assertEquals(ALICE, treasury.standaloneManager());
        assertEquals(MANAGER_CHANGED, treasury.history().getFirst().kind());
        var copy = treasury.copy();

        copy.setStandaloneManager(null, MANAGER, START + 2);

        assertNull(copy.standaloneManager());
        assertEquals(ALICE, treasury.standaloneManager());
        assertEquals(20, copy.balance(EMERALD));
        assertEquals(20, treasury.balance(EMERALD));
    }

    @Test
    void marketPolicyAndTaxHistorySurviveSnapshotCopiesWithoutChangingOldObligations() {
        var policy = new Policy(30, FIXED, 1250, 7, 20, 24, 10, CHRONICLES, 2, 6, 500);
        var treasury = restored(policy, Map.of(EMERALD, 50L, CHRONICLES, 2000L),
                Map.of(ALICE, Map.of(CHRONICLES, 1700L)), Map.of(BOB, Map.of(CHRONICLES, 300L)), null);
        treasury.recordActivity(CAROL, "Carol", 4, START + 1);
        var before = treasury.snapshot();

        var copy = treasury.copy();
        copy.addFunds(CHRONICLES, 100, MARKET_TAX, null, "Market sales tax", START + 2);
        var restored = new Treasury(copy.snapshot());

        assertEquals(before, treasury.snapshot());
        assertEquals(copy.snapshot(), restored.snapshot());
        assertEquals(policy, restored.policy());
        assertEquals(500, restored.policy().marketTaxBasisPoints());
        assertEquals(2100, restored.balance(CHRONICLES));
        assertEquals(50, restored.balance(EMERALD));
        assertEquals(1700, restored.claims(ALICE, CHRONICLES));
        assertEquals(300, restored.taxDue(BOB, CHRONICLES));
        assertEquals(new Treasury.Activity("Carol", 4), restored.activitySnapshot().get(CAROL));
        assertEquals(MARKET_TAX, restored.history().getFirst().kind());
        assertEquals(100, restored.history().getFirst().amount());
        assertNull(restored.history().getFirst().actor());
    }

    @Test
    void marketRevenueCanCompleteFrozenPayrollWithoutReassessingTheSellerOrChangingTheRate() {
        var policy = new Policy(0, FIXED, 2500, 5, 20, 1, 10, EMERALD, 1, 3, 500);
        var treasury = initialized(policy);
        eligible(treasury);
        treasury.addFunds(EMERALD, 29, DEPOSIT, MANAGER, "Payroll budget", START + 1);
        treasury.cycle(START + HOUR);
        assertNotNull(treasury.pendingBatch());

        treasury.addFunds(EMERALD, 1, MARKET_TAX, null, "Market sales tax", START + HOUR + 1);
        assertTrue(treasury.settlePending(START + HOUR + 1));

        assertEquals(15, treasury.claims(ALICE, EMERALD));
        assertEquals(15, treasury.claims(BOB, EMERALD));
        assertEquals(0, treasury.balance(EMERALD));
        assertTrue(treasury.taxDueSnapshot().isEmpty());
        assertEquals(500, treasury.policy().marketTaxBasisPoints());
        assertEquals(0, treasury.policy().contractTaxBasisPoints());
        assertEquals(5, treasury.policy().effectiveFixedTax());
        assertEquals(MARKET_TAX, treasury.history().get(1).kind());
    }

    private static Treasury initialized(Policy policy) {
        var treasury = new Treasury();
        treasury.setPolicy(policy, MANAGER, START);
        assertTrue(treasury.cycle(START));
        return treasury;
    }

    private static Policy policy(Treasury.Currency currency, Policy.TaxMode mode, long salary, long tax) {
        return new Policy(0, mode, 500, tax, salary, 1, 10, currency, 1, 3);
    }

    private static void eligible(Treasury treasury) {
        treasury.recordActivity(ALICE, "Alice", 10, START + 1);
        treasury.recordActivity(BOB, "Bob", 10, START + 2);
    }

    private static Treasury restored(Policy policy, Map<Treasury.Currency, Long> balances,
                                     Map<UUID, Map<Treasury.Currency, Long>> claims,
                                     Map<UUID, Map<Treasury.Currency, Long>> taxDue,
                                     Treasury.PendingBatch pending) {
        return new Treasury(new Treasury.Snapshot(policy, balances, claims, taxDue, Map.of(), List.of(),
                pending, START, START + HOUR, MANAGER));
    }

    private static long claimTotal(Treasury treasury, Treasury.Currency currency) {
        return treasury.claimsSnapshot().values().stream().mapToLong(m -> m.getOrDefault(currency, 0L)).sum();
    }
}
