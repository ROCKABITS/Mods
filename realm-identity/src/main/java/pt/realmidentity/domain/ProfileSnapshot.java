package pt.realmidentity.domain;

import java.util.Objects;

/** Read-only identity fields captured by the server at the time of issue. */
public record ProfileSnapshot(Source source, String name, int age, String race,
                              String occupation, String signature, String signatureDrawing) {
    public enum Source { MINECRAFT, CHRONICLES }

    public static final int NAME_LIMIT = 64;
    public static final int RACE_LIMIT = 256;
    public static final int OCCUPATION_LIMIT = 96;
    public static final int SIGNATURE_LIMIT = 128;

    public ProfileSnapshot {
        Objects.requireNonNull(source, "source");
        validateText(name, "name", NAME_LIMIT);
        if (name.isBlank()) throw new IllegalArgumentException("A name is required.");
        if (age < 0 || age > 999) throw new IllegalArgumentException("Invalid age.");
        validateText(race, "race", RACE_LIMIT);
        validateText(occupation, "occupation", OCCUPATION_LIMIT);
        validateText(signature, "signature", SIGNATURE_LIMIT);
        InkDrawing.parse(Objects.requireNonNull(signatureDrawing, "signatureDrawing"));
    }

    private static void validateText(String text, String field, int limit) {
        if (text == null || text.length() > limit) throw new IllegalArgumentException("Invalid " + field + ".");
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if (codePoint == '\u00a7' || Character.isISOControl(codePoint)
                    || Character.getType(codePoint) == Character.SURROGATE) {
                throw new IllegalArgumentException("Invalid characters in " + field + ".");
            }
            i += Character.charCount(codePoint);
        }
    }
}
