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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import pt.lawsjustice.block.PrisonControlBlock;
import pt.lawsjustice.block.ReinforcedLockBlock;
import pt.lawsjustice.compat.JusticeNames;
import pt.lawsjustice.compat.JusticeNotifications;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.data.JusticeData.ReleaseTarget;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.domain.JusticeLedger.Area;
import pt.lawsjustice.domain.JusticeLedger.Cell;
import pt.lawsjustice.domain.JusticeLedger.Point;
import pt.lawsjustice.domain.JusticeLedger.Prison;
import pt.lawsjustice.domain.JusticeLedger.Sentence;
import pt.lawsjustice.network.JusticeNetwork;

/** Authoritative, physical custody. Phase one grants administration only to operators. */
public final class JusticeService {
    private static final Gson JSON=new Gson();
    private static final WeakHashMap<MinecraftServer,RuntimeState> RUNTIMES=new WeakHashMap<>();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final class RuntimeState {
        final Map<UUID,Session> sessions=new HashMap<>();
        final Map<UUID,Selection> selections=new HashMap<>();
        final Set<UUID> offline=new HashSet<>();
        final Set<UUID> delivered=new HashSet<>();
        int lastTick=Integer.MIN_VALUE;
    }
    private static final class Session {
        final String dimension;final BlockPos pos;final long openedAt;final int openedTick;
        final UUID sessionId=UUID.randomUUID();UUID nonce=UUID.randomUUID();int prisonPage,activePage,historyPage;String error="",message="";
        int requestTick=Integer.MIN_VALUE,requests;
        Session(ServerPlayer p,BlockPos pos){dimension=dimension(p);this.pos=pos.immutable();openedAt=System.nanoTime();openedTick=p.getServer().getTickCount();}
    }
    private record Selection(String dimension,Point a,Point b,Point anchor) {}
    private JusticeService(){}
    private static RuntimeState runtime(MinecraftServer s){return RUNTIMES.computeIfAbsent(s,key->new RuntimeState());}
    private static void thread(ServerPlayer p){if(p==null||p.getServer()==null||!p.getServer().isSameThread())throw failure("thread");}
    public static boolean administrator(ServerPlayer p){return p.hasPermissions(2);}
    private static String dimension(ServerPlayer p){return p.level().dimension().location().toString();}
    private static Point point(BlockPos p){return new Point(p.getX(),p.getY(),p.getZ());}
    private static BlockPos pos(Point p){return new BlockPos(p.x(),p.y(),p.z());}
    private static long now(){return Math.max(0,System.currentTimeMillis());}

    public static void open(ServerPlayer player,BlockPos anchor) {
        thread(player);
        if(!physical(player,anchor,dimension(player)))return;
        CourtService.closeSession(player);WorkService.closeSession(player);FinanceService.closeSession(player);
        RuntimeState r=runtime(player.getServer());r.sessions.put(player.getUUID(),new Session(player,anchor));
        send(player);
    }
    public static void closeSession(ServerPlayer p){thread(p);runtime(p.getServer()).sessions.remove(p.getUUID());}
    private static boolean physical(ServerPlayer player,BlockPos anchor,String dim) {
        if(!player.isAlive()||!dimension(player).equals(dim)||player.distanceToSqr(anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5)>64)return false;
        var block=player.level().getBlockState(anchor).getBlock();
        return block instanceof PrisonControlBlock||block instanceof ReinforcedLockBlock;
    }
    private static boolean valid(ServerPlayer p,Session s){
        return s!=null&&physical(p,s.pos,s.dimension)&&System.nanoTime()-s.openedAt<=300_000_000_000L
                &&(long)p.getServer().getTickCount()-s.openedTick<=6000;
    }
    public static void select(ServerPlayer p,BlockPos clicked,boolean anchor) {
        thread(p);
        try {
            if(!administrator(p))throw failure("permission");
            if(!p.isAlive()||p.distanceToSqr(clicked.getX()+.5,clicked.getY()+.5,clicked.getZ()+.5)>64
                    ||!p.level().hasChunkAt(clicked)||!p.level().getWorldBorder().isWithinBounds(clicked))throw failure("selection");
            RuntimeState r=runtime(p.getServer());Selection old=r.selections.get(p.getUUID());String dim=dimension(p);
            if(old==null||!old.dimension().equals(dim))old=new Selection(dim,null,null,null);
            Selection next=anchor?new Selection(dim,old.a(),old.b(),point(clicked.above())):
                    old.a()==null||old.b()!=null?new Selection(dim,point(clicked),null,old.anchor()):new Selection(dim,old.a(),point(clicked),old.anchor());
            r.selections.put(p.getUUID(),next);
            p.displayClientMessage(Component.translatable(anchor?"lawsjustice.selection.anchor":next.b()==null?"lawsjustice.selection.first":"lawsjustice.selection.second",clicked.getX(),anchor?clicked.getY()+1:clicked.getY(),clicked.getZ()),true);
            if(valid(p,r.sessions.get(p.getUUID())))send(p);
        }catch(RuntimeException ex){p.displayClientMessage(Component.translatable(errorKey(ex)),true);}
    }

