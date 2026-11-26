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
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.*;
import pt.realmfitness.*;
import pt.realmfitness.domain.*;

@GameTestHolder(value="realmfitness",namespace="realmfitness")
public final class FitnessGameTests {
    private static ServerPlayer player(GameTestHelper h){
        var profile=new GameProfile(UUID.randomUUID(),"FitnessQA");var server=h.getLevel().getServer();
        var player=new ServerPlayer(server,h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        player.setGameMode(GameType.SURVIVAL);return player;
    }
    private static BlockPos position(GameTestHelper h){return h.absolutePos(new BlockPos(4,2,4));}
    private static void space(GameTestHelper h,BlockPos p){for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){for(int y=0;y<=3;y++)h.getLevel().setBlockAndUpdate(p.offset(x,y,z),Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(p.offset(x,-1,z),Blocks.STONE.defaultBlockState());}h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(6)).forEach(ItemEntity::discard);}
    private static void place(GameTestHelper h,Exercise e,Direction direction){
        var pos=position(h);space(h,pos);var p=player(h);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);p.setYRot(direction.getOpposite().toYRot());p.setXRot(0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FitnessMod.MACHINES.get(e).get()));
        boolean placed=p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false))).consumesAction();
        h.assertTrue(placed,"Actual BlockItem placement failed");
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void renamedCommandsKeepLegacyAliasAndOperatorPermissions(GameTestHelper h){
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();var gym=dispatcher.getRoot().getChild("gym");var legacy=dispatcher.getRoot().getChild("fitness");
        h.assertTrue(gym!=null&&legacy!=null&&legacy.getRedirect()==gym&&legacy.getCommand()==gym.getCommand(),"Gym command or legacy alias missing");
        for(String command:List.of("gym","fitness")){
            var p=player(h);var source=p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            try{h.assertTrue(dispatcher.execute(command+" equipment",source)==1,"Equipment command failed");}catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new AssertionError(error);}
            for(var machine:FitnessMod.MACHINES.values())h.assertTrue(p.getInventory().contains(new ItemStack(machine.get())),"Equipment command omitted a machine");
            h.assertTrue(p.getInventory().contains(new ItemStack(FitnessMod.BOARD.get()))&&p.getInventory().contains(new ItemStack(FitnessMod.CARD.get())),"Equipment command omitted accessories");
            boolean refused=false;try{dispatcher.execute(command+" equipment",source.withPermission(0));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){refused=true;}
            h.assertTrue(refused,"Equipment command bypassed operator permissions");
        }h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void allEquipmentAndRecipesAreRegistered(GameTestHelper h){
        for(var e:Exercise.values()){var id=ResourceLocation.fromNamespaceAndPath("realmfitness",e.id());h.assertTrue(BuiltInRegistries.ITEM.get(id)!=Items.AIR,"Missing machine item");h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(id).isPresent(),"Missing machine recipe");}
        for(String name:List.of("record_board","athlete_card","whey_powder","protein_shake","protein_bar"))h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("realmfitness",name)).isPresent(),"Missing accessory recipe");h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void allSixMachinesPlaceCompleteFootprintsInAllDirections(GameTestHelper h){
        for(var e:Exercise.values())for(var dir:Direction.Plane.HORIZONTAL){place(h,e,dir);var p=position(h);var state=h.getLevel().getBlockState(p);var block=(GymBlock)state.getBlock();h.assertTrue(block.intact(h.getLevel(),p),"Incomplete machine footprint");h.assertTrue(state.getValue(GymBlock.FACING)==dir,"Incorrect orientation");for(int i=0;i<18;i++)if(GymGeometry.contains(e,i)){var q=GymBlock.cell(p,dir,i);var cell=h.getLevel().getBlockState(q);h.assertTrue(cell.is(block)&&cell.getValue(GymBlock.EXPANDED)&&cell.getValue(GymBlock.PART)==i,"Missing cell "+i);h.assertTrue(GymBlock.anchor(q,cell).equals(p),"Rotated cell resolves to wrong anchor");}}h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void destroyingEitherHalfDropsExactlyOneMachine(GameTestHelper h){
        for(var e:Exercise.values())for(boolean upper:List.of(false,true)){place(h,e,Direction.NORTH);var p=position(h);h.getLevel().destroyBlock(upper?p.above():p,true);h.assertTrue(h.getLevel().getBlockState(p).isAir()&&h.getLevel().getBlockState(p.above()).isAir(),"Orphan machine part");int drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(6)).stream().filter(i->i.getItem().is(FitnessMod.MACHINES.get(e).get().asItem())).mapToInt(i->i.getItem().getCount()).sum();h.assertTrue(drops==1,"Expected exactly one machine drop, got "+drops);}h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void placementRejectsAnOccupiedUpperBlock(GameTestHelper h){var pos=position(h);space(h,pos);h.getLevel().setBlockAndUpdate(pos.above(),Blocks.STONE.defaultBlockState());var p=player(h);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1.5);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FitnessMod.MACHINES.get(Exercise.BENCH).get()));p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false)));h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&h.getLevel().getBlockState(pos.above()).is(Blocks.STONE),"Machine overwrote an occupied block");h.assertTrue(p.getMainHandItem().getCount()==1,"Rejected placement consumed the item");h.succeed();}
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void creativeBreakProducesNoDuplicatedLoot(GameTestHelper h){place(h,Exercise.BENCH,Direction.NORTH);var p=player(h);p.setGameMode(GameType.CREATIVE);var pos=position(h).above();p.gameMode.destroyBlock(pos);h.assertTrue(h.getLevel().getBlockState(pos.below()).isAir(),"Creative break orphaned lower part");h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6)).isEmpty(),"Creative break produced loot");h.succeed();}
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void savedDataRoundTripKeepsPermanentAttributeExperience(GameTestHelper h){var server=h.getLevel().getServer();var data=FitnessData.get(server);data.ledger=new FitnessLedger();var a=data.ledger.athlete(UUID.randomUUID(),"Athlete",System.currentTimeMillis());a.experience.put(Exercise.BENCH,250);var saved=data.save(new CompoundTag(),server.registryAccess());var loaded=FitnessData.load(saved,server.registryAccess());h.assertTrue(loaded.ledger.athletes.values().iterator().next().experience.get(Exercise.BENCH)==250,"Permanent XP lost in NBT roundtrip");h.succeed();}
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void corruptNbtIsRejectedInsteadOfReplacingAthletes(GameTestHelper h){boolean refused=false;try{FitnessData.load(new CompoundTag(),h.getLevel().registryAccess());}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"Empty/corrupt data was accepted");h.succeed();}
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void modifiersAreReappliedWithoutStacking(GameTestHelper h){var p=player(h);var d=FitnessData.get(h.getLevel().getServer());var a=d.ledger.athlete(p.getUUID(),p.getGameProfile().getName(),System.currentTimeMillis());a.experience.put(Exercise.BENCH,FitnessLedger.threshold(20));FitnessService.apply(p);double value=p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);FitnessService.apply(p);h.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==value,"Repeated sync stacked bonuses");h.assertTrue(value>p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getBaseValue(),"Permanent modifier was not applied");h.succeed();}
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void physicalRecordBoardPersistsItsFilter(GameTestHelper h){var pos=position(h);space(h,pos);h.getLevel().setBlockAndUpdate(pos,FitnessMod.BOARD.get().defaultBlockState());var b=(RecordBoardEntity)h.getLevel().getBlockEntity(pos);b.exercise="BOX";b.load=2;b.repetitions=12;b.period="week";b.scope="gym";var tag=b.saveWithoutMetadata(h.getLevel().registryAccess());var restored=new RecordBoardEntity(pos,b.getBlockState());restored.loadWithComponents(tag,h.getLevel().registryAccess());h.assertTrue(restored.exercise.equals("BOX")&&restored.load==2&&restored.period.equals("week")&&restored.scope.equals("gym"),"Record-board configuration did not survive NBT");h.succeed();}

    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void everyOccupiedCellBreaksWithoutOrphansOrDuplicateItems(GameTestHelper h){
        for(var e:Exercise.values())for(int i=0;i<18;i++)if(GymGeometry.contains(e,i)){
            place(h,e,Direction.WEST);var anchor=position(h);h.getLevel().destroyBlock(GymBlock.cell(anchor,Direction.WEST,i),true);
            for(int j=0;j<18;j++)if(GymGeometry.contains(e,j))h.assertTrue(h.getLevel().getBlockState(GymBlock.cell(anchor,Direction.WEST,j)).isAir(),"Orphan cell after breaking "+i);
            int drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(anchor).inflate(6)).stream().filter(item->item.getItem().is(FitnessMod.MACHINES.get(e).get().asItem())).mapToInt(item->item.getItem().getCount()).sum();h.assertTrue(drops==1,"Cell break duplicated or lost the item");
        }h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void placementRejectsFarCornerWithoutConsumingItem(GameTestHelper h){
        var pos=position(h);space(h,pos);var obstacle=GymBlock.cell(pos,Direction.NORTH,17);h.getLevel().setBlockAndUpdate(obstacle,Blocks.STONE.defaultBlockState());var p=player(h);p.setYRot(Direction.SOUTH.toYRot());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FitnessMod.MACHINES.get(Exercise.BENCH).get()));
        p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false)));
        h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&h.getLevel().getBlockState(obstacle).is(Blocks.STONE)&&p.getMainHandItem().getCount()==1,"Far obstacle was overwritten or item consumed");h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void removingFarFloorSupportRemovesWholeMachineOnce(GameTestHelper h){
        place(h,Exercise.RUN,Direction.NORTH);var pos=position(h);h.getLevel().removeBlock(GymBlock.cell(pos,Direction.NORTH,5).below(),false);
        h.runAfterDelay(5,()->{h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Unsupported treadmill remained");int drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6)).stream().filter(i->i.getItem().is(FitnessMod.MACHINES.get(Exercise.RUN).get().asItem())).mapToInt(i->i.getItem().getCount()).sum();h.assertTrue(drops==1,"Support loss duplicated or lost machine");h.succeed();});
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void legacyTwoBlockMachinesRemainUsableAndDropOnce(GameTestHelper h){
        for(var e:Exercise.values()){
            var pos=position(h);space(h,pos);var block=FitnessMod.MACHINES.get(e).get();var state=block.defaultBlockState();h.getLevel().setBlock(pos,state,2);h.getLevel().setBlock(pos.above(),state.setValue(GymBlock.HALF,DoubleBlockHalf.UPPER),3);
            h.assertTrue(block.intact(h.getLevel(),pos),"Legacy saved state no longer intact");h.getLevel().destroyBlock(pos.above(),true);int drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6)).stream().filter(i->i.getItem().is(block.asItem())).mapToInt(i->i.getItem().getCount()).sum();h.assertTrue(drops==1&&h.getLevel().getBlockState(pos).isAir(),"Legacy break lost or duplicated item");
        }h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void collisionBoxesAreLocalAndOpenFrameStaysOpen(GameTestHelper h){
        var level=h.getLevel();for(var e:Exercise.values())for(var facing:Direction.Plane.HORIZONTAL)for(int i=0;i<18;i++)if(GymGeometry.contains(e,i)){
            var shape=GymGeometry.shape(e,i,facing);for(var box:shape.toAabbs())h.assertTrue(box.minX>=0&&box.minY>=0&&box.minZ>=0&&box.maxX<=1&&box.maxY<=1&&box.maxZ<=1,"Collision extends outside its cell");
        }
        h.assertTrue(GymGeometry.shape(Exercise.PULL,6,Direction.NORTH).isEmpty(),"Pull-up open front has an invisible solid collision");h.succeed();
    }

    private static net.minecraft.world.entity.decoration.ArmorStand passenger(GameTestHelper h){var p=position(h);var entity=new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),p.getX(),p.getY(),p.getZ());entity.setNoGravity(true);h.getLevel().addFreshEntity(entity);return entity;}
    private static GymMount ride(GameTestHelper h,Exercise exercise,Direction direction,net.minecraft.world.entity.LivingEntity rider){
        var pos=position(h);var mount=FitnessMod.MOUNT.get().create(h.getLevel());var entrance=Vec3.atBottomCenterOf(pos.relative(Direction.NORTH,2));rider.setPos(entrance);mount.configure(pos,direction,exercise,false,entrance);h.getLevel().addFreshEntity(mount);h.assertTrue(rider.startRiding(mount,true),"Player could not ride gym vehicle");mount.positionRider(rider);return mount;
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void equipmentVehiclesSeatPassengersInAllFourDirections(GameTestHelper h){
        for(var exercise:Exercise.values())for(var direction:Direction.Plane.HORIZONTAL){place(h,exercise,direction);var rider=passenger(h);var mount=ride(h,exercise,direction,rider);h.assertTrue(rider.getVehicle()==mount,"Player is not an actual passenger");h.assertTrue(rider.distanceToSqr(mount.position())<.01,"Vehicle failed to position rider");h.assertTrue(mount.facing()==direction&&mount.exercise()==exercise,"Vehicle lost machine orientation or exercise");rider.stopRiding();mount.discard();rider.discard();}h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void breakingOccupiedEquipmentEjectsRiderAndRemovesTemporaryVehicle(GameTestHelper h){
        place(h,Exercise.BENCH,Direction.NORTH);var rider=passenger(h);var mount=ride(h,Exercise.BENCH,Direction.NORTH,rider);h.getLevel().destroyBlock(position(h).above(),true);h.runAfterDelay(8,()->{h.assertTrue(mount.isRemoved()&&!rider.isPassenger(),"Broken machine left a mounted player or vehicle");h.succeed();});
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void occupiedModelTransitionsKeepAllPartsAndDoNotProduceLoot(GameTestHelper h){
        for(var exercise:Exercise.values()){place(h,exercise,Direction.EAST);var pos=position(h);var block=FitnessMod.MACHINES.get(exercise).get();block.working(h.getLevel(),pos,true);h.assertTrue(block.intact(h.getLevel(),pos),"Animation state broke the equipment footprint");for(int i=0;i<18;i++)if(GymGeometry.contains(exercise,i))h.assertTrue(h.getLevel().getBlockState(GymBlock.cell(pos,Direction.EAST,i)).getValue(GymBlock.WORKING),"Animation state missed a cell");block.working(h.getLevel(),pos,false);h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6)).isEmpty(),"Animation transition produced a duplicate item");}h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void temporaryVehiclesAreNotSavedAndDismountUsesSafeEntrance(GameTestHelper h){
        place(h,Exercise.RUN,Direction.NORTH);var rider=passenger(h);var mount=ride(h,Exercise.RUN,Direction.NORTH,rider);h.assertTrue(!mount.save(new CompoundTag()),"Temporary gym vehicle can be serialized into a world");var target=Vec3.atBottomCenterOf(position(h).relative(Direction.NORTH,2));h.assertTrue(mount.getDismountLocationForPassenger(rider).distanceToSqr(target)<.001,"Dismount ignored the safe entrance");rider.stopRiding();mount.discard();rider.discard();h.succeed();
    }

    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void forgeFallEventReducesActualDamage(GameTestHelper h){
        var player=player(h);try{var protection=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");protection.setAccessible(true);protection.setInt(player,0);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        var data=FitnessData.get(h.getLevel().getServer());data.ledger=new FitnessLedger();var athlete=data.ledger.athlete(player.getUUID(),"FitnessQA",System.currentTimeMillis());
        player.causeFallDamage(13,1,player.damageSources().fall());double base=20-player.getHealth();player.setHealth(20);player.invulnerableTime=0;
        athlete.experience.put(Exercise.PULL,FitnessLedger.threshold(20));player.causeFallDamage(13,1,player.damageSources().fall());double trained=20-player.getHealth();
        h.assertTrue(base>0&&trained>0&&trained<base,"Forge fall reduction did not affect actual damage: "+base+" -> "+trained);h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void forgeSwimAttributeBoostsActualWaterMovement(GameTestHelper h){
        var player=player(h);var pos=position(h);space(h,pos);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<3;y++)h.getLevel().setBlockAndUpdate(pos.offset(x,y,z),Blocks.WATER.defaultBlockState());
        var data=FitnessData.get(h.getLevel().getServer());data.ledger=new FitnessLedger();var athlete=data.ledger.athlete(player.getUUID(),"FitnessQA",System.currentTimeMillis());
        player.setPos(Vec3.atCenterOf(pos));player.baseTick();h.assertTrue(player.isInWater(),"Swim athlete was not in water");player.setDeltaMovement(Vec3.ZERO);player.travel(new Vec3(0,0,1));double base=player.getDeltaMovement().z;
        player.setPos(Vec3.atCenterOf(pos));player.setDeltaMovement(Vec3.ZERO);athlete.experience.put(Exercise.ROW,FitnessLedger.threshold(20));FitnessService.apply(player);FitnessService.apply(player);player.travel(new Vec3(0,0,1));double boosted=player.getDeltaMovement().z;
        h.assertTrue(Math.abs(player.getAttributeValue(net.minecraftforge.common.ForgeMod.SWIM_SPEED.getHolder().orElseThrow())-1.15)<.00001,"Swim modifier missing or stacked");h.assertTrue(base>0&&boosted>base*1.1&&boosted<base*1.2,"Forge swim modifier did not affect movement: "+base+" -> "+boosted);h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void unregisteredSyntheticPlayersCannotStartRewardedTraining(GameTestHelper h){
        var player=player(h);var view=new FitnessService.View();view.pos=position(h);view.dimension=h.getLevel().dimension().location();view.exercise=Exercise.BENCH;
        boolean refused=false;try{FitnessService.start(player,view,20,8);}catch(IllegalArgumentException expected){refused="fitness.survival_required".equals(expected.getMessage());}
        h.assertTrue(refused&&!player.isPassenger(),"An unregistered synthetic player could start a rewarded session");h.succeed();
    }

    private static void ageCondition(FitnessLedger ledger,FitnessLedger.Athlete athlete,int days){
        athlete.conditionClock.elapsed=days*FitnessLedger.DAY;athlete.conditionClock.observedAt=ledger.highWater;
        for(var state:athlete.condition.values()){state.maintainedAt=0;state.lossDays=0;state.percent=100;}
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void physicalConditionChangesRealModifiersWithoutStackingOrUnlockLoss(GameTestHelper h){
        var p=player(h);FitnessService.apply(p);var d=FitnessData.get(h.getLevel().getServer());var previous=d.ledger.settings;
        var config=FitnessLedger.JSON.fromJson(FitnessLedger.JSON.toJson(previous),FitnessLedger.Settings.class);config.conditioning.enabled=true;
        try{
            d.ledger.configure(config,System.currentTimeMillis());var a=d.ledger.athlete(p.getUUID(),p.getGameProfile().getName(),System.currentTimeMillis());
            for(var e:Exercise.attributes())a.experience.put(e,FitnessLedger.threshold(20));ageCondition(d.ledger,a,11);FitnessService.apply(p);
            var damage=net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;var speed=net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
            double expected=p.getAttribute(damage).getBaseValue()*1.08;
            h.assertTrue(Math.abs(p.getAttributeValue(damage)-expected)<.000001,"Condition was not applied to actual damage");
            h.assertTrue(Math.abs(p.getAttributeValue(speed)-p.getAttribute(speed).getBaseValue()*1.064)<.000001,"Condition was not applied to actual movement speed");
            h.assertTrue(Math.abs(FitnessService.fallReduction(p)-.16)<.000001,"Condition was not applied to fall protection");
            FitnessService.apply(p);h.assertTrue(Math.abs(p.getAttributeValue(damage)-expected)<.000001,"Condition modifiers stacked");
            h.assertTrue(d.ledger.level(a,Exercise.BENCH)==20,"Condition decay removed permanent levels");
            d.ledger.ready(a,Exercise.BENCH,Exercise.BENCH.maximumLoad(20),8,d.ledger.highWater);
        }finally{d.ledger.configure(previous,System.currentTimeMillis());FitnessService.apply(p);}
        h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void physicalConditionSurvivesNativeSavedDataAndDisablingRestoresFullBonus(GameTestHelper h){
        var p=player(h);FitnessService.apply(p);var d=FitnessData.get(h.getLevel().getServer());var previous=d.ledger.settings;
        var config=FitnessLedger.JSON.fromJson(FitnessLedger.JSON.toJson(previous),FitnessLedger.Settings.class);config.conditioning.enabled=true;
        try{
            d.ledger.configure(config,System.currentTimeMillis());var a=d.ledger.athlete(p.getUUID(),p.getGameProfile().getName(),System.currentTimeMillis());a.experience.put(Exercise.BENCH,FitnessLedger.threshold(20));
            ageCondition(d.ledger,a,11);FitnessService.apply(p);d.ledger.online(a);var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());var restored=FitnessData.load(saved,h.getLevel().registryAccess());
            var copy=restored.ledger.athletes.get(p.getUUID());h.assertTrue(restored.ledger.condition(copy,Exercise.BENCH)==80,"SavedData lost physical condition");
            h.assertTrue(!copy.conditionClock.online,"SavedData incorrectly resumed an online session");
            var disabled=FitnessLedger.JSON.fromJson(FitnessLedger.JSON.toJson(config),FitnessLedger.Settings.class);disabled.conditioning.enabled=false;d.ledger.configure(disabled,System.currentTimeMillis());FitnessService.apply(p);
            h.assertTrue(Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)-p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getBaseValue()*1.10)<.000001,"Disabling condition did not restore full permanent bonus");
            h.assertTrue(a.experience.get(Exercise.BENCH)==FitnessLedger.threshold(20),"Disabling condition removed permanent experience");
        }finally{d.ledger.configure(previous,System.currentTimeMillis());FitnessService.apply(p);}
        h.succeed();
    }

    private static java.nio.file.Path commandConfig(){return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("realm-fitness.json");}
    private static void restoreCommandConfig(net.minecraft.server.MinecraftServer server,byte[] contents){
        try{java.nio.file.Files.write(commandConfig(),contents);FitnessService.reload(server);}catch(java.io.IOException error){throw new AssertionError(error);}
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void conditioningCommandsPersistAndRespectOperatorPermissions(GameTestHelper h){
        var server=h.getLevel().getServer();FitnessService.conditioningEnabled(server);byte[] original;
        try{original=java.nio.file.Files.readAllBytes(commandConfig());}catch(java.io.IOException error){throw new AssertionError(error);}
        try{
            var json=com.google.gson.JsonParser.parseString(new String(original,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();json.addProperty("command_test_extension","preserved");java.nio.file.Files.writeString(commandConfig(),json.toString());
            var dispatcher=server.getCommands().getDispatcher();var operator=server.createCommandSourceStack().withPermission(2).withSuppressedOutput();var visitor=operator.withPermission(0);
            h.assertTrue(dispatcher.execute("gym conditioning on",operator)==1,"Console-compatible enable command failed");
            h.assertTrue(FitnessService.conditioningEnabled(server),"Enable command did not immediately change the active rules");
            h.assertTrue(FitnessConfig.load(commandConfig()).conditioning.enabled,"Enabled state was not saved to the configuration");
            FitnessService.reload(server);h.assertTrue(FitnessService.conditioningEnabled(server),"Reload lost the saved enabled state");
            var before=java.nio.file.Files.readString(commandConfig());
            for(String prefix:List.of("gym","fitness")){
                h.assertTrue(dispatcher.execute(prefix+" conditioning",visitor)==1&&dispatcher.execute(prefix+" conditioning status",visitor)==1,"Status command requires a player or operator");
                for(String operation:List.of("on","off")){
                    boolean denied=false;try{dispatcher.execute(prefix+" conditioning "+operation,visitor);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}
                    h.assertTrue(denied,"Non-operator changed the condition setting");
                }
            }
            h.assertTrue(java.nio.file.Files.readString(commandConfig()).equals(before),"Denied commands changed the file");
            h.assertTrue(dispatcher.execute("fitness conditioning off",operator)==1&&!FitnessService.conditioningEnabled(server),"Legacy alias did not disable the rules immediately");
            var saved=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(commandConfig())).getAsJsonObject();
            h.assertTrue(!FitnessConfig.load(commandConfig()).conditioning.enabled&&saved.get("command_test_extension").getAsString().equals("preserved"),"Disable command lost persisted state or unrelated settings");
        }catch(java.io.IOException|com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new AssertionError(error);}
        finally{restoreCommandConfig(server,original);}
        h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void repeatedEnableKeepsConditionAndInvalidConfigDoesNotChangeActiveRules(GameTestHelper h){
        var server=h.getLevel().getServer();FitnessService.conditioningEnabled(server);byte[] original;
        try{original=java.nio.file.Files.readAllBytes(commandConfig());}catch(java.io.IOException error){throw new AssertionError(error);}
        try{
            var dispatcher=server.getCommands().getDispatcher();var operator=server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            h.assertTrue(dispatcher.execute("gym conditioning on",operator)==1,"Enable command failed");
            var p=player(h);var d=FitnessData.get(server);var a=d.ledger.athlete(p.getUUID(),p.getGameProfile().getName(),System.currentTimeMillis());a.experience.put(Exercise.BENCH,FitnessLedger.threshold(20));
            ageCondition(d.ledger,a,11);FitnessService.apply(p);double condition=d.ledger.condition(a,Exercise.BENCH);long maintained=a.condition.get(Exercise.BENCH).maintainedAt;
            h.assertTrue(dispatcher.execute("gym conditioning on",operator)==1,"Repeated enable failed");
            h.assertTrue(d.ledger.condition(a,Exercise.BENCH)==condition&&a.condition.get(Exercise.BENCH).maintainedAt==maintained,"Repeated enable reset condition or its grace period");
            var invalid=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(commandConfig())).getAsJsonObject();invalid.getAsJsonObject("conditioning").addProperty("graceDays",-1);String broken=invalid.toString();java.nio.file.Files.writeString(commandConfig(),broken);
            h.assertTrue(dispatcher.execute("gym conditioning off",operator)==0,"Invalid configuration was reported as a successful update");
            h.assertTrue(FitnessService.conditioningEnabled(server)&&java.nio.file.Files.readString(commandConfig()).equals(broken),"Failed command changed the active mode or configuration");
            h.assertTrue(a.experience.get(Exercise.BENCH)==FitnessLedger.threshold(20),"Commands changed permanent experience");
        }catch(java.io.IOException|com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new AssertionError(error);}
        finally{restoreCommandConfig(server,original);}
        h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void dayModeCommandsPersistAndEnforcePermissions(GameTestHelper h){
        var server=h.getLevel().getServer();var data=FitnessData.get(server);var previous=data.ledger;byte[] before=null;
        try{
            if(java.nio.file.Files.exists(commandConfig()))before=java.nio.file.Files.readAllBytes(commandConfig());
            data.ledger=new FitnessLedger();var document=FitnessLedger.JSON.toJsonTree(new FitnessLedger.Settings()).getAsJsonObject();document.addProperty("clock_extension","keep");
            java.nio.file.Files.writeString(commandConfig(),document.toString());FitnessService.reload(server);
            var dispatcher=server.getCommands().getDispatcher();var source=server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            h.assertTrue(dispatcher.execute("gym conditioning days minecraft 3",source)==1,"Minecraft-day command failed");
            FitnessService.reload(server);var settings=FitnessService.conditioningSettings(server);
            h.assertTrue(settings.timeMode==Conditioning.TimeMode.MINECRAFT&&settings.graceDays==3&&!settings.enabled,"Day command did not persist, or enabled the option unexpectedly");
            var saved=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(commandConfig())).getAsJsonObject();
            h.assertTrue(saved.get("clock_extension").getAsString().equals("keep"),"Day command lost an unrelated setting");
            h.assertTrue(dispatcher.execute("gym conditioning status",source.withPermission(0))==1,"Visitor cannot view the current clock");
            for(String command:List.of("gym conditioning days real 7","gym conditioning days minecraft 7")){
                boolean refused=false;try{dispatcher.execute(command,source.withPermission(0));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){refused=true;}
                h.assertTrue(refused,"Visitor changed the conditioning clock");
            }
            for(String command:List.of("gym conditioning days real -1","gym conditioning days minecraft 366")){
                boolean refused=false;try{dispatcher.execute(command,source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){refused=true;}
                h.assertTrue(refused,"Invalid grace period was accepted");
            }
            h.assertTrue(dispatcher.execute("fitness conditioning days real 5",source)==1,"Legacy day-command alias failed");
            var restored=FitnessConfig.load(commandConfig());h.assertTrue(restored.conditioning.timeMode==Conditioning.TimeMode.REAL&&restored.conditioning.graceDays==5&&!restored.conditioning.enabled,"Real-day mode was not saved correctly");
        }catch(Exception error){throw new AssertionError(error);}
        finally{restoreClockFixture(server,data,previous,before);}h.succeed();
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120) public static void worldTickClockChangesActualBonusesAndIgnoresDaylightChanges(GameTestHelper h){
        var server=h.getLevel().getServer();var data=FitnessData.get(server);var previous=data.ledger;byte[] before=null;long daylight=server.overworld().getDayTime();
        try{
            if(java.nio.file.Files.exists(commandConfig()))before=java.nio.file.Files.readAllBytes(commandConfig());
            data.ledger=new FitnessLedger();java.nio.file.Files.writeString(commandConfig(),FitnessLedger.JSON.toJson(new FitnessLedger.Settings()));FitnessService.reload(server);
            var dispatcher=server.getCommands().getDispatcher();var source=server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            dispatcher.execute("gym conditioning days minecraft 1",source);dispatcher.execute("gym conditioning on",source);
            var p=player(h);FitnessService.apply(p);var athlete=data.ledger.athletes.get(p.getUUID());athlete.experience.put(Exercise.BENCH,FitnessLedger.threshold(20));FitnessService.apply(p);
            long tick=server.overworld().getGameTime();h.assertTrue(athlete.conditionClock.minecraftObservedAt==tick,"Service did not observe the overworld game-time clock");
            server.overworld().setDayTime(daylight+240000);FitnessService.apply(p);
            h.assertTrue(data.ledger.condition(athlete,Exercise.BENCH)==100,"Changing daylight time charged inactivity");
            data.ledger.observeMinecraft(tick+2*Conditioning.MINECRAFT_DAY_TICKS);FitnessService.apply(p);
            h.assertTrue(data.ledger.condition(athlete,Exercise.BENCH)==95,"Configured Minecraft grace did not produce gradual decay");
            var damage=net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;double base=p.getAttribute(damage).getBaseValue();
            h.assertTrue(Math.abs(p.getAttributeValue(damage)-base*1.095)<.000001,"Minecraft-day condition did not affect the real damage modifier");
            dispatcher.execute("gym conditioning days minecraft 1",source);data.ledger.observeMinecraft(tick+3*Conditioning.MINECRAFT_DAY_TICKS);FitnessService.apply(p);
            h.assertTrue(data.ledger.condition(athlete,Exercise.BENCH)==90,"Repeated command reset the grace period");
            var copy=FitnessLedger.fromJson(FitnessLedger.JSON.toJson(data.ledger));h.assertTrue(copy.settings.conditioning.timeMode==Conditioning.TimeMode.MINECRAFT&&copy.condition(copy.athletes.get(p.getUUID()),Exercise.BENCH)==90,"Saved data lost the clock or condition");
            dispatcher.execute("gym conditioning days real 4",source);FitnessService.apply(p);
            h.assertTrue(data.ledger.condition(athlete,Exercise.BENCH)==90&&data.ledger.level(athlete,Exercise.BENCH)==20,"Clock switch lost condition or permanent progress");
        }catch(Exception error){throw new AssertionError(error);}
        finally{server.overworld().setDayTime(daylight);restoreClockFixture(server,data,previous,before);}h.succeed();
    }
    private static void restoreClockFixture(net.minecraft.server.MinecraftServer server,FitnessData data,FitnessLedger previous,byte[] bytes){
        data.ledger=previous;
        try{if(bytes==null)java.nio.file.Files.deleteIfExists(commandConfig());else java.nio.file.Files.write(commandConfig(),bytes);FitnessService.reload(server);}
        catch(java.io.IOException error){throw new AssertionError(error);}
    }
    @GameTest(template="realmfitness:empty",timeoutTicks=120)
    public static void occupiedBenchAcceptsOneSpotterAndKeepsTheLifterRiding(GameTestHelper h) throws ReflectiveOperationException{
        var anchor=position(h);
        place(h,Exercise.BENCH,Direction.NORTH);
        var lifter=player(h);
        var spotter=player(h);
        var replacement=player(h);
        // The Forge adapter checks the server's UUID index before starting a workout.
        var playerIndexField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        playerIndexField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var playerIndex=(Map<UUID,ServerPlayer>)playerIndexField.get(h.getLevel().getServer().getPlayerList());
        for(var participant:List.of(lifter,spotter,replacement))playerIndex.put(participant.getUUID(),participant);
        try{
            replacement.setGameMode(GameType.SURVIVAL);
            replacement.setPos(Vec3.atBottomCenterOf(anchor.south(3)));
            lifter.setGameMode(GameType.SURVIVAL);spotter.setGameMode(GameType.SURVIVAL);
            lifter.setPos(Vec3.atBottomCenterOf(anchor.north(2)));
            spotter.setPos(Vec3.atBottomCenterOf(anchor.south(3)));
            FitnessService.interact(lifter,anchor,false);
            h.assertTrue(lifter.getVehicle() instanceof GymMount,"Lifter failed to mount the bench");
            var mount=(GymMount)lifter.getVehicle();
            FitnessService.interact(spotter,anchor,false);
            h.assertTrue(spotter.getVehicle()==mount&&mount.getFirstPassenger()==lifter,"Second player did not join as spotter");
            h.assertTrue(spotter.distanceToSqr(mount.local(1,0,2.5))<.01,"Spotter did not stand behind the bench");
            FitnessService.interact(replacement,anchor,false);
            h.assertTrue(!replacement.isPassenger(),"A third rider joined an occupied bench");
            FitnessService.cancel(spotter);
            h.assertTrue(!spotter.isPassenger()&&lifter.getVehicle()==mount,"Leaving as spotter cancelled the lifter");
            FitnessService.interact(replacement,anchor,false);
            h.assertTrue(replacement.getVehicle()==mount,"The freed spotter place did not reopen");
            FitnessService.cancel(lifter);
            h.assertTrue(!lifter.isPassenger()&&!replacement.isPassenger()&&mount.isRemoved(),"Ending the set left a rider behind");
        }finally{
            try{
                FitnessService.cancel(replacement);
                FitnessService.cancel(spotter);
                FitnessService.cancel(lifter);
            }finally{
                for(var participant:List.of(lifter,spotter,replacement))playerIndex.remove(participant.getUUID());
            }
        }
        h.succeed();
    }
}
