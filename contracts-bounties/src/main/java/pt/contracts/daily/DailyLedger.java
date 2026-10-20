package pt.contracts.daily;

import com.google.gson.Gson;
import java.util.*;
import pt.contracts.daily.DailyRules.*;

/** Server-owned domain state. Completed reward IDs are never removed by a daily rollover. */
public final class DailyLedger {
    public static final Gson JSON=new Gson();
    public static final int MAX_PLAYERS=10000,MAX_RECEIPTS=65536,MAX_MARKS=200000;
    public UUID salt=UUID.randomUUID();
    public boolean initialized;
    public long highWater,revision;
    public Settings settings=new Settings();
    public Map<UUID,Account> players=new LinkedHashMap<>();
    public Map<UUID,Reward> rewards=new LinkedHashMap<>();
    public Map<String,Template> overrides=new LinkedHashMap<>();
    public Set<String> placed=new HashSet<>(),consumed=new HashSet<>();
    public boolean trackingFull;
    public List<Audit> audit=new ArrayList<>();
    public record Audit(long at,UUID actor,String operation,String details) {}
    public static final class Account {
        public Round round;
        public List<Round> history=new ArrayList<>();
        public Set<String> biomes=new HashSet<>(),structures=new HashSet<>(),dimensions=new HashSet<>();
        public long reputationDay=Long.MIN_VALUE;
        public int reputationReserved;
    }
    public static final class Round {
        public long day,generatedAt,resetAt,cap,allBonus,grossCompleted,grossPaid;
        public String currency;
        public boolean specialConfirmed;
        public UUID specialId;
        public List<Quest> quests=new ArrayList<>();
    }
    public static final class Quest {
        public UUID id;
        public Template template;
        public long base,bonus,gross;
        public int progress;
        public int reputation;
        public State state=State.AVAILABLE;
        public long acceptedAt,completedAt;
    }
    public static final class Reward {
        public UUID id,player,quest;
        public long day,gross,net,tax,completedAt;
        public String currency,title;
        public Difficulty difficulty;
        public int reputation,missions;
        public boolean paid,notified,prepared,deliveryConfirmed=true;
        public String error="";
    }
    public DailyLedger copy(){return fromJson(JSON.toJson(this));}
    public static DailyLedger fromJson(String json){DailyLedger state=JSON.fromJson(json,DailyLedger.class);if(state==null)throw new IllegalArgumentException("Missing daily data");state.validate();return state;}
    public void validate(){
        Objects.requireNonNull(salt);settings.validate();
        if(highWater<0||revision<0||players==null||players.size()>MAX_PLAYERS||rewards==null||rewards.size()>MAX_RECEIPTS||overrides==null||overrides.size()>1024
                ||placed==null||consumed==null||placed.size()+consumed.size()>MAX_MARKS||audit==null||audit.size()>1000)throw new IllegalArgumentException("Daily data exceeds limits");
        for(var e:players.entrySet()){
            Objects.requireNonNull(e.getKey());Account a=Objects.requireNonNull(e.getValue());
            if(a.biomes==null||a.structures==null||a.dimensions==null||a.biomes.size()>16384||a.structures.size()>16384||a.dimensions.size()>256||a.history==null||a.history.size()>366||a.reputationReserved<0||a.reputationReserved>100)throw new IllegalArgumentException("Invalid exploration or reputation history");
            if(a.round!=null)validateRound(e.getKey(),a.round);for(var r:a.history)validateRound(e.getKey(),r);
        }
        for(var e:rewards.entrySet()){
            Reward r=Objects.requireNonNull(e.getValue());
            if(!e.getKey().equals(r.id)||r.player==null||r.quest==null||r.difficulty==null||r.gross<0||r.gross>1_000_000_000L||r.tax<0||r.net<0||r.tax+r.net!=r.gross||r.reputation<0||r.reputation>100||r.missions<0||r.missions>1||!Set.of("CHRONICLES","EMERALD").contains(r.currency)||r.title==null||r.title.length()>120||r.error==null||r.error.length()>256)throw new IllegalArgumentException("Invalid daily receipt");
        }
        for(var e:overrides.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("Invalid template override");
    }
    private void validateRound(UUID player,Round r){
        if(r==null||r.quests==null||r.quests.size()!=5||r.generatedAt<0||r.resetAt<=r.generatedAt||r.cap<1||r.cap>1_000_000_000L||r.grossCompleted<0||r.grossPaid<0||r.grossPaid>r.grossCompleted||!Set.of("CHRONICLES","EMERALD").contains(r.currency))throw new IllegalArgumentException("Invalid daily round");
        long total=r.allBonus;Set<UUID> ids=new HashSet<>();Set<String> objectives=new HashSet<>();
        long completed=0,paid=0;
        for(Quest q:r.quests){
            if(q==null||q.id==null||!ids.add(q.id)||q.template==null||!objectives.add(q.template.objectiveKey())||q.gross<1||q.base<1||q.bonus<0||q.base+q.bonus!=q.gross||q.progress<0||q.progress>q.template.quantity()||q.state==null||q.reputation<0||q.reputation>100)throw new IllegalArgumentException("Invalid frozen daily quest");total=Math.addExact(total,q.gross);
            Reward reward=rewards.get(q.id);if(q.state==State.PENDING||q.state==State.PAID){
                if(reward==null||!reward.player.equals(player)||!reward.quest.equals(q.id)||reward.day!=r.day||reward.gross!=q.gross||reward.paid!=(q.state==State.PAID)||q.progress!=q.template.quantity())throw new IllegalArgumentException("Quest and reward receipt disagree");
                completed+=reward.gross;if(reward.paid)paid+=reward.gross;
            }else if(reward!=null)throw new IllegalArgumentException("Uncompleted quest has a reward receipt");
        }
        if(r.specialConfirmed){Reward special=rewards.get(r.specialId);if(special==null||special.difficulty!=Difficulty.ALL||special.gross!=r.allBonus||!special.player.equals(player)||special.day!=r.day||r.quests.stream().anyMatch(q->q.state!=State.PAID))throw new IllegalArgumentException("Invalid daily finale receipt");completed+=special.gross;if(special.paid)paid+=special.gross;}
        if(r.grossCompleted!=completed||r.grossPaid!=paid)throw new IllegalArgumentException("Daily reward totals disagree with receipts");
        if(r.allBonus<0||total>r.cap||r.grossCompleted>total)throw new IllegalArgumentException("Daily cap violated");
    }
    public Account account(UUID player){if(!players.containsKey(player)&&players.size()>=MAX_PLAYERS)throw new IllegalStateException("Daily player capacity reached");return players.computeIfAbsent(player,id->new Account());}
    public long observe(long realNow){if(realNow<0)throw new IllegalArgumentException("Invalid real clock");return highWater=Math.max(highWater,realNow);}
    public boolean needsRound(UUID player,long realNow){long now=observe(realNow);Round r=account(player).round;return r==null||now>=r.resetAt&&settings.day(now)>r.day;}
    public Round generate(UUID player,long realNow,List<Template> catalog,String currency){
        long now=observe(realNow);Account a=account(player);if(!needsRound(player,now))return a.round;
        List<Template> eligible=catalog.stream().filter(t->t.enabled()&&settings.enabled.contains(t.category())).toList();
        List<Difficulty> distribution=settings.distribution();List<Template> chosen=new ArrayList<>();Set<String> objectives=new HashSet<>();Set<Category> categories=new HashSet<>();
        long day=settings.day(now);Random rng=new Random(salt.getMostSignificantBits()^salt.getLeastSignificantBits()^player.getMostSignificantBits()^Long.rotateLeft(player.getLeastSignificantBits(),17)^day*0x9E3779B97F4A7C15L);
        for(int slot=0;slot<distribution.size();slot++){
            var difficulty=distribution.get(slot);int remainingSlot=slot+1;
            List<Template> candidates=eligible.stream().filter(t->t.difficulty()==difficulty&&!objectives.contains(t.objectiveKey())).toList();
            candidates=candidates.stream().filter(t->{Set<String> excluded=new HashSet<>(objectives);excluded.add(t.objectiveKey());return canComplete(distribution.subList(remainingSlot,distribution.size()),eligible,excluded);}).toList();
            if(candidates.isEmpty())throw new IllegalStateException("Insufficient supported templates for five distinct daily quests");
            List<Template> varied=candidates.stream().filter(t->!categories.contains(t.category())).toList();if(!varied.isEmpty())candidates=varied;
            int ticket=rng.nextInt(candidates.stream().mapToInt(Template::weight).sum());Template selected=candidates.getLast();
            for(var t:candidates){ticket-=t.weight();if(ticket<0){selected=t;break;}}
            chosen.add(selected);objectives.add(selected.objectiveKey());categories.add(selected.category());
        }
        Round round=new Round();round.day=day;round.generatedAt=now;round.resetAt=settings.nextReset(now);round.cap=settings.dailyCap;round.currency=currency;
        long minima=distribution.stream().mapToLong(d->settings.range(d).minimum()).sum();round.allBonus=Math.min(settings.allBonus,round.cap-minima);long budget=round.cap-round.allBonus;
        for(int i=0;i<chosen.size();i++){
            Template template=chosen.get(i);Range range=settings.range(template.difficulty());long future=0;for(int j=i+1;j<chosen.size();j++)future+=settings.range(chosen.get(j).difficulty()).minimum();
            long unit=currency.equals("EMERALD")?100:1;
            long random=template.reward()<0?rng.nextLong((range.minimum()+unit-1)/unit,range.maximum()/unit+1)*unit:Math.clamp(template.reward()/unit*unit,range.minimum(),range.maximum());
            Quest q=new Quest();q.id=DailyRules.key(salt+":"+player+":"+day+":"+template.id());q.template=template;q.reputation=settings.reputation(template.difficulty());
            q.base=Math.min(random,budget-future);q.bonus=Math.min(q.base*settings.bonuses.get(template.category())/10000/unit*unit,budget-future-q.base);q.gross=q.base+q.bonus;budget-=q.gross;round.quests.add(q);
        }
        if(a.round!=null){for(Quest q:a.round.quests)if(q.state==State.AVAILABLE||q.state==State.ACTIVE)q.state=State.EXPIRED;a.history.addFirst(a.round);while(a.history.size()>7){int remove=-1;for(int i=a.history.size()-1;i>=7;i--)if(a.history.get(i).quests.stream().noneMatch(q->q.state==State.PENDING||q.state==State.COMPLETED)){remove=i;break;}if(remove<0)break;a.history.remove(remove);}if(a.history.size()>366)throw new IllegalStateException("Pending daily round archive is full");}
        a.round=round;validateRound(player,round);return round;
    }
    private static boolean canComplete(List<Difficulty> slots,List<Template> catalog,Set<String> excluded){
        List<Set<String>> choices=new ArrayList<>();
        for(var difficulty:slots){Set<String> keys=new HashSet<>();for(var t:catalog)if(t.enabled()&&t.difficulty()==difficulty&&!excluded.contains(t.objectiveKey()))keys.add(t.objectiveKey());if(keys.isEmpty())return false;choices.add(keys);}
        Map<String,Integer> assigned=new HashMap<>();for(int slot=0;slot<choices.size();slot++)if(!match(slot,choices,assigned,new HashSet<>()))return false;return true;
    }
    private static boolean match(int slot,List<Set<String>> choices,Map<String,Integer> assigned,Set<String> seen){for(String key:choices.get(slot))if(seen.add(key)){Integer previous=assigned.get(key);if(previous==null||match(previous,choices,assigned,seen)){assigned.put(key,slot);return true;}}return false;}
    public Quest quest(UUID player,UUID id){Round r=account(player).round;if(r==null)throw new IllegalArgumentException("No current daily round");return r.quests.stream().filter(q->q.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Quest is unavailable"));}
    public void accept(UUID player,UUID id,long now){now=observe(now);Quest q=quest(player,id);if(now>=account(player).round.resetAt||q.state!=State.AVAILABLE)throw new IllegalStateException("Quest cannot be accepted");q.state=State.ACTIVE;q.acceptedAt=now;}
    public boolean progress(UUID player,UUID id,int count,long now){now=observe(now);Quest q=quest(player,id);if(count<1)throw new IllegalArgumentException("Positive progress required");if(q.state!=State.ACTIVE||now>=account(player).round.resetAt)return false;q.progress=(int)Math.min(q.template.quantity(),(long)q.progress+count);if(q.progress==q.template.quantity())q.state=State.COMPLETED;return q.state==State.COMPLETED;}
    public Reward complete(UUID player,UUID id,long now,int taxBps,int publicReputation,int repLimit){
        now=observe(now);Quest q=quest(player,id);Account a=account(player);Round r=a.round;
        if(q.state!=State.COMPLETED||q.progress!=q.template.quantity()||now>=r.resetAt)throw new IllegalStateException("Daily quest is not complete");
        if(rewards.size()>=MAX_RECEIPTS)throw new IllegalStateException("Daily receipt capacity reached");
        long utcDay=Math.floorDiv(now,DailyRules.DAY);if(utcDay>a.reputationDay){a.reputationDay=utcDay;a.reputationReserved=0;}
        int reputation=Math.min(q.reputation,Math.max(0,Math.min(repLimit,settings.reputationLimit)-publicReputation-a.reputationReserved));
        Reward reward=reward(player,id,r.day,q.gross,taxBps,r.currency,q.template.title(),q.template.difficulty(),reputation,1,now);
        if(rewards.putIfAbsent(reward.id,reward)!=null)throw new IllegalStateException("Duplicate daily completion");
        q.completedAt=now;q.state=State.PENDING;r.grossCompleted+=q.gross;a.reputationReserved+=reputation;return reward;
    }
    private Reward reward(UUID player,UUID quest,long day,long gross,int taxBps,String currency,String title,Difficulty difficulty,int reputation,int missions,long now){
        Reward r=new Reward();r.id=quest;r.player=player;r.quest=quest;r.day=day;r.gross=gross;r.tax=currency.equals("EMERALD")?DailyRules.tax(gross/100,taxBps)*100:DailyRules.tax(gross,taxBps);r.net=gross-r.tax;r.currency=currency;r.title=title;r.difficulty=difficulty;r.reputation=reputation;r.missions=missions;r.completedAt=now;return r;
    }
    public Reward special(UUID player,long now,int taxBps){
        return special(player,account(player).round,now,taxBps);
    }
    public Reward special(UUID player,Round round,long now,int taxBps){
        if(round==null||round.specialConfirmed||round.quests.stream().anyMatch(q->q.state!=State.PAID))return null;
        if(rewards.size()>=MAX_RECEIPTS)throw new IllegalStateException("Daily receipt capacity reached");
        UUID id=DailyRules.key(salt+":"+player+":"+round.day+":all");Reward reward=reward(player,id,round.day,round.allBonus,taxBps,round.currency,"daily.contracts.all_complete",Difficulty.ALL,0,0,observe(now));
        if(rewards.putIfAbsent(id,reward)!=null)throw new IllegalStateException("Duplicate daily finale");round.specialConfirmed=true;round.specialId=id;round.grossCompleted+=round.allBonus;return reward;
    }
    public void paid(UUID id){Reward reward=Objects.requireNonNull(rewards.get(id));if(reward.paid)return;reward.paid=true;reward.error="";Account a=account(reward.player);List<Round> rounds=new ArrayList<>(a.history);if(a.round!=null)rounds.add(a.round);for(var round:rounds)if(round.day==reward.day){round.grossPaid+=reward.gross;for(var q:round.quests)if(q.id.equals(reward.quest))q.state=State.PAID;}}
    public int reputation(UUID player,long now){Account a=account(player);return Math.floorDiv(Math.max(highWater,now),DailyRules.DAY)>a.reputationDay?0:a.reputationReserved;}
    public void audit(UUID actor,String operation,String details,long now){audit.addFirst(new Audit(observe(now),actor,operation,details.substring(0,Math.min(details.length(),1024))));while(audit.size()>1000)audit.removeLast();revision++;}
    public boolean mark(String key,boolean placedBlock){if(placed.contains(key)||consumed.contains(key))return false;if(placed.size()+consumed.size()>=MAX_MARKS){trackingFull=true;return false;}return (placedBlock?placed:consumed).add(key);}
}
