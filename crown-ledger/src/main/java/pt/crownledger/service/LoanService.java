package pt.crownledger.service;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import pt.crownledger.LoanConfig;
import pt.crownledger.block.BlockRegistry;
import pt.crownledger.compat.*;
import pt.crownledger.data.*;
import pt.crownledger.domain.*;
import pt.crownledger.domain.Treasury.Currency;
import pt.crownledger.network.LoanPackets;

/** Every write requires a physical desk, fresh session nonce, server-owned money and current authority. */
public final class LoanService {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static final int PAGE_SIZE=24;
    private record Session(BlockPos pos,ResourceKey<Level> dimension,long expires,UUID nonce,String view,int page){}
    private record Clock(long millis,long nanos){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Long> MUTATIONS=new HashMap<>(),QUERIES=new HashMap<>();
    private static final Map<MinecraftServer,Clock> CLOCKS=new WeakHashMap<>();
    public static long now(MinecraftServer server){long wall=Math.max(0,System.currentTimeMillis()),nano=System.nanoTime();var old=CLOCKS.get(server);long n=old==null?Math.max(wall,TreasuryData.get(server).loans.lastObservedAt()):Math.max(wall,old.millis()+Math.max(0,(nano-old.nanos())/1_000_000));if(old==null||wall>=n)CLOCKS.put(server,new Clock(n,nano));return n;}
    public static void open(ServerPlayer player,BlockPos pos){
        try{requirePlayer(player);LoanTransactions.recover(player);TreasuryData.get(player.getServer()).requireBankAvailable();
            SESSIONS.put(player.getUUID(),new Session(pos.immutable(),player.level().dimension(),System.nanoTime()+300_000_000_000L,UUID.randomUUID(),"mine",0));requireSession(player);player.closeContainer();advance(player.getServer());send(player,true,"");
        }catch(RuntimeException error){player.displayClientMessage(net.minecraft.network.chat.Component.translatable(message(error)),false);}
    }
    public static void requireSession(ServerPlayer p){
        requirePlayer(p);var s=SESSIONS.get(p.getUUID());if(s==null||System.nanoTime()-s.expires()>=0||s.dimension()!=p.level().dimension()||!p.level().hasChunkAt(s.pos())||p.distanceToSqr(Vec3.atCenterOf(s.pos()))>64||!p.level().getBlockState(s.pos()).is(BlockRegistry.LOAN_DESK.get()))throw error("session");
    }
    public static boolean manager(ServerPlayer p){return !p.isSpectator()&&(CrownService.mayManage(p)||p.hasPermissions(2));}
    public static void request(ServerPlayer p,String operation,String payload){
        try{
            requireSession(p);if(payload==null||payload.length()>4096)throw error("invalid");var input=JsonParser.parseString(payload).getAsJsonObject();
            var s=SESSIONS.get(p.getUUID());if(!uuid(input,"nonce").equals(s.nonce()))throw error("stale");
            long nano=System.nanoTime();boolean query=operation.equals("refresh");
            var rates=query?QUERIES:MUTATIONS;Long last=rates.put(p.getUUID(),nano);if(last!=null&&nano-last<(query?300_000_000L:150_000_000L)){if(query)return;throw error("rate");}
            if(operation.equals("leave")){fields(input,"nonce");logout(p.getUUID());return;}
            var server=p.getServer();var data=TreasuryData.get(server);data.requireBankAvailable();advance(server);long time=now(server);var bank=data.loans.copy();var treasury=data.treasury.copy();String name=ChroniclesBridge.publicName(p),result="";
            switch(operation){
                case "refresh"->{fields(input,"nonce");send(p,false,"");return;}
                case "view"->{fields(input,"nonce","view","page");String view=text(input,"view",12);if(!Set.of("mine","review","archive").contains(view)||view.equals("review")&&!manager(p))throw error("authority");int page=integer(input,"page");if(page<0||page>Loans.MAX_LOANS/PAGE_SIZE)throw error("invalid");SESSIONS.put(p.getUUID(),new Session(s.pos(),s.dimension(),s.expires(),s.nonce(),view,page));send(p,false,"");return;}
                case "apply"->{fields(input,"nonce","amount","purpose");if(!LoanConfig.ENABLED.get())throw error("disabled");Currency currency=WalletBridge.activeCurrency();if(currency==Currency.CHRONICLES&&!ChroniclesBridge.available(p))throw error("profile");bank.request(p.getUUID(),name,text(input,"purpose",160),currency,number(input,"amount"),LoanConfig.limits(currency),time);result="requested";}
                case "offer"->{fields(input,"nonce","id","interest","installments","days");requireManager(p);if(!LoanConfig.ENABLED.get())throw error("disabled");var id=uuid(input,"id");var loan=bank.get(id);bank.offer(treasury,id,p.getUUID(),name,integer(input,"interest"),integer(input,"installments"),integer(input,"days"),LoanConfig.limits(loan.currency()),p.hasPermissions(2),time);result="offered";}
                case "accept"->{fields(input,"nonce","id");var id=uuid(input,"id");var loan=bank.get(id);bank.accept(id,p.getUUID(),name,time);LoanTransactions.commit(p,id,true,loan.principal(),time,bank,treasury);result="accepted";}
                case "pay","settle"->{fields(input,"nonce","id");var id=uuid(input,"id");long amount=bank.payment(id,p.getUUID(),operation.equals("settle"));bank.repay(treasury,id,p.getUUID(),name,amount,time);LoanTransactions.commit(p,id,false,amount,time,bank,treasury);result=bank.get(id).state()==Loans.State.SETTLED?"settled":"paid";}
                case "cancel","decline"->{fields(input,"nonce","id");boolean decline=operation.equals("decline");if(decline)requireManager(p);bank.close(treasury,uuid(input,"id"),p.getUUID(),name,decline,time);result=decline?"declined":"cancelled";}
                default->throw error("invalid");
            }
            if(!Set.of("accept","pay","settle").contains(operation))data.commitLoans(server,treasury,bank);
            SESSIONS.put(p.getUUID(),new Session(s.pos(),s.dimension(),System.nanoTime()+300_000_000_000L,UUID.randomUUID(),s.view(),s.page()));
            send(p,false,"realm.treasury.loan.message."+result);refreshOthers(server,p.getUUID());
            if(Set.of("accepted","paid","settled").contains(result))NotificationsBridge.notify(p,"treasury",net.minecraft.network.chat.Component.translatable("realm.treasury.loan.message."+result));
        }catch(RuntimeException|LinkageError error){send(p,false,error instanceof RuntimeException r?message(r):"realm.treasury.loan.error.profile");}
    }
    private static void requireManager(ServerPlayer p){if(!manager(p))throw error("authority");}
    private static void advance(MinecraftServer server){var data=TreasuryData.get(server);data.requireBankAvailable();long time=now(server);if(!data.loans.hasDue(time))return;var bank=data.loans.copy();var treasury=data.treasury.copy();bank.expire(treasury,time);data.commitLoans(server,treasury,bank);}
    public static void tick(MinecraftServer server){
        if(server.getTickCount()%20!=0||TreasuryData.get(server).loanJournal()!=null)return;
        try{advance(server);var data=TreasuryData.get(server);long time=now(server);if(server.getTickCount()%600==0&&data.loans.all().stream().anyMatch(Loans.Loan::open)){var bank=data.loans.copy();bank.observe(time);data.commitLoans(server,data.treasury.copy(),bank);}
            if(server.getTickCount()%100==0)for(var p:server.getPlayerList().getPlayers())if(SESSIONS.containsKey(p.getUUID()))try{requireSession(p);send(p,false,"");}catch(RuntimeException expired){send(p,false,"realm.treasury.loan.error.session");logout(p.getUUID());}
        }catch(RuntimeException error){com.mojang.logging.LogUtils.getLogger().warn("Public loan state retained for retry",error);}
    }
    private static void refreshOthers(MinecraftServer server,UUID exclude){for(var p:server.getPlayerList().getPlayers())if(!p.getUUID().equals(exclude)&&SESSIONS.containsKey(p.getUUID()))send(p,false,"");}
    public static String snapshot(ServerPlayer p,boolean open,String message){
        requireSession(p);var s=SESSIONS.get(p.getUUID());var data=TreasuryData.get(p.getServer());long time=now(p.getServer());boolean manager=manager(p);Currency currency=WalletBridge.activeCurrency();
        var records=data.loans.all().stream().filter(l->s.view().equals("review")?manager&&l.open():s.view().equals("archive")?!l.open()&&(manager||l.borrower().equals(p.getUUID())):l.borrower().equals(p.getUUID())&&l.open()).sorted(Comparator.comparingLong(Loans.Loan::requestedAt).reversed().thenComparing(l->l.id().toString())).toList();
        int pages=Math.max(1,(records.size()+PAGE_SIZE-1)/PAGE_SIZE),page=Math.min(s.page(),pages-1);var rows=new ArrayList<JsonObject>();
        for(var loan:records.subList(page*PAGE_SIZE,Math.min(records.size(),(page+1)*PAGE_SIZE))){var row=JSON.toJsonTree(loan).getAsJsonObject();row.addProperty("total",loan.total());row.addProperty("remaining",loan.remaining());row.addProperty("nextPayment",loan.nextPayment());row.addProperty("nextDueAt",loan.nextDueAt());row.addProperty("completed",loan.completed());row.addProperty("overdue",loan.overdue(time));row.addProperty("own",loan.borrower().equals(p.getUUID()));rows.add(row);}
        Map<String,Object> out=new LinkedHashMap<>();out.put("open",open);out.put("nonce",s.nonce());out.put("manager",manager);out.put("admin",p.hasPermissions(2));out.put("viewer",p.getUUID());out.put("name",ChroniclesBridge.publicName(p));out.put("view",s.view());out.put("page",page);out.put("pages",pages);out.put("count",records.size());out.put("rows",rows);out.put("now",time);out.put("message",message);out.put("currency",currency);out.put("limits",LoanConfig.limits(currency));out.put("enabled",LoanConfig.ENABLED.get());out.put("canApply",data.loanJournal()==null&&data.loans.open(p.getUUID())==null&&LoanConfig.ENABLED.get());out.put("recoveryPending",data.loanJournal()!=null);out.put("balances",data.treasury.balances());
        Map<String,Long> wallets=new LinkedHashMap<>();for(var c:Currency.values())try{wallets.put(c.name(),WalletBridge.balance(p,c));}catch(RuntimeException missing){wallets.put(c.name(),-1L);}out.put("wallets",wallets);
        out.put("events",data.loans.events().stream().filter(e->manager||data.loans.get(e.loan()).borrower().equals(p.getUUID())).limit(64).toList());return JSON.toJson(out);
    }
    public static void send(ServerPlayer p,boolean open,String message){try{PacketDistributor.sendToPlayer(p,new LoanPackets.State(snapshot(p,open,message)));}catch(RuntimeException|LinkageError invalid){if(p!=null){try{PacketDistributor.sendToPlayer(p,new LoanPackets.State(JSON.toJson(Map.of("close",true,"message",message))));}catch(RuntimeException ignored){}if(!message.isEmpty())p.displayClientMessage(net.minecraft.network.chat.Component.translatable(message),false);}}}
    public static void login(ServerPlayer p){try{LoanTransactions.recover(p);}catch(RuntimeException error){com.mojang.logging.LogUtils.getLogger().warn("Loan owner must recover the retained receipt before using public funds",error);}}
    public static void logout(UUID player){SESSIONS.remove(player);MUTATIONS.remove(player);QUERIES.remove(player);}
    public static void stop(MinecraftServer server){SESSIONS.clear();MUTATIONS.clear();QUERIES.clear();CLOCKS.remove(server);}
    private static void fields(JsonObject input,String... keys){if(!input.keySet().equals(Set.of(keys)))throw error("invalid");}
    private static String text(JsonObject j,String k,int max){var v=j.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw error("invalid");String s=v.getAsString();if(s.length()>max||s.chars().anyMatch(c->Character.isISOControl(c)||c=='§'))throw error("invalid");return s.trim();}
    private static UUID uuid(JsonObject j,String k){return UUID.fromString(text(j,k,36));}
    private static long number(JsonObject j,String k){var v=j.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw error("invalid");return v.getAsBigDecimal().longValueExact();}
    private static int integer(JsonObject j,String k){return Math.toIntExact(number(j,k));}
    private static String message(RuntimeException e){String k=e.getMessage();if("Insufficient treasury funds".equals(k))return "realm.treasury.loan.error.funds";return k!=null&&(k.startsWith("realm.treasury.loan.")||k.startsWith("gui.kingdomtreasury."))?k:"realm.treasury.loan.error.invalid";}
    private static void requirePlayer(ServerPlayer p){if(p==null||p.getServer()==null||!p.getServer().isSameThread()||p.isSpectator()||p instanceof FakePlayer)throw error("invalid");}
    private static IllegalStateException error(String key){return new IllegalStateException("realm.treasury.loan.error."+key);}
    private LoanService(){}
}
