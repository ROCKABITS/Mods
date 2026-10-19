package pt.lawsjustice.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.block.WorkStationBlock;
import pt.lawsjustice.compat.JusticeNotifications;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.domain.JusticeLedger.Point;
import pt.lawsjustice.domain.JusticeLedger.Sentence;
import pt.lawsjustice.domain.WorkLedger;
import pt.lawsjustice.domain.WorkLedger.Job;
import pt.lawsjustice.domain.WorkLedger.Node;
import pt.lawsjustice.domain.WorkLedger.Zone;
import pt.lawsjustice.network.JusticeNetwork;

/** All work, resource provenance, timed production and delivery are owned by the server. */
public final class WorkService {
    private static final Gson JSON=new Gson();
    private static final WeakHashMap<MinecraftServer,RuntimeState> RUNTIMES=new WeakHashMap<>();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final class RuntimeState {final Map<UUID,Session> sessions=new HashMap<>();final Map<UUID,Pending> production=new HashMap<>();int lastTick=Integer.MIN_VALUE;}
    private static final class Session {final BlockPos anchor;final String dimension;final long opened=System.nanoTime();final int tick;UUID nonce=UUID.randomUUID();int page,requests,requestTick=Integer.MIN_VALUE;String error="",message="";Session(ServerPlayer p,BlockPos pos){anchor=pos.immutable();dimension=dim(p);tick=p.getServer().getTickCount();}}
    private static final class Pending {final UUID job,node;final BlockPos position;final String dimension;final int total;int remaining;Pending(ServerPlayer p,Job job,BlockPos pos,UUID node,int ticks){this.job=job.id();this.node=node;position=pos.immutable();dimension=dim(p);total=ticks;remaining=ticks;}}
    private WorkService(){}
    private static RuntimeState runtime(MinecraftServer s){return RUNTIMES.computeIfAbsent(s,k->new RuntimeState());}
    private static long now(){return Math.max(0,System.currentTimeMillis());}
    private static String dim(ServerPlayer p){return p.level().dimension().location().toString();}
    private static Point point(BlockPos p){return new Point(p.getX(),p.getY(),p.getZ());}
    private static BlockPos position(Point p){return new BlockPos(p.x(),p.y(),p.z());}
    private static void thread(ServerPlayer p){if(p==null||p.getServer()==null||!p.getServer().isSameThread())throw failure("thread");}
    public static void closeSession(ServerPlayer p){thread(p);runtime(p.getServer()).sessions.remove(p.getUUID());}
    public static void open(ServerPlayer p,BlockPos pos){thread(p);if(!physical(p,pos,dim(p)))return;JusticeService.closeSession(p);CourtService.closeSession(p);runtime(p.getServer()).sessions.put(p.getUUID(),new Session(p,pos));send(p);}
    private static boolean physical(ServerPlayer p,BlockPos pos,String dimension){return p.isAlive()&&dim(p).equals(dimension)&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&p.level().getBlockState(pos).getBlock()instanceof WorkStationBlock;}
    private static boolean valid(ServerPlayer p,Session s){return s!=null&&physical(p,s.anchor,s.dimension)&&System.nanoTime()-s.opened<=300_000_000_000L&&(long)p.getServer().getTickCount()-s.tick<=6000;}
    private static String station(ServerPlayer p,BlockPos pos){return p.level().getBlockState(pos).getBlock()instanceof WorkStationBlock b?b.kind():"";}
    private static void administrator(ServerPlayer p){if(!p.hasPermissions(2))throw failure("permission");}
    private static void atStation(ServerPlayer p,Session s,String kind){if(!station(p,s.anchor).equals(kind))throw failure("work_station");}
    private static Sentence prisoner(ServerPlayer p){Sentence s=JusticeData.get(p.getServer()).ledger().active().get(p.getUUID());if(s==null||!p.isAlive()||p.isCreative()||p.isSpectator()||CourtService.restricted(p))throw failure("work_prisoner");return s;}
    private static Job job(ServerPlayer p){Sentence s=prisoner(p);Job j=JusticeData.get(p.getServer()).work().active(p.getUUID());if(j==null||!j.sentence().equals(s.id())||!j.prison().equals(s.prison()))throw failure("work_missing");return j;}
    private static Zone zone(ServerPlayer p,Job j,BlockPos at){Zone z=JusticeData.get(p.getServer()).work().zones().get(j.zone());if(z==null||!z.area().dimension().equals(dim(p))||!z.area().contains(point(at))||!z.area().contains(point(p.blockPosition())))throw failure("work_zone");return z;}
    public static void handle(ServerPlayer p,String op,String raw){
        thread(p);RuntimeState r=runtime(p.getServer());Session s=r.sessions.get(p.getUUID());
        if(op.equals("work_close")){r.sessions.remove(p.getUUID());return;}
        if(!valid(p,s)){r.sessions.remove(p.getUUID());closed(p);return;}
        int tick=p.getServer().getTickCount();if(s.requestTick!=tick){s.requestTick=tick;s.requests=0;}if(++s.requests>32)return;
        try{
            if(raw==null||raw.length()>8192)throw failure("request");JsonObject request=JsonParser.parseString(raw).getAsJsonObject();
            if(op.equals("work_refresh")){keys(request);send(p);return;}
            if(op.equals("work_page")){keys(request,"index");s.page=(int)number(request,"index",0,256);send(p);return;}
            JusticeData data=JusticeData.get(p.getServer());WorkLedger work=data.work();
            if(!uuid(request,"nonce").equals(s.nonce)||number(request,"version",0,Long.MAX_VALUE)!=data.version())throw failure("stale");
            switch(op){
                case "work_zone"->{
                    keys(request,"name","type","prison","nonce","version");administrator(p);atStation(p,s,"BOARD");var area=JusticeService.selectedArea(p);UUID prison=uuid(request,"prison");var registered=data.ledger().prisons().get(prison);
                    if(registered==null||!registered.area().contains(area))throw failure("work_zone");if(volume(area)>65536)throw failure("work_zone_size");
                    data.replaceWork(work.putZone(new Zone(UUID.randomUUID(),prison,text(request,"name",48),text(request,"type",16),area,0)));s.message="lawsjustice.message.work_zone_saved";
                }
                case "work_commission"->{keys(request,"zone","nonce","version");administrator(p);atStation(p,s,"BOARD");UUID id=uuid(request,"zone");Zone z=work.zones().get(id);if(z==null||!z.area().dimension().equals(dim(p)))throw failure("work_zone");data.replaceWork(work.commission(id,scan(p,z)));s.message="lawsjustice.message.work_resources_saved";}
                case "work_policy"->{
                    keys(request,"kind","quantity","minutes","percent","nonce","version");administrator(p);atStation(p,s,"BOARD");String kind=text(request,"kind",16);var tasks=new ArrayList<>(work.rules().tasks());var rule=new WorkLedger.Rule(kind,(int)number(request,"quantity",1,4096),Math.multiplyExact(number(request,"minutes",1,43200),1200));tasks.replaceAll(old->old.kind().equals(kind)?rule:old);
                    data.replaceWork(work.configure(new WorkLedger.Rules((int)number(request,"percent",0,100),tasks)));s.message="lawsjustice.message.work_policy_saved";
                }
                case "work_accept"->{
                    keys(request,"zone","kind","nonce","version");atStation(p,s,"BOARD");Sentence sentence=prisoner(p);var prison=data.ledger().prisons().get(sentence.prison());if(!prison.area().dimension().equals(dim(p))||!prison.area().contains(point(s.anchor)))throw failure("work_zone");if(sentence.reductionTicks()>=sentence.originalTicks()*work.rules().maximumPercent()/100)throw failure("work_cap");
                    UUID id=UUID.randomUUID();WorkLedger next=work.accept(id,p.getUUID(),sentence.id(),sentence.prison(),uuid(request,"zone"),text(request,"kind",16),now());data.replaceWork(next);giveBundle(p,id);s.message="lawsjustice.message.work_accepted";
                }
                case "work_recover"->{keys(request,"nonce","version");Job j=job(p);giveBundle(p,j.id());s.message="lawsjustice.message.work_recovered";}
                case "work_cancel"->{keys(request,"nonce","version");Job j=job(p);data.replaceWork(work.cancel(p.getUUID(),now()));r.production.remove(p.getUUID());removeBundles(p,j.id());s.message="lawsjustice.message.work_cancelled";}
                case "work_produce"->{keys(request,"nonce","version");atStation(p,s,"WORKSHOP");Job j=job(p);zone(p,j,s.anchor);if(!Set.of("CARPENTRY","SMITHING").contains(j.kind()))throw failure("work_station");work.produce(p.getUUID(),now());start(p,j,s.anchor,null,j.kind().equals("SMITHING")?80:40);s.message="lawsjustice.message.work_producing";}
                case "work_cargo"->{keys(request,"nonce","version");atStation(p,s,"WAREHOUSE");Job j=job(p);zone(p,j,s.anchor);data.replaceWork(work.cargo(p.getUUID(),point(s.anchor),now()));s.message="lawsjustice.message.work_cargo";}
                case "work_deliver"->{keys(request,"nonce","version");atStation(p,s,"DELIVERY");deliver(p,s);}
                default->throw failure("request");
            }
            s.nonce=UUID.randomUUID();send(p);
        }catch(RuntimeException error){s.error=errorKey(error);send(p);}
    }
    private static long volume(JusticeLedger.Area a){return ((long)a.max().x()-a.min().x()+1)*(a.max().y()-a.min().y()+1)*(a.max().z()-a.min().z()+1);}
    private static List<Node> scan(ServerPlayer p,Zone z){
        if(volume(z.area())>65536)throw failure("work_zone_size");ArrayList<Node> nodes=new ArrayList<>();
        for(BlockPos pos:BlockPos.betweenClosed(position(z.area().min()),position(z.area().max()))){if(!p.level().hasChunkAt(pos))throw failure("work_unloaded");BlockState state=p.level().getBlockState(pos);String resource=resource(z.type(),state,p.level().isEmptyBlock(pos.above()));if(resource.isEmpty())continue;nodes.add(new Node(UUID.randomUUID(),z.id(),point(pos),resource,BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),"READY"));if(nodes.size()>WorkLedger.MAX_NODES)throw failure("capacity");}
        return nodes;
    }
    private static String resource(String type,BlockState state,boolean airAbove){
        return switch(type){
            case "MINE"->Set.of(Blocks.STONE,Blocks.COBBLESTONE,Blocks.DEEPSLATE,Blocks.COBBLED_DEEPSLATE,Blocks.ANDESITE,Blocks.DIORITE,Blocks.GRANITE).contains(state.getBlock())?"STONE":"";
            case "FARM"->state.is(Blocks.WHEAT)?"WHEAT":"";
            case "WORKSHOP"->state.is(BlockTags.LOGS)?"LOG":state.is(Blocks.IRON_ORE)||state.is(Blocks.DEEPSLATE_IRON_ORE)?"IRON":state.is(Blocks.COAL_ORE)||state.is(Blocks.DEEPSLATE_COAL_ORE)?"COAL":"";
            case "YARD"->airAbove&&!state.isAir()&&state.isSolid()?"CLEAN":"";
            default->"";
        };
    }
    private static boolean canGather(ServerPlayer p,Node n,Job j){
        if(n==null||!n.zone().equals(j.zone())||!n.state().equals("READY"))return false;zone(p,j,position(n.position()));BlockState state=p.level().getBlockState(position(n.position()));if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(n.block()))return false;
        if(p.distanceToSqr(n.position().x()+.5,n.position().y()+.5,n.position().z()+.5)>36)return false;
        return switch(j.kind()){
            case "MINING"->n.resource().equals("STONE")&&p.getMainHandItem().is(ItemTags.PICKAXES);
            case "FARMING"->n.resource().equals("WHEAT")&&state.getBlock()instanceof CropBlock crop&&crop.isMaxAge(state);
            case "CARPENTRY"->n.resource().equals("LOG")&&p.getMainHandItem().is(ItemTags.AXES);
            case "SMITHING"->Set.of("IRON","COAL").contains(n.resource())&&p.getMainHandItem().is(ItemTags.PICKAXES);
            case "CLEANING"->n.resource().equals("CLEAN")&&p.getMainHandItem().is(Items.BRUSH);
            default->false;
        };
    }
    /** Handles a genuine server break event. Job output is sealed; ordinary item drops are suppressed. */
    public static boolean harvest(ServerPlayer p,BlockPos at){
        thread(p);if(!JusticeService.imprisoned(p))return false;
        try{Job j=job(p);if(j.kind().equals("CLEANING"))return false;JusticeData data=JusticeData.get(p.getServer());Node n=data.work().nodeAt(dim(p),point(at));if(!canGather(p,n,j))return false;
            BlockState before=p.level().getBlockState(at);WorkLedger next=data.work().gather(p.getUUID(),n.id(),now());data.replaceWork(next);
            if(j.kind().equals("FARMING"))p.level().setBlockAndUpdate(at,((CropBlock)before.getBlock()).getStateForAge(0));else{p.level().setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());p.getMainHandItem().hurtAndBreak(1,p,EquipmentSlot.MAINHAND);}
            p.level().levelEvent(2001,at,Block.getId(before));progress(p,next.active(p.getUUID()));return true;
        }catch(RuntimeException error){p.displayClientMessage(Component.translatable(errorKey(error)),true);return false;}
    }
    public static boolean clean(ServerPlayer p,BlockPos at){
        thread(p);if(!JusticeService.imprisoned(p)||!p.getMainHandItem().is(Items.BRUSH))return false;
        try{Job j=job(p);if(!j.kind().equals("CLEANING"))return false;Node n=JusticeData.get(p.getServer()).work().nodeAt(dim(p),point(at));if(!canGather(p,n,j))return false;start(p,j,at,n.id(),40);return true;}
        catch(RuntimeException error){p.displayClientMessage(Component.translatable(errorKey(error)),true);return true;}
    }
    private static void start(ServerPlayer p,Job j,BlockPos at,UUID node,int ticks){if(runtime(p.getServer()).production.containsKey(p.getUUID()))throw failure("work_wait");if(p.distanceToSqr(at.getX()+.5,at.getY()+.5,at.getZ()+.5)>16)throw failure("work_station");runtime(p.getServer()).production.put(p.getUUID(),new Pending(p,j,at,node,ticks));}
    public static void placed(ServerPlayer p,BlockPos at){thread(p);JusticeData data=JusticeData.get(p.getServer());WorkLedger next=data.work().placed(dim(p),point(at),p.hasPermissions(2)&&!p.isCreative()&&!p.isSpectator());if(next!=data.work())data.replaceWork(next);}
    public static void destroyed(ServerPlayer p,BlockPos at){thread(p);JusticeData data=JusticeData.get(p.getServer());WorkLedger next=data.work().placed(dim(p),point(at),false);if(next!=data.work())data.replaceWork(next);}
    private static void deliver(ServerPlayer p,Session s){
        JusticeData data=JusticeData.get(p.getServer());Job j=job(p);zone(p,j,s.anchor);Sentence before=prisoner(p);if(!hasBundle(p,j.id()))throw failure("work_bundle");
        JusticeLedger after=data.ledger().reduce(p.getUUID(),j.reductionTicks(),data.work().rules().maximumPercent(),now());
        long amount=Math.min(j.reductionTicks(),Math.min(before.remainingTicks(),Math.max(0,before.originalTicks()*data.work().rules().maximumPercent()/100-before.reductionTicks())));
        WorkLedger next=data.work().finish(p.getUUID(),before.id(),amount,now());
        if(after.active().containsKey(p.getUUID()))data.replaceWork(after,next);
        else{var prison=data.ledger().prisons().get(before.prison());data.replaceWorkAndQueue(after,next,p.getUUID(),new JusticeData.ReleaseTarget(prison.area().dimension(),prison.release()));}
        removeBundles(p,j.id());runtime(p.getServer()).production.remove(p.getUUID());p.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, .7F,1.25F);JusticeNotifications.send(p,"lawsjustice.notification.work_reduced");s.message="lawsjustice.message.work_delivered";
    }
    private static void giveBundle(ServerPlayer p,UUID job){if(hasBundle(p,job))return;ItemStack stack=new ItemStack(BlockRegistry.WORK_BUNDLE.get());CompoundTag tag=new CompoundTag();tag.putString("lawsjustice_work",job.toString());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));if(!p.getInventory().add(stack))p.drop(stack,false);}
    private static boolean bundle(ItemStack stack,UUID job){if(!stack.is(BlockRegistry.WORK_BUNDLE.get()))return false;CustomData data=stack.get(DataComponents.CUSTOM_DATA);return data!=null&&data.copyTag().getString("lawsjustice_work").equals(job.toString());}
    private static boolean hasBundle(ServerPlayer p,UUID job){for(int slot=0;slot<p.getInventory().getContainerSize();slot++)if(bundle(p.getInventory().getItem(slot),job))return true;return false;}
    private static void removeBundles(ServerPlayer p,UUID job){for(int slot=0;slot<p.getInventory().getContainerSize();slot++)if(bundle(p.getInventory().getItem(slot),job))p.getInventory().setItem(slot,ItemStack.EMPTY);p.getInventory().setChanged();p.containerMenu.broadcastChanges();}
    private static void progress(ServerPlayer p,Job j){p.displayClientMessage(Component.translatable("lawsjustice.work.progress",j.output(),j.quantity(),j.raw(),j.fuel()),true);}
    public static JsonObject snapshot(ServerPlayer p){
        thread(p);Session s=runtime(p.getServer()).sessions.get(p.getUUID());JsonObject out=new JsonObject();out.addProperty("kind","work");out.addProperty("protocol",3);out.addProperty("open",valid(p,s));if(!valid(p,s))return out;
        JusticeData data=JusticeData.get(p.getServer());WorkLedger work=data.work();boolean admin=p.hasPermissions(2);Sentence self=data.ledger().active().get(p.getUUID());Job job=work.active(p.getUUID());
        out.addProperty("admin",admin);out.addProperty("canWork",self!=null&&p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!CourtService.restricted(p));out.addProperty("nonce",s.nonce.toString());out.addProperty("version",data.version());out.addProperty("station",station(p,s.anchor));if(!s.error.isEmpty())out.addProperty("error",s.error);if(!s.message.isEmpty())out.addProperty("message",s.message);
        out.add("self",self==null?JsonNull.INSTANCE:JSON.toJsonTree(self));out.add("job",job==null?JsonNull.INSTANCE:JSON.toJsonTree(job));out.add("rules",JSON.toJsonTree(work.rules()));out.addProperty("bundle",job!=null&&hasBundle(p,job.id()));
        List<Zone> visible=work.zones().values().stream().filter(z->admin||self!=null&&z.prison().equals(self.prison())).sorted(Comparator.comparing(Zone::name)).toList();s.page=Math.min(s.page,Math.max(0,(visible.size()-1)/8));JsonObject pages=new JsonObject();pages.addProperty("index",s.page);pages.addProperty("total",Math.max(1,(visible.size()+7)/8));pages.addProperty("records",visible.size());out.add("pages",pages);JsonArray zones=new JsonArray();
        for(Zone z:visible.stream().skip(s.page*8L).limit(8).toList()){JsonObject row=JSON.toJsonTree(z).getAsJsonObject();row.addProperty("readyNodes",work.nodes().values().stream().filter(n->n.zone().equals(z.id())&&n.state().equals("READY")).count());row.addProperty("spentNodes",work.nodes().values().stream().filter(n->n.zone().equals(z.id())&&!n.state().equals("READY")).count());zones.add(row);}out.add("zones",zones);
        JsonArray prisons=new JsonArray();if(admin)for(var prison:data.ledger().prisons().values()){JsonObject row=new JsonObject();row.addProperty("id",prison.id().toString());row.addProperty("name",prison.name());prisons.add(row);}out.add("prisons",prisons);
        if(admin)try{out.add("selection",JSON.toJsonTree(JusticeService.selectedArea(p)));}catch(RuntimeException missing){out.add("selection",JsonNull.INSTANCE);}
        JsonArray history=new JsonArray();for(Job previous:work.jobs().values().stream().filter(j->(admin||j.player().equals(p.getUUID()))&&!j.status().equals(WorkLedger.ACTIVE)).sorted(Comparator.comparing(Job::finishedAt).reversed()).limit(8).toList())history.add(JSON.toJsonTree(previous));out.add("history",history);
        JsonArray hints=new JsonArray();if(job!=null)for(Node n:work.nodes().values().stream().filter(n->n.zone().equals(job.zone())&&n.state().equals("READY")).limit(8).toList())hints.add(JSON.toJsonTree(n));out.add("hints",hints);
        Pending pending=runtime(p.getServer()).production.get(p.getUUID());out.addProperty("processingRemaining",pending==null?0:pending.remaining);out.addProperty("processingTotal",pending==null?0:pending.total);return out;
    }
    public static void tick(MinecraftServer server){
        RuntimeState r=runtime(server);int tick=server.getTickCount();if(r.lastTick==tick)return;r.lastTick=tick;
        for(var e:List.copyOf(r.production.entrySet())){ServerPlayer p=server.getPlayerList().getPlayer(e.getKey());Pending pending=e.getValue();
            try{if(p==null)throw failure("work_wait");Job j=job(p);if(!j.id().equals(pending.job)||!dim(p).equals(pending.dimension)||p.distanceToSqr(pending.position.getX()+.5,pending.position.getY()+.5,pending.position.getZ()+.5)>16)throw failure("work_station");zone(p,j,pending.position);JusticeData data=JusticeData.get(server);
                if(pending.node!=null){Node n=data.work().nodes().get(pending.node);if(!canGather(p,n,j))throw failure("work_resource");}
                else if(!station(p,pending.position).equals("WORKSHOP"))throw failure("work_station");
                if(--pending.remaining<=0){WorkLedger next=pending.node==null?data.work().produce(p.getUUID(),now()):data.work().gather(p.getUUID(),pending.node,now());data.replaceWork(next);r.production.remove(e.getKey());if(pending.node!=null)p.getMainHandItem().hurtAndBreak(1,p,EquipmentSlot.MAINHAND);p.playSound(pending.node==null?SoundEvents.ANVIL_USE:SoundEvents.BRUSH_GENERIC,.45F,1.1F);progress(p,next.active(p.getUUID()));}
                if(tick%8==0)p.serverLevel().sendParticles(pending.node==null?ParticleTypes.ENCHANT:ParticleTypes.WAX_OFF,pending.position.getX()+.5,pending.position.getY()+1,pending.position.getZ()+.5,2,.18,.1,.18,.02);
            }catch(RuntimeException error){r.production.remove(e.getKey());if(p!=null)p.displayClientMessage(Component.translatable(errorKey(error)),true);}
        }
        if(tick%20==0)for(var e:List.copyOf(r.sessions.entrySet())){ServerPlayer p=server.getPlayerList().getPlayer(e.getKey());if(p==null||!valid(p,e.getValue())){r.sessions.remove(e.getKey());if(p!=null)closed(p);}else send(p);}
    }
    public static void logout(ServerPlayer p){thread(p);RuntimeState r=runtime(p.getServer());r.sessions.remove(p.getUUID());r.production.remove(p.getUUID());}
    public static void stop(MinecraftServer server){RUNTIMES.remove(server);}
    private static void send(ServerPlayer p){Session s=runtime(p.getServer()).sessions.get(p.getUUID());try{JusticeNetwork.send(p,snapshot(p));if(s!=null){s.error="";s.message="";}}catch(RuntimeException error){LOG.warn("Cannot send prison work state",error);closed(p);}}
    private static void closed(ServerPlayer p){JsonObject out=new JsonObject();out.addProperty("kind","work");out.addProperty("open",false);JusticeNetwork.send(p,out);}
    private static void keys(JsonObject o,String...allowed){if(!Set.of(allowed).containsAll(o.keySet()))throw failure("request");}
    private static String text(JsonObject o,String key,int max){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw failure("request");String s=v.getAsString().strip();if(s.isBlank()||s.length()>max||s.codePoints().anyMatch(Character::isISOControl))throw failure("request");return s;}
    private static UUID uuid(JsonObject o,String key){String raw=text(o,key,36);try{UUID id=UUID.fromString(raw);if(!id.toString().equals(raw))throw failure("request");return id;}catch(IllegalArgumentException bad){throw failure("request");}}
    private static long number(JsonObject o,String key,long min,long max){try{JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw failure("request");long n=v.getAsBigDecimal().longValueExact();if(n<min||n>max)throw failure("request");return n;}catch(ArithmeticException bad){throw failure("request");}}
    private static String errorKey(RuntimeException e){String key=e.getMessage();return key!=null&&key.startsWith("lawsjustice.error.")?key:"lawsjustice.error.request";}
    private static IllegalStateException failure(String key){return new IllegalStateException("lawsjustice.error."+key);}
}
