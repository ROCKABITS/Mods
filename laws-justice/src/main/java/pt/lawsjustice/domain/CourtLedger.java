package pt.lawsjustice.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable, server-owned case files, warrant authorizations and temporary arrests. */
public final class CourtLedger {
    public static final int MAX_CASES=256, MAX_EVIDENCE=16, MAX_WARRANTS=1024, MAX_RESTRAINTS=256;
    public static final long MAX_WARRANT_MILLIS=7L*24*60*60*1000, MAX_RESTRAINT_MILLIS=5L*60*1000;
    public static final String OPEN="OPEN", HEARING="HEARING", ACQUITTED="ACQUITTED", CONVICTED="CONVICTED";
    public static final String ACTIVE="ACTIVE", EXECUTED="EXECUTED", REVOKED="REVOKED";
    public static final String PRESIDENT="PRESIDENT", ADMIN_EMERGENCY="ADMIN_EMERGENCY";

    public record Evidence(UUID id,UUID author,String authorName,String text,long at) {
        public Evidence { nonNull(id);nonNull(author);CourtLedger.text(authorName,1,64,"name");CourtLedger.text(text,1,1200,"evidence");require(at>=0,"date"); }
    }

    public record CaseFile(UUID id,UUID complainant,String complainantName,UUID accused,String accusedName,
                           String accusation,List<Evidence> evidence,String status,long openedAt,long heardAt,
                           String judge,String judgeName,String authorityKind,String verdict,long decidedAt,String sentence) {
        public CaseFile {
            nonNull(id);nonNull(complainant);nonNull(accused);text(complainantName,1,64,"name");text(accusedName,1,64,"name");
            text(accusation,1,800,"accusation");nonNull(evidence);require(evidence.size()<=MAX_EVIDENCE,"capacity");
            Set<UUID> ids=new HashSet<>();for(Evidence entry:evidence){nonNull(entry);require(ids.add(entry.id()),"duplicate");require(entry.at()>=openedAt,"date");}
            evidence=List.copyOf(evidence);optionalUuid(judge);optionalUuid(sentence);text(judgeName,0,64,"name");
            text(verdict,0,800,"verdict");require(openedAt>=0&&heardAt>=0&&decidedAt>=0,"date");
            require(Set.of(OPEN,HEARING,ACQUITTED,CONVICTED).contains(nonNull(status)),"case_status");
            if(OPEN.equals(status)){
                require(heardAt==0&&decidedAt==0&&judge.isEmpty()&&judgeName.isEmpty()&&authorityKind!=null&&authorityKind.isEmpty()
                        &&verdict.isEmpty()&&sentence.isEmpty(),"case_state");
            }else{
                require(!judge.isEmpty(),"case_state");text(judgeName,1,64,"name");authority(authorityKind);
                require(heardAt>=openedAt,"date");
                if(HEARING.equals(status))require(decidedAt==0&&verdict.isEmpty()&&sentence.isEmpty(),"case_state");
                else{
                    require(decidedAt>=heardAt,"date");text(verdict,1,800,"verdict");
                    require(CONVICTED.equals(status)?!sentence.isEmpty():sentence.isEmpty(),"case_state");
                    for(Evidence entry:evidence)require(entry.at()<=decidedAt,"date");
                }
            }
        }
    }

    public record Warrant(UUID id,UUID accused,String accusedName,UUID author,String authorName,String authorityKind,
                          String reason,long issuedAt,long expiresAt,String status,String caseId) {
        public Warrant {
            nonNull(id);nonNull(accused);nonNull(author);text(accusedName,1,64,"name");text(authorName,1,64,"name");
            authority(authorityKind);text(reason,1,300,"reason");optionalUuid(caseId);
            require(issuedAt>=0&&expiresAt>issuedAt&&expiresAt-issuedAt<=MAX_WARRANT_MILLIS,"warrant_duration");
            require(Set.of(ACTIVE,EXECUTED,REVOKED).contains(nonNull(status)),"warrant_status");
        }
    }

    public record Restraint(UUID player,UUID escort,UUID warrant,String dimension,JusticeLedger.Point anchor,long appliedAt,long expiresAt) {
        public Restraint {
            nonNull(player);nonNull(escort);nonNull(warrant);require(!player.equals(escort),"self_restraint");
            new JusticeLedger.Area(dimension,anchor,anchor);
            require(appliedAt>=0&&expiresAt>appliedAt&&expiresAt-appliedAt<=MAX_RESTRAINT_MILLIS,"restraint_duration");
        }
    }

