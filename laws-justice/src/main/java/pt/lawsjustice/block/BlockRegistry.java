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
import pt.lawsjustice.item.HandcuffsItem;
import pt.lawsjustice.item.WarrantItem;

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
    public static final DeferredBlock<JusticeDeskBlock> JUSTICE_DESK = BLOCKS.register("justice_desk",
            () -> new JusticeDeskBlock(metalProperties().sound(SoundType.WOOD)));

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
    public static final DeferredItem<BlockItem> JUSTICE_DESK_ITEM = ITEMS.register("justice_desk",
            () -> new BlockItem(JUSTICE_DESK.get(), new Item.Properties()));
    public static final DeferredItem<HandcuffsItem> HANDCUFFS = ITEMS.register("handcuffs",
            () -> new HandcuffsItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<WarrantItem> WARRANT = ITEMS.register("warrant",
            () -> new WarrantItem(new Item.Properties().stacksTo(1)));

    public static final DeferredBlock<WorkStationBlock> WORK_BOARD=station("work_board","BOARD");
    public static final DeferredBlock<WorkStationBlock> DELIVERY_BOX=station("delivery_box","DELIVERY");
    public static final DeferredBlock<WorkStationBlock> MINING_POST=station("mining_post","MINE");
    public static final DeferredBlock<WorkStationBlock> AGRICULTURAL_POST=station("agricultural_post","FARM");
    public static final DeferredBlock<WorkStationBlock> WORKSHOP_TABLE=station("workshop_table","WORKSHOP");
    public static final DeferredBlock<WorkStationBlock> MAINTENANCE_POST=station("maintenance_post","YARD");
    public static final DeferredBlock<WorkStationBlock> WAREHOUSE_POST=station("warehouse_post","WAREHOUSE");
    public static final DeferredItem<BlockItem> WORK_BOARD_ITEM=stationItem("work_board",WORK_BOARD);
    public static final DeferredItem<BlockItem> DELIVERY_BOX_ITEM=stationItem("delivery_box",DELIVERY_BOX);
    public static final DeferredItem<BlockItem> MINING_POST_ITEM=stationItem("mining_post",MINING_POST);
    public static final DeferredItem<BlockItem> AGRICULTURAL_POST_ITEM=stationItem("agricultural_post",AGRICULTURAL_POST);
    public static final DeferredItem<BlockItem> WORKSHOP_TABLE_ITEM=stationItem("workshop_table",WORKSHOP_TABLE);
    public static final DeferredItem<BlockItem> MAINTENANCE_POST_ITEM=stationItem("maintenance_post",MAINTENANCE_POST);
    public static final DeferredItem<BlockItem> WAREHOUSE_POST_ITEM=stationItem("warehouse_post",WAREHOUSE_POST);
    public static final DeferredItem<pt.lawsjustice.item.WorkBundleItem> WORK_BUNDLE=ITEMS.register("work_bundle",()->new pt.lawsjustice.item.WorkBundleItem(new Item.Properties().stacksTo(1)));
    private static DeferredBlock<WorkStationBlock> station(String name,String kind){return BLOCKS.register(name,()->new WorkStationBlock(metalProperties().sound(SoundType.WOOD),kind));}
    private static DeferredItem<BlockItem> stationItem(String name,DeferredBlock<WorkStationBlock> block){return ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties()));}

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
