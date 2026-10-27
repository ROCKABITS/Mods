package pt.realmeconomy.block;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;
public final class EconomyBlocks {
 private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"realmeconomy");
 private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"realmeconomy");
 private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"realmeconomy");
 public static final java.util.function.Supplier<Block> DESK=BLOCKS.register("economy_desk",()->new EconomyDeskBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
 public static final java.util.function.Supplier<Item> DESK_ITEM=ITEMS.register("economy_desk",()->new BlockItem(DESK.get(),new Item.Properties()));
 public static final java.util.function.Supplier<Block> COLONY_CHEST=BLOCKS.register("colony_coffer",()->new EconomyDeskBlock(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.WOOD).noOcclusion()));
 public static final java.util.function.Supplier<Item> COLONY_ITEM=ITEMS.register("colony_coffer",()->new BlockItem(COLONY_CHEST.get(),new Item.Properties()));
 public static final java.util.function.Supplier<CreativeModeTab> TAB=TABS.register("economy",()->CreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("itemGroup.realmeconomy")).icon(()->new ItemStack(DESK_ITEM.get())).displayItems((p,o)->{o.accept(DESK_ITEM.get());o.accept(COLONY_ITEM.get());}).build());
 public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);}
}
