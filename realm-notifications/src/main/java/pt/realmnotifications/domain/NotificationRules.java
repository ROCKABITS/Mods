package pt.realmnotifications.domain;

import java.util.Set;

/** Bounds shared by the trusted server API and its optional client protocol. */
public final class NotificationRules {
    public static final int TITLE_LIMIT = 96;
    public static final int BODY_LIMIT = 512;
    public static final int JSON_LIMIT = 8192;
    public static final int MAX_DEPTH = 8;
    public static final int MAX_NODES = 64;
    public static final Set<String> KINDS = Set.of("money", "reward", "contract", "election", "market", "salary", "treasury", "info", "warning");
    private NotificationRules() {}

    public static String kind(String value) { return value != null && KINDS.contains(value) ? value : "info"; }

    /** Remove control/format codes without splitting Unicode supplementary characters. */
    public static String text(String value, int limit) {
        if (value == null || limit < 0) throw new IllegalArgumentException("Invalid notification text");
        StringBuilder clean = new StringBuilder();
        int count = 0;
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            offset += Character.charCount(point);
            if (point == '\t') point = ' ';
            if (point == 0x00a7 || Character.getType(point) == Character.FORMAT || (Character.isISOControl(point) && point != '\n')) continue;
            if (Character.isSurrogate((char) point) && point <= Character.MAX_VALUE) continue;
            if (++count > limit) throw new IllegalArgumentException("Notification text exceeds its limit");
            clean.appendCodePoint(point);
        }
        return clean.toString();
    }

    public static boolean translationKey(String value) {
        return value != null && value.length() <= 180 && value.matches("[A-Za-z0-9_.:/-]+");
    }

    /** Guard recursive JSON/component parsers before they see an untrusted server payload. */
    public static void checkJsonEnvelope(String json) {
        if (json == null || json.length() > JSON_LIMIT) throw new IllegalArgumentException("Notification JSON is too long");
        boolean quoted = false, escaped = false;
        int depth = 0, containers = 0;
        for (int offset = 0; offset < json.length(); offset++) {
            char value = json.charAt(offset);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
            } else if (value == '"') quoted = true;
            else if (value == '{' || value == '[') {
                if (++depth > 24 || ++containers > 256) throw new IllegalArgumentException("Notification JSON is too complex");
            } else if (value == '}' || value == ']') {
                if (--depth < 0) throw new IllegalArgumentException("Unbalanced notification JSON");
            }
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("Unbalanced notification JSON");
    }
}
