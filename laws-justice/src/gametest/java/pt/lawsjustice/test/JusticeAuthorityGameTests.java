package pt.lawsjustice.test;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.lawsjustice.service.JusticeService;

/** Actual physical server sessions, live operator checks, privacy and authoritative selections. */
@GameTestHolder("lawsjustice")
@PrefixGameTestTemplate(false)
public final class JusticeAuthorityGameTests {
    private JusticeAuthorityGameTests() {}

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void runtimeLoadsTheActualRequestedTenCompanionsAndOptionalIntegrations(GameTestHelper h) {
        boolean integration = Boolean.getBoolean("justice.integration");
        for (String id : List.of("cronicasrp", "realmnotifications", "contracts", "realmcouncil", "kingdomtreasury",
                "realmmarkets", "realmrewards", "realmidentity", "realmtitles", "realmcasino"))
            h.assertTrue(ModList.get().isLoaded(id) == integration, "Unexpected actual runtime mode for " + id);
        if (integration) for (String id : List.of("origins", "jupiter"))
            h.assertTrue(ModList.get().isLoaded(id), "Actual runtime dependency was not loaded: " + id);
        System.out.println("JUSTICE_RUNTIME_MODE_PASSED integration=" + integration);
        System.out.println("JUSTICE_NATIVE_PASSED integration=" + integration);
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void realPhysicalControlSendsAProductionSnapshotToAnOrdinaryPlayer(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.open(f.visitor); var state = f.visitor.packetSnapshot();
            h.assertTrue(state.get("protocol").getAsInt() == 1 && state.get("open").getAsBoolean()
                    && !state.get("admin").getAsBoolean(), "A real ordinary-player physical session or production S2C packet is missing");
            h.assertTrue(!state.toString().contains("PRIVATE JUSTICE"), "A snapshot exposed private RP profile fields");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void ordinaryPlayersCannotSentenceEvenWithAValidPhysicalSession(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); f.open(f.visitor); var json = f.mutation(f.visitor);
            json.addProperty("player", f.prisoner.getUUID().toString()); json.addProperty("prison", f.prisonId.toString());
            json.addProperty("cell", f.cellId.toString()); json.addProperty("minutes", 1); json.addProperty("reason", "Unauthorized");
            f.act(f.visitor, "sentence", json);
            h.assertTrue(f.data.ledger().active().isEmpty() && f.data.ledger().history().isEmpty(), "An ordinary player authorized a judicial decision");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void operatorAuthorityIsRecheckedAfterThePhysicalMenuWasOpened(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); f.open(f.admin); var json = f.sentenceMutation(f.prisoner, 1);
            f.admin.server.getPlayerList().getOps().remove(f.admin.getGameProfile());
            h.assertTrue(!f.admin.hasPermissions(2), "The arranged native operator revocation did not take effect");
            f.act(f.admin, "sentence", json);
            h.assertTrue(f.data.ledger().active().isEmpty(), "A revoked administrator retained judicial powers through a cached menu");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void anAdministratorCannotMutateWithoutFirstOpeningAPhysicalBlock(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); var json = new JsonObject(); json.addProperty("nonce", UUID.randomUUID().toString()); json.addProperty("version", 0);
            json.addProperty("player", f.prisoner.getUUID().toString()); json.addProperty("prison", f.prisonId.toString());
            json.addProperty("cell", f.cellId.toString()); json.addProperty("minutes", 1); json.addProperty("reason", "Forged remote menu");
            f.act(f.admin, "sentence", json);
            h.assertTrue(f.data.ledger().active().isEmpty(), "An administrator could send a remote mutation with an invented physical session");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void inventedNonceAndStaleVersionCannotCreateASentence(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); f.open(f.admin);
            for (String mutation : List.of("nonce", "version")) {
                f.open(f.admin); var json = f.sentenceMutation(f.prisoner, 1);
                if (mutation.equals("nonce")) json.addProperty("nonce", UUID.randomUUID().toString());
                else json.addProperty("version", json.get("version").getAsLong() + 1);
                f.act(f.admin, "sentence", json);
                h.assertTrue(f.data.ledger().active().isEmpty(), "A forged " + mutation + " was accepted");
            }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aCommittedSentenceCannotBeReplayedIntoADuplicateDecision(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); f.open(f.admin); var json = f.sentenceMutation(f.prisoner, 1);
            f.act(f.admin, "sentence", json); h.assertTrue(f.sentence() != null, "The valid native sentencing action was not accepted");
            var sentence = f.sentence(); int history = f.data.ledger().history().size();
            f.act(f.admin, "sentence", json);
            h.assertTrue(f.data.ledger().active().size() == 1 && f.sentence().id().equals(sentence.id())
                    && f.data.ledger().history().size() == history, "Replaying a committed action duplicated a sentence or audit decision");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void movingAwayOrRemovingThePhysicalControlInvalidatesMutations(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); f.open(f.admin); var json = f.sentenceMutation(f.prisoner, 1);
            f.admin.setPos(f.pos.getX() + 30, f.pos.getY(), f.pos.getZ()); f.act(f.admin, "sentence", json);
            h.assertTrue(f.data.ledger().active().isEmpty(), "A distant physical control still authorized a sentence");
            f.open(f.admin); json = f.sentenceMutation(f.prisoner, 1);
            h.getLevel().setBlockAndUpdate(f.pos, Blocks.AIR.defaultBlockState()); f.act(f.admin, "sentence", json);
            h.assertTrue(f.data.ledger().active().isEmpty(), "A removed physical control still authorized a sentence");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void closingOrChangingDimensionInvalidatesThePhysicalSession(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); f.open(f.admin); var json = f.sentenceMutation(f.prisoner, 1);
            f.act(f.admin, "close", new JsonObject()); f.act(f.admin, "sentence", json);
            h.assertTrue(f.data.ledger().active().isEmpty(), "A closed menu retained authority");
            f.open(f.admin); json = f.sentenceMutation(f.prisoner, 1);
            var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertTrue(nether != null, "The actual native Nether is missing"); f.admin.setServerLevel(nether);
            f.act(f.admin, "sentence", json); f.admin.setServerLevel(h.getLevel());
            h.assertTrue(f.data.ledger().active().isEmpty(), "A menu in another dimension retained judicial authority");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void serverSelectionsCreateThePrisonInsteadOfForgedClientCoordinates(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.open(f.admin); var a = f.pos.offset(-2, 0, -2); var b = f.pos.offset(5, 4, 5); var release = f.pos.offset(-4, 0, 0);
            f.select(a, b, release); var json = f.mutation(f.admin); json.addProperty("name", "Selected prison");
            var invented = new JsonObject(); invented.addProperty("x", 29_000_000); invented.addProperty("y", 64); invented.addProperty("z", 29_000_000);
            json.add("min", invented); json.add("max", invented); json.add("release", invented);
            f.act(f.admin, "save_prison", json);
            if (!f.data.ledger().prisons().isEmpty()) {
                var prison = f.data.ledger().prisons().values().iterator().next();
                h.assertTrue(prison.area().min().equals(JusticeFixture.point(a)) && prison.area().max().equals(JusticeFixture.point(b))
                        && prison.release().equals(JusticeFixture.point(release)), "Client coordinates overrode the authoritative selection");
            } else {
                h.assertTrue(f.admin.packetSnapshot().has("error"), "Unknown coordinate fields were neither rejected nor safely ignored");
            }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void selectionRequiresOperatorAuthorityAndDoesNotAcceptInventedAreas(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.open(f.visitor); JusticeService.select(f.visitor, f.pos, false); JusticeService.select(f.visitor, f.pos.offset(2, 2, 2), false);
            var selection = f.view(f.visitor).getAsJsonObject("selection");
            h.assertTrue(selection == null || !selection.has("a") && !selection.has("b"), "An ordinary player retained an administrator area selection");
            f.open(f.admin); var json = f.mutation(f.admin); json.addProperty("name", "Without selection"); f.act(f.admin, "save_prison", json);
            h.assertTrue(f.data.ledger().prisons().isEmpty(), "A prison could be created without a complete server-side area selection");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void nonexistentAndOfflineTargetsAreRejectedWithoutAuditOrCustody(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register(); f.open(f.admin); var json = f.sentenceMutation(f.prisoner, 1); json.addProperty("player", UUID.randomUUID().toString());
            f.act(f.admin, "sentence", json); h.assertTrue(f.data.ledger().active().isEmpty(), "A nonexistent target was sentenced by a forged UUID");
            f.open(f.admin); json = f.sentenceMutation(f.prisoner, 1); f.actors.disconnect(f.prisoner); f.act(f.admin, "sentence", json);
            h.assertTrue(f.data.ledger().active().isEmpty() && f.data.ledger().history().isEmpty(), "An offline target bypassed online sentencing validation");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void invalidDurationsAndReasonsNeverBecomeCustodyDecisions(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.register();
            for (int minutes : List.of(-1, 0, 43_201)) {
                f.open(f.admin); var json = f.sentenceMutation(f.prisoner, minutes); f.act(f.admin, "sentence", json);
                h.assertTrue(f.data.ledger().active().isEmpty(), "An invalid sentence duration was accepted: " + minutes);
            }
            f.open(f.admin); var json = f.sentenceMutation(f.prisoner, 1); json.addProperty("reason", " "); f.act(f.admin, "sentence", json);
            h.assertTrue(f.data.ledger().history().isEmpty(), "A blank sentencing reason entered the permanent audit");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aFullCellRejectsAnotherSentenceAndPreservesTheOriginalPrisoner(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); f.open(f.admin); f.act(f.admin, "sentence", f.sentenceMutation(f.visitor, 1));
            h.assertTrue(f.data.ledger().active().size() == 1 && f.sentence() != null && !f.data.ledger().active().containsKey(f.visitor.getUUID()),
                    "A full real cell accepted a second occupant or evicted its existing prisoner");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void activeOfflineCustodyPreventsCellAndPrisonRemoval(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); f.actors.disconnect(f.prisoner);
            f.open(f.admin); var json = f.mutation(f.admin); json.addProperty("prison", f.prisonId.toString()); json.addProperty("cell", f.cellId.toString());
            f.act(f.admin, "remove_cell", json); h.assertTrue(f.prison().cells().size() == 1, "An occupied offline cell was removed");
            f.open(f.admin); json = f.mutation(f.admin); json.addProperty("prison", f.prisonId.toString()); f.act(f.admin, "remove_prison", json);
            h.assertTrue(f.prison() != null && f.sentence() != null, "An occupied offline prison was removed or its sentence silently dropped");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void ordinarySnapshotsExposeOnlyTheirOwnSentenceAndNoOtherPlayersOrAudit(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); f.open(f.prisoner); var own = f.view(f.prisoner);
            h.assertTrue(own.has("self") && !own.get("self").isJsonNull(), "A prisoner cannot read their own remaining sentence");
            h.assertTrue(own.getAsJsonArray("active").isEmpty() && own.getAsJsonArray("history").isEmpty()
                    && own.getAsJsonArray("players").isEmpty(), "An ordinary player received the private global custody or player lists");
            f.open(f.visitor); h.assertTrue(f.view(f.visitor).get("self").isJsonNull(), "A visitor received another prisoner's private sentence");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void optionalChroniclesRpNamesAreUsedWithoutExposingPrivateCharacterFields(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.open(f.admin); String names = f.view(f.admin).getAsJsonArray("players").toString();
            String expected = ModList.get().isLoaded("cronicasrp") ? "RP PrisonerQA" : "PrisonerQA";
            h.assertTrue(names.contains(expected) && !f.view(f.admin).toString().contains("PRIVATE JUSTICE"),
                    "Actual optional RP names were not resolved or private character fields leaked");
        }
        h.succeed();
    }
}
