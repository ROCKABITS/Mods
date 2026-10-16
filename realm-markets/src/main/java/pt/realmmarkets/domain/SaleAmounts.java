package pt.realmmarkets.domain;

/** Exact smallest-unit amounts for one paid purchase, rounded once on its total. */
public record SaleAmounts(long gross, long tax, long net, int taxBasisPoints) {
    public static final long MAX_AMOUNT = Stall.MAX_EARNINGS;

    public SaleAmounts {
        if (gross < 1 || gross > MAX_AMOUNT || tax < 0 || tax > gross || net < 0
                || net != gross - tax || taxBasisPoints < 0 || taxBasisPoints > 10_000) {
            throw new IllegalArgumentException("Invalid market sale amounts.");
        }
        if (tax != calculateTax(gross, taxBasisPoints)) {
            throw new IllegalArgumentException("Sale tax does not match its quoted rate.");
        }
    }

    /** Avoid gross*basisPoints overflow even at the maximum supported wallet. */
    public static SaleAmounts quote(long gross, int basisPoints) {
        if (gross < 1 || gross > MAX_AMOUNT || basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("Invalid market sale quote.");
        }
        long tax = calculateTax(gross, basisPoints);
        return new SaleAmounts(gross, tax, gross - tax, basisPoints);
    }

    private static long calculateTax(long gross, int basisPoints) {
        return (gross / 10_000) * basisPoints + (gross % 10_000) * basisPoints / 10_000;
    }
}
