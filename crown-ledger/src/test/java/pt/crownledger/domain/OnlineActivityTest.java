package pt.crownledger.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pt.crownledger.domain.Treasury.Currency.EMERALD;
import static pt.crownledger.domain.Treasury.Kind.DEPOSIT;

class OnlineActivityTest {
    private static final UUID ALICE = new UUID(0, 101);
    private static final UUID BOB = new UUID(0, 102);
    private static final long START = 1_700_000_000_000L;
    private static final long MINUTE_MILLIS = 60_000L;
    private static final long MINUTE = OnlineActivity.MINUTE_NANOS;
    private static final long SECOND = MINUTE / 60;
    private static final long HOUR = 60 * MINUTE_MILLIS;

    @Test
    void reconnectingOneSecondBeforeTenGlobalSamplesNeverEarnsACompleteMinute() {
        var clock = new OnlineActivity();
        int total = 0;
        for (int sample = 1; sample <= 10; sample++) {
            long now = sample * MINUTE;
            clock.login(ALICE, now - SECOND);
            total += clock.elapsedMinutes(ALICE, now);
            clock.logout(ALICE);
        }
        assertEquals(0, total);
    }

    @Test
    void repeatedSamplesRetainTheUncompletedPartOfAnOnlineMinute() {
        var clock = new OnlineActivity();
        clock.login(ALICE, 0);

        assertEquals(0, clock.elapsedMinutes(ALICE, 20 * SECOND));
        assertEquals(0, clock.elapsedMinutes(ALICE, 50 * SECOND));
        assertEquals(1, clock.elapsedMinutes(ALICE, 70 * SECOND));
        assertEquals(0, clock.elapsedMinutes(ALICE, 119 * SECOND));
        assertEquals(1, clock.elapsedMinutes(ALICE, 120 * SECOND));
        assertEquals(1, clock.elapsedMinutes(ALICE, 185 * SECOND));
        assertEquals(0, clock.elapsedMinutes(ALICE, 239 * SECOND));
        assertEquals(1, clock.elapsedMinutes(ALICE, 240 * SECOND));
        assertEquals(0, clock.elapsedMinutes(ALICE, 240 * SECOND));
    }

    @Test
    void logoutAndReconnectDiscardPartialMinutesAndExcludeOfflineTime() {
        var clock = new OnlineActivity();
        clock.login(ALICE, 0);
        assertEquals(0, clock.elapsedMinutes(ALICE, 59 * SECOND));
        clock.logout(ALICE);
        long reconnect = 600 * MINUTE;
        clock.login(ALICE, reconnect);

        assertEquals(0, clock.elapsedMinutes(ALICE, reconnect + 59 * SECOND));
        assertEquals(1, clock.elapsedMinutes(ALICE, reconnect + MINUTE));
    }

    @Test
    void observingAnOnlinePlayerDoesNotResetTheirAccumulatedTime() {
        var clock = new OnlineActivity();
        clock.login(ALICE, 0);
        clock.observe(ALICE, 30 * SECOND);
        assertEquals(1, clock.elapsedMinutes(ALICE, MINUTE));
        clock.observe(ALICE, 90 * SECOND);
        assertEquals(1, clock.elapsedMinutes(ALICE, 2 * MINUTE));
    }

    @Test
    void firstObservationAndFirstSampleStartClocksWithoutPhantomCredit() {
        var clock = new OnlineActivity();
        clock.observe(ALICE, 100 * MINUTE);
        assertEquals(0, clock.elapsedMinutes(ALICE, 100 * MINUTE));
        assertEquals(1, clock.elapsedMinutes(ALICE, 101 * MINUTE));

        assertEquals(0, clock.elapsedMinutes(BOB, 200 * MINUTE));
        assertEquals(1, clock.elapsedMinutes(BOB, 201 * MINUTE));
    }

