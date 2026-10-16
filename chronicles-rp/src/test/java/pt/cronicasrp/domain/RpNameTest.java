package pt.cronicasrp.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RpNameTest {
    private static CharacterProfile profile(boolean created, String name, String story, long money) {
        return new CharacterProfile(created, name, 25, "Elfo", story, "private-signature", "private-origin", money, money);
    }
    @Test void onlyCreatedCharacterNameIsPublic() {
        assertEquals("Aetherion da Lua", RpName.publicName(profile(true, "Aetherion da Lua", "secret", 500)));
        assertEquals("", RpName.publicName(profile(false, "Hidden draft", "secret", 500)));
        assertEquals("", RpName.publicName(CharacterProfile.empty()));
        assertEquals("", RpName.publicName(null));
    }
    @Test void changingPrivateDataDoesNotChangePublicIdentity() {
        assertEquals(RpName.publicName(profile(true, "Íris 山", "private biography", 1)),
                RpName.publicName(profile(true, "Íris 山", "different private biography", 50000)));
    }
    @Test void unicodeSpacesAndSupplementaryCharactersSurviveWithoutFormattingInterpretation() {
        assertEquals("Íris da Lua 山🌙", RpName.publicName(profile(true, "Íris da Lua 山🌙", "", 0)));
        assertEquals("A".repeat(40), RpName.checkedUpdate("A".repeat(40)));
        assertTrue(RpName.isValid("rainbow‍moon"));
    }
    @Test void invalidLegacyDataFallsBackWithoutCrashingOrInjectingChatLines() {
        for (String name : new String[]{"", " ", "A", "A".repeat(41), "N\nOther", "N\rOther", "N\tOther", "§cRed", "A\u2028B", "A\u2029B", "Bad\uD800", "Bad\uDC00"})
            assertEquals("", RpName.publicName(profile(true, name, "", 0)), "name=" + name);
        assertEquals("Ari", RpName.publicName(profile(true, " Ari ", "", 0)));
    }
    @Test void packetValidationAllowsExplicitRemovalButRejectsMalformedNames() {
        assertEquals("", RpName.checkedUpdate(""));
        for (String name : new String[]{"A", "   ", " Ari", "Ari ", "A".repeat(41), "§aAri", "A\nB", "A\uD800"})
            assertThrows(IllegalArgumentException.class, () -> RpName.checkedUpdate(name));
        assertThrows(IllegalArgumentException.class, () -> RpName.checkedUpdate(null));
    }
}
