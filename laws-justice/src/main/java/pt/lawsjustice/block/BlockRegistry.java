package pt.lawsjustice.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.lawsjustice.item.AreaSelectorItem;

/** Physical furniture and tools; prison authority is always kept on the server. */
public final class BlockRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("lawsjustice");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("lawsjustice");

    public static final DeferredBlock<PrisonDoorBlock> PRISON_DOOR = BLOCKS.register("prison_door",
            () -> new PrisonDoorBlock(metalProperties()));
    public static final DeferredBlock<PrisonBarsBlock> PRISON_BARS = BLOCKS.register("prison_bars",
            () -> new PrisonBarsBlock(metalProperties()));
    public static final DeferredBlock<ReinforcedLockBlock> REINFORCED_LOCK = BLOCKS.register("reinforced_lock",
            () -> new ReinforcedLockBlock(metalProperties()));
    public static final DeferredBlock<PrisonControlBlock> PRISON_CONTROL = BLOCKS.register("prison_control",
            () -> new PrisonControlBlock(metalProperties().sound(SoundType.DEEPSLATE)));

    public static final DeferredItem<DoubleHighBlockItem> PRISON_DOOR_ITEM = ITEMS.register("prison_door",
            () -> new DoubleHighBlockItem(PRISON_DOOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> PRISON_BARS_ITEM = ITEMS.register("prison_bars",
            () -> new BlockItem(PRISON_BARS.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> REINFORCED_LOCK_ITEM = ITEMS.register("reinforced_lock",
            () -> new BlockItem(REINFORCED_LOCK.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> PRISON_CONTROL_ITEM = ITEMS.register("prison_control",
            () -> new BlockItem(PRISON_CONTROL.get(), new Item.Properties()));
    public static final DeferredItem<AreaSelectorItem> AREA_SELECTOR = ITEMS.register("area_selector",
            () -> new AreaSelectorItem(new Item.Properties().stacksTo(1)));

    private BlockRegistry() {}

    private static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of().strength(6F, 3_600_000F).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