    public static void handle(ServerPlayer p,String operation,String raw) {
        thread(p);RuntimeState r=runtime(p.getServer());Session s=r.sessions.get(p.getUUID());
        if("close".equals(operation)){r.sessions.remove(p.getUUID());return;}
        if(!valid(p,s)){r.sessions.remove(p.getUUID());closed(p,"lawsjustice.error.session");return;}
        int tick=p.getServer().getTickCount();if(s.requestTick!=tick){s.requestTick=tick;s.requests=0;}if(++s.requests>32)return;
        try {
            if(raw==null||raw.length()>8192)throw failure("request");
            JsonObject request=JsonParser.parseString(raw).getAsJsonObject();
            JusticeData data=JusticeData.get(p.getServer());JusticeLedger before=data.ledger(),next=before;
            if("refresh".equals(operation)){keys(request);send(p);return;}
            if(!administrator(p))throw failure("permission");
            if("page".equals(operation)){
                keys(request,"kind","index");String kind=text(request,"kind",16);int index=(int)number(request,"index",0,512);
                switch(kind){case "prisons"->s.prisonPage=index;case "active"->s.activePage=index;case "history"->s.historyPage=index;default->throw failure("request");}
                send(p);return;
            }
            if(!uuid(request,"nonce").equals(s.nonce)||number(request,"version",0,Long.MAX_VALUE)!=data.version())throw failure("stale");
            switch(operation){
                case "save_prison"->{
                    keys(request,"id","name","nonce","version");Selection selection=selection(p);Area area=Area.between(selection.dimension(),selection.a(),selection.b());
                    ServerLevel level=level(p.getServer(),area.dimension());validateArea(level,area);
                    if(!safe(level,p,selection.anchor())||area.contains(selection.anchor()))throw failure("release_point");
                    UUID id=optionalId(request,"id");Prison old=before.prisons().get(id);
                    next=before.putPrison(new Prison(id,text(request,"name",48),area,selection.anchor(),old==null?List.of():old.cells(),old==null?0:old.revision()));
                }
                case "save_cell"->{
                    keys(request,"prison","id","name","capacity","nonce","version");Selection selection=selection(p);UUID prison=uuid(request,"prison");
                    Area area=Area.between(selection.dimension(),selection.a(),selection.b());ServerLevel level=level(p.getServer(),area.dimension());validateArea(level,area);
                    if(!safe(level,p,selection.anchor()))throw failure("cell_spawn");
                    next=before.putCell(prison,new Cell(optionalId(request,"id"),text(request,"name",48),area,selection.anchor(),(int)number(request,"capacity",1,16)));
                }
                case "remove_cell"->{keys(request,"prison","cell","nonce","version");next=before.removeCell(uuid(request,"prison"),uuid(request,"cell"));}
                case "remove_prison"->{keys(request,"prison","nonce","version");next=before.removePrison(uuid(request,"prison"));}
                case "sentence"->{
                    keys(request,"player","prison","cell","minutes","reason","nonce","version");UUID who=uuid(request,"player");
                    ServerPlayer target=p.getServer().getPlayerList().getPlayer(who);if(target==null||!target.isAlive()||target.isSpectator())throw failure("player_offline");
                    Prison prison=prison(before,uuid(request,"prison"));Cell cell=cell(prison,uuid(request,"cell"));
                    ServerLevel level=level(p.getServer(),prison.area().dimension());level.getChunk(pos(cell.spawn()));
                    if(!safe(level,target,cell.spawn()))throw failure("cell_spawn");
                    next=before.sentence(new Sentence(UUID.randomUUID(),who,JusticeNames.name(target),prison.id(),cell.id(),p.getUUID(),JusticeNames.name(p),text(request,"reason",300),now(),Math.multiplyExact(number(request,"minutes",1,43200),1200L),0,0,0));
                    data.replace(next);s.nonce=UUID.randomUUID();arrive(target,next.active().get(who),next);
                    JusticeNotifications.send(target,"lawsjustice.notification.sentenced");s.message="lawsjustice.message.sentenced";send(p);return;
                }
                case "release"->{
                    keys(request,"player","reason","nonce","version");UUID who=uuid(request,"player");Sentence sentence=before.active().get(who);if(sentence==null)throw failure("sentence_missing");
                    Prison prison=prison(before,sentence.prison());next=before.release(who,p.getUUID(),JusticeNames.name(p),text(request,"reason",300),now());
                    data.replaceAndQueue(next,who,new ReleaseTarget(prison.area().dimension(),prison.release()));s.nonce=UUID.randomUUID();
                    ServerPlayer target=p.getServer().getPlayerList().getPlayer(who);if(target!=null)deliverRelease(target,data);
                    s.message="lawsjustice.message.released";send(p);return;
                }
                default->throw failure("request");
            }
            data.replace(next);s.nonce=UUID.randomUUID();s.message="lawsjustice.message.saved";send(p);
        }catch(RuntimeException ex){s.error=errorKey(ex);send(p);}
    }
    public static Area selectedArea(ServerPlayer p){thread(p);if(!administrator(p))throw failure("permission");Selection s=runtime(p.getServer()).selections.get(p.getUUID());if(s==null||s.a()==null||s.b()==null||!s.dimension().equals(dimension(p)))throw failure("selection");return Area.between(s.dimension(),s.a(),s.b());}
    private static Selection selection(ServerPlayer p){Selection s=runtime(p.getServer()).selections.get(p.getUUID());if(s==null||s.a()==null||s.b()==null||s.anchor()==null||!s.dimension().equals(dimension(p)))throw failure("selection");return s;}
    private static Prison prison(JusticeLedger ledger,UUID id){Prison p=ledger.prisons().get(id);if(p==null)throw failure("prison_missing");return p;}
    private static Cell cell(Prison p,UUID id){return p.cells().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(()->failure("cell_missing"));}