    @Test
    void backwardElapsedTimeResetsWithoutGrantingOrBankingAnyMinutes() {
        var clock = new OnlineActivity();
        clock.login(ALICE, 2 * MINUTE);
        assertEquals(0, clock.elapsedMinutes(ALICE, MINUTE));
        assertEquals(0, clock.elapsedMinutes(ALICE, MINUTE + 59 * SECOND));
        assertEquals(1, clock.elapsedMinutes(ALICE, 2 * MINUTE));
    }

    @Test
    void sixtyExactOnlineMinutesQualifyEveryPlayerBeforeTheCallerClosesThePeriod() {
        var treasury = initialized(60, 20);
        treasury.addFunds(EMERALD, 40, DEPOSIT, ALICE, "Complete payroll funding", START);
        var clock = new OnlineActivity();
        clock.login(ALICE, 0);
        clock.login(BOB, 0);

        assertEquals(60, clock.recordCompletedMinutes(treasury, ALICE, "Alice", START + HOUR, 60 * MINUTE));
        assertEquals(START, treasury.periodStartedAt());
        assertEquals(START + HOUR, treasury.nextCycleAt());
        assertEquals(60, treasury.activitySnapshot().get(ALICE).minutes());
        assertEquals(0, treasury.claims(ALICE, EMERALD));
        assertEquals(60, clock.recordCompletedMinutes(treasury, BOB, "Bob", START + HOUR, 60 * MINUTE));

        assertTrue(treasury.cycle(START + HOUR));
        assertEquals(20, treasury.claims(ALICE, EMERALD));
        assertEquals(20, treasury.claims(BOB, EMERALD));
        assertEquals(0, treasury.balance(EMERALD));
        assertTrue(treasury.activitySnapshot().isEmpty());
        assertNull(treasury.pendingBatch());
    }

