package pt.lawsjustice.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.service.JusticeService;

/** Native registration, actual BlockItem placement, both physical door halves and server restrictions. */
@GameTestHolder("lawsjustice")
@PrefixGameTestTemplate(false)
public final class JusticeBlockGameTests {
    private JusticeBlockGameTests() {}
    static List<Block> blocks() {
        return List.of(BlockRegistry.PRISON_DOOR.get(), BlockRegistry.PRISON_BARS.get(), BlockRegistry.REINFORCED_LOCK.get(), BlockRegistry.PRISON_CONTROL.get());
    }
    private static BlockPos base(GameTestHelper h) { return h.absolutePos(new BlockPos(5, 2, 5)); }
    private static void place(GameTestHelper h, JusticeActors.Actor actor, BlockPos pos, Block block, Direction facing) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); h.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        actor.setPos(pos.getX() + 3.5, pos.getY(), pos.getZ() - 3.5); actor.setYRot((block instanceof DoorBlock?facing:facing.getOpposite()).toYRot());
        var stack = new ItemStack(block.asItem(), 2); actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
        h.assertTrue(stack.useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit)).consumesAction()
                && stack.getCount() == 1 && h.getLevel().getBlockState(pos).is(block), "Actual block item placement did not consume exactly one item: " + block);
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void fourPhysicalBlocksAndSurveyingToolHaveRecipesAndCreativeSearchEntries(GameTestHelper h) {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.stream().filter(t -> BuiltInRegistries.CREATIVE_MODE_TAB.getKey(t).getNamespace().equals("lawsjustice"))
                .findFirst().orElseThrow(() -> new AssertionError("The justice creative tab is missing"));
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, h.getLevel().registryAccess()));
        var items = new java.util.ArrayList<net.minecraft.world.item.Item>(); blocks().forEach(b -> items.add(b.asItem())); items.add(BlockRegistry.AREA_SELECTOR.get());
        for (var item : items) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            h.assertTrue(id.getNamespace().equals("lawsjustice") && h.getLevel().getServer().getRecipeManager().byKey(id).isPresent(), "A justice item has no registered survival recipe: " + id);
            h.assertTrue(tab.getDisplayItems().stream().filter(stack -> stack.is(item)).count() == 1
                    && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(item)), "A justice item is missing or duplicated in creative/search: " + id);
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void allCustomBlocksRejectPistonMovementAndHaveBoundedCollision(GameTestHelper h) {
        var pos = base(h);
        for (var block : blocks()) {
            var state = block.defaultBlockState(); var shape = state.getCollisionShape(h.getLevel(), pos);
            h.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, "A piston can move a configured justice block");
            var b = shape.bounds(); h.assertTrue(!shape.isEmpty() && b.minX >= 0 && b.minY >= 0 && b.minZ >= 0 && b.maxX <= 1 && b.maxY <= 1 && b.maxZ <= 1,
                    "A justice block has empty collision or collision outside its physical block");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void actualBlockItemsPlaceAllFourHorizontalOrientations(GameTestHelper h) {
        try (var actors = new JusticeActors(h)) {
            var actor = actors.actor("JusticeBuilderQA", false); var pos = base(h);
            for (var block : blocks()) for (Direction direction : Direction.Plane.HORIZONTAL) {
                place(h, actor, pos, block, direction);
                h.assertTrue(h.getLevel().getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_FACING) == direction,
                        "A justice block item ignored the player's physical facing");
                if (block instanceof DoorBlock) h.assertTrue(h.getLevel().getBlockState(pos.above()).is(block)
                        && h.getLevel().getBlockState(pos.above()).getValue(DoorBlock.FACING) == direction,
                        "A door upper half did not follow its lower-half orientation");
            }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void prisonDoorPlacesBothHalvesAndBreakingEitherRemovesItsPartner(GameTestHelper h) {
        try (var actors = new JusticeActors(h)) {
            var actor = actors.actor("DoorBuilderQA", false); var block = BlockRegistry.PRISON_DOOR.get(); var pos = base(h);
            for (boolean upper : List.of(false, true)) {
                place(h, actor, pos, block, Direction.NORTH);
                h.assertTrue(h.getLevel().getBlockState(pos).getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        && h.getLevel().getBlockState(pos.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "A prison door has malformed physical halves");
                actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                h.assertTrue(actor.gameMode.destroyBlock(upper ? pos.above() : pos), "An unconvicted builder could not dismantle a prison door");
                h.assertTrue(!h.getLevel().getBlockState(pos).is(block) && !h.getLevel().getBlockState(pos.above()).is(block), "Destroying a prison door left an orphan half");
                h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
            }
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aPrisonerCannotOpenEitherPhysicalDoorHalfButAdministratorCan(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var pos = f.pos.offset(1, 0, 0); var door = BlockRegistry.PRISON_DOOR.get();
            place(h, f.admin, pos, door, Direction.NORTH);
            for (var half : List.of(pos, pos.above())) {
                h.assertTrue(!JusticeService.canUseDoor(f.prisoner, half), "A prisoner was authorized to open a physical cell door");
                h.getLevel().getBlockState(half).useWithoutItem(h.getLevel(), f.prisoner, new BlockHitResult(Vec3.atCenterOf(half), Direction.NORTH, half, false));
                h.assertTrue(!h.getLevel().getBlockState(pos).getValue(DoorBlock.OPEN), "A prisoner actually opened a locked physical door half");
            }
            h.assertTrue(JusticeService.canUseDoor(f.admin, pos), "The actual administrator cannot operate a prison door");
            h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(), f.admin, new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            h.assertTrue(h.getLevel().getBlockState(pos).getValue(DoorBlock.OPEN), "Authorized hand interaction did not open the real prison door");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aConvictedPlayerCannotBreakBlocksThroughTheActualNativeBreakEvent(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var pos = f.pos.offset(1, 0, 0); h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            f.prisoner.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5); f.prisoner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            h.assertTrue(!JusticeService.canBreak(f.prisoner, pos) && !f.prisoner.gameMode.destroyBlock(pos)
                    && h.getLevel().getBlockState(pos).is(Blocks.STONE), "Actual native block destruction bypassed custody restrictions");
            h.assertTrue(JusticeService.canBreak(f.admin, pos) && JusticeService.canBreak(f.visitor, pos), "Custody restrictions incorrectly forbid unconvicted builders");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void aPrisonerCannotPlaceBlocksOrUseBucketsAndUnconvictedBuildersRemainFree(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var pos = f.pos.offset(1, 0, 0);
            h.assertTrue(!JusticeService.canPlace(f.prisoner, pos) && !JusticeService.canInteract(f.prisoner, pos), "A convicted player may place blocks or use a bucket/interactable prison escape path");
            h.assertTrue(JusticeService.canPlace(f.visitor, pos) && JusticeService.canInteract(f.visitor, pos), "A free builder was affected by another player's sentence");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void theActualSurveyingItemSelectsAServerSidePointForAdministrators(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.open(f.admin); f.admin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlockRegistry.AREA_SELECTOR.get()));
            var hit = new BlockHitResult(Vec3.atCenterOf(f.pos.below()), Direction.UP, f.pos.below(), false);
            f.admin.getMainHandItem().useOn(new UseOnContext(f.admin, InteractionHand.MAIN_HAND, hit));
            var selection = f.view(f.admin).getAsJsonObject("selection");
            h.assertTrue(selection != null && selection.has("a"), "The physical surveying item did not create a server-side first selection point");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void actualNativePlacementEventRollsBackAConvictedPlayersBlockAndItem(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200); var pos = f.pos.offset(2, 0, 0);
            h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            f.prisoner.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5); var stack = new ItemStack(Items.COBBLESTONE, 2);
            f.prisoner.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
            stack.useOn(new UseOnContext(f.prisoner, InteractionHand.MAIN_HAND, hit));
            h.assertTrue(h.getLevel().getBlockState(pos).isAir() && stack.getCount() == 2,
                    "The actual native placement hook failed to roll back the prisoner's placed block and restore its consumed item");
        }
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void actualRightClickItemEventBlocksBucketEscapeForAConvictedPlayer(GameTestHelper h) {
        try (var f = new JusticeFixture(h)) {
            f.convict(1200);
            for (var item : List.of(Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.POWDER_SNOW_BUCKET)) {
                f.prisoner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                var event = new PlayerInteractEvent.RightClickItem(f.prisoner, InteractionHand.MAIN_HAND); NeoForge.EVENT_BUS.post(event);
                h.assertTrue(event.isCanceled(), "Actual server right-click-item event permitted a prisoner's bucket action: " + item);
            }
        }
        h.succeed();
    }
}
