package pt.contracts.test;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import pt.contracts.*;
import pt.contracts.compat.ChroniclesBridge;
import pt.contracts.daily.*;
import pt.contracts.daily.DailyLedger.*;
import pt.contracts.daily.DailyRules.*;

@GameTestHolder("contracts_daily")
@PrefixGameTestTemplate(false)
public final class DailyGameTests {
    private static void rejected(GameTestHelper h,Runnable r){boolean rejected=false;try{r.run();}catch(RuntimeException expected){rejected=true;}h.assertTrue(rejected,"Unsafe daily quest action was accepted");}
    private static DailyData data(ServerPlayer p){return DailyData.get(p.getServer());}
    private static Reward finish(ServerPlayer p,Category category){var q=DailyActors.quest(p,category);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());DailyService.activity(p,category,q.template.validation(),t->t.id().equals(q.template.id()),q.template.quantity());return data(p).ledger.rewards.get(q.id);}
    private static long emeralds(ServerPlayer p){return p.getInventory().items.stream().filter(s->s.is(Items.EMERALD)).mapToLong(ItemStack::getCount).sum();}
    private static CompoundTag intent(ServerPlayer p,Reward r){CompoundTag tag=new CompoundTag();tag.putUUID("id",r.id);tag.putUUID("player",p.getUUID());tag.putBoolean("delivery",false);tag.put("inventoryBefore",DailyRewardJournal.inventory(p));tag.put("inventoryAfter",DailyRewardJournal.inventory(p));if(r.currency.equals("CHRONICLES")){tag.put("profileBefore",DailyWallet.current(p));tag.put("profileAfter",DailyWallet.awarded(p,r));}return tag;}
    private static Quest mining(ServerPlayer p,GameTestHelper h,BlockPos pos){var q=DailyActors.quest(p,Category.MINING);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);String key=DailyEvents.position(p.level(),pos);data(p).ledger.placed.remove(key);data(p).ledger.consumed.remove(key);h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);return q;}

    @GameTest(template="daily_empty",batch="daily_finance",timeoutTicks=200)
    public static void runtimeLoadsRequestedCompanionsAndFiftySevenPackagedTemplates(GameTestHelper h){
        boolean integrated=Boolean.getBoolean("daily.integration");h.assertTrue(ChroniclesBridge.loaded()==integrated,"Wrong native runtime mode");
        if(integrated)for(String id:List.of("cronicasrp","origins","jupiter","realmcouncil","kingdomtreasury","realmnotifications","realmrewards","realmmarkets","realmeconomy"))h.assertTrue(ModList.get().isLoaded(id),"Missing actual mod: "+id);
        h.assertTrue(DailyCatalog.all(DailyData.get(h.getLevel().getServer()).ledger).size()>=57,"Packaged daily templates were not discovered");
        System.out.println("DAILY_RUNTIME_PASSED integration="+integrated);h.succeed();
    }
    @GameTest(template="daily_empty",batch="daily_finance",timeoutTicks=200)
    public static void actualWalletOrEmeraldPaymentIsSavedOnceAndPreservesTheProfile(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();CompoundTag before=ChroniclesBridge.loaded()?DailyWallet.current(p):null;long balance=ChroniclesBridge.loaded()?ChroniclesBridge.balance(p):emeralds(p);
        Reward reward=finish(p,Category.MINING);h.assertTrue(reward.paid,"Reward was not confirmed");long after=ChroniclesBridge.loaded()?ChroniclesBridge.balance(p):emeralds(p);
        h.assertTrue(after-balance==(ChroniclesBridge.loaded()?reward.net:reward.net/100),"Actual wallet/inventory received the wrong reward");
        DailyService.retry(p);h.assertTrue((ChroniclesBridge.loaded()?ChroniclesBridge.balance(p):emeralds(p))==after,"Retry paid twice");
        CompoundTag proof=new CompoundTag();proof.putUUID("id",reward.id);proof.putUUID("player",p.getUUID());proof.putBoolean("delivery",false);h.assertTrue(DailyRewardJournal.savedReceipt(p,proof),"Actual player file lost the reward receipt");
        if(before!=null){CompoundTag afterProfile=DailyWallet.current(p);before.remove("balance");before.remove("progress");afterProfile.remove("balance");afterProfile.remove("progress");h.assertTrue(before.equals(afterProfile),"Daily payment changed unrelated character data");}
        System.out.println("DAILY_WALLET_PASSED");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_finance",timeoutTicks=200)
    public static void deliveryConsumesTheMaterialsAndCannotBeSubmittedAgain(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();var q=DailyActors.quest(p,Category.DELIVERY);DailyActors.board(p,h);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());p.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,3));
        DailyService.deliver(p,q.id);h.assertTrue(p.getInventory().items.stream().filter(s->s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum()==1,"Delivery did not consume exactly two iron ingots");
        Reward reward=data(p).ledger.rewards.get(q.id);h.assertTrue(reward.paid&&reward.deliveryConfirmed,"Delivery or payment is unconfirmed");long before=ChroniclesBridge.loaded()?ChroniclesBridge.balance(p):emeralds(p);rejected(h,()->DailyService.deliver(p,q.id));h.assertTrue((ChroniclesBridge.loaded()?ChroniclesBridge.balance(p):emeralds(p))==before,"Repeated delivery duplicated a reward");System.out.println("DAILY_DELIVERY_PASSED");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_finance",timeoutTicks=200)
    public static void insufficientDeliveryDoesNotRemoveItemsOrCreateAReceipt(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();var q=DailyActors.quest(p,Category.DELIVERY);DailyActors.board(p,h);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());p.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT));rejected(h,()->DailyService.deliver(p,q.id));h.assertTrue(p.getInventory().getItem(0).getCount()==1&&q.progress==0&&!data(p).ledger.rewards.containsKey(q.id),"Failed delivery consumed resources or minted credit");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_finance",timeoutTicks=200)
    public static void noCharacterOrFullInventoryRetainsTheExactPendingCredit(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();if(ChroniclesBridge.loaded()){DailyActors.profile(p,0);try{var tag=DailyWallet.current(p);tag.putBoolean("created",false);DailyWallet.install(p,DailyWallet.current(p),tag);}catch(RuntimeException invalid){throw new IllegalStateException(invalid);}}
        else for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        Reward reward=finish(p,Category.MINING);h.assertTrue(!reward.paid&&reward.net>0,"Unavailable destination lost or paid the reward");
        if(ChroniclesBridge.loaded())DailyActors.profile(p,0);else p.getInventory().setItem(1,ItemStack.EMPTY);
        DailyService.retry(p);h.assertTrue(reward.paid,"Reserved payment did not resume");h.assertTrue((ChroniclesBridge.loaded()?ChroniclesBridge.balance(p):emeralds(p))==(ChroniclesBridge.loaded()?reward.net:reward.net/100),"Pending payment changed value");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_finance",timeoutTicks=200)
    public static void completionCreatesNoDuplicateGenericHudNotification(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();DailyActors.round(p);p.packets.clear();p.messages.clear();Reward reward=finish(p,Category.MINING);h.assertTrue(reward.paid&&p.finales()==1,"Confirmed reward did not produce one cinematic payload");
        long generic=p.packets.stream().filter(packet->packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket c&&c.payload().type().id().getNamespace().equals("realmnotifications")).count();h.assertTrue(generic==0,"Completion also produced a duplicate generic HUD notice");DailyService.acknowledge(p,reward.id);p.packets.clear();DailyService.sendFinales(p);h.assertTrue(p.finales()==0,"Acknowledged cinematic was sent again");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_finance",timeoutTicks=200)
    public static void allFiveProduceTheirIndividualPacketsBeforeASingleFinale(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();Round round=DailyActors.round(p);for(var q:round.quests){data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());data(p).ledger.progress(p.getUUID(),q.id,q.template.quantity(),System.currentTimeMillis());DailyService.complete(p,q.id,false);}
        DailyService.retry(p);h.assertTrue(round.specialConfirmed&&round.quests.stream().allMatch(q->q.state==State.PAID),"All-five completion was not confirmed");
        var payloads=p.packets.stream().filter(packet->packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket c&&c.payload()instanceof DailyPackets.Finale).map(packet->(DailyPackets.Finale)((net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket)packet).payload()).toList();
        h.assertTrue(payloads.size()==6,"Expected five normal and one special payload");h.assertTrue(JsonParser.parseString(payloads.getLast().json()).getAsJsonObject().get("difficulty").getAsString().equals("ALL"),"Special celebration preceded individual quests");
        for(var payload:payloads)DailyService.acknowledge(p,UUID.fromString(JsonParser.parseString(payload.json()).getAsJsonObject().get("id").getAsString()));p.packets.clear();DailyService.retry(p);h.assertTrue(p.finales()==0,"Finale was emitted twice");System.out.println("DAILY_FINALE_PASSED");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_restart",timeoutTicks=200)
    public static void serializedRoundPendingReceiptsAndClockGuardSurviveReload(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();Round round=DailyActors.round(p);Quest q=round.quests.getFirst();data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());data(p).ledger.progress(p.getUUID(),q.id,q.template.quantity(),System.currentTimeMillis());Reward reward=DailyService.complete(p,q.id,false);
        DailyData loaded=DailyData.load(data(p).save(new CompoundTag(),p.registryAccess()),p.registryAccess());h.assertTrue(!loaded.ledger.rewards.get(reward.id).paid&&loaded.ledger.account(p.getUUID()).round.quests.getFirst().id.equals(q.id),"Reload changed the pending credit or daily identity");
        h.assertTrue(!loaded.ledger.needsRound(p.getUUID(),System.currentTimeMillis()-86400000),"A rewound clock rerolled the saved daily set");System.out.println("DAILY_PERSISTENCE_PASSED");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_restart",timeoutTicks=200)
    public static void preparedWalletJournalCanResumeAfterAnActualDataStorageReload(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();if(!ChroniclesBridge.loaded()){h.succeed();return;}var q=DailyActors.quest(p,Category.MINING);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());data(p).ledger.progress(p.getUUID(),q.id,1,System.currentTimeMillis());Reward reward=DailyService.complete(p,q.id,false);long before=ChroniclesBridge.balance(p);data(p).prepare(p.getServer(),intent(p,reward));
        DailyData loaded=DailyData.load(data(p).save(new CompoundTag(),p.registryAccess()),p.registryAccess());p.getServer().overworld().getDataStorage().set(DailyData.KEY,loaded);DailyRewardJournal.recover(p);h.assertTrue(ChroniclesBridge.balance(p)==before+reward.net&&loaded.ledger.rewards.get(reward.id).paid,"Reloaded prepared intent did not settle exactly once");DailyService.retry(p);h.assertTrue(ChroniclesBridge.balance(p)==before+reward.net,"Recovery replay minted money");System.out.println("DAILY_RECOVERY_PASSED");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_restart",timeoutTicks=200)
    public static void preparedReceiptWithoutItsJournalIsRejected(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();var q=DailyActors.quest(p,Category.MINING);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());data(p).ledger.progress(p.getUUID(),q.id,1,System.currentTimeMillis());Reward r=DailyService.complete(p,q.id,false);r.prepared=true;CompoundTag tag=data(p).save(new CompoundTag(),p.registryAccess());rejected(h,()->DailyData.load(tag,p.registryAccess()));r.prepared=false;
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_authority",timeoutTicks=200)
    public static void acceptingWithoutAPhysicalSessionOrAfterLeavingTheBoardFails(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();var q=DailyActors.quest(p,Category.MINING);JsonObject input=new JsonObject();input.addProperty("id",q.id.toString());input.addProperty("reward",999999999);DailyService.request(p,"accept",input.toString());h.assertTrue(q.state==State.AVAILABLE,"Remote accept without a board session succeeded");
        DailyActors.board(p,h);p.setPos(p.getX()+30,p.getY(),p.getZ());DailyService.request(p,"accept",input.toString());h.assertTrue(q.state==State.AVAILABLE,"Out-of-range accept succeeded");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_authority",timeoutTicks=200)
    public static void creativePlayersCannotAcceptProgressOrDeliverDailyQuests(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();var q=DailyActors.quest(p,Category.MINING);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());p.setGameMode(GameType.CREATIVE);DailyService.activity(p,Category.MINING,Validation.NATURAL_BLOCK,t->true,100);h.assertTrue(q.progress==0,"Creative actions counted toward progress");DailyActors.board(p,h);rejected(h,()->DailyService.deliver(p,q.id));
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_authority",timeoutTicks=200)
    public static void ordinaryPlayersCannotChangeFiscalLimitsOrTemplates(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();JsonObject input=new JsonObject();input.addProperty("revision",data(p).ledger.revision);input.add("settings",DailyLedger.JSON.toJsonTree(data(p).ledger.settings));rejected(h,()->DailyService.adminSettings(p,input));rejected(h,()->DailyService.adminTemplate(p,input));rejected(h,()->DailyService.govern(p,input));
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_catalog",timeoutTicks=200)
    public static void unsupportedRegistriesModsAndAlreadySeenBiomesAreExcluded(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();DailyActors.round(p);var ledger=data(p).ledger;var missing=new Template("daily_fixture:missing","Missing",Category.MINING,Difficulty.EASY,"missing:ore",1,-1,10,Set.of("no_such_daily_mod"),Set.of(),Set.of(),true,null);ledger.overrides.put(missing.id(),missing);
        var available=DailyCatalog.available(p,ledger);h.assertTrue(available.stream().noneMatch(t->t.id().equals(missing.id())),"Uninstalled content generated an objective");
        var seen=ledger.account(p.getUUID()).biomes;h.assertTrue(available.stream().filter(t->t.validation()==Validation.NEW_BIOME).noneMatch(t->seen.contains(t.target())),"A discovered biome became an impossible quest");ledger.overrides.remove(missing.id());System.out.println("DAILY_CATALOG_PASSED");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_events",timeoutTicks=200)
    public static void aRealSurvivalBlockBreakCompletesANaturalMiningQuest(GameTestHelper h){var f=new DailyActors(h);var p=f.actor();BlockPos pos=h.absolutePos(new BlockPos(1,2,1));Quest q=mining(p,h,pos);h.assertTrue(p.gameMode.destroyBlock(pos),"Native survival mining failed");h.runAfterDelay(2,()->{try{h.assertTrue(q.progress==1&&data(p).ledger.rewards.get(q.id).paid,"Actual mining event did not produce one confirmed reward");System.out.println("DAILY_MINING_EVENT_PASSED");h.succeed();}finally{f.close();}});}
    @GameTest(template="daily_empty",batch="daily_events",timeoutTicks=200)
    public static void aPlayerPlacedStoneCannotBeFarmedForQuestProgress(GameTestHelper h){var f=new DailyActors(h);var p=f.actor();BlockPos pos=h.absolutePos(new BlockPos(1,2,1));Quest q=mining(p,h,pos);h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE));
        var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),net.minecraft.core.Direction.UP,pos.below(),false);h.assertTrue(p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Real block placement failed");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));p.gameMode.destroyBlock(pos);
        h.runAfterDelay(2,()->{try{h.assertTrue(q.progress==0&&!data(p).ledger.rewards.containsKey(q.id),"Placed/broken stone granted a quest reward");h.succeed();}finally{f.close();}});}
    @GameTest(template="daily_empty",batch="daily_events",timeoutTicks=200)
    public static void anUnchangedOrCancelledBlockBreakCannotCount(GameTestHelper h){var f=new DailyActors(h);var p=f.actor();BlockPos pos=h.absolutePos(new BlockPos(1,2,1));Quest q=mining(p,h,pos);DailyEvents.broken(new BlockEvent.BreakEvent(h.getLevel(),pos,Blocks.STONE.defaultBlockState(),p));h.runAfterDelay(2,()->{try{h.assertTrue(q.progress==0,"Unconfirmed block break counted before the world changed");h.succeed();}finally{f.close();}});}
    @GameTest(template="daily_empty",batch="daily_events",timeoutTicks=200)
    public static void harvestingAMatureCropCountsButAnUnripeCropDoesNot(GameTestHelper h){var f=new DailyActors(h);var p=f.actor();Quest q=DailyActors.quest(p,Category.FARMING);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());BlockPos young=h.absolutePos(new BlockPos(1,2,1)),ripe=h.absolutePos(new BlockPos(2,2,1));h.getLevel().setBlock(young,Blocks.WHEAT.defaultBlockState(),3);p.gameMode.destroyBlock(young);h.getLevel().setBlock(ripe,((CropBlock)Blocks.WHEAT).getStateForAge(7),3);p.gameMode.destroyBlock(ripe);h.runAfterDelay(2,()->{try{h.assertTrue(q.progress==1&&data(p).ledger.rewards.get(q.id).paid,"Mature crop validation failed");System.out.println("DAILY_FARM_EVENT_PASSED");h.succeed();}finally{f.close();}});}
    @GameTest(template="daily_empty",batch="daily_events",timeoutTicks=200)
    public static void naturalSpawnAndDirectPlayerKillCountWhileSpawnerAndAutomaticKillsDoNot(GameTestHelper h){var f=new DailyActors(h);var p=f.actor();Quest q=DailyActors.quest(p,Category.COMBAT);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());BlockPos pos=p.blockPosition().offset(2,0,0);
        var spawner=net.minecraft.world.entity.EntityType.ZOMBIE.spawn(h.getLevel(),pos,MobSpawnType.SPAWNER);var natural=net.minecraft.world.entity.EntityType.ZOMBIE.spawn(h.getLevel(),pos.offset(1,0,0),MobSpawnType.NATURAL);var automatic=net.minecraft.world.entity.EntityType.ZOMBIE.spawn(h.getLevel(),pos.offset(2,0,0),MobSpawnType.NATURAL);
        h.assertTrue(spawner!=null&&natural!=null&&automatic!=null,"Native mob creation failed");h.assertTrue(!DailyEvents.natural(spawner)&&DailyEvents.natural(natural),"Spawn reason was not recorded authoritatively");for(var mob:List.of(spawner,natural,automatic)){mob.setNoAi(true);mob.setHealth(1);}
        spawner.hurt(p.damageSources().playerAttack(p),50);automatic.hurt(p.damageSources().fall(),50);natural.hurt(p.damageSources().playerAttack(p),50);
        h.runAfterDelay(2,()->{try{h.assertTrue(q.progress==1&&data(p).ledger.rewards.get(q.id).paid,"Combat counted artificial/automatic kills or missed a legitimate kill");System.out.println("DAILY_COMBAT_EVENT_PASSED");h.succeed();}finally{f.close();}});
    }
    @GameTest(template="daily_empty",batch="daily_events",timeoutTicks=200)
    public static void vanillaRodRetrievalCountsActualLootAndNeverInventoryPresence(GameTestHelper h){var f=new DailyActors(h);var p=f.actor();Quest q=DailyActors.quest(p,Category.FISHING);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());p.getInventory().setItem(0,new ItemStack(Items.COD,64));h.assertTrue(q.progress==0,"Inventory fish granted progress without fishing");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.FISHING_ROD));
        try{var nibble=FishingHook.class.getDeclaredField("nibble");nibble.setAccessible(true);for(int i=0;i<12;i++){FishingHook hook=new FishingHook(p,h.getLevel(),0,0);hook.setPos(p.getX()+1,p.getY(),p.getZ());h.getLevel().addFreshEntity(hook);nibble.setInt(hook,10);hook.retrieve(p.getMainHandItem());}}catch(Exception failure){f.close();throw new IllegalStateException(failure);}
        h.runAfterDelay(2,()->{try{h.assertTrue(q.progress==1&&data(p).ledger.rewards.get(q.id).paid,"Actual fishing retrieval did not count its loot");System.out.println("DAILY_FISHING_EVENT_PASSED");h.succeed();}finally{f.close();}});
    }
    @GameTest(template="daily_empty",batch="daily_catalog",timeoutTicks=200)
    public static void persistentDiscoveryPreventsAlreadyVisitedBiomeObjectives(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();DailyActors.round(p);DailyEvents.discover(p);String current=p.level().getBiome(p.blockPosition()).unwrapKey().orElseThrow().location().toString();h.assertTrue(data(p).ledger.account(p.getUUID()).biomes.contains(current),"Server biome discovery was not persisted");DailyData loaded=DailyData.load(data(p).save(new CompoundTag(),p.registryAccess()),p.registryAccess());h.assertTrue(loaded.ledger.account(p.getUUID()).biomes.contains(current),"Discovery was forgotten across reload");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_exploration",timeoutTicks=200)
    public static void anActualServerBiomeChangeIsObservedOnceAfterAcceptance(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();Quest q=DailyActors.quest(p,Category.FARMING);q.template=new Template(q.template.id(),"Native biome",Category.EXPLORATION,Difficulty.EASY,"minecraft:desert",1,-1,1,Set.of(),Set.of(),Set.of(),true,Validation.NEW_BIOME);var account=data(p).ledger.account(p.getUUID());account.biomes.remove("minecraft:desert");data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());BlockPos pos=p.blockPosition();
        for(int cx=(pos.getX()-8)>>4;cx<=((pos.getX()+8)>>4);cx++)for(int cz=(pos.getZ()-8)>>4;cz<=((pos.getZ()+8)>>4);cz++)p.serverLevel().getChunk(cx,cz);
        p.getServer().getCommands().performPrefixedCommand(p.getServer().createCommandSourceStack().withPermission(4),"fillbiome "+(pos.getX()-8)+" "+Math.max(p.level().getMinBuildHeight(),pos.getY()-8)+" "+(pos.getZ()-8)+" "+(pos.getX()+8)+" "+(pos.getY()+8)+" "+(pos.getZ()+8)+" minecraft:desert");
        h.assertTrue(p.level().getBiome(pos).is(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,ResourceLocation.parse("minecraft:desert"))),"Native biome fixture was not installed");DailyEvents.discover(p);h.assertTrue(q.progress==1&&data(p).ledger.rewards.get(q.id).paid,"Server biome discovery did not complete the accepted quest");long completed=data(p).ledger.account(p.getUUID()).round.grossCompleted;DailyEvents.discover(p);h.assertTrue(data(p).ledger.account(p.getUUID()).round.grossCompleted==completed,"Revisiting a biome repeated its discovery reward");System.out.println("DAILY_BIOME_EVENT_PASSED");
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_exploration",timeoutTicks=200)
    public static void discoveryRequiresARealStructureStartAndServerReferences(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();Quest q=DailyActors.quest(p,Category.FARMING);q.template=new Template(q.template.id(),"Native structure",Category.EXPLORATION,Difficulty.EASY,"minecraft:swamp_hut",1,-1,1,Set.of(),Set.of(),Set.of(),true,Validation.NEW_STRUCTURE);data(p).ledger.account(p.getUUID()).structures.remove("minecraft:swamp_hut");data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());
        var registry=p.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);var structure=registry.get(ResourceLocation.parse("minecraft:swamp_hut"));var piece=new net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece(net.minecraft.util.RandomSource.create(7),p.getBlockX(),p.getBlockZ());var box=piece.getBoundingBox();BlockPos inside=new BlockPos(box.minX()+2,box.minY()+1,box.minZ()+2);var chunkPos=new ChunkPos(inside);var chunk=h.getLevel().getChunk(chunkPos.x,chunkPos.z);var previous=chunk.getStartForStructure(structure);var references=new it.unimi.dsi.fastutil.longs.LongOpenHashSet(chunk.getReferencesForStructure(structure));
        try{var pieces=new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece));var start=new net.minecraft.world.level.levelgen.structure.StructureStart(structure,chunkPos,0,pieces);chunk.setStartForStructure(structure,start);chunk.addReferenceForStructure(structure,chunkPos.toLong());p.setPos(inside.getX()+.5,inside.getY(),inside.getZ()+.5);DailyEvents.discover(p);h.assertTrue(q.progress==1&&data(p).ledger.rewards.get(q.id).paid,"Structure manager discovery did not complete the accepted quest");h.assertTrue(data(p).ledger.account(p.getUUID()).structures.contains("minecraft:swamp_hut"),"Discovered structure was not recorded");System.out.println("DAILY_STRUCTURE_EVENT_PASSED");}
        finally{var starts=new java.util.HashMap<>(chunk.getAllStarts());if(previous==null)starts.remove(structure);else starts.put(structure,previous);chunk.setAllStarts(starts);var allReferences=new java.util.HashMap<>(chunk.getAllReferences());if(references.isEmpty())allReferences.remove(structure);else allReferences.put(structure,references);chunk.setAllReferences(allReferences);}
    }h.succeed();}
}
