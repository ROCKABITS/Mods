package pt.lawsjustice.test;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.domain.CourtLedger;
import pt.lawsjustice.domain.CourtLedger.CaseFile;
import pt.lawsjustice.domain.CourtLedger.Warrant;
import pt.lawsjustice.service.CourtService;

/** Real Council state and real physical court sessions share the existing isolated justice world file. */
final class CourtFixture implements AutoCloseable {
    final JusticeFixture base;
    final GameTestHelper helper;
    final JusticeActors.Actor president, judge, complainant, accused, stranger;
    final BlockPos desk;
    final Council council;
    boolean closed;

    CourtFixture(GameTestHelper helper) {
        this.helper = helper; base = new JusticeFixture(helper);
        president = base.visitor; accused = base.prisoner;
        complainant = base.actors.actor("ComplainantQA", false); stranger = base.actors.actor("CourtStrangerQA", false);
        council = ModList.get().isLoaded("realmcouncil") ? new Council(helper.getLevel().getServer()) : null;
        if (council != null) council.mandate(president.getUUID(), System.currentTimeMillis() - 1_000,
                System.currentTimeMillis() + 3_600_000, System.currentTimeMillis() - 500);
        judge = council == null ? base.admin : president;
        desk = base.pos.offset(-1, 0, -1); helper.getLevel().setBlockAndUpdate(desk, BlockRegistry.JUSTICE_DESK.get().defaultBlockState());
    }

    CourtLedger court() { return base.data.court(); }
    void open(JusticeActors.Actor actor) {
        actor.setServerLevel(helper.getLevel()); actor.setPos(desk.getX() + .5, desk.getY(), desk.getZ() - 1.5);
        helper.getLevel().getBlockState(desk).useWithoutItem(helper.getLevel(), actor,
                new BlockHitResult(Vec3.atCenterOf(desk), Direction.NORTH, desk, false));
        helper.assertTrue(view(actor).get("open").getAsBoolean(), "A real physical Justice Desk did not authorize the court session");
    }
    JsonObject view(JusticeActors.Actor actor) { return CourtService.snapshot(actor); }
    JsonObject mutation(JusticeActors.Actor actor) {
        var state = view(actor); var request = new JsonObject(); request.addProperty("nonce", state.get("nonce").getAsString());
        request.addProperty("version", state.get("version").getAsLong()); return request;
    }
    void act(JusticeActors.Actor actor, String operation, JsonObject request) { CourtService.handle(actor, operation, request.toString()); }
    UUID file() {
        open(complainant); var request = mutation(complainant); request.addProperty("accused", accused.getUUID().toString());
        request.addProperty("accusation", "A native physical complaint for an alleged theft"); act(complainant, "court_file", request);
        helper.assertTrue(court().cases().size() == 1, "A player's real physical complaint did not create exactly one case");
        return court().cases().keySet().iterator().next();
    }
    void hear(UUID caseId) {
        open(judge); var request = mutation(judge); request.addProperty("case", caseId.toString()); act(judge, "court_hear", request);
        helper.assertTrue(court().cases().get(caseId).status().equals("HEARING"), "The authorized actual judge could not start a hearing");
    }
    JsonObject verdict(UUID caseId) {
        var request = mutation(judge); request.addProperty("case", caseId.toString()); request.addProperty("reason", "Native judicial finding"); return request;
    }
    Warrant warrant(JusticeActors.Actor target, long issued, long expires) {
        var warrant = new Warrant(UUID.randomUUID(), target.getUUID(), target.getGameProfile().getName(), judge.getUUID(), "Native judge",
                council == null ? "ADMIN_EMERGENCY" : "PRESIDENT", "A server-authorized temporary arrest", issued, expires, "ACTIVE", "");
        base.data.replaceCourt(court().issue(warrant)); return warrant;
    }
    Warrant warrant() { long now = System.currentTimeMillis(); return warrant(accused, now - 1_000, now + 240_000); }
    ItemStack paper(UUID id) {
        var stack = new ItemStack(BlockRegistry.WARRANT.get()); var tag = new CompoundTag(); tag.putString("lawsjustice_warrant", id.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); return stack;
    }
    void equip(JusticeActors.Actor escort, UUID id) {
        escort.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlockRegistry.HANDCUFFS.get())); escort.getInventory().add(paper(id));
        escort.setPos(desk.getX() + .5, desk.getY(), desk.getZ() - 1.5); accused.setPos(desk.getX() + 1.5, desk.getY(), desk.getZ() - 1.5);
    }
    void cuff(JusticeActors.Actor escort) {
        escort.getMainHandItem().interactLivingEntity(escort, accused, InteractionHand.MAIN_HAND);
    }
    static void attempt(Runnable operation) { try { operation.run(); } catch (RuntimeException expected) { /* Rejections may be translated by the item adapter. */ } }

    /** Only the installed Council is changed, and its preceding state is always restored. */
    static final class Council implements AutoCloseable {
        final Object data, original;
        final Field stateField;
        final Class<?> stateType;
        Council(MinecraftServer server) {
            try {
                Class<?> type = Class.forName("pt.realmcouncil.CouncilData"); stateType = Class.forName("pt.realmcouncil.domain.CouncilState");
                data = type.getMethod("get", MinecraftServer.class).invoke(null, server); stateField = type.getField("state");
                original = stateField.get(data); stateField.set(data, stateType.getConstructor().newInstance());
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("The actual Realm Council fixture is incompatible", failure); }
        }
        void mandate(UUID player, long begins, long ends, long sworn) {
            try {
                Class<?> type = Class.forName("pt.realmcouncil.domain.CouncilState$Mandate"); Object mandate = type.getConstructor().newInstance();
                type.getField("electionId").set(mandate, UUID.randomUUID()); type.getField("playerId").set(mandate, player);
                type.getField("name").set(mandate, "Native President"); type.getField("title").set(mandate, "President");
                type.getField("beginsAt").setLong(mandate, begins); type.getField("endsAt").setLong(mandate, ends); type.getField("claimedAt").setLong(mandate, sworn);
                stateType.getField("mandate").set(stateField.get(data), mandate);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        void empty() { try { stateField.set(data, stateType.getConstructor().newInstance()); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); } }
        void unavailable() { try { stateField.set(data, null); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); } }
        @Override public void close() { try { stateField.set(data, original); } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); } }
    }
    @Override public void close() {
        if (closed) return; closed = true;
        for (var actor : base.actors.actors) CourtService.logout(actor);
        if (council != null) council.close(); base.close();
    }
}
