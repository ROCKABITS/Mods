package pt.realmcouncil.domain;

import com.google.gson.Gson;
import java.util.*;
import static pt.realmcouncil.domain.Rules.*;

/** Server-owned state. Private ballots are never used as network data. */
public final class CouncilState {
    public static final Gson JSON=new Gson();
    public int schema=1;
    public LinkedHashMap<UUID,Citizen> citizens=new LinkedHashMap<>();
    public Election current;
    public ArrayList<Election> history=new ArrayList<>();
    public Mandate mandate;
    public record Citizen(UUID id,String name,long firstSeen,long playTicks) {}
    public record Identity(String name,String race,int age,boolean roleplay) {}
    public record Candidate(UUID id,String minecraftName,String name,String race,int age,boolean roleplay,String slogan,String symbol,String proposals,String skinValue,String skinSignature) {
        public Candidate withSkin(String value,String signature){return new Candidate(id,minecraftName,name,race,age,roleplay,slogan,symbol,proposals,value,signature);}
    }
    public record RoundResult(int round,LinkedHashMap<UUID,Integer> counts,int blank,int turnout) {}
    public record Decision(String author,String text,long time) {}
    public static final Set<String> SYMBOLS=Set.of("sun","moon","tree","anvil","dragon");
    public enum Phase { NOMINATIONS,CAMPAIGN,VOTING,RUNOFF,FINISHED,CANCELLED }
    public static final class Election {
        public UUID id;
        public String title;
        public Phase phase=Phase.NOMINATIONS;
        public long startedAt,nominationEnd,campaignEnd,votingEnd,termMillis,runoffMillis;
        public Long minimumPlayMinutes;
        public int minVotes,maxCandidates,maxRounds,round=1;
        public LinkedHashSet<UUID> eligible=new LinkedHashSet<>();
        public LinkedHashMap<UUID,Candidate> candidates=new LinkedHashMap<>();
        public LinkedHashSet<UUID> contenders=new LinkedHashSet<>();
        public LinkedHashMap<UUID,String> ballots=new LinkedHashMap<>();
        public ArrayList<RoundResult> results=new ArrayList<>();
        public UUID winner;
        public String outcome="";
        public long closedAt;
        public boolean terminal(){return phase==Phase.FINISHED||phase==Phase.CANCELLED;}
        public boolean voting(){return phase==Phase.VOTING||phase==Phase.RUNOFF;}
        public long deadline(){return switch(phase){case NOMINATIONS->nominationEnd;case CAMPAIGN->campaignEnd;case VOTING,RUNOFF->votingEnd;default->closedAt;};}
    }
    public static final class Mandate {
        public UUID electionId,playerId;
        public String title,name;
        public long beginsAt,endsAt,claimedAt;
        public ArrayList<Decision> decisions=new ArrayList<>();
        public boolean active(long now){return now<endsAt;}
    }
    public void observe(UUID id,String name,long playTicks,long now){
        var old=citizens.get(id);
        citizens.put(id,new Citizen(id,name,old==null?now:old.firstSeen(),Math.max(playTicks,old==null?0:old.playTicks())));
    }
    public Election create(String title,long now,long nominations,long campaign,long voting,long term,long runoff,long minPlayTicks,int minVotes,int maxCandidates,int maxRounds){
        tick(now);
        require(current==null||current.terminal(),"election_active");
        require(mandate==null||!mandate.active(now),"term_active");
        title=text(title,3,64,false);
        require(nominations>0&&campaign>0&&voting>0&&term>0&&runoff>0&&minPlayTicks>=0,"duration");
        require(minVotes>=1&&maxCandidates>=1&&maxCandidates<=16&&maxRounds>=1&&maxRounds<=10,"options");
        if(current!=null){history.add(current);while(history.size()>20)history.removeFirst();}
        var e=new Election();e.id=UUID.randomUUID();e.title=title;e.startedAt=now;
        e.nominationEnd=Math.addExact(now,nominations);e.campaignEnd=Math.addExact(e.nominationEnd,campaign);e.votingEnd=Math.addExact(e.campaignEnd,voting);
        e.termMillis=term;e.runoffMillis=runoff;e.minVotes=minVotes;e.maxCandidates=maxCandidates;e.maxRounds=maxRounds;
        e.minimumPlayMinutes=minPlayTicks/1200;
        citizens.values().stream().filter(c->c.firstSeen()<=now&&c.playTicks()>=minPlayTicks).forEach(c->e.eligible.add(c.id()));
        current=e;return e;
    }
    public void enrol(UUID id){var e=requireElection();require(e.phase==Phase.NOMINATIONS,"roll_locked");require(citizens.containsKey(id),"unknown_player");e.eligible.add(id);}
    public void exclude(UUID id){var e=requireElection();require(e.phase==Phase.NOMINATIONS,"roll_locked");require(e.eligible.remove(id),"not_eligible");e.candidates.remove(id);}
    public void nominate(UUID id,Identity identity,String slogan,String symbol,String proposals,long now){
        tick(now);var e=requireElection();require(e.phase==Phase.NOMINATIONS||e.phase==Phase.CAMPAIGN&&e.candidates.containsKey(id),"nominations_closed");require(e.eligible.contains(id),"not_eligible");
        require(e.candidates.containsKey(id)||e.candidates.size()<e.maxCandidates,"candidate_limit");
        require(SYMBOLS.contains(symbol),"symbol");
        var citizen=citizens.get(id);require(citizen!=null,"unknown_player");
        var c=new Candidate(id,citizen.name(),text(identity.name(),1,64,false),identity.race(),identity.age(),identity.roleplay(),text(slogan,3,80,false),symbol,text(proposals,10,1000,true),"","");
        e.candidates.put(id,c);
    }
    public void withdraw(UUID id,long now){tick(now);var e=requireElection();require(e.phase==Phase.NOMINATIONS||e.phase==Phase.CAMPAIGN,"nominations_closed");require(e.candidates.remove(id)!=null,"not_candidate");}
    public void cast(UUID actor,UUID electionId,int round,UUID choice,long now){
        tick(now);var e=requireElection();require(e.id.equals(electionId)&&e.round==round,"stale");require(e.voting(),"voting_closed");
        require(e.eligible.contains(actor),"not_eligible");require(!e.ballots.containsKey(actor),"already_voted");
        if(choice!=null)require(e.contenders.contains(choice),"candidate");
        e.ballots.put(actor,choice==null?"":choice.toString());
    }
    public boolean tick(long now){
        var e=current;if(e==null||e.terminal())return false;
        boolean changed=false;
        for(int i=0;i<4&&!e.terminal()&&now>=e.deadline();i++){
            changed=true;
            if(e.phase==Phase.NOMINATIONS){e.phase=Phase.CAMPAIGN;}
            else if(e.phase==Phase.CAMPAIGN){e.phase=Phase.VOTING;e.contenders.addAll(e.candidates.keySet());}
            else {finishRound(e,now);}
        }
        return changed;
    }
    public void advance(long now){var e=requireElection();require(!e.terminal(),"no_election");
        if(e.phase==Phase.NOMINATIONS){long campaign=e.campaignEnd-e.nominationEnd,voting=e.votingEnd-e.campaignEnd;e.nominationEnd=now;e.campaignEnd=Math.addExact(now,campaign);e.votingEnd=Math.addExact(e.campaignEnd,voting);e.phase=Phase.CAMPAIGN;}
        else if(e.phase==Phase.CAMPAIGN){long voting=e.votingEnd-e.campaignEnd;e.campaignEnd=now;e.votingEnd=Math.addExact(now,voting);e.phase=Phase.VOTING;e.contenders.addAll(e.candidates.keySet());}
        else finishRound(e,now);
    }
    private void finishRound(Election e,long now){
        LinkedHashMap<UUID,Integer> counts=new LinkedHashMap<>();e.contenders.forEach(id->counts.put(id,0));
        int blank=0;for(String vote:e.ballots.values()){if(vote.isEmpty())blank++;else counts.computeIfPresent(UUID.fromString(vote),(id,n)->n+1);}
        e.results.add(new RoundResult(e.round,counts,blank,e.ballots.size()));
        int best=counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<UUID> tied=counts.keySet().stream().filter(id->counts.get(id)==best).toList();
        if(e.candidates.isEmpty())e.outcome="no_candidates";
        else if(e.ballots.size()<e.minVotes)e.outcome="turnout";
        else if(best==0)e.outcome="no_support";
        else if(tied.size()>1){
            if(e.round>=e.maxRounds)e.outcome="tie_limit";
            else{e.round++;e.phase=Phase.RUNOFF;e.contenders=new LinkedHashSet<>(tied);e.ballots.clear();e.votingEnd=Math.addExact(now,e.runoffMillis);return;}
        }else{e.winner=tied.getFirst();e.outcome="elected";}
        e.phase=Phase.FINISHED;e.closedAt=now;
        if(e.winner!=null){var c=e.candidates.get(e.winner);var m=new Mandate();m.electionId=e.id;m.playerId=c.id();m.name=c.name();m.title=e.title;m.beginsAt=now;m.endsAt=Math.addExact(now,e.termMillis);mandate=m;}
    }
    public void cancel(long now){var e=requireElection();require(!e.terminal(),"no_election");e.phase=Phase.CANCELLED;e.closedAt=now;e.outcome="cancelled";}
    public void claim(UUID actor,long now){require(mandate!=null&&mandate.active(now),"no_term");require(mandate.playerId.equals(actor),"not_winner");require(mandate.claimedAt==0,"already_sworn");mandate.claimedAt=Math.max(1,now);}
    public void publish(UUID actor,String message,long now){require(mandate!=null&&mandate.active(now),"no_term");require(mandate.playerId.equals(actor)&&mandate.claimedAt>0,"not_governor");
        require(mandate.decisions.size()<20,"decision_limit");mandate.decisions.add(new Decision(mandate.name,text(message,10,500,true),now));}
    public void endTerm(long now){require(mandate!=null&&mandate.active(now),"no_term");mandate.endsAt=now;}
    public Election requireElection(){require(current!=null,"no_election");return current;}
    public String toJson(){return JSON.toJson(this);}
    public static CouncilState fromJson(String json){
        var s=JSON.fromJson(json,CouncilState.class);require(s!=null&&s.schema==1&&s.citizens!=null&&s.history!=null,"storage");
        if(s.current!=null)validateElection(s.current);for(var e:s.history)validateElection(e);
        if(s.mandate!=null)require(s.mandate.playerId!=null&&s.mandate.decisions!=null,"storage");return s;
    }
    private static void validateElection(Election e){
        require(e.id!=null&&e.phase!=null&&e.eligible!=null&&e.candidates!=null&&e.contenders!=null&&e.ballots!=null&&e.results!=null,"storage");
        require(e.candidates.size()<=16&&e.round>=1&&e.maxRounds>=e.round,"storage");
        for(var b:e.ballots.entrySet())require(e.eligible.contains(b.getKey())&&(b.getValue().isEmpty()||e.contenders.contains(UUID.fromString(b.getValue()))),"storage");
    }
}
