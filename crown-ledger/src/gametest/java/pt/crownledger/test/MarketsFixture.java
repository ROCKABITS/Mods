package pt.crownledger.test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Uses the actual optional Markets production jar, physical placement and inventory transaction. */
final class MarketsFixture implements AutoCloseable {
    private final Object data;
    private final Field ledgerField;
    private final Object original;
    private final FakePlayer owner;
    private final FakePlayer buyer;
    private final UUID stall;

    static boolean loaded() { return ModList.get().isLoaded("realmmarkets"); }

    MarketsFixture(GameTestHelper helper, FakePlayer owner, FakePlayer buyer, int stock, long price) {
        this.owner = owner;
        this.buyer = buyer;
        data = callStatic("pt.realmmarkets.data.MarketData", "get", new Class<?>[]{MinecraftServer.class}, owner.server);
        try {
            ledgerField = data.getClass().getField("ledger");
            original = ledgerField.get(data);
            ledgerField.set(data, Class.forName("pt.realmmarkets.domain.MarketLedger").getConstructor().newInstance());
            ((SavedData) data).setDirty();
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Could not isolate the actual Markets SavedData", failure); }
        BlockPos master = helper.absolutePos(new BlockPos(7, 1, 4));
        Direction facing = Direction.SOUTH;
        Direction right = facing.getCounterClockWise();
        helper.getLevel().setBlockAndUpdate(master.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(master.relative(right).below(), Blocks.STONE.defaultBlockState());
        for (int part = 0; part < 4; part++) {
            BlockPos at = (BlockPos) callStatic("pt.realmmarkets.block.MarketStallBlock", "position",
                    new Class<?>[]{BlockPos.class, Direction.class, int.class}, master, facing, part);
            helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        }
        owner.setPos(master.getX() + .5, master.getY(), master.getZ() + 2.5);
        owner.setYRot(facing.getOpposite().toYRot());
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("realmmarkets", "crimson_stall"));
        var stack = new ItemStack(item);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.assertTrue(stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(master), Direction.UP, master.below(), false))).consumesAction(),
                "The actual optional market block item failed physical placement");
        callStatic("pt.realmmarkets.service.MarketService", "open", new Class<?>[]{ServerPlayer.class, BlockPos.class}, owner, master);
        Object current = callStatic("pt.realmmarkets.service.MarketService", "requireSession", new Class<?>[]{ServerPlayer.class}, owner);
        stall = (UUID) call(current, "id", new Class<?>[]{});
        owner.getInventory().clearContent();
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
        service("trustedConfigure", new Class<?>[]{ServerPlayer.class, int.class, long.class}, owner, 0, price);
        owner.getInventory().clearContent();
        owner.getInventory().setItem(0, new ItemStack(Items.APPLE, stock));
        service("trustedStock", new Class<?>[]{ServerPlayer.class, int.class, int.class}, owner, 0, stock);
        buyer.setPos(master.getX() + .5, master.getY(), master.getZ() + 2.5);
        callStatic("pt.realmmarkets.service.MarketService", "open", new Class<?>[]{ServerPlayer.class, BlockPos.class}, buyer, master);
        callStatic("pt.realmmarkets.service.MarketService", "requireSession", new Class<?>[]{ServerPlayer.class}, buyer);
    }

    void purchase(int quantity) { service("trustedBuy", new Class<?>[]{ServerPlayer.class, int.class, int.class}, buyer, 0, quantity); }
    void claim() { service("trustedClaim", new Class<?>[]{ServerPlayer.class}, owner); }
    long earnings() {
        Object ledger;
        try { ledger = ledgerField.get(data); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        Object current = ((Optional<?>) call(ledger, "find", new Class<?>[]{UUID.class}, stall)).orElseThrow();
        return (long) call(current, "earnings", new Class<?>[]{});
    }
    long tax() { return (long) callStatic("pt.realmmarkets.api.MarketsApi", "taxBalance", new Class<?>[]{MinecraftServer.class, String.class}, owner.server, "EMERALD"); }
    int rate() { return (int) callStatic("pt.realmmarkets.api.MarketsApi", "salesTaxBasisPoints", new Class<?>[]{MinecraftServer.class}, owner.server); }

    private static Object service(String method, Class<?>[] signature, Object... args) {
        return callStatic("pt.realmmarkets.service.MarketService", method, signature, args);
    }
    static Object callStatic(String type, String method, Class<?>[] signature, Object... args) {
        try { return Class.forName(type).getMethod(method, signature).invoke(null, args); }
        catch (InvocationTargetException failure) { throw unwrap(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Actual Markets0.2.0 API unavailable", failure); }
    }
    private static Object call(Object target, String method, Class<?>[] signature, Object... args) {
        try { return target.getClass().getMethod(method, signature).invoke(target, args); }
        catch (InvocationTargetException failure) { throw unwrap(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Actual Markets record unavailable", failure); }
    }
    private static RuntimeException unwrap(Throwable cause) {
        if (cause instanceof RuntimeException runtime) return runtime;
        if (cause instanceof Error error) throw error;
        return new IllegalStateException(cause);
    }
    @Override public void close() {
        service("logout", new Class<?>[]{UUID.class}, owner.getUUID());
        service("logout", new Class<?>[]{UUID.class}, buyer.getUUID());
        try { ledgerField.set(data, original); ((SavedData) data).setDirty(); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }
}
