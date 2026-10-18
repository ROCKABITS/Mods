package pt.lawsjustice.test;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.domain.JusticeLedger.Area;
import pt.lawsjustice.domain.JusticeLedger.Cell;
import pt.lawsjustice.domain.JusticeLedger.Point;
import pt.lawsjustice.domain.JusticeLedger.Prison;
import pt.lawsjustice.domain.JusticeLedger.Sentence;
import pt.lawsjustice.service.JusticeService;

/** Every case owns a real isolated durable world file and restores the preceding native world state. */
final class JusticeFixture implements AutoCloseable {
    final GameTestHelper helper;
    final JusticeActors actors;
    final JusticeActors.Actor admin, prisoner, visitor;
    final JusticeData data, previous;
    final Map<MinecraftServer, JusticeData> loaded;
    final Path directory;
    final BlockPos pos;
    final UUID prisonId = UUID.randomUUID(), cellId = UUID.randomUUID();
    final String dimension;
    private boolean closed;

    @SuppressWarnings("unchecked") JusticeFixture(GameTestHelper helper) {
        this.helper = helper; actors = new JusticeActors(helper);
        admin = actors.actor("JusticeAdminQA", true); prisoner = actors.actor("PrisonerQA", false); visitor = actors.actor("VisitorQA", false);
        previous = JusticeData.get(helper.getLevel().getServer());
        try {
            Field cache = JusticeData.class.getDeclaredField("LOADED"); cache.setAccessible(true);
            loaded = (Map<MinecraftServer, JusticeData>) cache.get(null);
            directory = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("justice-native/" + UUID.randomUUID());
            Files.createDirectories(directory);
            data = new JusticeData(); field(data, "file", directory.resolve("laws_justice.dat"));
            loaded.put(helper.getLevel().getServer(), data); data.replace(JusticeLedger.empty());
        } catch (ReflectiveOperationException | java.io.IOException failure) { throw new IllegalStateException(failure); }
        pos = helper.absolutePos(new BlockPos(5, 2, 5)); dimension = helper.getLevel().dimension().location().toString();
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, BlockRegistry.PRISON_CONTROL.get().defaultBlockState());
        admin.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        visitor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        prisoner.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
    }
    static Point point(BlockPos pos) { return new Point(pos.getX(), pos.getY(), pos.getZ()); }
    Prison prison() { return data.ledger().prisons().get(prisonId); }
    Cell cell() { return prison().cells().stream().filter(c -> c.id().equals(cellId)).findFirst().orElseThrow(); }
    Sentence sentence() { return data.ledger().active().get(prisoner.getUUID()); }
    Prison register() {
        for(int x=-6;x<=8;x++)for(int z=-6;z<=8;z++)helper.getLevel().setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());
        var area = Area.between(dimension, point(pos.offset(-2, 0, -2)), point(pos.offset(6, 5, 6)));
        var cell = new Cell(cellId, "Cell A", Area.between(dimension, point(pos.offset(1, 0, 1)), point(pos.offset(3, 2, 3))), point(pos.offset(2, 0, 2)), 1);
        data.replace(data.ledger().putPrison(new Prison(prisonId, "Native Prison", area, point(pos.offset(-4, 0, 0)), List.of(cell), 0)));
        return prison();
    }
    Sentence convict(long ticks) {
        if (prison() == null) register();
        var row = new Sentence(UUID.randomUUID(), prisoner.getUUID(), "Native prisoner", prisonId, cellId,
                admin.getUUID(), "Native administrator", "Native custody proof", System.currentTimeMillis(), ticks, 0, 0, 0);
        data.replace(data.ledger().sentence(row)); return row;
    }
    void open(JusticeActors.Actor actor) {
        actor.setServerLevel(helper.getLevel()); actor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), actor,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        helper.assertTrue(view(actor).get("open").getAsBoolean(), "Physical prison control did not create a real server session");
    }
    JsonObject view(JusticeActors.Actor actor) { return JusticeService.snapshot(actor); }
    JsonObject mutation(JusticeActors.Actor actor) {
        var snapshot = view(actor); var json = new JsonObject(); json.addProperty("nonce", snapshot.get("nonce").getAsString());
        json.addProperty("version", snapshot.get("version").getAsLong()); return json;
    }
    void act(JusticeActors.Actor actor, String operation, JsonObject json) { JusticeService.handle(actor, operation, json.toString()); }
    void select(BlockPos a, BlockPos b, BlockPos anchor) {
        click(a, false); click(b, false); click(anchor.below(), true);
        admin.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
    }
    void click(BlockPos clicked, boolean anchor) {
        admin.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(BlockRegistry.AREA_SELECTOR.get()));
        admin.setPos(clicked.getX() + .5, clicked.getY() + 1, clicked.getZ() + .5);
        JusticeService.select(admin, clicked, anchor);
    }
    JsonObject sentenceMutation(JusticeActors.Actor target, int minutes) {
        var json = mutation(admin); json.addProperty("player", target.getUUID().toString()); json.addProperty("prison", prisonId.toString());
        json.addProperty("cell", cellId.toString()); json.addProperty("minutes", minutes); json.addProperty("reason", "Native process reason"); return json;
    }
    static void field(Object receiver, String name, Object value) {
        try { Field field = receiver.getClass().getDeclaredField(name); field.setAccessible(true); field.set(receiver, value); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    static void rejected(GameTestHelper helper, Runnable operation) {
        boolean rejected = false; try { operation.run(); } catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected, "An invalid production operation or persisted state was accepted");
    }
    @Override public void close() {
        if (closed) return; closed = true;
        for (var actor : actors.actors) JusticeService.logout(actor);
        loaded.put(helper.getLevel().getServer(), previous); actors.close();
        try (var paths = Files.walk(directory)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
            try { Files.deleteIfExists(path); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
