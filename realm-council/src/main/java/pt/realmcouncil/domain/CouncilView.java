package pt.realmcouncil.domain;

import java.util.*;
import pt.realmcouncil.domain.CouncilState.*;

/** Public projection with an optional operator-only roll. Individual choices stay on the server. */
public record CouncilView(String mode,UUID viewer,Identity identity,boolean bridge,boolean eligible,boolean voted,
                          int voters,int turnout,long serverTime,long minimumPlayMinutes,ElectionView election,Mandate mandate,List<HistoryView> history,CouncilAdministration.View administration) {
    public record ElectionView(UUID id,String title,String phase,int round,long deadline,int minVotes,String outcome,UUID winner,List<Candidate> candidates,List<UUID> contenders,List<RoundResult> results){}
    public record HistoryView(String title,String outcome,String winner,long time){}
    public static CouncilView of(CouncilState s,UUID viewer,Identity identity,boolean bridge,String mode,long now,long minPlayMinutes){
        return of(s,viewer,identity,bridge,mode,now,minPlayMinutes,null);
    }
    public static CouncilView of(CouncilState s,UUID viewer,Identity identity,boolean bridge,String mode,long now,long minPlayMinutes,CouncilAdministration.View administration){
        var e=s.current;
        var ev=e==null?null:new ElectionView(e.id,e.title,e.phase.name(),e.round,e.deadline(),e.minVotes,e.outcome,e.winner,List.copyOf(e.candidates.values()),List.copyOf(e.contenders),List.copyOf(e.results));
        var history=s.history.reversed().stream().limit(10).map(h->new HistoryView(h.title,h.outcome,h.winner==null?"":h.candidates.get(h.winner).name(),h.closedAt)).toList();
        long minimum=e!=null&&e.minimumPlayMinutes!=null?e.minimumPlayMinutes:minPlayMinutes;
        return new CouncilView(mode,viewer,identity,bridge,e!=null&&e.eligible.contains(viewer),e!=null&&e.ballots.containsKey(viewer),e==null?0:e.eligible.size(),e==null?0:e.ballots.size(),now,minimum,ev,s.mandate,history,administration);
    }
}
