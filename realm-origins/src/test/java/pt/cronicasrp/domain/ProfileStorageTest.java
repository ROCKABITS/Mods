package pt.cronicasrp.domain;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileStorageTest {
    @Test void savingAndLoadingKeepsEveryFieldIncludingCurrentAndStartingMoney() {
        var original = new CharacterProfile(true, "Ari", 180, "Anão", "Veio da serra.\nProcura um novo lar.",
                "Ari da Serra", "Humilde", 70, 8_000_000_000_000_001L);
        var encoded = ProfileStorage.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var loaded = ProfileStorage.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original, loaded);
        assertTrue(loaded.created());
        assertNotEquals(loaded.initialBalance(), loaded.balance());
    }

    @Test void playerWithoutStoredDataStartsUncreatedAndWithoutMoney() {
        assertEquals(CharacterProfile.empty(), ProfileStorage.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
    }

    @Test void legacyProfileLoadsWithItsOriginalSignatureAndMoney() {
        var old = new JsonObject(); old.addProperty("created", true); old.addProperty("name", "Ari");
        old.addProperty("age", 24); old.addProperty("race", "Elfo"); old.addProperty("signature", "Ari da floresta");
        old.addProperty("origin", "Abastada"); old.addProperty("initialBalance", 211); old.addProperty("balance", 78);
        var migrated = ProfileStorage.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertTrue(migrated.created()); assertEquals("Ari da floresta", migrated.signature());
        assertEquals("", migrated.signatureDrawing()); assertEquals(7800, migrated.balance()); assertEquals(21100, migrated.initialBalance());
        var edited = CharacterRules.editStory(migrated, "Novo capítulo.", migrated.signature(), "");
        assertEquals(migrated.balance(), edited.balance()); assertEquals(migrated.origin(), edited.origin());
    }

    @Test void drawingSurvivesStorageAndWalletChanges() {
        byte[] ink = new byte[SignatureDrawing.BYTES];
        SignatureDrawing.pixel(ink, 255, 95, true); SignatureDrawing.pixel(ink, 0, 0, true);
        var original = new CharacterProfile(true, "Ari", 24, "Elfo", "", "Ari", "Nobre", 550, 550, SignatureDrawing.encode(ink));
        var changed = original.withBalance(123);
        var loaded = ProfileStorage.CODEC.parse(JsonOps.INSTANCE, ProfileStorage.CODEC.encodeStart(JsonOps.INSTANCE, changed).getOrThrow()).getOrThrow();
        assertEquals(changed, loaded); assertArrayEquals(ink, SignatureDrawing.decode(loaded.signatureDrawing()));
    }
    @Test void migrationRunsExactlyOnceAndRetainsDrawingAndProgress() {
        var old = new JsonObject(); old.addProperty("created", true); old.addProperty("name", "Ari");
        old.addProperty("age", 128); old.addProperty("race", "Elfo"); old.addProperty("signature", "Ari");
        old.addProperty("initialBalance", 110); old.addProperty("balance", 93);
        byte[] ink = new byte[SignatureDrawing.BYTES]; SignatureDrawing.pixel(ink, 10, 12, true);
        old.addProperty("signatureDrawing", SignatureDrawing.encode(ink));
        var progress = RpProgress.EMPTY.withOccupation("Estudioso").withReputation(64).completeMissions(3).award(RpProgress.Medal.GOLD);
        old.add("progress", ProfileStorage.PROGRESS_CODEC.encodeStart(JsonOps.INSTANCE, progress).getOrThrow());
        var migrated = ProfileStorage.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertEquals(9300, migrated.balance()); assertEquals(11000, migrated.initialBalance());
        assertEquals(progress, migrated.progress()); assertEquals(SignatureDrawing.encode(ink), migrated.signatureDrawing());
        var newJson = ProfileStorage.CODEC.encodeStart(JsonOps.INSTANCE, migrated).getOrThrow();
        assertEquals(1, newJson.getAsJsonObject().get("moneyVersion").getAsInt());
        for (int i = 0; i < 5; i++) {
            assertEquals(migrated, ProfileStorage.CODEC.parse(JsonOps.INSTANCE, newJson).getOrThrow());
            newJson = ProfileStorage.CODEC.encodeStart(JsonOps.INSTANCE, migrated).getOrThrow();
        }
    }
    @Test void corruptCurrencyVersionsAndOverflowAreRejected() {
        var invalid = new JsonObject(); invalid.addProperty("moneyVersion", 2);
        assertTrue(ProfileStorage.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
        invalid.addProperty("moneyVersion", 0); invalid.addProperty("balance", Long.MAX_VALUE);
        assertTrue(ProfileStorage.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
        invalid.addProperty("balance", -1);
        assertTrue(ProfileStorage.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
    }
}
