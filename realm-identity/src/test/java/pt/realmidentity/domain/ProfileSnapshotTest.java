package pt.realmidentity.domain;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileSnapshotTest {
    private static ProfileSnapshot profile(String name, int age, String race, String occupation, String signature, String drawing) {
        return new ProfileSnapshot(ProfileSnapshot.Source.CHRONICLES, name, age, race, occupation, signature, drawing);
    }

    @Test void namesPreserveSourceSpellingUnicodePunctuationAndSpaces() {
        String name = "  Éowyn d'Água — Guardiã  ";
        ProfileSnapshot snapshot = profile(name, 999, "Elfo", "Guardiã", "Éowyn", "");
        assertEquals(name, snapshot.name());
        assertEquals(999, snapshot.age());
        assertEquals(ProfileSnapshot.Source.CHRONICLES, snapshot.source());
        assertEquals("A", profile("A", 1, "", "", "", "").name());
        assertEquals("👑", profile("👑", 1, "", "", "", "").name());
    }

    @Test void standaloneSnapshotAllowsUnknownFieldsWithoutInventingMetadata() {
        ProfileSnapshot snapshot = new ProfileSnapshot(ProfileSnapshot.Source.MINECRAFT, "Player_1", 0, "", "", "", "");
        assertEquals(0, snapshot.age());
        assertEquals("", snapshot.race());
        assertEquals("", snapshot.occupation());
        assertEquals("", snapshot.signature());
        assertEquals("", snapshot.signatureDrawing());
    }

    @Test void allTextAndAgeBoundsAreEnforcedWithoutTruncation() {
        assertDoesNotThrow(() -> profile("N".repeat(64), 999, "R".repeat(256), "O".repeat(96), "S".repeat(128), ""));
        assertThrows(IllegalArgumentException.class, () -> profile("N".repeat(65), 1, "", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> profile("N", -1, "", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> profile("N", 1000, "", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> profile("N", 1, "R".repeat(257), "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> profile("N", 1, "", "O".repeat(97), "", ""));
        assertThrows(IllegalArgumentException.class, () -> profile("N", 1, "", "", "S".repeat(129), ""));
    }

    @Test void nameMustContainVisibleContentAndNullsAreRejected() {
        for (String invalid : new String[] {"", " ", "\u2003\u2003", "\t", "\n"}) {
            assertThrows(IllegalArgumentException.class, () -> profile(invalid, 1, "", "", "", ""));
        }
        assertThrows(IllegalArgumentException.class, () -> profile(null, 1, "", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> profile("N", 1, null, "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> profile("N", 1, "", null, "", ""));
        assertThrows(IllegalArgumentException.class, () -> profile("N", 1, "", "", null, ""));
        assertThrows(NullPointerException.class, () -> profile("N", 1, "", "", "", null));
        assertThrows(NullPointerException.class, () -> new ProfileSnapshot(null, "N", 1, "", "", "", ""));
    }

    @Test void formattingControlAndMalformedUnicodeAreRejectedInEveryTextField() {
        for (String bad : new String[] {"\u0000", "\u007f", "\n", "\t", "§", "\ud800", "\udc00"}) {
            assertThrows(IllegalArgumentException.class, () -> profile("N" + bad, 1, "", "", "", ""));
            assertThrows(IllegalArgumentException.class, () -> profile("N", 1, bad, "", "", ""));
            assertThrows(IllegalArgumentException.class, () -> profile("N", 1, "", bad, "", ""));
            assertThrows(IllegalArgumentException.class, () -> profile("N", 1, "", "", bad, ""));
        }
    }

    @Test void existingChroniclesNamesWithJoinedEmojiSurviveAnIssuedSnapshotRoundtrip() {
        String name = "Joana 👩\u200d🔬";
        ProfileSnapshot snapshot = profile(name, 29, "Humana", "Investigadora", name, "");
        IdentityLedger ledger = new IdentityLedger();
        Document issued = ledger.issue(new java.util.UUID(0, 1), snapshot, 100);
        IdentityLedger restored = new IdentityLedger(ledger.seal(), ledger.nextSerial(), ledger.documents());

        ProfileSnapshot restoredProfile = restored.lookup(issued.id()).orElseThrow().profile();
        assertEquals(name, restoredProfile.name());
        assertEquals(name, restoredProfile.signature());
        assertEquals(snapshot, restoredProfile);
    }

    @Test void signatureDrawingMustBeCanonicalAndRemainsPartOfSnapshotEquality() {
        byte[] bits = new byte[InkDrawing.BYTES];
        bits[0] = 1;
        String drawing = "ink1:" + Base64.getEncoder().encodeToString(bits);
        ProfileSnapshot original = profile("N", 1, "Elf", "Smith", "N", drawing);
        assertEquals(drawing, original.signatureDrawing());
        assertEquals(original, profile("N", 1, "Elf", "Smith", "N", drawing));
        assertNotEquals(original, profile("N", 1, "Elf", "Smith", "N", ""));
        assertNotEquals(original, profile("N", 1, "Elf", "Guard", "N", drawing));
        assertThrows(IllegalArgumentException.class, () -> profile("N", 1, "", "", "", "ink1:AA=="));
        assertThrows(IllegalArgumentException.class, () -> profile("N", 1, "", "", "", drawing + "\n"));
    }
}
