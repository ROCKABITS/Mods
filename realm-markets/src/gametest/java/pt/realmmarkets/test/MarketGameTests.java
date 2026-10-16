package pt.realmmarkets.test;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import pt.realmmarkets.api.MarketsApi;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmmarkets.block.BlockRegistry;
import pt.realmmarkets.block.MarketStallBlock;
import pt.realmmarkets.data.MarketData;
import pt.realmmarkets.domain.Currency;
import pt.realmmarkets.domain.Listing;
import pt.realmmarkets.domain.MarketLedger;
import pt.realmmarkets.domain.Stall;
import pt.realmmarkets.service.MarketService;

/** Native blocks, inventories, authorization, purchases, conservation, and SavedData. */
@GameTestHolder("realmmarkets")
@PrefixGameTestTemplate(false)
public final class MarketGameTests {
    private MarketGameTests() {}

    /** Tests are synchronous; restore only their own mutations before the next fixture starts. */
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final MarketData data;
        final MarketLedger original;
        final List<FakePlayer> players = new ArrayList<>();
        final BlockPos master;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.data = MarketData.get(helper.getLevel().getServer());
            this.original = data.ledger.copy();
            this.master = helper.absolutePos(new BlockPos(4, 1, 4));
        }

        FakePlayer player() {
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MarketQA"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(master.getX() + .5, master.getY(), master.getZ() + 2.5);
            players.add(player);
            return player;
        }

        Stall place(FakePlayer owner) {
            return place(owner, Direction.SOUTH, false);
        }

        Stall place(FakePlayer owner, Direction facing, boolean sage) {
            var level = helper.getLevel();
            Direction right = facing.getCounterClockWise();
            for (var floor : List.of(master.below(), master.relative(right).below())) {
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            }
            for (int part = 0; part < 4; part++) {
                level.setBlock(MarketStallBlock.position(master, facing, part), Blocks.AIR.defaultBlockState(), 2);
            }
            owner.setYRot(facing.getOpposite().toYRot());
            owner.setPos(master.getX() + facing.getStepX() * 2 + .5, master.getY(),
                    master.getZ() + facing.getStepZ() * 2 + .5);
            var stack = new ItemStack(sage ? BlockRegistry.SAGE_STALL_ITEM.get() : BlockRegistry.CRIMSON_STALL_ITEM.get(), 2);
            owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(master), Direction.UP, master.below(), false);
            helper.assertTrue(stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                    "The actual physical stall item failed to place its structure");
            helper.assertTrue(stack.getCount() == 1, "Stall placement did not consume exactly one physical item");
            var result = data.ledger.at(level.dimension().location().toString(), master.asLong()).orElseThrow();
            MarketService.open(owner, master);
            helper.assertTrue(MarketService.requireSession(owner).id().equals(result.id()),
                    "A placed physical stall did not open its owner's authorized session");
            return result;
        }

        void open(FakePlayer player) {
            player.setPos(master.getX() + .5, master.getY(), master.getZ() + 2.5);
            MarketService.open(player, master);
            MarketService.requireSession(player);
        }

        Stall current(Stall stall) { return data.ledger.find(stall.id()).orElseThrow(); }

        void apples(FakePlayer owner, int quantity, long price) {
            owner.getInventory().clearContent();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            MarketService.trustedConfigure(owner, 0, price);
            owner.getInventory().clearContent();
            give(owner, Items.APPLE, quantity);
            MarketService.trustedStock(owner, 0, quantity);
        }

        void replace(Stall replacement) {
            var records = new ArrayList<>(data.ledger.stalls());
            records.removeIf(stall -> stall.id().equals(replacement.id()));
            records.add(replacement);
            data.replace(new MarketLedger(records, data.ledger.taxBalances()));
        }

        @Override public void close() {
            for (var player : players) MarketService.logout(player.getUUID());
            data.replace(original);
        }
    }

    private static void rejected(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException | SecurityException expected) { rejected = true; }
        helper.assertTrue(rejected, "A server operation accepted invalid or unauthorized input");
    }

    private static void give(FakePlayer player, Item item, int count) {
        for (int slot = 0; slot < 36 && count > 0; slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) continue;
            int amount = Math.min(item.getDefaultMaxStackSize(), count);
            player.getInventory().setItem(slot, new ItemStack(item, amount));
            count -= amount;
        }
        if (count != 0) throw new AssertionError("Test supplies did not fit the player's real inventory");
    }

    private static void fill(FakePlayer player) {
        player.getInventory().clearContent();
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
    }

    private static Listing offer(Fixture fixture, Stall stall) {
        return fixture.current(stall).listings().stream().filter(listing -> listing.slot() == 0).findFirst().orElseThrow();
    }

    private static String buy(int slot, int quantity, long price, String item) {
        return buy(slot, quantity, price, item, 0);
    }

    private static String buy(int slot, int quantity, long price, String item, int rate) {
        return "{\"slot\":" + slot + ",\"quantity\":" + quantity + ",\"price\":" + price
                + ",\"item\":\"" + item + "\",\"marketTaxBasisPoints\":" + rate + "}";
    }

    /** Changes only the trusted server capability, restoring the actual Crown provider afterwards. */
    private static final class TaxProvider implements AutoCloseable {
        final MarketsApi.TreasuryProvider original;
        final MarketsApi.TreasuryProvider replacement;
        TaxProvider(MarketsApi.TreasuryProvider replacement) {
            try {
                var field = MarketsApi.class.getDeclaredField("treasury");
                field.setAccessible(true);
                original = (MarketsApi.TreasuryProvider) field.get(null);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            this.replacement = replacement;
            if (original != null) MarketsApi.unregisterTreasury(original);
            if (replacement != null) MarketsApi.registerTreasury(replacement);
        }
        @Override public void close() {
            if (replacement != null) MarketsApi.unregisterTreasury(replacement);
            if (original != null) MarketsApi.registerTreasury(original);
        }
    }

    /** Arrange only one immutable expiry field; the live physical authorization stays valid. */
    @SuppressWarnings("unchecked")
    private static void expireSession(FakePlayer player, boolean wallClock) {
        try {
            var field = MarketService.class.getDeclaredField("SESSIONS");
            field.setAccessible(true);
            var sessions = (Map<UUID, Object>) field.get(null);
            Object session = sessions.get(player.getUUID());
            var components = session.getClass().getRecordComponents();
            Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                var accessor = components[i].getAccessor();
                accessor.setAccessible(true);
                values[i] = accessor.invoke(session);
                if (wallClock && components[i].getName().equals("expiresAt")) values[i] = System.currentTimeMillis() - 1;
                if (!wallClock && components[i].getName().equals("expiresTick")) values[i] = player.getServer().overworld().getGameTime();
            }
            var constructor = session.getClass().getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            sessions.put(player.getUUID(), constructor.newInstance(values));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not arrange the existing native session's expiry", failure);
        }
    }

    private static boolean cancelledBreak(Fixture fixture, FakePlayer player, BlockPos pos) {
        var event = new BlockEvent.BreakEvent(fixture.helper.getLevel(), pos,
                fixture.helper.getLevel().getBlockState(pos), player);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    public static final class CancelPlacement {
        private final UUID player;
        CancelPlacement(UUID player) { this.player = player; }
        @SubscribeEvent public void cancel(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity() != null && event.getEntity().getUUID().equals(player)) event.setCanceled(true);
        }
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void twoPhysicalVariantsHaveSurvivalRecipesAndCreativeRegistrations(GameTestHelper helper) {
        var tabKey = ResourceLocation.fromNamespaceAndPath("realmmarkets", "realm_markets");
        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tabKey),
                "The physical market stalls have no registered creative inventory tab");
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabKey);
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, false, helper.getLevel().registryAccess()));
        for (String id : List.of("crimson_stall", "sage_stall")) {
            var key = ResourceLocation.fromNamespaceAndPath("realmmarkets", id);
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(key) && BuiltInRegistries.ITEM.containsKey(key)
                            && helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
                    "A stall block, its physical item, or its survival recipe is missing: " + id);
            var item = BuiltInRegistries.ITEM.get(key);
            helper.assertTrue(tab.getDisplayItems().stream().filter(stack -> stack.is(item)).count() == 1
                            && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(item)),
                    "A physical stall is absent or duplicated in the ordinary player's creative inventory/search: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void actualItemPlacesFourOwnedPartsInAllFourFacings(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            try (var f = new Fixture(helper)) {
                var owner = f.player();
                var stall = f.place(owner, facing, false);
                for (int part = 0; part < 4; part++) {
                    var pos = MarketStallBlock.position(f.master, facing, part);
                    var state = helper.getLevel().getBlockState(pos);
                    helper.assertTrue(state.is(BlockRegistry.CRIMSON_STALL.get())
                                    && state.getValue(MarketStallBlock.PART) == part
                                    && state.getValue(MarketStallBlock.FACING) == facing,
                            "Actual placement produced the wrong part/facing for " + facing + "/" + part);
                    helper.assertTrue(((MarketStallBlock) state.getBlock()).anchor(pos, state).equals(f.master),
                            "A rotated child part did not resolve to its real ownership anchor");
                }
                helper.assertTrue(stall.owner().equals(owner.getUUID()) && stall.currency() == Currency.EMERALD,
                        "Placement did not assign the placing player's UUID or the standalone currency");
                // Remove the old physical fixture before the next facing is placed at the same test location.
                MarketService.removed(helper.getLevel(), f.master);
                for (int part = 0; part < 4; part++) helper.getLevel().setBlock(
                        MarketStallBlock.position(f.master, facing, part), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void sageVariantPlacesAndAnyPhysicalPartOpensTheSameOwnedStall(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner, Direction.WEST, true);
            var buyer = f.player();
            for (int part = 0; part < 4; part++) {
                var pos = MarketStallBlock.position(f.master, Direction.WEST, part);
                var state = helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.is(BlockRegistry.SAGE_STALL.get()), "The sage variant lost one physical part");
                state.useWithoutItem(helper.getLevel(), buyer,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
                helper.assertTrue(MarketService.requireSession(buyer).id().equals(stall.id()),
                        "Right-clicking a physical child part did not open its own master stall");
                MarketService.logout(buyer.getUUID());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void obstructedPlacementCreatesNoPartialStructureOrOwnershipRecord(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            helper.getLevel().setBlockAndUpdate(f.master.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(f.master.east().below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(f.master.east().above(), Blocks.STONE.defaultBlockState());
            owner.setYRot(Direction.NORTH.toYRot());
            var stack = new ItemStack(BlockRegistry.CRIMSON_STALL_ITEM.get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var before = f.data.ledger.stalls();
            stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atBottomCenterOf(f.master), Direction.UP, f.master.below(), false)));
            helper.assertTrue(stack.getCount() == 1 && f.data.ledger.stalls().equals(before)
                            && helper.getLevel().getBlockState(f.master).isAir()
                            && helper.getLevel().getBlockState(f.master.east()).isAir(),
                    "An obstructed two-wide placement consumed its item or left physical/server ownership fragments");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void canceledNativePlacementRestoresAllCellsInventoryAndRegistryWithoutFreeLoot(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            helper.getLevel().setBlockAndUpdate(f.master.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(f.master.east().below(), Blocks.STONE.defaultBlockState());
            owner.setYRot(Direction.NORTH.toYRot());
            var stack = new ItemStack(BlockRegistry.CRIMSON_STALL_ITEM.get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var records = f.data.ledger.stalls();
            var bounds = new AABB(f.master).inflate(3);
            int before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                    .filter(entity -> entity.getItem().is(BlockRegistry.CRIMSON_STALL_ITEM.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            var cancellation = new CancelPlacement(owner.getUUID());
            NeoForge.EVENT_BUS.register(cancellation);
            try {
                stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(f.master), Direction.UP, f.master.below(), false)));
            } finally {
                NeoForge.EVENT_BUS.unregister(cancellation);
            }
            for (int part = 0; part < 4; part++) {
                helper.assertTrue(helper.getLevel().getBlockState(MarketStallBlock.position(f.master, Direction.SOUTH, part)).isAir(),
                        "A canceled native placement left a partial physical shop in the world");
            }
            int after = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                    .filter(entity -> entity.getItem().is(BlockRegistry.CRIMSON_STALL_ITEM.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(stack.getCount() == 1 && f.data.ledger.stalls().equals(records) && after == before,
                    "Canceling a native placement consumed/duplicated its item or retained unplaced ownership");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void onlyTheOwnerCanConfigureStockWithdrawRenameOrClaim(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 4, 3);
            var stranger = f.player();
            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 4));
            f.open(stranger);
            var before = f.current(stall);
            rejected(helper, () -> MarketService.trustedConfigure(stranger, 0, 1));
            rejected(helper, () -> MarketService.trustedStock(stranger, 0, 1));
            rejected(helper, () -> MarketService.trustedWithdraw(stranger, 0, 1));
            rejected(helper, () -> MarketService.trustedRename(stranger, "Stolen stall"));
            rejected(helper, () -> MarketService.trustedClaim(stranger));
            helper.assertTrue(f.current(stall).equals(before) && stranger.getInventory().countItem(Items.APPLE) == 4,
                    "A public buyer changed private stock, ownership, prices, title, or earnings");
            MarketService.trustedRename(owner, "The travelling orchard");
            helper.assertTrue(f.current(stall).title().equals("The travelling orchard"), "The real owner could not rename the stall");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void stockingRemovesRealItemsAndWithdrawalsRestoreExactlyThoseItems(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 70, 2);
            helper.assertTrue(owner.getInventory().countItem(Items.APPLE) == 0 && offer(f, stall).stock() == 70,
                    "Stocking created goods without consuming the owner's actual inventory items");
            MarketService.trustedWithdraw(owner, 0, 65);
            helper.assertTrue(owner.getInventory().countItem(Items.APPLE) == 65 && offer(f, stall).stock() == 5,
                    "Withdrawing did not preserve the exact item total across real inventory and finite stock");
            helper.assertTrue(f.data.isDirty(), "A real stock mutation was not marked for native SavedData persistence");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void offhandAndModifiedItemsNeverBecomePlainSaleStock(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            MarketService.trustedConfigure(owner, 0, 1);
            owner.getInventory().clearContent();
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE, 4));
            rejected(helper, () -> MarketService.trustedStock(owner, 0, 1));
            var named = new ItemStack(Items.APPLE, 4);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("A valuable named apple"));
            owner.setItemInHand(InteractionHand.MAIN_HAND, named);
            rejected(helper, () -> MarketService.trustedConfigure(owner, 1, 1));
            rejected(helper, () -> MarketService.trustedStock(owner, 0, 1));
            helper.assertTrue(offer(f, stall).stock() == 0 && owner.getMainHandItem().getCount() == 4
                            && owner.getMainHandItem().has(DataComponents.CUSTOM_NAME)
                            && owner.getOffhandItem().getCount() == 4,
                    "Offhand or customized items were silently converted into plain stock");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void insufficientRestockAndFullInventoryWithdrawalLeaveAllItemsInPlace(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            give(owner, Items.APPLE, 1);
            rejected(helper, () -> MarketService.trustedStock(owner, 0, 2));
            helper.assertTrue(offer(f, stall).stock() == 3 && owner.getInventory().countItem(Items.APPLE) == 1,
                    "A short restock removed a partial payment of inventory goods");
            fill(owner);
            rejected(helper, () -> MarketService.trustedWithdraw(owner, 0, 1));
            helper.assertTrue(offer(f, stall).stock() == 3 && owner.getInventory().countItem(Items.APPLE) == 0,
                    "A full withdrawal inventory lost or dropped sale stock");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void aRealPurchaseChargesTheExactTotalAndReservesOfflineOwnerEarnings(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 10, 4);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 20);
            f.open(buyer);
            helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(owner.getUUID()) == null,
                    "The offline-owner fixture was accidentally registered as online");
            MarketService.trustedBuy(buyer, 0, 3);
            helper.assertTrue(buyer.getInventory().countItem(Items.APPLE) == 3
                            && buyer.getInventory().countItem(Items.EMERALD) == 8
                            && offer(f, stall).stock() == 7 && f.current(stall).earnings() == 12
                            && owner.getInventory().countItem(Items.EMERALD) == 0,
                    "A real offline-owner sale lost goods, charged the wrong amount, or wrote directly into an offline wallet");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void insufficientFundsNeverDeliverItemsOrChangeStockAndEarnings(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 5, 4);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 7);
            f.open(buyer);
            var before = f.current(stall);
            rejected(helper, () -> MarketService.trustedBuy(buyer, 0, 2));
            helper.assertTrue(f.current(stall).equals(before) && buyer.getInventory().countItem(Items.EMERALD) == 7
                            && buyer.getInventory().countItem(Items.APPLE) == 0,
                    "An unaffordable sale charged money, reserved earnings, or delivered unpaid goods");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void aFullBuyerInventoryCannotPayOrLoseTheRequestedStock(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 5, 2);
            var buyer = f.player();
            fill(buyer);
            buyer.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
            f.open(buyer);
            var before = f.current(stall);
            rejected(helper, () -> MarketService.trustedBuy(buyer, 0, 1));
            helper.assertTrue(f.current(stall).equals(before) && buyer.getInventory().countItem(Items.EMERALD) == 64
                            && buyer.getInventory().countItem(Items.APPLE) == 0,
                    "A full destination inventory was charged or caused paid sale goods to be dropped/lost");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void payingWithTheLastEmeraldCanFreeTheDestinationInventorySlot(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 2, 3);
            var buyer = f.player();
            fill(buyer);
            buyer.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
            f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 1);
            helper.assertTrue(buyer.getInventory().countItem(Items.EMERALD) == 0
                            && buyer.getInventory().countItem(Items.APPLE) == 1
                            && offer(f, stall).stock() == 1 && f.current(stall).earnings() == 3,
                    "The purchase preflight ignored the inventory slot released by the exact emerald payment");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void twoBuyersCannotOversellTheSameFiniteStock(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            var first = f.player();
            var second = f.player();
            give(first, Items.EMERALD, 10);
            give(second, Items.EMERALD, 10);
            f.open(first);
            f.open(second);
            MarketService.trustedBuy(first, 0, 2);
            rejected(helper, () -> MarketService.trustedBuy(second, 0, 2));
            MarketService.trustedBuy(second, 0, 1);
            helper.assertTrue(offer(f, stall).stock() == 0 && f.current(stall).earnings() == 6
                            && first.getInventory().countItem(Items.APPLE) == 2
                            && second.getInventory().countItem(Items.APPLE) == 1
                            && first.getInventory().countItem(Items.EMERALD) == 6
                            && second.getInventory().countItem(Items.EMERALD) == 8,
                    "Two live buyer sessions oversold stock or charged the buyer whose first request failed");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void ownersCannotBuyTheirOwnStockOrConvertTheirMoneyIntoFalseRevenue(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            give(owner, Items.EMERALD, 10);
            var before = f.current(stall);
            rejected(helper, () -> MarketService.trustedBuy(owner, 0, 1));
            helper.assertTrue(f.current(stall).equals(before) && owner.getInventory().countItem(Items.EMERALD) == 10,
                    "The owner generated self-sale revenue or bypassed stock withdrawal rules");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void collectedEarningsFitPartiallyAndTheUnclaimedBalanceRemainsReserved(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 130, 1);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 200);
            f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 130);
            fill(owner);
            owner.getInventory().setItem(35, new ItemStack(Items.EMERALD, 60));
            MarketService.trustedClaim(owner);
            helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 64 && f.current(stall).earnings() == 126,
                    "A partial collection exceeded fitting inventory capacity or discarded the unclaimed balance");
            owner.getInventory().setItem(1, ItemStack.EMPTY);
            MarketService.trustedClaim(owner);
            helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 128 && f.current(stall).earnings() == 62,
                    "A second partial collection did not retain exactly the unpaid remainder");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void completelyFullOwnerInventoryDoesNotEraseReservedEarnings(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 1, 3);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 3);
            f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 1);
            fill(owner);
            try { MarketService.trustedClaim(owner); }
            catch (IllegalArgumentException | IllegalStateException expected) { /* No capacity may be reported as an error. */ }
            helper.assertTrue(f.current(stall).earnings() == 3 && owner.getInventory().countItem(Items.EMERALD) == 0,
                    "A full owner's collection erased reserved earnings or emitted overflowing currency items");
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            MarketService.trustedClaim(owner);
            helper.assertTrue(f.current(stall).earnings() == 0 && owner.getInventory().countItem(Items.EMERALD) == 3,
                    "Reserved earnings could not be collected once real inventory space became available");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void earningsOverflowFailsBeforePaymentOrItemDelivery(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 2, 1);
            var current = f.current(stall);
            f.replace(new Stall(current.id(), current.owner(), current.dimension(), current.position(), current.title(),
                    current.currency(), 900_000_000_000_000_000L - 1, current.listings()));
            var buyer = f.player();
            give(buyer, Items.EMERALD, 2);
            f.open(buyer);
            var before = f.current(stall);
            rejected(helper, () -> MarketService.trustedBuy(buyer, 0, 2));
            helper.assertTrue(f.current(stall).equals(before) && buyer.getInventory().countItem(Items.EMERALD) == 2
                            && buyer.getInventory().countItem(Items.APPLE) == 0,
                    "An overflowing owner's earnings charged money or released goods before validation");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void replacingTheItemInAStockedListingIsRejectedUntilAllGoodsAreWithdrawn(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT));
            rejected(helper, () -> MarketService.trustedConfigure(owner, 0, 5));
            helper.assertTrue(offer(f, stall).item().equals("minecraft:apple") && offer(f, stall).stock() == 3,
                    "Changing a stocked listing transmuted real apples into another item");
            MarketService.trustedWithdraw(owner, 0, 3);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT));
            MarketService.trustedConfigure(owner, 0, 5);
            helper.assertTrue(offer(f, stall).item().equals("minecraft:carrot") && offer(f, stall).stock() == 0
                            && offer(f, stall).unitPrice() == 5,
                    "An emptied listing could not be safely reconfigured without manufacturing goods");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void missingDistantAndBrokenPhysicalStallsDoNotAuthorizeTransactions(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            rejected(helper, () -> MarketService.requireSession(owner));
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            var before = f.current(stall);
            owner.setPos(f.master.getX() + 20, f.master.getY(), f.master.getZ());
            rejected(helper, () -> MarketService.trustedWithdraw(owner, 0, 1));
            f.open(owner);
            helper.getLevel().setBlock(MarketStallBlock.position(f.master, Direction.SOUTH, 3),
                    Blocks.AIR.defaultBlockState(), 2);
            rejected(helper, () -> MarketService.requireSession(owner));
            rejected(helper, () -> MarketService.trustedWithdraw(owner, 0, 1));
            helper.assertTrue(f.current(stall).equals(before), "A distant or incomplete stall released protected goods");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void spectatorDeadAndDifferentDimensionSessionsAreRejected(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            f.place(owner);
            owner.setGameMode(GameType.SPECTATOR);
            rejected(helper, () -> MarketService.requireSession(owner));
            owner.setGameMode(GameType.SURVIVAL);
            f.open(owner);
            owner.setHealth(0);
            rejected(helper, () -> MarketService.requireSession(owner));
            var traveller = f.player();
            f.open(traveller);
            var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "The native server did not create the Nether test dimension");
            traveller.setServerLevel(nether);
            rejected(helper, () -> MarketService.requireSession(traveller));
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void malformedForgedAndFractionalPayloadsNeverChooseAnotherStallOrCreateItems(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 10, 2);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 20);
            f.open(buyer);
            var before = f.current(stall);
            String quote = ",\"price\":2,\"item\":\"minecraft:apple\",\"marketTaxBasisPoints\":0}";
            for (String bad : List.of("{", "[]", "null", "{\"slot\":0,\"quantity\":1.5" + quote,
                    "{\"slot\":0,\"quantity\":1,\"owner\":\"" + owner.getUUID() + "\"" + quote,
                    "{\"slot\":0,\"quantity\":1,\"stall\":\"" + UUID.randomUUID() + "\"" + quote,
                    "{\"slot\":0,\"quantity\":\"1\"" + quote, "{\"slot\":0,\"quantity\":-1" + quote,
                    "{\"slot\":0,\"quantity\":4097" + quote, "{\"slot\":8,\"quantity\":1" + quote,
                    "{\"slot\":0,\"quantity\":1e0" + quote, "{\"slot\":0,\"quantity\":1,\"quantity\":2" + quote,
                    "{\"slot\":0,\"quantity\":1}", buy(0, 1, 2, "minecraft:apple") + " {}")) {
                f.open(buyer);
                MarketService.action(buyer, "buy", bad);
                helper.assertTrue(f.current(stall).equals(before) && buyer.getInventory().countItem(Items.EMERALD) == 20
                                && buyer.getInventory().countItem(Items.APPLE) == 0,
                        "A forged, malformed, fractional, oversized, or extra-field payload changed a real sale: " + bad);
            }
            f.open(buyer);
            MarketService.action(buyer, "invent_money", "{}");
            helper.assertTrue(f.current(stall).equals(before), "An unknown packet operation mutated stall state");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void aValidNetworkActionUsesTheBoundPhysicalSessionAndRealCurrentPrice(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 10);
            f.open(buyer);
            MarketService.action(buyer, "buy", buy(0, 2, 2, "minecraft:apple"));
            helper.assertTrue(offer(f, stall).stock() == 1 && f.current(stall).earnings() == 4
                            && buyer.getInventory().countItem(Items.APPLE) == 2
                            && buyer.getInventory().countItem(Items.EMERALD) == 6,
                    "The real server packet dispatcher failed to execute an authorized physical purchase exactly once");
            MarketService.action(buyer, "close", "{}");
            rejected(helper, () -> MarketService.requireSession(buyer));
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void stalePriceAndReplacedItemQuotesNeverChargeAnUnexpectedPurchase(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 20);
            f.open(buyer);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            MarketService.trustedConfigure(owner, 0, 5);
            var repriced = f.current(stall);
            MarketService.action(buyer, "buy", buy(0, 1, 2, "minecraft:apple"));
            helper.assertTrue(f.current(stall).equals(repriced) && buyer.getInventory().countItem(Items.EMERALD) == 20
                            && buyer.getInventory().countItem(Items.APPLE) == 0,
                    "A stale displayed price charged the newly increased price without buyer consent");
            MarketService.trustedWithdraw(owner, 0, 3);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT, 3));
            MarketService.trustedConfigure(owner, 0, 5);
            MarketService.trustedStock(owner, 0, 3);
            var replaced = f.current(stall);
            MarketService.action(buyer, "buy", buy(0, 1, 5, "minecraft:apple"));
            helper.assertTrue(f.current(stall).equals(replaced) && buyer.getInventory().countItem(Items.EMERALD) == 20
                            && buyer.getInventory().countItem(Items.CARROT) == 0,
                    "A replaced item was bought using the old item's otherwise matching price quote");
            MarketService.action(buyer, "buy", buy(0, 1, 5, "minecraft:carrot"));
            helper.assertTrue(buyer.getInventory().countItem(Items.CARROT) == 1
                            && buyer.getInventory().countItem(Items.EMERALD) == 15
                            && offer(f, stall).stock() == 2 && f.current(stall).earnings() == 5,
                    "Accepting the current item and price quote did not perform the exact authorized purchase");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void offhandAndNamedEmeraldsCannotPayForGoodsOrLoseTheirCustomComponents(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            var buyer = f.player();
            buyer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD, 2));
            f.open(buyer);
            var before = f.current(stall);
            rejected(helper, () -> MarketService.trustedBuy(buyer, 0, 1));
            var named = new ItemStack(Items.EMERALD, 2);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Heirloom gems"));
            buyer.setItemInHand(InteractionHand.MAIN_HAND, named);
            rejected(helper, () -> MarketService.trustedBuy(buyer, 0, 1));
            helper.assertTrue(f.current(stall).equals(before) && buyer.getOffhandItem().getCount() == 2
                            && buyer.getMainHandItem().getCount() == 2
                            && buyer.getMainHandItem().has(DataComponents.CUSTOM_NAME)
                            && buyer.getInventory().countItem(Items.APPLE) == 0,
                    "A payment used protected offhand/customized gems or flattened their identity into ordinary currency");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void containersBundlesAndWrittenBooksCannotBeFlattenedIntoSaleListings(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            for (Item item : List.of(Items.CHEST, Items.SHULKER_BOX, Items.BUNDLE, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK)) {
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                rejected(helper, () -> MarketService.trustedConfigure(owner, 0, 1));
                helper.assertTrue(f.current(stall).listings().isEmpty() && owner.getMainHandItem().is(item)
                                && owner.getMainHandItem().getCount() == 1,
                        "A potentially stateful container/book became a lossy plain-item offer: " + item);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void unavailableChroniclesNeverConvertsAnExistingStallOrItsEarningsIntoEmeralds(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            var current = f.current(stall);
            f.replace(new Stall(current.id(), current.owner(), current.dimension(), current.position(), current.title(),
                    Currency.CHRONICLES, 25, current.listings()));
            var buyer = f.player();
            give(buyer, Items.EMERALD, 64);
            f.open(buyer);
            var before = f.current(stall);
            rejected(helper, () -> MarketService.trustedBuy(buyer, 0, 1));
            rejected(helper, () -> MarketService.trustedClaim(owner));
            var view = MarketService.snapshot(buyer);
            helper.assertTrue(f.current(stall).equals(before) && view.get("currency").equals("CHRONICLES")
                            && ((Number) view.get("balance")).longValue() == 0
                            && buyer.getInventory().countItem(Items.EMERALD) == 64
                            && owner.getInventory().countItem(Items.EMERALD) == 0,
                    "An unavailable optional economy silently converted stored cents into physical emeralds");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void wallClockAndServerTickSessionExpiryRejectOtherwiseValidTransactions(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            var before = f.current(stall);
            for (boolean wallClock : List.of(true, false)) {
                f.open(owner);
                helper.assertTrue(MarketService.requireSession(owner).id().equals(stall.id()),
                        "Expiry test was not authorized before changing its deadline");
                expireSession(owner, wallClock);
                rejected(helper, () -> MarketService.trustedWithdraw(owner, 0, 1));
                helper.assertTrue(f.current(stall).equals(before) && owner.getInventory().countItem(Items.APPLE) == 0,
                        "A timed-out " + (wallClock ? "wall-clock" : "server-tick") + " session released real stock");
            }
            f.open(owner);
            MarketService.logout(owner.getUUID());
            rejected(helper, () -> MarketService.trustedWithdraw(owner, 0, 1));
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void removingGroundSupportNeverErasesAnOwnedFundedStall(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            var before = f.current(stall);
            helper.getLevel().setBlockAndUpdate(f.master.below(), Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(f.master.east().below(), Blocks.AIR.defaultBlockState());
            for (int part = 0; part < 4; part++) {
                helper.assertTrue(helper.getLevel().getBlockState(MarketStallBlock.position(f.master, Direction.SOUTH, part))
                                .is(BlockRegistry.CRIMSON_STALL.get()),
                        "Removing ordinary supporting terrain destroyed a protected market-stall part");
            }
            helper.assertTrue(f.current(stall).equals(before), "A support update erased owned stock or reserved proceeds");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void actuallyBreakingAnEmptyChildRemovesAllFourPartsAndDropsOneOwnedItem(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            var bounds = new AABB(f.master).inflate(3);
            int before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                    .filter(entity -> entity.getItem().is(BlockRegistry.CRIMSON_STALL_ITEM.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(owner.gameMode.destroyBlock(f.master.east().above()),
                    "The actual player break path could not remove an empty child part owned by that player");
            for (int part = 0; part < 4; part++) {
                helper.assertTrue(helper.getLevel().getBlockState(MarketStallBlock.position(f.master, Direction.SOUTH, part)).isAir(),
                        "Breaking an empty child left a detached physical part behind");
            }
            int after = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                    .filter(entity -> entity.getItem().is(BlockRegistry.CRIMSON_STALL_ITEM.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(f.data.ledger.find(stall.id()).isEmpty() && after - before == 1,
                    "Actual child removal kept a stale ownership record or duplicated/lost its physical stall item");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void anActualExplosionCannotDestroyFiniteStockOrMarketGeometry(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 2);
            var before = f.current(stall);
            helper.getLevel().explode(null, f.master.getX() + .5, f.master.getY() + 1.0,
                    f.master.getZ() + .5, 4.0F, Level.ExplosionInteraction.BLOCK);
            for (int part = 0; part < 4; part++) {
                helper.assertTrue(helper.getLevel().getBlockState(MarketStallBlock.position(f.master, Direction.SOUTH, part))
                                .is(BlockRegistry.CRIMSON_STALL.get()),
                        "An actual server explosion destroyed one protected market-stall part");
            }
            helper.assertTrue(f.current(stall).equals(before), "An explosion deleted finite sale goods or owned revenue");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void publicSnapshotsExposeStockAndOwnBalanceButProtectOwnerEarningsAndControls(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            f.place(owner);
            f.apples(owner, 3, 2);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 10);
            f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 1);
            var publicView = MarketService.snapshot(buyer);
            var ownerView = MarketService.snapshot(owner);
            helper.assertTrue(Boolean.FALSE.equals(publicView.get("owner"))
                            && ((Number) publicView.get("earnings")).longValue() == 0
                            && ((Number) publicView.get("balance")).longValue() == 8
                            && Boolean.TRUE.equals(ownerView.get("owner"))
                            && ((Number) ownerView.get("earnings")).longValue() == 2,
                    "A public buyer snapshot revealed private owner earnings or used another player's wallet/authority");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void breakEventsRejectStrangersAndStockedOrFundedCreativeOwners(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            var stranger = f.player();
            helper.assertTrue(cancelledBreak(f, stranger, f.master), "A stranger broke a privately owned empty stall");
            helper.assertTrue(!cancelledBreak(f, owner, f.master), "The owner could not remove an empty stall");
            f.apples(owner, 1, 2);
            owner.setGameMode(GameType.CREATIVE);
            helper.assertTrue(cancelledBreak(f, owner, f.master)
                            && cancelledBreak(f, owner, f.master.east().above()),
                    "Creative mode bypassed real stocked-stall protection through its master or child part");
            owner.setGameMode(GameType.SURVIVAL);
            give(stranger, Items.EMERALD, 2);
            f.open(stranger);
            MarketService.trustedBuy(stranger, 0, 1);
            owner.setGameMode(GameType.CREATIVE);
            helper.assertTrue(offer(f, stall).stock() == 0 && f.current(stall).earnings() == 2
                            && cancelledBreak(f, owner, f.master),
                    "Creative mode erased a stall whose finite stock was sold but whose owner earnings were unclaimed");
            owner.setGameMode(GameType.SURVIVAL);
            MarketService.trustedClaim(owner);
            helper.assertTrue(!cancelledBreak(f, owner, f.master), "An emptied and fully collected stall stayed permanently protected");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void onlyTheMasterHasOnePhysicalLootItemAndTheStructureBlocksPistons(GameTestHelper helper) {
        for (var block : List.of(BlockRegistry.CRIMSON_STALL.get(), BlockRegistry.SAGE_STALL.get())) {
            var state = block.defaultBlockState();
            helper.assertTrue(state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE),
                    "A physical stall variant is missing the native Wither/Ender Dragon destruction immunity tags");
            helper.assertTrue(block.getFlammability(state, helper.getLevel(), helper.absolutePos(BlockPos.ZERO), Direction.UP) == 0
                            && block.getFireSpreadSpeed(state, helper.getLevel(), helper.absolutePos(BlockPos.ZERO), Direction.UP) == 0,
                    "Ordinary fire can spread through or consume a server-owned market stall");
        }
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            f.place(owner);
            int drops = 0;
            for (int part = 0; part < 4; part++) {
                var pos = MarketStallBlock.position(f.master, Direction.SOUTH, part);
                var state = helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK,
                        "Pistons can displace part of a server-owned market stall");
                for (ItemStack stack : Block.getDrops(state, helper.getLevel(), pos, null, owner, ItemStack.EMPTY)) {
                    helper.assertTrue(stack.is(BlockRegistry.CRIMSON_STALL_ITEM.get()), "A stall part produced an unexpected physical loot item");
                    drops += stack.getCount();
                }
            }
            helper.assertTrue(drops == 1, "One four-part stall produces duplicate or missing physical stall-item drops");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void operatorCommandsGivePhysicalItemsButNeverOpenRemoteMarketMenus(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var citizen = f.player();
            var commands = helper.getLevel().getServer().getCommands();
            var source = citizen.createCommandSourceStack();
            commands.performPrefixedCommand(source.withPermission(0), "markets stall");
            helper.assertTrue(citizen.getInventory().countItem(BlockRegistry.CRIMSON_STALL_ITEM.get()) == 0
                            && citizen.getInventory().countItem(BlockRegistry.SAGE_STALL_ITEM.get()) == 0,
                    "An ordinary player obtained administrative stall items");
            commands.performPrefixedCommand(source.withPermission(2), "markets stall");
            helper.assertTrue(citizen.getInventory().countItem(BlockRegistry.CRIMSON_STALL_ITEM.get()) == 1
                            && citizen.getInventory().countItem(BlockRegistry.SAGE_STALL_ITEM.get()) == 1,
                    "The operator command did not provide both actual craftable stall variants");
            commands.performPrefixedCommand(source.withPermission(2), "markets");
            commands.performPrefixedCommand(source.withPermission(2), "markets open");
            rejected(helper, () -> MarketService.requireSession(citizen));
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void nativeNbtRoundTripRetainsOwnershipStockPricesCurrencyAndUnclaimedOfflineEarnings(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 5, 7);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 21);
            f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 3);
            var saved = f.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var restored = MarketData.load(saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored.ledger.stalls().equals(f.data.ledger.stalls())
                            && restored.ledger.find(stall.id()).orElseThrow().earnings() == 21
                            && restored.ledger.find(stall.id()).orElseThrow().listings().getFirst().stock() == 2,
                    "Native NBT reload lost physical ownership, finite goods, per-item prices, or unpaid owner revenue");
            f.data.replace(restored.ledger);
            MarketService.trustedClaim(owner);
            helper.assertTrue(f.current(stall).earnings() == 0 && owner.getInventory().countItem(Items.EMERALD) == 21,
                    "Valid reserved earnings could not be collected after native SavedData deserialization");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void corruptOrUnsupportedNativeNbtFailsClosedWithoutResettingValidSaleAssets(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var owner = f.player();
            f.place(owner);
            f.apples(owner, 5, 7);
            var saved = f.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            List<Consumer<CompoundTag>> corruptions = List.of(
                    tag -> tag.remove("stalls"),
                    tag -> tag.putInt("version", 99),
                    tag -> tag.putString("version", "1"),
                    tag -> { var wrong = new ListTag(); wrong.add(StringTag.valueOf("forged")); tag.put("stalls", wrong); },
                    tag -> tag.getList("stalls", Tag.TAG_COMPOUND).getCompound(0).remove("owner"),
                    tag -> tag.getList("stalls", Tag.TAG_COMPOUND).getCompound(0).putString("currency", "FORGED"),
                    tag -> tag.getList("stalls", Tag.TAG_COMPOUND).getCompound(0).putLong("earnings", -1),
                    tag -> { var records = tag.getList("stalls", Tag.TAG_COMPOUND); records.add(records.getCompound(0).copy()); });
            for (var corruption : corruptions) {
                var malformed = saved.copy();
                corruption.accept(malformed);
                rejected(helper, () -> MarketData.load(malformed, helper.getLevel().registryAccess()));
            }
            helper.assertTrue(MarketData.load(saved, helper.getLevel().registryAccess()).ledger.stalls().equals(f.data.ledger.stalls()),
                    "Rejecting corrupt native records changed the still-valid ownership and finite stock");
        }
        helper.succeed();
    }
    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void fivePercentPaidSaleReservesOfflineSellerNetAndClaimsNeverRetax(GameTestHelper helper) {
        try (var tax = new TaxProvider(server -> 500); var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 2, 20);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 20);
            f.open(buyer);
            helper.assertTrue(owner.server.getPlayerList().getPlayer(owner.getUUID()) == null,
                    "The taxed seller fixture must really be offline in the server PlayerList");
            var view = MarketService.snapshot(buyer);
            helper.assertTrue(((Number) view.get("marketTaxBasisPoints")).intValue() == 500
                            && Boolean.TRUE.equals(view.get("treasuryAvailable")),
                    "The actual buyer snapshot did not expose the trusted current treasury rate");
            MarketService.action(buyer, "buy", buy(0, 1, 20, "minecraft:apple", 500));
            helper.assertTrue(buyer.getInventory().countItem(Items.EMERALD) == 0
                            && buyer.getInventory().countItem(Items.APPLE) == 1
                            && offer(f, stall).stock() == 1 && f.current(stall).earnings() == 19
                            && f.data.ledger.taxBalance(Currency.EMERALD) == 1
                            && owner.getInventory().countItem(Items.EMERALD) == 0,
                    "Paid gross20 must conserve buyer20 = offline seller19 + funded treasury escrow1");
            MarketService.trustedClaim(owner);
            rejected(helper, () -> MarketService.trustedClaim(owner));
            MarketService.trustedWithdraw(owner, 0, 1);
            helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 19
                            && owner.getInventory().countItem(Items.APPLE) == 1
                            && f.current(stall).earnings() == 0 && f.data.ledger.taxBalance(Currency.EMERALD) == 1,
                    "Repeated earnings collection or stock withdrawal taxed existing assets a second time");
            f.data.ledger.remove(stall.id(), owner.getUUID());
            helper.assertTrue(f.data.ledger.taxBalance(Currency.EMERALD) == 1,
                    "Removing an empty stall discarded independently retained paid sales tax");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void taxRoundsDownOnTheWholePurchaseAndOneHundredPercentAllowsZeroSellerNet(GameTestHelper helper) {
        var rate = new java.util.concurrent.atomic.AtomicInteger(500);
        try (var tax = new TaxProvider(server -> rate.get()); var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 4, 10);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 40);
            f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 2);
            helper.assertTrue(f.current(stall).earnings() == 19 && f.data.ledger.taxBalance(Currency.EMERALD) == 1,
                    "Tax must round down once on gross20, rather than rounding each gross10 unit to zero");
            rate.set(10_000);
            MarketService.trustedBuy(buyer, 0, 2);
            helper.assertTrue(buyer.getInventory().countItem(Items.APPLE) == 4
                            && buyer.getInventory().countItem(Items.EMERALD) == 0
                            && f.current(stall).earnings() == 19 && f.data.ledger.taxBalance(Currency.EMERALD) == 21,
                    "A 100% paid sale must deliver items and retain gross20 tax with zero added seller earnings");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void staleMissingFractionalAndForgedRateQuotesCannotChargeTheBuyer(GameTestHelper helper) {
        try (var tax = new TaxProvider(server -> 500); var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 2, 20);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 40);
            f.open(buyer);
            var before = f.current(stall);
            String valid = buy(0, 1, 20, "minecraft:apple", 500);
            for (String invalid : List.of(buy(0, 1, 20, "minecraft:apple", 0),
                    buy(0, 1, 20, "minecraft:apple", -1), buy(0, 1, 20, "minecraft:apple", 10_001),
                    valid.replace(",\"marketTaxBasisPoints\":500", ""),
                    valid.replace("\"marketTaxBasisPoints\":500", "\"marketTaxBasisPoints\":500.5"),
                    valid.replace("\"marketTaxBasisPoints\":500", "\"marketTaxBasisPoints\":\"500\""))) {
                f.open(buyer);
                MarketService.action(buyer, "buy", invalid);
                helper.assertTrue(f.current(stall).equals(before) && buyer.getInventory().countItem(Items.EMERALD) == 40
                                && buyer.getInventory().countItem(Items.APPLE) == 0
                                && f.data.ledger.taxBalance(Currency.EMERALD) == 0,
                        "A stale, missing or noninteger tax quote changed a live buyer wallet or stock");
            }
            f.open(buyer);
            MarketService.action(buyer, "buy", valid);
            helper.assertTrue(f.current(stall).earnings() == 19 && f.data.ledger.taxBalance(Currency.EMERALD) == 1
                            && buyer.getInventory().countItem(Items.EMERALD) == 20,
                    "An explicitly accepted current tax quote could not purchase after rejected stale quotes");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void brokenOrUnregisteredInstalledTreasuryBlocksSalesButNotStockOrEarningsRecovery(GameTestHelper helper) {
        var broken = new java.util.concurrent.atomic.AtomicBoolean(false);
        try (var tax = new TaxProvider(server -> {
            if (broken.get()) throw new IllegalStateException("Native broken-provider fixture");
            return 500;
        }); var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 20);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 40);
            f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 1);
            broken.set(true);
            f.open(buyer);
            var view = MarketService.snapshot(buyer);
            helper.assertTrue(Boolean.FALSE.equals(view.get("treasuryAvailable")),
                    "A broken installed provider was presented to buyers as available or tax-free");
            var before = f.current(stall);
            rejected(helper, () -> MarketService.trustedBuy(buyer, 0, 1));
            helper.assertTrue(f.current(stall).equals(before) && buyer.getInventory().countItem(Items.EMERALD) == 20,
                    "An unavailable provider silently allowed an untaxed sale or charged a failed purchase");
            f.open(owner);
            MarketService.trustedWithdraw(owner, 0, 2);
            MarketService.trustedClaim(owner);
            helper.assertTrue(owner.getInventory().countItem(Items.APPLE) == 2
                            && owner.getInventory().countItem(Items.EMERALD) == 19
                            && f.data.ledger.taxBalance(Currency.EMERALD) == 1,
                    "Treasury unavailability trapped stock/earnings or taxed recovery of old assets");
        }
        try (var noProvider = new TaxProvider(null)) {
            if (ModList.get().isLoaded("kingdomtreasury")) {
                rejected(helper, () -> MarketsApi.salesTaxBasisPoints(helper.getLevel().getServer()));
            } else {
                helper.assertTrue(MarketsApi.salesTaxBasisPoints(helper.getLevel().getServer()) == 0,
                        "Standalone markets without Crown Ledger must retain their original zero-tax behavior");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void privilegedTaxTransfersRequireExactCapabilityThreadCurrencyAmountAndFunding(GameTestHelper helper) {
        try (var tax = new TaxProvider(server -> 500); var f = new Fixture(helper)) {
            var owner = f.player();
            f.place(owner);
            f.apples(owner, 1, 20);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 20);
            f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 1);
            var server = helper.getLevel().getServer();
            MarketsApi.TreasuryProvider impostor = ignored -> 500;
            rejected(helper, () -> MarketsApi.registerTreasury(impostor));
            rejected(helper, () -> MarketsApi.unregisterTreasury(impostor));
            rejected(helper, () -> MarketsApi.treasuryDebit(server, impostor, "EMERALD", 1));
            rejected(helper, () -> MarketsApi.treasuryDebit(server, null, "EMERALD", 1));
            for (long amount : new long[]{-1, 0, 2, MarketLedger.MAX_EARNINGS + 1}) {
                rejected(helper, () -> MarketsApi.treasuryDebit(server, tax.replacement, "EMERALD", amount));
            }
            rejected(helper, () -> MarketsApi.treasuryDebit(server, tax.replacement, "FORGED", 1));
            rejected(helper, () -> MarketsApi.treasuryDebit(server, tax.replacement, "CHRONICLES", 1));
            AtomicReference<Throwable> offThread = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try { MarketsApi.treasuryDebit(server, tax.replacement, "EMERALD", 1); }
                catch (Throwable expected) { offThread.set(expected); }
            }, "native-markets-wrong-thread");
            worker.start();
            try { worker.join(2_000); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
            helper.assertTrue(!worker.isAlive() && offThread.get() instanceof IllegalStateException
                            && f.data.ledger.taxBalance(Currency.EMERALD) == 1,
                    "A spoofed, unsupported, unfunded or off-thread transfer changed funded sales escrow");
            helper.assertTrue(MarketsApi.treasuryDebit(server, tax.replacement, "EMERALD", 1) == 1
                            && f.data.ledger.taxBalance(Currency.EMERALD) == 0,
                    "The exact registered server capability could not transfer its one funded emerald");
            rejected(helper, () -> MarketsApi.treasuryDebit(server, tax.replacement, "EMERALD", 1));
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void nativeTaxEscrowRoundTripAndLegacyMigrationPreserveStockAndExistingOwnerEarnings(GameTestHelper helper) {
        try (var tax = new TaxProvider(server -> 500); var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 3, 20);
            var buyer = f.player();
            give(buyer, Items.EMERALD, 20);
            f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 1);
            var saved = f.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var restored = MarketData.load(saved, helper.getLevel().registryAccess());
            helper.assertTrue(saved.getInt("version") == 2 && restored.ledger.stalls().equals(f.data.ledger.stalls())
                            && restored.ledger.taxBalance(Currency.EMERALD) == 1,
                    "Native version2 serialization lost the distinct paid sales-tax escrow");
            var legacy = saved.copy();
            legacy.putInt("version", 1);
            legacy.remove("taxBalances");
            var migrated = MarketData.load(legacy, helper.getLevel().registryAccess());
            helper.assertTrue(migrated.isDirty() && migrated.ledger.stalls().equals(f.data.ledger.stalls())
                            && migrated.ledger.find(stall.id()).orElseThrow().earnings() == 19
                            && migrated.ledger.find(stall.id()).orElseThrow().listings().getFirst().stock() == 2
                            && migrated.ledger.taxBalance(Currency.EMERALD) == 0,
                    "Migrating a real version1 stall changed its stock/earnings or retroactively created taxes");
            var missing = saved.copy(); missing.remove("taxBalances");
            rejected(helper, () -> MarketData.load(missing, helper.getLevel().registryAccess()));
            var duplicate = saved.copy();
            var balances = duplicate.getList("taxBalances", Tag.TAG_COMPOUND);
            balances.add(balances.getCompound(0).copy());
            rejected(helper, () -> MarketData.load(duplicate, helper.getLevel().registryAccess()));
            var negative = saved.copy();
            negative.getList("taxBalances", Tag.TAG_COMPOUND).getCompound(0).putLong("amount", -1);
            rejected(helper, () -> MarketData.load(negative, helper.getLevel().registryAccess()));
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void overflowingGlobalTaxEscrowRejectsTheSaleBeforeMoneyOrStockMoves(GameTestHelper helper) {
        try (var tax = new TaxProvider(server -> 10_000); var f = new Fixture(helper)) {
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 1, 1);
            f.data.replace(new MarketLedger(f.data.ledger.stalls(), Map.of(Currency.EMERALD, MarketLedger.MAX_EARNINGS)));
            var buyer = f.player();
            give(buyer, Items.EMERALD, 1);
            f.open(buyer);
            var before = f.current(stall);
            rejected(helper, () -> MarketService.trustedBuy(buyer, 0, 1));
            helper.assertTrue(f.current(stall).equals(before) && f.data.ledger.taxBalance(Currency.EMERALD) == MarketLedger.MAX_EARNINGS
                            && buyer.getInventory().countItem(Items.EMERALD) == 1
                            && buyer.getInventory().countItem(Items.APPLE) == 0,
                    "A full tax destination destroyed funded money or delivered unpaid items before rejecting overflow");
        }
        helper.succeed();
    }

    @GameTest(template = "markets_empty", timeoutTicks = 100)
    public static void actualCrownProviderSweepsPaidSalesOnceAndRetainsEscrowWhenItsDestinationOverflows(GameTestHelper helper) {
        if (!ModList.get().isLoaded("kingdomtreasury")) {
            helper.assertTrue(!MarketsApi.treasuryAvailable()
                            && MarketsApi.salesTaxBasisPoints(helper.getLevel().getServer()) == 0,
                    "A standalone market unexpectedly required an absent treasury provider");
            helper.succeed();
            return;
        }
        var server = helper.getLevel().getServer();
        Object crownData = optionalStatic("pt.crownledger.data.TreasuryData", "get", new Class<?>[]{MinecraftServer.class}, server);
        Object previous;
        try { previous = crownData.getClass().getField("treasury").get(crownData); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        try (var f = new Fixture(helper)) {
            Object treasury;
            Object policy;
            Class<?> treasuryType;
            Class<?> policyType;
            Class<?> currencyType;
            Class<?> kindType;
            try {
                treasuryType = Class.forName("pt.crownledger.domain.Treasury");
                policyType = Class.forName("pt.crownledger.domain.Policy");
                currencyType = Class.forName("pt.crownledger.domain.Treasury$Currency");
                kindType = Class.forName("pt.crownledger.domain.Treasury$Kind");
                var taxMode = Class.forName("pt.crownledger.domain.Policy$TaxMode");
                treasury = treasuryType.getConstructor().newInstance();
                policy = policyType.getConstructor(int.class, taxMode, int.class, long.class, long.class,
                                int.class, int.class, currencyType, int.class, int.class, int.class)
                        .newInstance(0, optionalEnum(taxMode, "FIXED"), 700, 3L, 0L, 168, 10,
                                optionalEnum(currencyType, "EMERALD"), 1, 3, 500);
                crownData.getClass().getField("treasury").set(crownData, treasury);
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Actual Crown0.2.0 domain missing", failure); }
            optionalCall(treasury, "setPolicy", new Class<?>[]{policyType, UUID.class, long.class},
                    policy, UUID.randomUUID(), System.currentTimeMillis());
            helper.assertTrue(MarketsApi.treasuryAvailable() && MarketsApi.salesTaxBasisPoints(server) == 500,
                    "The actual registered Crown provider did not preserve market5% independently of FIXED tax/contract7%");
            var owner = f.player();
            var stall = f.place(owner);
            f.apples(owner, 2, 20);
            var buyer = f.player(); give(buyer, Items.EMERALD, 40); f.open(buyer);
            MarketService.trustedBuy(buyer, 0, 1);
            helper.assertTrue(f.current(stall).earnings() == 19 && f.data.ledger.taxBalance(Currency.EMERALD) == 1,
                    "Actual paired Crown/Markets did not split the first paid gross20 sale into19+1");
            optionalStatic("pt.crownledger.service.CrownService", "sweepMarkets", new Class<?>[]{MinecraftServer.class}, server);
            Object emerald = optionalEnum(currencyType, "EMERALD");
            helper.assertTrue((long) optionalCall(treasuryOf(crownData), "balance", new Class<?>[]{currencyType}, emerald) == 1
                            && f.data.ledger.taxBalance(Currency.EMERALD) == 0,
                    "The actual Crown service failed to transfer exactly one funded market tax");
            MarketService.trustedClaim(owner);
            rejected(helper, () -> MarketService.trustedClaim(owner));
            optionalStatic("pt.crownledger.service.CrownService", "sweepMarkets", new Class<?>[]{MinecraftServer.class}, server);
            helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 19
                            && (long) optionalCall(treasuryOf(crownData), "balance", new Class<?>[]{currencyType}, emerald) == 1,
                    "Repeated market collection or actual Crown synchronization taxed or credited the same sale twice");
            MarketService.trustedBuy(buyer, 0, 1);
            Object deposit = optionalEnum(kindType, "DEPOSIT");
            optionalCall(treasuryOf(crownData), "addFunds",
                    new Class<?>[]{currencyType, long.class, kindType, UUID.class, String.class, long.class},
                    emerald, Long.MAX_VALUE - 1, deposit, null, "Native full destination fixture", System.currentTimeMillis());
            rejected(helper, () -> optionalStatic("pt.crownledger.service.CrownService", "sweepMarkets",
                    new Class<?>[]{MinecraftServer.class}, server));
            helper.assertTrue(f.data.ledger.taxBalance(Currency.EMERALD) == 1
                            && (long) optionalCall(treasuryOf(crownData), "balance", new Class<?>[]{currencyType}, emerald) == Long.MAX_VALUE,
                    "Crown destination overflow destroyed the still-funded Markets escrow");
            optionalCall(treasuryOf(crownData), "removeFunds",
                    new Class<?>[]{currencyType, long.class, kindType, UUID.class, String.class, long.class},
                    emerald, Long.MAX_VALUE - 1, deposit, null, "Release native destination fixture", System.currentTimeMillis());
            optionalStatic("pt.crownledger.service.CrownService", "sweepMarkets", new Class<?>[]{MinecraftServer.class}, server);
            optionalStatic("pt.crownledger.service.CrownService", "sweepMarkets", new Class<?>[]{MinecraftServer.class}, server);
            helper.assertTrue(f.data.ledger.taxBalance(Currency.EMERALD) == 0
                            && (long) optionalCall(treasuryOf(crownData), "balance", new Class<?>[]{currencyType}, emerald) == 2
                            && buyer.getInventory().countItem(Items.EMERALD) == 0
                            && buyer.getInventory().countItem(Items.APPLE) == 2
                            && f.current(stall).earnings() == 19,
                    "Restored capacity failed to transfer the retained tax once while conserving two real sales");
            System.out.println("MARKETS_CROWN_TAX_INTEGRATION_PASSED");
        } finally {
            try { crownData.getClass().getField("treasury").set(crownData, previous); ((net.minecraft.world.level.saveddata.SavedData) crownData).setDirty(); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        helper.succeed();
    }

    private static Object treasuryOf(Object data) {
        try { return data.getClass().getField("treasury").get(data); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object optionalEnum(Class<?> type, String name) { return Enum.valueOf((Class) type, name); }

    private static Object optionalStatic(String type, String method, Class<?>[] signature, Object... arguments) {
        try { return Class.forName(type).getMethod(method, signature).invoke(null, arguments); }
        catch (InvocationTargetException failure) { throw optionalFailure(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Actual optional API unavailable", failure); }
    }

    private static Object optionalCall(Object target, String method, Class<?>[] signature, Object... arguments) {
        try { return target.getClass().getMethod(method, signature).invoke(target, arguments); }
        catch (InvocationTargetException failure) { throw optionalFailure(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Actual optional record unavailable", failure); }
    }

    private static RuntimeException optionalFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException(failure);
    }

}
