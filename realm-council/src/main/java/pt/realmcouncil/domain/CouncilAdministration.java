package pt.realmcouncil.domain;

import com.google.gson.JsonParseException;
import java.util.*;
import static pt.realmcouncil.domain.Rules.*;

/** An operator-only projection. Ballots and individual choices are never included. */
public final class CouncilAdministration {
    public static final int CREATE=10, ADVANCE=11, CANCEL=12, LIST=13, ENROL=14,
            REMOVE_CANDIDATE=15, END_TERM=16, EXCLUDE=17, BLOCKS=18;
    public static final int PAGE_SIZE=4;

    public record Settings(int minimumPlayMinutes, int nominationMinutes, int campaignMinutes,
                           int votingMinutes, int termMinutes, int runoffMinutes,
                           int minimumVotes, int maximumCandidates, int maximumRounds) {
        public Settings validated() {
            require(minimumPlayMinutes>=0 && minimumPlayMinutes<=525600,"options");
            duration(nominationMinutes); duration(campaignMinutes); duration(votingMinutes);
            duration(termMinutes); duration(runoffMinutes);
            require(minimumVotes>=1 && minimumVotes<=100000 && maximumCandidates>=1 &&
                    maximumCandidates<=16 && maximumRounds>=1 && maximumRounds<=10,"options");
            return this;
        }
        public void create(CouncilState state,String title,long now) {
            validated();
            state.create(title,now,duration(nominationMinutes),duration(campaignMinutes),
                    duration(votingMinutes),duration(termMinutes),duration(runoffMinutes),
                    minimumPlayMinutes*1200L,minimumVotes,maximumCandidates,maximumRounds);
        }
        public static Settings decode(String json) {
            try {
                Settings s=CouncilState.JSON.fromJson(json,Settings.class);
                require(s!=null,"options"); return s.validated();
            } catch (JsonParseException ex) { throw new RuleException("options"); }
        }
    }
    public record PlayerEntry(UUID id,String name,long playMinutes,boolean online,
                              boolean eligible,boolean candidate) {}
    public record View(Settings defaults,String query,int page,int pages,int totalPlayers,
                       List<PlayerEntry> players,boolean canCreate) {}

    public static View of(CouncilState state,Settings defaults,Set<UUID> online,
                          String query,int requestedPage,long now) {
        String filter=text(query,0,32,false).toLowerCase(Locale.ROOT);
        var citizens=state.citizens.values().stream()
                .filter(c->c.name().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing((CouncilState.Citizen c)->c.name().toLowerCase(Locale.ROOT))
                        .thenComparing(CouncilState.Citizen::id)).toList();
        int pages=Math.max(1,(citizens.size()+PAGE_SIZE-1)/PAGE_SIZE);
        int page=Math.max(0,Math.min(requestedPage,pages-1));
        var e=state.current;
        var entries=citizens.stream().skip((long)page*PAGE_SIZE).limit(PAGE_SIZE)
                .map(c->new PlayerEntry(c.id(),c.name(),c.playTicks()/1200,online.contains(c.id()),
                        e!=null&&e.eligible.contains(c.id()),e!=null&&e.candidates.containsKey(c.id())))
                .toList();
        boolean canCreate=(e==null||e.terminal())&&(state.mandate==null||!state.mandate.active(now));
        return new View(defaults,filter,page,pages,citizens.size(),entries,canCreate);
    }

    /** Compare the entire phase/round snapshot before a potentially stale operator action. */
    public static void current(CouncilState state,String election,int round,String phase) {
        var e=state.current;
        require(e==null ? election.isEmpty()&&round==0&&phase.isEmpty()
                : e.id.toString().equals(election)&&e.round==round&&e.phase.name().equals(phase),"stale");
    }
    private CouncilAdministration() {}
}
