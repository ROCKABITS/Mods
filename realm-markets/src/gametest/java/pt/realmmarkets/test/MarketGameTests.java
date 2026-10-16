package pt.realmmarkets.test;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
            data.replace(new MarketLedger(records));
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
        return "{\"slot\":" + slot + ",\"quantity\":" + quantity + ",\"price\":" + price + ",\"item\":\"" + item + "\"}";
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
            String quote = ",\"price\":2,\"item\":\"minecraft:apple\"}";
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
}
