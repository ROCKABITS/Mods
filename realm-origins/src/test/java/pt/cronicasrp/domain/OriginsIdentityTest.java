package pt.cronicasrp.domain;

import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OriginsIdentityTest {
    private static String ink() {
        byte[] ink = new byte[SignatureDrawing.BYTES];
        SignatureDrawing.pixel(ink, 40, 20, true);
        return SignatureDrawing.encode(ink);
    }
    @Test void datapackOriginAcceptedOnlyWhenVerifiedByServer() {
        String id = "fantasy_pack:moonborn/royal_elf";
        var draft = new CharacterDraft("Ari", 128, id, "Lua", "Ari", ink(), "Ferreiro");
        var profile = CharacterRules.create(CharacterProfile.empty(), draft, EconomySettings.defaults(), new Random(7), Set.of(id));
        assertEquals(id, profile.race());
        assertEquals("Ferreiro", profile.progress().occupation());
        Random forbidden = new Random() {
            @Override public int nextInt(int bound) { fail("Unverified origins cannot trigger the wealth roll."); return 0; }
        };
        assertThrows(IllegalArgumentException.class, () -> CharacterRules.create(CharacterProfile.empty(), draft,
                EconomySettings.defaults(), forbidden, Set.of("origins:human")));
    }
    @Test void mirroringAnOriginPreservesAllEconomyAndRpData() {
        var old = new CharacterProfile(true, "Ari", 128, "Elfo", "Floresta", "Ari", "Nobre", 50000, 12345,
                ink(), RpProgress.EMPTY.withOccupation("Ferreiro").withReputation(88).completeMissions(2).award(RpProgress.Medal.GOLD));
        var changed = old.withRace("origins:shulk");
        assertEquals("origins:shulk", changed.race());
        assertEquals(old, changed.withRace("Elfo"));
        assertEquals(old.balance(), changed.balance());
        assertEquals(old.progress(), changed.progress());
    }
}