    @Test
    void delayedBoundarySamplesCountOnlyOldPeriodEndpointsAndDiscardTheLaterGap() {
        var treasury = initialized(60, 10);
        treasury.addFunds(EMERALD, 10, DEPOSIT, ALICE, "Payroll funding", START);
        var clock = new OnlineActivity();
        clock.login(ALICE, 0);
        long sampledAt = START + HOUR + 2 * MINUTE_MILLIS + 30_000;
        long sampledNanos = 62 * MINUTE + 30 * SECOND;

        assertEquals(60, clock.recordCompletedMinutes(treasury, ALICE, "Alice", sampledAt, sampledNanos));
        assertEquals(60, treasury.activitySnapshot().get(ALICE).minutes());
        assertEquals(START, treasury.periodStartedAt());
        assertEquals(0, treasury.claims(ALICE, EMERALD));
        assertTrue(treasury.cycle(sampledAt));
        assertEquals(10, treasury.claims(ALICE, EMERALD));

        assertEquals(0, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                sampledAt + 30_000, sampledNanos + 30 * SECOND));
        assertTrue(treasury.activitySnapshot().isEmpty());
        assertEquals(1, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                sampledAt + MINUTE_MILLIS, sampledNanos + MINUTE));
        assertEquals(1, treasury.activitySnapshot().get(ALICE).minutes());
    }

    @Test
    void aMinuteCompletedAfterTheDeadlineDoesNotCountItsEarlierPartialTime() {
        var treasury = initialized(1, 10);
        treasury.addFunds(EMERALD, 10, DEPOSIT, ALICE, "Payroll funding", START);
        var clock = new OnlineActivity();
        clock.login(ALICE, 59 * MINUTE + 30 * SECOND);

        assertEquals(0, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                START + HOUR + MINUTE_MILLIS, 61 * MINUTE));
        assertTrue(treasury.activitySnapshot().isEmpty());
        assertEquals(START, treasury.periodStartedAt());
        treasury.cycle(START + HOUR + MINUTE_MILLIS);
        assertEquals(0, treasury.claims(ALICE, EMERALD));
        assertEquals(10, treasury.balance(EMERALD));
        assertEquals(1, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                START + HOUR + 2 * MINUTE_MILLIS, 62 * MINUTE));
        assertEquals(1, treasury.activitySnapshot().get(ALICE).minutes());
    }

    @Test
    void anUninitializedTreasuryStartsNoPeriodAndBanksNoActivity() {
        var treasury = new Treasury();
        var before = treasury.snapshot();
        var clock = new OnlineActivity();
        clock.login(ALICE, 0);

        assertEquals(0, clock.recordCompletedMinutes(treasury, ALICE, "Alice", START, 5 * MINUTE));
        assertEquals(before, treasury.snapshot());
        treasury.cycle(START);
        assertEquals(0, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                START + 59_000, 5 * MINUTE + 59 * SECOND));
        assertEquals(1, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                START + MINUTE_MILLIS, 6 * MINUTE));
        assertEquals(1, treasury.activitySnapshot().get(ALICE).minutes());
    }

    @Test
    void frozenPayrollCollectsNoActivityAndDoesNotBankTimeForItsNextPeriod() {
        var treasury = initialized(1, 10);
        treasury.recordActivity(ALICE, "Alice", 1, START + MINUTE_MILLIS);
        treasury.cycle(START + HOUR);
        assertNotNull(treasury.pendingBatch());
        var frozen = treasury.snapshot();
        var clock = new OnlineActivity();
        clock.login(ALICE, 0);
        long resumedAt = START + HOUR + 5 * MINUTE_MILLIS;

        assertEquals(0, clock.recordCompletedMinutes(treasury, ALICE, "Alice", resumedAt, 65 * MINUTE));
        assertEquals(frozen, treasury.snapshot());
        treasury.addFunds(EMERALD, 10, DEPOSIT, ALICE, "Complete frozen payroll", resumedAt);
        assertTrue(treasury.settlePending(resumedAt));
        assertEquals(10, treasury.claims(ALICE, EMERALD));
        assertEquals(0, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                resumedAt + 59_000, 65 * MINUTE + 59 * SECOND));
        assertTrue(treasury.activitySnapshot().isEmpty());
        assertEquals(1, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                resumedAt + MINUTE_MILLIS, 66 * MINUTE));
        assertEquals(1, treasury.activitySnapshot().get(ALICE).minutes());
    }

    @Test
    void fundingBetweenSamplesCountsOnlineTimeOnlyFromTheNewPeriodStart() {
        var treasury = initialized(1, 10);
        treasury.recordActivity(ALICE, "Alice", 1, START + MINUTE_MILLIS);
        treasury.cycle(START + HOUR);
        assertNotNull(treasury.pendingBatch());
        var clock = new OnlineActivity();
        clock.login(ALICE, 0);
        assertEquals(0, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                START + HOUR + 2 * MINUTE_MILLIS, 62 * MINUTE));

        long fundedAt = START + HOUR + 2 * MINUTE_MILLIS + 30_000;
        treasury.addFunds(EMERALD, 10, DEPOSIT, ALICE, "Funding between activity samples", fundedAt);
        assertTrue(treasury.settlePending(fundedAt));
        assertEquals(fundedAt, treasury.periodStartedAt());
        assertEquals(0, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                START + HOUR + 3 * MINUTE_MILLIS, 63 * MINUTE));
        assertTrue(treasury.activitySnapshot().isEmpty());
        assertEquals(1, clock.recordCompletedMinutes(treasury, ALICE, "Alice",
                START + HOUR + 3 * MINUTE_MILLIS + 30_000, 63 * MINUTE + 30 * SECOND));
        assertEquals(1, treasury.activitySnapshot().get(ALICE).minutes());
    }

    private static Treasury initialized(int activeMinutes, long salary) {
        var treasury = new Treasury();
        treasury.setPolicy(new Policy(0, Policy.TaxMode.PERCENTAGE, 0, 0, salary,
                1, activeMinutes, EMERALD, 1, 3), ALICE, START);
        treasury.cycle(START);
        return treasury;
    }
}
