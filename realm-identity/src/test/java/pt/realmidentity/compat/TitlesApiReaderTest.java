package pt.realmidentity.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TitlesApiReaderTest {
    @TempDir Path temporary;
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ID = "22222222-2222-2222-2222-222222222222";
    private static final String TITLE = "33333333-3333-3333-3333-333333333333";

    private static String title(long expiry) {
        return "{\"id\":\"" + ID + "\",\"titleId\":\"" + TITLE
                + "\",\"name\":\"Royal cartographer\",\"description\":\"Maps the kingdom\",\"grantedAt\":1000,\"expiresAt\":" + expiry + "}";
    }

    @Test void freshConsultationsReadOnlyPublicApiAndNeverCacheGrants() throws Exception {
        try (Fixture fixture = fixture("1", "public static String activeTitlesJson(net.minecraft.server.MinecraftServer server, java.util.UUID owner) { calls++; lastOwner = owner; return json; }")) {
            fixture.json("[]");
            assertTrue(fixture.read(1500).isEmpty());
            fixture.json("[" + title(0) + "]");
            var granted = fixture.read(1500);
            assertEquals("Royal cartographer", granted.getFirst().name());
            assertEquals("Maps the kingdom", granted.getFirst().description());
            assertEquals(0, granted.getFirst().expiresAt());
            assertEquals(OWNER, fixture.api.getField("lastOwner").get(null));
            fixture.json("[]");
            assertTrue(fixture.read(1500).isEmpty(), "A revoked title must disappear on the same document's next consultation");
            assertEquals(3, fixture.api.getField("calls").getInt(null));
        }
    }

    @Test void offlineExpiryUsesServerTimeAndExcludesTheExpiryInstant() throws Exception {
        try (Fixture fixture = fixture("1", "public static String activeTitlesJson(net.minecraft.server.MinecraftServer server, java.util.UUID owner) { return json; }")) {
            fixture.json("[" + title(2000) + "]");
            assertEquals(1, fixture.read(1999).size());
            assertTrue(fixture.read(2000).isEmpty());
            assertTrue(fixture.read(9999).isEmpty());
        }
    }

    @Test void incompatibleOrMissingLoadedApiFailsClosed() throws Exception {
        for (String version : List.of("2", "0")) {
            try (Fixture fixture = fixture(version, "public static String activeTitlesJson(net.minecraft.server.MinecraftServer server, java.util.UUID owner) { return \"[]\"; }")) {
                assertUnavailable(fixture);
            }
        }
        for (String method : List.of(
                "public String activeTitlesJson(net.minecraft.server.MinecraftServer server, java.util.UUID owner) { return \"[]\"; }",
                "public static Object activeTitlesJson(net.minecraft.server.MinecraftServer server, java.util.UUID owner) { return \"[]\"; }",
                "public static String activeTitlesJson(java.util.UUID owner) { return \"[]\"; }")) {
            try (Fixture fixture = fixture("1", method)) { assertUnavailable(fixture); }
        }
    }

    @Test void throwingOrNullLoadedApiFailsClosed() throws Exception {
        for (String body : List.of("throw new IllegalStateException(\"unavailable registry\");", "return null;")) {
            try (Fixture fixture = fixture("1", "public static String activeTitlesJson(net.minecraft.server.MinecraftServer server, java.util.UUID owner) { " + body + " }")) {
                assertUnavailable(fixture);
            }
        }
    }

    @Test void rejectsPrivateFieldsDuplicateFieldsAndMalformedContractValues() {
        String good = title(0);
        for (String bad : List.of(
                "{}", "null", "[null]", "[" + good + "] trailing",
                "[" + good.replace("\"name\":", "\"name\":\"duplicate\",\"name\":") + "]",
                "[" + good.replace("\"description\":", "\"privateBiography\":\"secret\",\"description\":") + "]",
                "[" + good.replace("\"grantedAt\":1000", "\"grantedAt\":\"1000\"") + "]",
                "[" + good.replace("\"grantedAt\":1000", "\"grantedAt\":1e3") + "]",
                "[" + good.replace("\"grantedAt\":1000", "\"grantedAt\":-1") + "]",
                "[" + good.replace("\"expiresAt\":0", "\"expiresAt\":1000") + "]",
                "[" + good.replace("Royal cartographer", "§aForged") + "]",
                "[" + good.replace("Royal cartographer", "x".repeat(49)) + "]",
                "[" + good.replace("Maps the kingdom", "x".repeat(257)) + "]",
                "[" + good.replace(ID, "1-2-3-4-5") + "]")) {
            assertThrows(IllegalArgumentException.class, () -> TitlesApiReader.parse(bad, 1500), bad);
        }
    }

    @Test void rejectsDuplicateAssignmentsAndOverCapacityRatherThanTruncating() {
        assertThrows(IllegalArgumentException.class, () -> TitlesApiReader.parse("[" + title(0) + "," + title(0) + "]", 1500));
        String seventeen = java.util.stream.IntStream.range(0, 17).mapToObj(i -> title(0)
                .replace(ID, UUID.randomUUID().toString()).replace(TITLE, UUID.randomUUID().toString()))
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        assertThrows(IllegalArgumentException.class, () -> TitlesApiReader.parse(seventeen, 1500));
        assertThrows(IllegalArgumentException.class, () -> TitlesApiReader.parse(" ".repeat(32769), 1500));
    }

    @Test void acceptsAllSixteenPublicTitlesIncludingEmptyDescriptions() {
        String sixteen = java.util.stream.IntStream.range(0, 16).mapToObj(i -> title(0)
                .replace(ID, UUID.randomUUID().toString()).replace(TITLE, UUID.randomUUID().toString())
                .replace("Maps the kingdom", "")).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        var result = TitlesApiReader.parse(sixteen, 1500);
        assertEquals(16, result.size());
        assertTrue(result.stream().allMatch(title -> title.description().isEmpty()));
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    private static void assertUnavailable(Fixture fixture) {
        var failure = assertThrows(IllegalStateException.class, () -> fixture.read(1500));
        assertEquals("gui.realmidentity.titles_unavailable", failure.getMessage());
    }

    private Fixture fixture(String version, String method) throws Exception {
        Path root = Files.createTempDirectory(temporary, "titles-api-");
        Path server = root.resolve("net/minecraft/server/MinecraftServer.java");
        Files.createDirectories(server.getParent());
        Files.writeString(server, "package net.minecraft.server; public class MinecraftServer { public Object getPlayerList() { throw new AssertionError(\"offline owner lookup\"); } }", StandardCharsets.UTF_8);
        Path api = root.resolve("pt/realmtitles/api/TitlesApi.java");
        Files.createDirectories(api.getParent());
        Files.writeString(api, "package pt.realmtitles.api; public class TitlesApi { public static final int VERSION=" + version
                + "; public static String json; public static int calls; public static java.util.UUID lastOwner; " + method
                + " public static Object ledger() { throw new AssertionError(\"private ledger read\"); }"
                + " public static String biography() { throw new AssertionError(\"private profile read\"); } }", StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var errors = new ByteArrayOutputStream();
        assertEquals(0, compiler.run(null, null, errors, "--release", "21", "-d", root.toString(), server.toString(), api.toString()), errors.toString(StandardCharsets.UTF_8));
        var loader = new URLClassLoader(new java.net.URL[] {root.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        return new Fixture(loader, loader.loadClass("net.minecraft.server.MinecraftServer").getConstructor().newInstance(), loader.loadClass("pt.realmtitles.api.TitlesApi"));
    }

    private record Fixture(URLClassLoader loader, Object server, Class<?> api) implements AutoCloseable {
        void json(String value) throws Exception { api.getField("json").set(null, value); }
        List<pt.realmidentity.domain.PublicTitle> read(long now) { return TitlesApiReader.read(server, OWNER, now, loader); }
        public void close() throws Exception { loader.close(); }
    }
}
