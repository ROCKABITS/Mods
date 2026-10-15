package pt.crownledger.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static pt.crownledger.domain.Policy.TaxMode.*;
import static pt.crownledger.domain.Treasury.Currency.*;

class PolicyTest {
    @Test
    void defaultPolicyHasWeeklyActivityBasedEmeraldPayrollAndSmallPublicContractRewards() {
        var policy = Policy.DEFAULT;
        assertEquals(0, policy.minimumReputation());
        assertEquals(PERCENTAGE, policy.taxMode());
        assertEquals(0, policy.contractTaxBasisPoints());
        assertEquals(0, policy.effectiveFixedTax());
        assertEquals(0, policy.salary());
        assertEquals(168, policy.periodHours());
        assertEquals(10, policy.activeMinutes());
        assertEquals(EMERALD, policy.currency());
        assertEquals(1, policy.reputationPerPublicContract());
        assertEquals(3, policy.dailyReputationLimit());
    }

    @Test
    void taxModeSelectsContractPercentageOrFixedAssessmentWithoutApplyingBoth() {
        var percentage = new Policy(0, PERCENTAGE, 500, 7, 20, 1, 10, CHRONICLES, 1, 3);
        var fixed = new Policy(0, FIXED, 500, 7, 20, 1, 10, CHRONICLES, 1, 3);

        assertEquals(500, percentage.contractTaxBasisPoints());
        assertEquals(0, percentage.effectiveFixedTax());
        assertEquals(0, fixed.contractTaxBasisPoints());
        assertEquals(7, fixed.effectiveFixedTax());
        assertEquals(3_600_000L, fixed.periodMillis());
    }

    @Test
    void documentedMaximumsAndZeroMonetaryValuesRemainRepresentable() {
        var maximum = new Policy(100, FIXED, 10_000, Long.MAX_VALUE, Long.MAX_VALUE,
                720, Integer.MAX_VALUE, CHRONICLES, 100, 100);
        assertEquals(2_592_000_000L, maximum.periodMillis());
        assertEquals(Long.MAX_VALUE, maximum.salary());
        assertEquals(Long.MAX_VALUE, maximum.effectiveFixedTax());
        assertDoesNotThrow(() -> new Policy(0, PERCENTAGE, 0, 0, 0, 1, 1, EMERALD, 0, 0));
    }

    @Test
    void invalidReputationTaxMoneyPeriodsActivityAndCurrencyAreRejected() {
        for (var reputation : new int[]{-1, 101}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Policy(reputation, PERCENTAGE, 0, 0, 0, 1, 1, EMERALD, 1, 3));
            assertThrows(IllegalArgumentException.class,
                    () -> new Policy(0, PERCENTAGE, 0, 0, 0, 1, 1, EMERALD, reputation, 3));
            assertThrows(IllegalArgumentException.class,
                    () -> new Policy(0, PERCENTAGE, 0, 0, 0, 1, 1, EMERALD, 1, reputation));
        }
        for (var tax : new int[]{-1, 10001}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Policy(0, PERCENTAGE, tax, 0, 0, 1, 1, EMERALD, 1, 3));
        }
        for (var period : new int[]{-1, 0, 721}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Policy(0, PERCENTAGE, 0, 0, 0, period, 1, EMERALD, 1, 3));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new Policy(0, FIXED, 0, -1, 0, 1, 1, EMERALD, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Policy(0, FIXED, 0, 0, -1, 1, 1, EMERALD, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Policy(0, PERCENTAGE, 0, 0, 0, 1, 0, EMERALD, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Policy(0, null, 0, 0, 0, 1, 1, EMERALD, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Policy(0, PERCENTAGE, 0, 0, 0, 1, 1, null, 1, 3));
    }
}
