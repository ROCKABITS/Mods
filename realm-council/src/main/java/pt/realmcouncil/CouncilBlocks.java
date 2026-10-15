package pt.realmcouncil;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
public final class CouncilBlocks {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(RealmCouncil.ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(RealmCouncil.ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,RealmCouncil.ID);
    public static final DeferredBlock<CouncilBlock> BALLOT_BOX=BLOCKS.register("ballot_box",()->new CouncilBlock("urn",BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<CouncilBlock> NOTICE_BOARD=BLOCKS.register("notice_board",()->new CouncilBlock("board",BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<CouncilBlock> OATH_DESK=BLOCKS.register("oath_desk",()->new CouncilBlock("oath",BlockBehaviour.Properties.of().strength(3F).sound(SoundType.STONE).noOcclusion().lightLevel(s->5)));
    static{ITEMS.registerSimpleBlockItem(BALLOT_BOX);ITEMS.registerSimpleBlockItem(NOTICE_BOARD);ITEMS.registerSimpleBlockItem(OATH_DESK);
        TABS.register("council",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.realmcouncil")).icon(()->new ItemStack(BALLOT_BOX.get())).displayItems((params,out)->{out.accept(BALLOT_BOX.get());out.accept(NOTICE_BOARD.get());out.accept(OATH_DESK.get());}).build());}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);}
}
