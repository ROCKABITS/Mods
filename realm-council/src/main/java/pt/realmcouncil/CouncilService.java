package pt.realmcouncil;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Stats;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.domain.CouncilState.*;
import pt.realmcouncil.network.*;
import pt.realmcouncil.compat.NotificationsBridge;
import static pt.realmcouncil.domain.Rules.*;

public final class CouncilService {
    public record Session(String mode,ResourceLocation dimension,BlockPos pos,long openedAt){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Long> REQUESTS=new HashMap<>();
    private static final Map<UUID,Long> REFRESHES=new HashMap<>();
    private record Roster(String query,int page){}
    private static final Map<UUID,Roster> ROSTERS=new HashMap<>();
    public static void clear(){SESSIONS.clear();REQUESTS.clear();REFRESHES.clear();ROSTERS.clear();}
    public static void logout(UUID id){SESSIONS.remove(id);REQUESTS.remove(id);REFRESHES.remove(id);ROSTERS.remove(id);}
    public static void observe(ServerPlayer player){
        var d=CouncilData.get(player.server);d.state.observe(player.getUUID(),player.getGameProfile().getName(),Math.max(0,player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME))),System.currentTimeMillis());d.setDirty();
    }
    public static void open(ServerPlayer player,String mode,BlockPos pos){
        var session=new Session(mode,player.level().dimension().location(),pos==null?null:pos.immutable(),System.currentTimeMillis());
        validateStation(player,session);
        SESSIONS.put(player.getUUID(),session);
        send(player,true,"");
    }
    public static CouncilView snapshot(ServerPlayer player,long now){
        var session=requireStation(player);
        var d=CouncilData.get(player.server);
        String mode=session.mode();
        CouncilAdministration.View admin=null;
        if(player.hasPermissions(2)){
            var r=ROSTERS.getOrDefault(player.getUUID(),new Roster("",0));
            var online=new HashSet<UUID>();player.server.getPlayerList().getPlayers().forEach(p->online.add(p.getUUID()));
            admin=CouncilAdministration.of(d.state,CouncilConfig.settings(),online,r.query(),r.page(),now);
        }else ROSTERS.remove(player.getUUID());
        return CouncilView.of(d.state,player.getUUID(),RpBridge.identity(player),RpBridge.present(),mode,now,CouncilConfig.MIN_PLAY.get(),admin);
    }
    public static void send(ServerPlayer player,boolean open,String message){
        requireStation(player);
        advanceClock(player.server);long now=System.currentTimeMillis();
        var view=snapshot(player,now);
        CouncilNetwork.send(player,new CouncilPackets.State(CouncilState.JSON.toJson(view),open,message));
    }
    /** All automatic transitions share one persistence and announcement path, including menu reads. */
    public static void advanceClock(MinecraftServer server){
        var data=CouncilData.get(server);
        if(data.state.tick(System.currentTimeMillis())){
            data.setDirty();server.overworld().getDataStorage().save();announcePhase(server);
        }
    }
    public static void notify(ServerPlayer player,Component message){NotificationsBridge.notify(player,"election",message);}
    public static void broadcast(MinecraftServer server,Component message){
        for(var player:server.getPlayerList().getPlayers())notify(player,message);
    }
    /** Public phase/result only. Individual ballots and voter choices never enter notifications. */
    public static void announcePhase(MinecraftServer server){
        var election=CouncilData.get(server).state.current;if(election==null)return;
        if(election.phase==Phase.FINISHED&&election.winner!=null){
            var winner=election.candidates.get(election.winner);
            if(winner!=null){broadcast(server,Component.translatable("notification.realmcouncil.winner",winner.name(),election.title));return;}
        }
        broadcast(server,Component.translatable("notification.realmcouncil.phase."+election.phase.name().toLowerCase(java.util.Locale.ROOT),election.title,election.round));
    }
    public static void physical(ServerPlayer player,String expected){
        require(requireStation(player).mode().equals(expected),"station");
    }
    private static Session requireStation(ServerPlayer player){
        Session session=SESSIONS.get(player.getUUID());
        validateStation(player,session);
        return session;
    }
    private static void validateStation(ServerPlayer player,Session session){
        long now=System.currentTimeMillis();
        require(session!=null&&session.pos()!=null&&now>=session.openedAt()&&now-session.openedAt()<600000,"station");
        require(player.level().dimension().location().equals(session.dimension())
                &&player.distanceToSqr(session.pos().getX()+0.5,session.pos().getY()+0.5,session.pos().getZ()+0.5)<=64
                &&player.level().hasChunkAt(session.pos()),"station");
        var block=player.level().getBlockState(session.pos()).getBlock();
        boolean valid=switch(session.mode()){
            case "board"->block==CouncilBlocks.NOTICE_BOARD.get();
            case "urn"->block==CouncilBlocks.BALLOT_BOX.get();
            case "oath"->block==CouncilBlocks.OATH_DESK.get();
            default->false;
        };
        require(valid,"station");
    }
    /** An invalid session receives no public state and the stale client menu closes. */
    public static void reject(ServerPlayer player,String code){
        try{send(player,false,"error."+code);}
        catch(RuleException invalidSession){
            logout(player.getUUID());
            CouncilNetwork.send(player,new CouncilPackets.State("",false,"error."+code));
        }
    }
    public static void action(ServerPlayer player,CouncilPackets.Action p){
        // Old clients and hand-crafted packets cannot reopen a ledger remotely.
        require(p.action()!=6,"station");
        requireStation(player);
        long now=System.currentTimeMillis();long last=REQUESTS.getOrDefault(player.getUUID(),0L);
        if(p.action()==0){require(now-REFRESHES.getOrDefault(player.getUUID(),0L)>=1000,"too_fast");REFRESHES.put(player.getUUID(),now);}
        else{require(now-last>=300,"too_fast");REQUESTS.put(player.getUUID(),now);}
        advanceClock(player.server);var d=CouncilData.get(player.server);var s=d.state;
        if(p.action()>=CouncilAdministration.CREATE){
            require(player.hasPermissions(2),"permission");
            if(p.action()==CouncilAdministration.LIST){
                String query=text(p.text(),0,32,false);
                ROSTERS.put(player.getUUID(),new Roster(query,Math.max(0,p.round())));
                send(player,false,"roster");return;
            }
            administer(player,s,p,now);
            d.setDirty();player.server.overworld().getDataStorage().save();
            if(p.action()==CouncilAdministration.CREATE)broadcast(player.server,Component.translatable("message.realmcouncil.created",s.current.title));
            if(p.action()==CouncilAdministration.ADVANCE||p.action()==CouncilAdministration.CANCEL)announcePhase(player.server);
            if(p.action()==CouncilAdministration.END_TERM)broadcast(player.server,Component.translatable("notification.realmcouncil.term_ended"));
            send(player,false,p.action()==CouncilAdministration.BLOCKS?"blocks_received":"saved");return;
        }
        switch(p.action()){
            case 0->{}
            case 1->{var e=s.requireElection();require(e.id.toString().equals(p.election()),"stale");s.nominate(player.getUUID(),RpBridge.identity(player),p.text(),p.choice(),p.extra(),now);var skin=player.getGameProfile().getProperties().get("textures").stream().findFirst();skin.filter(v->v.value()!=null&&v.value().length()<=4096&&(v.signature()==null||v.signature().length()<=2048)).ifPresent(v->e.candidates.put(player.getUUID(),e.candidates.get(player.getUUID()).withSkin(v.value(),v.signature()==null?"":v.signature())));}
            case 2->{require(s.requireElection().id.toString().equals(p.election()),"stale");s.withdraw(player.getUUID(),now);}
            case 3->{physical(player,"urn");UUID election=parseUuid(p.election());UUID choice=p.choice().isEmpty()?null:parseUuid(p.choice());s.cast(player.getUUID(),election,p.round(),choice,now);}
            case 4->{physical(player,"oath");require(s.mandate!=null&&s.mandate.electionId.toString().equals(p.election()),"stale");s.claim(player.getUUID(),now);}
            case 5->{require(s.mandate!=null&&s.mandate.electionId.toString().equals(p.election()),"stale");s.publish(player.getUUID(),p.extra(),now);}
            default->throw new RuleException("action");
        }
        if(p.action()!=0){d.setDirty();player.server.overworld().getDataStorage().save();}
        if(p.action()==1)notify(player,Component.translatable("notification.realmcouncil.candidacy_saved"));
        if(p.action()==2)notify(player,Component.translatable("notification.realmcouncil.candidacy_withdrawn"));
        if(p.action()==3)notify(player,Component.translatable("notification.realmcouncil.vote_sealed"));
        if(p.action()==4)broadcast(player.server,Component.translatable("message.realmcouncil.sworn",s.mandate.name,s.mandate.title));
        if(p.action()==5)broadcast(player.server,Component.translatable("notification.realmcouncil.decision_published",s.mandate.name));
        send(player,false,p.action()==0?"":"saved");
    }
    private static void administer(ServerPlayer player,CouncilState s,CouncilPackets.Action p,long now){
        switch(p.action()){
            case CouncilAdministration.CREATE->{
                CouncilAdministration.current(s,p.election(),p.round(),p.choice());
                player.server.getPlayerList().getPlayers().forEach(CouncilService::observe);
                CouncilAdministration.Settings.decode(p.extra()).create(s,p.text(),now);
            }
            case CouncilAdministration.ADVANCE,CouncilAdministration.CANCEL,CouncilAdministration.ENROL,
                    CouncilAdministration.REMOVE_CANDIDATE,CouncilAdministration.EXCLUDE->{
                CouncilAdministration.current(s,p.election(),p.round(),p.extra());
                switch(p.action()){
                    case CouncilAdministration.ADVANCE->s.advance(now);
                    case CouncilAdministration.CANCEL->s.cancel(now);
                    case CouncilAdministration.ENROL->{UUID id=parseUuid(p.choice());var target=player.server.getPlayerList().getPlayer(id);if(target!=null)observe(target);s.enrol(id);}
                    case CouncilAdministration.REMOVE_CANDIDATE->s.withdraw(parseUuid(p.choice()),now);
                    case CouncilAdministration.EXCLUDE->s.exclude(parseUuid(p.choice()));
                }
            }
            case CouncilAdministration.END_TERM->{
                require(s.mandate!=null&&s.mandate.electionId.toString().equals(p.election()),"stale");s.endTerm(now);
            }
            case CouncilAdministration.BLOCKS->{
                for(var block:List.of(CouncilBlocks.NOTICE_BOARD.get(),CouncilBlocks.BALLOT_BOX.get(),CouncilBlocks.OATH_DESK.get())){
                    var stack=new ItemStack(block);
                    if(!player.getInventory().add(stack))player.drop(stack,false);
                }
            }
            default->throw new RuleException("action");
        }
    }
    private static UUID parseUuid(String v){try{return UUID.fromString(v);}catch(IllegalArgumentException ex){throw new RuleException("stale");}}
}
