package pt.realmidentity.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.realmidentity.item.IdentityDocumentItem;

/** Physical roleplay objects; authority remains in the server's identity records. */
public final class BlockRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("realmidentity");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("realmidentity");

    public static final DeferredBlock<WritingDeskBlock> WRITING_DESK = BLOCKS.register("writing_desk",
            () -> new WritingDeskBlock(BlockBehaviour.Properties.of().strength(2.5F)
                    .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> WRITING_DESK_ITEM = ITEMS.registerSimpleBlockItem(WRITING_DESK);
    public static final DeferredItem<IdentityDocumentItem> IDENTITY_DOCUMENT = ITEMS.register("identity_document",
            () -> new IdentityDocumentItem(new Item.Properties().stacksTo(1)));

    private BlockRegistry() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
