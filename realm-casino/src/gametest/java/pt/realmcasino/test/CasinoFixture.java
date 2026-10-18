package pt.realmcasino.test;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pt.realmcasino.block.CasinoMachineBlock;
import pt.realmcasino.data.CasinoData;
import pt.realmcasino.domain.CasinoLedger;
import pt.realmcasino.service.CasinoService;

/** Isolated actual durable data and real physical sessions. No production test hooks or alternate wallet. */
final class CasinoFixture implements AutoCloseable {
    final GameTestHelper helper;
    final CasinoActors actors;
    final CasinoData data;
    final CasinoData previous;
    final Path directory;
    final Map<MinecraftServer, CasinoData> loaded;
    final CasinoActors.Actor owner;
    final CasinoActors.Actor player;
    final BlockPos pos;
    final CasinoMachineBlock block;

    @SuppressWarnings("unchecked")
    CasinoFixture(GameTestHelper helper, CasinoMachineBlock block) {
        this.helper = helper; this.block = block;
        actors = new CasinoActors(helper); owner = actors.actor("CasinoOwnerQA", 2_000_000); player = actors.actor("CasinoPlayerQA", 100_000);
        previous = CasinoData.get(helper.getLevel().getServer());
        try {
            Field cache = CasinoData.class.getDeclaredField("LOADED"); cache.setAccessible(true);
            loaded = (Map<MinecraftServer, CasinoData>) cache.get(null);
            directory = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("casino-native/" + UUID.randomUUID());
            Files.createDirectories(directory); data = new CasinoData();
            field(data, "file", directory.resolve("realm_casino.dat"));
            data.replace(new CasinoLedger(), Map.of());
            loaded.put(helper.getLevel().getServer(), data);
        } catch (ReflectiveOperationException | java.io.IOException failure) { throw new IllegalStateException(failure); }
        pos = CasinoBlockGameTests.base(helper);
        CasinoBlockGameTests.place(helper, owner, pos, block, Direction.NORTH);
    }

    void open(CasinoActors.Actor actor) {
        actor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), actor,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        helper.assertTrue(!view(actor).get("closed").getAsBoolean(), "A real physical right click did not authorize the casino screen");
    }
    JsonObject view(CasinoActors.Actor actor) { return CasinoService.snapshot(actor); }
    JsonObject mutation(CasinoActors.Actor actor) {
        var snapshot = view(actor); var json = new JsonObject();
        json.addProperty("nonce", snapshot.get("nonce").getAsString()); json.addProperty("version", snapshot.get("version").getAsLong());
        return json;
    }
    JsonObject amount(CasinoActors.Actor actor, long amount) { var json = mutation(actor); json.addProperty("amount", amount); return json; }
    void act(CasinoActors.Actor actor, String operation, JsonObject json) { CasinoService.action(actor, operation, json.toString()); }
    void amount(CasinoActors.Actor actor, String operation, long amount) { act(actor, operation, amount(actor, amount)); }
    void fund(long amount) { open(owner); amount(owner, "deposit", amount); helper.assertTrue(machine().bank() == amount, "Actual wallet funding did not enter the machine bank"); }
    CasinoLedger.Machine machine() { return data.ledger().at(helper.getLevel().dimension().location().toString(), pos.asLong()).orElseThrow(); }
    long pending(CasinoActors.Actor actor) { return data.ledger().credit(actor.getUUID()); }
    static void field(Object receiver, String name, Object value) {
        try { Field field = receiver.getClass().getDeclaredField(name); field.setAccessible(true); field.set(receiver, value); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    static final class Entropy implements AutoCloseable {
        final Field field; final Object original;
        Entropy(Random random) {
            try {
                field = java.util.Arrays.stream(CasinoService.class.getDeclaredFields()).filter(f -> java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && Random.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();
                field.setAccessible(true); original = field.get(null); field.set(null, random);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not arrange test-only authoritative server entropy", failure); }
        }
        @Override public void close() { try { field.set(null, original); } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); } }
    }
    static Random draws(int... sequence) {
        return new Random(0) {
            int index;
            @Override public int nextInt(int bound) { return Math.floorMod(sequence[Math.min(index++, sequence.length - 1)], bound); }
        };
    }
    @Override public void close() {
        for (var actor : actors.actors) CasinoService.logout(actor);
        loaded.put(helper.getLevel().getServer(), previous);
        actors.close();
        try (var paths = Files.walk(directory)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
            try { Files.deleteIfExists(path); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
