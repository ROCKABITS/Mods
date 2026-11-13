package pt.realmfitness.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import pt.realmfitness.*;
import pt.realmfitness.domain.*;

@GameTestHolder("realmfitness") @PrefixGameTestTemplate(false)
public final class FitnessGameTests {
    private static FakePlayer player(GameTestHelper h){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"FitnessQA"));p.setGameMode(GameType.SURVIVAL);return p;}
    private static BlockPos position(GameTestHelper h){return h.absolutePos(new BlockPos(2,2,2));}
    private static void space(GameTestHelper h,BlockPos p){for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++){for(int y=0;y<=3;y++)h.getLevel().setBlockAndUpdate(p.offset(x,y,z),Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(p.offset(x,-1,z),Blocks.STONE.defaultBlockState());}h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3)).forEach(ItemEntity::discard);}
    private static void place(GameTestHelper h,Exercise e,Direction direction){
        var pos=position(h);space(h,pos);var p=player(h);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);p.setYRot(direction.getOpposite().toYRot());p.setXRot(0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FitnessMod.MACHINES.get(e).get()));
        boolean placed=p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false))).consumesAction();
        h.assertTrue(placed,"Actual BlockItem placement failed");
    }
    @GameTest(template="empty",timeoutTicks=120) public static void allEquipmentAndRecipesAreRegistered(GameTestHelper h){
        for(var e:Exercise.values()){var id=ResourceLocation.fromNamespaceAndPath("realmfitness",e.id());h.assertTrue(BuiltInRegistries.ITEM.get(id)!=Items.AIR,"Missing machine item");h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(id).isPresent(),"Missing machine recipe");}
        for(String name:List.of("record_board","athlete_card"))h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("realmfitness",name)).isPresent(),"Missing accessory recipe");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void allSixMachinesPlaceTwoRealBlocksInAllDirections(GameTestHelper h){
        for(var e:Exercise.values())for(var dir:Direction.Plane.HORIZONTAL){place(h,e,dir);var p=position(h);var state=h.getLevel().getBlockState(p);var block=(GymBlock)state.getBlock();h.assertTrue(block.intact(h.getLevel(),p),"Incomplete machine footprint");h.assertTrue(state.getValue(GymBlock.FACING)==dir,"Incorrect orientation");h.assertTrue(h.getLevel().getBlockState(p.above()).getValue(GymBlock.HALF)==DoubleBlockHalf.UPPER,"Missing upper part");}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void destroyingEitherHalfDropsExactlyOneMachine(GameTestHelper h){
        for(var e:Exercise.values())for(boolean upper:List.of(false,true)){place(h,e,Direction.NORTH);var p=position(h);h.getLevel().destroyBlock(upper?p.above():p,true);h.assertTrue(h.getLevel().getBlockState(p).isAir()&&h.getLevel().getBlockState(p.above()).isAir(),"Orphan machine part");int drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3)).stream().filter(i->i.getItem().is(FitnessMod.MACHINES.get(e).get().asItem())).mapToInt(i->i.getItem().getCount()).sum();h.assertTrue(drops==1,"Expected exactly one machine drop, got "+drops);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void placementRejectsAnOccupiedUpperBlock(GameTestHelper h){var pos=position(h);space(h,pos);h.getLevel().setBlockAndUpdate(pos.above(),Blocks.STONE.defaultBlockState());var p=player(h);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FitnessMod.MACHINES.get(Exercise.BENCH).get()));p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false)));h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&h.getLevel().getBlockState(pos.above()).is(Blocks.STONE),"Machine overwrote an occupied block");h.assertTrue(p.getMainHandItem().getCount()==1,"Rejected placement consumed the item");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void creativeBreakProducesNoDuplicatedLoot(GameTestHelper h){place(h,Exercise.BENCH,Direction.NORTH);var p=player(h);p.setGameMode(GameType.CREATIVE);var pos=position(h).above();p.gameMode.destroyBlock(pos);h.assertTrue(h.getLevel().getBlockState(pos.below()).isAir(),"Creative break orphaned lower part");h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)).isEmpty(),"Creative break produced loot");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void savedDataRoundTripKeepsPermanentAttributeExperience(GameTestHelper h){var server=h.getLevel().getServer();var data=FitnessData.get(server);data.ledger=new FitnessLedger();var a=data.ledger.athlete(UUID.randomUUID(),"Athlete",System.currentTimeMillis());a.experience.put(Exercise.BENCH,250);var saved=data.save(new CompoundTag(),server.registryAccess());var loaded=FitnessData.load(saved,server.registryAccess());h.assertTrue(loaded.ledger.athletes.values().iterator().next().experience.get(Exercise.BENCH)==250,"Permanent XP lost in NBT roundtrip");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void corruptNbtIsRejectedInsteadOfReplacingAthletes(GameTestHelper h){boolean refused=false;try{FitnessData.load(new CompoundTag(),h.getLevel().registryAccess());}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"Empty/corrupt data was accepted");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void modifiersAreReappliedWithoutStacking(GameTestHelper h){var p=player(h);var d=FitnessData.get(h.getLevel().getServer());var a=d.ledger.athlete(p.getUUID(),p.getGameProfile().getName(),System.currentTimeMillis());a.experience.put(Exercise.BENCH,FitnessLedger.threshold(20));FitnessService.apply(p);double value=p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);FitnessService.apply(p);h.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==value,"Repeated sync stacked bonuses");h.assertTrue(value>p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getBaseValue(),"Permanent modifier was not applied");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void physicalRecordBoardPersistsItsFilter(GameTestHelper h){var pos=position(h);space(h,pos);h.getLevel().setBlockAndUpdate(pos,FitnessMod.BOARD.get().defaultBlockState());var b=(RecordBoardEntity)h.getLevel().getBlockEntity(pos);b.exercise="BOX";b.load=2;b.repetitions=12;b.period="week";b.scope="gym";var tag=b.saveWithoutMetadata(h.getLevel().registryAccess());var restored=new RecordBoardEntity(pos,b.getBlockState());restored.loadWithComponents(tag,h.getLevel().registryAccess());h.assertTrue(restored.exercise.equals("BOX")&&restored.load==2&&restored.period.equals("week")&&restored.scope.equals("gym"),"Record-board configuration did not survive NBT");h.succeed();}
}