    public static JsonObject snapshot(ServerPlayer p) {
        thread(p);RuntimeState r=runtime(p.getServer());Session s=r.sessions.get(p.getUUID());JsonObject out=new JsonObject();out.addProperty("protocol",1);out.addProperty("open",valid(p,s));
        if(!valid(p,s))return out;out.addProperty("session",s.sessionId.toString());
        JusticeData data=JusticeData.get(p.getServer());JusticeLedger ledger=data.ledger();boolean admin=administrator(p);
        out.addProperty("admin",admin);out.addProperty("nonce",s.nonce.toString());out.addProperty("version",data.version());
        if(!s.error.isEmpty())out.addProperty("error",s.error);if(!s.message.isEmpty())out.addProperty("message",s.message);
        Selection selection=admin?r.selections.get(p.getUUID()):null;JsonObject selected=new JsonObject();
        if(selection!=null){selected.addProperty("dimension",selection.dimension());if(selection.a()!=null)selected.add("a",JSON.toJsonTree(selection.a()));if(selection.b()!=null)selected.add("b",JSON.toJsonTree(selection.b()));if(selection.anchor()!=null)selected.add("anchor",JSON.toJsonTree(selection.anchor()));}
        out.add("selection",selected);JsonObject pages=new JsonObject();
        List<Prison> prisons=ledger.prisons().values().stream().sorted(Comparator.comparing(Prison::name).thenComparing(Prison::id)).toList();
        JsonArray prisonRows=new JsonArray();
        if(admin){s.prisonPage=page(s.prisonPage,prisons.size(),4);pages.add("prisons",pageMeta(s.prisonPage,prisons.size(),4));for(Prison prison:slice(prisons,s.prisonPage,4))prisonRows.add(prisonJson(prison,ledger,true));}
        else {Sentence own=ledger.active().get(p.getUUID());if(own!=null)prisonRows.add(prisonJson(prison(ledger,own.prison()),ledger,false));}
        out.add("prisons",prisonRows);JsonArray players=new JsonArray();
        if(admin)for(ServerPlayer player:p.getServer().getPlayerList().getPlayers().stream().limit(256).toList()){JsonObject row=new JsonObject();row.addProperty("id",player.getUUID().toString());row.addProperty("name",JusticeNames.name(player));players.add(row);}
        out.add("players",players);JsonArray active=new JsonArray();
        if(admin){List<Sentence> values=ledger.active().values().stream().sorted(Comparator.comparing(Sentence::issuedAt).reversed()).toList();s.activePage=page(s.activePage,values.size(),16);pages.add("active",pageMeta(s.activePage,values.size(),16));for(Sentence sentence:slice(values,s.activePage,16))active.add(sentenceJson(sentence,ledger));}
        out.add("active",active);JsonArray history=new JsonArray();
        if(admin){List<JusticeLedger.Decision> entries=new ArrayList<>(ledger.history());java.util.Collections.reverse(entries);s.historyPage=page(s.historyPage,entries.size(),16);pages.add("history",pageMeta(s.historyPage,entries.size(),16));for(var entry:slice(entries,s.historyPage,16))history.add(JSON.toJsonTree(entry));}
        out.add("history",history);out.add("pages",pages);Sentence self=ledger.active().get(p.getUUID());out.add("self",self==null?JsonNull.INSTANCE:sentenceJson(self,ledger));return out;
    }
    private static JsonObject prisonJson(Prison p,JusticeLedger ledger,boolean admin){
        JsonObject out=new JsonObject();out.addProperty("id",p.id().toString());out.addProperty("name",p.name());out.addProperty("dimension",p.area().dimension());
        JsonArray cells=new JsonArray();if(admin){out.add("min",JSON.toJsonTree(p.area().min()));out.add("max",JSON.toJsonTree(p.area().max()));out.add("release",JSON.toJsonTree(p.release()));out.addProperty("revision",p.revision());
            for(Cell c:p.cells()){JsonObject row=new JsonObject();row.addProperty("id",c.id().toString());row.addProperty("name",c.name());row.add("min",JSON.toJsonTree(c.area().min()));row.add("max",JSON.toJsonTree(c.area().max()));row.add("spawn",JSON.toJsonTree(c.spawn()));row.addProperty("capacity",c.capacity());row.addProperty("occupied",ledger.active().values().stream().filter(s->s.cell().equals(c.id())).count());cells.add(row);}}
        out.add("cells",cells);return out;
    }
    private static JsonObject sentenceJson(Sentence s,JusticeLedger l){JsonObject o=JSON.toJsonTree(s).getAsJsonObject();Prison p=prison(l,s.prison());o.addProperty("prisonName",p.name());o.addProperty("cellName",cell(p,s.cell()).name());o.addProperty("remainingTicks",s.remainingTicks());return o;}
    private static int page(int index,int count,int size){return Math.min(index,Math.max(0,(count-1)/size));}
    private static JsonObject pageMeta(int index,int count,int size){JsonObject o=new JsonObject();o.addProperty("index",index);o.addProperty("total",Math.max(1,(count+size-1)/size));o.addProperty("records",count);return o;}
    private static <T> List<T> slice(List<T> all,int index,int size){int from=Math.min(all.size(),index*size);return all.subList(from,Math.min(all.size(),from+size));}
    private static void send(ServerPlayer p){
        try{JsonObject state=snapshot(p);JusticeNetwork.send(p,state.toString());Session s=runtime(p.getServer()).sessions.get(p.getUUID());if(s!=null){s.error="";s.message="";}}
        catch(RuntimeException ex){LOG.warn("Unable to send Laws & Justice state",ex);closed(p,"lawsjustice.error.storage");}
    }
    private static void closed(ServerPlayer p,String error){JsonObject o=new JsonObject();o.addProperty("protocol",1);o.addProperty("open",false);o.addProperty("error",error);try{JusticeNetwork.send(p,o.toString());}catch(RuntimeException ignored){}}

