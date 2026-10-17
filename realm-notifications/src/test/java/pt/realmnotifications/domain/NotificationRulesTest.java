package pt.realmnotifications.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationRulesTest {
    @Test void supportedKindsAreStableAndUnknownValuesUseInfo() {
        for (String value : NotificationRules.KINDS) assertEquals(value, NotificationRules.kind(value));
        assertEquals("info", NotificationRules.kind(null));
        assertEquals("info", NotificationRules.kind("Money"));
        assertEquals("info", NotificationRules.kind("arbitrary:extension"));
    }
    @Test void controlAndFormattingCharactersCannotChangeThePresentation() {
        assertEquals("Olá mundo\n50 €", NotificationRules.text("Olá\tmundo\u0000\n50 €\u202e\u00a7", 30));
    }
    @Test void unicodeCodepointsUseOneCharacterOfBudget() {
        assertEquals("€🎉é", NotificationRules.text("€🎉é", 3));
        assertThrows(IllegalArgumentException.class, () -> NotificationRules.text("€🎉éx", 3));
    }
    @Test void unpairedSurrogatesAreDiscarded() {
        assertEquals("OK🎉", NotificationRules.text("O\ud800K🎉\udc00", 3));
    }
    @Test void exactTextLimitIsAcceptedAndOverflowRejected() {
        assertEquals("a".repeat(512), NotificationRules.text("a".repeat(512), 512));
        assertThrows(IllegalArgumentException.class, () -> NotificationRules.text("a".repeat(513), 512));
        assertThrows(IllegalArgumentException.class, () -> NotificationRules.text(null, 512));
        assertThrows(IllegalArgumentException.class, () -> NotificationRules.text("hello", -1));
    }
    @Test void translationKeysHaveARestrictedAlphabetAndLength() {
        assertTrue(NotificationRules.translationKey("notification.realmnotifications.kind.money"));
        assertTrue(NotificationRules.translationKey("another-mod:display/key_2"));
        assertFalse(NotificationRules.translationKey("x".repeat(181)));
        assertFalse(NotificationRules.translationKey("click here"));
        assertFalse(NotificationRules.translationKey(""));
        assertFalse(NotificationRules.translationKey(null));
    }
    @Test void jsonEnvelopeRejectsRecursiveAttacksBeforeParsing() {
        assertThrows(IllegalArgumentException.class, () -> NotificationRules.checkJsonEnvelope("[".repeat(1000) + "0" + "]".repeat(1000)));
        assertThrows(IllegalArgumentException.class, () -> NotificationRules.checkJsonEnvelope("[" + "{},".repeat(256) + "{}]"));
        assertThrows(IllegalArgumentException.class, () -> NotificationRules.checkJsonEnvelope("{\"text\":\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> NotificationRules.checkJsonEnvelope("a".repeat(8193)));
    }
    @Test void bracesInsideEscapedStringsDoNotConsumeTheJsonNestingBudget() {
        assertDoesNotThrow(() -> NotificationRules.checkJsonEnvelope("{\"text\":\"" + "[".repeat(1000) + "\\\"}\\\\\"}"));
    }
}
