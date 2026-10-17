package pt.realmidentity.domain;

import java.util.UUID;

/** Public honours at consultation time, deliberately separate from issued profile records. */
public record PublicTitle(UUID id, UUID titleId, String name, String description, long grantedAt, long expiresAt) {
    public PublicTitle {
        if (id == null || titleId == null || !validText(name, 48, false)
                || !validText(description, 256, true) || grantedAt < 0
                || (expiresAt != 0 && expiresAt <= grantedAt)) throw new IllegalArgumentException("Invalid public title");
    }

    private static boolean validText(String value, int maximum, boolean empty) {
        return value != null && value.length() <= maximum && (empty || !value.isBlank())
                && value.equals(value.strip()) && value.codePoints().noneMatch(c -> Character.isISOControl(c)
                || c == 0x00a7 || Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE
                || Character.getType(c) == Character.LINE_SEPARATOR || Character.getType(c) == Character.PARAGRAPH_SEPARATOR);
    }

    public boolean active(long now) { return now >= grantedAt && (expiresAt == 0 || now < expiresAt); }
}
