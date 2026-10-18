package pt.lawsjustice.test;

import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.domain.JusticeLedger.Sentence;
import pt.lawsjustice.service.JusticeService;

/** Real native compressed world files; corrupt inputs never substitute a fresh empty justice system. */
@GameTestHolder("lawsjustice")
@PrefixGameTestTemplate(false)
public final class JusticePersistenceGameTests {
    private JusticePersistenceGameTests() {}
    private static JusticeLedger read(JusticeFixture f) { return JusticeData.read(f.directory.resolve("laws_justice.dat")); }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void actualWorldFileRoundTripPreservesPrisonsCellsOfflineSentencesAndAudit(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); f.data.replace(f.data.ledger().advance(f.prisoner.getUUID(), 73, System.currentTimeMillis()));
            f.actors.disconnect(f.prisoner); f.data.flush(); var restored = read(f);
            h.assertTrue(restored.prisons().equals(f.data.ledger().prisons()) && restored.active().equals(f.data.ledger().active())
                    && restored.history().equals(f.data.ledger().history()), "Actual compressed world persistence changed a prison, cell, offline sentence or audit decision");
            System.out.println("JUSTICE_PERSISTENCE_PASSED");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void anAncientIssueTimestampDoesNotServeTimeDuringAWorldFileReload(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); var row = new Sentence(UUID.randomUUID(), f.prisoner.getUUID(), "Old prisoner", f.prisonId, f.cellId,
                    f.admin.getUUID(), "Administrator", "An old real-world timestamp is not online time", 0, 1200, 0, 0, 0);
            f.data.replace(f.data.ledger().sentence(row).advance(f.prisoner.getUUID(), 17, System.currentTimeMillis())); var restored = read(f).active().get(f.prisoner.getUUID());
            h.assertTrue(restored.servedTicks() == 17 && restored.remainingTicks() == 1183 && restored.issuedAt() == 0,
                    "Loading an old epoch timestamp consumed offline time or reset the original sentence");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void completedAndEmergencyDecisionsRemainInThePersistedHistoricalRecord(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(20); f.data.replace(f.data.ledger().advance(f.prisoner.getUUID(), 20, System.currentTimeMillis()));
            f.convict(40); f.data.replace(f.data.ledger().release(f.prisoner.getUUID(), f.admin.getUUID(), "Administrator", "Emergency proof", System.currentTimeMillis()));
            var restored = read(f);
            h.assertTrue(restored.active().isEmpty() && restored.history().equals(f.data.ledger().history())
                    && restored.history().stream().filter(row -> row.action().equals("COMPLETED")).count() == 1
                    && restored.history().stream().filter(row -> row.action().equals("EMERGENCY_RELEASE")).count() == 1,
                    "Native world persistence lost a completed sentence or an emergency release audit");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void malformedCompressedDataIsRejectedWithoutResettingLiveCustody(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var before = f.data.ledger(); var file = f.directory.resolve("laws_justice.dat");
            try {
                byte[] valid = Files.readAllBytes(file); Files.write(file, new byte[] {1, 2, 3, 4});
                JusticeFixture.rejected(h, () -> JusticeData.read(file));
                h.assertTrue(f.data.ledger() == before && f.sentence() != null, "A corrupt file silently substituted an empty live ledger");
                Files.write(file, valid); h.assertTrue(read(f).active().equals(before.active()), "Restoring the authentic file could not restore original custody");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void unknownNativeRootFieldsAreNotSilentlyAcceptedAsACompatibleSchema(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var file = f.directory.resolve("laws_justice.dat");
            try {
                var valid = NbtIo.readCompressed(file, NbtAccounter.create(64L * 1024 * 1024)); var corrupt = valid.copy();
                corrupt.putString("invented_justice_field", "This is not part of the known schema"); NbtIo.writeCompressed(corrupt, file);
                JusticeFixture.rejected(h, () -> JusticeData.read(file));
                NbtIo.writeCompressed(valid, file); h.assertTrue(read(f).active().equals(f.data.ledger().active()), "Rejecting an unknown schema damaged its authentic sentence");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void missingAndTruncatedWorldFilesFailClosedRatherThanInventingAReset(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var file = f.directory.resolve("laws_justice.dat");
            try {
                byte[] valid = Files.readAllBytes(file); var missing = f.directory.resolve("missing.dat");
                JusticeFixture.rejected(h, () -> JusticeData.read(missing));
                Files.write(file, java.util.Arrays.copyOf(valid, Math.max(1, valid.length / 2)));
                JusticeFixture.rejected(h, () -> JusticeData.read(file));
                Files.write(file, valid); h.assertTrue(read(f).active().size() == 1, "Truncation handling lost the original custody state");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void anActualAtomicReplacementFailurePreservesPreviousCustodyAndItsDiskFile(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var original = f.data.ledger(); var file = f.directory.resolve("laws_justice.dat");
            var candidate = original.release(f.prisoner.getUUID(), f.admin.getUUID(), "Administrator", "Must not commit", System.currentTimeMillis());
            JusticeFixture.field(f.data, "file", f.directory);
            JusticeFixture.rejected(h, () -> f.data.replace(candidate));
            h.assertTrue(f.data.ledger() == original && f.sentence() != null && Files.isRegularFile(file),
                    "A failed atomic world-file replacement pretended that a release had committed");
            h.assertTrue(JusticeData.read(file).active().equals(original.active()), "The previous durable sentence was lost during failed replacement");
            JusticeFixture.field(f.data, "file", file);
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void persistedInvalidReferencesNegativeTicksAndCapacityAreRejectedBeforeRestore(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var file = f.directory.resolve("laws_justice.dat");
            try {
                var valid = NbtIo.readCompressed(file, NbtAccounter.create(64L * 1024 * 1024));
                for (String mutation : java.util.List.of("reference", "ticks", "capacity", "fractional")) {
                    var tag = valid.copy(); var json = com.google.gson.JsonParser.parseString(tag.contains("ledger",net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)?new String(tag.getByteArray("ledger"),java.nio.charset.StandardCharsets.UTF_8):tag.getString("ledger")).getAsJsonObject();
                    var sentence = json.getAsJsonObject("active").getAsJsonObject(f.prisoner.getUUID().toString());
                    switch (mutation) {
                        case "reference" -> sentence.addProperty("prison", UUID.randomUUID().toString());
                        case "ticks" -> sentence.addProperty("servedTicks", -1);
                        case "capacity" -> json.getAsJsonObject("prisons").getAsJsonObject(f.prisonId.toString()).getAsJsonArray("cells").get(0).getAsJsonObject().addProperty("capacity", 0);
                        case "fractional" -> sentence.addProperty("originalTicks", 1200.5);
                        default -> throw new AssertionError();
                    }
                    tag.putString("ledger", json.toString()); NbtIo.writeCompressed(tag, file);
                    JusticeFixture.rejected(h, () -> JusticeData.read(file));
                }
                NbtIo.writeCompressed(valid, file); h.assertTrue(read(f).active().equals(f.data.ledger().active()), "Malformed native record rejection altered the authentic sentence");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aValidHistoricalLedgerLargerThanNbtStringLimitPersistsWithoutTruncation(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); var ledger = f.data.ledger(); long now = System.currentTimeMillis();
            for (int index = 0; index < 160; index++) {
                var sentence = new Sentence(UUID.randomUUID(), f.prisoner.getUUID(), "Large historical prisoner", f.prisonId, f.cellId,
                        f.admin.getUUID(), "Large historical administrator", "A complete medieval custody decision retained across a large world history " + index,
                        now + index * 2L, 1, 0, 0, 0);
                ledger = ledger.sentence(sentence).advance(f.prisoner.getUUID(), 1, now + index * 2L + 1);
            }
            f.data.replace(ledger); var file = f.directory.resolve("laws_justice.dat");
            try {
                var tag = NbtIo.readCompressed(file, NbtAccounter.create(64L * 1024 * 1024));
                h.assertTrue(tag.contains("ledger", net.minecraft.nbt.Tag.TAG_BYTE_ARRAY) && tag.getByteArray("ledger").length > 65_535,
                        "The large actual history did not exceed the NBT string encoding boundary or use the safe UTF-8 byte-array format");
                h.assertTrue(read(f).history().equals(ledger.history()) && read(f).history().size() == 320,
                        "A valid historical ledger larger than 65535 bytes was truncated or changed by native persistence");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void nestedSchemaMissingFieldsNumericStringsDuplicateKeysAndNoncanonicalUuidsAreRejected(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var file = f.directory.resolve("laws_justice.dat");
            try {
                var valid = NbtIo.readCompressed(file, NbtAccounter.create(64L * 1024 * 1024));
                String serialized = valid.contains("ledger", net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)
                        ? new String(valid.getByteArray("ledger"), java.nio.charset.StandardCharsets.UTF_8) : valid.getString("ledger");
                for (String mutation : java.util.List.of("unknown", "missing", "numeric_string", "fraction", "boolean", "duplicate_key", "short_uuid", "uppercase_uuid")) {
                    var tag = valid.copy(); var json = com.google.gson.JsonParser.parseString(serialized).getAsJsonObject();
                    var sentence = json.getAsJsonObject("active").getAsJsonObject(f.prisoner.getUUID().toString());
                    switch (mutation) {
                        case "unknown" -> sentence.addProperty("inventedPrivilege", true);
                        case "missing" -> sentence.remove("servedTicks");
                        case "numeric_string" -> sentence.addProperty("servedTicks", "0");
                        case "fraction" -> sentence.addProperty("servedTicks", 0.5);
                        case "boolean" -> sentence.addProperty("servedTicks", false);
                        case "short_uuid" -> sentence.addProperty("authority", "1-1-1-1-1");
                        case "uppercase_uuid" -> sentence.addProperty("authority", "ABCDEFAB-ABCD-ABCD-ABCD-ABCDEFABCDEF");
                        case "duplicate_key" -> { }
                        default -> throw new AssertionError();
                    }
                    String corrupted = json.toString();
                    if (mutation.equals("duplicate_key")) corrupted = corrupted.replace("\"servedTicks\":0", "\"servedTicks\":0,\"servedTicks\":0");
                    tag.putByteArray("ledger", corrupted.getBytes(java.nio.charset.StandardCharsets.UTF_8)); NbtIo.writeCompressed(tag, file);
                    JusticeFixture.rejected(h, () -> JusticeData.read(file));
                }
                NbtIo.writeCompressed(valid, file); h.assertTrue(read(f).active().equals(f.data.ledger().active()),
                        "Rejecting malformed nested schemas damaged the authentic persisted custody record");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void anOfflineEmergencyReleaseSurvivesReloadAndIsDeliveredExactlyOnceOnLogin(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); JusticeService.login(f.prisoner); JusticeService.logout(f.prisoner); f.actors.disconnect(f.prisoner);
            f.open(f.admin); var request = f.mutation(f.admin); request.addProperty("player", f.prisoner.getUUID().toString());
            request.addProperty("reason", "Offline release must survive world restart"); f.act(f.admin, "release", request);
            h.assertTrue(f.sentence() == null && f.data.pendingReleases().containsKey(f.prisoner.getUUID()),
                    "The offline emergency release failed to create a durable release destination");
            var restored = JusticeData.readState(f.directory.resolve("laws_justice.dat")); f.loaded.put(h.getLevel().getServer(), restored);
            h.assertTrue(restored.ledger().active().isEmpty() && restored.pendingReleases().containsKey(f.prisoner.getUUID()),
                    "Native world reload discarded the offline release queue");
            f.actors.reconnect(f.prisoner); f.prisoner.clear(); JusticeService.login(f.prisoner);
            h.assertTrue(!f.prison().area().contains(JusticeFixture.point(f.prisoner.blockPosition())) && restored.pendingReleases().isEmpty(),
                    "Relogin did not deliver and durably consume the offline release");
            var destination = f.prisoner.position(); long notices = f.prisoner.notices(); int messages = f.prisoner.chat.size();
            JusticeService.login(f.prisoner);
            h.assertTrue(f.prisoner.position().equals(destination) && f.prisoner.notices() == notices && f.prisoner.chat.size() == messages
                    && JusticeData.readState(f.directory.resolve("laws_justice.dat")).pendingReleases().isEmpty()
                    && restored.ledger().history().stream().filter(row -> row.action().equals("EMERGENCY_RELEASE")).count() == 1,
                    "A consumed offline release was replayed, notified twice or restored from its durable world file");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aFailedReleaseCheckpointNeverRepeatedlyTeleportsOrNotifiesAnAlreadyFreedPlayer(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); JusticeService.login(f.prisoner);
            var released = f.data.ledger().release(f.prisoner.getUUID(), f.admin.getUUID(), "Administrator", "Storage fault after release", System.currentTimeMillis());
            f.data.replaceAndQueue(released, f.prisoner.getUUID(), new JusticeData.ReleaseTarget(f.dimension, f.prison().release()));
            var file = f.directory.resolve("laws_justice.dat"); var obstruction = f.directory.resolve("laws_justice.dat.tmp");
            try {
                Files.createDirectory(obstruction); Files.writeString(obstruction.resolve("keep"), "A nonempty directory prevents the atomic checkpoint from being written");
                f.prisoner.clear(); JusticeService.login(f.prisoner);
                h.assertTrue(!f.prison().area().contains(JusticeFixture.point(f.prisoner.blockPosition())) && f.data.pendingReleases().containsKey(f.prisoner.getUUID()),
                        "An actual release-checkpoint failure either kept the player imprisoned or lost its pending durable retry");
                long notices = f.prisoner.notices(); int messages = f.prisoner.chat.size();
                if (net.neoforged.fml.ModList.get().isLoaded("realmnotifications")) h.assertTrue(notices == 1, "The first physical release did not send one actual notification");
                else h.assertTrue(messages == 1, "The first physical release did not send one private fallback notice");
                var freePosition = f.prisoner.position().add(-3, 0, 0); f.prisoner.teleportTo(freePosition.x, freePosition.y, freePosition.z);
                JusticeService.login(f.prisoner);
                h.assertTrue(f.prisoner.position().equals(freePosition) && f.prisoner.notices() == notices && f.prisoner.chat.size() == messages
                        && f.data.pendingReleases().containsKey(f.prisoner.getUUID()),
                        "Retrying a still-failed release checkpoint repeatedly teleported or notified an already freed player");
                Files.delete(obstruction.resolve("keep")); Files.delete(obstruction); JusticeService.login(f.prisoner);
                h.assertTrue(f.prisoner.position().equals(freePosition) && f.prisoner.notices() == notices && f.prisoner.chat.size() == messages
                        && f.data.pendingReleases().isEmpty() && JusticeData.readState(file).pendingReleases().isEmpty(),
                        "A recovered release checkpoint moved the freed player again, notified twice or failed to clear its durable retry");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        h.succeed();
    }
}
