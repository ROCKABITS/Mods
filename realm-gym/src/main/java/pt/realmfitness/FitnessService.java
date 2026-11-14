package pt.realmfitness;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForgeMod;
import pt.realmfitness.domain.*;
import pt.realmfitness.domain.FitnessLedger.*;

public final class FitnessService {
    private static final Map<UUID,View> VIEWS=new HashMap<>();
    private static final Map<UUID,Training> ACTIVE=new HashMap<>();
    private static final Map<UUID,Integer> LAST=new HashMap<>();
    private static boolean configured;
    public static final class View {
        public BlockPos pos;
        public ResourceLocation dimension;
        public boolean board;
        public int expires;
        public Exercise exercise=Exercise.BENCH;
        public int load=20,reps=8;
        public String period="all",scope="server";
    }
    public record Training(Minigame game,BlockPos pos,ResourceLocation dimension,double x,double y,double z,int startsAt,GymMount mount){}
    private FitnessService(){}
    public static void stop(MinecraftServer server){for(var p:server.getPlayerList().getPlayers())cancel(p);}
    public static void clear(){ACTIVE.clear();VIEWS.clear();LAST.clear();configured=false;}
    private static FitnessData data(ServerPlayer p){var d=FitnessData.get(p.getServer());d.available();if(!configured){reload(p.getServer());configured=true;}return d;}
    public static void reload(MinecraftServer server){
        Path file=FMLPaths.CONFIGDIR.get().resolve("realm-fitness.json");
        try{
            if(!Files.exists(file))Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(new Settings())+"\n");
            if(Files.size(file)>16384)throw new IllegalArgumentException("fitness.invalid_settings");
            Settings settings=FitnessLedger.JSON.fromJson(Files.readString(file),Settings.class);settings.validate();
            var d=FitnessData.get(server);d.ledger.settings=settings;d.persist(server);configured=true;
            for(var p:server.getPlayerList().getPlayers())apply(p);
        }catch(java.io.IOException e){throw new IllegalStateException("fitness.invalid_settings",e);}
    }
    public static String gym(BlockPos p){return Math.floorDiv(p.getX(),32)+","+Math.floorDiv(p.getZ(),32);}
    private static Athlete athlete(ServerPlayer p){return data(p).ledger.athlete(p.getUUID(),p.getGameProfile().getName(),System.currentTimeMillis());}
    public static void open(ServerPlayer p,BlockPos pos,boolean board){
        if(pos!=null&&!board){interact(p,pos,false);return;}
        try{
            View v=new View();v.pos=pos==null?null:pos.immutable();v.dimension=p.level().dimension().location();v.board=board;v.expires=p.getServer().getTickCount()+6000;
            if(pos!=null){if(p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>64)throw new IllegalArgumentException("fitness.too_far");
                var state=p.level().getBlockState(pos);if(board){
                    if(!(p.level().getBlockEntity(pos) instanceof RecordBoardEntity b))throw new IllegalArgumentException("fitness.station_missing");
                    v.exercise=Exercise.valueOf(b.exercise);v.load=b.load;v.reps=b.repetitions;v.period=b.period;v.scope=b.scope;
                }else if(state.getBlock() instanceof GymBlock b&&b.intact(p.level(),pos))v.exercise=b.exercise;else throw new IllegalArgumentException("fitness.station_missing");
            }
            VIEWS.put(p.getUUID(),v);var a=athlete(p);if(a.routines.isEmpty())suggestions(a);data(p).persist(p.getServer());snapshot(p,"",board?"leaders":pos==null?"profile":"machine");
        }catch(RuntimeException e){p.displayClientMessage(Component.translatable(error(e)),true);}
    }
    public static void interact(ServerPlayer p,BlockPos pos,boolean adjusting){
        try{
            if(ACTIVE.containsKey(p.getUUID()))throw new IllegalArgumentException("fitness.already_training");
            if(!p.isAlive()||p.isSpectator()||p.isPassenger())throw new IllegalArgumentException("fitness.survival_required");
            if(!p.level().hasChunkAt(pos)||p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>64)throw new IllegalArgumentException("fitness.too_far");
            var state=p.level().getBlockState(pos);if(!(state.getBlock() instanceof GymBlock block)||!block.intact(p.level(),pos))throw new IllegalArgumentException("fitness.station_missing");
            for(var t:ACTIVE.values())if(t.dimension.equals(p.level().dimension().location())&&t.pos.equals(pos))throw new IllegalArgumentException("fitness.machine_busy");
            var d=data(p);var a=athlete(p);Exercise exercise=block.exercise;
            if(adjusting){int load=d.ledger.cycleLoad(a,exercise);d.persist(p.getServer());p.displayClientMessage(Component.translatable("fitness.load_changed",Component.translatable("fitness.exercise."+exercise.id()),exercise.weighted?load+" kg":Component.translatable("fitness.resistance",load)),true);return;}
            if(!state.getValue(GymBlock.EXPANDED))throw new IllegalArgumentException("fitness.replace_legacy");
            View v=new View();v.pos=pos.immutable();v.dimension=p.level().dimension().location();v.exercise=exercise;v.expires=p.getServer().getTickCount()+6000;
            var step=a.workout==null?null:a.workout.next();v.load=d.ledger.selectedLoad(a,exercise);v.reps=step==null?8:step.repetitions();
            VIEWS.put(p.getUUID(),v);start(p,v,v.load,v.reps);
        }catch(RuntimeException e){p.displayClientMessage(Component.translatable(error(e)),true);}
    }
    private static void suggestions(Athlete a){
        a.routines.add(new Routine(UUID.randomUUID(),"fitness.plan_balanced",0,List.of(new Step(Exercise.BENCH,2,8,20,30),new Step(Exercise.RUN,2,8,1,30),new Step(Exercise.BOX,2,8,1,30))));
        a.routines.add(new Routine(UUID.randomUUID(),"fitness.plan_strength",0,List.of(new Step(Exercise.BENCH,2,8,20,30),new Step(Exercise.PULL,2,8,1,30),new Step(Exercise.SQUAT,2,8,20,30))));
    }
    private static View view(ServerPlayer p,boolean physical){
        View v=VIEWS.get(p.getUUID());if(v==null||v.expires<p.getServer().getTickCount()||!v.dimension.equals(p.level().dimension().location()))throw new IllegalArgumentException("fitness.session_closed");
        if(physical||v.pos!=null){
            if(v.pos==null||p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(v.pos))>64||!p.level().hasChunkAt(v.pos))throw new IllegalArgumentException("fitness.too_far");
            if(v.board){if(!(p.level().getBlockEntity(v.pos) instanceof RecordBoardEntity))throw new IllegalArgumentException("fitness.station_missing");}
            else if(!(p.level().getBlockState(v.pos).getBlock() instanceof GymBlock b)||!b.intact(p.level(),v.pos))throw new IllegalArgumentException("fitness.station_missing");
        }
        v.expires=p.getServer().getTickCount()+6000;return v;
    }
    public static void request(ServerPlayer p,String operation,String json){
        if(!p.getServer().isSameThread())throw new IllegalStateException("Fitness requires server thread");
        try{
            if(json.length()>16384)throw new IllegalArgumentException("fitness.invalid_request");
            JsonObject j=JsonParser.parseString(json).getAsJsonObject();
            if(operation.equals("input")){Training t=ACTIVE.get(p.getUUID());if(t!=null&&t.game.id.toString().equals(string(j,"id",36)))t.game.input(number(j,"sequence",0,10000),number(j,"keys",0,127));return;}
            if(operation.equals("cancel")){cancel(p);return;}
            int now=p.getServer().getTickCount();Integer old=LAST.put(p.getUUID(),now);if(old!=null&&now-old<2)return;
            if(operation.equals("open")){open(p,null,false);return;}
            View v=view(p,operation.equals("start")||operation.equals("filter")||operation.equals("pin"));
            if(ACTIVE.containsKey(p.getUUID()))throw new IllegalArgumentException("fitness.already_training");
            var d=data(p);var a=athlete(p);String page="";
            switch(operation){
                case "refresh" -> {}
                case "routine" -> {Routine r=FitnessLedger.JSON.fromJson(j,Routine.class);d.ledger.saveRoutine(a,r);d.persist(p.getServer());page="routines";}
                case "delete" -> {d.ledger.deleteRoutine(a,UUID.fromString(string(j,"id",36)));d.persist(p.getServer());page="routines";}
                case "begin" -> {d.ledger.begin(a,UUID.fromString(string(j,"id",36)),System.currentTimeMillis());d.persist(p.getServer());page="routines";}
                case "end_workout" -> {a.workout=null;d.persist(p.getServer());page="routines";}
                case "start" -> {if(v.board)throw new IllegalArgumentException("fitness.station_missing");start(p,v,number(j,"load",1,240),number(j,"repetitions",4,20));return;}
                case "filter", "pin" -> {
                    if(!v.board)throw new IllegalArgumentException("fitness.station_missing");
                    v.exercise=Exercise.valueOf(string(j,"exercise",12));v.load=number(j,"load",1,240);v.exercise.checkLoad(v.load,20);v.reps=number(j,"repetitions",4,20);
                    v.period=string(j,"period",8);v.scope=string(j,"scope",8);if(!Set.of("all","week","month").contains(v.period)||!Set.of("gym","server").contains(v.scope))throw new IllegalArgumentException("fitness.invalid_request");
                    if(operation.equals("pin")){if(!p.hasPermissions(2))throw new IllegalArgumentException("fitness.forbidden");var b=(RecordBoardEntity)p.level().getBlockEntity(v.pos);b.exercise=v.exercise.name();b.load=v.load;b.repetitions=v.reps;b.period=v.period;b.scope=v.scope;updateBoard(p.getServer(),b);}
                    page="leaders";
                }
                default -> throw new IllegalArgumentException("fitness.invalid_request");
            }
            snapshot(p,"",page);
        }catch(RuntimeException|LinkageError error){FitnessMod.LOG.debug("Fitness operation rejected: {}",error.getMessage());try{snapshot(p,error(error),"");}catch(RuntimeException ignored){p.displayClientMessage(Component.translatable(error(error)),true);}}
    }
    public static void start(ServerPlayer p,View v,int load,int repetitions){
        if(p.isSpectator()||p.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL||!p.isAlive()||p.isPassenger()||p instanceof net.neoforged.neoforge.common.util.FakePlayer)throw new IllegalArgumentException("fitness.survival_required");
        if(v.pos==null||!(p.level().getBlockState(v.pos).getBlock() instanceof GymBlock block)||!block.intact(p.level(),v.pos))throw new IllegalArgumentException("fitness.station_missing");
        for(var t:ACTIVE.values())if(t.dimension.equals(v.dimension)&&t.pos.equals(v.pos))throw new IllegalArgumentException("fitness.machine_busy");
        var d=data(p);var a=athlete(p);d.ledger.ready(a,v.exercise,load,repetitions,d.ledger.highWater);
        Minigame g=new Minigame(UUID.randomUUID(),v.exercise,load,repetitions,p.getRandom().nextLong(),a.fatigue.get(v.exercise.attribute()),d.ledger.level(a,v.exercise));
        GymMount mount=FitnessMod.MOUNT.get().create(p.level());if(mount==null)throw new IllegalStateException("fitness.station_missing");
        var state=p.level().getBlockState(v.pos);mount.configure(v.pos,state.getValue(GymBlock.FACING),v.exercise,!state.getValue(GymBlock.EXPANDED),p.position());
        if(!p.serverLevel().addFreshEntity(mount)){mount.discard();throw new IllegalArgumentException("fitness.machine_busy");}
        Training training=new Training(g,v.pos.immutable(),v.dimension,p.getX(),p.getY(),p.getZ(),p.getServer().getTickCount()+40,mount);
        if(!p.startRiding(mount,true)){mount.discard();throw new IllegalArgumentException("fitness.training_interrupted");}
        ACTIVE.put(p.getUUID(),training);mount.positionRider(p);block.working(p.level(),v.pos,true);sendGame(p,g,true);
    }
    private static void sendGame(ServerPlayer p,Minigame g,boolean opening){
        var t=ACTIVE.get(p.getUUID());Map<String,Object> payload=new LinkedHashMap<>();payload.put("type","game");payload.put("open",opening);payload.put("id",g.id.toString());payload.put("exercise",g.exercise.name());payload.put("load",g.load);payload.put("repetitions",g.repetitions);payload.put("seed",g.seed);payload.put("fatigue",g.fatigue);payload.put("trainingLevel",g.trainingLevel);payload.put("yaw",t==null?p.getYRot():t.mount.actorYaw());payload.put("countdown",t==null?0:Math.max(0,t.startsAt-p.getServer().getTickCount()));
        Map<String,Object> state=new LinkedHashMap<>();state.put("tick",g.tick);state.put("sequence",g.sequence());state.put("feedbackId",g.feedbackId);state.put("effort",g.effort);state.put("balance",g.balance);state.put("leftPressure",g.leftPressure);state.put("rightPressure",g.rightPressure);state.put("grip",g.grip);state.put("valid",g.valid);state.put("accuracy",g.accuracy());state.put("target",g.target());state.put("tolerance",g.tolerance());state.put("stage",g.stage());state.put("locked",g.locked);state.put("landed",g.landed);state.put("marker",g.marker());state.put("streak",g.streak);state.put("bestStreak",g.bestStreak);state.put("feedback",g.feedback);state.put("feedbackTicks",g.feedbackTicks);state.put("nextFoot",g.nextFoot);state.put("holdTicks",g.holdTicks);state.put("holdRequired",g.squatHoldRequired());payload.put("state",state);FitnessNetwork.send(p,payload);
    }
    public static void tick(MinecraftServer server){
        for(UUID id:List.copyOf(ACTIVE.keySet())){
            var p=server.getPlayerList().getPlayer(id);Training t=ACTIVE.get(id);if(p==null){t.mount.discard();if(t.mount.level().getBlockState(t.pos).getBlock() instanceof GymBlock b)b.working(t.mount.level(),t.pos,false);ACTIVE.remove(id);continue;}
            try{
                view(p,true);
                if(!p.isAlive()||p.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL||p.getVehicle()!=t.mount||t.mount.isRemoved()||!t.dimension.equals(p.level().dimension().location()))throw new IllegalArgumentException("fitness.training_interrupted");
                if(server.getTickCount()<t.startsAt){t.game.clearEdges();if(server.getTickCount()%4==0)sendGame(p,t.game,false);continue;}
                t.game.advance();t.mount.progress(t.game.tick,t.game.effort,t.game.keys());
                if(t.game.done()){
                    release(p,t);var d=data(p);Award award=d.ledger.finish(id,p.getGameProfile().getName(),t.game,System.currentTimeMillis(),t.dimension.toString(),t.pos.asLong(),gym(t.pos));d.persist(server);apply(p);FitnessNetwork.send(p,Map.of("type","done","valid",award.result().valid(),"repetitions",award.result().repetitions(),"accuracy",award.result().accuracy(),"xp",award.gained(),"daily",award.dailyBonus(),"bestStreak",t.game.bestStreak));
                    refreshBoards(server);
                }else{
                    if(t.game.tick%2==0)sendGame(p,t.game,false);

                }
            }catch(RuntimeException e){cancel(p);p.displayClientMessage(Component.translatable(error(e)),true);}
        }
        if(server.getTickCount()%100==0)refreshBoards(server);
    }
    private static final Set<RecordBoardEntity> BOARDS=Collections.newSetFromMap(new WeakHashMap<>());
    public static void watch(RecordBoardEntity b){BOARDS.add(b);}
    public static void refreshBoards(MinecraftServer server){for(var b:List.copyOf(BOARDS))if(!b.isRemoved()&&b.getLevel()!=null&&!b.getLevel().isClientSide&&b.getLevel().getServer()==server)updateBoard(server,b);}
    private static void updateBoard(MinecraftServer server,RecordBoardEntity b){
        var results=FitnessData.get(server).ledger.leaders(Exercise.valueOf(b.exercise),b.load,b.repetitions,b.period,b.getLevel().dimension().location().toString(),b.scope.equals("gym")?gym(b.getBlockPos()):"",System.currentTimeMillis());
        String display=FitnessLedger.JSON.toJson(results.stream().map(r->Map.of("player",r.player().toString(),"name",r.name(),"points",r.points(),"accuracy",r.accuracy())).toList());
        if(!b.display.equals(display)){b.display=display;b.sync();}else b.setChanged();
    }
    private static void release(ServerPlayer p,Training t){
        ACTIVE.remove(p.getUUID());var exit=t.mount.safeExit(p);
        if(p.getVehicle()==t.mount)p.stopRiding();t.mount.discard();
        if(t.dimension.equals(p.level().dimension().location())){p.teleportTo(p.serverLevel(),exit.x,exit.y,exit.z,p.getYRot(),p.getXRot());p.fallDistance=0;}
        if(t.mount.level().getBlockState(t.pos).getBlock() instanceof GymBlock block)block.working(t.mount.level(),t.pos,false);
    }
    public static void cancel(ServerPlayer p){Training t=ACTIVE.get(p.getUUID());if(t!=null)release(p,t);FitnessNetwork.send(p,Map.of("type","cancel"));}
    public static void logout(ServerPlayer p){cancel(p);VIEWS.remove(p.getUUID());LAST.remove(p.getUUID());}
    public static double fallReduction(ServerPlayer p){var d=data(p);return d.ledger.bonus(athlete(p),Exercise.PULL);}
    public static void apply(ServerPlayer p){
        var d=data(p);var a=athlete(p);
        modify(p,Attributes.ATTACK_DAMAGE,Exercise.BENCH,d.ledger.bonus(a,Exercise.BENCH));
        modify(p,Attributes.MOVEMENT_SPEED,Exercise.RUN,d.ledger.bonus(a,Exercise.RUN));
        modify(p,Attributes.ATTACK_SPEED,Exercise.BOX,d.ledger.bonus(a,Exercise.BOX));
        modify(p,NeoForgeMod.SWIM_SPEED,Exercise.ROW,d.ledger.bonus(a,Exercise.ROW));
        // Jump height is proportional to velocity squared.
        modify(p,Attributes.JUMP_STRENGTH,Exercise.SQUAT,Math.sqrt(1+d.ledger.bonus(a,Exercise.SQUAT))-1);
    }
    private static void modify(ServerPlayer p,Holder<Attribute> attr,Exercise exercise,double value){var instance=p.getAttribute(attr);if(instance==null)return;var id=ResourceLocation.fromNamespaceAndPath("realmfitness",exercise.id());instance.removeModifier(id);if(value>0)instance.addTransientModifier(new AttributeModifier(id,value,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
    public static void snapshot(ServerPlayer p,String message,String page){
        var d=data(p);var a=athlete(p);View v=VIEWS.get(p.getUUID());Map<String,Object> out=new LinkedHashMap<>();out.put("type","profile");out.put("page",page);out.put("message",message);out.put("name",a.name);out.put("now",d.ledger.highWater);out.put("resetAt",d.ledger.settings.resetAt(d.ledger.highWater));
        out.put("experience",a.experience);out.put("fatigue",a.fatigue);out.put("restUntil",a.restUntil);out.put("dailySets",a.validToday);out.put("dailyGoal",d.ledger.settings.dailySets);out.put("dailyVariety",a.trainedToday.size());out.put("varietyGoal",d.ledger.settings.dailyVariety);out.put("dailyComplete",a.dailyComplete);out.put("dailyXp",a.earnedToday);out.put("dailyXpCap",d.ledger.settings.dailyXp);out.put("bonuses",d.ledger.settings.bonuses);out.put("days",a.completedDays);out.put("routines",a.routines);
        if(a.workout!=null)out.put("workout",a.workout);
        out.put("history",a.results.reversed().stream().limit(24).toList());out.put("records",a.best.entrySet().stream().filter(e->e.getKey().equals(e.getValue().category())).map(Map.Entry::getValue).sorted(Comparator.comparing(Result::load).reversed()).limit(24).toList());
        Set<String> available=new HashSet<>();
        for(BlockPos q:BlockPos.betweenClosed(p.blockPosition().offset(-12,-3,-12),p.blockPosition().offset(12,3,12)))if(p.level().hasChunkAt(q)&&p.level().getBlockState(q).getBlock() instanceof GymBlock b&&p.level().getBlockState(q).getValue(GymBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)available.add(b.exercise.name());
        out.put("available",available);
        if(v!=null){out.put("machine",v.pos!=null&&!v.board?v.exercise.name():"");out.put("board",v.board);out.put("exercise",v.exercise.name());out.put("load",v.load);out.put("repetitions",v.reps);out.put("period",v.period);out.put("scope",v.scope);out.put("mayPin",p.hasPermissions(2));
            if(v.board&&v.pos!=null){out.put("leaders",d.ledger.leaders(v.exercise,v.load,v.reps,v.period,v.dimension.toString(),v.scope.equals("gym")?gym(v.pos):"",d.ledger.highWater));}
        }
        FitnessNetwork.send(p,out);
    }
    private static String string(JsonObject j,String key,int max){if(!j.has(key)||!j.get(key).isJsonPrimitive()||!j.getAsJsonPrimitive(key).isString())throw new IllegalArgumentException("fitness.invalid_request");String s=j.get(key).getAsString();if(s.length()>max)throw new IllegalArgumentException("fitness.invalid_request");return s;}
    private static int number(JsonObject j,String key,int min,int max){int n=j.get(key).getAsBigDecimal().intValueExact();if(n<min||n>max)throw new IllegalArgumentException("fitness.invalid_request");return n;}
    private static String error(Throwable e){String s=e.getMessage();return s!=null&&s.startsWith("fitness.")?s:"fitness.invalid_request";}
}
