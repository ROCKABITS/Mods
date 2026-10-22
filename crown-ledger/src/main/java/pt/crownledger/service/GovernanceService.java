package pt.crownledger.service;

import com.google.gson.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import pt.crownledger.GovernanceConfig;
import pt.crownledger.compat.ChroniclesBridge;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.Governance;
import pt.crownledger.domain.Treasury;
import pt.crownledger.network.GovernancePackets;
import net.neoforged.neoforge.network.PacketDistributor;

/** The remote endpoint exposes only public voting. Spending authority still requires a live physical chest session. */
public final class GovernanceService {
    private static final Gson JSON=new Gson();
    private record Clock(long millis,long nanos){}
    private static final Map<MinecraftServer,Clock> CLOCKS=new WeakHashMap<>();
    private static final Map<UUID,Long> RATE=new HashMap<>(),QUERIES=new HashMap<>();
    public static long now(MinecraftServer server){
        long nano=System.nanoTime(),wall=Math.max(0,System.currentTimeMillis());var previous=CLOCKS.get(server);
        long value=previous==null?Math.max(wall,TreasuryData.get(server).governance.lastObservedAt()):
                Math.max(wall,previous.millis()+Math.max(0,(nano-previous.nanos())/1_000_000));
        if(previous==null || wall>=value)CLOCKS.put(server,new Clock(value,nano));
        return value;
    }
    public static void request(ServerPlayer player,String operation,String payload){
        if(player==null||player.getServer()==null||!player.getServer().isSameThread())return;
        try {
            if(player.isSpectator()||player instanceof FakePlayer)throw error("ineligible");
            long nano=System.nanoTime();
            if(operation.equals("query")){Long last=QUERIES.put(player.getUUID(),nano);if(last!=null&&nano-last<500_000_000L)return;}
            else {Long last=RATE.put(player.getUUID(),nano);if(last!=null&&nano-last<150_000_000L)throw error("rate");}
            if(payload==null||payload.length()>2048)throw error("invalid");
            JsonObject input=JsonParser.parseString(payload).getAsJsonObject();
            var server=player.getServer();var data=TreasuryData.get(server);long time=now(server);
            var next=data.governance.copy();var treasury=data.treasury.copy();
            next.advance(treasury,time);
            switch(operation){
                case "query" -> { send(player,true,"");return; }
                case "vote" -> next.vote(UUID.fromString(input.get("id").getAsString()),player.getUUID(),strictBoolean(input,"yes"),time);
                case "propose" -> {
                    requireProposer(player);
                    if(!GovernanceConfig.ENABLED.get())throw error("disabled");
                    Governance.Offer offer=null;String question;
                    if(input.has("benefit")){
                        offer=GovernanceConfig.offer(Governance.Benefit.valueOf(input.get("benefit").getAsString()),treasury.policy().currency());
                        if(!input.has("cost")||input.get("cost").getAsLong()!=offer.cost()||input.get("duration").getAsLong()!=offer.durationMillis()||
                                input.get("strength").getAsInt()!=offer.strength()||!input.get("currency").getAsString().equals(offer.currency().name()))throw error("quote");
                        question="realm.treasury.benefit."+offer.benefit().name().toLowerCase(Locale.ROOT);
                    }else question=input.get("question").getAsString();
                    Set<UUID> eligible=new LinkedHashSet<>();
                    for(var citizen:server.getPlayerList().getPlayers())if(!citizen.isSpectator()&&!(citizen instanceof FakePlayer))eligible.add(citizen.getUUID());
                    next.propose(treasury,player.getUUID(),ChroniclesBridge.publicName(player),question,offer,eligible,
                            GovernanceConfig.QUORUM.get(),GovernanceConfig.MAX_ACTIVE.get(),GovernanceConfig.VOTING_SECONDS.get()*1000L,
                            GovernanceConfig.COOLDOWN_SECONDS.get()*1000L,time);
                }
                case "cancel" -> {
                    CrownService.requireSession(player);
                    if(!player.hasPermissions(2))throw error("authority");
                    if(next.current()==null||!next.current().id().toString().equals(input.get("id").getAsString()))throw error("closed");
                    next.cancel(treasury,player.getUUID(),time);
                }
                default -> throw error("invalid");
            }
            data.commitGovernance(server,treasury,next);
            broadcast(server);
        }catch(RuntimeException error){
            String key=error.getMessage();
            if("Insufficient treasury funds".equals(key))key="realm.treasury.error.funds";
            if(key==null||!key.startsWith("realm.treasury.")&&!key.startsWith("gui.kingdomtreasury."))key="realm.treasury.error.invalid";
            send(player,false,key);
        }
    }
    private static boolean strictBoolean(JsonObject input,String key){
        var v=input.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw error("invalid");return v.getAsBoolean();
    }
    public static void requireProposer(ServerPlayer player){
        CrownService.requireSession(player);
        if(!CrownService.mayManage(player)&&!player.hasPermissions(2))throw error("authority");
    }
    public static void tick(MinecraftServer server){
        if(server.getTickCount()%20!=0)return;
        var data=TreasuryData.get(server);long time=now(server);var next=data.governance.copy();var treasury=data.treasury.copy();
        try {
            boolean changed=next.advance(treasury,time);
            if(changed)data.commitGovernance(server,treasury,next);
            if(changed||server.getTickCount()%200==0)broadcast(server);
        }catch(RuntimeException error){com.mojang.logging.LogUtils.getLogger().error("Realm Treasury vote state retained; retrying",error);}
    }
    public static void broadcast(MinecraftServer server){for(var player:server.getPlayerList().getPlayers())send(player,false,"");}
    public static void login(ServerPlayer player){if(player!=null)send(player,false,"");}
    public static void logout(UUID player){RATE.remove(player);QUERIES.remove(player);}
    public static void stop(MinecraftServer server){CLOCKS.remove(server);RATE.clear();QUERIES.clear();}
    public static int strength(ServerPlayer player,Governance.Benefit kind){
        if(player==null||player.getServer()==null||!player.getServer().isSameThread()||player.isCreative()||player.isSpectator()||player instanceof FakePlayer)return 0;
        var server=player.getServer();return TreasuryData.get(server).governance.strength(kind,now(server));
    }
    public static String snapshot(ServerPlayer player,boolean open,String message){
        var data=TreasuryData.get(player.getServer());var g=data.governance;long time=now(player.getServer());
        Map<String,Object> view=new LinkedHashMap<>();view.put("open",open);view.put("message",message);view.put("now",time);
        boolean proposer;try{requireProposer(player);proposer=true;}catch(RuntimeException unavailable){proposer=false;}
        view.put("canPropose",proposer);view.put("admin",player.hasPermissions(2)&&proposer);view.put("nextProposalAt",g.nextProposalAt());
        view.put("refundPending",g.refund()!=null);view.put("maximumActive",GovernanceConfig.MAX_ACTIVE.get());
        view.put("durationSeconds",GovernanceConfig.VOTING_SECONDS.get());view.put("quorumPercent",GovernanceConfig.QUORUM.get());
        view.put("currency",data.treasury.policy().currency().name());view.put("balance",data.treasury.balance(data.treasury.policy().currency()));
        var p=g.current();
        if(p!=null){
            Map<String,Object> poll=new LinkedHashMap<>();poll.put("id",p.id());poll.put("author",p.authorName());poll.put("question",p.question());
            poll.put("offer",p.offer());poll.put("deadline",p.deadline());poll.put("electorate",p.eligible().size());poll.put("minimumVotes",p.minimumVotes());
            poll.put("participation",p.ballots().size());poll.put("ownVote",p.ballots().get(player.getUUID()));
            poll.put("canVote",p.eligible().contains(player.getUUID())&&!player.isSpectator()&&time<p.deadline());view.put("poll",poll);
        }
        view.put("active",g.active().stream().filter(a->a.expiresAt()>time).toList());view.put("history",g.history());
        var offers=new ArrayList<Governance.Offer>();
        for(var kind:Governance.Benefit.values())try{offers.add(GovernanceConfig.offer(kind,data.treasury.policy().currency()));}catch(RuntimeException disabled){}
        view.put("offers",offers);return JSON.toJson(view);
    }
    public static void send(ServerPlayer player,boolean open,String message){
        try{PacketDistributor.sendToPlayer(player,new GovernancePackets.State(snapshot(player,open,message)));}
        catch(RuntimeException|LinkageError disconnected){/* A disconnected viewer cannot roll back a persisted decision. */}
    }
    private static IllegalStateException error(String key){return new IllegalStateException("realm.treasury.error."+key);}
    private GovernanceService(){}
}
