package pt.contracts.daily;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pt.contracts.ContractsMod;
import pt.contracts.daily.DailyRules.*;

/** Listens to authoritative survival actions; no client packet can report objective progress. */
@EventBusSubscriber(modid="contracts")
public final class DailyEvents {
    private static final String NATURAL="contracts_daily_natural";
    private record Harvest(UUID player,ResourceKey<Level> dimension,BlockPos pos,BlockState before,Category category,int tick){}
    private static final List<Harvest> HARVESTS=new ArrayList<>();
    private static final Set<String> TICK_KEYS=new HashSet<>();
    private static final List<ItemFishedEvent> CATCHES=new ArrayList<>();
    private static final List<LivingDeathEvent> DEATHS=new ArrayList<>();
    private static boolean reportedFailure;
    private DailyEvents(){}
    private static void report(Throwable failure){if(!reportedFailure){reportedFailure=true;ContractsMod.LOG.error("Daily quest validator stopped safely; normal world actions remain available",failure);}}
    public static void clear(){HARVESTS.clear();TICK_KEYS.clear();CATCHES.clear();DEATHS.clear();reportedFailure=false;}
    @SubscribeEvent public static void cloned(PlayerEvent.Clone event){if(event.getOriginal().getPersistentData().get(DailyRewardJournal.RECEIPT)instanceof net.minecraft.nbt.CompoundTag receipt)event.getEntity().getPersistentData().put(DailyRewardJournal.RECEIPT,receipt.copy());}
    public static boolean valid(ServerPlayer p){return !(p instanceof FakePlayer)&&DailyService.survivor(p)&&p.isAlive();}
    public static String position(Level level,BlockPos pos){return level.dimension().location()+"@"+pos.asLong();}
    public static boolean mature(BlockState state){
        if(state.getBlock()instanceof CropBlock crop)return crop.isMaxAge(state);
        for(var property:state.getProperties())if(property instanceof IntegerProperty age&&property.getName().equals("age"))return state.getValue(age).equals(age.getPossibleValues().stream().max(Integer::compareTo).orElse(-1));
        return state.is(Blocks.PUMPKIN)||state.is(Blocks.MELON);
    }
    public static boolean block(Template template,BlockState state){String target=template.target();return target.startsWith("#")?state.is(TagKey.create(Registries.BLOCK,ResourceLocation.parse(target.substring(1)))):BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(target);}
    private static boolean miningTarget(ServerPlayer p,BlockState state){return DailyCatalog.all(DailyData.get(p.getServer()).ledger).stream().anyMatch(t->(t.category()==Category.MINING||t.category()==Category.FARMING&&!(state.getBlock()instanceof CropBlock))&&block(t,state));}
    private static void place(Level level,BlockPos pos,BlockState state){
        if(!(level instanceof net.minecraft.server.level.ServerLevel server))return;DailyData data=DailyData.get(server.getServer());
        boolean target=DailyCatalog.all(data.ledger).stream().anyMatch(t->(t.category()==Category.MINING||t.category()==Category.FARMING&&mature(state))&&block(t,state));
        if(target){data.ledger.mark(position(level,pos),true);data.persist(server.getServer());}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void placed(BlockEvent.EntityPlaceEvent event){try{if(event.isCanceled()||!(event.getLevel()instanceof Level level))return;
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi)for(var snapshot:multi.getReplacedBlockSnapshots())place(level,snapshot.getPos(),level.getBlockState(snapshot.getPos()));
        else place(level,event.getPos(),event.getPlacedBlock());
    }catch(RuntimeException|LinkageError failure){report(failure);}}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fluid(BlockEvent.FluidPlaceBlockEvent event){try{if(!event.isCanceled()&&event.getLevel()instanceof Level level)place(level,event.getPos(),event.getNewState());}catch(RuntimeException|LinkageError failure){report(failure);}}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void piston(PistonEvent.Pre event){try{if(event.isCanceled()||!(event.getLevel()instanceof Level level)||level.isClientSide)return;var helper=event.getStructureHelper();if(helper==null||!helper.resolve())return;
        DailyData data=DailyData.get(((net.minecraft.server.level.ServerLevel)level).getServer());boolean changed=false;
        for(BlockPos pos:helper.getToPush())if(DailyCatalog.all(data.ledger).stream().anyMatch(t->(t.category()==Category.MINING||t.category()==Category.FARMING&&mature(level.getBlockState(pos)))&&block(t,level.getBlockState(pos))))for(BlockPos mark:List.of(pos,pos.relative(event.getPistonMoveType().isExtend?event.getDirection():event.getDirection().getOpposite()))){if(!data.ledger.placed.contains(position(level,mark))){data.ledger.mark(position(level,mark),true);changed=true;}}
        if(changed)data.persist(((net.minecraft.server.level.ServerLevel)level).getServer());
    }catch(RuntimeException|LinkageError failure){report(failure);}}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void broken(BlockEvent.BreakEvent event){
        if(event.isCanceled()||!(event.getPlayer()instanceof ServerPlayer p)||!valid(p))return;BlockState state=event.getState();
        Category category=mature(state)?Category.FARMING:Category.MINING;
        if(DailyCatalog.all(DailyData.get(p.getServer()).ledger).stream().noneMatch(t->t.category()==category&&block(t,state)))return;
        if(category==Category.MINING&&!p.hasCorrectToolForDrops(state))return;
        if(p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(event.getPos()))>64)return;
        queue(p,event.getPos(),state,category);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void rightHarvest(PlayerInteractEvent.RightClickBlock event){if(event.isCanceled()||!(event.getEntity()instanceof ServerPlayer p)||!valid(p))return;BlockState before=p.level().getBlockState(event.getPos());if(mature(before)&&!before.is(Blocks.PUMPKIN)&&!before.is(Blocks.MELON))queue(p,event.getPos(),before,Category.FARMING);}
    private static void queue(ServerPlayer p,BlockPos pos,BlockState state,Category category){String key=p.getUUID()+":"+position(p.level(),pos);if(HARVESTS.size()<4096&&TICK_KEYS.add(key))HARVESTS.add(new Harvest(p.getUUID(),p.level().dimension(),pos.immutable(),state,category,p.getServer().getTickCount()));}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void spawned(FinalizeSpawnEvent event){
        boolean natural=event.getSpawnType()==MobSpawnType.NATURAL||event.getSpawnType()==MobSpawnType.CHUNK_GENERATION;
        event.getEntity().getPersistentData().putBoolean(NATURAL,natural&&!event.isSpawnCancelled());
    }
    public static boolean natural(Mob mob){return mob.getPersistentData().getBoolean(NATURAL);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void killed(LivingDeathEvent event){
        if(DEATHS.size()<1024)DEATHS.add(event);
    }
    private static void countKill(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getEntity()instanceof Mob mob)||!natural(mob)||!(event.getSource().getEntity()instanceof ServerPlayer p)||!valid(p)||p.distanceToSqr(mob)>4096)return;
        if(!mob.isDeadOrDying()||!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)&&!event.getSource().is(DamageTypeTags.IS_PROJECTILE))return;
        var direct=event.getSource().getDirectEntity();if(direct!=p&&!(direct instanceof net.minecraft.world.entity.projectile.Projectile projectile&&projectile.getOwner()==p))return;
        try{DailyService.activity(p,Category.COMBAT,Validation.NATURAL_KILL,t->t.target().equals("*hostile")?mob instanceof Enemy:t.target().startsWith("#")?mob.getType().is(TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.parse(t.target().substring(1)))):BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString().equals(t.target()),1);}catch(RuntimeException e){ContractsMod.LOG.debug("Daily combat remains unchanged: {}",e.getMessage());}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fished(ItemFishedEvent event){
        if(CATCHES.size()<1024)CATCHES.add(event);
    }
    private static void countCatch(ItemFishedEvent event){
        if(event.isCanceled()||!(event.getEntity()instanceof ServerPlayer p)||!valid(p)||event.getHookEntity().getPlayerOwner()!=p)return;
        if(!event.getHookEntity().isRemoved())return;
        boolean open=event.getHookEntity().isOpenWaterFishing();
        for(ItemStack stack:event.getDrops()){
            boolean fish=stack.is(ItemTags.FISHES)||stack.is(TagKey.create(Registries.ITEM,ResourceLocation.parse("c:foods/raw_fish"))),treasure=!fish&&open&&stack.is(TagKey.create(Registries.ITEM,ResourceLocation.parse("contracts:daily_fishing_treasures")));
            try{DailyService.activity(p,Category.FISHING,Validation.ROD_CATCH,t->t.target().equals("*fish")?fish:t.target().equals("*treasure")?treasure:DailyService.item(t,stack),Math.min(10000,stack.getCount()));}catch(RuntimeException e){ContractsMod.LOG.debug("Daily fishing remains unchanged: {}",e.getMessage());}
        }
    }
    public static void discover(ServerPlayer p){
        if(!valid(p))return;DailyData data=DailyData.get(p.getServer());var account=data.ledger.account(p.getUUID());account.dimensions.add(p.level().dimension().location().toString());
        String biome=p.level().getBiome(p.blockPosition()).unwrapKey().map(k->k.location().toString()).orElse("");
        if(!biome.isBlank()&&account.biomes.add(biome)){DailyService.activity(p,Category.EXPLORATION,Validation.NEW_BIOME,t->t.target().equals(biome),1);data.setDirty();}
        var registry=p.registryAccess().registryOrThrow(Registries.STRUCTURE);for(var structure:p.serverLevel().structureManager().getAllStructuresAt(p.blockPosition()).keySet()){
            var start=p.serverLevel().structureManager().getStructureAt(p.blockPosition(),structure);if(!start.isValid())continue;String id=Objects.requireNonNull(registry.getKey(structure)).toString();
            if(account.structures.add(id)){DailyService.activity(p,Category.EXPLORATION,Validation.NEW_STRUCTURE,t->t.target().equals(id),1);data.setDirty();}
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!(event.getEntity()instanceof ServerPlayer p)||p instanceof FakePlayer)return;try{
        DailyData data=DailyData.get(p.getServer());var account=data.ledger.account(p.getUUID());account.dimensions.add(p.level().dimension().location().toString());p.level().getBiome(p.blockPosition()).unwrapKey().ifPresent(k->account.biomes.add(k.location().toString()));
        var adventure=p.getServer().getAdvancements().get(ResourceLocation.parse("minecraft:adventure/adventuring_time"));if(adventure!=null)for(String key:p.getAdvancements().getOrStartProgress(adventure).getCompletedCriteria())if(ResourceLocation.tryParse(key)!=null)account.biomes.add(key);
        var structures=p.registryAccess().registryOrThrow(Registries.STRUCTURE);for(var structure:p.serverLevel().structureManager().getAllStructuresAt(p.blockPosition()).keySet())if(p.serverLevel().structureManager().getStructureAt(p.blockPosition(),structure).isValid())account.structures.add(Objects.requireNonNull(structures.getKey(structure)).toString());
        for(var entry:Map.of("minecraft:nether/find_fortress","minecraft:fortress","minecraft:end/find_end_city","minecraft:end_city","minecraft:nether/find_bastion","minecraft:bastion_remnant").entrySet()){var holder=p.getServer().getAdvancements().get(ResourceLocation.parse(entry.getKey()));if(holder!=null&&p.getAdvancements().getOrStartProgress(holder).isDone())account.structures.add(entry.getValue());}
        DailyRewardJournal.recover(p);DailyService.ensure(p);discover(p);DailyService.retry(p);
    }catch(RuntimeException|LinkageError failed){ContractsMod.LOG.error("Daily quest login could not reconcile for {}: {}",p.getUUID(),failed.getMessage());}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity()instanceof ServerPlayer p)try{DailyData.get(p.getServer()).persist(p.getServer());}catch(RuntimeException failed){ContractsMod.LOG.error("Could not checkpoint daily quests at logout",failed);}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();List<Harvest> pending=new ArrayList<>(HARVESTS);HARVESTS.clear();TICK_KEYS.clear();
        List<LivingDeathEvent> deaths=new ArrayList<>(DEATHS);DEATHS.clear();for(var death:deaths)countKill(death);
        List<ItemFishedEvent> catches=new ArrayList<>(CATCHES);CATCHES.clear();for(var caught:catches)countCatch(caught);
        for(var harvest:pending)try{
            ServerPlayer p=server.getPlayerList().getPlayer(harvest.player);var level=server.getLevel(harvest.dimension);
            if(p==null||!valid(p)||level==null||!p.level().dimension().equals(harvest.dimension)||!level.hasChunkAt(harvest.pos)||level.getBlockState(harvest.pos).equals(harvest.before))continue;
            DailyData data=DailyData.get(server);String pos=position(level,harvest.pos);
            if(data.ledger.trackingFull||data.ledger.placed.contains(pos))continue;
            if(harvest.category==Category.MINING){if(!data.ledger.mark(pos,false))continue;DailyService.activity(p,Category.MINING,Validation.NATURAL_BLOCK,t->block(t,harvest.before),1);}
            else{BlockState after=level.getBlockState(harvest.pos);if(!after.isAir()&&mature(after))continue;DailyService.activity(p,Category.FARMING,Validation.MATURE_CROP,t->block(t,harvest.before),1);}
        }catch(RuntimeException|LinkageError failed){ContractsMod.LOG.debug("Daily harvest remains unchanged: {}",failed.getMessage());}
        if(server.getTickCount()%20==0)for(ServerPlayer p:server.getPlayerList().getPlayers())try{discover(p);DailyService.ensure(p);if(server.getTickCount()%100==0)DailyService.retry(p);}catch(RuntimeException|LinkageError failed){ContractsMod.LOG.debug("Daily quests remain reserved: {}",failed.getMessage());}
        if(server.getTickCount()%600==0)try{DailyData data=DailyData.get(server);data.ledger.observe(System.currentTimeMillis());data.persist(server);}catch(RuntimeException|LinkageError failed){ContractsMod.LOG.error("Daily quest checkpoint failed",failed);}
    }
}
