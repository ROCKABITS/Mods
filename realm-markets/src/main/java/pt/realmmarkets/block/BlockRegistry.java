package pt.realmmarkets.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BlockRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("realmmarkets");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("realmmarkets");
    public static final DeferredBlock<MarketStallBlock> CRIMSON_STALL = BLOCKS.register("crimson_stall",
            () -> new MarketStallBlock(properties()));
    public static final DeferredBlock<MarketStallBlock> SAGE_STALL = BLOCKS.register("sage_stall",
            () -> new MarketStallBlock(properties()));
    public static final DeferredItem<BlockItem> CRIMSON_STALL_ITEM = ITEMS.register("crimson_stall",
            () -> new MarketStallBlock.StallItem(CRIMSON_STALL.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<BlockItem> SAGE_STALL_ITEM = ITEMS.register("sage_stall",
            () -> new MarketStallBlock.StallItem(SAGE_STALL.get(), new net.minecraft.world.item.Item.Properties()));

    private BlockRegistry() {}

    private static BlockBehaviour.Properties properties() {
        // No ignitedByLava; custom block also reports zero flammability/spread.
        return BlockBehaviour.Properties.of().strength(3.0F, 3_600_000F).sound(SoundType.WOOD)
                .noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
}
