package pt.lawsjustice.test;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.service.JusticeService;

/** Native custody adapters exercise actual positions, real server online ticks, events and durable state. */
@GameTestHolder("lawsjustice")
@PrefixGameTestTemplate(false)
public final class JusticeCustodyGameTests {
    private JusticeCustodyGameTests() {}
    private static void inside(GameTestHelper h, JusticeFixture f, JusticeActors.Actor actor) {
        h.assertTrue(actor.serverLevel() == h.getLevel() && f.prison().area().contains(JusticeFixture.point(actor.blockPosition())),
                "A convicted player remains outside the registered prison or in a different dimension");
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aValidPhysicalSentenceTransfersThePlayerToTheirConfiguredCell(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); f.open(f.admin); f.act(f.admin, "sentence", f.sentenceMutation(f.prisoner, 1));
            h.assertTrue(f.sentence() != null && f.sentence().originalTicks() == 1200 && f.sentence().servedTicks() == 0,
                    "A valid minute sentence was not stored as exactly 1200 online ticks");
            inside(h, f, f.prisoner);
            h.assertTrue(f.prisoner.blockPosition().equals(new net.minecraft.core.BlockPos(f.cell().spawn().x(), f.cell().spawn().y(), f.cell().spawn().z())),
                    "The real sentenced player did not arrive at the configured cell spawn");
            h.assertTrue(f.data.ledger().history().size() == 1 && f.data.ledger().history().getFirst().action().equals("SENTENCED"),
                    "The actual sentence was not appended to the authoritative audit exactly once");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void loginRestoresCustodyWithoutCountingTimeSpentOffline(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var before = f.sentence();
            f.prisoner.setPos(f.pos.getX() + 100, f.pos.getY() + 30, f.pos.getZ()); JusticeService.login(f.prisoner);
            inside(h, f, f.prisoner);
            h.assertTrue(f.sentence().servedTicks() == before.servedTicks() && f.sentence().remainingTicks() == before.remainingTicks(),
                    "Login converted a calendar timestamp into served prison time");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aTeleportOutsideThePrisonIsCorrectedAndDoesNotEraseTheSentence(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var id = f.sentence().id();
            f.prisoner.teleportTo(f.pos.getX() + 50.5, f.pos.getY() + 5, f.pos.getZ() + 50.5); JusticeService.login(f.prisoner);
            inside(h, f, f.prisoner); h.assertTrue(f.sentence().id().equals(id), "An actual teleport erased or replaced the original custody decision");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void changingDimensionCannotEscapeAnExistingSentence(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var nether = f.admin.server.getLevel(Level.NETHER); h.assertTrue(nether != null, "Actual native Nether is missing");
            f.prisoner.setServerLevel(nether); f.prisoner.setPos(0, 80, 0); JusticeService.login(f.prisoner);
            inside(h, f, f.prisoner); h.assertTrue(f.sentence() != null, "Cross-dimensional containment dropped the sentence");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void theActualRespawnEventRestoresASameUuidPlayerToCustody(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var original = f.sentence(); f.prisoner.setHealth(0);
            var replacement = f.actors.respawn(f.prisoner); replacement.setPos(f.pos.getX() + 50, f.pos.getY() + 20, f.pos.getZ());
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(replacement, false));
            inside(h, f, replacement);
            h.assertTrue(f.data.ledger().active().get(replacement.getUUID()).id().equals(original.id()),
                    "An actual same-UUID respawn event discarded or restarted the sentence");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void administrativeEmergencyReleaseIsPhysicalAuditedAndCannotBeReplayed(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); JusticeService.login(f.prisoner); f.open(f.admin);
            var json = f.mutation(f.admin); json.addProperty("player", f.prisoner.getUUID().toString()); json.addProperty("reason", "Native emergency release");
            f.act(f.admin, "release", json); h.assertTrue(f.sentence() == null, "A valid emergency release did not end custody");
            h.assertTrue(!f.prison().area().contains(JusticeFixture.point(f.prisoner.blockPosition())), "A released player remained trapped inside prison bounds");
            h.assertTrue(f.data.ledger().history().getLast().action().equals("EMERGENCY_RELEASE"), "The release was not audited");
            int count = f.data.ledger().history().size(); f.act(f.admin, "release", json);
            h.assertTrue(f.data.ledger().history().size() == count, "Replaying a release duplicated an audit or applied another decision");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void ordinaryPrisonersCannotAuthorizeTheirOwnEmergencyRelease(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); f.open(f.prisoner); var json = f.mutation(f.prisoner);
            json.addProperty("player", f.prisoner.getUUID().toString()); json.addProperty("reason", "Unauthorized release");
            f.act(f.prisoner, "release", json); h.assertTrue(f.sentence() != null && f.data.ledger().history().size() == 1,
                    "An ordinary prisoner could release themselves through a forged physical action");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 160)
    public static void actualOnlineTicksPauseOfflinePersistAndAutomaticallyReleaseExactlyOnce(GameTestHelper h) {
        var f = new JusticeFixture(h);
        try {
            f.convict(16); JusticeService.login(f.prisoner);
            h.runAfterDelay(3, () -> {
                try {
                    h.assertTrue(f.sentence() != null && f.sentence().servedTicks() > 0 && f.sentence().remainingTicks() > 0,
                            "Real server Post ticks did not advance the online prisoner's sentence");
                    long served = f.sentence().servedTicks(); JusticeService.logout(f.prisoner); f.actors.disconnect(f.prisoner);
                    h.assertTrue(JusticeData.read(f.directory.resolve("laws_justice.dat")).active().get(f.prisoner.getUUID()).servedTicks() == served,
                            "Logout did not checkpoint the actual online sentence to its world file");
                    h.runAfterDelay(5, () -> {
                        try {
                            h.assertTrue(f.sentence().servedTicks() == served, "Offline server ticks reduced the prisoner's remaining sentence");
                            f.prisoner.setPos(f.pos.getX() + 100, f.pos.getY(), f.pos.getZ()); f.actors.reconnect(f.prisoner); JusticeService.login(f.prisoner);
                            inside(h, f, f.prisoner); h.assertTrue(f.sentence().servedTicks() == served, "Reconnection counted wall time as served prison time");
                            h.runAfterDelay(22, () -> {
                                try {
                                    h.assertTrue(f.sentence() == null, "The real online sentence did not release automatically when its remaining ticks reached zero");
                                    h.assertTrue(!f.prison().area().contains(JusticeFixture.point(f.prisoner.blockPosition())), "Automatic release did not move the player outside their prison");
                                    long completed = f.data.ledger().history().stream().filter(row -> row.action().equals("COMPLETED")).count();
                                    h.assertTrue(completed == 1 && f.data.ledger().history().getLast().servedTicks() == 16,
                                            "Automatic completion overshot its sentence or logged release more than once");
                                    System.out.println("JUSTICE_CUSTODY_PASSED"); f.close(); h.succeed();
                                } catch (Throwable failure) { f.close(); throw failure; }
                            });
                        } catch (Throwable failure) { f.close(); throw failure; }
                    });
                } catch (Throwable failure) { f.close(); throw failure; }
            });
        } catch (Throwable failure) { f.close(); throw failure; }
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void sentencingUsesTheActualOptionalNotificationApiOrPrivateFallback(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); f.open(f.admin); f.prisoner.clear(); f.act(f.admin, "sentence", f.sentenceMutation(f.prisoner, 1));
            h.assertTrue(f.sentence() != null, "A valid sentencing decision failed before its notification");
            if (net.neoforged.fml.ModList.get().isLoaded("realmnotifications")) h.assertTrue(f.prisoner.notices() == 1,
                    "The real optional notification API omitted or duplicated the sentencing notification");
            else h.assertTrue(f.prisoner.notices() == 0 && !f.prisoner.chat.isEmpty(), "Standalone mode omitted the prisoner's private fallback notice");
        }
        h.succeed();
    }
}
