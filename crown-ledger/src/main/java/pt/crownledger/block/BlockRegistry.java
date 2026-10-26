package pt.crownledger.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The physical entrance to the server-owned treasury; this block stores no item inventory. */
public final class BlockRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("kingdomtreasury");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("kingdomtreasury");
    public static final DeferredBlock<CrownChestBlock> CROWN_CHEST = BLOCKS.register("crown_chest",
            () -> new CrownChestBlock(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> CROWN_CHEST_ITEM = ITEMS.registerSimpleBlockItem(CROWN_CHEST);
    public static final DeferredBlock<LoanDeskBlock> LOAN_DESK=BLOCKS.register("loan_desk",()->new LoanDeskBlock(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> LOAN_DESK_ITEM=ITEMS.registerSimpleBlockItem(LOAN_DESK);

    private BlockRegistry() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
