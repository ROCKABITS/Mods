package pt.realmfitness.domain;

import com.google.gson.Gson;
import java.time.*;
import java.util.*;

/** World-owned athlete records. All writes happen on the authoritative server thread. */
public final class FitnessLedger {
    public static final Gson JSON=new Gson();
    public static final long DAY=86_400_000L;
    public static final int MAX_PLAYERS=10000, MAX_RESULTS=100000;
    public long highWater;
    public Map<UUID,Athlete> athletes=new LinkedHashMap<>();
    public Settings settings=new Settings();
    public static final class Settings {
        public int resetMinute=0, dailyXp=240, dailySets=6, dailyVariety=3, bonusXp=30;
        public double fatiguePerSet=.12, recoveryMinutes=30;
        public Map<Exercise,Double> bonuses=new EnumMap<>(Exercise.class);
        public Settings(){for(var e:Exercise.values())bonuses.put(e,e.maximumBonus);}
        public void validate(){
            if(resetMinute<0||resetMinute>=1440||dailyXp<0||dailyXp>10000||dailySets<1||dailySets>24||dailyVariety<1||dailyVariety>6||bonusXp<0||bonusXp>1000
                    ||!Double.isFinite(fatiguePerSet)||fatiguePerSet<0||fatiguePerSet>1||!Double.isFinite(recoveryMinutes)||recoveryMinutes<1||recoveryMinutes>1440||bonuses==null||bonuses.size()!=6)
                throw new IllegalArgumentException("fitness.invalid_settings");
            for(var e:Exercise.values()){Double b=bonuses.get(e);if(b==null||!Double.isFinite(b)||b<0||b>(e==Exercise.PULL?.8:1))throw new IllegalArgumentException("fitness.invalid_settings");}
        }
        public long day(long now){return Math.floorDiv(now-resetMinute*60_000L,DAY);}
        public long resetAt(long now){return (day(now)+1)*DAY+resetMinute*60_000L;}
    }
    public static final class Athlete {
        public String name="";
        public Map<Exercise,Integer> experience=new EnumMap<>(Exercise.class);
        public Map<Exercise,Integer> preferredLoads=new EnumMap<>(Exercise.class);
        public Map<Exercise,Double> fatigue=new EnumMap<>(Exercise.class);
        public long recoveredAt, restUntil, dailyDay=Long.MIN_VALUE;
        public int earnedToday, validToday;
        public Set<Exercise> trainedToday=new HashSet<>();
        public boolean dailyComplete;
        public Set<Long> completedDays=new TreeSet<>();
        public List<Routine> routines=new ArrayList<>();
        public Workout workout;
        public List<Result> results=new ArrayList<>();
        public Map<String,Result> best=new LinkedHashMap<>();
        public Athlete(){for(var e:Exercise.values()){experience.put(e,0);fatigue.put(e,0.0);preferredLoads.put(e,e.minimumLoad());}}
    }
    public record Step(Exercise exercise,int sets,int repetitions,int load,int restSeconds) {
        public Step {
            if(exercise==null||sets<1||sets>5||repetitions<4||repetitions>20||restSeconds<5||restSeconds>180)throw new IllegalArgumentException("fitness.invalid_plan");
            exercise.checkLoad(load,20);
        }
    }
    public record Routine(UUID id,String name,int weekday,List<Step> steps) {
        public Routine {
            if(id==null||name==null||name.isBlank()||name.length()>40||name.chars().anyMatch(Character::isISOControl)||weekday<0||weekday>7||steps==null||steps.isEmpty()||steps.size()>8)
                throw new IllegalArgumentException("fitness.invalid_plan");
            steps=List.copyOf(steps);
            if(steps.stream().mapToInt(Step::sets).sum()>24)throw new IllegalArgumentException("fitness.invalid_plan");
        }
    }
    public static final class Workout {
        public Routine plan;
        public UUID id=UUID.randomUUID();
        public long started;
        public int completed;
        public Map<Integer,Integer> loadOverrides=new HashMap<>();
        public int nextIndex(){int n=completed;for(int i=0;i<plan.steps.size();i++){if(n<plan.steps.get(i).sets)return i;n-=plan.steps.get(i).sets;}return -1;}
        public int load(Step step){return step==next()?loadOverrides.getOrDefault(nextIndex(),step.load):step.load;}
        public Workout(){}
        public Workout(Routine plan,long started){this.plan=plan;this.started=started;}
        public int total(){return plan.steps.stream().mapToInt(Step::sets).sum();}
        public Step next(){int n=completed;for(var s:plan.steps){if(n<s.sets)return s;n-=s.sets;}return null;}
    }
    public record Result(UUID id,UUID player,String name,Exercise exercise,int load,int repetitions,int valid,int accuracy,long at,String dimension,long station,String gym,UUID workout,int experience) {
        public int points(){return valid*accuracy;}
        public String category(){return exercise+":"+load+":"+repetitions;}
    }
    public record Award(Result result,boolean dailyBonus,int gained,boolean workoutCompleted){}
    public long observe(long now){if(now<0)throw new IllegalArgumentException("fitness.invalid_clock");return highWater=Math.max(now,highWater);}
    public Athlete athlete(UUID id,String name,long now){
        if(id==null||name==null||name.isBlank()||name.length()>64)throw new IllegalArgumentException("fitness.invalid_player");
        if(!athletes.containsKey(id)&&athletes.size()>=MAX_PLAYERS)throw new IllegalStateException("fitness.capacity");
        Athlete a=athletes.computeIfAbsent(id,k->new Athlete());a.name=name;refresh(a,observe(now));return a;
    }
    private void refresh(Athlete a,long now){
        double recovered=Math.max(0,now-a.recoveredAt)/(settings.recoveryMinutes*60_000.0);
        a.fatigue.replaceAll((e,f)->Math.max(0,f-recovered));a.recoveredAt=now;
        long day=settings.day(now);
        if(a.dailyDay<day){a.dailyDay=day;a.earnedToday=0;a.validToday=0;a.trainedToday.clear();a.dailyComplete=false;}
        a.completedDays.removeIf(d->d<day-365);
        a.results.removeIf(r->r.at<now-70*DAY);
    }
    public static int threshold(int level){int n=Math.clamp(level,0,20);return 100*n+25*n*(n-1)/2;}
    public static int level(int xp){int n=0;while(n<20&&xp>=threshold(n+1))n++;return n;}
    public int level(Athlete a,Exercise e){return level(a.experience.getOrDefault(e,0));}
    public double bonus(Athlete a,Exercise e){return settings.bonuses.get(e)*level(a,e)/20.0;}
    public void saveRoutine(Athlete a,Routine routine){
        var existing=a.routines.stream().filter(r->r.id.equals(routine.id)).findFirst();
        if(existing.isEmpty()&&a.routines.size()>=12)throw new IllegalArgumentException("fitness.plan_limit");
        // A running workout retains its immutable plan snapshot.
        a.routines.removeIf(r->r.id.equals(routine.id));a.routines.add(routine);
    }
    public void deleteRoutine(Athlete a,UUID id){a.routines.removeIf(r->r.id.equals(id));}
    public void begin(Athlete a,UUID routine,long now){
        if(a.workout!=null&&a.workout.next()!=null)throw new IllegalArgumentException("fitness.workout_active");
        Routine r=a.routines.stream().filter(p->p.id.equals(routine)).findFirst().orElseThrow(()->new IllegalArgumentException("fitness.plan_missing"));
        for(var s:r.steps)s.exercise.checkLoad(s.load,level(a,s.exercise));
        a.workout=new Workout(r,observe(now));
    }
    public int selectedLoad(Athlete a,Exercise e){var step=a.workout==null?null:a.workout.next();return step!=null&&step.exercise==e?a.workout.load(step):a.preferredLoads.getOrDefault(e,e.minimumLoad());}
    public int cycleLoad(Athlete a,Exercise e){
        int maximum=e.maximumLoad(level(a,e)),current=selectedLoad(a,e);if(maximum==e.minimumLoad())throw new IllegalArgumentException("fitness.load_locked");
        int next=current>=maximum?e.minimumLoad():current+(e.weighted?5:1);e.checkLoad(next,level(a,e));a.preferredLoads.put(e,next);
        var step=a.workout==null?null:a.workout.next();if(step!=null&&step.exercise==e)a.workout.loadOverrides.put(a.workout.nextIndex(),next);return next;
    }
    public void ready(Athlete a,Exercise e,int load,int reps,long now){
        refresh(a,observe(now));e.checkLoad(load,level(a,e));
        if(reps<4||reps>20)throw new IllegalArgumentException("fitness.invalid_series");
        if(now<a.restUntil)throw new IllegalArgumentException("fitness.resting");
        if(a.workout!=null&&a.workout.next()!=null){var s=a.workout.next();if(s.exercise!=e||a.workout.load(s)!=load||s.repetitions!=reps)throw new IllegalArgumentException("fitness.follow_plan");}
        if(a.best.size()>4094||a.results.size()>=4096||athletes.values().stream().mapToInt(p->p.results.size()).sum()>=MAX_RESULTS)throw new IllegalStateException("fitness.capacity");
    }
    public Award finish(UUID player,String name,Minigame game,long now,String dimension,long station,String gym){
        if(!game.done())throw new IllegalArgumentException("fitness.series_unfinished");
        Athlete a=athlete(player,name,now);now=highWater;
        if(a.results.stream().anyMatch(r->r.id.equals(game.id)))throw new IllegalArgumentException("fitness.already_saved");
        ready(a,game.exercise,game.load,game.repetitions,now);
        boolean qualified=game.valid>=Math.ceil(game.repetitions*.6)&&game.accuracy()>=50;
        int gain=qualified?(int)Math.round((10+game.accuracy()/5.0)*Math.min(2,game.valid/8.0)*(1-game.fatigue*.65)):0;
        gain=credit(a,game.exercise,gain);
        boolean complete=false,bonus=false;
        UUID workout=a.workout==null?null:a.workout.id;
        if(qualified){
            if(game.repetitions>=8){a.validToday++;a.trainedToday.add(game.exercise);}
            if(a.workout!=null&&a.workout.next()!=null){a.restUntil=now+a.workout.next().restSeconds*1000L;a.workout.completed++;complete=a.workout.next()==null;}
            else a.restUntil=now+15_000L;
            a.fatigue.put(game.exercise,Math.min(1,a.fatigue.get(game.exercise)+settings.fatiguePerSet));
            if(!a.dailyComplete&&a.validToday>=settings.dailySets&&a.trainedToday.size()>=settings.dailyVariety){
                a.dailyComplete=true;a.completedDays.add(a.dailyDay);bonus=true;
                int each=settings.bonusXp/a.trainedToday.size();
                for(var e:new TreeSet<>(a.trainedToday))gain+=credit(a,e,each);
            }
        }
        Result r=new Result(game.id,player,name,game.exercise,game.load,game.repetitions,game.valid,game.accuracy(),now,dimension,station,gym,workout,gain);
        a.results.add(r);
        if(qualified)for(String key:List.of(r.category(),r.category()+":"+dimension+":"+gym)){
            Result old=a.best.get(key);if(old==null||better(r,old))a.best.put(key,r);
        }
        return new Award(r,bonus,gain,complete);
    }
    private int credit(Athlete a,Exercise e,int amount){int n=Math.max(0,Math.min(amount,Math.min(settings.dailyXp-a.earnedToday,threshold(20)-a.experience.get(e))));a.experience.merge(e,n,Integer::sum);a.earnedToday+=n;return n;}
    private static boolean better(Result a,Result b){return a.points()>b.points()||a.points()==b.points()&&a.accuracy>b.accuracy;}
    public List<Result> leaders(Exercise e,int load,int reps,String period,String dimension,String gym,long now){
        observe(now);long cutoff=switch(period){
            case "week" -> LocalDate.ofEpochDay(settings.day(highWater)).with(java.time.DayOfWeek.MONDAY).toEpochDay()*DAY+settings.resetMinute*60000L;
            case "month" -> LocalDate.ofEpochDay(settings.day(highWater)).withDayOfMonth(1).toEpochDay()*DAY+settings.resetMinute*60000L;
            case "all" -> 0L;default -> throw new IllegalArgumentException("fitness.invalid_period");};
        Map<UUID,Result> byPlayer=new HashMap<>();
        for(var a:athletes.values()){
            Collection<Result> source=period.equals("all")?a.best.values():a.results;
            for(var r:source)if(r.exercise==e&&r.load==load&&r.repetitions==reps&&r.at>=cutoff&&r.accuracy>=50&&r.valid>=Math.ceil(reps*.6)&&(gym.isEmpty()||r.gym.equals(gym)&&r.dimension.equals(dimension)))
                byPlayer.merge(r.player,r,(x,y)->better(y,x)?y:x);
        }
        return byPlayer.values().stream().sorted(Comparator.comparingInt(Result::points).reversed().thenComparing(Result::at).thenComparing(r->r.player.toString())).limit(10).toList();
    }
    public static FitnessLedger fromJson(String json){
        FitnessLedger l=JSON.fromJson(json,FitnessLedger.class);if(l==null)throw new IllegalArgumentException("fitness.corrupt_data");l.validate();return l;
    }
    public void validate(){
        settings.validate();if(highWater<0||athletes==null||athletes.size()>MAX_PLAYERS)throw new IllegalArgumentException("fitness.corrupt_data");
        Set<UUID> ids=new HashSet<>();int total=0;
        for(var entry:athletes.entrySet()){
            Athlete a=Objects.requireNonNull(entry.getValue());Objects.requireNonNull(entry.getKey());
            if(a.name==null||a.name.length()>64||a.experience==null||a.experience.size()!=6||a.fatigue==null||a.fatigue.size()!=6||a.results==null||a.results.size()>4096||a.routines==null||a.routines.size()>12||a.best==null||a.best.size()>4096||a.completedDays==null||a.completedDays.size()>367||a.trainedToday==null||a.trainedToday.size()>6||a.earnedToday<0||a.earnedToday>10000||a.validToday<0||a.validToday>100000||a.recoveredAt<0||a.restUntil<0)
                throw new IllegalArgumentException("fitness.corrupt_data");
            for(var e:Exercise.values())if(a.experience.get(e)==null||a.experience.get(e)<0||a.experience.get(e)>threshold(20)||a.fatigue.get(e)==null||!Double.isFinite(a.fatigue.get(e))||a.fatigue.get(e)<0||a.fatigue.get(e)>1)throw new IllegalArgumentException("fitness.corrupt_data");
            if(a.preferredLoads==null||a.preferredLoads.size()>6)throw new IllegalArgumentException("fitness.corrupt_data");
            for(var load:a.preferredLoads.entrySet())load.getKey().checkLoad(load.getValue(),level(a,load.getKey()));
            if(a.workout!=null){if(a.workout.loadOverrides==null||a.workout.loadOverrides.size()>8)throw new IllegalArgumentException("fitness.corrupt_data");for(var load:a.workout.loadOverrides.entrySet()){if(load.getKey()==null||load.getKey()<0||load.getKey()>=a.workout.plan.steps.size())throw new IllegalArgumentException("fitness.corrupt_data");Exercise exercise=a.workout.plan.steps.get(load.getKey()).exercise;exercise.checkLoad(load.getValue(),level(a,exercise));}}
            Set<UUID> plans=new HashSet<>();for(var r:a.routines){new Routine(r.id,r.name,r.weekday,r.steps);if(!plans.add(r.id))throw new IllegalArgumentException("fitness.corrupt_data");}
            if(a.workout!=null){new Routine(a.workout.plan.id,a.workout.plan.name,a.workout.plan.weekday,a.workout.plan.steps);if(a.workout.id==null||a.workout.completed<0||a.workout.completed>a.workout.total()||a.workout.started<0)throw new IllegalArgumentException("fitness.corrupt_data");}
            for(var r:a.results){validateResult(entry.getKey(),r);if(!ids.add(r.id))throw new IllegalArgumentException("fitness.corrupt_data");}
            for(var best:a.best.entrySet()){var r=best.getValue();validateResult(entry.getKey(),r);if(!best.getKey().equals(r.category())&&!best.getKey().equals(r.category()+":"+r.dimension+":"+r.gym))throw new IllegalArgumentException("fitness.corrupt_data");}
            total+=a.results.size();
        }
        if(total>MAX_RESULTS)throw new IllegalArgumentException("fitness.capacity");
    }
    private static void validateResult(UUID player,Result r){
        if(r==null||r.id==null||!player.equals(r.player)||r.name==null||r.name.length()>64||r.exercise==null||r.repetitions<4||r.repetitions>20||r.valid<0||r.valid>r.repetitions||r.accuracy<0||r.accuracy>100||r.at<0||r.dimension==null||r.dimension.length()>256||r.gym==null||r.gym.length()>128||r.experience<0||r.experience>10000)
            throw new IllegalArgumentException("fitness.corrupt_data");
        r.exercise.checkLoad(r.load,20);
    }
}
