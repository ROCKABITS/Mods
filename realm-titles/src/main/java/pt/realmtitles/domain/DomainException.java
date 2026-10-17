package pt.realmtitles.domain;

/** A safe translation key for a rejected domain operation. */
public final class DomainException extends IllegalArgumentException {
    private final String code;

    public DomainException(String code) {
        super("gui.realmtitles." + code);
        this.code = code;
    }

    public String code() { return code; }
}
