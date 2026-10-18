package pt.realmcasino.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmcasino.block.BlockRegistry;
import pt.realmcasino.block.CasinoMachineBlock;
import pt.realmcasino.service.CasinoService;

/** Real BlockItem placement, survival/creative destruction, collision, protected support and native recipes. */
@GameTestHolder("realmcasino")
@PrefixGameTestTemplate(false)
public final class CasinoBlockGameTests {
    private CasinoBlockGameTests() {}
    static List<CasinoMachineBlock> machines() {
        return List.of(BlockRegistry.SLOT_MACHINE.get(), BlockRegistry.ROULETTE_TABLE.get(), BlockRegistry.BLACKJACK_TABLE.get());
    }
    static BlockPos base(GameTestHelper helper) { return helper.absolutePos(new BlockPos(4, 1, 4)); }
    static void place(GameTestHelper helper, CasinoActors.Actor owner, BlockPos pos, CasinoMachineBlock block, Direction facing) {
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        owner.setPos(pos.getX() + 3.5, pos.getY(), pos.getZ() - 3.5);
        owner.setYRot(facing.getOpposite().toYRot());
        ItemStack stack = new ItemStack(block.asItem(), 2);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
        helper.assertTrue(stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit)).consumesAction() && stack.getCount() == 1,
                "A real casino BlockItem failed to place exactly one machine: " + block.game());
        helper.assertTrue(block.validStructure(helper.getLevel(), pos), "A newly placed physical machine is incomplete");
    }
    private static int drops(GameTestHelper helper, BlockPos pos, CasinoMachineBlock block) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                item -> item.getItem().is(block.asItem())).stream().mapToInt(item -> item.getItem().getCount()).sum();
    }
    private static void clearDrops(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void allThreePhysicalMachinesHaveRecipesCreativeSearchAndBlockedPistons(GameTestHelper helper) {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath("realmcasino", "realm_casino"));
        helper.assertTrue(tab != null, "The casino creative tab is missing");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));
        for (CasinoMachineBlock block : machines()) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "A physical machine has no corresponding registered item");
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(id).isPresent(), "A machine has no survival crafting recipe: " + id);
            helper.assertTrue(tab.getDisplayItems().stream().filter(stack -> stack.is(block.asItem())).count() == 1
                    && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(block.asItem())), "Machine is absent or duplicated in creative/search: " + id);
            helper.assertTrue(block.defaultBlockState().getPistonPushReaction() == PushReaction.BLOCK, "A piston can move casino furniture with a bound bank");
        }
        helper.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void allMachineItemsPlaceFourFacingsWithBoundedCollisionAndCorrectMaster(GameTestHelper helper) {
        try (var actors = new CasinoActors(helper)) {
            BlockPos pos = base(helper);
            for (CasinoMachineBlock block : machines()) for (Direction direction : Direction.Plane.HORIZONTAL) {
                var owner = actors.actor("BlockFacingQA", 100_000);
                place(helper, owner, pos, block, direction);
                int parts = block.isTall() ? 2 : 1;
                for (int half = 0; half < parts; half++) {
                    var state = helper.getLevel().getBlockState(pos.above(half));
                    helper.assertTrue(state.getValue(CasinoMachineBlock.FACING) == direction, "Machine facing did not follow the actual item placement");
                    var shape = state.getCollisionShape(helper.getLevel(), pos.above(half)); var bounds = shape.bounds();
                    helper.assertTrue(!shape.isEmpty() && bounds.minX >= 0 && bounds.minY >= 0 && bounds.minZ >= 0
                            && bounds.maxX <= 1 && bounds.maxY <= 1 && bounds.maxZ <= 1, "Machine collision is empty or extends outside its physical footprint");
                    helper.assertTrue(CasinoMachineBlock.master(helper.getLevel(), pos.above(half)).equals(pos), "A physical machine part resolved to another station");
                }
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
                helper.assertTrue(owner.gameMode.destroyBlock(pos), "An owner could not safely dismantle an empty casino machine");
                clearDrops(helper, pos);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void breakingEitherEmptySlotHalfInSurvivalDropsExactlyOneItem(GameTestHelper helper) {
        try (var actors = new CasinoActors(helper)) {
            BlockPos pos = base(helper); var block = BlockRegistry.SLOT_MACHINE.get();
            for (boolean upper : List.of(false, true)) {
                var owner = actors.actor("SlotBreakerQA", 100_000); place(helper, owner, pos, block, Direction.NORTH);
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
                helper.assertTrue(owner.gameMode.destroyBlock(upper ? pos.above() : pos), "The owner could not dismantle an empty slot machine");
                helper.assertTrue(!helper.getLevel().getBlockState(pos).is(block) && !helper.getLevel().getBlockState(pos.above()).is(block), "Dismantling a slot half left an orphan");
                helper.assertTrue(drops(helper, pos, block) == 1, "A survival slot dismantling duplicated or lost the single machine item");
                clearDrops(helper, pos);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void ownerCreativeDestructionNeverCreatesMachineItems(GameTestHelper helper) {
        try (var actors = new CasinoActors(helper)) {
            BlockPos pos = base(helper);
            for (CasinoMachineBlock block : machines()) {
                var owner = actors.actor("CreativeCasinoQA", 100_000); place(helper, owner, pos, block, Direction.WEST);
                owner.setGameMode(GameType.CREATIVE);
                helper.assertTrue(owner.gameMode.destroyBlock(block.isTall() ? pos.above() : pos), "A creative owner could not dismantle an empty machine");
                helper.assertTrue(!helper.getLevel().getBlockState(pos).is(block) && (!block.isTall() || !helper.getLevel().getBlockState(pos.above()).is(block)), "Creative destruction left a machine part");
                helper.assertTrue(drops(helper, pos, block) == 0, "Creative destruction duplicated physical casino items");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void outsidersCannotBreakEvenAnEmptyPlayerOwnedMachine(GameTestHelper helper) {
        try (var actors = new CasinoActors(helper)) {
            BlockPos pos = base(helper); var owner = actors.actor("MachineOwnerQA", 100_000); var outsider = actors.actor("OutsiderQA", 100_000);
            for (CasinoMachineBlock block : machines()) {
                place(helper, owner, pos, block, Direction.SOUTH);
                outsider.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2.5);
                outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
                helper.assertTrue(!CasinoService.canBreak(outsider, pos), "An outsider was authorized to break another player's machine");
                helper.assertTrue(!outsider.gameMode.destroyBlock(pos) && block.validStructure(helper.getLevel(), pos), "An outsider actually destroyed the protected machine");
                owner.gameMode.destroyBlock(pos); clearDrops(helper, pos);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void machinesAreSelfStandingAndRemainWholeAfterTheirFloorIsRemoved(GameTestHelper helper) {
        try (var actors = new CasinoActors(helper)) {
            BlockPos pos = base(helper); var owner = actors.actor("SupportOwnerQA", 100_000);
            for (CasinoMachineBlock block : machines()) {
                place(helper, owner, pos, block, Direction.EAST);
                helper.getLevel().destroyBlock(pos.below(), false);
                helper.assertTrue(block.validStructure(helper.getLevel(), pos), "Removing support silently erased a player bank or left an invalid part");
                helper.assertTrue(drops(helper, pos, block) == 0, "Support loss created an unbacked casino item");
                owner.gameMode.destroyBlock(pos); clearDrops(helper, pos);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void actualExplosionCannotErasePlayerOwnedCasinoFurniture(GameTestHelper helper) {
        try (var actors = new CasinoActors(helper)) {
            BlockPos pos = base(helper); var owner = actors.actor("ExplosionOwnerQA", 100_000);
            for (CasinoMachineBlock block : machines()) {
                place(helper, owner, pos, block, Direction.NORTH);
                helper.getLevel().explode(null, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, 4.0F, Level.ExplosionInteraction.BLOCK);
                helper.assertTrue(block.validStructure(helper.getLevel(), pos), "An actual explosion removed a machine or a slot-machine half");
                helper.assertTrue(drops(helper, pos, block) == 0, "An explosion cloned casino furniture items");
                owner.gameMode.destroyBlock(pos); clearDrops(helper, pos);
            }
        }
        helper.succeed();
    }
}
