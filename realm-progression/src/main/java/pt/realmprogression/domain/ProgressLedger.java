package pt.realmprogression.domain;

import java.util.*;

/** Immutable world-wide goals. Money is observed, never transferred or created. */
public final class ProgressLedger {
    public static final long MAX_GOAL=900_000_000_000_000L;
    public static final Set<String> PROTECTED=Set.of("minecraft","neoforge","forge","realmprogression","kingdomtreasury","cronicasrp","realmadmin");
    public record Rule(String mod,Set<String> namespaces,long goal,boolean enabled,long unlockedAt,long witnessed) {
        public Rule {
            id(mod);namespaces=Set.copyOf(Objects.requireNonNull(namespaces));
            if(PROTECTED.contains(mod)||namespaces.isEmpty()||namespaces.size()>8||goal<1||goal>MAX_GOAL||unlockedAt< -1||witnessed<0||unlockedAt<0&&witnessed!=0||unlockedAt>=0&&witnessed<1)throw new IllegalArgumentException("Invalid progression rule");
            for(String ns:namespaces){id(ns);if(PROTECTED.contains(ns))throw new IllegalArgumentException("Essential namespace");}
        }
        public boolean unlocked(){return unlockedAt>=0;}
        public boolean locks(){return enabled&&!unlocked();}
    }
    public record Entry(UUID id,UUID actor,String name,String action,String mod,String before,String after,long at) {
        public Entry {
            Objects.requireNonNull(id);Objects.requireNonNull(actor);ProgressLedger.id(mod);
            if(name==null||name.length()>64||!Set.of("CONFIGURE","UNLOCK").contains(action)||before==null||before.length()>512||after==null||after.length()>512||at<0)throw new IllegalArgumentException("Invalid progression history");
        }
    }
    public record State(int version,long revision,Map<String,Rule> rules,List<Entry> history) {
        public State {
            if(version!=1||revision<0)throw new IllegalArgumentException("Unsupported progression data");
            rules=Map.copyOf(Objects.requireNonNull(rules));history=List.copyOf(Objects.requireNonNull(history));
            if(rules.size()>256||history.size()>256)throw new IllegalArgumentException("Progression capacity exceeded");
            rules.forEach((id,r)->{if(!id.equals(r.mod()))throw new IllegalArgumentException("Rule identity mismatch");});
            Set<String> assigned=new HashSet<>();for(var r:rules.values())if(r.enabled())for(String ns:r.namespaces())if(!assigned.add(ns))throw new IllegalArgumentException("Namespace already managed");
            if(history.stream().map(Entry::id).distinct().count()!=history.size())throw new IllegalArgumentException("Repeated audit entry");
        }
        public static State fresh(){return new State(1,0,Map.of("create",new Rule("create",Set.of("create"),800_000,true,-1,0)),List.of());}
        public boolean blocked(String namespace){return rules.values().stream().anyMatch(r->r.locks()&&r.namespaces().contains(namespace));}
        public Set<String> locked(){var set=new TreeSet<String>();for(var r:rules.values())if(r.locks())set.addAll(r.namespaces());return Set.copyOf(set);}
    }
    public static State configure(State s,String mod,Set<String> namespaces,long goal,boolean enabled,UUID actor,String name,long now){
        Rule old=s.rules().get(mod);
        // Permanent approval is bound to its original output scope. Disable and create a different target to widen it.
        if(old!=null&&old.unlocked()&&!old.namespaces().equals(namespaces))throw new IllegalArgumentException("Unlocked output scope is permanent");
        Rule next=new Rule(mod,namespaces,goal,enabled,old==null?-1:old.unlockedAt(),old==null?0:old.witnessed());
        if(next.equals(old))return s;
        var rules=new TreeMap<>(s.rules());rules.put(mod,next);
        return append(s,rules,new Entry(UUID.randomUUID(),actor,name,"CONFIGURE",mod,old==null?"":describe(old),describe(next),now));
    }
    public static State observe(State s,long balance,Set<String> installed,long now){
        if(balance<0||now<0)throw new IllegalArgumentException("Invalid treasury observation");
        State next=s;
        for(Rule r:new TreeMap<>(s.rules()).values())if(r.locks()&&installed.contains(r.mod())&&balance>=r.goal()){
            var rules=new TreeMap<>(next.rules());rules.put(r.mod(),new Rule(r.mod(),r.namespaces(),r.goal(),r.enabled(),now,balance));
            next=append(next,rules,new Entry(UUID.randomUUID(),new UUID(0,0),"Realm Treasury","UNLOCK",r.mod(),Long.toString(r.goal()),Long.toString(balance),now));
        }
        return next;
    }
    private static State append(State s,Map<String,Rule> rules,Entry entry){
        var history=new ArrayList<Entry>();history.add(entry);history.addAll(s.history());if(history.size()>256)history.subList(256,history.size()).clear();
        return new State(1,Math.incrementExact(s.revision()),rules,history);
    }
    private static String describe(Rule r){return r.enabled()+";"+r.goal()+";"+String.join(",",new TreeSet<>(r.namespaces()));}
    public static void id(String id){if(id==null||!id.matches("[a-z0-9_.-]{1,64}"))throw new IllegalArgumentException("Invalid mod/namespace");}
    private ProgressLedger(){}
}
