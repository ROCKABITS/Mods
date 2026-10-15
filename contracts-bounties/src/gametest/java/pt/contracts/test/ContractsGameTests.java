package pt.contracts.test;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.contracts.ContractsData;
import pt.contracts.ContractsService;
import pt.contracts.ContractBoardBlock;
import pt.contracts.compat.ChroniclesBridge;
import pt.contracts.domain.Ledger;
import pt.contracts.network.ContractsPackets;

/** These tests exercise real server inventories and SavedData, without a client. */
@GameTestHolder("contracts")
@PrefixGameTestTemplate(false)
public final class ContractsGameTests {
    private static final String OAK = "minecraft:oak_log";
    // Expected public footprints, independent of the block's placement implementation.
    private record BoardSpec(String id, int width, int height) {}
    private static final List<BoardSpec> BOARDS = List.of(
            new BoardSpec("contract_board", 1, 1),
            new BoardSpec("tall_contract_board", 1, 2),
            new BoardSpec("wide_contract_board", 2, 2));

    private static ContractBoardBlock board(BoardSpec spec) {
        return (ContractBoardBlock) BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("contracts", spec.id()));
    }

    private static List<BlockPos> footprint(BoardSpec spec, BlockPos anchor, Direction facing) {
        var positions = new ArrayList<BlockPos>();
        for (int y = 0; y < spec.height(); y++) {
            for (int x = 0; x < spec.width(); x++) {
                positions.add(anchor.relative(facing.getClockWise(), x).above(y));
            }
        }
        return positions;
    }

    private static BlockPos boardPosition(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(4, 1, 4));
    }

    private static void prepareBoardSpace(GameTestHelper helper, BlockPos anchor) {
        var level = helper.getLevel();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y < 3; y++) {
                    level.setBlockAndUpdate(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(anchor).inflate(2)).forEach(ItemEntity::discard);
    }

    /** Use the real item path, including placement checks, setPlacedBy, and stack consumption. */
    private static boolean placeBoard(GameTestHelper helper, FakePlayer builder, BoardSpec spec,
                                      BlockPos anchor, Direction facing, ItemStack stack) {
        builder.setPos(anchor.getX() + 3.5, anchor.getY(), anchor.getZ() + 3.5);
        builder.setYRot(facing.getOpposite().toYRot());
        builder.setXRot(0);
        builder.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(anchor), Direction.UP, anchor.below(), false);
        return stack.useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND, hit)).consumesAction();
    }

    private static int droppedBoards(GameTestHelper helper, BoardSpec spec, BlockPos anchor) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(anchor).inflate(2)).stream()
                .filter(entity -> entity.getItem().is(board(spec).asItem()))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ContractsQA"));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ContractsData reset(GameTestHelper helper) {
        var data = ContractsData.get(helper.getLevel().getServer());
        data.ledger = new Ledger();
        data.setDirty();
        return data;
    }

    private static Ledger.Currency currency() {
        return ChroniclesBridge.loaded() ? Ledger.Currency.CHRONICLES : Ledger.Currency.EMERALD;
    }

    /** Seed the currency actually selected by the installed runtime. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void funds(FakePlayer player, long amount) {
        if (!ChroniclesBridge.loaded()) {
            player.getInventory().clearContent();
            int remaining = Math.toIntExact(amount);
            for (int slot = 0; remaining > 0; slot++) {
                int count = Math.min(64, remaining);
                player.getInventory().setItem(slot, new ItemStack(Items.EMERALD, count));
                remaining -= count;
            }
            return;
        }
        try {
            var attachment = (AttachmentType) ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments")
                    .getField("PROFILE").get(null)).get();
            var type = Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object profile = type.getConstructor(boolean.class, String.class, int.class, String.class,
                            String.class, String.class, String.class, long.class, long.class, String.class)
                    .newInstance(true, "Artesao QA", 30, "Humano", "Test character", "QA", "Comum",
                            amount, amount, "");
            player.setData(attachment, profile);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to seed the actual Chronicles RP character", exception);
        }
    }

    private static long balance(FakePlayer player) {
        return ChroniclesBridge.loaded() ? ChroniclesBridge.balance(player)
                : player.getInventory().countItem(Items.EMERALD);
    }

    private static void rejected(GameTestHelper helper, Runnable operation) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Invalid operation was accepted");
    }

    private static int pending(Ledger ledger, FakePlayer player) {
        return ledger.deliveries(player.getUUID()).stream().mapToInt(Ledger.Delivery::quantity).sum();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void boardBlockItemAndRecipeAreRegistered(GameTestHelper helper) {
        for (var spec : BOARDS) {
            var key = ResourceLocation.fromNamespaceAndPath("contracts", spec.id());
            helper.assertTrue(BuiltInRegistries.BLOCK.get(key) instanceof ContractBoardBlock,
                    spec.id() + " block is missing");
            helper.assertTrue(BuiltInRegistries.ITEM.get(key) == board(spec).asItem()
                            && board(spec).asItem() != Items.AIR,
                    spec.id() + " block item is missing");
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
                    spec.id() + " recipe is missing");
            helper.assertTrue(board(spec).variant().width() == spec.width()
                            && board(spec).variant().height() == spec.height(),
                    spec.id() + " has an unexpected public footprint");
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void boardIsVisibleInCreativeInventoryAndSearch(GameTestHelper helper) {
        var tabKey = ResourceLocation.fromNamespaceAndPath("contracts", "contracts");
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabKey);
        helper.assertTrue(tab != null, "Contracts creative inventory tab is missing");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));

        for (var spec : BOARDS) {
            var item = board(spec).asItem();
            helper.assertTrue(tab.getDisplayItems().stream().filter(stack -> stack.is(item)).count() == 1,
                    spec.id() + " must appear exactly once in the Contracts creative inventory tab");
            helper.assertTrue(tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(item)),
                    spec.id() + " is absent from creative inventory search");
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void realBlockItemsPlaceEveryFootprintInAllFourDirections(GameTestHelper helper) {
        var builder = player(helper);
        var anchor = boardPosition(helper);
        for (var spec : BOARDS) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                prepareBoardSpace(helper, anchor);
                var stack = new ItemStack(board(spec), 2);
                helper.assertTrue(placeBoard(helper, builder, spec, anchor, facing, stack),
                        spec.id() + " item placement failed facing " + facing);
                helper.assertTrue(stack.getCount() == 1, "Placement must consume exactly one board item");
                for (int y = 0; y < spec.height(); y++) {
                    for (int x = 0; x < spec.width(); x++) {
                        var pos = anchor.relative(facing.getClockWise(), x).above(y);
                        var state = helper.getLevel().getBlockState(pos);
                        helper.assertTrue(state.is(board(spec))
                                        && state.getValue(ContractBoardBlock.FACING) == facing,
                                spec.id() + " has a missing or wrongly oriented section at " + pos);
                        var part = state.getValue(ContractBoardBlock.PART);
                        helper.assertTrue(part.x() == x && part.y() == y,
                                "Board section has the wrong part identity");
                        helper.assertTrue(board(spec).anchor(pos, state).equals(anchor),
                                "A board section resolves to the wrong anchor");
                    }
                }
                helper.assertTrue(board(spec).validStructure(helper.getLevel(), anchor,
                                helper.getLevel().getBlockState(anchor)),
                        "Placement left an incomplete board structure");
                long placed = BlockPos.betweenClosedStream(anchor.offset(-2, 0, -2), anchor.offset(2, 2, 2))
                        .filter(pos -> helper.getLevel().getBlockState(pos).is(board(spec))).count();
                helper.assertTrue(placed == spec.width() * spec.height(),
                        "Placement created extra sections outside the declared footprint");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void blockedPlacementNeverOverwritesBlocksOrConsumesTheItem(GameTestHelper helper) {
        var builder = player(helper);
        var anchor = boardPosition(helper);
        for (var spec : BOARDS) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                for (var obstruction : footprint(spec, anchor, facing)) {
                    prepareBoardSpace(helper, anchor);
                    helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
                    var stack = new ItemStack(board(spec), 2);
                    helper.assertTrue(!placeBoard(helper, builder, spec, anchor, facing, stack),
                            "Placement accepted an obstructed section of " + spec.id());
                    helper.assertTrue(stack.getCount() == 2
                                    && helper.getLevel().getBlockState(obstruction).is(Blocks.STONE),
                            "Rejected placement consumed an item or overwrote the obstruction");
                    for (var pos : footprint(spec, anchor, facing)) {
                        helper.assertTrue(!helper.getLevel().getBlockState(pos).is(board(spec)),
                                "Rejected placement left a partial board");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void wideBoardRequiresSolidGroundUnderBothPosts(GameTestHelper helper) {
        var builder = player(helper);
        var anchor = boardPosition(helper);
        var wide = BOARDS.get(2);
        for (var facing : Direction.Plane.HORIZONTAL) {
            prepareBoardSpace(helper, anchor);
            helper.getLevel().setBlockAndUpdate(anchor.relative(facing.getClockWise()).below(),
                    Blocks.AIR.defaultBlockState());
            var stack = new ItemStack(board(wide), 2);
            helper.assertTrue(!placeBoard(helper, builder, wide, anchor, facing, stack),
                    "A wide board was placed with an unsupported second post");
            helper.assertTrue(stack.getCount() == 2, "Unsupported placement consumed a board item");
            for (var pos : footprint(wide, anchor, facing)) {
                helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(),
                        "Unsupported placement left a partial board");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void tallBoardsCannotPartiallyPlaceThroughTheWorldCeiling(GameTestHelper helper) {
        var builder = player(helper);
        var level = helper.getLevel();
        var local = boardPosition(helper);
        var anchor = new BlockPos(local.getX(), level.getMaxBuildHeight() - 1, local.getZ());
        for (var spec : BOARDS) {
            if (spec.height() == 1) continue;
            for (var facing : Direction.Plane.HORIZONTAL) {
                for (int x = 0; x < spec.width(); x++) {
                    level.setBlockAndUpdate(anchor.relative(facing.getClockWise(), x).below(),
                            Blocks.STONE.defaultBlockState());
                }
                try {
                    var stack = new ItemStack(board(spec), 2);
                    helper.assertTrue(!placeBoard(helper, builder, spec, anchor, facing, stack),
                            "A board was placed beyond the build height");
                    helper.assertTrue(stack.getCount() == 2,
                            "World-ceiling rejection consumed a board item");
                    for (int x = 0; x < spec.width(); x++) {
                        helper.assertTrue(level.getBlockState(anchor.relative(facing.getClockWise(), x)).isAir(),
                                "World-ceiling rejection left a lower board section");
                    }
                } finally {
                    for (int x = 0; x < spec.width(); x++) {
                        level.setBlockAndUpdate(anchor.relative(facing.getClockWise(), x), Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(anchor.relative(facing.getClockWise(), x).below(),
                                Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void breakingAnySectionInSurvivalDropsOneBoardAndLeavesNoOrphans(GameTestHelper helper) {
        checkBreakingEverySection(helper, GameType.SURVIVAL, 1);
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void breakingAnySectionInCreativeDropsNothingAndLeavesNoOrphans(GameTestHelper helper) {
        checkBreakingEverySection(helper, GameType.CREATIVE, 0);
        helper.succeed();
    }

    private static void checkBreakingEverySection(GameTestHelper helper, GameType gameType, int expectedDrops) {
        var builder = player(helper);
        var anchor = boardPosition(helper);
        for (var spec : BOARDS) {
            for (var target : footprint(spec, anchor, Direction.NORTH)) {
                prepareBoardSpace(helper, anchor);
                builder.setGameMode(GameType.SURVIVAL);
                helper.assertTrue(placeBoard(helper, builder, spec, anchor, Direction.NORTH,
                                new ItemStack(board(spec))), "Unable to prepare a real board for destruction");
                builder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                builder.setGameMode(gameType);
                helper.assertTrue(builder.gameMode.destroyBlock(target), "Player could not break board section");
                for (var pos : footprint(spec, anchor, Direction.NORTH)) {
                    helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(),
                            "Breaking " + spec.id() + " left an orphaned board section at " + pos);
                }
                helper.assertTrue(droppedBoards(helper, spec, anchor) == expectedDrops,
                        "Breaking " + spec.id() + " in " + gameType + " produced the wrong item count");
            }
        }
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void everyBoardSectionOpensAnAnchoredServerSession(GameTestHelper helper) {
        reset(helper);
        var builder = player(helper);
        var author = player(helper);
        var anchor = boardPosition(helper);
        for (var spec : BOARDS) {
            for (var clicked : footprint(spec, anchor, Direction.NORTH)) {
                prepareBoardSpace(helper, anchor);
                helper.assertTrue(placeBoard(helper, builder, spec, anchor, Direction.NORTH,
                                new ItemStack(board(spec))), "Unable to prepare a board for interaction");
                ContractsService.logout(author.getUUID());
                var awayFromClickedSection = Vec3.atCenterOf(clicked).subtract(Vec3.atCenterOf(anchor));
                if (awayFromClickedSection.lengthSqr() == 0) awayFromClickedSection = new Vec3(0, 0, 1);
                // At 7.5 blocks from the anchor, upper/right sections are more than eight blocks away.
                // Storing the clicked section instead of the anchor would reject this valid session.
                var position = Vec3.atCenterOf(anchor).subtract(awayFromClickedSection.normalize().scale(7.5));
                author.setPos(position.x, position.y, position.z);
                var state = helper.getLevel().getBlockState(clicked);
                var hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.NORTH, clicked, false);
                helper.assertTrue(state.useWithoutItem(helper.getLevel(), author, hit).consumesAction(),
                        "A board section did not handle interaction");
                ContractsService.requireSession(author);
                helper.getLevel().setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
                rejected(helper, () -> ContractsService.requireSession(author));
                ContractsService.logout(author.getUUID());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void boardOutlinesAndCollisionRotateWithoutBecomingSolidCubes(GameTestHelper helper) {
        var pos = boardPosition(helper);
        for (var spec : BOARDS) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                var state = board(spec).defaultBlockState().setValue(ContractBoardBlock.FACING, facing);
                var outline = state.getShape(helper.getLevel(), pos);
                var collision = state.getCollisionShape(helper.getLevel(), pos);
                helper.assertTrue(!outline.isEmpty() && !collision.isEmpty(),
                        "A board has no clickable outline or physical collision");
                helper.assertTrue(Shapes.joinIsNotEmpty(outline, Shapes.block(), BooleanOp.NOT_SAME)
                                && Shapes.joinIsNotEmpty(collision, Shapes.block(), BooleanOp.NOT_SAME),
                        "A board still uses a solid cube as its outline or collision");
            }
            var north = board(spec).defaultBlockState().setValue(ContractBoardBlock.FACING, Direction.NORTH)
                    .getShape(helper.getLevel(), pos).bounds();
            var east = board(spec).defaultBlockState().setValue(ContractBoardBlock.FACING, Direction.EAST)
                    .getShape(helper.getLevel(), pos).bounds();
            helper.assertTrue(Math.abs(north.getXsize() - east.getZsize()) < 0.00001
                            && Math.abs(north.getZsize() - east.getXsize()) < 0.00001
                            && north.getXsize() > north.getZsize(),
                    "Board outline depth does not rotate with the model facing");
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void publicationReservesExactlyTheAdvertisedReward(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        funds(author, 31);
        var contract = ContractsService.publish(author, "Lenha para a vila", OAK, 64, 20);
        helper.assertTrue(balance(author) == 11, "Publication did not reserve exactly 20 currency units");
        helper.assertTrue(contract.currency() == currency() && contract.status() == Ledger.Status.OPEN,
                "Published contract has the wrong currency or status");
        helper.assertTrue(data.ledger.list().size() == 1
                        && data.ledger.credits(author.getUUID(), currency()) == 0,
                "Publishing created an unexpected refund or duplicate contract");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void fullDeliveryEscrowsMaterialsAndPaysWorkerExactlyOnce(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 20);
        funds(worker, 0);
        var contract = ContractsService.publish(author, "Setenta troncos", OAK, 70, 20);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 64));
        worker.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 9));
        ContractsService.complete(worker, contract.id());
        helper.assertTrue(worker.getInventory().countItem(Items.OAK_LOG) == 3,
                "Delivery did not consume exactly 70 logs across inventory stacks");
        helper.assertTrue(pending(data.ledger, author) == 70
                        && data.ledger.credits(worker.getUUID(), currency()) == 20,
                "Offline-safe material delivery or pending worker reward is missing");
        helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 20 && balance(worker) == 20,
                "Worker did not receive the exact reward");
        helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 0 && balance(worker) == 20,
                "Repeated reward claim duplicated money");
        helper.assertTrue(ContractsService.collect(author) == 70
                        && author.getInventory().countItem(Items.OAK_LOG) == 70
                        && pending(data.ledger, author) == 0,
                "Author could not collect exactly the delivered materials");
        helper.assertTrue(ContractsService.collect(author) == 0, "Repeated collection duplicated materials");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void duplicateCompletionAndCancellationCannotChangeSettledEscrow(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 12);
        var contract = ContractsService.publish(author, "Uma entrega", OAK, 8, 12);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 16));
        ContractsService.complete(worker, contract.id());
        rejected(helper, () -> ContractsService.complete(worker, contract.id()));
        rejected(helper, () -> ContractsService.cancel(author, contract.id()));
        helper.assertTrue(worker.getInventory().countItem(Items.OAK_LOG) == 8,
                "Duplicate completion consumed additional materials");
        helper.assertTrue(pending(data.ledger, author) == 8
                        && data.ledger.credits(worker.getUUID(), currency()) == 12
                        && data.ledger.credits(author.getUUID(), currency()) == 0,
                "Settled escrow changed after duplicate requests");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void authorCannotCompleteOwnContractOrCancelAnotherPlayersContract(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var stranger = player(helper);
        funds(author, 10);
        var contract = ContractsService.publish(author, "Entrega publica", OAK, 4, 10);
        author.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
        rejected(helper, () -> ContractsService.complete(author, contract.id()));
        rejected(helper, () -> ContractsService.cancel(stranger, contract.id()));
        helper.assertTrue(author.getInventory().countItem(Items.OAK_LOG) == 4
                        && data.ledger.list().getFirst().status() == Ledger.Status.OPEN
                        && pending(data.ledger, author) == 0,
                "Unauthorized action consumed materials or changed the contract");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void cancellationReturnsReservedRewardExactlyOnce(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        funds(author, 17);
        var contract = ContractsService.publish(author, "Cancelar encomenda", OAK, 5, 12);
        ContractsService.cancel(author, contract.id());
        rejected(helper, () -> ContractsService.cancel(author, contract.id()));
        helper.assertTrue(balance(author) == 5 && data.ledger.credits(author.getUUID(), currency()) == 12,
                "Cancellation must place exactly the reserved amount in the author's claims");
        helper.assertTrue(ContractsService.claimMoney(author, currency()) == 12 && balance(author) == 17,
                "Cancellation refund changed the total balance");
        helper.assertTrue(ContractsService.claimMoney(author, currency()) == 0 && balance(author) == 17,
                "Duplicate refund claim duplicated money");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void rewardClaimsRespectCapacityAndPreserveEveryUnclaimedUnit(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 20);
        funds(worker, 0);
        var contract = ContractsService.publish(author, "Pagamento sem perdas", OAK, 1, 20);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 1));
        ContractsService.complete(worker, contract.id());
        if (ChroniclesBridge.loaded()) {
            funds(worker, ChroniclesBridge.MAX_CENTS);
            helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 0
                            && data.ledger.credits(worker.getUUID(), currency()) == 20,
                    "A full Chronicles wallet lost its pending reward");
            funds(worker, ChroniclesBridge.MAX_CENTS - 5);
            helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 5
                            && balance(worker) == ChroniclesBridge.MAX_CENTS
                            && data.ledger.credits(worker.getUUID(), currency()) == 15,
                    "Partial Chronicles payment lost its remainder or exceeded the wallet limit");
            funds(worker, 0);
        } else {
            for (int slot = 0; slot < 36; slot++) worker.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 0
                            && data.ledger.credits(worker.getUUID(), currency()) == 20,
                    "Full inventory lost its pending emerald reward");
            worker.getInventory().setItem(0, new ItemStack(Items.EMERALD, 59));
            helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 5
                            && worker.getInventory().getItem(0).getCount() == 64
                            && data.ledger.credits(worker.getUUID(), currency()) == 15,
                    "Partial emerald payment lost its remainder or overfilled its stack");
            worker.getInventory().setItem(1, ItemStack.EMPTY);
        }
        helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 15
                        && data.ledger.credits(worker.getUUID(), currency()) == 0,
                "The final reward remainder could not be claimed");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void missingFundsAndMaterialsLeaveBalancesAndInventoriesUntouched(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 9);
        rejected(helper, () -> ContractsService.publish(author, "Sem saldo", OAK, 4, 10));
        helper.assertTrue(balance(author) == 9 && data.ledger.list().isEmpty(),
                "Rejected publication changed funds or inserted a contract");
        var contract = ContractsService.publish(author, "Sem materiais", OAK, 4, 9);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));
        worker.getInventory().setItem(1, new ItemStack(Items.BIRCH_LOG, 8));
        rejected(helper, () -> ContractsService.complete(worker, contract.id()));
        helper.assertTrue(worker.getInventory().countItem(Items.OAK_LOG) == 3
                        && worker.getInventory().countItem(Items.BIRCH_LOG) == 8
                        && data.ledger.list().getFirst().status() == Ledger.Status.OPEN
                        && data.ledger.credits(worker.getUUID(), currency()) == 0,
                "Rejected delivery consumed partial materials or paid a reward");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void fullInventoryCollectionsKeepAllUndeliveredRemainders(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 1);
        var contract = ContractsService.publish(author, "Entrega volumosa", OAK, 70, 1);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 64));
        worker.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 6));
        ContractsService.complete(worker, contract.id());
        for (int slot = 0; slot < 36; slot++) author.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        helper.assertTrue(ContractsService.collect(author) == 0 && pending(data.ledger, author) == 70,
                "Full inventory lost pending materials");
        author.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 63));
        helper.assertTrue(ContractsService.collect(author) == 1 && pending(data.ledger, author) == 69
                        && author.getInventory().getItem(0).getCount() == 64,
                "Partially available stack did not preserve its remainder");
        author.getInventory().setItem(1, ItemStack.EMPTY);
        helper.assertTrue(ContractsService.collect(author) == 64 && pending(data.ledger, author) == 5,
                "Free inventory slot did not receive exactly one stack");
        author.getInventory().setItem(2, ItemStack.EMPTY);
        helper.assertTrue(ContractsService.collect(author) == 5 && pending(data.ledger, author) == 0,
                "Final collection lost its remainder");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void namedAndCustomComponentMaterialsAreNeverConsumed(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 5);
        var contract = ContractsService.publish(author, "Apenas troncos comuns", OAK, 4, 5);
        var named = new ItemStack(Items.OAK_LOG, 32);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Relicario de familia"));
        var tagged = new ItemStack(Items.OAK_LOG, 32);
        var tag = new CompoundTag();
        tag.putBoolean("roleplay_relic", true);
        tagged.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        worker.getInventory().setItem(0, named);
        worker.getInventory().setItem(1, tagged);
        worker.getInventory().setItem(2, new ItemStack(Items.OAK_LOG, 3));
        rejected(helper, () -> ContractsService.complete(worker, contract.id()));
        helper.assertTrue(named.getCount() == 32 && tagged.getCount() == 32
                        && worker.getInventory().getItem(2).getCount() == 3,
                "Failed delivery consumed protected custom items or partial materials");
        worker.getInventory().setItem(3, new ItemStack(Items.OAK_LOG, 1));
        ContractsService.complete(worker, contract.id());
        helper.assertTrue(worker.getInventory().getItem(0).getCount() == 32
                        && worker.getInventory().getItem(1).getCount() == 32
                        && worker.getInventory().getItem(0).has(DataComponents.CUSTOM_NAME)
                        && worker.getInventory().getItem(1).has(DataComponents.CUSTOM_DATA)
                        && worker.getInventory().getItem(2).isEmpty()
                        && worker.getInventory().getItem(3).isEmpty()
                        && pending(data.ledger, author) == 4,
                "Delivery did not exclusively consume default-component material stacks");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void offhandMaterialsCannotFulfilMainInventoryDelivery(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 2);
        var contract = ContractsService.publish(author, "So inventario principal", OAK, 4, 2);
        worker.getInventory().setItem(40, new ItemStack(Items.OAK_LOG, 4));
        rejected(helper, () -> ContractsService.complete(worker, contract.id()));
        helper.assertTrue(worker.getOffhandItem().getCount() == 4
                        && data.ledger.list().getFirst().status() == Ledger.Status.OPEN,
                "Offhand materials were consumed");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void currencyPrerequisitesCannotBeBypassedWithProtectedEmeralds(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        if (ChroniclesBridge.loaded()) {
            // Installing Chronicles makes a created character mandatory; no silent currency fallback.
            author.getInventory().setItem(0, new ItemStack(Items.EMERALD, 10));
            rejected(helper, () -> ContractsService.publish(author, "Personagem em falta", OAK, 1, 5));
            helper.assertTrue(author.getInventory().getItem(0).getCount() == 10 && data.ledger.list().isEmpty(),
                    "An uncreated Chronicles character fell back to spending emeralds");
        } else {
            var named = new ItemStack(Items.EMERALD, 10);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Tesouro ancestral"));
            author.getInventory().setItem(0, named);
            author.getInventory().setItem(40, new ItemStack(Items.EMERALD, 10));
            rejected(helper, () -> ContractsService.publish(author, "Esmeraldas protegidas", OAK, 1, 5));
            helper.assertTrue(named.getCount() == 10 && author.getOffhandItem().getCount() == 10
                            && data.ledger.list().isEmpty(),
                    "Protected named or offhand emeralds funded a contract");
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void packetsNeedAServerOpenedSessionAndCannotChooseTheirCurrency(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        funds(author, 20);
        ContractsService.logout(author.getUUID());
        String forgedCurrency = currency() == Ledger.Currency.EMERALD ? "CHRONICLES" : "EMERALD";
        var packet = new ContractsPackets.Action("publish", "", "Contrato do menu", OAK, 4, 7, forgedCurrency);
        ContractsService.action(author, packet);
        helper.assertTrue(data.ledger.list().isEmpty() && balance(author) == 20,
                "A packet without a server-opened session spent money or created a contract");
        ContractsService.open(author, null);
        ContractsService.action(author, packet);
        helper.assertTrue(data.ledger.list().isEmpty() && balance(author) == 20,
                "A forged packet currency created a contract or changed its wallet");
        // Reopening also starts a fresh server-side action throttle window.
        ContractsService.open(author, null);
        ContractsService.action(author, new ContractsPackets.Action("publish", "", "Contrato do menu",
                OAK, 4, 7, currency().name()));
        helper.assertTrue(data.ledger.list().size() == 1
                        && data.ledger.list().getFirst().currency() == currency() && balance(author) == 13,
                "Client packet overrode server-selected currency or failed valid publication");
        ContractsService.logout(author.getUUID());
        rejected(helper, () -> ContractsService.requireSession(author));
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void physicalBoardSessionsRejectDistanceAndChangedBlocks(GameTestHelper helper) {
        reset(helper);
        var author = player(helper);
        var pos = helper.absolutePos(new BlockPos(3, 1, 3));
        var board = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("contracts", "contract_board"));
        helper.getLevel().setBlockAndUpdate(pos, board.defaultBlockState());
        author.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 1.5);
        ContractsService.open(author, pos);
        ContractsService.requireSession(author);
        author.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
        rejected(helper, () -> ContractsService.requireSession(author));
        author.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 1.5);
        ContractsService.open(author, pos);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        rejected(helper, () -> ContractsService.requireSession(author));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        rejected(helper, () -> ContractsService.requireSession(author));
        ContractsService.logout(author.getUUID());
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void savedDataRoundtripRetainsContractsCreditsAndDeliveries(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 31);
        var completed = ContractsService.publish(author, "Entrega persistida", OAK, 7, 20);
        var cancelled = ContractsService.publish(author, "Reembolso persistido", "minecraft:birch_log", 3, 11);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 7));
        ContractsService.complete(worker, completed.id());
        ContractsService.cancel(author, cancelled.id());
        var tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = ContractsData.load(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.ledger.list().equals(data.ledger.list()),
                "Contract identities or terminal states changed during SavedData serialization");
        helper.assertTrue(restored.ledger.credits(worker.getUUID(), currency()) == 20
                        && restored.ledger.credits(author.getUUID(), currency()) == 11
                        && pending(restored.ledger, author) == 7,
                "SavedData lost pending rewards, cancellation refunds, or materials");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void corruptOrUnsupportedSavedDataIsRejectedInsteadOfBecomingEmpty(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        funds(author, 9);
        var contract = ContractsService.publish(author, "Reserva persistida", OAK, 3, 9);
        ContractsService.cancel(author, contract.id());
        var valid = data.save(new CompoundTag(), helper.getLevel().registryAccess());

        var unsupported = valid.copy();
        unsupported.putInt("version", 99);
        rejected(helper, () -> ContractsData.load(unsupported, helper.getLevel().registryAccess()));
        var missingList = valid.copy();
        missingList.remove("deliveries");
        rejected(helper, () -> ContractsData.load(missingList, helper.getLevel().registryAccess()));
        var wrongListType = valid.copy();
        wrongListType.putString("credits", "not a ledger list");
        rejected(helper, () -> ContractsData.load(wrongListType, helper.getLevel().registryAccess()));
        var invalidCredit = valid.copy();
        invalidCredit.getList("credits", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putLong("amount", 0);
        rejected(helper, () -> ContractsData.load(invalidCredit, helper.getLevel().registryAccess()));
        var duplicateContract = valid.copy();
        var entries = duplicateContract.getList("contracts", net.minecraft.nbt.Tag.TAG_COMPOUND);
        entries.add(entries.getCompound(0).copy());
        rejected(helper, () -> ContractsData.load(duplicateContract, helper.getLevel().registryAccess()));

        helper.assertTrue(data.ledger.list().size() == 1
                        && data.ledger.credits(author.getUUID(), currency()) == 9,
                "Rejected SavedData replaced or modified the live ledger");
        helper.succeed();
    }
}
