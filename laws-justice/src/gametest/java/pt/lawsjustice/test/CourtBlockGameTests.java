package pt.lawsjustice.test;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.lawsjustice.block.BlockRegistry;

/** The court adds physical furniture and items while blank creative papers remain unauthorized. */
@GameTestHolder("lawsjustice")
@PrefixGameTestTemplate(false)
public final class CourtBlockGameTests {
    private CourtBlockGameTests() {}

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void theJusticeDeskAndHandcuffsHaveRecipesAndAllThreeCourtItemsAppearInCreativeSearch(GameTestHelper h) {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.stream().filter(t -> BuiltInRegistries.CREATIVE_MODE_TAB.getKey(t).getNamespace().equals("lawsjustice"))
                .findFirst().orElseThrow();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, h.getLevel().registryAccess()));
        for (var item : List.of(BlockRegistry.JUSTICE_DESK_ITEM.get(), BlockRegistry.HANDCUFFS.get(), BlockRegistry.WARRANT.get())) {
            h.assertTrue(tab.getDisplayItems().stream().filter(stack -> stack.is(item)).count() == 1
                    && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(item)), "A court item is missing or duplicated in creative/search");
        }
        for (var item : List.of(BlockRegistry.JUSTICE_DESK_ITEM.get(), BlockRegistry.HANDCUFFS.get()))
            h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(BuiltInRegistries.ITEM.getKey(item)).isPresent(), "A physical justice desk or handcuffs item has no survival recipe");
        h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(BuiltInRegistries.ITEM.getKey(BlockRegistry.WARRANT.get())).isEmpty(),
                "A server-issued judicial warrant unexpectedly has a crafting recipe");
        h.succeed();
    }

    @GameTest(template = "justice_empty", timeoutTicks = 100)
    public static void theActualDeskItemPlacesFourFacingsAndBlankCreativeWarrantsAuthorizeNothing(GameTestHelper h) {
        try (var f = new CourtFixture(h)) {
            var pos = f.desk.offset(0, 0, 3); var block = BlockRegistry.JUSTICE_DESK.get();
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                f.complainant.setPos(pos.getX() + 3.5, pos.getY(), pos.getZ() - 3.5); f.complainant.setYRot(facing.getOpposite().toYRot());
                var stack = new ItemStack(block.asItem(), 2); f.complainant.setItemInHand(InteractionHand.MAIN_HAND, stack);
                stack.useOn(new UseOnContext(f.complainant, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false)));
                var state = h.getLevel().getBlockState(pos); h.assertTrue(state.is(block) && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing
                        && stack.getCount() == 1 && state.getPistonPushReaction() == PushReaction.BLOCK, "The real justice desk item did not place with the requested facing and safe piston rule");
            }
            f.complainant.getInventory().clearContent(); f.complainant.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlockRegistry.HANDCUFFS.get()));
            f.complainant.getInventory().add(new ItemStack(BlockRegistry.WARRANT.get()));
            f.complainant.setPos(f.accused.getX() + 1, f.accused.getY(), f.accused.getZ()); f.cuff(f.complainant);
            h.assertTrue(f.court().restraints().isEmpty(), "A blank paper copied from the creative inventory authorized an arrest");
        }
        h.succeed();
    }
}
