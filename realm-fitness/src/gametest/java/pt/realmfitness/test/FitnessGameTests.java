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
    private static BlockPos position(GameTestHelper h){return h.absolutePos(new BlockPos(4,2,4));}
    private static void space(GameTestHelper h,BlockPos p){for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){for(int y=0;y<=3;y++)h.getLevel().setBlockAndUpdate(p.offset(x,y,z),Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(p.offset(x,-1,z),Blocks.STONE.defaultBlockState());}h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(6)).forEach(ItemEntity::discard);}
    private static void place(GameTestHelper h,Exercise e,Direction direction){
        var pos=position(h);space(h,pos);var p=player(h);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);p.setYRot(direction.getOpposite().toYRot());p.setXRot(0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FitnessMod.MACHINES.get(e).get()));
        boolean placed=p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false))).consumesAction();
        h.assertTrue(placed,"Actual BlockItem placement failed");
    }
    @GameTest(template="empty",timeoutTicks=120) public static void allEquipmentAndRecipesAreRegistered(GameTestHelper h){
        for(var e:Exercise.values()){var id=ResourceLocation.fromNamespaceAndPath("realmfitness",e.id());h.assertTrue(BuiltInRegistries.ITEM.get(id)!=Items.AIR,"Missing machine item");h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(id).isPresent(),"Missing machine recipe");}
        for(String name:List.of("record_board","athlete_card"))h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("realmfitness",name)).isPresent(),"Missing accessory recipe");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void allSixMachinesPlaceCompleteFootprintsInAllDirections(GameTestHelper h){
        for(var e:Exercise.values())for(var dir:Direction.Plane.HORIZONTAL){place(h,e,dir);var p=position(h);var state=h.getLevel().getBlockState(p);var block=(GymBlock)state.getBlock();h.assertTrue(block.intact(h.getLevel(),p),"Incomplete machine footprint");h.assertTrue(state.getValue(GymBlock.FACING)==dir,"Incorrect orientation");for(int i=0;i<18;i++)if(GymGeometry.contains(e,i)){var q=GymBlock.cell(p,dir,i);var cell=h.getLevel().getBlockState(q);h.assertTrue(cell.is(block)&&cell.getValue(GymBlock.EXPANDED)&&cell.getValue(GymBlock.PART)==i,"Missing cell "+i);h.assertTrue(GymBlock.anchor(q,cell).equals(p),"Rotated cell resolves to wrong anchor");}}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void destroyingEitherHalfDropsExactlyOneMachine(GameTestHelper h){
        for(var e:Exercise.values())for(boolean upper:List.of(false,true)){place(h,e,Direction.NORTH);var p=position(h);h.getLevel().destroyBlock(upper?p.above():p,true);h.assertTrue(h.getLevel().getBlockState(p).isAir()&&h.getLevel().getBlockState(p.above()).isAir(),"Orphan machine part");int drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(6)).stream().filter(i->i.getItem().is(FitnessMod.MACHINES.get(e).get().asItem())).mapToInt(i->i.getItem().getCount()).sum();h.assertTrue(drops==1,"Expected exactly one machine drop, got "+drops);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void placementRejectsAnOccupiedUpperBlock(GameTestHelper h){var pos=position(h);space(h,pos);h.getLevel().setBlockAndUpdate(pos.above(),Blocks.STONE.defaultBlockState());var p=player(h);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FitnessMod.MACHINES.get(Exercise.BENCH).get()));p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false)));h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&h.getLevel().getBlockState(pos.above()).is(Blocks.STONE),"Machine overwrote an occupied block");h.assertTrue(p.getMainHandItem().getCount()==1,"Rejected placement consumed the item");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void creativeBreakProducesNoDuplicatedLoot(GameTestHelper h){place(h,Exercise.BENCH,Direction.NORTH);var p=player(h);p.setGameMode(GameType.CREATIVE);var pos=position(h).above();p.gameMode.destroyBlock(pos);h.assertTrue(h.getLevel().getBlockState(pos.below()).isAir(),"Creative break orphaned lower part");h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6)).isEmpty(),"Creative break produced loot");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void savedDataRoundTripKeepsPermanentAttributeExperience(GameTestHelper h){var server=h.getLevel().getServer();var data=FitnessData.get(server);data.ledger=new FitnessLedger();var a=data.ledger.athlete(UUID.randomUUID(),"Athlete",System.currentTimeMillis());a.experience.put(Exercise.BENCH,250);var saved=data.save(new CompoundTag(),server.registryAccess());var loaded=FitnessData.load(saved,server.registryAccess());h.assertTrue(loaded.ledger.athletes.values().iterator().next().experience.get(Exercise.BENCH)==250,"Permanent XP lost in NBT roundtrip");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void corruptNbtIsRejectedInsteadOfReplacingAthletes(GameTestHelper h){boolean refused=false;try{FitnessData.load(new CompoundTag(),h.getLevel().registryAccess());}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"Empty/corrupt data was accepted");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void modifiersAreReappliedWithoutStacking(GameTestHelper h){var p=player(h);var d=FitnessData.get(h.getLevel().getServer());var a=d.ledger.athlete(p.getUUID(),p.getGameProfile().getName(),System.currentTimeMillis());a.experience.put(Exercise.BENCH,FitnessLedger.threshold(20));FitnessService.apply(p);double value=p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);FitnessService.apply(p);h.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==value,"Repeated sync stacked bonuses");h.assertTrue(value>p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getBaseValue(),"Permanent modifier was not applied");h.succeed();}
    @GameTest(template="empty",timeoutTicks=120) public static void physicalRecordBoardPersistsItsFilter(GameTestHelper h){var pos=position(h);space(h,pos);h.getLevel().setBlockAndUpdate(pos,FitnessMod.BOARD.get().defaultBlockState());var b=(RecordBoardEntity)h.getLevel().getBlockEntity(pos);b.exercise="BOX";b.load=2;b.repetitions=12;b.period="week";b.scope="gym";var tag=b.saveWithoutMetadata(h.getLevel().registryAccess());var restored=new RecordBoardEntity(pos,b.getBlockState());restored.loadWithComponents(tag,h.getLevel().registryAccess());h.assertTrue(restored.exercise.equals("BOX")&&restored.load==2&&restored.period.equals("week")&&restored.scope.equals("gym"),"Record-board configuration did not survive NBT");h.succeed();}

    @GameTest(template="empty",timeoutTicks=120) public static void everyOccupiedCellBreaksWithoutOrphansOrDuplicateItems(GameTestHelper h){
        for(var e:Exercise.values())for(int i=0;i<18;i++)if(GymGeometry.contains(e,i)){
            place(h,e,Direction.WEST);var anchor=position(h);h.getLevel().destroyBlock(GymBlock.cell(anchor,Direction.WEST,i),true);
            for(int j=0;j<18;j++)if(GymGeometry.contains(e,j))h.assertTrue(h.getLevel().getBlockState(GymBlock.cell(anchor,Direction.WEST,j)).isAir(),"Orphan cell after breaking "+i);
            int drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(anchor).inflate(6)).stream().filter(item->item.getItem().is(FitnessMod.MACHINES.get(e).get().asItem())).mapToInt(item->item.getItem().getCount()).sum();h.assertTrue(drops==1,"Cell break duplicated or lost the item");
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void placementRejectsFarCornerWithoutConsumingItem(GameTestHelper h){
        var pos=position(h);space(h,pos);var obstacle=GymBlock.cell(pos,Direction.NORTH,17);h.getLevel().setBlockAndUpdate(obstacle,Blocks.STONE.defaultBlockState());var p=player(h);p.setYRot(Direction.SOUTH.toYRot());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FitnessMod.MACHINES.get(Exercise.BENCH).get()));
        p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false)));
        h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&h.getLevel().getBlockState(obstacle).is(Blocks.STONE)&&p.getMainHandItem().getCount()==1,"Far obstacle was overwritten or item consumed");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void removingFarFloorSupportRemovesWholeMachineOnce(GameTestHelper h){
        place(h,Exercise.RUN,Direction.NORTH);var pos=position(h);h.getLevel().removeBlock(GymBlock.cell(pos,Direction.NORTH,5).below(),false);
        h.runAfterDelay(5,()->{h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Unsupported treadmill remained");int drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6)).stream().filter(i->i.getItem().is(FitnessMod.MACHINES.get(Exercise.RUN).get().asItem())).mapToInt(i->i.getItem().getCount()).sum();h.assertTrue(drops==1,"Support loss duplicated or lost machine");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=120) public static void legacyTwoBlockMachinesRemainUsableAndDropOnce(GameTestHelper h){
        for(var e:Exercise.values()){
            var pos=position(h);space(h,pos);var block=FitnessMod.MACHINES.get(e).get();var state=block.defaultBlockState();h.getLevel().setBlock(pos,state,2);h.getLevel().setBlock(pos.above(),state.setValue(GymBlock.HALF,DoubleBlockHalf.UPPER),3);
            h.assertTrue(block.intact(h.getLevel(),pos),"Legacy saved state no longer intact");h.getLevel().destroyBlock(pos.above(),true);int drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6)).stream().filter(i->i.getItem().is(block.asItem())).mapToInt(i->i.getItem().getCount()).sum();h.assertTrue(drops==1&&h.getLevel().getBlockState(pos).isAir(),"Legacy break lost or duplicated item");
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void collisionBoxesAreLocalAndOpenFrameStaysOpen(GameTestHelper h){
        var level=h.getLevel();for(var e:Exercise.values())for(var facing:Direction.Plane.HORIZONTAL)for(int i=0;i<18;i++)if(GymGeometry.contains(e,i)){
            var shape=GymGeometry.shape(e,i,facing);for(var box:shape.toAabbs())h.assertTrue(box.minX>=0&&box.minY>=0&&box.minZ>=0&&box.maxX<=1&&box.maxY<=1&&box.maxZ<=1,"Collision extends outside its cell");
        }
        h.assertTrue(GymGeometry.shape(Exercise.PULL,6,Direction.NORTH).isEmpty(),"Pull-up open front has an invisible solid collision");h.succeed();
    }

    private static net.minecraft.world.entity.decoration.ArmorStand passenger(GameTestHelper h){var p=position(h);var entity=new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),p.getX(),p.getY(),p.getZ());entity.setNoGravity(true);h.getLevel().addFreshEntity(entity);return entity;}
    private static GymMount ride(GameTestHelper h,Exercise exercise,Direction direction,net.minecraft.world.entity.LivingEntity rider){
        var pos=position(h);var mount=FitnessMod.MOUNT.get().create(h.getLevel());var entrance=Vec3.atBottomCenterOf(pos.relative(Direction.NORTH,2));rider.setPos(entrance);mount.configure(pos,direction,exercise,false,entrance);h.getLevel().addFreshEntity(mount);h.assertTrue(rider.startRiding(mount,true),"Player could not ride gym vehicle");mount.positionRider(rider);return mount;
    }
    @GameTest(template="empty",timeoutTicks=120) public static void equipmentVehiclesSeatPassengersInAllFourDirections(GameTestHelper h){
        for(var exercise:Exercise.values())for(var direction:Direction.Plane.HORIZONTAL){place(h,exercise,direction);var rider=passenger(h);var mount=ride(h,exercise,direction,rider);h.assertTrue(rider.getVehicle()==mount,"Player is not an actual passenger");h.assertTrue(rider.distanceToSqr(mount.position())<.01,"Vehicle failed to position rider");h.assertTrue(mount.facing()==direction&&mount.exercise()==exercise,"Vehicle lost machine orientation or exercise");rider.stopRiding();mount.discard();rider.discard();}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void breakingOccupiedEquipmentEjectsRiderAndRemovesTemporaryVehicle(GameTestHelper h){
        place(h,Exercise.BENCH,Direction.NORTH);var rider=passenger(h);var mount=ride(h,Exercise.BENCH,Direction.NORTH,rider);h.getLevel().destroyBlock(position(h).above(),true);h.runAfterDelay(8,()->{h.assertTrue(mount.isRemoved()&&!rider.isPassenger(),"Broken machine left a mounted player or vehicle");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=120) public static void occupiedModelTransitionsKeepAllPartsAndDoNotProduceLoot(GameTestHelper h){
        for(var exercise:Exercise.values()){place(h,exercise,Direction.EAST);var pos=position(h);var block=FitnessMod.MACHINES.get(exercise).get();block.working(h.getLevel(),pos,true);h.assertTrue(block.intact(h.getLevel(),pos),"Animation state broke the equipment footprint");for(int i=0;i<18;i++)if(GymGeometry.contains(exercise,i))h.assertTrue(h.getLevel().getBlockState(GymBlock.cell(pos,Direction.EAST,i)).getValue(GymBlock.WORKING),"Animation state missed a cell");block.working(h.getLevel(),pos,false);h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6)).isEmpty(),"Animation transition produced a duplicate item");}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void temporaryVehiclesAreNotSavedAndDismountUsesSafeEntrance(GameTestHelper h){
        place(h,Exercise.RUN,Direction.NORTH);var rider=passenger(h);var mount=ride(h,Exercise.RUN,Direction.NORTH,rider);h.assertTrue(!mount.save(new CompoundTag()),"Temporary gym vehicle can be serialized into a world");var target=Vec3.atBottomCenterOf(position(h).relative(Direction.NORTH,2));h.assertTrue(mount.getDismountLocationForPassenger(rider).distanceToSqr(target)<.001,"Dismount ignored the safe entrance");rider.stopRiding();mount.discard();rider.discard();h.succeed();
    }
}
