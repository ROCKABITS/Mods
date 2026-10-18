package pt.realmcasino.domain;

/** A bounded public translation key; never exposes private wallet or card data. */
public final class DomainException extends IllegalArgumentException {
    private final String key;
    public DomainException(String code) {
        super("error.realmcasino." + code);
        if (code == null || !code.matches("[a-z_]{1,48}")) throw new IllegalArgumentException("Invalid error code.");
        key = "error.realmcasino." + code;
    }
    public String key() { return key; }
}
