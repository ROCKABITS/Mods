package pt.cronicasrp.domain;

import java.util.List;
import java.util.Arrays;

public enum CurrencyDenomination {
    CENT_1("coin_1_cent", "1c", 1, "1 cêntimo"),
    CENT_2("coin_2_cents", "2c", 2, "2 cêntimos"),
    CENT_5("coin_5_cents", "5c", 5, "5 cêntimos"),
    CENT_10("coin_10_cents", "10c", 10, "10 cêntimos"),
    CENT_20("coin_20_cents", "20c", 20, "20 cêntimos"),
    CENT_50("coin_50_cents", "50c", 50, "50 cêntimos"),
    EURO_1("coin_1_euro", "1e", 100, "1 euro"),
    EURO_2("coin_2_euros", "2e", 200, "2 euros"),
    NOTE_5("note_5_euros", "5e", 500, "5 euros"),
    NOTE_10("note_10_euros", "10e", 1_000, "10 euros"),
    NOTE_20("note_20_euros", "20e", 2_000, "20 euros"),
    NOTE_50("note_50_euros", "50e", 5_000, "50 euros"),
    NOTE_100("note_100_euros", "100e", 10_000, "100 euros"),
    NOTE_200("note_200_euros", "200e", 20_000, "200 euros"),
    NOTE_500("note_500_euros", "500e", 50_000, "500 euros");

    public static final List<CurrencyDenomination> ASCENDING = List.of(values());
    public static final List<CurrencyDenomination> DESCENDING = ASCENDING.reversed();
    private final String id, alias, label;
    private final int cents;
    CurrencyDenomination(String id, String alias, int cents, String label) {
        this.id = id; this.alias = alias; this.cents = cents; this.label = label;
    }
    public String id() { return id; }
    public String alias() { return alias; }
    public int cents() { return cents; }
    public String label() { return label; }
    public boolean isNote() { return cents >= 500; }
    public String shortLabel() { return cents < 100 ? cents + "c" : cents / 100 + "€"; }
    public static CurrencyDenomination byId(String id) {
        return Arrays.stream(values()).filter(d -> d.id.equals(id) || d.alias.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Escolhe uma moeda ou nota válida (ex.: 50c, 2e, 20e)."));
    }
}
