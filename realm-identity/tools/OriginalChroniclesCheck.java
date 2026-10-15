import java.util.Arrays;
import pt.cronicasrp.domain.CharacterProfile;
import pt.cronicasrp.domain.RpProgress;
import pt.cronicasrp.domain.SignatureDrawing;
import pt.realmidentity.compat.ChroniclesProfileReader;
import pt.realmidentity.domain.ProfileSnapshot;

public final class OriginalChroniclesCheck {
    public static void main(String[] args) {
        byte[] pixels = new byte[SignatureDrawing.BYTES];
        pixels[0] = 1;
        pixels[31] = (byte) 0x80;
        pixels[101] = 0x42;
        String ink = SignatureDrawing.encode(pixels);
        RpProgress progress = RpProgress.EMPTY.withOccupation("Bardo").withReputation(74).completeMissions(7);
        CharacterProfile original = new CharacterProfile(true, "Iria Vale", 38, "Elfo", "Private biography",
                "I. Vale", "Private origin", 41500, 29700, ink, progress);
        String before = original.toString();
        ProfileSnapshot snapshot = ChroniclesProfileReader.read(original);
        ProfileSnapshot expected = new ProfileSnapshot(ProfileSnapshot.Source.CHRONICLES,
                "Iria Vale", 38, "Elfo", "Bardo", "I. Vale", ink);
        if (!expected.equals(snapshot)) throw new AssertionError("Identity mismatch: " + snapshot);
        if (!Arrays.equals(pixels, SignatureDrawing.decode(snapshot.signatureDrawing()))) throw new AssertionError("Ink mismatch");
        if (!before.equals(original.toString()) || original.balance() != 29700 || original.initialBalance() != 41500
                || original.progress() != progress || !original.description().equals("Private biography")
                || !original.origin().equals("Private origin")) throw new AssertionError("Original profile changed");
        if (snapshot.equals(ChroniclesProfileReader.read(original.withProgress(progress.withOccupation("Mago"))))) {
            throw new AssertionError("Occupation change was not observed");
        }
        try { ChroniclesProfileReader.read(CharacterProfile.empty()); throw new AssertionError("Uncreated profile accepted"); }
        catch (IllegalStateException expectedFailure) {
            if (!expectedFailure.getMessage().equals("gui.realmidentity.profile_required")) throw expectedFailure;
        }
        CharacterProfile malformed = new CharacterProfile(true, "Iria", 38, "Elfo", "", "", "", 0, 0, "ink1:bad", progress);
        try { ChroniclesProfileReader.read(malformed); throw new AssertionError("Malformed signature accepted"); }
        catch (IllegalStateException expectedFailure) {
            if (!expectedFailure.getMessage().equals("gui.realmidentity.profile_unavailable")) throw expectedFailure;
        }
        System.out.println("PASS original Chronicles RP 0.5.2 record API: identity, occupation, ink, uncreated/malformed rejection, and unchanged biography/origin/money/progress");
    }
}
