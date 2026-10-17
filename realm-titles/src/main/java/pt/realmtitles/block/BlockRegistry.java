package pt.realmtitles.block;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The physical register contains no authority or player data. */
public final class BlockRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("realmtitles");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("realmtitles");

    public static final DeferredBlock<SealDeskBlock> SEAL_DESK = BLOCKS.register("royal_seal_desk",
            () -> new SealDeskBlock(BlockBehaviour.Properties.of().strength(2.5F)
                    .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<SealDeskBlock.DeskItem> SEAL_DESK_ITEM = ITEMS.register("royal_seal_desk",
            () -> new SealDeskBlock.DeskItem(SEAL_DESK.get(), new Item.Properties()));

    private BlockRegistry() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