    /** Deeply immutable maps of validated immutable records; no state is silently pruned. */
    public record State(Map<UUID,CaseFile> cases,Map<UUID,Warrant> warrants,Map<UUID,Restraint> restraints) {
        public State {
            cases=copy(cases,MAX_CASES);warrants=copy(warrants,MAX_WARRANTS);restraints=copy(restraints,MAX_RESTRAINTS);
            Set<String> sentenceIds=new HashSet<>();
            for(var entry:cases.entrySet()){
                CaseFile row=entry.getValue();require(entry.getKey().equals(row.id()),"reference");
                if(CONVICTED.equals(row.status()))require(sentenceIds.add(row.sentence()),"duplicate");
            }
            for(var entry:warrants.entrySet()){
                Warrant row=entry.getValue();require(entry.getKey().equals(row.id()),"reference");
                if(!row.caseId().isEmpty()){
                    CaseFile related=cases.get(UUID.fromString(row.caseId()));
                    require(related!=null&&related.accused().equals(row.accused()),"case_reference");
                }
            }
            Set<UUID> executed=new HashSet<>();
            for(var entry:restraints.entrySet()){
                Restraint row=entry.getValue();require(entry.getKey().equals(row.player()),"reference");
                Warrant warrant=warrants.get(row.warrant());
                require(warrant!=null&&EXECUTED.equals(warrant.status())&&warrant.accused().equals(row.player()),"warrant_reference");
                require(row.appliedAt()>=warrant.issuedAt()&&row.expiresAt()<=warrant.expiresAt(),"restraint_duration");
                require(executed.add(row.warrant()),"duplicate");
            }
        }
    }

    private final State state;
    private CourtLedger(State state){this.state=nonNull(state);}
    public static CourtLedger empty(){return new CourtLedger(new State(Map.of(),Map.of(),Map.of()));}
    public static CourtLedger restore(State state){return new CourtLedger(nonNull(state));}
    public State state(){return state;}
    public Map<UUID,CaseFile> cases(){return state.cases();}
    public Map<UUID,Warrant> warrants(){return state.warrants();}
    public Map<UUID,Restraint> restraints(){return state.restraints();}

    public CourtLedger file(CaseFile initial){
        nonNull(initial);require(OPEN.equals(initial.status())&&initial.evidence().isEmpty(),"case_state");
        valid(!cases().containsKey(initial.id()),"duplicate");
        Map<UUID,CaseFile> cases=new LinkedHashMap<>(cases());cases.put(initial.id(),initial);return with(cases,warrants(),restraints());
    }

    public CourtLedger evidence(UUID caseId,Evidence entry){
        CaseFile row=unresolved(caseId);nonNull(entry);
        valid(row.evidence().stream().noneMatch(previous->previous.id().equals(entry.id())),"duplicate");
        ArrayList<Evidence> entries=new ArrayList<>(row.evidence());entries.add(entry);
        return put(new CaseFile(row.id(),row.complainant(),row.complainantName(),row.accused(),row.accusedName(),row.accusation(),entries,
                row.status(),row.openedAt(),row.heardAt(),row.judge(),row.judgeName(),row.authorityKind(),row.verdict(),row.decidedAt(),row.sentence()));
    }

    public CourtLedger hear(UUID caseId,UUID judge,String name,String authority,long now){
        CaseFile row=unresolved(caseId);valid(OPEN.equals(row.status()),"case_hearing");
        nonNull(judge);text(name,1,64,"name");authority(authority);require(now>=0,"date");
        return put(new CaseFile(row.id(),row.complainant(),row.complainantName(),row.accused(),row.accusedName(),row.accusation(),row.evidence(),
                HEARING,row.openedAt(),Math.max(now,row.openedAt()),judge.toString(),name,authority,"",0,""));
    }

    public CourtLedger decide(UUID caseId,UUID judge,String name,String authority,String verdict,String sentenceId,boolean guilty,long now){
        CaseFile row=unresolved(caseId);valid(HEARING.equals(row.status()),"hearing_required");
        nonNull(judge);text(name,1,64,"name");authority(authority);text(verdict,1,800,"verdict");optionalUuid(sentenceId);
        require(guilty?!sentenceId.isEmpty():sentenceId.isEmpty(),"case_state");require(now>=0,"date");
        long at=Math.max(now,row.heardAt());for(Evidence entry:row.evidence())at=Math.max(at,entry.at());
        return put(new CaseFile(row.id(),row.complainant(),row.complainantName(),row.accused(),row.accusedName(),row.accusation(),row.evidence(),
                guilty?CONVICTED:ACQUITTED,row.openedAt(),row.heardAt(),judge.toString(),name,authority,verdict,at,sentenceId));
    }

    public CourtLedger issue(Warrant warrant){
        nonNull(warrant);require(ACTIVE.equals(warrant.status()),"warrant_status");valid(!warrants().containsKey(warrant.id()),"duplicate");
        if(!warrant.caseId().isEmpty()){
            CaseFile row=caseFile(UUID.fromString(warrant.caseId()));valid(!ACQUITTED.equals(row.status()),"case_resolved");
        }
        Map<UUID,Warrant> warrants=new LinkedHashMap<>(warrants());warrants.put(warrant.id(),warrant);return with(cases(),warrants,restraints());
    }

