package pt.cronicasrp.domain;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterRulesTest {
    private static final CharacterDraft DRAFT = new CharacterDraft("Ari", 24, "Elfo", "Veio da floresta.", "Ari", ink());
    private static String ink() {
        byte[] ink = new byte[SignatureDrawing.BYTES];
        for (int x = 15; x < 75; x++) SignatureDrawing.pixel(ink, x, 35 + x % 9, true);
        return SignatureDrawing.encode(ink);
    }

    @Test void registeringTwiceCannotRerollOrCreditMoney() {
        CharacterProfile first = CharacterRules.create(CharacterProfile.empty(), DRAFT, EconomySettings.defaults(), new Random(42));
        Random forbiddenRandom = new Random() {
            @Override public int nextInt(int bound) { fail("Um registo repetido não pode sequer usar o RNG."); return 0; }
        };
        assertThrows(IllegalArgumentException.class, () -> CharacterRules.create(first, DRAFT, EconomySettings.defaults(), forbiddenRandom));
        assertEquals(first.initialBalance(), first.balance());
    }

    @Test void editingStoryPreservesCurrentBalanceAndOriginalIdentity() {
        CharacterProfile first = CharacterRules.create(CharacterProfile.empty(), DRAFT, EconomySettings.defaults(), new Random(10)).withBalance(17);
        CharacterProfile edited = CharacterRules.editStory(first, "Agora vive na cidade.", "Ari da Floresta");
        assertEquals(first.name(), edited.name()); assertEquals(first.age(), edited.age());
        assertEquals(first.race(), edited.race()); assertEquals(first.origin(), edited.origin());
        assertEquals(first.initialBalance(), edited.initialBalance()); assertEquals(17, edited.balance());
        assertEquals("Agora vive na cidade.", edited.description()); assertEquals("Ari da Floresta", edited.signature());
        assertEquals(first.signatureDrawing(), edited.signatureDrawing());
    }

    @Test void invalidDraftCannotDrawMoney() {
        Random forbiddenRandom = new Random() {
            @Override public int nextInt(int bound) { fail("Não pode sortear dinheiro para dados inválidos."); return 0; }
        };
        assertThrows(IllegalArgumentException.class, () -> CharacterRules.create(CharacterProfile.empty(),
                new CharacterDraft("A", 24, "Elfo", "", ""), EconomySettings.defaults(), forbiddenRandom));
        assertThrows(IllegalArgumentException.class, () -> CharacterRules.create(CharacterProfile.empty(),
                new CharacterDraft("Ari", 0, "Elfo", "", ""), EconomySettings.defaults(), forbiddenRandom));
        assertThrows(IllegalArgumentException.class, () -> CharacterRules.create(CharacterProfile.empty(),
                new CharacterDraft("Ari", 24, "Dragão", "", ""), EconomySettings.defaults(), forbiddenRandom));
        assertThrows(IllegalArgumentException.class, () -> CharacterRules.create(CharacterProfile.empty(),
                new CharacterDraft("Ari", 24, "Elfo", "x".repeat(601), ""), EconomySettings.defaults(), forbiddenRandom));
    }

    @Test void signatureDefaultsToNameAndMultilineStoryIsKept() {
        var profile = CharacterRules.create(CharacterProfile.empty(), new CharacterDraft(" Ari ", 180, "Elfo", "linha um\nlinha dois", " ", ink()),
                EconomySettings.defaults(), new Random(5));
        assertEquals("Ari", profile.name()); assertEquals("Ari", profile.signature());
        assertEquals("linha um\nlinha dois", profile.description());
    }

    @Test void weightedBoundariesAndInclusiveMoneyBounds() {
        var settings = new EconomySettings(List.of(new EconomySettings.Band("Humilde", 35, 50, 80),
                new EconomySettings.Band("Comum", 55, 100, 140), new EconomySettings.Band("Abastada", 10, 200, 260)));
        assertEquals("Humilde", settings.roll(ticket(0, false)).origin());
        assertEquals(80, settings.roll(ticket(34, true)).amount());
        assertEquals("Comum", settings.roll(ticket(35, false)).origin());
        assertEquals(140, settings.roll(ticket(89, true)).amount());
        assertEquals("Abastada", settings.roll(ticket(90, false)).origin());
        assertEquals(260, settings.roll(ticket(99, true)).amount());
    }

    @Test void zeroWeightOriginsAreNeverChosen() {
        var settings = new EconomySettings(List.of(new EconomySettings.Band("Desligada", 0, 0, 0), new EconomySettings.Band("Ativa", 1, 10, 10)));
        assertEquals(new EconomySettings.StartingMoney("Ativa", 10), settings.roll(new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> new EconomySettings(List.of(new EconomySettings.Band("Nenhuma", 0, 0, 0))));
    }

    @Test void missingOrMalformedDrawingNeverConsumesRng() {
        Random forbidden = new Random() {
            @Override public int nextInt(int bound) { fail("Assinaturas inválidas não podem iniciar o sorteio."); return 0; }
        };
        for (String drawing : List.of("", "ink1:AAAA", SignatureDrawing.encode(new byte[SignatureDrawing.BYTES]))) {
            var draft = new CharacterDraft("Ari", 24, "Elfo", "", "Ari", drawing);
            assertThrows(IllegalArgumentException.class, () -> CharacterRules.create(CharacterProfile.empty(), draft, EconomySettings.defaults(), forbidden));
        }
    }

    @Test void allSevenNewOriginsCanBeRolledAtTheirBoundaries() {
        var settings = EconomySettings.defaults();
        assertEquals(100, settings.bands().stream().mapToInt(EconomySettings.Band::weight).sum());
        assertEquals(7, settings.bands().size());
        int start = 0;
        for (var band : settings.bands()) {
            assertEquals(new EconomySettings.StartingMoney(band.name(), band.minimum()), settings.roll(ticket(start, false)));
            assertEquals(new EconomySettings.StartingMoney(band.name(), band.maximum()), settings.roll(ticket(start + band.weight() - 1, true)));
            start += band.weight();
        }
    }

    @Test void updatingDrawnSignaturePreservesWalletAndCannotEraseIt() {
        var original = CharacterRules.create(CharacterProfile.empty(), DRAFT, EconomySettings.defaults(), new Random(7)).withBalance(31);
        byte[] ink = SignatureDrawing.decode(original.signatureDrawing()); SignatureDrawing.pixel(ink, 180, 50, true);
        var changed = CharacterRules.editStory(original, "Um novo capítulo.", original.signature(), SignatureDrawing.encode(ink));
        assertEquals(31, changed.balance()); assertEquals(original.initialBalance(), changed.initialBalance());
        assertEquals(original.origin(), changed.origin()); assertNotEquals(original.signatureDrawing(), changed.signatureDrawing());
        assertThrows(IllegalArgumentException.class, () -> CharacterRules.editStory(original, "", original.signature(), ""));
    }

    private static Random ticket(int ticket, boolean maximum) {
        return new Random() {
            @Override public int nextInt(int bound) { return ticket; }
            @Override public int nextInt(int origin, int bound) { return maximum ? bound - 1 : origin; }
        };
    }
}
