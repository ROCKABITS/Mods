package pt.realmprogression.service;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import pt.realmprogression.api.ProgressionPermissions;
import pt.realmprogression.block.ProgressBlocks;
import pt.realmprogression.compat.TreasuryBridge;
import pt.realmprogression.data.ProgressStore;
import pt.realmprogression.domain.ProgressLedger;
import pt.realmprogression.menu.ProgressMenu;
import pt.realmprogression.network.ProgressNetwork;

/** Physical sessions, fresh server permissions, strict requests and atomic world decisions. */
public final class ProgressService {
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Set<MinecraftServer> BUSY=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<MinecraftServer,Long> ERRORS=new WeakHashMap<>();
    private static final class Session {
        final BlockPos pos;final String dimension;final long opened=System.nanoTime();
        UUID nonce=UUID.randomUUID();long lastAction,lastQuery;String search="",selected="create";boolean browse;int page;
        Session(ServerPlayer p,BlockPos pos){this.pos=pos.immutable();dimension=p.level().dimension().location().toString();}
    }
    public static void observe(MinecraftServer server){
        if(server==null||!server.isSameThread()||!BUSY.add(server))return;
        try{
            var store=ProgressStore.get(server);var before=store.state();long balance=TreasuryBridge.balance(server);
            var installed=ModCatalog.installed().keySet();
            if(before.rules().values().stream().noneMatch(r->r.locks()&&installed.contains(r.mod())&&balance>=r.goal()))return;
            long confirmed=TreasuryBridge.confirmed(server);
            var after=ProgressLedger.observe(before,confirmed,installed,Math.max(0,System.currentTimeMillis()));
            if(after.equals(before))return;
            store.commit(after);syncAll(server);CraftGate.refresh(server);
            for(var r:after.rules().values())if(r.unlocked()&&!before.rules().get(r.mod()).unlocked()){
                String name=installed.contains(r.mod())?ModCatalog.installed().get(r.mod()).name():r.mod();
                ProgressNetwork.announce(server,name);
            }
        }catch(RuntimeException|LinkageError failure){
            long now=System.nanoTime();if(now-ERRORS.getOrDefault(server,0L)>10_000_000_000L){ERRORS.put(server,now);com.mojang.logging.LogUtils.getLogger().error("Progression could not confirm public funds; existing locks are preserved",failure);}
        }finally{BUSY.remove(server);}
    }
    public static void interact(PlayerInteractEvent.RightClickBlock e){
        if(e.getLevel().isClientSide||!(e.getEntity() instanceof ServerPlayer p)||!e.getLevel().getBlockState(e.getPos()).is(ProgressBlocks.TABLE.get()))return;
        e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        try{open(p,e.getPos());}catch(RuntimeException failure){p.displayClientMessage(Component.translatable(message(failure)),false);}
    }
    public static void open(ServerPlayer p,BlockPos pos){
        if(p instanceof FakePlayer||p.isSpectator()||!p.isAlive()||!p.level().hasChunkAt(pos)||p.distanceToSqr(pos.getCenter())>64||!p.level().getBlockState(pos).is(ProgressBlocks.TABLE.get()))throw error("distance");
        observe(p.getServer());ProgressStore.get(p.getServer()).state();p.closeContainer();SESSIONS.put(p.getUUID(),new Session(p,pos));
        p.openMenu(new SimpleMenuProvider((id,inv,player)->new ProgressMenu(id,inv),Component.translatable("gui.realmprogression.title")));send(p,"");
    }
    public static boolean valid(ServerPlayer p){
        var s=SESSIONS.get(p.getUUID());return s!=null&&!(p instanceof FakePlayer)&&p.isAlive()&&!p.isSpectator()&&System.nanoTime()-s.opened<600_000_000_000L&&p.level().dimension().location().toString().equals(s.dimension)&&p.level().hasChunkAt(s.pos)&&p.distanceToSqr(s.pos.getCenter())<=64&&p.level().getBlockState(s.pos).is(ProgressBlocks.TABLE.get());
    }
    private static Session session(ServerPlayer p){if(!valid(p)||!(p.containerMenu instanceof ProgressMenu))throw error("distance");return SESSIONS.get(p.getUUID());}
    public static void close(ServerPlayer p){SESSIONS.remove(p.getUUID());}
    public static void stop(MinecraftServer s){ProgressStore.forget(s);SESSIONS.clear();BUSY.remove(s);ERRORS.remove(s);}
    private static long number(JsonObject body,String key){if(!body.has(key)||!body.get(key).isJsonPrimitive()||!body.getAsJsonPrimitive(key).isNumber())throw error("request");return body.get(key).getAsBigDecimal().longValueExact();}
    private static String text(JsonObject body,String key,int max){if(!body.has(key)||!body.get(key).isJsonPrimitive()||!body.getAsJsonPrimitive(key).isString())throw error("request");String s=body.get(key).getAsString();if(s.length()>max)throw error("request");return s;}
    private static boolean bool(JsonObject body,String key){if(!body.has(key)||!body.get(key).isJsonPrimitive()||!body.getAsJsonPrimitive(key).isBoolean())throw error("request");return body.get(key).getAsBoolean();}
    private static void exact(JsonObject body,String...keys){if(!body.keySet().equals(Set.of(keys)))throw error("request");}
    public static void action(ServerPlayer p,String operation,String json){
        try{
            var s=session(p);if(json.length()>2048)throw error("request");var body=JsonParser.parseString(json).getAsJsonObject();long now=System.nanoTime();
            if(operation.equals("query")){
                exact(body,"search","selected","page","browse");if(now-s.lastQuery<250_000_000L)return;s.lastQuery=now;
                s.search=text(body,"search",64).strip();s.selected=text(body,"selected",64);s.page=Math.toIntExact(number(body,"page"));if(s.page<0||s.page>4096)throw error("request");s.browse=bool(body,"browse")&&ProgressionPermissions.canAdminister(p);observe(p.getServer());send(p,"");return;
            }
            exact(body,"nonce","revision","mod","goal","enabled","namespaces");
            if(!operation.equals("configure")||!ProgressionPermissions.canAdminister(p))throw error("permission");
            if(!UUID.fromString(text(body,"nonce",36)).equals(s.nonce)||now-s.lastAction<150_000_000L)throw error("stale");s.nonce=UUID.randomUUID();s.lastAction=now;
            var store=ProgressStore.get(p.getServer());var before=store.state();if(number(body,"revision")!=before.revision())throw error("stale");
            String id=text(body,"mod",64);ProgressLedger.id(id);if(ProgressLedger.PROTECTED.contains(id)||!ModCatalog.installed().containsKey(id))throw error("protected");
            long goal=number(body,"goal");boolean enabled=bool(body,"enabled");Set<String> namespaces=new TreeSet<>(Arrays.asList(text(body,"namespaces",520).split(",",-1)));
            if(!ModCatalog.namespaces().containsAll(namespaces)||enabled&&ModCatalog.count(ModCatalog.coverage(p.getServer()),namespaces)==0)throw error("coverage");
            store.commit(ProgressLedger.configure(before,id,namespaces,goal,enabled,p.getUUID(),pt.crownledger.compat.ChroniclesBridge.publicName(p),Math.max(0,System.currentTimeMillis())));
            s.selected=id;syncAll(p.getServer());CraftGate.refresh(p.getServer());observe(p.getServer());send(p,"");
        }catch(RuntimeException|LinkageError failure){if(valid(p)&&p.containerMenu instanceof ProgressMenu)try{send(p,message(failure));}catch(RuntimeException secondary){p.closeContainer();}else p.closeContainer();}
    }
    public static JsonObject snapshot(ServerPlayer p){
        var s=session(p);var state=ProgressStore.get(p.getServer()).state();boolean admin=ProgressionPermissions.canAdminister(p);if(!admin)s.browse=false;
        var mods=new TreeMap<>(ModCatalog.installed());for(String id:state.rules().keySet())mods.putIfAbsent(id,new ModCatalog.Mod(id,id,"",false));
        var coverage=ModCatalog.coverage(p.getServer());var rows=new ArrayList<JsonObject>();
        for(var mod:mods.values()){
            var rule=state.rules().get(mod.id());if(!s.browse&&rule==null)continue;
            if(!(mod.id()+" "+mod.name()).toLowerCase(Locale.ROOT).contains(s.search.toLowerCase(Locale.ROOT)))continue;
            var row=new JsonObject();row.addProperty("id",mod.id());row.addProperty("name",mod.name());row.addProperty("version",mod.version());row.addProperty("installed",mod.installed());row.addProperty("protected",ProgressLedger.PROTECTED.contains(mod.id()));
            Set<String> namespaces=rule==null?Set.of(mod.id()):rule.namespaces();row.addProperty("namespaces",String.join(",",new TreeSet<>(namespaces)));row.addProperty("recipes",ModCatalog.count(coverage,namespaces));row.addProperty("configured",rule!=null);row.addProperty("enabled",rule!=null&&rule.enabled());row.addProperty("unlocked",rule!=null&&rule.unlocked());row.addProperty("goal",rule==null?800_000:rule.goal());row.addProperty("at",rule==null?-1:rule.unlockedAt());rows.add(row);
        }
        var out=new JsonObject();out.addProperty("container",p.containerMenu.containerId);out.addProperty("revision",state.revision());out.addProperty("nonce",s.nonce.toString());out.addProperty("admin",admin);out.addProperty("browse",s.browse);out.addProperty("search",s.search);
        try{out.addProperty("balance",TreasuryBridge.balance(p.getServer()));out.addProperty("treasuryAvailable",true);}catch(RuntimeException failure){out.addProperty("balance",0);out.addProperty("treasuryAvailable",false);}
        int pages=Math.max(1,(rows.size()+5)/6);s.page=Math.min(s.page,pages-1);out.addProperty("page",s.page);out.addProperty("pages",pages);out.addProperty("total",rows.size());
        var items=new JsonArray();rows.stream().skip(s.page*6L).limit(6).forEach(items::add);out.add("mods",items);
        var selected=rows.stream().filter(r->r.get("id").getAsString().equals(s.selected)).findFirst().orElse(items.isEmpty()?null:items.get(0).getAsJsonObject());if(selected!=null){s.selected=selected.get("id").getAsString();out.add("selected",selected);}
        var history=new JsonArray();for(var e:state.history().stream().limit(24).toList())history.add(new Gson().toJsonTree(e));out.add("history",history);return out;
    }
    public static void sync(ServerPlayer p){
        var out=new JsonObject();var list=new JsonArray();ProgressStore.get(p.getServer()).state().locked().forEach(list::add);out.add("locked",list);ProgressNetwork.state(p,out);
    }
    public static void syncAll(MinecraftServer s){for(var p:s.getPlayerList().getPlayers())sync(p);}
    private static void send(ServerPlayer p,String error){var out=snapshot(p);out.addProperty("error",error);ProgressNetwork.snapshot(p,out);}
    private static IllegalStateException error(String key){return new IllegalStateException("error.realmprogression."+key);}
    private static String message(Throwable t){String message=t.getMessage();return message!=null&&message.startsWith("error.realmprogression.")?message:"error.realmprogression.request";}
    private ProgressService(){}
}