    public CourtLedger revoke(UUID id){
        Warrant warrant=warrant(id);valid(ACTIVE.equals(warrant.status()),"warrant_inactive");
        Map<UUID,Warrant> warrants=new LinkedHashMap<>(warrants());warrants.put(id,status(warrant,REVOKED));return with(cases(),warrants,restraints());
    }

    public CourtLedger restrain(Restraint restraint,long now){
        nonNull(restraint);require(now>=0,"date");valid(!restraints().containsKey(restraint.player()),"already_restrained");
        valid(validWarrant(restraint.warrant(),restraint.player(),now),"warrant_invalid");
        require(restraint.appliedAt()<=now&&restraint.expiresAt()>now,"restraint_duration");
        Warrant warrant=warrant(restraint.warrant());
        Map<UUID,Warrant> warrants=new LinkedHashMap<>(warrants());warrants.put(warrant.id(),status(warrant,EXECUTED));
        Map<UUID,Restraint> restraints=new LinkedHashMap<>(restraints());restraints.put(restraint.player(),restraint);return with(cases(),warrants,restraints);
    }

    public CourtLedger releaseRestraint(UUID player){
        nonNull(player);if(!restraints().containsKey(player))return this;
        Map<UUID,Restraint> restraints=new LinkedHashMap<>(restraints());restraints.remove(player);return with(cases(),warrants(),restraints);
    }

    public CourtLedger expire(long now){
        require(now>=0,"date");
        if(restraints().values().stream().noneMatch(row->row.expiresAt()<=now))return this;
        Map<UUID,Restraint> restraints=new LinkedHashMap<>(restraints());restraints.values().removeIf(row->row.expiresAt()<=now);
        return with(cases(),warrants(),restraints);
    }

    public boolean validWarrant(UUID id,UUID target,long now){
        if(id==null||target==null||now<0)return false;Warrant row=warrants().get(id);
        if(row==null||!row.accused().equals(target)||!ACTIVE.equals(row.status())||now<row.issuedAt()||now>=row.expiresAt())return false;
        return row.caseId().isEmpty()||!ACQUITTED.equals(cases().get(UUID.fromString(row.caseId())).status());
    }

    private CourtLedger put(CaseFile row){Map<UUID,CaseFile> cases=new LinkedHashMap<>(cases());cases.put(row.id(),row);return with(cases,warrants(),restraints());}
    private CourtLedger with(Map<UUID,CaseFile> cases,Map<UUID,Warrant> warrants,Map<UUID,Restraint> restraints){return new CourtLedger(new State(cases,warrants,restraints));}
    private CaseFile caseFile(UUID id){nonNull(id);CaseFile row=cases().get(id);valid(row!=null,"case_missing");return row;}
    private CaseFile unresolved(UUID id){CaseFile row=caseFile(id);valid(OPEN.equals(row.status())||HEARING.equals(row.status()),"case_resolved");return row;}
    private Warrant warrant(UUID id){nonNull(id);Warrant row=warrants().get(id);valid(row!=null,"warrant_missing");return row;}
    private static Warrant status(Warrant row,String status){return new Warrant(row.id(),row.accused(),row.accusedName(),row.author(),row.authorName(),row.authorityKind(),row.reason(),row.issuedAt(),row.expiresAt(),status,row.caseId());}
    private static <T> Map<UUID,T> copy(Map<UUID,T> source,int maximum){
        nonNull(source);require(source.size()<=maximum,"capacity");LinkedHashMap<UUID,T> copy=new LinkedHashMap<>();
        for(var entry:source.entrySet())copy.put(nonNull(entry.getKey()),nonNull(entry.getValue()));return Collections.unmodifiableMap(copy);
    }
    private static void authority(String kind){require(PRESIDENT.equals(kind)||ADMIN_EMERGENCY.equals(kind),"court_authority");}
    private static void optionalUuid(String text){
        nonNull(text);if(text.isEmpty())return;
        try{require(UUID.fromString(text).toString().equals(text),"reference");}catch(IllegalArgumentException failure){throw new IllegalArgumentException("lawsjustice.error.reference",failure);}
    }
    private static void text(String text,int minimum,int maximum,String error){
        require(text!=null&&text.length()>=minimum&&text.length()<=maximum&&(minimum==0||!text.isBlank())
                &&text.codePoints().noneMatch(c -> Character.isISOControl(c) && (!(error.equals("evidence") || error.equals("accusation") || error.equals("verdict") || error.equals("reason")) || c != '\r' && c != '\n')),error);
    }
    private static <T>T nonNull(T value){require(value!=null,"invalid");return value;}
    private static void require(boolean accepted,String error){if(!accepted)throw new IllegalArgumentException("lawsjustice.error."+error);}
    private static void valid(boolean accepted,String error){if(!accepted)throw new IllegalStateException("lawsjustice.error."+error);}
}
