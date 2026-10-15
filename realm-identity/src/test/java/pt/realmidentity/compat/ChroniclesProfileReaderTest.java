package pt.realmidentity.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.realmidentity.domain.InkDrawing;
import pt.realmidentity.domain.ProfileSnapshot;

/** Minimal API-shape fixtures, separate from the optional original-JAR compatibility check. */
class ChroniclesProfileReaderTest {
    @TempDir Path temporary;

    @Test void readsOnlyIdentityAccessorsAndPreservesTheOriginalRecord() throws Exception {
        byte[] ink = new byte[InkDrawing.BYTES];
        ink[0] = 1;
        ink[37] = (byte) 0xa4;
        String drawing = "ink1:" + Base64.getEncoder().encodeToString(ink);
        Object profile = fixture("int", "RpProgress", true, "Iria", 38, "Elfo", "Bardo", "I. Vale", drawing);
        String before = profile.toString();
        ProfileSnapshot snapshot = ChroniclesProfileReader.read(profile);
        assertEquals(new ProfileSnapshot(ProfileSnapshot.Source.CHRONICLES, "Iria", 38,
                "Elfo", "Bardo", "I. Vale", drawing), snapshot);
        assertEquals(before, profile.toString());
        assertTrue(InkDrawing.decode(snapshot.signatureDrawing()).isSet(0, 0));
    }

    @Test void missingCharacterFailsClosedBeforeReadingIdentityFields() throws Exception {
        Object profile = fixture("int", "RpProgress", false, "", 0, "", "", "", "");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ChroniclesProfileReader.read(profile));
        assertEquals("gui.realmidentity.profile_required", failure.getMessage());
    }

    @Test void incompatibleAgeAccessorFailsClosed() throws Exception {
        Object profile = fixture("long", "RpProgress", true, "Iria", 38L, "Elfo", "Bardo", "", "");
        assertUnavailable(profile);
    }

    @Test void incompatibleProgressAccessorFailsClosed() throws Exception {
        Object profile = fixture("int", "String", true, "Iria", 38, "Elfo", "Bardo", "", "");
        assertUnavailable(profile);
    }

    @Test void malformedProfileAndSignatureFailClosed() throws Exception {
        Object profile = fixture("int", "RpProgress", true, "Iria", 38, "Elfo", "Bardo", "", "ink1:bad");
        assertUnavailable(profile);
        assertUnavailable(null);
        assertUnavailable(new Object());
    }

    private static void assertUnavailable(Object profile) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ChroniclesProfileReader.read(profile));
        assertEquals("gui.realmidentity.profile_unavailable", failure.getMessage());
    }

    private Object fixture(String ageType, String progressType, boolean created, String name, Object age,
                           String race, String occupation, String signature, String drawing) throws Exception {
        Path root = Files.createTempDirectory(temporary, "api-shape-");
        Path directory = Files.createDirectories(root.resolve("pt/cronicasrp/domain"));
        Path progress = directory.resolve("RpProgress.java");
        Files.writeString(progress, "package pt.cronicasrp.domain; public record RpProgress(String occupation) {}", StandardCharsets.UTF_8);
        Path profile = directory.resolve("CharacterProfile.java");
        Files.writeString(profile, "package pt.cronicasrp.domain; public record CharacterProfile(boolean created, "
                + "String name, " + ageType + " age, String race, String signature, String signatureDrawing, "
                + progressType + " progress) { public long balance() { throw new AssertionError(\"economy read\"); } "
                + "public String description() { throw new AssertionError(\"non-identity read\"); } }", StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The compatibility fixture requires the project's JDK 21 toolchain.");
        var errors = new ByteArrayOutputStream();
        assertEquals(0, compiler.run(null, null, errors, "--release", "21", "-d", root.toString(),
                progress.toString(), profile.toString()), errors.toString(StandardCharsets.UTF_8));
        try (var loader = new URLClassLoader(new java.net.URL[] {root.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> profileClass = loader.loadClass("pt.cronicasrp.domain.CharacterProfile");
            Class<?> progressClass = loader.loadClass("pt.cronicasrp.domain.RpProgress");
            Object progressValue = progressType.equals("String") ? occupation : progressClass.getConstructor(String.class).newInstance(occupation);
            return profileClass.getConstructors()[0].newInstance(created, name, age, race, signature, drawing, progressValue);
        }
    }
}
