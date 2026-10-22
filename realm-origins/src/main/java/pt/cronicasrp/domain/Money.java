package pt.cronicasrp.domain;

import java.math.BigDecimal;
import java.util.Locale;

/** Every stored amount is an integer number of cents, never a floating-point value. */
public final class Money {
    public static final long MAX_CENTS = 900_000_000_000_000_000L;
    private Money() {}
    public static long fromEuros(long euros) {
        try { return checked(Math.multiplyExact(euros, 100)); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Esse valor ultrapassa o limite de saldo."); }
    }
    public static long checked(long cents) {
        if (cents < 0 || cents > MAX_CENTS) throw new IllegalArgumentException("Esse valor ultrapassa o limite de saldo.");
        return cents;
    }
    public static long parse(String value) {
        if (value == null || !value.strip().matches("[0-9]+([.,][0-9]{1,2})?"))
            throw new IllegalArgumentException("Escreve um valor em euros, por exemplo 12,50 (até duas casas decimais).");
        try { return checked(new BigDecimal(value.strip().replace(',', '.')).movePointRight(2).longValueExact()); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Esse valor ultrapassa o limite de saldo."); }
    }
    public static long positive(String value) {
        long amount = parse(value);
        if (amount == 0) throw new IllegalArgumentException("O valor deve ser pelo menos 0,01 €.");
        return amount;
    }
    public static String format(long cents) {
        if (cents < 0) throw new IllegalArgumentException("O valor não pode ser negativo.");
        return String.format(Locale.ROOT, "%,d", cents / 100).replace(',', '.')
                + "," + String.format(Locale.ROOT, "%02d", cents % 100) + " €";
    }
    public static String formatEnglish(long cents) {
        if (cents < 0) throw new IllegalArgumentException("O valor não pode ser negativo.");
        return "€" + String.format(Locale.ROOT, "%,d", cents / 100)
                + "." + String.format(Locale.ROOT, "%02d", cents % 100);
    }
}