    public static void login(ServerPlayer p){thread(p);runtime(p.getServer()).offline.remove(p.getUUID());JusticeData data=JusticeData.get(p.getServer());if(data.pendingReleases().containsKey(p.getUUID()))deliverRelease(p,data);Sentence s=data.ledger().active().get(p.getUUID());if(s!=null&&p.isAlive())contain(p,s,data.ledger());}
    public static void logout(ServerPlayer p){thread(p);RuntimeState r=runtime(p.getServer());r.sessions.remove(p.getUUID());r.selections.remove(p.getUUID());r.offline.add(p.getUUID());JusticeData.get(p.getServer()).flush();}
    public static void stop(MinecraftServer s){try{JusticeData.get(s).flush();}finally{RUNTIMES.remove(s);JusticeData.drop(s);}}
    public static void tick(MinecraftServer server){
        RuntimeState r=runtime(server);int tick=server.getTickCount();if(r.lastTick==tick)return;r.lastTick=tick;
        JusticeData data;
        try{data=JusticeData.get(server);}catch(RuntimeException failure){if(tick%200==0)LOG.error("Custody data cannot be read; restore the world backup before using justice",failure);return;}
        for(ServerPlayer p:List.copyOf(server.getPlayerList().getPlayers())) {
            if(r.offline.contains(p.getUUID()))continue;
            if(data.pendingReleases().containsKey(p.getUUID())){if(!r.delivered.contains(p.getUUID())||tick%100==0)deliverRelease(p,data);continue;}
            JusticeLedger before=data.ledger();Sentence sentence=before.active().get(p.getUUID());if(sentence==null)continue;
            JusticeLedger after=before.advance(p.getUUID(),1,now());
            if(after.active().containsKey(p.getUUID())){data.replaceProgress(after);if(p.isAlive())contain(p,after.active().get(p.getUUID()),after);}
            else {
                Prison prison=prison(before,sentence.prison());ReleaseTarget release=new ReleaseTarget(prison.area().dimension(),prison.release());
                try{data.replaceAndQueue(after,p.getUUID(),release);}catch(RuntimeException failure){data.completedInMemory(after,p.getUUID(),release);LOG.error("Sentence completed; retrying its durable checkpoint",failure);}
                deliverRelease(p,data);
            }
        }
        if(tick%100==0)try{data.flush();}catch(RuntimeException failure){LOG.error("Custody checkpoint failed; online progress is retained in memory",failure);}
        if(tick%20==0)for(var entry:List.copyOf(r.sessions.entrySet())){ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());if(p==null||!valid(p,entry.getValue())){r.sessions.remove(entry.getKey());if(p!=null)closed(p,"lawsjustice.error.session");}else send(p);}
    }
    private static void contain(ServerPlayer p,Sentence sentence,JusticeLedger ledger){ if(!inside(p,prison(ledger,sentence.prison()).area()))arrive(p,sentence,ledger); }
    private static void arrive(ServerPlayer p,Sentence sentence,JusticeLedger ledger){
        Prison prison=prison(ledger,sentence.prison());
        ServerLevel target=level(p.getServer(),prison.area().dimension());Cell c=cell(prison,sentence.cell());target.getChunk(pos(c.spawn()));
        Point landing=landing(target,p,c.spawn(),prison.area(),true);
        p.stopRiding();p.teleportTo(target,landing.x()+.5,landing.y(),landing.z()+.5,p.getYRot(),p.getXRot());p.fallDistance=0;
    }
    private static boolean inside(ServerPlayer p,Area a){if(!dimension(p).equals(a.dimension()))return false;AABB b=p.getBoundingBox();return b.minX>=a.min().x()&&b.maxX<=a.max().x()+1D&&b.minY>=a.min().y()&&b.maxY<=a.max().y()+1D&&b.minZ>=a.min().z()&&b.maxZ<=a.max().z()+1D;}
    private static void deliverRelease(ServerPlayer p,JusticeData data){
        ReleaseTarget release=data.pendingReleases().get(p.getUUID());if(release==null||!p.isAlive())return;
        RuntimeState runtime=runtime(p.getServer());
        if(runtime.delivered.contains(p.getUUID())){try{data.completeRelease(p.getUUID());runtime.delivered.remove(p.getUUID());}catch(RuntimeException failure){LOG.warn("Release checkpoint will be retried",failure);}return;}
        ServerLevel target=p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(release.dimension())));
        Point destination=null;
        if(target!=null){target.getChunk(pos(release.point()));destination=landing(target,p,release.point(),null,false);}
        if(destination==null){target=p.getServer().overworld();BlockPos spawn=target.getSharedSpawnPos();target.getChunk(spawn);BlockPos surface=target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,spawn);destination=landing(target,p,point(surface),null,false);if(destination==null)destination=point(surface);}
        p.stopRiding();p.teleportTo(target,destination.x()+.5,destination.y(),destination.z()+.5,p.getYRot(),p.getXRot());p.fallDistance=0;
        runtime.delivered.add(p.getUUID());
        try{data.completeRelease(p.getUUID());runtime.delivered.remove(p.getUUID());}catch(RuntimeException failure){LOG.warn("Release checkpoint will be retried",failure);}
        runtime(p.getServer()).sessions.remove(p.getUUID());closed(p,"");JusticeNotifications.send(p,"lawsjustice.notification.released");
    }
    private static Point landing(ServerLevel level,ServerPlayer p,Point origin,Area area,boolean allowUnsafeFallback){
        if(safe(level,p,origin)&&(area==null?!protectedArea(level,pos(origin)):area.contains(origin)))return origin;
        for(int radius=1;radius<=6;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(Math.abs(x)!=radius&&Math.abs(z)!=radius)continue;
            for(int dy=-3;dy<=3;dy++){Point next;try{next=new Point(origin.x()+x,origin.y()+dy,origin.z()+z);}catch(RuntimeException ignored){continue;}
                if((area==null?!protectedArea(level,pos(next)):area.contains(next)&&area.contains(new Point(next.x(),next.y()+1,next.z())))&&level.hasChunkAt(pos(next))&&safe(level,p,next))return next;
            }
        }
        return allowUnsafeFallback?origin:null;
    }
    private static boolean safe(ServerLevel level,ServerPlayer p,Point point){
        BlockPos at=pos(point);if(!level.hasChunkAt(at)||at.getY()<level.getMinBuildHeight()+1||at.getY()+2>=level.getMaxBuildHeight())return false;
        AABB body=new AABB(point.x()+.2,point.y(),point.z()+.2,point.x()+.8,point.y()+1.8,point.z()+.8);
        if(!level.getWorldBorder().isWithinBounds(body)||!level.noCollision(p,body)||!level.getFluidState(at).isEmpty()||!level.getFluidState(at.below()).isEmpty())return false;
        var floor=level.getBlockState(at.below());return !floor.getCollisionShape(level,at.below()).isEmpty()&&!floor.is(Blocks.MAGMA_BLOCK)&&!floor.is(Blocks.CACTUS)&&!floor.is(Blocks.CAMPFIRE)&&!floor.is(Blocks.SOUL_CAMPFIRE);
    }
    private static ServerLevel level(MinecraftServer server,String dimension){ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(dimension)));if(level==null)throw failure("dimension");return level;}
    private static void validateArea(ServerLevel l,Area a){if(a.min().y()<l.getMinBuildHeight()||a.max().y()>=l.getMaxBuildHeight()||!l.getWorldBorder().isWithinBounds(pos(a.min()))||!l.getWorldBorder().isWithinBounds(pos(a.max())))throw failure("area");}
    public static void validateCell(MinecraftServer server,Prison prison,Cell cell,ServerPlayer player) {
        ServerLevel target=level(server,prison.area().dimension());target.getChunk(pos(cell.spawn()));
        if(!safe(target,player,cell.spawn()))throw failure("cell_spawn");
    }
    public static void transferToCell(ServerPlayer player) {
        thread(player);JusticeLedger ledger=JusticeData.get(player.getServer()).ledger();Sentence sentence=ledger.active().get(player.getUUID());
        if(sentence!=null&&player.isAlive())arrive(player,sentence,ledger);
    }
    public static boolean imprisoned(ServerPlayer p){return JusticeData.get(p.getServer()).ledger().active().containsKey(p.getUUID());}
    public static boolean protectedArea(ServerLevel level,BlockPos at){return JusticeData.get(level.getServer()).ledger().prisons().values().stream().anyMatch(prison->prison.area().dimension().equals(level.dimension().location().toString())&&prison.area().contains(point(at)));}
    public static boolean canBreak(ServerPlayer p,BlockPos at){return !imprisoned(p)&&!CourtService.restricted(p);}
    public static boolean canPlace(ServerPlayer p,BlockPos at){return canBreak(p,at);}
    public static boolean canUseDoor(ServerPlayer p,BlockPos at){return !imprisoned(p)&&!CourtService.restricted(p);}
    public static boolean canInteract(ServerPlayer p,BlockPos at){if(!imprisoned(p))return true;var b=p.level().getBlockState(at).getBlock();return b instanceof PrisonControlBlock||b instanceof ReinforcedLockBlock||b instanceof pt.lawsjustice.block.JusticeDeskBlock||b instanceof pt.lawsjustice.block.WorkStationBlock;}

    private static void keys(JsonObject o,String... allowed){Set<String> keys=Set.of(allowed);if(!keys.containsAll(o.keySet()))throw failure("request");}
    private static String text(JsonObject o,String k,int max){JsonElement v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw failure("request");String t=v.getAsString().strip();if(t.isEmpty()||t.length()>max||t.codePoints().anyMatch(Character::isISOControl))throw failure("request");return t;}
    private static long number(JsonObject o,String k,long min,long max){JsonElement v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw failure("request");long n;try{n=v.getAsBigDecimal().longValueExact();}catch(ArithmeticException e){throw failure("request");}if(n<min||n>max)throw failure("request");return n;}
    private static UUID uuid(JsonObject o,String k){try{return UUID.fromString(text(o,k,36));}catch(IllegalArgumentException e){throw failure("request");}}
    private static UUID optionalId(JsonObject o,String k){if(!o.has(k)||o.get(k).isJsonNull()||o.get(k).isJsonPrimitive()&&o.get(k).getAsJsonPrimitive().isString()&&o.get(k).getAsString().isEmpty())return UUID.randomUUID();return uuid(o,k);}
    private static IllegalStateException failure(String key){return new IllegalStateException("lawsjustice.error."+key);}
    private static String errorKey(RuntimeException e){String key=e.getMessage();return key!=null&&key.startsWith("lawsjustice.error.")?key:"lawsjustice.error.request";}
}
