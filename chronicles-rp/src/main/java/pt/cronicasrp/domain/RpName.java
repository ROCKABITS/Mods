package pt.cronicasrp.domain;

/** The only public part of a character profile used for multiplayer name labels. */
public final class RpName {
    private RpName() {}

    /** Empty means use the normal Minecraft username. Invalid legacy names also fall back safely. */
    public static String publicName(CharacterProfile profile) {
        if (profile == null || !profile.created() || profile.name() == null) return "";
        String name = profile.name().strip();
        return isValid(name) ? name : "";
    }

    public static boolean isValid(String name) {
        if (name == null || name.length() < 2 || name.length() > CharacterRules.NAME_LIMIT
                || name.isBlank() || !name.equals(name.strip())) return false;
        for (int index = 0; index < name.length(); index++) {
            char ch = name.charAt(index);
            if (ch == '§' || Character.isISOControl(ch) || ch == '\u2028' || ch == '\u2029') return false;
            if (Character.isHighSurrogate(ch)) {
                if (++index >= name.length() || !Character.isLowSurrogate(name.charAt(index))) return false;
            } else if (Character.isLowSurrogate(ch)) return false;
        }
        return true;
    }

    public static String checkedUpdate(String name) {
        if (name == null || (!name.isEmpty() && !isValid(name)))
            throw new IllegalArgumentException("Invalid public character name.");
        return name;
    }
}
