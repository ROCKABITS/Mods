package pt.realmprogression.block;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class ProgressBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"realmprogression");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"realmprogression");
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"realmprogression");
    public static final java.util.function.Supplier<Block> TABLE=BLOCKS.register("research_table",()->new ResearchTableBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final java.util.function.Supplier<Item> TABLE_ITEM=ITEMS.register("research_table",()->new BlockItem(TABLE.get(),new Item.Properties()));
    public static final java.util.function.Supplier<CreativeModeTab> TAB=TABS.register("research",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.realmprogression")).icon(()->new ItemStack(TABLE_ITEM.get())).displayItems((p,o)->o.accept(TABLE_ITEM.get())).build());
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);}
}
