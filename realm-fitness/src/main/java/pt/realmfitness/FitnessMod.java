package pt.realmfitness;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import pt.realmfitness.domain.Exercise;

@Mod(FitnessMod.ID)
public final class FitnessMod {
    public static final String ID="realmfitness";
    public static final org.slf4j.Logger LOG=LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,ID);
    public static final DeferredHolder<EntityType<?>,EntityType<GymMount>> MOUNT=ENTITY_TYPES.register("gym_mount",()->EntityType.Builder.<GymMount>of(GymMount::new,MobCategory.MISC).sized(.05f,.05f).clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("realmfitness:gym_mount"));
    public static final Map<Exercise,DeferredBlock<GymBlock>> MACHINES=new EnumMap<>(Exercise.class);
    static{for(var e:Exercise.values()){var b=BLOCKS.register(e.id(),()->new GymBlock(e,properties()));MACHINES.put(e,b);ITEMS.registerSimpleBlockItem(b);}}
    public static final DeferredBlock<RecordBoardBlock> BOARD=BLOCKS.register("record_board",()->new RecordBoardBlock(properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<RecordBoardEntity>> BOARD_ENTITY=ENTITIES.register("record_board",()->BlockEntityType.Builder.of(RecordBoardEntity::new,BOARD.get()).build(null));
    public static final DeferredItem<Item> CARD=ITEMS.registerSimpleItem("athlete_card",new Item.Properties().stacksTo(1));
    static{ITEMS.registerSimpleBlockItem(BOARD);TABS.register("fitness",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.realmfitness")).icon(()->new ItemStack(MACHINES.get(Exercise.BENCH).get())).displayItems((p,out)->{for(var b:MACHINES.values())out.accept(b.get());out.accept(BOARD.get());out.accept(CARD.get());}).build());}
    private static BlockBehaviour.Properties properties(){return BlockBehaviour.Properties.of().strength(3).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK);}
    public FitnessMod(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);ENTITY_TYPES.register(bus);TABS.register(bus);bus.addListener(FitnessNetwork::register);NeoForge.EVENT_BUS.addListener(FitnessMod::commands);}
    private static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("fitness").executes(c->{FitnessService.open(c.getSource().getPlayerOrException(),null,false);return 1;})
            .then(Commands.literal("equipment").requires(s->s.hasPermission(2)).executes(c->{var p=c.getSource().getPlayerOrException();for(var b:MACHINES.values())give(p,new ItemStack(b.get()));give(p,new ItemStack(BOARD.get()));give(p,new ItemStack(CARD.get()));return 1;}))
            .then(Commands.literal("reload").requires(s->s.hasPermission(2)).executes(c->{FitnessService.reload(c.getSource().getServer());c.getSource().sendSuccess(()->Component.translatable("fitness.settings_reloaded"),false);return 1;})));
    }
    private static void give(net.minecraft.server.level.ServerPlayer p,ItemStack s){if(!p.getInventory().add(s))p.drop(s,false);}
}
