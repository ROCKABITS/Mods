package pt.crownledger.domain;

/** Immutable server policy. Monetary values are minor currency units, never floating point. */
public record Policy(int minimumReputation, TaxMode taxMode, int taxBasisPoints,
                     long fixedTax, long salary, int periodHours, int activeMinutes,
                     Treasury.Currency currency, int reputationPerPublicContract,
                     int dailyReputationLimit) {
    public enum TaxMode { PERCENTAGE, FIXED }
    public static final Policy DEFAULT = new Policy(0, TaxMode.PERCENTAGE, 0, 0, 0, 168, 10,
            Treasury.Currency.EMERALD, 1, 3);

    public Policy {
        if (minimumReputation < 0 || minimumReputation > 100 || taxMode == null || currency == null
                || taxBasisPoints < 0 || taxBasisPoints > 10_000 || fixedTax < 0 || salary < 0
                || periodHours < 1 || periodHours > 720 || activeMinutes < 1
                || reputationPerPublicContract < 0 || reputationPerPublicContract > 100
                || dailyReputationLimit < 0 || dailyReputationLimit > 100)
            throw new IllegalArgumentException("Policy values are outside their supported limits");
    }

    public int contractTaxBasisPoints() { return taxMode == TaxMode.PERCENTAGE ? taxBasisPoints : 0; }
    public long effectiveFixedTax() { return taxMode == TaxMode.FIXED ? fixedTax : 0; }
    public long periodMillis() { return periodHours * 3_600_000L; }
}
