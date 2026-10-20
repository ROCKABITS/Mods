package pt.realmmarriage.block;

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
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("realmmarriage");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("realmmarriage");

    public static final DeferredBlock<WeddingAltarBlock> ALTAR = BLOCKS.register("wedding_altar",
            () -> new WeddingAltarBlock(BlockBehaviour.Properties.of().strength(2.5F)
                    .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<WeddingAltarBlock.DeskItem> ALTAR_ITEM = ITEMS.register("wedding_altar",
            () -> new WeddingAltarBlock.DeskItem(ALTAR.get(), new Item.Properties()));

    public static final DeferredItem<pt.realmmarriage.item.MarriageItem> ENGAGEMENT_RING=ITEMS.register("engagement_ring",()->new pt.realmmarriage.item.MarriageItem(new Item.Properties().stacksTo(1),false));
    public static final DeferredItem<pt.realmmarriage.item.MarriageItem> WEDDING_RING=ITEMS.register("wedding_ring",()->new pt.realmmarriage.item.MarriageItem(new Item.Properties().stacksTo(1),true));
    public static final DeferredItem<pt.realmmarriage.item.MarriageItem> CERTIFICATE=ITEMS.register("marriage_certificate",()->new pt.realmmarriage.item.MarriageItem(new Item.Properties().stacksTo(1),true));
    private BlockRegistry() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
