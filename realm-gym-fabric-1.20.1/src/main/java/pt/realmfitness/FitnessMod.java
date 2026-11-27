package pt.realmfitness;

import com.mojang.logging.LogUtils;
import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.server.level.ServerPlayer;
import pt.realmfitness.domain.Exercise;

/** Fabric lifecycle and registries. Shared domain rules remain loader independent. */
public final class FitnessMod implements ModInitializer {
    public static final String ID="realmfitness";
    public static final org.slf4j.Logger LOG=LogUtils.getLogger();
    public record Ref<T>(T value) implements Supplier<T> {public T get(){return value;}}
    private static ResourceLocation id(String path){return new ResourceLocation(ID,path);}
    public static final Attribute SWIM_SPEED=Registry.register(BuiltInRegistries.ATTRIBUTE,id("swim_speed"),new RangedAttribute("attribute.name.realmfitness.swim_speed",1,0,16).setSyncable(true));
    public static final Attribute JUMP_STRENGTH=Registry.register(BuiltInRegistries.ATTRIBUTE,id("jump_multiplier"),new RangedAttribute("fitness.attribute.squat",1,1,2).setSyncable(true));
    public static final Ref<EntityType<GymMount>> MOUNT=new Ref<>(Registry.register(BuiltInRegistries.ENTITY_TYPE,id("gym_mount"),EntityType.Builder.<GymMount>of(GymMount::new,MobCategory.MISC).sized(.05f,.05f).clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("realmfitness:gym_mount")));
    public static final Map<Exercise,Ref<GymBlock>> MACHINES=new EnumMap<>(Exercise.class);
    static{for(var e:Exercise.values()){var b=Registry.register(BuiltInRegistries.BLOCK,id(e.id()),new GymBlock(e,properties()));MACHINES.put(e,new Ref<>(b));Registry.register(BuiltInRegistries.ITEM,id(e.id()),new BlockItem(b,new Item.Properties()));}}
    public static final Ref<TrophyBlock> TROPHY=new Ref<>(Registry.register(BuiltInRegistries.BLOCK,id("realm_classic_trophy"),new TrophyBlock(properties())));
    public static final Ref<RecordBoardBlock> BOARD=new Ref<>(Registry.register(BuiltInRegistries.BLOCK,id("record_board"),new RecordBoardBlock(properties())));
    public static final Ref<BlockEntityType<RecordBoardEntity>> BOARD_ENTITY=new Ref<>(Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id("record_board"),net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(RecordBoardEntity::new,BOARD.get()).build()));
    public static final Ref<Item> CARD=new Ref<>(Registry.register(BuiltInRegistries.ITEM,id("athlete_card"),new Item(new Item.Properties().stacksTo(1))));
    public static final Ref<Item> WHEY=new Ref<>(Registry.register(BuiltInRegistries.ITEM,id("whey_powder"),new Item(new Item.Properties())));
    public static final Ref<Item> PROTEIN_SHAKE=new Ref<>(Registry.register(BuiltInRegistries.ITEM,id("protein_shake"),new ProteinItem(true)));
    public static final Ref<Item> PROTEIN_BAR=new Ref<>(Registry.register(BuiltInRegistries.ITEM,id("protein_bar"),new ProteinItem(false)));
    private static BlockBehaviour.Properties properties(){return BlockBehaviour.Properties.of().strength(3).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK);}
    @Override public void onInitialize(){
        Registry.register(BuiltInRegistries.ITEM,id("record_board"),new BlockItem(BOARD.get(),new Item.Properties()));
        Registry.register(BuiltInRegistries.ITEM,id("realm_classic_trophy"),new BlockItem(TROPHY.get(),new Item.Properties()));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,id("fitness"),FabricItemGroup.builder().title(Component.translatable("itemGroup.realmfitness")).icon(()->new ItemStack(MACHINES.get(Exercise.BENCH).get())).displayItems((params,out)->{MACHINES.values().forEach(b->out.accept(b.get()));out.accept(BOARD.get());out.accept(TROPHY.get());out.accept(CARD.get());out.accept(WHEY.get());out.accept(PROTEIN_SHAKE.get());out.accept(PROTEIN_BAR.get());}).build());
        FabricDefaultAttributeRegistry.register(EntityType.PLAYER,Player.createAttributes().add(SWIM_SPEED).add(JUMP_STRENGTH));
        FitnessNetwork.register();
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var gym=dispatcher.register(Commands.literal("gym").executes(c->{FitnessService.open(c.getSource().getPlayerOrException(),null,false);return 1;})
                .then(Commands.literal("equipment").requires(s->s.hasPermission(2)).executes(c->{var player=c.getSource().getPlayerOrException();for(var b:MACHINES.values())give(player,new ItemStack(b.get()));give(player,new ItemStack(BOARD.get()));give(player,new ItemStack(CARD.get()));return 1;}))
                .then(ConditioningCommands.create())
            .then(Commands.literal("reload").requires(s->s.hasPermission(2)).executes(c->{FitnessService.reload(c.getSource().getServer());c.getSource().sendSuccess(()->Component.translatable("fitness.settings_reloaded"),false);return 1;})));
            dispatcher.register(Commands.literal("fitness").executes(gym.getCommand()).redirect(gym));
        });
        ServerTickEvents.END_SERVER_TICK.register(FitnessService::tick);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{FitnessService.apply(handler.player);FitnessService.syncRecovery(handler.player);});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->FitnessService.logout(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->FitnessService.apply(newPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,origin,destination)->{FitnessService.logout(player);FitnessService.apply(player);});
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity,world)->{if(entity instanceof RecordBoardEntity board)FitnessService.watch(board);});
        ServerLifecycleEvents.SERVER_STOPPING.register(FitnessService::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->FitnessService.clear());
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            var state=world.getBlockState(hit.getBlockPos());if(!(state.getBlock() instanceof GymBlock))return InteractionResult.PASS;
            if(hand==InteractionHand.MAIN_HAND&&player instanceof ServerPlayer serverPlayer)FitnessService.interact(serverPlayer,GymBlock.anchor(hit.getBlockPos(),state),player.isShiftKeyDown());
            return InteractionResult.SUCCESS;
        });
        UseItemCallback.EVENT.register((player,world,hand)->{var stack=player.getItemInHand(hand);if(!stack.is(CARD.get()))return InteractionResultHolder.pass(stack);if(player instanceof ServerPlayer sp)FitnessService.open(sp,null,false);return InteractionResultHolder.success(stack);});
    }
    private static void give(ServerPlayer player,ItemStack stack){if(!player.getInventory().add(stack))player.drop(stack,false);}
}
