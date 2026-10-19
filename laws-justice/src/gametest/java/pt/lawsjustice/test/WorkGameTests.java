package pt.lawsjustice.test;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.domain.WorkLedger;
import pt.lawsjustice.service.JusticeService;
import pt.lawsjustice.service.WorkService;

@GameTestHolder("lawsjustice")
@PrefixGameTestTemplate(false)
public final class WorkGameTests {
    private WorkGameTests(){}
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void allSevenWorkBlocksHaveRecipesAndCreativeSearchEntriesButBlankPouchesGrantNothing(GameTestHelper h){
        var tab=BuiltInRegistries.CREATIVE_MODE_TAB.stream().filter(t->BuiltInRegistries.CREATIVE_MODE_TAB.getKey(t).getNamespace().equals("lawsjustice")).findFirst().orElseThrow();tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS,true,h.getLevel().registryAccess()));
        for(var block:List.of(BlockRegistry.WORK_BOARD.get(),BlockRegistry.DELIVERY_BOX.get(),BlockRegistry.MINING_POST.get(),BlockRegistry.AGRICULTURAL_POST.get(),BlockRegistry.WORKSHOP_TABLE.get(),BlockRegistry.MAINTENANCE_POST.get(),BlockRegistry.WAREHOUSE_POST.get())){
            h.assertTrue(tab.getSearchTabDisplayItems().stream().anyMatch(s->s.is(block.asItem())),"Work furniture is missing from creative search");h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(BuiltInRegistries.BLOCK.getKey(block)).isPresent(),"Work furniture has no actual recipe");}
        h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(BuiltInRegistries.ITEM.getKey(BlockRegistry.WORK_BUNDLE.get())).isEmpty(),"Sealed work authorization must not be craftable");System.out.println("WORK_NATIVE_PASSED integration="+Boolean.getBoolean("justice.integration"));h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void onlyAdministratorsCanConfigureZonesAndNeitherCoordinatesNorWorkProgressComeFromTheClient(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            f.open(f.base.prisoner,f.board);var request=f.mutation(f.base.prisoner);request.addProperty("kind","MINING");request.addProperty("quantity",99);request.addProperty("minutes",99);request.addProperty("percent",100);f.act(f.base.prisoner,"work_policy",request);h.assertTrue(f.work().rules().maximumPercent()==50&&f.work().rules().rule("MINING").quantity()==1,"An ordinary prisoner changed work policy");
            request=f.mutation(f.base.prisoner);request.addProperty("zone",f.zone.toString());request.addProperty("kind","MINING");request.addProperty("output",999);f.act(f.base.prisoner,"work_accept",request);h.assertTrue(f.work().active(f.base.prisoner.getUUID())==null,"A client-supplied progress counter was accepted");
            f.accept();h.assertTrue(f.work().active(f.base.prisoner.getUUID()).output()==0&&f.pouch(),"Task acceptance did not issue a zero-progress server pouch");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void theActualPrisonSurveyorSelectionCreatesAContainedAdminWorkZone(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            var a=f.base.pos.offset(-2,0,-2);var b=f.base.pos.offset(-1,2,-1);f.base.select(a,b,f.base.pos.offset(-4,0,0));f.open(f.base.admin,f.board);
            var request=f.mutation(f.base.admin);request.addProperty("name","Surveyed native yard");request.addProperty("type","YARD");request.addProperty("prison",f.base.prisonId.toString());f.act(f.base.admin,"work_zone",request);
            // The existing fixture covers the whole prison: overlap must reject rather than silently resize it.
            h.assertTrue(f.work().zones().size()==1,"Overlapping zone selection was accepted");
            f.base.data.replaceWork(WorkLedger.empty());f.open(f.base.admin,f.board);request=f.mutation(f.base.admin);request.addProperty("name","Surveyed native yard");request.addProperty("type","YARD");request.addProperty("prison",f.base.prisonId.toString());f.act(f.base.admin,"work_zone",request);
            h.assertTrue(f.work().zones().size()==1&&f.work().zones().values().iterator().next().area().contains(JusticeFixture.point(a)),"The genuine server-owned survey did not register the contained zone");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void aFreePlayerACreativePrisonerAndAWrongPhysicalStationCannotAcceptWork(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            f.open(f.base.visitor,f.board);var request=f.mutation(f.base.visitor);request.addProperty("zone",f.zone.toString());request.addProperty("kind","MINING");f.act(f.base.visitor,"work_accept",request);h.assertTrue(f.work().jobs().isEmpty(),"A free player acquired prison work");
            f.base.prisoner.setGameMode(GameType.CREATIVE);f.open(f.base.prisoner,f.board);request=f.mutation(f.base.prisoner);request.addProperty("zone",f.zone.toString());request.addProperty("kind","MINING");f.act(f.base.prisoner,"work_accept",request);h.assertTrue(f.work().jobs().isEmpty(),"Creative mode created work progress");f.base.prisoner.setGameMode(GameType.SURVIVAL);
            f.open(f.base.prisoner,f.box);request=f.mutation(f.base.prisoner);request.addProperty("zone",f.zone.toString());request.addProperty("kind","MINING");f.act(f.base.prisoner,"work_accept",request);h.assertTrue(f.work().jobs().isEmpty(),"A Delivery Box granted a remote Work Board action");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void aGenuineMiningBreakConsumesOneWorldNodeAndStoresSealedMaterialsWithoutOrdinaryDrops(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            var node=f.resource(f.first,"STONE",Blocks.STONE.defaultBlockState());f.accept();var event=f.breakResource(f.first,Items.IRON_PICKAXE);
            h.assertTrue(event.isCanceled()&&h.getLevel().isEmptyBlock(f.first)&&f.work().active(f.base.prisoner.getUUID()).output()==1&&f.work().nodes().get(node.id()).state().equals("SPENT"),"A real server mining event did not consume the finite proof and seal its output");
            h.assertTrue(f.base.prisoner.getInventory().items.stream().noneMatch(s->s.is(Items.STONE)||s.is(Items.COBBLESTONE)),"Prison work also produced ordinary transferable resource items");
            h.getLevel().setBlockAndUpdate(f.first,Blocks.STONE.defaultBlockState());WorkService.placed(f.base.visitor,f.first);f.breakResource(f.first,Items.IRON_PICKAXE);h.assertTrue(f.work().active(f.base.prisoner.getUUID()).output()==1,"A replaced block counted twice");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void farmingCountsOnlyMatureCommissionedWheatAndDoesNotProduceExternalCropItems(GameTestHelper h){
        try(var f=new WorkFixture(h,"FARMING")){
            var crop=(CropBlock)Blocks.WHEAT;f.resource(f.first,"WHEAT",crop.getStateForAge(1));f.accept();f.breakResource(f.first,Items.AIR);h.assertTrue(f.work().active(f.base.prisoner.getUUID()).output()==0,"Immature wheat counted");
            h.getLevel().setBlockAndUpdate(f.first,crop.getStateForAge(crop.getMaxAge()));f.breakResource(f.first,Items.AIR);h.assertTrue(f.work().active(f.base.prisoner.getUUID()).output()==1&&!crop.isMaxAge(h.getLevel().getBlockState(f.first)),"A real mature harvest did not seal output and reset the crop");
            h.assertTrue(f.base.prisoner.getInventory().items.stream().noneMatch(s->s.is(Items.WHEAT)||s.is(Items.WHEAT_SEEDS)),"Work produced extra transferable wheat or seeds");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void anExternalCreativeStackAndForgedPouchCountersCannotCompleteADelivery(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            f.accept();var job=f.work().active(f.base.prisoner.getUUID());f.base.prisoner.getInventory().add(new ItemStack(Items.STONE,64));var forged=new ItemStack(BlockRegistry.WORK_BUNDLE.get());var tag=new CompoundTag();tag.putString("lawsjustice_work",job.id().toString());tag.putInt("output",999);forged.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));f.base.prisoner.getInventory().add(forged);f.deliver();
            h.assertTrue(f.base.sentence().reductionTicks()==0&&f.work().active(f.base.prisoner.getUUID()).output()==0,"Item display counters or external material stacks authorized work credit");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void aCompletedDeliveryCommitsItsExactWorkCreditAndSentenceReductionOnceInTheSameWorldFile(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            f.resource(f.first,"STONE",Blocks.STONE.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_PICKAXE);f.open(f.base.prisoner,f.box);var request=f.mutation(f.base.prisoner);f.act(f.base.prisoner,"work_deliver",request);
            h.assertTrue(f.base.sentence().reductionTicks()==3600&&f.work().active(f.base.prisoner.getUUID())==null&&!f.pouch(),"A valid sealed delivery did not consume its pouch and reduce the sentence");
            f.act(f.base.prisoner,"work_deliver",request);h.assertTrue(f.base.sentence().reductionTicks()==3600,"A replayed delivery granted another reduction");var loaded=JusticeData.readState(f.base.directory.resolve("laws_justice.dat"));h.assertTrue(loaded.work().state().equals(f.work().state())&&loaded.ledger().active().equals(f.base.data.ledger().active()),"Work and custody diverged in durable persistence");
            System.out.println("WORK_DELIVERY_PASSED");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void aFailedWorldWriteConsumesNeitherThePouchNorItsSentenceReduction(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            f.resource(f.first,"STONE",Blocks.STONE.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_PICKAXE);var before=f.work().state();var file=f.base.directory.resolve("laws_justice.dat");JusticeFixture.field(f.base.data,"file",f.base.directory);f.deliver();JusticeFixture.field(f.base.data,"file",file);
            h.assertTrue(f.base.sentence().reductionTicks()==0&&f.work().state().equals(before)&&f.pouch(),"A failed actual disk write partially consumed a delivery");f.deliver();h.assertTrue(f.base.sentence().reductionTicks()==3600,"The unconsumed delivery could not be retried after storage recovered");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void theDefaultHalfSentenceLimitClampsTheActualAwardAndPreventsASecondAcceptedTask(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            var old=f.base.sentence();var shorter=new JusticeLedger.Sentence(old.id(),old.player(),old.playerName(),old.prison(),old.cell(),old.authority(),old.authorityName(),old.reason(),old.issuedAt(),1200,0,0,0);f.base.data.replace(JusticeLedger.restore(f.base.data.ledger().prisons(),java.util.Map.of(old.player(),shorter),List.of()));
            f.resource(f.first,"STONE",Blocks.STONE.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_PICKAXE);f.deliver();h.assertTrue(f.base.sentence().reductionTicks()==600&&f.work().credits().get(old.id()).ticks()==600,"A large reward exceeded half of the original sentence");
            f.open(f.base.prisoner,f.board);var request=f.mutation(f.base.prisoner);request.addProperty("zone",f.zone.toString());request.addProperty("kind","MINING");f.act(f.base.prisoner,"work_accept",request);h.assertTrue(f.work().active(old.player())==null,"A player beyond the cap accepted another reward task");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void workThatCompletesTheRemainingSentenceQueuesAndDeliversTheNormalReleaseExactlyOnce(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            f.base.data.replace(f.base.data.ledger().advance(f.base.prisoner.getUUID(),11990,System.currentTimeMillis()));f.resource(f.first,"STONE",Blocks.STONE.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_PICKAXE);f.deliver();
            h.assertTrue(f.base.sentence()==null&&f.base.data.pendingReleases().containsKey(f.base.prisoner.getUUID()),"Work completion did not atomically reserve the ordinary release");JusticeService.login(f.base.prisoner);h.assertTrue(f.base.data.pendingReleases().isEmpty()&&!f.base.prison().area().contains(JusticeFixture.point(f.base.prisoner.blockPosition())),"The earned release did not use the normal release mechanism");h.assertTrue(f.base.data.ledger().history().stream().filter(d->d.action().equals("COMPLETED")).count()==1,"Work completion wrote more than one completion decision");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void endingCustodyInvalidatesAcceptedWorkAndPreservesItsCancellationAfterReload(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            f.accept();var id=f.work().active(f.base.prisoner.getUUID()).id();f.base.data.replace(f.base.data.ledger().advance(f.base.prisoner.getUUID(),Long.MAX_VALUE,System.currentTimeMillis()));h.assertTrue(f.work().active(f.base.prisoner.getUUID())==null&&f.work().jobs().get(id).status().equals("CANCELLED"),"A task survived the end of its sentence");h.assertTrue(JusticeData.readState(f.base.directory.resolve("laws_justice.dat")).work().jobs().get(id).status().equals("CANCELLED"),"Cancelled job authorization returned after reload");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void resourceReinspectionCannotRestoreSpentNodesUntilASurvivalAdministratorActuallyRefillsThem(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            var node=f.resource(f.first,"STONE",Blocks.STONE.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_PICKAXE);f.base.data.replaceWork(f.work().cancel(f.base.prisoner.getUUID(),System.currentTimeMillis()));h.getLevel().setBlockAndUpdate(f.first,Blocks.STONE.defaultBlockState());WorkService.placed(f.base.visitor,f.first);
            f.open(f.base.admin,f.board);var request=f.mutation(f.base.admin);request.addProperty("zone",f.zone.toString());f.act(f.base.admin,"work_commission",request);h.assertTrue(f.work().nodes().get(node.id()).state().equals("SPENT"),"Reinspection reset a player-replaced spent node");
            f.base.admin.setGameMode(GameType.CREATIVE);WorkService.placed(f.base.admin,f.first);h.assertTrue(f.work().nodes().get(node.id()).state().equals("SPENT"),"Creative replacement created a valid resource");f.base.admin.setGameMode(GameType.SURVIVAL);WorkService.placed(f.base.admin,f.first);f.open(f.base.admin,f.board);request=f.mutation(f.base.admin);request.addProperty("zone",f.zone.toString());f.act(f.base.admin,"work_commission",request);h.assertTrue(f.work().nodes().get(node.id()).state().equals("READY"),"A deliberate survival-administrator refill could not be commissioned");
        }h.succeed();
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void custodyCannotGainAnUnbackedReductionAndAWorkVerdictCannotCommitWithoutItsCustodyCredit(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            JusticeFixture.rejected(h,()->f.base.data.replace(f.base.data.ledger().reduce(f.base.prisoner.getUUID(),10,50,System.currentTimeMillis())));h.assertTrue(f.base.sentence().reductionTicks()==0,"An unbacked reduction reached custody");
            f.resource(f.first,"STONE",Blocks.STONE.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_PICKAXE);JusticeFixture.rejected(h,()->f.base.data.replaceWork(f.work().finish(f.base.prisoner.getUUID(),f.base.sentence().id(),10,System.currentTimeMillis())));h.assertTrue(f.work().active(f.base.prisoner.getUUID())!=null&&f.work().credits().isEmpty(),"A work credit committed separately from its sentence reduction");
        }h.succeed();
    }
    @GameTest(batch="work_timed",template="justice_empty",timeoutTicks=180)
    public static void realOnlinePostTicksCompleteTimedWorkshopProductionWhileNormalSentenceTimeKeepsRunning(GameTestHelper h){
        var f=new WorkFixture(h,"CARPENTRY");
        try{var node=f.resource(f.first,"LOG",Blocks.OAK_LOG.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_AXE);f.open(f.base.prisoner,f.post);f.act(f.base.prisoner,"work_produce",f.mutation(f.base.prisoner));long served=f.base.sentence().servedTicks();h.assertTrue(f.work().active(f.base.prisoner.getUUID()).output()==0&&f.work().active(f.base.prisoner.getUUID()).raw()==1,"Production consumed inputs or finished before its real server ticks");
            h.runAfterDelay(45,()->{try{h.assertTrue(f.work().active(f.base.prisoner.getUUID()).output()==1&&f.work().active(f.base.prisoner.getUUID()).raw()==0,"Real online server ticks did not finish the workshop batch");h.assertTrue(f.base.sentence().servedTicks()>served,"Normal prison time stopped during work");f.deliver();h.assertTrue(f.base.sentence().reductionTicks()==3600,"The timed output could not be delivered");System.out.println("WORK_TIMED_PASSED");f.close();h.succeed();}catch(Throwable failure){f.close();throw failure;}});
        }catch(Throwable failure){f.close();throw failure;}
    }

    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void transportRequiresTwoActualWarehousePostsAndDeliveryInTheSameRegisteredZone(GameTestHelper h){
        try(var f=new WorkFixture(h,"TRANSPORT")){
            var source=f.base.pos.offset(-1,0,1);var destination=f.base.pos.offset(5,0,5);h.getLevel().setBlockAndUpdate(source,BlockRegistry.WAREHOUSE_POST.get().defaultBlockState());h.getLevel().setBlockAndUpdate(destination,BlockRegistry.WAREHOUSE_POST.get().defaultBlockState());f.accept();
            f.open(f.base.prisoner,source);f.act(f.base.prisoner,"work_cargo",f.mutation(f.base.prisoner));h.assertTrue(f.work().active(f.base.prisoner.getUUID()).output()==0,"Picking up cargo completed the transport without a route");
            f.act(f.base.prisoner,"work_cargo",f.mutation(f.base.prisoner));h.assertTrue(f.work().active(f.base.prisoner.getUUID()).route().size()==1,"The same post counted as both ends of the route");
            f.open(f.base.prisoner,destination);f.act(f.base.prisoner,"work_cargo",f.mutation(f.base.prisoner));h.assertTrue(f.work().active(f.base.prisoner.getUUID()).output()==1,"The real second warehouse post did not validate the route");f.deliver();h.assertTrue(f.base.sentence().reductionTicks()==3600,"Sealed cargo could not be delivered after the completed physical route");
        }h.succeed();
    }

    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void aDeliveryBoxInAnotherZoneCannotRedeemAValidPouch(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            var small=JusticeLedger.Area.between(f.base.dimension,JusticeFixture.point(f.base.pos.offset(-2,0,-2)),JusticeFixture.point(f.base.pos.offset(3,5,3)));
            var tasks=new java.util.ArrayList<>(f.work().rules().tasks());f.base.data.replaceWork(WorkLedger.empty().configure(f.work().rules()).putZone(new WorkLedger.Zone(f.zone,f.base.prisonId,"Small mine","MINE",small,0)));
            f.resource(f.first,"STONE",Blocks.STONE.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_PICKAXE);f.deliver();h.assertTrue(f.base.sentence().reductionTicks()==0&&f.work().active(f.base.prisoner.getUUID())!=null,"A box outside the task's zone redeemed its pouch");
        }h.succeed();
    }

    @GameTest(batch="work_smithing",template="justice_empty",timeoutTicks=200)
    public static void smithingUsesActualOreAndCoalBreaksAndWaitsForRealOnlineProductionTicks(GameTestHelper h){
        var f=new WorkFixture(h,"SMITHING");
        try{f.resource(f.first,"IRON",Blocks.IRON_ORE.defaultBlockState());f.resource(f.second,"COAL",Blocks.COAL_ORE.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_PICKAXE);
            h.runAfterDelay(4,()->{try{f.breakResource(f.second,Items.IRON_PICKAXE);h.assertTrue(f.work().active(f.base.prisoner.getUUID()).raw()==1&&f.work().active(f.base.prisoner.getUUID()).fuel()==8,"Real ore and coal breaks did not reserve finite smithing inputs");f.open(f.base.prisoner,f.post);f.act(f.base.prisoner,"work_produce",f.mutation(f.base.prisoner));
                h.runAfterDelay(85,()->{try{var j=f.work().active(f.base.prisoner.getUUID());h.assertTrue(j.output()==1&&j.raw()==0&&j.fuel()==7,"Timed smithing did not consume one sealed ore and one fuel unit");f.deliver();h.assertTrue(f.base.sentence().reductionTicks()==3600,"A genuinely smelted ingot did not complete its sealed delivery");System.out.println("WORK_SMITHING_PASSED");f.close();h.succeed();}catch(Throwable failure){f.close();throw failure;}});
            }catch(Throwable failure){f.close();throw failure;}});
        }catch(Throwable failure){f.close();throw failure;}
    }

    @GameTest(batch="work_cleaning",template="justice_empty",timeoutTicks=160)
    public static void anActualBrushInteractionCleansOneCommissionedPointOverRealServerTicks(GameTestHelper h){
        var f=new WorkFixture(h,"CLEANING");
        try{f.resource(f.first,"CLEAN",Blocks.STONE_BRICKS.defaultBlockState());f.accept();f.base.prisoner.setPos(f.first.getX()+.5,f.first.getY()+1,f.first.getZ()+1.5);f.base.prisoner.getInventory().selected=8;f.base.prisoner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BRUSH));
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(f.first),net.minecraft.core.Direction.UP,f.first,false);var interaction=new PlayerInteractEvent.RightClickBlock(f.base.prisoner,InteractionHand.MAIN_HAND,f.first,hit);NeoForge.EVENT_BUS.post(interaction);h.assertTrue(interaction.isCanceled()&&f.work().active(f.base.prisoner.getUUID()).output()==0,"The maintenance interaction bypassed timed server work");
            h.runAfterDelay(45,()->{try{h.assertTrue(f.work().active(f.base.prisoner.getUUID()).output()==1&&h.getLevel().getBlockState(f.first).is(Blocks.STONE_BRICKS),"The actual brush action failed to clean a point while preserving its floor");h.assertTrue(f.base.prisoner.getMainHandItem().getDamageValue()==1,"Cleaning did not consume finite brush durability");f.deliver();h.assertTrue(f.base.sentence().reductionTicks()==3600,"The cleaned point could not be delivered");System.out.println("WORK_CLEANING_PASSED");f.close();h.succeed();}catch(Throwable failure){f.close();throw failure;}});
        }catch(Throwable failure){f.close();throw failure;}
    }

    @GameTest(batch="work_interrupted",template="justice_empty",timeoutTicks=100)
    public static void walkingAwayFromAnActiveWorkshopStopsProductionWithoutConsumingInputs(GameTestHelper h){
        var f=new WorkFixture(h,"CARPENTRY");
        try{f.resource(f.first,"LOG",Blocks.OAK_LOG.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_AXE);f.open(f.base.prisoner,f.post);f.act(f.base.prisoner,"work_produce",f.mutation(f.base.prisoner));f.base.prisoner.setPos(f.base.pos.getX()-.5,f.base.pos.getY(),f.base.pos.getZ()-.5);
            h.runAfterDelay(5,()->{try{var job=f.work().active(f.base.prisoner.getUUID());h.assertTrue(job.output()==0&&job.raw()==1,"Interrupted production consumed its raw material or awarded output");f.open(f.base.prisoner,f.post);h.assertTrue(f.view(f.base.prisoner).get("processingRemaining").getAsInt()==0,"A remote workshop continued processing after the prisoner walked away");f.close();h.succeed();}catch(Throwable failure){f.close();throw failure;}});
        }catch(Throwable failure){f.close();throw failure;}
    }
    @GameTest(template="justice_empty",timeoutTicks=100)
    public static void malformedDurableWorkCreditsAndResourcesFailClosedWithoutResettingValidProgress(GameTestHelper h){
        try(var f=new WorkFixture(h,"MINING")){
            f.resource(f.first,"STONE",Blocks.STONE.defaultBlockState());f.accept();f.breakResource(f.first,Items.IRON_PICKAXE);f.deliver();var file=f.base.directory.resolve("laws_justice.dat");
            try{var original=net.minecraft.nbt.NbtIo.readCompressed(file,net.minecraft.nbt.NbtAccounter.create(16_777_216));
                for(String mutation:List.of("credit","quantity","node")){
                    var tag=original.copy();var json=com.google.gson.JsonParser.parseString(new String(tag.getByteArray("ledger"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();var work=json.getAsJsonObject("work");
                    if(mutation.equals("credit"))work.getAsJsonObject("credits").entrySet().iterator().next().getValue().getAsJsonObject().addProperty("ticks",1);
                    else if(mutation.equals("quantity"))work.getAsJsonObject("jobs").entrySet().iterator().next().getValue().getAsJsonObject().addProperty("quantity",0);
                    else work.getAsJsonObject("nodes").entrySet().iterator().next().getValue().getAsJsonObject().addProperty("zone",UUID.randomUUID().toString());
                    tag.putByteArray("ledger",json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));net.minecraft.nbt.NbtIo.writeCompressed(tag,file);JusticeFixture.rejected(h,()->JusticeData.readState(file));
                }
                net.minecraft.nbt.NbtIo.writeCompressed(original,file);h.assertTrue(JusticeData.readState(file).work().state().equals(f.work().state()),"Rejecting corrupt work reset the original valid progress");
            }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        }h.succeed();
    }

}
