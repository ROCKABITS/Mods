package pt.lawsjustice.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.block.JusticeDeskBlock;
import pt.lawsjustice.compat.CouncilAuthority;
import pt.lawsjustice.compat.JusticeNames;
import pt.lawsjustice.compat.JusticeNotifications;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.domain.CourtLedger;
import pt.lawsjustice.domain.CourtLedger.CaseFile;
import pt.lawsjustice.domain.CourtLedger.Evidence;
import pt.lawsjustice.domain.CourtLedger.Warrant;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.domain.JusticeLedger.Sentence;
import pt.lawsjustice.network.JusticeNetwork;

/** Physical courts and server-issued papers. Judicial authority is read afresh for every decision. */
public final class CourtService {
    private static final Gson JSON=new Gson();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final WeakHashMap<MinecraftServer,RuntimeState> RUNTIMES=new WeakHashMap<>();
    private static final class RuntimeState {final Map<UUID,Session> sessions=new HashMap<>();int lastTick=Integer.MIN_VALUE;}
    private static final class Session {
        final BlockPos desk;final UUID document;final String dimension;final long openedAt;final int openedTick;
        UUID nonce=UUID.randomUUID();int casePage,warrantPage,prisonPage,requests,requestTick=Integer.MIN_VALUE;
        String error="",message="";
        Session(ServerPlayer p,BlockPos desk,UUID document){this.desk=desk==null?null:desk.immutable();this.document=document;dimension=dimension(p);openedAt=System.nanoTime();openedTick=p.getServer().getTickCount();}
    }
    private record Role(boolean admin,boolean judge,boolean president,String name,String authority,String warning){}
    private CourtService(){}
    private static RuntimeState runtime(MinecraftServer s){return RUNTIMES.computeIfAbsent(s,k->new RuntimeState());}
    private static String dimension(ServerPlayer p){return p.level().dimension().location().toString();}
    private static long now(){return Math.max(0,System.currentTimeMillis());}
    private static void thread(ServerPlayer p){if(p==null||p.getServer()==null||!p.getServer().isSameThread())throw failure("thread");}
    private static Role role(ServerPlayer p){
        boolean admin=p.hasPermissions(2);CouncilAuthority.President president=null;String warning="";
        if(CouncilAuthority.loaded())try{president=CouncilAuthority.governor(p.getServer(),now());}catch(RuntimeException|LinkageError unavailable){warning="lawsjustice.error.council_unavailable";}
        boolean elected=president!=null&&president.id().equals(p.getUUID());
        return new Role(admin,admin||elected,elected,president==null?"":president.name(),elected?CourtLedger.PRESIDENT:admin?CourtLedger.ADMIN_EMERGENCY:"",warning);
    }
    public static void open(ServerPlayer p,BlockPos desk){thread(p);if(!physical(p,desk,dimension(p)))return;JusticeService.closeSession(p);runtime(p.getServer()).sessions.put(p.getUUID(),new Session(p,desk,null));send(p);}
    public static void closeSession(ServerPlayer p){thread(p);runtime(p.getServer()).sessions.remove(p.getUUID());}
    private static boolean physical(ServerPlayer p,BlockPos pos,String dim){return p.isAlive()&&dimension(p).equals(dim)&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&p.level().getBlockState(pos).getBlock()instanceof JusticeDeskBlock;}
    private static boolean valid(ServerPlayer p,Session s){return s!=null&&p.isAlive()&&dimension(p).equals(s.dimension)&&System.nanoTime()-s.openedAt<=300_000_000_000L&&(long)p.getServer().getTickCount()-s.openedTick<=6000
            &&(s.desk!=null?physical(p,s.desk,s.dimension):s.document!=null&&holding(p,s.document));}
    private static UUID paperId(ItemStack stack){
        if(!stack.is(BlockRegistry.WARRANT.get()))return null;
        CustomData custom=stack.get(DataComponents.CUSTOM_DATA);if(custom==null)return null;CompoundTag tag=custom.copyTag();
        if(!tag.contains("lawsjustice_warrant",Tag.TAG_STRING))return null;
        try{String raw=tag.getString("lawsjustice_warrant");UUID id=UUID.fromString(raw);return id.toString().equals(raw)?id:null;}catch(IllegalArgumentException invalid){return null;}
    }
    private static boolean holding(ServerPlayer p,UUID id){return id.equals(paperId(p.getMainHandItem()))||id.equals(paperId(p.getOffhandItem()));}
    public static void inspectWarrant(ServerPlayer p,ItemStack stack){
        thread(p);UUID id=paperId(stack);
        if(id==null||!holding(p,id)||!JusticeData.get(p.getServer()).court().warrants().containsKey(id)){p.displayClientMessage(Component.translatable("lawsjustice.error.warrant_invalid"),false);return;}
        JusticeService.closeSession(p);runtime(p.getServer()).sessions.put(p.getUUID(),new Session(p,null,id));send(p);
    }
    public static void handle(ServerPlayer p,String operation,String raw){
        thread(p);RuntimeState r=runtime(p.getServer());Session s=r.sessions.get(p.getUUID());
        if("court_close".equals(operation)){r.sessions.remove(p.getUUID());return;}
        if(!valid(p,s)){r.sessions.remove(p.getUUID());closed(p,"lawsjustice.error.session");return;}
        int tick=p.getServer().getTickCount();if(s.requestTick!=tick){s.requestTick=tick;s.requests=0;}if(++s.requests>32)return;
        try{
            if(raw==null||raw.length()>8192)throw failure("request");JsonObject request=JsonParser.parseString(raw).getAsJsonObject();
            if("court_refresh".equals(operation)){keys(request);send(p);return;}
            // A paper inspection is read-only and never grants a remote court session.
            if(s.desk==null)throw failure("session");
            JusticeData data=JusticeData.get(p.getServer());CourtLedger court=data.court();Role role=role(p);
            if("court_page".equals(operation)){
                keys(request,"kind","index");int page=(int)number(request,"index",0,512);
                switch(text(request,"kind",16,false)){case "cases"->s.casePage=page;case "warrants"->s.warrantPage=page;case "prisons"->{judge(role);s.prisonPage=page;}default->throw failure("request");}
                send(p);return;
            }
            if(!uuid(request,"nonce").equals(s.nonce)||number(request,"version",0,Long.MAX_VALUE)!=data.version())throw failure("stale");
            CourtLedger next=court;
            switch(operation){
                case "court_file"->{
                    keys(request,"accused","accusation","nonce","version");ServerPlayer accused=p.getServer().getPlayerList().getPlayer(uuid(request,"accused"));
                    if(accused==null)throw failure("player_offline");
                    next=court.file(new CaseFile(UUID.randomUUID(),p.getUUID(),JusticeNames.name(p),accused.getUUID(),JusticeNames.name(accused),text(request,"accusation",800,true),List.of(),CourtLedger.OPEN,now(),0,"","","","",0,""));
                }
                case "court_evidence"->{
                    keys(request,"case","text","nonce","version");CaseFile file=visible(court,uuid(request,"case"),p,role);
                    next=court.evidence(file.id(),new Evidence(UUID.randomUUID(),p.getUUID(),JusticeNames.name(p),text(request,"text",1200,true),Math.max(now(),file.openedAt())));
                }
                case "court_hear"->{keys(request,"case","nonce","version");judge(role);next=court.hear(uuid(request,"case"),p.getUUID(),JusticeNames.name(p),role.authority(),now());}
                case "court_acquit"->{keys(request,"case","reason","nonce","version");judge(role);next=court.decide(uuid(request,"case"),p.getUUID(),JusticeNames.name(p),role.authority(),text(request,"reason",800,true),"",false,now());}
                case "court_convict"->{
                    keys(request,"case","prison","cell","minutes","reason","nonce","version");judge(role);CaseFile file=visible(court,uuid(request,"case"),p,role);
                    JusticeLedger custody=data.ledger();var prison=custody.prisons().get(uuid(request,"prison"));if(prison==null)throw failure("prison_missing");
                    var cell=prison.cells().stream().filter(c->c.id().equals(uuid(request,"cell"))).findFirst().orElseThrow(()->failure("cell_missing"));
                    ServerPlayer accused=p.getServer().getPlayerList().getPlayer(file.accused());JusticeService.validateCell(p.getServer(),prison,cell,accused);
                    String verdict=text(request,"reason",300,true);String reason=verdict.replace('\r',' ').replace('\n',' ');
                    Sentence sentence=new Sentence(UUID.randomUUID(),file.accused(),file.accusedName(),prison.id(),cell.id(),p.getUUID(),JusticeNames.name(p),reason,now(),Math.multiplyExact(number(request,"minutes",1,43200),1200L),0,0,0);
                    next=court.decide(file.id(),p.getUUID(),JusticeNames.name(p),role.authority(),verdict,sentence.id().toString(),true,now());
                    data.replace(custody.sentence(sentence),next);s.nonce=UUID.randomUUID();s.message="lawsjustice.message.court_decided";
                    if(accused!=null){JusticeService.transferToCell(accused);JusticeNotifications.send(accused,"lawsjustice.notification.sentenced");}
                    send(p);return;
                }
                case "court_warrant"->{
                    keys(request,"accused","reason","hours","case","nonce","version");judge(role);UUID accused=uuid(request,"accused");String name=accusedName(p.getServer(),court,accused);
                    String related=optionalUuid(request,"case");long at=now();long expires=Math.addExact(at,Math.multiplyExact(number(request,"hours",1,168),3_600_000L));
                    Warrant warrant=new Warrant(UUID.randomUUID(),accused,name,p.getUUID(),JusticeNames.name(p),role.authority(),text(request,"reason",300,true),at,expires,CourtLedger.ACTIVE,related);
                    data.replaceCourt(court.issue(warrant));s.nonce=UUID.randomUUID();givePaper(p,warrant.id());s.message="lawsjustice.message.warrant_issued";send(p);return;
                }
                case "court_revoke"->{keys(request,"warrant","nonce","version");judge(role);next=court.revoke(uuid(request,"warrant"));}
                case "court_copy"->{keys(request,"warrant","nonce","version");judge(role);UUID id=uuid(request,"warrant");if(!court.warrants().containsKey(id))throw failure("warrant_missing");s.nonce=UUID.randomUUID();givePaper(p,id);s.message="lawsjustice.message.warrant_copied";send(p);return;}
                default->throw failure("request");
            }
            data.replaceCourt(next);s.nonce=UUID.randomUUID();s.message=operation.equals("court_file")?"lawsjustice.message.case_filed":operation.equals("court_evidence")?"lawsjustice.message.evidence_saved":"lawsjustice.message.court_saved";send(p);
        }catch(RuntimeException rejected){s.error=errorKey(rejected);send(p);}
    }
    private static void givePaper(ServerPlayer p,UUID id){ItemStack stack=new ItemStack(BlockRegistry.WARRANT.get());CompoundTag tag=new CompoundTag();tag.putString("lawsjustice_warrant",id.toString());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));if(!p.getInventory().add(stack))p.drop(stack,false);}
    private static String accusedName(MinecraftServer server,CourtLedger court,UUID id){ServerPlayer player=server.getPlayerList().getPlayer(id);if(player!=null)return JusticeNames.name(player);return court.cases().values().stream().filter(c->c.accused().equals(id)).map(CaseFile::accusedName).findFirst().orElseThrow(()->failure("player_offline"));}
    private static void judge(Role role){if(!role.judge())throw failure(role.warning().isEmpty()?"court_authority":"council_unavailable");}
    private static CaseFile visible(CourtLedger court,UUID id,ServerPlayer player,Role role){CaseFile file=court.cases().get(id);if(file==null||!role.judge()&&!file.accused().equals(player.getUUID())&&!file.complainant().equals(player.getUUID()))throw failure("case_missing");return file;}

    public static JsonObject snapshot(ServerPlayer p){
        thread(p);Session s=runtime(p.getServer()).sessions.get(p.getUUID());JsonObject out=new JsonObject();out.addProperty("protocol",2);out.addProperty("kind",s!=null&&s.document!=null?"warrant":"court");out.addProperty("open",valid(p,s));if(!valid(p,s))return out;
        JusticeData data=JusticeData.get(p.getServer());CourtLedger court=data.court();
        if(s.document!=null){Warrant warrant=court.warrants().get(s.document);if(warrant==null){out.addProperty("open",false);out.addProperty("error","lawsjustice.error.warrant_invalid");return out;}
            boolean valid=court.validWarrant(warrant.id(),warrant.accused(),now());out.addProperty("valid",valid);out.add("warrant",JSON.toJsonTree(warrant));if(!valid)out.addProperty("warning","lawsjustice.error.warrant_inactive");return out;}
        Role role=role(p);out.addProperty("nonce",s.nonce.toString());out.addProperty("version",data.version());out.addProperty("admin",role.admin());out.addProperty("judge",role.judge());out.addProperty("president",role.president());out.addProperty("presidentName",role.name());out.addProperty("authorityKind",role.authority());
        if(!role.warning().isEmpty())out.addProperty("warning",role.warning());if(!s.error.isEmpty())out.addProperty("error",s.error);if(!s.message.isEmpty())out.addProperty("message",s.message);
        JsonObject pages=new JsonObject();List<CaseFile> cases=court.cases().values().stream().filter(c->role.judge()||c.accused().equals(p.getUUID())||c.complainant().equals(p.getUUID())).sorted(Comparator.comparing(CaseFile::openedAt).reversed().thenComparing(CaseFile::id)).toList();
        s.casePage=page(s.casePage,cases.size(),2);pages.add("cases",pageInfo(s.casePage,cases.size(),2));JsonArray caseRows=new JsonArray();for(CaseFile row:slice(cases,s.casePage,2))caseRows.add(JSON.toJsonTree(row));out.add("cases",caseRows);
        List<Warrant> warrants=court.warrants().values().stream().filter(w->role.judge()||w.accused().equals(p.getUUID())||holding(p,w.id())).sorted(Comparator.comparing(Warrant::issuedAt).reversed().thenComparing(Warrant::id)).toList();
        s.warrantPage=page(s.warrantPage,warrants.size(),8);pages.add("warrants",pageInfo(s.warrantPage,warrants.size(),8));JsonArray warrantRows=new JsonArray();for(Warrant row:slice(warrants,s.warrantPage,8))warrantRows.add(JSON.toJsonTree(row));out.add("warrants",warrantRows);
        JsonArray people=new JsonArray();for(ServerPlayer online:p.getServer().getPlayerList().getPlayers().stream().limit(256).toList()){JsonObject row=new JsonObject();row.addProperty("id",online.getUUID().toString());row.addProperty("name",JusticeNames.name(online));people.add(row);}out.add("players",people);
        JsonArray prisons=new JsonArray();if(role.judge()){
            var registered=data.ledger().prisons().values().stream().sorted(Comparator.comparing(JusticeLedger.Prison::name)).toList();s.prisonPage=page(s.prisonPage,registered.size(),4);pages.add("prisons",pageInfo(s.prisonPage,registered.size(),4));
            for(var prison:slice(registered,s.prisonPage,4)){JsonObject row=new JsonObject();row.addProperty("id",prison.id().toString());row.addProperty("name",prison.name());JsonArray cells=new JsonArray();for(var cell:prison.cells()){JsonObject c=new JsonObject();c.addProperty("id",cell.id().toString());c.addProperty("name",cell.name());c.addProperty("capacity",cell.capacity());c.addProperty("occupied",data.ledger().active().values().stream().filter(a->a.cell().equals(cell.id())).count());cells.add(c);}row.add("cells",cells);prisons.add(row);}
        }out.add("prisons",prisons);out.add("pages",pages);Sentence self=data.ledger().active().get(p.getUUID());out.add("self",self==null?JsonNull.INSTANCE:JSON.toJsonTree(self));return out;
    }
    private static int page(int index,int count,int size){return Math.min(Math.max(0,index),Math.max(0,(count-1)/size));}
    private static JsonObject pageInfo(int index,int count,int size){JsonObject p=new JsonObject();p.addProperty("index",index);p.addProperty("total",Math.max(1,(count+size-1)/size));p.addProperty("records",count);return p;}
    private static <T>List<T> slice(List<T> list,int page,int size){int first=Math.min(list.size(),page*size);return list.subList(first,Math.min(list.size(),first+size));}
    private static void send(ServerPlayer p){try{JusticeNetwork.send(p,snapshot(p));Session s=runtime(p.getServer()).sessions.get(p.getUUID());if(s!=null){s.error="";s.message="";}}catch(RuntimeException error){LOG.warn("Cannot send court state",error);closed(p,"lawsjustice.error.storage");}}
    private static void closed(ServerPlayer p,String error){JsonObject o=new JsonObject();o.addProperty("kind","court");o.addProperty("protocol",2);o.addProperty("open",false);o.addProperty("error",error);try{JusticeNetwork.send(p,o);}catch(RuntimeException ignored){}}
    public static void applyHandcuffs(ServerPlayer actor,ServerPlayer target){try{CourtRestraints.apply(actor,target);}catch(RuntimeException error){actor.displayClientMessage(Component.translatable(errorKey(error)),false);}}
    public static void releaseHandcuffs(ServerPlayer actor,ServerPlayer target){try{CourtRestraints.release(actor,target);}catch(RuntimeException error){actor.displayClientMessage(Component.translatable(errorKey(error)),false);}}
    public static boolean restricted(ServerPlayer p){return CourtRestraints.restricted(p);}
    public static void login(ServerPlayer p){thread(p);CourtRestraints.login(p);}
    public static void logout(ServerPlayer p){thread(p);runtime(p.getServer()).sessions.remove(p.getUUID());CourtRestraints.logout(p);}
    public static void stop(MinecraftServer server){RUNTIMES.remove(server);}
    public static void tick(MinecraftServer server){RuntimeState r=runtime(server);int tick=server.getTickCount();if(r.lastTick==tick)return;r.lastTick=tick;CourtRestraints.tick(server);
        if(tick%20==0)for(var entry:List.copyOf(r.sessions.entrySet())){ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());if(p==null||!valid(p,entry.getValue())){r.sessions.remove(entry.getKey());if(p!=null)closed(p,"lawsjustice.error.session");}else send(p);}}
    private static void keys(JsonObject o,String...allowed){if(!Set.of(allowed).containsAll(o.keySet()))throw failure("request");}
    private static String text(JsonObject o,String key,int max,boolean prose){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw failure("request");String s=v.getAsString().strip();if(s.isEmpty()||s.length()>max||s.codePoints().anyMatch(c->Character.isISOControl(c)&&(!prose||c!='\r'&&c!='\n')))throw failure("request");return s;}
    private static UUID uuid(JsonObject o,String key){try{String raw=text(o,key,36,false);UUID id=UUID.fromString(raw);if(!id.toString().equals(raw))throw failure("request");return id;}catch(IllegalArgumentException e){throw failure("request");}}
    private static String optionalUuid(JsonObject o,String key){if(!o.has(key)||o.get(key).isJsonNull()||o.get(key).isJsonPrimitive()&&o.get(key).getAsJsonPrimitive().isString()&&o.get(key).getAsString().isEmpty())return "";return uuid(o,key).toString();}
    private static long number(JsonObject o,String key,long min,long max){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw failure("request");long n;try{n=v.getAsBigDecimal().longValueExact();}catch(ArithmeticException invalid){throw failure("request");}if(n<min||n>max)throw failure("request");return n;}
    private static IllegalStateException failure(String key){return new IllegalStateException("lawsjustice.error."+key);}
    private static String errorKey(RuntimeException e){String key=e.getMessage();return key!=null&&key.startsWith("lawsjustice.error.")?key:"lawsjustice.error.request";}
}
