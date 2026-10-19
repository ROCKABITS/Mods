package pt.lawsjustice.test;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.domain.CourtLedger;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.service.CourtService;

/** Actual physical papers and handcuff items are checked against the authoritative server ledger. */
@GameTestHolder("lawsjustice")
@PrefixGameTestTemplate(false)
public final class CourtWarrantGameTests {
    private CourtWarrantGameTests() {}
    private static void unrestrained(GameTestHelper h, CourtFixture f, String message) {
        h.assertTrue(f.court().restraints().isEmpty() && !CourtService.restricted(f.accused), message);
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aJudicialWarrantIsDurableBeforeItsRealPhysicalPaperIsIssued(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            f.open(f.judge); var request = f.mutation(f.judge); request.addProperty("accused", f.accused.getUUID().toString());
            request.addProperty("reason", "Physical warrant native proof"); request.addProperty("hours", 24); f.act(f.judge, "court_warrant", request);
            h.assertTrue(f.court().warrants().size() == 1, "The actual authorized judge could not issue a warrant");
            var warrant = f.court().warrants().values().iterator().next();
            h.assertTrue(pt.lawsjustice.data.JusticeData.readState(f.base.directory.resolve("laws_justice.dat")).court().warrants().containsKey(warrant.id()),
                    "A physical warrant was created before its server authorization became durable");
            ItemStack paper = f.judge.getInventory().items.stream().filter(stack -> stack.is(BlockRegistry.WARRANT.get())).findFirst().orElseThrow();
            h.assertTrue(paper.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("lawsjustice_warrant").equals(warrant.id().toString()),
                    "The issued physical document does not reference its authoritative warrant");
            f.judge.setItemInHand(InteractionHand.MAIN_HAND, paper.copy()); paper.getItem().use(h.getLevel(), f.judge, InteractionHand.MAIN_HAND);
            var document = f.judge.packetSnapshot(); h.assertTrue(document.get("kind").getAsString().equals("warrant") && document.get("valid").getAsBoolean(),
                    "The actual physical warrant did not open a server-validated parchment document");
            f.act(f.judge, "court_warrant", request); h.assertTrue(f.court().warrants().size() == 1, "Replaying an issuance action duplicated its authoritative warrant");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void copiedDisplayFieldsAndInventedWarrantIdentifiersCannotAuthorizeAnArrest(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            f.equip(f.complainant, UUID.randomUUID()); f.cuff(f.complainant); unrestrained(h, f, "An invented physical warrant identifier authorized an arrest");
            var valid = f.warrant(f.stranger, System.currentTimeMillis() - 1_000, System.currentTimeMillis() + 60_000);
            f.complainant.getInventory().clearContent(); f.equip(f.complainant, valid.id());
            var paper = f.complainant.getInventory().items.stream().filter(s -> s.is(BlockRegistry.WARRANT.get())).findFirst().orElseThrow();
            var forged = paper.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); forged.putString("accused", f.accused.getUUID().toString());
            forged.putString("author", f.complainant.getUUID().toString()); forged.putString("reason", "Forged display authorization"); paper.set(DataComponents.CUSTOM_DATA, CustomData.of(forged));
            f.cuff(f.complainant); unrestrained(h, f, "Copied item display fields changed the server's specified accused or issuing authority");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void expiredFutureAndRevokedWarrantsCannotAuthorizePhysicalHandcuffs(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            long now = System.currentTimeMillis();
            for (String kind : List.of("expired", "future", "revoked")) {
                var warrant = switch (kind) {
                    case "expired" -> f.warrant(f.accused, now - 120_000, now - 60_000);
                    case "future" -> f.warrant(f.accused, now + 60_000, now + 120_000);
                    default -> f.warrant();
                };
                if (kind.equals("revoked")) f.base.data.replaceCourt(f.court().revoke(warrant.id()));
                f.complainant.getInventory().clearContent(); f.equip(f.complainant, warrant.id()); f.cuff(f.complainant);
                unrestrained(h, f, "An " + kind + " warrant authorized physical handcuffs");
            }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void anyOrdinaryWarrantBearerMayApplyPartialRestraintWithoutChangingGameModeOrAbilities(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            var warrant = f.warrant(); f.equip(f.complainant, warrant.id());
            double speed = f.accused.getAttributeValue(Attributes.MOVEMENT_SPEED); var mode = f.accused.gameMode.getGameModeForPlayer();
            boolean mayFly = f.accused.getAbilities().mayfly, mayBuild = f.accused.getAbilities().mayBuild;
            f.cuff(f.complainant);
            h.assertTrue(f.court().restraints().containsKey(f.accused.getUUID()) && CourtService.restricted(f.accused)
                    && f.court().warrants().get(warrant.id()).status().equals("EXECUTED"), "A valid ordinary warrant bearer could not apply the actual handcuff item exactly once");
            h.assertTrue(Math.abs(f.accused.getAttributeValue(Attributes.MOVEMENT_SPEED) / speed - .4) < .00001,
                    "The physical restraint did not apply the intended partial 60 percent movement reduction");
            h.assertTrue(f.accused.gameMode.getGameModeForPlayer() == mode && f.accused.getAbilities().mayfly == mayFly && f.accused.getAbilities().mayBuild == mayBuild,
                    "Handcuffs permanently changed game mode or player abilities");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aConsumedWarrantCannotBeReusedAfterHandcuffsAreReleased(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            var warrant = f.warrant(); f.equip(f.complainant, warrant.id()); double speed = f.accused.getAttributeValue(Attributes.MOVEMENT_SPEED);
            f.cuff(f.complainant); CourtService.releaseHandcuffs(f.complainant, f.accused);
            unrestrained(h, f, "The original escort could not remove their temporary handcuffs");
            h.assertTrue(Math.abs(f.accused.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < .00001, "Releasing handcuffs left a permanent movement penalty");
            f.cuff(f.complainant); unrestrained(h, f, "A copied or reused executed warrant authorized another arrest");
            h.assertTrue(f.court().warrants().get(warrant.id()).status().equals("EXECUTED"), "Uncuffing restored the consumed warrant authorization");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void handcuffsRequireThePhysicalItemNearbyValidPaperAndNoCreativeOrSelfArrest(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            var warrant = f.warrant(); f.equip(f.complainant, warrant.id());
            f.complainant.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK)); CourtService.applyHandcuffs(f.complainant, f.accused);
            unrestrained(h, f, "An actor without physical handcuffs could arrest a player");
            f.equip(f.complainant, warrant.id()); f.complainant.setPos(f.accused.getX() + 8, f.accused.getY(), f.accused.getZ()); f.cuff(f.complainant);
            unrestrained(h, f, "Physical handcuffs arrested a distant player");
            f.equip(f.complainant, warrant.id()); f.complainant.setGameMode(GameType.CREATIVE); f.cuff(f.complainant);
            unrestrained(h, f, "Creative mode bypassed physical arrest authorization"); f.complainant.setGameMode(GameType.SURVIVAL);
            CourtService.applyHandcuffs(f.complainant, f.complainant); unrestrained(h, f, "An actor could authorize their own self-arrest");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void unrelatedPlayersCannotUncuffButExplicitEmergencyAdministratorsCan(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            var warrant = f.warrant(); f.equip(f.complainant, warrant.id()); f.cuff(f.complainant);
            f.stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlockRegistry.HANDCUFFS.get())); f.stranger.setPos(f.accused.getX() + 1, f.accused.getY(), f.accused.getZ());
            CourtService.releaseHandcuffs(f.stranger, f.accused); h.assertTrue(CourtService.restricted(f.accused), "An unrelated ordinary player released someone else's handcuffs");
            f.base.admin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlockRegistry.HANDCUFFS.get())); f.base.admin.setPos(f.accused.getX() + 1, f.accused.getY(), f.accused.getZ());
            CourtService.releaseHandcuffs(f.base.admin, f.accused); unrestrained(h, f, "An explicit nearby administrator could not perform emergency uncuffing");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void anEscortLogoutReleasesTheActualMovementModifierAndKeepsTheWarrantConsumed(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            var warrant = f.warrant(); f.equip(f.complainant, warrant.id()); double speed = f.accused.getAttributeValue(Attributes.MOVEMENT_SPEED); f.cuff(f.complainant);
            CourtService.logout(f.complainant); f.base.actors.disconnect(f.complainant); CourtService.login(f.accused);
            unrestrained(h, f, "A missing escort left a permanent temporary detention");
            h.assertTrue(Math.abs(f.accused.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < .00001 && f.court().warrants().get(warrant.id()).status().equals("EXECUTED"),
                    "Escort logout left a speed penalty or restored an already consumed warrant");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void targetLogoutKeepsOnlyTheBoundedDetentionAndExpiredLoginCleansTheMovementModifier(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            var warrant = f.warrant(); f.equip(f.complainant, warrant.id()); double speed = f.accused.getAttributeValue(Attributes.MOVEMENT_SPEED); f.cuff(f.complainant);
            CourtService.logout(f.accused); f.base.actors.disconnect(f.accused);
            h.assertTrue(f.court().restraints().containsKey(f.accused.getUUID()), "The restrained target escaped authorization by disconnecting");
            var detention = f.court().restraints().get(f.accused.getUUID());
            h.assertTrue(detention.expiresAt() <= warrant.expiresAt() && detention.expiresAt() - detention.appliedAt() <= 300_000,
                    "A temporary arrest exceeded its bounded wall-clock or warrant validity");
            var expired = f.court().expire(detention.expiresAt()); f.base.data.replaceCourt(expired); f.base.actors.reconnect(f.accused); CourtService.login(f.accused);
            unrestrained(h, f, "An expired offline arrest was restored on login");
            h.assertTrue(Math.abs(f.accused.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < .00001, "Expired login retained a stale handcuff movement modifier");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aValidIssuedWarrantSurvivesPresidentialTurnoverUntilItsOwnExpiry(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            var warrant = f.warrant();
            if (f.council != null) { long now = System.currentTimeMillis(); f.council.mandate(f.stranger.getUUID(), now - 1_000, now + 60_000, now - 500); }
            f.equip(f.complainant, warrant.id()); f.cuff(f.complainant);
            h.assertTrue(CourtService.restricted(f.accused) && f.court().warrants().get(warrant.id()).status().equals("EXECUTED"),
                    "Presidential turnover incorrectly invalidated a previously lawful unexpired physical warrant");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void anAcquittalInvalidatesTheAssociatedWarrantEvenWhenItsPaperStillExists(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            var id = f.file(); f.hear(id); long now = System.currentTimeMillis();
            var warrant = new CourtLedger.Warrant(UUID.randomUUID(), f.accused.getUUID(), "Native accused", f.judge.getUUID(), "Native judge",
                    f.council == null ? "ADMIN_EMERGENCY" : "PRESIDENT", "An accusation-bound warrant", now - 1_000, now + 60_000, "ACTIVE", id.toString());
            f.base.data.replaceCourt(f.court().issue(warrant)); f.open(f.judge); f.act(f.judge, "court_acquit", f.verdict(id));
            h.assertTrue(f.court().cases().get(id).status().equals("ACQUITTED"), "The valid hearing was not acquitted before its warrant check");
            f.equip(f.complainant, warrant.id()); f.cuff(f.complainant);
            unrestrained(h, f, "A physical warrant tied to an acquitted case still authorized an arrest");
        }
        h.succeed();
    }
}
