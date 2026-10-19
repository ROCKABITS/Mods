package pt.lawsjustice.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import pt.lawsjustice.domain.JusticeLedger.Area;
import pt.lawsjustice.domain.JusticeLedger.Point;

/** Sealed work provenance. Inventory display items never create work credit. */
public final class WorkLedger {
    public static final int MAX_ZONES=256, MAX_NODES=8192, MAX_JOBS=4096;
    public static final Set<String> KINDS=Set.of("MINING","FARMING","CARPENTRY","CLEANING","SMITHING","TRANSPORT");
    public static final Set<String> TYPES=Set.of("MINE","FARM","WORKSHOP","WAREHOUSE","YARD");
    public static final String ACTIVE="ACTIVE", DONE="DONE", CANCELLED="CANCELLED";
    public record Rule(String kind,int quantity,long reductionTicks) {
        public Rule {require(KINDS.contains(kind),"work_kind");require(quantity>=1&&quantity<=4096,"work_quantity");require(reductionTicks>=1&&reductionTicks<=JusticeLedger.MAX_TICKS,"work_reward");}
    }
    public record Rules(int maximumPercent,List<Rule> tasks) {
        public Rules {require(maximumPercent>=0&&maximumPercent<=100,"work_cap");require(tasks!=null&&tasks.size()==6,"work_rules");Set<String> kinds=new HashSet<>();for(Rule r:tasks)require(r!=null&&kinds.add(r.kind()),"work_rules");tasks=List.copyOf(tasks);}
        public Rule rule(String kind){return tasks.stream().filter(r->r.kind().equals(kind)).findFirst().orElseThrow(()->error("work_kind"));}
        public static Rules defaults(){return new Rules(50,List.of(new Rule("MINING",64,3600),new Rule("FARMING",32,4800),new Rule("CARPENTRY",16,2400),new Rule("CLEANING",10,6000),new Rule("SMITHING",8,6000),new Rule("TRANSPORT",16,3600)));}
    }
    public record Zone(UUID id,UUID prison,String name,String type,Area area,long revision) {
        public Zone {nn(id);nn(prison);text(name,48);require(TYPES.contains(type),"work_zone_type");nn(area);require(revision>=0,"revision");}
    }
    public record Node(UUID id,UUID zone,Point position,String resource,String block,String state) {
        public Node {nn(id);nn(zone);nn(position);require(Set.of("STONE","WHEAT","LOG","IRON","COAL","CLEAN").contains(resource),"work_resource");require(block!=null&&block.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"),"work_resource");require(Set.of("READY","SPENT","REFILLABLE").contains(state),"work_resource");}
    }
    public record Job(UUID id,UUID player,UUID sentence,UUID prison,UUID zone,String kind,int quantity,long reductionTicks,
                      int raw,int fuel,int output,String status,List<Point> route,long acceptedAt,long lastActionAt,long finishedAt,long awardedTicks) {
        public Job {
            nn(id);nn(player);nn(sentence);nn(prison);nn(zone);new Rule(kind,quantity,reductionTicks);
            require(raw>=0&&raw<=4096&&fuel>=0&&fuel<=32768&&output>=0&&output<=quantity,"work_progress");
            require(Set.of(ACTIVE,DONE,CANCELLED).contains(status),"work_status");
            require(route!=null&&route.size()<=2&&route.stream().allMatch(p->p!=null),"work_route");route=List.copyOf(route);
            require(kind.equals("TRANSPORT")||route.isEmpty(),"work_route");
            require(acceptedAt>=0&&lastActionAt>=acceptedAt&&finishedAt>=0,"date");
            require(ACTIVE.equals(status)?finishedAt==0:finishedAt>=lastActionAt,"work_status");
            require(!DONE.equals(status)||output==quantity,"work_progress");
            require(DONE.equals(status)?awardedTicks>0&&awardedTicks<=reductionTicks:awardedTicks==0,"work_reward");
            if(Set.of("MINING","FARMING","CLEANING","TRANSPORT").contains(kind))require(raw==0&&fuel==0,"work_progress");
            if(kind.equals("CARPENTRY"))require(fuel==0&&raw*4L+output<=quantity+3L,"work_progress");
            if(kind.equals("SMITHING"))require(raw+output<=quantity,"work_progress");
            if(kind.equals("TRANSPORT"))require(output==(route.size()==2?quantity:0),"work_route");
            if(route.size()==2){Point a=route.get(0),b=route.get(1);require(distance(a,b)>=36,"work_route");}
        }
    }
    public record Credit(UUID sentence,UUID player,long ticks){public Credit{nn(sentence);nn(player);require(ticks>0&&ticks<=JusticeLedger.MAX_TICKS,"work_reward");}}
    public record State(Map<UUID,Zone> zones,Map<UUID,Node> nodes,Map<UUID,Job> jobs,Rules rules,Map<UUID,Credit> credits) {
        public State {
            zones=copy(zones,MAX_ZONES);nodes=copy(nodes,MAX_NODES);jobs=copy(jobs,MAX_JOBS);nn(rules);credits=copy(credits,65536);for(var e:credits.entrySet())require(e.getKey().equals(e.getValue().sentence()),"reference");
            ArrayList<Zone> previous=new ArrayList<>();
            for(var e:zones.entrySet()){Zone z=e.getValue();require(e.getKey().equals(z.id()),"reference");for(Zone p:previous)require(!z.area().intersects(p.area()),"overlap");previous.add(z);}
            Set<String> positions=new HashSet<>();
            for(var e:nodes.entrySet()){Node n=e.getValue();Zone z=zones.get(n.zone());require(e.getKey().equals(n.id())&&z!=null&&z.area().contains(n.position()),"work_zone");require(positions.add(z.area().dimension()+":"+n.position()),"duplicate");require(resourceMatches(z.type(),n.resource()),"work_resource");}
            Set<UUID> activePlayers=new HashSet<>();Map<UUID,Long> totals=new LinkedHashMap<>();
            for(var e:jobs.entrySet()){Job j=e.getValue();require(e.getKey().equals(j.id()),"reference");if(DONE.equals(j.status())){Credit c=credits.get(j.sentence());require(c!=null&&c.player().equals(j.player()),"work_credit");long total=totals.merge(j.sentence(),j.awardedTicks(),Math::addExact);require(total<=c.ticks(),"work_credit");}if(ACTIVE.equals(j.status())){Zone z=zones.get(j.zone());require(z!=null&&z.prison().equals(j.prison())&&zoneType(j.kind()).equals(z.type()),"work_zone");require(activePlayers.add(j.player()),"work_active");for(Point p:j.route())require(z.area().contains(p),"work_route");}}
        }
    }
    private final State state;
    private WorkLedger(State state){this.state=nn(state);}
    public static WorkLedger empty(){return new WorkLedger(new State(Map.of(),Map.of(),Map.of(),Rules.defaults(),Map.of()));}
    public static WorkLedger restore(State state){return new WorkLedger(state);}
    public State state(){return state;}
    public Map<UUID,Zone> zones(){return state.zones();}
    public Map<UUID,Node> nodes(){return state.nodes();}
    public Map<UUID,Job> jobs(){return state.jobs();}
    public Rules rules(){return state.rules();}
    public Map<UUID,Credit> credits(){return state.credits();}
    public Job active(UUID player){return jobs().values().stream().filter(j->j.player().equals(player)&&ACTIVE.equals(j.status())).findFirst().orElse(null);}
    public WorkLedger configure(Rules rules){return with(zones(),nodes(),jobs(),nn(rules));}
    public WorkLedger putZone(Zone zone){
        nn(zone);Zone old=zones().get(zone.id());idle(zone.id());
        if(old!=null){require(old.revision()==zone.revision(),"stale");require(old.prison().equals(zone.prison())&&old.type().equals(zone.type())&&old.area().equals(zone.area()),"work_zone_fixed");}
        else require(zone.revision()==0,"revision");
        require(zone.revision()<Long.MAX_VALUE,"revision");Map<UUID,Zone> changed=new LinkedHashMap<>(zones());changed.put(zone.id(),new Zone(zone.id(),zone.prison(),zone.name(),zone.type(),zone.area(),zone.revision()+1));return with(changed,nodes(),jobs(),rules());
    }
    /** Recommissioning preserves spent nodes unless a real survival administrator refilled them. */
    public WorkLedger commission(UUID zone,List<Node> supplied){
        require(zones().containsKey(zone),"work_zone");idle(zone);Map<UUID,Node> changed=new LinkedHashMap<>(nodes());
        Map<Point,Node> previous=new LinkedHashMap<>();for(Node old:nodes().values())if(old.zone().equals(zone))previous.put(old.position(),old);
        for(Node n:supplied){require(n.zone().equals(zone)&&n.state().equals("READY"),"work_resource");Node old=previous.get(n.position());
            if(old==null){require(!changed.containsKey(n.id()),"duplicate");changed.put(n.id(),n);}
            else if(old.state().equals("REFILLABLE"))changed.put(old.id(),new Node(old.id(),zone,n.position(),n.resource(),n.block(),"READY"));
        }
        return with(zones(),changed,jobs(),rules());
    }
    public WorkLedger placed(String dimension,Point point,boolean survivalAdministrator){
        Node old=nodeAt(dimension,point);if(old==null)return this;
        String status=survivalAdministrator?"REFILLABLE":"SPENT";if(old.state().equals(status))return this;
        Map<UUID,Node> changed=new LinkedHashMap<>(nodes());changed.put(old.id(),new Node(old.id(),old.zone(),old.position(),old.resource(),old.block(),status));return with(zones(),changed,jobs(),rules());
    }
    public Node nodeAt(String dimension,Point point){return nodes().values().stream().filter(n->{Zone z=zones().get(n.zone());return z.area().dimension().equals(dimension)&&n.position().equals(point);}).findFirst().orElse(null);}
    public WorkLedger accept(UUID id,UUID player,UUID sentence,UUID prison,UUID zone,String kind,long now){
        require(active(player)==null,"work_active");Zone z=zones().get(zone);require(z!=null&&z.prison().equals(prison)&&zoneType(kind).equals(z.type()),"work_zone");require(!jobs().containsKey(id),"duplicate");
        Rule r=rules().rule(kind);require(rules().maximumPercent()>0,"work_cap");Job j=new Job(id,player,sentence,prison,zone,kind,r.quantity(),r.reductionTicks(),0,0,0,ACTIVE,List.of(),now,now,0,0);
        Map<UUID,Job> changed=new LinkedHashMap<>(jobs());
        if(changed.size()>=MAX_JOBS){UUID oldest=changed.values().stream().filter(x->!ACTIVE.equals(x.status())).min(java.util.Comparator.comparingLong(Job::finishedAt)).map(Job::id).orElseThrow(()->error("capacity"));changed.remove(oldest);}
        changed.put(id,j);return with(zones(),nodes(),changed,rules());
    }
    /** Server verified a real configured resource action; consume its proof exactly once. */
    public WorkLedger gather(UUID player,UUID node,long now){
        Job j=job(player);Node n=nodes().get(node);require(n!=null&&n.zone().equals(j.zone())&&n.state().equals("READY"),"work_resource");require(j.output()<j.quantity(),"work_ready");
        int raw=j.raw(),fuel=j.fuel(),output=j.output();
        switch(j.kind()){
            case "MINING"->{require(n.resource().equals("STONE"),"work_resource");output++;}
            case "FARMING"->{require(n.resource().equals("WHEAT"),"work_resource");output++;}
            case "CARPENTRY"->{require(n.resource().equals("LOG")&&raw*4L+output<j.quantity(),"work_resource");raw++;}
            case "SMITHING"->{require(n.resource().equals("IRON")&&raw+output<j.quantity()||n.resource().equals("COAL")&&fuel<Math.max(1,j.quantity()-output),"work_resource");if(n.resource().equals("IRON"))raw++;else fuel+=8;}
            case "CLEANING"->{require(n.resource().equals("CLEAN"),"work_resource");output++;}
            default->throw error("work_resource");
        }
        Job updated=progress(j,raw,fuel,output,j.route(),now);Map<UUID,Node> changed=new LinkedHashMap<>(nodes());changed.put(n.id(),new Node(n.id(),n.zone(),n.position(),n.resource(),n.block(),"SPENT"));return with(zones(),changed,put(updated),rules());
    }
    public WorkLedger produce(UUID player,long now){
        Job j=job(player);require(j.output()<j.quantity()&&j.raw()>0,"work_inputs");int fuel=j.fuel(),output=j.output();
        if(j.kind().equals("CARPENTRY"))output=Math.min(j.quantity(),output+4);
        else {require(j.kind().equals("SMITHING")&&fuel>0,"work_inputs");fuel--;output++;}
        return with(zones(),nodes(),put(progress(j,j.raw()-1,fuel,output,j.route(),now)),rules());
    }
    public WorkLedger cargo(UUID player,Point post,long now){
        Job j=job(player);require(j.kind().equals("TRANSPORT")&&j.route().size()<2,"work_route");Zone z=zones().get(j.zone());require(z.area().contains(post),"work_zone");List<Point> route=new ArrayList<>(j.route());if(!route.isEmpty())require(distance(route.getFirst(),post)>=36,"work_route");route.add(post);
        return with(zones(),nodes(),put(progress(j,0,0,route.size()==2?j.quantity():0,route,now)),rules());
    }
    public WorkLedger finish(UUID player,UUID sentence,long awarded,long now){
        Job j=job(player);require(j.sentence().equals(sentence)&&j.output()==j.quantity(),"work_incomplete");require(awarded>0&&awarded<=j.reductionTicks(),"work_reward");
        Credit before=credits().get(sentence);if(before!=null)require(before.player().equals(player),"work_credit");
        Map<UUID,Credit> credits=new LinkedHashMap<>(credits());credits.put(sentence,new Credit(sentence,player,Math.addExact(before==null?0:before.ticks(),awarded)));
        Job closed=new Job(j.id(),j.player(),j.sentence(),j.prison(),j.zone(),j.kind(),j.quantity(),j.reductionTicks(),j.raw(),j.fuel(),j.output(),DONE,j.route(),j.acceptedAt(),j.lastActionAt(),Math.max(now,j.lastActionAt()),awarded);
        return new WorkLedger(new State(zones(),nodes(),put(closed),rules(),credits));
    }
    public WorkLedger cancel(UUID player,long now){Job j=active(player);return j==null?this:close(j,CANCELLED,now);}
    public WorkLedger reconcile(JusticeLedger custody,long now){WorkLedger next=this;for(Job j:jobs().values())if(ACTIVE.equals(j.status())){var sentence=custody.active().get(j.player());if(sentence==null||!sentence.id().equals(j.sentence()))next=next.cancel(j.player(),now);}return next;}
    private WorkLedger close(Job j,String status,long now){Job closed=new Job(j.id(),j.player(),j.sentence(),j.prison(),j.zone(),j.kind(),j.quantity(),j.reductionTicks(),j.raw(),j.fuel(),j.output(),status,j.route(),j.acceptedAt(),j.lastActionAt(),Math.max(now,j.lastActionAt()),0);return with(zones(),nodes(),put(closed),rules());}
    private Job progress(Job j,int raw,int fuel,int output,List<Point> route,long now){require(now>=0,"date");return new Job(j.id(),j.player(),j.sentence(),j.prison(),j.zone(),j.kind(),j.quantity(),j.reductionTicks(),raw,fuel,output,ACTIVE,route,j.acceptedAt(),Math.max(now,j.lastActionAt()),0,0);}
    private Map<UUID,Job> put(Job j){Map<UUID,Job> changed=new LinkedHashMap<>(jobs());changed.put(j.id(),j);return changed;}
    private Job job(UUID player){Job j=active(player);require(j!=null,"work_missing");return j;}
    private void idle(UUID zone){require(jobs().values().stream().noneMatch(j->j.zone().equals(zone)&&j.status().equals(ACTIVE)),"work_zone_busy");}
    public static String zoneType(String kind){return switch(kind){case "MINING"->"MINE";case "FARMING"->"FARM";case "CARPENTRY","SMITHING"->"WORKSHOP";case "CLEANING"->"YARD";case "TRANSPORT"->"WAREHOUSE";default->throw error("work_kind");};}
    public static boolean resourceMatches(String type,String resource){return switch(type){case "MINE"->resource.equals("STONE");case "FARM"->resource.equals("WHEAT");case "WORKSHOP"->Set.of("LOG","IRON","COAL").contains(resource);case "YARD"->resource.equals("CLEAN");case "WAREHOUSE"->false;default->false;};}
    private static long distance(Point a,Point b){long x=(long)a.x()-b.x(),z=(long)a.z()-b.z();return x*x+z*z;}
    private WorkLedger with(Map<UUID,Zone> zones,Map<UUID,Node> nodes,Map<UUID,Job> jobs,Rules rules){return new WorkLedger(new State(zones,nodes,jobs,rules,credits()));}
    private static <T>Map<UUID,T> copy(Map<UUID,T> source,int max){require(source!=null&&source.size()<=max,"capacity");LinkedHashMap<UUID,T> result=new LinkedHashMap<>();for(var e:source.entrySet())result.put(nn(e.getKey()),nn(e.getValue()));return Collections.unmodifiableMap(result);}
    private static void text(String s,int max){require(s!=null&&!s.isBlank()&&s.length()<=max&&s.codePoints().noneMatch(Character::isISOControl),"name");}
    private static <T>T nn(T value){require(value!=null,"invalid");return value;}
    private static void require(boolean valid,String key){if(!valid)throw error(key);}
    private static IllegalArgumentException error(String key){return new IllegalArgumentException("lawsjustice.error."+key);}
}
