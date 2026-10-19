package pt.realmcasino.domain;

import java.math.BigDecimal;

/** Integer cents, using the same upper bound as the Chronicles RP wallet. */
public final class Money {
    public static final long MAX_CENTS = 900_000_000_000_000_000L;
    private Money() {}
    public static long checked(long value) {
        if (value < 0 || value > MAX_CENTS) throw new DomainException("invalid");
        return value;
    }
    public static long positive(long value) {
        if (checked(value) == 0) throw new DomainException("invalid");
        return value;
    }
    public static long add(long left, long right) {
        try { return checked(Math.addExact(checked(left), checked(right))); }
        catch (ArithmeticException exception) { throw new DomainException("invalid"); }
    }
    public static long multiply(long value, int factor) {
        if (factor < 0) throw new DomainException("invalid");
        try { return checked(Math.multiplyExact(checked(value), factor)); }
        catch (ArithmeticException exception) { throw new DomainException("invalid"); }
    }
    /** Exact rational gross return, rounded down to a whole cent without intermediate overflow. */
    public static long ratio(long value,long numerator,long denominator) {
        checked(value);if(numerator<0||denominator<=0)throw new DomainException("invalid");
        try{return checked(java.math.BigInteger.valueOf(value).multiply(java.math.BigInteger.valueOf(numerator)).divide(java.math.BigInteger.valueOf(denominator)).longValueExact());}
        catch(ArithmeticException failure){throw new DomainException("invalid");}
    }
    public static long parse(String text) {
        if (text == null || !text.strip().matches("[0-9]+([.,][0-9]{1,2})?"))
            throw new DomainException("invalid");
        try { return checked(new BigDecimal(text.strip().replace(',', '.')).movePointRight(2).longValueExact()); }
        catch (ArithmeticException exception) { throw new DomainException("invalid"); }
    }
}
