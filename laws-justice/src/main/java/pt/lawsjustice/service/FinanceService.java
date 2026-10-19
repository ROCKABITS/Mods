package pt.lawsjustice.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import pt.lawsjustice.block.JusticeDeskBlock;
import pt.lawsjustice.block.RegisterBookBlock;
import pt.lawsjustice.compat.JusticeNames;
import pt.lawsjustice.compat.JusticeNotifications;
import pt.lawsjustice.compat.JusticeTreasury;
import pt.lawsjustice.compat.JusticeWallet;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.domain.CourtLedger;
import pt.lawsjustice.domain.FinanceAmounts;
import pt.lawsjustice.domain.FinanceLedger;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.network.JusticeNetwork;

/** Physical voluntary payments and public final records. Every judicial mutation checks live authority. */
public final class FinanceService {
    private static final Gson JSON=new Gson();
    private static final WeakHashMap<MinecraftServer,RuntimeState> RUNTIMES=new WeakHashMap<>();
    private static final class RuntimeState {final Map<UUID,Session> sessions=new HashMap<>();int lastTick=Integer.MIN_VALUE;}
    private static final class Session {
        final BlockPos pos;final String dimension;final long openedAt=System.nanoTime();final int openedTick;final boolean book;
        final UUID sessionId=UUID.randomUUID();UUID nonce=UUID.randomUUID();int chargePage,recordPage,sentencePage,auditPage,requestTick=-1,requests;String error="",message="";
        Session(ServerPlayer p,BlockPos pos,boolean book){this.pos=pos.immutable();dimension=dim(p);openedTick=p.getServer().getTickCount();this.book=book;}
    }
    private FinanceService(){}
    private static RuntimeState runtime(MinecraftServer s){return RUNTIMES.computeIfAbsent(s,k->new RuntimeState());}
    private static void thread(ServerPlayer p){if(p==null||p.getServer()==null||!p.getServer().isSameThread())throw error("thread");}
    private static String dim(ServerPlayer p){return p.level().dimension().location().toString();}
    private static long now(){return Math.max(0,System.currentTimeMillis());}
    private static boolean physical(ServerPlayer p,BlockPos pos,boolean book){return p.isAlive()&&p.distanceToSqr(pos.getCenter())<=64&&(book?p.level().getBlockState(pos).getBlock()instanceof RegisterBookBlock:p.level().getBlockState(pos).getBlock()instanceof JusticeDeskBlock);}
    private static boolean valid(ServerPlayer p,Session s){return s!=null&&dim(p).equals(s.dimension)&&System.nanoTime()-s.openedAt<=300_000_000_000L&&(long)p.getServer().getTickCount()-s.openedTick<=6000&&physical(p,s.pos,s.book);}
    public static void open(ServerPlayer p,BlockPos pos){thread(p);boolean book=p.level().getBlockState(pos).getBlock()instanceof RegisterBookBlock;if(!physical(p,pos,book))return;JusticeService.closeSession(p);CourtService.closeSession(p);WorkService.closeSession(p);runtime(p.getServer()).sessions.put(p.getUUID(),new Session(p,pos,book));send(p);}
    public static void closeSession(ServerPlayer p){thread(p);runtime(p.getServer()).sessions.remove(p.getUUID());}
    private static String authority(ServerPlayer p){try{return CourtService.judicialAuthority(p);}catch(RuntimeException|LinkageError noAuthority){return "";}}
    public static void handle(ServerPlayer p,String operation,String raw){
        thread(p);var r=runtime(p.getServer());Session s=r.sessions.get(p.getUUID());if(operation.equals("finance_close")){r.sessions.remove(p.getUUID());return;}
        if(!valid(p,s)){r.sessions.remove(p.getUUID());closed(p);return;}
        int tick=p.getServer().getTickCount();if(s.requestTick!=tick){s.requestTick=tick;s.requests=0;}if(++s.requests>24)return;
        try{if(raw==null||raw.length()>8192)throw error("request");JsonObject req=JsonParser.parseString(raw).getAsJsonObject();
            if(operation.equals("finance_refresh")){keys(req);send(p);return;}
            if(operation.equals("finance_page")){keys(req,"kind","index");int index=(int)number(req,"index",0,1024);switch(text(req,"kind",16)){case "charges"->s.chargePage=index;case "records"->s.recordPage=index;case "sentences"->s.sentencePage=index;case "audit"->s.auditPage=index;default->throw error("request");}send(p);return;}
            JusticeData data=JusticeData.get(p.getServer());if(!uuid(req,"nonce").equals(s.nonce)||number(req,"version",0,Long.MAX_VALUE)!=data.version())throw error("stale");
            if(s.book)throw error("records_readonly");
            switch(operation){
                case "finance_court"->{keys(req,"nonce","version");CourtService.open(p,s.pos);return;}
                case "finance_pay"->{keys(req,"charge","nonce","version");UUID payment=MoneySettlement.pay(p,uuid(req,"charge"));String failure=MoneySettlement.error(p.getServer(),payment);if(!failure.isEmpty())s.error=failure;else s.message="lawsjustice.message.payment_paid";}
                case "finance_cancel"->{keys(req,"payment","nonce","version");MoneySettlement.cancel(p,uuid(req,"payment"));s.message="lawsjustice.message.payment_cancelled";}
                case "finance_bail"->{
                    keys(req,"sentence","amount","reason","nonce","version");String auth=CourtService.judicialAuthority(p);UUID id=uuid(req,"sentence");var sentence=data.ledger().active().values().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow(()->error("bail_sentence"));
                    String currency=JusticeWallet.currentCurrency();long amount=FinanceAmounts.parse(text(req,"amount",22),currency);String reason=text(req,"reason",300);
                    data.replaceFinance(data.finance().offerBail(UUID.randomUUID(),id,sentence.player(),sentence.playerName(),p.getUUID(),JusticeNames.name(p),auth,reason,currency,amount,now()));
                    ServerPlayer target=p.getServer().getPlayerList().getPlayer(sentence.player());if(target!=null)JusticeNotifications.send(target,"lawsjustice.notification.bail_offered");s.message="lawsjustice.message.bail_offered";
                }
                case "finance_revoke"->{keys(req,"charge","reason","nonce","version");String auth=CourtService.judicialAuthority(p);data.replaceFinance(data.finance().revoke(uuid(req,"charge"),p.getUUID(),JusticeNames.name(p),auth,text(req,"reason",300),now()));s.message="lawsjustice.message.charge_revoked";}
                case "finance_release"->{
                    keys(req,"sentence","reason","nonce","version");CourtService.judicialAuthority(p);UUID id=uuid(req,"sentence");var sentence=data.ledger().active().values().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow(()->error("bail_sentence"));
                    var prison=data.ledger().prisons().get(sentence.prison());JusticeLedger next=data.ledger().judicialRelease(sentence.player(),p.getUUID(),JusticeNames.name(p),text(req,"reason",300),now(),false);
                    data.replaceFinanceAndQueue(next,data.finance(),sentence.player(),new JusticeData.ReleaseTarget(prison.area().dimension(),prison.release()));ServerPlayer target=p.getServer().getPlayerList().getPlayer(sentence.player());if(target!=null){JusticeService.login(target);JusticeNotifications.send(target,"lawsjustice.notification.judicial_release");}s.message="lawsjustice.message.judicial_release";
                }
                default->throw error("request");
            }
            s.nonce=UUID.randomUUID();send(p);
        }catch(RuntimeException rejected){s.error=key(rejected);send(p);}
    }
    public static JsonObject snapshot(ServerPlayer p){
        thread(p);Session s=runtime(p.getServer()).sessions.get(p.getUUID());JsonObject out=new JsonObject();out.addProperty("kind","finance");out.addProperty("protocol",4);out.addProperty("open",valid(p,s));if(!valid(p,s))return out;out.addProperty("session",s.sessionId.toString());
        JusticeData data=JusticeData.get(p.getServer());String auth=authority(p);boolean judge=!auth.isEmpty()&&!s.book;out.addProperty("book",s.book);out.addProperty("judge",judge);out.addProperty("authorityKind",judge?auth:"");out.addProperty("self",p.getUUID().toString());out.addProperty("nonce",s.nonce.toString());out.addProperty("version",data.version());out.addProperty("currency",JusticeWallet.currentCurrency());out.addProperty("treasury",JusticeTreasury.loaded());
        try{out.addProperty("balance",JusticeWallet.balance(p,JusticeWallet.currentCurrency()));out.addProperty("wallet",true);}catch(RuntimeException|LinkageError unavailable){out.addProperty("wallet",false);}
        if(!s.error.isEmpty())out.addProperty("error",s.error);if(!s.message.isEmpty())out.addProperty("message",s.message);JsonObject pages=new JsonObject();
        var charges=data.finance().charges().values().stream().filter(c->judge||c.target().equals(p.getUUID())||c.kind().equals(FinanceLedger.BAIL)&&c.status().equals(FinanceLedger.DUE)).sorted(Comparator.comparing(FinanceLedger.Charge::issuedAt).reversed().thenComparing(FinanceLedger.Charge::id)).toList();
        s.chargePage=page(s.chargePage,charges.size(),6);pages.add("charges",pageInfo(s.chargePage,charges.size(),6));JsonArray chargeRows=new JsonArray();
        if(!s.book)for(var c:slice(charges,s.chargePage,6)){JsonObject row=JSON.toJsonTree(c).getAsJsonObject();var pending=data.finance().pending(c.id());boolean mine=pending!=null&&pending.payer().equals(p.getUUID());row.addProperty("busy",pending!=null);row.addProperty("mine",mine);row.addProperty("payable",c.status().equals(FinanceLedger.DUE)&&(c.kind().equals(FinanceLedger.BAIL)||c.target().equals(p.getUUID()))&&(pending==null||mine));
            if(mine){row.addProperty("payment",pending.id().toString());row.addProperty("paymentError",MoneySettlement.error(p.getServer(),pending.id()));}var paid=data.finance().payments().values().stream().filter(v->v.charge().equals(c.id())&&v.status().equals(FinanceLedger.PAID)).findFirst().orElse(null);if(paid!=null)row.addProperty("destination",paid.destination());chargeRows.add(row);}
        out.add("charges",chargeRows);
        var cases=data.court().cases().values().stream().filter(c->Set.of(CourtLedger.CONVICTED,CourtLedger.FINED,CourtLedger.ACQUITTED).contains(c.status())).sorted(Comparator.comparing(CourtLedger.CaseFile::decidedAt).reversed().thenComparing(CourtLedger.CaseFile::id)).toList();
        s.recordPage=page(s.recordPage,cases.size(),6);pages.add("records",pageInfo(s.recordPage,cases.size(),6));JsonArray records=new JsonArray();for(var c:slice(cases,s.recordPage,6)){JsonObject row=new JsonObject();row.addProperty("id",c.id().toString());row.addProperty("name",c.accusedName());row.addProperty("uuid",c.accused().toString());row.addProperty("judge",c.judgeName());row.addProperty("authority",c.authorityKind());row.addProperty("status",c.status());row.addProperty("reason",c.verdict());row.addProperty("date",c.decidedAt());row.addProperty("sentence",c.sentence());row.addProperty("originalTicks",c.originalTicks());data.finance().charges().values().stream().filter(v->v.caseId().equals(c.id().toString())).findFirst().ifPresent(v->{row.addProperty("amount",v.amount());row.addProperty("currency",v.currency());row.addProperty("paymentStatus",v.status());});records.add(row);}out.add("records",records);
        var audit=data.ledger().history().stream().sorted(Comparator.comparing(JusticeLedger.Decision::at).reversed().thenComparing(JusticeLedger.Decision::id)).toList();s.auditPage=page(s.auditPage,audit.size(),6);pages.add("audit",pageInfo(s.auditPage,audit.size(),6));JsonArray auditRows=new JsonArray();for(var decision:slice(audit,s.auditPage,6))auditRows.add(JSON.toJsonTree(decision));out.add("audit",auditRows);
        JsonArray sentences=new JsonArray();if(judge){var active=data.ledger().active().values().stream().sorted(Comparator.comparing(JusticeLedger.Sentence::playerName).thenComparing(JusticeLedger.Sentence::id)).toList();s.sentencePage=page(s.sentencePage,active.size(),8);pages.add("sentences",pageInfo(s.sentencePage,active.size(),8));for(var sentence:slice(active,s.sentencePage,8))sentences.add(JSON.toJsonTree(sentence));out.addProperty("emeraldEscrow",data.finance().escrow("EMERALD"));out.addProperty("chroniclesEscrow",data.finance().escrow("CHRONICLES"));}out.add("sentences",sentences);out.add("pages",pages);return out;
    }
    private static void send(ServerPlayer p){JusticeNetwork.send(p,snapshot(p));Session s=runtime(p.getServer()).sessions.get(p.getUUID());if(s!=null){s.error="";s.message="";}}
    private static void closed(ServerPlayer p){JsonObject o=new JsonObject();o.addProperty("kind","finance");o.addProperty("open",false);o.addProperty("error","lawsjustice.error.session");JusticeNetwork.send(p,o);}
    public static void logout(ServerPlayer p){closeSession(p);}
    public static void stop(MinecraftServer s){RUNTIMES.remove(s);}
    public static void tick(MinecraftServer server){var r=runtime(server);int tick=server.getTickCount();if(r.lastTick==tick)return;r.lastTick=tick;if(tick%20!=0)return;for(var e:List.copyOf(r.sessions.entrySet())){ServerPlayer p=server.getPlayerList().getPlayer(e.getKey());if(p==null||!valid(p,e.getValue())){r.sessions.remove(e.getKey());if(p!=null)closed(p);}else send(p);}}
    private static int page(int n,int count,int size){return Math.clamp(n,0,Math.max(0,(count-1)/size));}
    private static JsonObject pageInfo(int index,int count,int size){JsonObject o=new JsonObject();o.addProperty("index",index);o.addProperty("total",Math.max(1,(count+size-1)/size));o.addProperty("records",count);return o;}
    private static <T>List<T> slice(List<T> list,int page,int size){int first=Math.min(list.size(),page*size);return list.subList(first,Math.min(list.size(),first+size));}
    private static void keys(JsonObject o,String...allowed){if(!Set.of(allowed).containsAll(o.keySet()))throw error("request");}
    private static String text(JsonObject o,String key,int max){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw error("request");String text=v.getAsString().strip();if(text.isEmpty()||text.length()>max||text.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'))throw error("request");return text;}
    private static UUID uuid(JsonObject o,String key){String text=text(o,key,36);UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw error("request");return id;}
    private static long number(JsonObject o,String key,long min,long max){JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw error("request");long n=v.getAsBigDecimal().longValueExact();if(n<min||n>max)throw error("request");return n;}
    private static String key(RuntimeException e){String key=e.getMessage();return key!=null&&key.startsWith("lawsjustice.error.")?key:"lawsjustice.error.request";}
    private static IllegalStateException error(String key){return new IllegalStateException("lawsjustice.error."+key);}
}
