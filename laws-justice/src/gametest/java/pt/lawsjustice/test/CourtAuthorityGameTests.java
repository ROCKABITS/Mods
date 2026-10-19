package pt.lawsjustice.test;

import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.lawsjustice.compat.CouncilAuthority;
import pt.lawsjustice.data.JusticeData;

/** Live installed Council mandates, physical court authority, private evidence and atomic custody verdicts. */
@GameTestHolder("lawsjustice")
@PrefixGameTestTemplate(false)
public final class CourtAuthorityGameTests {
    private CourtAuthorityGameTests() {}

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void theActualCouncilPresidentOrExplicitAdministratorIsTheOnlyJudicialAuthority(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            boolean integrated = Boolean.getBoolean("justice.integration");
            h.assertTrue(CouncilAuthority.loaded() == integrated, "The actual Council integration does not match this native runtime mode");
            h.assertTrue(CouncilAuthority.mayJudge(f.base.admin), "The explicit emergency administrator has no judicial access");
            h.assertTrue(!CouncilAuthority.mayJudge(f.complainant) && !CouncilAuthority.mayJudge(f.stranger), "An ordinary player acquired an invented judicial role");
            if (integrated) h.assertTrue(CouncilAuthority.governor(f.base.admin.server, System.currentTimeMillis()).id().equals(f.president.getUUID())
                    && CouncilAuthority.mayJudge(f.president), "The installed elected and sworn President was not recognized");
            else h.assertTrue(CouncilAuthority.governor(f.base.admin.server, System.currentTimeMillis()) == null && !CouncilAuthority.mayJudge(f.president),
                    "Standalone mode invented a President instead of limiting authorization to administrators");
            f.open(f.judge); var state = f.view(f.judge);
            h.assertTrue(state.get("judge").getAsBoolean() && state.get("president").getAsBoolean() == integrated,
                    "The physical court snapshot misrepresented actual judicial authority");
            System.out.println("CROWN_JUDGE_PASSED integration=" + integrated);
            System.out.println("COURT_NATIVE_PASSED integration=" + integrated);
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void futureExpiredUnclaimedAndImpossibleOathsCannotAuthorizeAJudicialAction(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            UUID id = f.file(); f.open(f.president); long now = System.currentTimeMillis();
            if (f.council != null) for (long[] times : List.of(new long[]{now - 1_000, now + 60_000, 0},
                    new long[]{now + 60_000, now + 120_000, now + 60_000}, new long[]{now - 60_000, now - 1_000, now - 50_000},
                    new long[]{now - 1_000, now + 60_000, now + 30_000}, new long[]{now - 1_000, now + 60_000, now - 2_000})) {
                f.council.mandate(f.president.getUUID(), times[0], times[1], times[2]);
                h.assertTrue(CouncilAuthority.governor(f.base.admin.server, now) == null, "An invalid real Council mandate still identifies a President");
                var request = f.mutation(f.president); request.addProperty("case", id.toString()); f.act(f.president, "court_hear", request);
                h.assertTrue(f.court().cases().get(id).status().equals("OPEN"), "An invalid Council mandate authorized a hearing through an already open menu");
            }
            else {
                var request = f.mutation(f.president); request.addProperty("case", id.toString()); f.act(f.president, "court_hear", request);
                h.assertTrue(f.court().cases().get(id).status().equals("OPEN"), "An ordinary player could hear a case without Realm Council");
            }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void actualPresidentialTurnoverImmediatelyRevokesAnExistingCourtSession(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            UUID id = f.file(); f.open(f.president); var old = f.mutation(f.president); old.addProperty("case", id.toString());
            if (f.council != null) {
                long now = System.currentTimeMillis(); f.council.mandate(f.stranger.getUUID(), now - 1_000, now + 60_000, now - 500);
                f.act(f.president, "court_hear", old); h.assertTrue(f.court().cases().get(id).status().equals("OPEN"), "A former President retained authority through a cached physical session");
                f.open(f.stranger); var current = f.mutation(f.stranger); current.addProperty("case", id.toString()); f.act(f.stranger, "court_hear", current);
                h.assertTrue(f.court().cases().get(id).status().equals("HEARING"), "The new sworn President could not assume the existing case");
            } else {
                f.act(f.president, "court_hear", old); h.assertTrue(f.court().cases().get(id).status().equals("OPEN"), "Standalone mode allowed a non-administrator judge");
            }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void missingOrUnreadableCouncilSuspendsOrdinaryDecisionsAndKeepsEmergencyAccessExplicit(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            UUID id = f.file(); f.open(f.president);
            if (f.council != null) {
                f.council.empty(); var request = f.mutation(f.president); request.addProperty("case", id.toString()); f.act(f.president, "court_hear", request);
                h.assertTrue(f.court().cases().get(id).status().equals("OPEN"), "A missing President did not suspend ordinary judicial decisions");
                f.council.unavailable();
                JusticeFixture.rejected(h, () -> CouncilAuthority.governor(f.base.admin.server, System.currentTimeMillis()));
                request = f.mutation(f.president); request.addProperty("case", id.toString()); f.act(f.president, "court_hear", request);
                h.assertTrue(f.court().cases().get(id).status().equals("OPEN"), "An unreadable installed Council silently authorized an ordinary fallback judge");
                f.council.empty();
            }
            f.open(f.base.admin); var emergency = f.mutation(f.base.admin); emergency.addProperty("case", id.toString()); f.act(f.base.admin, "court_hear", emergency);
            h.assertTrue(f.court().cases().get(id).status().equals("HEARING") && f.court().cases().get(id).authorityKind().equals("ADMIN_EMERGENCY"),
                    "Administrator emergency access was unavailable or recorded as an ordinary Presidency");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void playerComplaintsAndEvidenceAreVisibleOnlyToThePartiesAndCurrentJudge(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            UUID id = f.file(); var request = f.mutation(f.complainant); request.addProperty("case", id.toString()); request.addProperty("text", "PRIVATE CASE EVIDENCE: the missing royal seal");
            f.act(f.complainant, "court_evidence", request); h.assertTrue(f.court().cases().get(id).evidence().size() == 1, "A party could not append their actual physical evidence");
            f.open(f.accused); h.assertTrue(f.view(f.accused).getAsJsonArray("cases").toString().contains("PRIVATE CASE EVIDENCE"), "The accused cannot review evidence in their own case");
            f.open(f.stranger); h.assertTrue(f.view(f.stranger).getAsJsonArray("cases").isEmpty() && !f.view(f.stranger).toString().contains("PRIVATE CASE EVIDENCE"),
                    "An unrelated player received private accusations or evidence");
            request = f.mutation(f.stranger); request.addProperty("case", id.toString()); request.addProperty("text", "Unauthorized evidence injection"); f.act(f.stranger, "court_evidence", request);
            h.assertTrue(f.court().cases().get(id).evidence().size() == 1, "An unrelated player altered someone else's case");
            f.open(f.judge); h.assertTrue(f.view(f.judge).getAsJsonArray("cases").size() == 1, "The actual current judge cannot review pending private cases");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aVerdictRequiresAHearingAndCannotBeReplayedOrChangedAfterAcquittal(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            UUID id = f.file(); f.open(f.judge); f.act(f.judge, "court_acquit", f.verdict(id));
            h.assertTrue(f.court().cases().get(id).status().equals("OPEN"), "An unheard complaint received a verdict");
            f.hear(id); var request = f.verdict(id); f.act(f.judge, "court_acquit", request); var decision = f.court().cases().get(id);
            h.assertTrue(decision.status().equals("ACQUITTED") && decision.sentence().isEmpty() && f.base.data.ledger().active().isEmpty(),
                    "A valid acquittal created a prison sentence or failed to resolve the case");
            f.act(f.judge, "court_acquit", request); f.open(f.judge); f.act(f.judge, "court_acquit", f.verdict(id));
            h.assertTrue(f.court().cases().get(id).equals(decision), "Replaying or resubmitting a resolved verdict changed the permanent decision");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aPrisonVerdictCommitsItsCaseAndExactCustodySentenceInOneNativeWorldFile(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            f.base.register(); UUID id = f.file(); f.hear(id); var request = f.verdict(id);
            request.addProperty("prison", f.base.prisonId.toString()); request.addProperty("cell", f.base.cellId.toString()); request.addProperty("minutes", 3);
            f.act(f.judge, "court_convict", request); var decision = f.court().cases().get(id); var sentence = f.base.sentence();
            h.assertTrue(decision.status().equals("CONVICTED") && sentence != null && decision.sentence().equals(sentence.id().toString())
                    && sentence.player().equals(f.accused.getUUID()) && sentence.originalTicks() == 3600 && decision.originalTicks() == 3600,
                    "A court verdict and its exact prison sentence diverged");
            h.assertTrue(decision.judge().equals(f.judge.getUUID().toString()) && decision.authorityKind().equals(f.council == null ? "ADMIN_EMERGENCY" : "PRESIDENT"),
                    "A judicial verdict stored a client-supplied or incorrect judicial authority");
            var restored = JusticeData.readState(f.base.directory.resolve("laws_justice.dat"));
            h.assertTrue(restored.court().state().equals(f.court().state()) && restored.ledger().active().equals(f.base.data.ledger().active()),
                    "The atomic native file did not restore both sides of the conviction");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void anActualFailedVerdictWriteCommitsNeitherTheCaseNorItsPrisonSentence(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            f.base.register(); UUID id = f.file(); f.hear(id); var before = f.court().cases().get(id); var request = f.verdict(id);
            request.addProperty("prison", f.base.prisonId.toString()); request.addProperty("cell", f.base.cellId.toString()); request.addProperty("minutes", 1);
            var file = f.base.directory.resolve("laws_justice.dat"); JusticeFixture.field(f.base.data, "file", f.base.directory);
            f.act(f.judge, "court_convict", request); JusticeFixture.field(f.base.data, "file", file);
            h.assertTrue(f.court().cases().get(id).equals(before) && f.base.data.ledger().active().isEmpty(), "A real failed atomic write committed half of a prison verdict");
            var restored = JusticeData.readState(file); h.assertTrue(restored.court().cases().get(id).equals(before) && restored.ledger().active().isEmpty(),
                    "The previous durable court hearing was lost when its verdict write failed");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void courtMutationsRejectForgedSessionTokensAndRemovedPhysicalDesks(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            UUID id = f.file();
            for (String forged : List.of("nonce", "version", "physical")) {
                f.open(f.judge); var request = f.mutation(f.judge); request.addProperty("case", id.toString());
                if (forged.equals("nonce")) request.addProperty("nonce", UUID.randomUUID().toString());
                else if (forged.equals("version")) request.addProperty("version", request.get("version").getAsLong() + 1);
                else h.getLevel().setBlockAndUpdate(f.desk, Blocks.AIR.defaultBlockState());
                f.act(f.judge, "court_hear", request); h.assertTrue(f.court().cases().get(id).status().equals("OPEN"), "A forged or nonphysical court mutation was accepted: " + forged);
            }
        }
        h.succeed();
    }
    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aPhaseOneWorldMigratesWithoutResettingCustodyOrInventingCases(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            f.base.convict(1200); var file=f.base.directory.resolve("laws_justice.dat");
            try {
                var tag=net.minecraft.nbt.NbtIo.readCompressed(file,net.minecraft.nbt.NbtAccounter.create(16_777_216));
                var old=com.google.gson.JsonParser.parseString(new String(tag.getByteArray("ledger"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                old.remove("court");old.remove("work");old.remove("finance");tag.putInt("format",1);tag.putByteArray("ledger",old.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));net.minecraft.nbt.NbtIo.writeCompressed(tag,file);
                var migrated=JusticeData.readState(file);
                h.assertTrue(migrated.ledger().active().equals(f.base.data.ledger().active()) && migrated.court().cases().isEmpty(),"Phase-one migration changed custody or invented a case");
                migrated.replaceCourt(migrated.court());
                h.assertTrue(net.minecraft.nbt.NbtIo.readCompressed(file,net.minecraft.nbt.NbtAccounter.create(16_777_216)).getInt("format")==4,"The migrated file did not use the current atomic format");
                h.assertTrue(JusticeData.readState(file).ledger().active().equals(f.base.data.ledger().active()),"Migration lost the sentence on its next reopen");
            } catch(java.io.IOException error) { throw new IllegalStateException(error); }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aCourtVerdictCannotBeCommittedWithoutItsMatchingCustodySentence(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            UUID id=f.file();f.hear(id);var previous=f.court();
            var incomplete=previous.decide(id,f.judge.getUUID(),"Judge",f.council==null?"ADMIN_EMERGENCY":"PRESIDENT","Reasoned decision",UUID.randomUUID().toString(),true,System.currentTimeMillis());
            JusticeFixture.rejected(h,()->f.base.data.replaceCourt(incomplete));
            h.assertTrue(f.court()==previous && f.base.data.ledger().active().isEmpty(),"An incomplete verdict reached live state");
            h.assertTrue(JusticeData.readState(f.base.directory.resolve("laws_justice.dat")).court().state().equals(previous.state()),"An incomplete verdict reached the world file");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void openingAnotherPhysicalJusticeMenuClosesThePreviousPushSession(GameTestHelper h) {
        try(var f=new CourtFixture(h)) {
            f.base.open(f.judge);h.assertTrue(f.base.view(f.judge).get("open").getAsBoolean(),"Prison menu did not open");
            f.open(f.judge);h.assertTrue(!f.base.view(f.judge).get("open").getAsBoolean(),"Old prison pushes can replace the court screen");
            f.base.open(f.judge);h.assertTrue(!f.view(f.judge).get("open").getAsBoolean(),"Old court pushes can replace the prison screen");
        }
        h.succeed();
    }

    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void aPhaseTwoCourtWorldMigratesItsCasesAndCustodyWithoutInventingWorkCredits(GameTestHelper h){
        try(var f=new CourtFixture(h)){
            f.base.register();UUID id=f.file();f.hear(id);var file=f.base.directory.resolve("laws_justice.dat");
            try{
                var tag=net.minecraft.nbt.NbtIo.readCompressed(file,net.minecraft.nbt.NbtAccounter.create(16_777_216));var json=com.google.gson.JsonParser.parseString(new String(tag.getByteArray("ledger"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();json.remove("work");json.remove("finance");for(var row:json.getAsJsonObject("court").getAsJsonObject("cases").entrySet())row.getValue().getAsJsonObject().remove("originalTicks");tag.putInt("format",2);tag.putByteArray("ledger",json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));net.minecraft.nbt.NbtIo.writeCompressed(tag,file);
                var migrated=JusticeData.readState(file);h.assertTrue(migrated.court().state().equals(f.court().state())&&migrated.work().jobs().isEmpty()&&migrated.work().credits().isEmpty(),"Phase-two migration lost the court or invented work credit");migrated.replaceCourt(migrated.court());h.assertTrue(JusticeData.readState(file).court().cases().get(id).status().equals("HEARING"),"Reopening the migrated court lost its active hearing");
            }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        }h.succeed();
    }

}
