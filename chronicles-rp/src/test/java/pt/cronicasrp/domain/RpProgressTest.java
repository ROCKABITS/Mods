package pt.cronicasrp.domain;

import java.util.Random;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RpProgressTest {
    private static String ink() {
        byte[] bytes = new byte[SignatureDrawing.BYTES];
        SignatureDrawing.pixel(bytes, 10, 12, true);
        return SignatureDrawing.encode(bytes);
    }
    private static CharacterProfile profile() {
        return new CharacterProfile(true, "Ari", 128, "Elfo", "Uma história.", "Ari", "Comum", 110, 93, ink(),
                RpProgress.EMPTY.withOccupation("Estudioso").withReputation(64).completeMissions(3).award(RpProgress.Medal.GOLD));
    }
    @Test void editingOccupationAndStoryPreservesIdentityMoneyAndAccomplishments() {
        var original = profile();
        var edited = CharacterRules.editStory(original, "Novo capítulo.", "Ari", original.signatureDrawing(), "Bardo");
        assertEquals(original.name(), edited.name()); assertEquals(original.age(), edited.age());
        assertEquals(original.race(), edited.race()); assertEquals(original.origin(), edited.origin());
        assertEquals(original.initialBalance(), edited.initialBalance()); assertEquals(original.balance(), edited.balance());
        assertEquals(original.signatureDrawing(), edited.signatureDrawing());
        assertEquals(original.progress().withOccupation("Bardo"), edited.progress());
        assertEquals("Novo capítulo.", edited.description());
        assertThrows(IllegalArgumentException.class, () -> CharacterRules.editStory(original, "", "Ari", ink(), "Imperador"));
    }
    @Test void olderEditOverloadsKeepOccupationAndAwards() {
        var original = profile();
        assertEquals(original.progress(), CharacterRules.editStory(original, "Um capítulo.", "Ari").progress());
        assertEquals(original.progress(), CharacterRules.editStory(original, "Outro capítulo.", "Ari", ink()).progress());
    }
    @Test void moneyAndAwardChangesPreserveOtherFieldsAndBestMedal() {
        var original = profile();
        var result = Wallet.transfer(original, profile(), 20);
        assertEquals(original.progress(), result.sender().progress());
        assertEquals(original.progress(), result.receiver().progress());
        assertEquals(73, result.sender().balance()); assertEquals(113, result.receiver().balance());
        var changed = original.withProgress(original.progress().completeMissions(2).award(RpProgress.Medal.BRONZE));
        assertEquals(5, changed.progress().missions()); assertEquals(2, changed.progress().medals());
        assertEquals(RpProgress.Medal.GOLD, changed.progress().bestMedal());
        assertEquals(original.balance(), changed.balance()); assertEquals(original.signatureDrawing(), changed.signatureDrawing());
    }
    @Test void storageRoundtripAndLegacyDrawingMigrationPreserveTheProfile() {
        var original = profile();
        var json = ProfileStorage.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, ProfileStorage.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        JsonObject legacy = json.getAsJsonObject().deepCopy(); legacy.remove("progress");
        var migrated = ProfileStorage.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(RpProgress.EMPTY, migrated.progress());
        assertEquals(original.balance(), migrated.balance()); assertEquals(original.initialBalance(), migrated.initialBalance());
        assertEquals(original.signatureDrawing(), migrated.signatureDrawing()); assertEquals(original.name(), migrated.name());
    }
    @Test void invalidOccupationIsRejectedBeforeAnyRngIsUsed() {
        Random forbidden = new Random() {
            @Override public int nextInt(int bound) { fail("Não pode sortear com uma ocupação inválida."); return 0; }
        };
        var draft = new CharacterDraft("Ari", 128, "Elfo", "", "Ari", ink(), "Imperador");
        assertThrows(IllegalArgumentException.class, () -> CharacterRules.create(CharacterProfile.empty(), draft, EconomySettings.defaults(), forbidden));
        var valid = CharacterRules.create(CharacterProfile.empty(), new CharacterDraft("Ari", 128, "Elfo", "", "Ari", ink(), "Mago"),
                EconomySettings.defaults(), new Random(42));
        assertEquals(RpProgress.EMPTY.withOccupation("Mago"), valid.progress());
    }
    @Test void boundedCountersRanksAndWorldTimeCannotWrapOrBecomeInvalid() {
        int[] boundaries = {0, 19, 20, 39, 40, 59, 60, 79, 80, 99, 100};
        String[] ranks = {"Carvão", "Carvão", "Cobre", "Cobre", "Ferro", "Ferro", "Ouro", "Ouro", "Diamante", "Diamante", "Netherite"};
        for (int i = 0; i < boundaries.length; i++) assertEquals(ranks[i], RpProgress.EMPTY.withReputation(boundaries[i]).classification());
        assertThrows(IllegalArgumentException.class, () -> RpProgress.EMPTY.withReputation(-1));
        assertThrows(IllegalArgumentException.class, () -> RpProgress.EMPTY.withReputation(101));
        assertThrows(IllegalArgumentException.class, () -> RpProgress.EMPTY.completeMissions(0));
        assertThrows(IllegalArgumentException.class, () -> RpProgress.EMPTY.completeMissions(RpProgress.MAX_COUNTER).completeMissions(1));
        var maximum = new RpProgress("Mago", 0, 0, RpProgress.MAX_COUNTER, RpProgress.Medal.GOLD);
        assertThrows(IllegalArgumentException.class, () -> maximum.award(RpProgress.Medal.BRONZE));
        assertThrows(IllegalArgumentException.class, () -> RpProgress.EMPTY.award(RpProgress.Medal.NONE));
        assertThrows(IllegalArgumentException.class, () -> new RpProgress("Mago", 0, 0, 0, RpProgress.Medal.GOLD));
        assertThrows(IllegalArgumentException.class, () -> CharacterProfile.empty().withProgress(RpProgress.EMPTY));
        assertEquals("2h 03min", new PlayerStatistics(123L * 1200, 5, 1).playTime());
        assertThrows(IllegalArgumentException.class, () -> new PlayerStatistics(0, -1, 0));
    }
}
