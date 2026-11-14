package pt.realmfitness.visualtest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import pt.realmfitness.*;
import pt.realmfitness.client.*;
import pt.realmfitness.domain.*;
import pt.realmfitness.domain.FitnessLedger.*;

/** Actual client inputs and C2S/S2C packets; no client-submitted scores or synthetic results. */
@EventBusSubscriber(modid="realmfitness",value=Dist.CLIENT)
public final class FitnessVisualQA {
    private static final int COUNT=Exercise.values().length;
    private static final Path OUT=Path.of(System.getProperty("fitness.qaOutput"));
    private static final long START=System.currentTimeMillis();
    private static final List<String> CHECKS=new ArrayList<>(),CAPTURES=new ArrayList<>();
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile UUID playerId,planId;
    private static volatile String folder;
    private static int stage,exercise,held,lastStage=-1,galleryIndex=-1;
    private static long next,stageSince;
    private static boolean done,shot,mountedSeen,compactShot;
    private static int joinPhase,cancelMode,lastBenchTap=-100,lastRunTap=-100;
    private static final Map<String,Integer> FRAMES=new HashMap<>();
    private static final Map<String,Long> CAPTURE_STARTED=new HashMap<>();
    private static JsonObject profile;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayer(playerId);}
    private static BlockPos station(int i){return new BlockPos(i*5-18,161,0);}
    private static final BlockPos BOARD=new BlockPos(0,161,5);
    private static void server(Runnable task){ready=false;mc().getSingleplayerServer().execute(()->{try{task.run();ready=true;}catch(Throwable e){failure=e;}});}
    private static boolean click(String key){if(mc().screen==null)return false;String label=key.startsWith("selectWorld.")||key.startsWith("gui.")?Component.translatable(key).getString():FitnessTheme.text(key);for(var c:List.copyOf(mc().screen.children()))if(c instanceof AbstractButton b&&b.active&&b.visible&&b.getMessage().getString().equals(label)){b.onPress();return true;}return false;}
    private static void screenshot(String name)throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}if(!CAPTURES.contains(name))CAPTURES.add(name);}
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void prepare(){
        var p=player();var server=p.getServer();p.setGameMode(GameType.SURVIVAL);server.setDifficulty(Difficulty.PEACEFUL,true);p.serverLevel().setDayTime(6000);
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);server.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,server);
        for(int x=-22;x<=23;x++)for(int z=-5;z<=9;z++){for(int y=161;y<=164;y++)p.level().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());p.level().setBlockAndUpdate(new BlockPos(x,160,z),Blocks.POLISHED_DEEPSLATE.defaultBlockState());}
        for(var e:Exercise.values()){var pos=station(e.ordinal());var block=FitnessMod.MACHINES.get(e).get();for(int i=0;i<18;i++)if(GymGeometry.contains(e,i))p.level().setBlock(GymBlock.cell(pos,Direction.NORTH,i),block.partState(Direction.NORTH,i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);check(block.intact(p.level(),pos),"Expanded visual machine is incomplete");}
        p.level().setBlockAndUpdate(BOARD,FitnessMod.BOARD.get().defaultBlockState());
        p.teleportTo(p.serverLevel(),.5,161,-3.5,0,0);p.getInventory().clearContent();
        var d=FitnessData.get(server);d.ledger=new FitnessLedger();var a=d.ledger.athlete(p.getUUID(),p.getGameProfile().getName(),System.currentTimeMillis());
        // Controlled baseline exposes a level-up from each genuinely played series.
        for(var e:Exercise.values())a.experience.put(e.attribute(),90);
        d.persist(server);FitnessService.apply(p);FitnessService.open(p,null,false);
        folder=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
    }
    private static void worldModel(int index){
        mc().setScreen(null);mc().options.hideGui=true;mc().options.setCameraType(CameraType.FIRST_PERSON);
        server(()->{var p=player();var pos=station(index);p.setGameMode(GameType.SPECTATOR);p.teleportTo(p.serverLevel(),pos.getX()+4.5,162.6,-3.8,33.5f,20);});
    }
    private static void exercise(){
        var p=player();var a=FitnessData.get(p.getServer()).ledger.athletes.get(p.getUUID());a.restUntil=0;
        var pos=station(exercise);p.teleportTo(p.serverLevel(),pos.getX()+.5,161,-1.8,0,0);if(exercise==0)a.experience.put(Exercise.BENCH,100);
    }
    private static void useMachine(){var pos=station(exercise);mc().gameMode.useItemOn(mc().player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false));}
    private static void feed(TrainingHud screen)throws Exception{
        var state=(JsonObject)field(screen,"state");var pattern=(Minigame)field(screen,"pattern");long received=(long)field(screen,"received");
        int t=FitnessClient.num(state,"tick")+(int)Math.min(4,(System.nanoTime()-received)/50_000_000)+1;
        if((int)field(screen,"countdown")>0){release(screen);return;}
        int bits=0;String stage=FitnessClient.str(state,"stage");double effort=state.get("effort").getAsDouble();int index=FitnessClient.num(state,"tick")/pattern.repTicks();
        if(!stage.equals("rest")&&!stage.equals("miss"))switch(pattern.exercise){
            case BENCH->{if(t-lastBenchTap>=4&&(held&Minigame.SPACE)==0){bits=Minigame.SPACE;lastBenchTap=t;}}
            case BOX->{if(!state.get("locked").getAsBoolean()&&Math.abs(pattern.boxMarkerAt(t)-.5)<.07)bits=Minigame.LEFT;}
            case RUN->{double predicted=effort-pattern.runDrag()*Math.min(3,(System.nanoTime()-received)/50_000_000.0);if(predicted<state.get("target").getAsDouble()&&t-lastRunTap>=5){bits=FitnessClient.num(state,"nextFoot");lastRunTap=t;}}
            case PULL->{if(stage.equals("pull"))bits|=Minigame.SPACE;bits|=state.get("balance").getAsDouble()>.5?Minigame.A:Minigame.D;}
            case ROW->{double predicted=effort+((held&Minigame.W)!=0?pattern.rowChargeSpeed():0)*Math.min(3,(System.nanoTime()-received)/50_000_000.0);if(stage.equals("charge"))bits=predicted<.71?Minigame.W:0;else if(stage.equals("recover"))bits=Minigame.S;}
            case SQUAT->{double predicted=effort+((held&Minigame.W)!=0?.045:0)*Math.min(3,(System.nanoTime()-received)/50_000_000.0);if(stage.equals("rise"))bits=Minigame.S;else if(predicted<.73)bits=Minigame.W;}
            case LEG_PRESS->{double elapsed=Math.min(2,(System.nanoTime()-received)/50_000_000.0),rate=pattern.legPressureRate();if(stage.equals("leg_push")){double left=state.get("leftPressure").getAsDouble()+((held&Minigame.A)!=0?rate:-rate*.7)*elapsed,right=state.get("rightPressure").getAsDouble()+((held&Minigame.D)!=0?rate:-rate*.7)*elapsed;if(left<state.get("target").getAsDouble())bits|=Minigame.A;if(right<state.get("target").getAsDouble())bits|=Minigame.D;}}
            case TRICEPS->{if(stage.equals("rope_pull")){bits=Minigame.SPACE;double predicted=effort+pattern.ropeSpeed()*Math.min(2,(System.nanoTime()-received)/50_000_000.0);if(predicted>=.83&&predicted<=.91)bits|=Minigame.LEFT;}else if(stage.equals("rope_hold"))bits=Minigame.SPACE;}
        }
        for(int bit:new int[]{1,2,4,8,16,32,64})if((bits&bit)!=(held&bit)){
            boolean down=(bits&bit)!=0;
            if(bit==32||bit==64){if(down)screen.mouseClicked(0,mc().getWindow().getGuiScaledHeight()/2.0,bit==32?0:1);else screen.mouseReleased(0,mc().getWindow().getGuiScaledHeight()/2.0,bit==32?0:1);}
            else{int code=switch(bit){case 1->GLFW.GLFW_KEY_SPACE;case 2->GLFW.GLFW_KEY_A;case 4->GLFW.GLFW_KEY_D;case 8->GLFW.GLFW_KEY_W;default->GLFW.GLFW_KEY_S;};if(down)screen.keyPressed(code,0,0);else screen.keyReleased(code,0,0);}
        }
        held=bits;
    }
    private static void release(TrainingHud screen){for(int code:new int[]{GLFW.GLFW_KEY_SPACE,GLFW.GLFW_KEY_A,GLFW.GLFW_KEY_D,GLFW.GLFW_KEY_W,GLFW.GLFW_KEY_S})screen.keyReleased(code,0,0);screen.mouseReleased(0,0,0);screen.mouseReleased(0,0,1);held=0;}
    private static void complete(boolean success,String message){done=true;try{Files.createDirectories(OUT);Map<String,Object> result=new LinkedHashMap<>();result.put("success",success);result.put("stage",stage);result.put("message",message);result.put("checks",CHECKS);result.put("screenshots",CAPTURES);result.put("actualMinecraftClient",true);result.put("actualIntegratedServer",true);result.put("controlledBaselineXp",90);result.put("syntheticSetResults",false);result.put("actualMountedMinigames",true);result.put("motionFrames",FRAMES);result.put("hudPresentation",Map.of("frames",TrainingHud.renderedFrames,"interpolatedFrames",TrainingHud.interpolatedFrames,"snapshots",TrainingHud.snapshots));Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){e.printStackTrace();}System.out.println(success?"FITNESS_CLIENT_PASSED":"FITNESS_CLIENT_FAILED "+message);mc().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(done)return;long now=System.currentTimeMillis();
        try{
            if(failure!=null)throw new IllegalStateException("Integrated server check failed",failure);
            check(now-START<900000,"Fitness QA timed out at stage "+stage);
            if(TrainingHud.active()!=null&&stage<30){var s=TrainingHud.active();check(mc().screen==null,"Mounted minigame opened a menu");feed(s);int tick=FitnessClient.num((JsonObject)field(s,"state"),"tick");String id=Exercise.values()[exercise].id();int frame=FRAMES.getOrDefault(id,0);
                if(tick>=60)CAPTURE_STARTED.putIfAbsent(id,now);
                if(frame<24&&tick>=60&&now>=CAPTURE_STARTED.get(id)+frame*100L){Path target=OUT.resolve("motion/"+id);Files.createDirectories(target);try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){image.writeToFile(target.resolve(String.format("frame-%03d.png",frame)));}FRAMES.put(id,frame+1);}
                if(!shot&&tick>112){screenshot("fitness-minigame-"+id+".png");shot=true;}
                if(exercise==0&&!compactShot&&tick>135){mc().getWindow().setWindowed(640,480);mc().resizeDisplay();compactShot=true;}
                if(exercise==0&&compactShot&&mc().getWindow().getScreenWidth()==640&&tick>160){screenshot("fitness-compact-training.png");mc().getWindow().setWindowed(1600,900);mc().resizeDisplay();}
            }
            if(now<next||mc().getOverlay()!=null)return;next=now+180;
            if(stage!=lastStage){lastStage=stage;stageSince=now;System.out.println("FITNESS_QA_STAGE "+stage);}else check(now-stageSince<150000,"Fitness stage stalled "+stage);
            switch(stage){
                case 0->{if(mc().screen instanceof TitleScreen){mc().getWindow().setWindowed(1600,900);mc().options.pauseOnLostFocus=false;mc().options.renderDistance().set(3);mc().options.simulationDistance().set(5);mc().options.guiScale().set(2);mc().resizeDisplay();mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);CreateWorldScreen.openFresh(mc(),mc().screen);stage++;}else if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility")){for(var child:List.copyOf(mc().screen.children()))if(child instanceof AbstractButton b&&b.getMessage().getString().equals(Component.translatable("gui.continue").getString()))b.onPress();}}
                case 1->{if(mc().screen instanceof CreateWorldScreen world){world.getUiState().setName("Realm Gym client QA");world.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);world.getUiState().setAllowCommands(true);check(click("selectWorld.create"),"Create world button missing");stage++;}}
                case 2->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){playerId=mc().player.getUUID();server(FitnessVisualQA::prepare);stage++;}}
                case 3->{if(!ready||!(mc().screen instanceof AthleteScreen))return;profile=((AthleteScreen)mc().screen).data();
                    for(var name:List.of("leg_press_sled","triceps_stack","triceps_front_cable","triceps_stack_cable","triceps_clip","triceps_rope_left","triceps_rope_right")){
                        var id=net.minecraft.client.resources.model.ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("realmfitness","block/"+name));var model=mc().getModelManager().getModel(id);check(model!=mc().getModelManager().getMissingModel()&&!model.getParticleIcon().contents().name().getPath().contains("missing"),"Moving model failed to bake: "+name);
                    }
                    CHECKS.add("Leg press sled and all six moving triceps meshes baked with valid textures");mc().setScreen(new Gallery(-1));stage++;next=now+2500;}
                case 4->{
                    if(galleryIndex<COUNT){if(galleryIndex<0)screenshot("fitness-eight-machines.png");else screenshot("fitness-model-"+Exercise.values()[galleryIndex].id()+".png");if(++galleryIndex<COUNT)mc().setScreen(new Gallery(galleryIndex));else worldModel(0);next=now+1300;}
                    else{if(!ready)return;screenshot("fitness-world-"+Exercise.values()[galleryIndex-COUNT].id()+".png");if(++galleryIndex<COUNT*2){worldModel(galleryIndex-COUNT);next=now+1500;}else{mc().options.hideGui=false;server(()->player().setGameMode(GameType.SURVIVAL));CHECKS.add("Eight original detailed models captured individually as items and as complete expanded structures in the actual integrated world");mc().setScreen(new AthleteScreen(profile));stage++;next=now+1500;}}
                }
                case 5->{screenshot("fitness-athlete-profile.png");check(click("tab.catalog"),"Catalog tab missing");stage++;next=now+800;}
                case 6->{screenshot("fitness-machine-catalog.png");check(click("tab.routines"),"Routine tab missing");stage++;next=now+700;}
                case 7->{screenshot("fitness-routines.png");check(click("new_plan"),"Visual plan editor missing");stage++;next=now+500;}
                case 8->{check(mc().screen instanceof RoutineEditorScreen,"Editor failed to open");((EditBox)field(mc().screen,"nameField")).setValue("QA daily eight");screenshot("fitness-plan-editor.png");check(click("save"),"Save routine widget missing");stage++;next=now+800;}
                case 9->{if(!(mc().screen instanceof AthleteScreen screen))return;check(screen.data().getAsJsonArray("routines").asList().stream().anyMatch(e->FitnessClient.str(e.getAsJsonObject(),"name").equals("QA daily eight")),"Routine was not saved over the real channel");
                    // Use an explicit eight-exercise plan; completed records still come exclusively from real game inputs.
                    planId=UUID.randomUUID();List<Step> steps=Arrays.stream(Exercise.values()).map(e->new Step(e,1,8,e.minimumLoad(),5)).toList();FitnessNetwork.request("routine",new Routine(planId,"QA daily eight",5,steps));stage++;next=now+600;}
                case 10->{FitnessNetwork.request("begin",Map.of("id",planId.toString()));stage++;next=now+600;}
                case 11->{mc().setScreen(null);joinPhase=0;mountedSeen=false;server(FitnessVisualQA::exercise);stage++;}
                case 12->{
                    if(!ready)return;
                    if(exercise==0&&joinPhase==0){mc().player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc().player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));useMachine();mc().player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc().player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));joinPhase++;next=now+700;return;}
                    if(exercise==0&&joinPhase==1){server(()->{var d=FitnessData.get(player().getServer());var a=d.ledger.athletes.get(playerId);check(!player().isPassenger()&&d.ledger.selectedLoad(a,Exercise.BENCH)==25,"Sneak-right-click did not increase load without mounting");check(a.workout.load(a.workout.next())==25&&a.workout.next().load()==20,"Equipment load changed the saved plan instead of its active override");a.experience.put(Exercise.BENCH,90);a.preferredLoads.put(Exercise.BENCH,20);a.workout.loadOverrides.clear();d.persist(player().getServer());CHECKS.add("Actual sneak-right-click changes equipment load without mounting or opening a menu; original routine is preserved");});joinPhase++;return;}
                    useMachine();shot=false;held=0;lastBenchTap=lastRunTap=-100;stage++;
                }
                case 13->{if(TrainingHud.active()!=null){if(mc().player.getVehicle() instanceof GymMount)mountedSeen=true;return;}if(!mountedSeen)return;int index=exercise;server(()->{check(!player().isPassenger(),"Finished set left player mounted");
                    var a=FitnessData.get(player().getServer()).ledger.athletes.get(playerId);check(a.results.size()==index+1,"Set result missing");var r=a.results.getLast();check(r.valid()>=5&&r.accuracy()>=50,"Real "+r.exercise()+" controls did not qualify: "+r.valid()+"/8 at "+r.accuracy()+"%");check(a.workout.completed==index+1,"Active routine did not advance exactly once");CHECKS.add("Actual "+r.exercise()+" minigame: "+r.valid()+"/8 valid at "+r.accuracy()+"%, saved through real player inputs");
                });stage++;}
                case 14->{if(!ready)return;if(++exercise<COUNT){stage=11;return;}server(()->{var p=player();var a=FitnessData.get(p.getServer()).ledger.athletes.get(playerId);check(a.dailyComplete&&a.validToday==COUNT&&a.completedDays.size()==1,"Daily completion was not recorded once");for(var e:Exercise.values())check(FitnessLedger.level(a.experience.get(e.attribute()))>=1,"Permanent level missing for "+e);check(p.getAttribute(Attributes.ATTACK_DAMAGE).getModifiers().stream().anyMatch(m->m.id().getNamespace().equals("realmfitness")),"Permanent damage modifier missing");FitnessService.open(p,null,false);CHECKS.add("Eight real sets complete a custom routine and exactly one daily bonus; permanent modifiers applied");});stage++;next=now+1000;}
                case 15->{if(!ready||!(mc().screen instanceof AthleteScreen))return;check(click("tab.profile"),"Profile tab missing");stage++;next=now+900;}
                case 16->{screenshot("fitness-earned-attributes.png");check(click("tab.history"),"Journal tab missing");stage++;next=now+800;}
                case 17->{screenshot("fitness-real-history.png");check(click("tab.records"),"Record view missing");stage++;next=now+700;}
                case 18->{screenshot("fitness-personal-records.png");check(click("tab.progress"),"Progress view missing");stage++;next=now+700;}
                case 19->{screenshot("fitness-training-calendar.png");mc().getWindow().setWindowed(640,480);mc().options.guiScale().set(2);mc().resizeDisplay();check(click("tab.profile"),"Compact profile tab missing");stage++;next=now+1000;}
                case 20->{screenshot("fitness-compact-profile.png");for(var c:mc().screen.children())if(c instanceof AbstractWidget b&&b.visible)check(b.getX()>=0&&b.getX()+b.getWidth()<=mc().getWindow().getGuiScaledWidth()&&b.getY()>=0&&b.getY()+b.getHeight()<=mc().getWindow().getGuiScaledHeight(),"Compact widget outside screen");mc().getWindow().setWindowed(1600,900);mc().resizeDisplay();mc().getLanguageManager().setSelected("pt_pt");mc().options.languageCode="pt_pt";mc().reloadResourcePacks();stage++;next=now+1500;}
                case 21->{mc().screen.resize(mc(),mc().getWindow().getGuiScaledWidth(),mc().getWindow().getGuiScaledHeight());stage++;next=now+800;}
                case 22->{screenshot("fitness-profile-portuguese.png");server(()->{player().teleportTo(player().serverLevel(),.5,161,3.5,0,0);FitnessService.open(player(),BOARD,true);});stage++;next=now+700;}
                case 23->{if(!ready||!(mc().screen instanceof AthleteScreen))return;screenshot("fitness-leaderboard-menu.png");CHECKS.add("Real RPG profile, editable plan, history, records, calendar, compact controls and Portuguese presentation captured");server(()->{var b=(RecordBoardEntity)player().level().getBlockEntity(BOARD);FitnessService.refreshBoards(player().getServer());check(!b.display.equals("[]"),"World leaderboard did not receive real records");player().teleportTo(player().serverLevel(),.5,161,2.5,0,23);});stage++;next=now+600;}
                case 24->{if(!ready)return;mc().setScreen(null);mc().options.setCameraType(CameraType.FIRST_PERSON);stage++;next=now+1200;}
                case 25->{screenshot("fitness-live-record-board.png");stage=30;}
                case 26->{if(!ready)return;mc().level.disconnect();mc().disconnect();mc().setScreen(new TitleScreen());stage++;}
                case 27->{if(mc().getSingleplayerServer()!=null||mc().level!=null)return;mc().createWorldOpenFlows().openWorld(folder,()->mc().setScreen(new TitleScreen()));stage++;}
                case 28->{if(mc().player==null||mc().getSingleplayerServer()==null)return;server(()->{var p=player();var a=FitnessData.get(p.getServer()).ledger.athletes.get(playerId);check(a.results.size()==COUNT&&a.dailyComplete&&a.workout.completed==COUNT,"World reopen lost or duplicated workout records");check(!p.isPassenger()&&p.serverLevel().getEntitiesOfClass(GymMount.class,new net.minecraft.world.phys.AABB(-30,158,-8,35,168,12)).isEmpty(),"World close/reopen left a passenger or temporary gym vehicle");check(!p.level().getBlockState(station(2)).getValue(GymBlock.WORKING),"World close/reopen left equipment stuck in use");for(var e:Exercise.values())check(FitnessLedger.level(a.experience.get(e.attribute()))>=1,"World reopen lost permanent levels");FitnessService.apply(p);double first=p.getAttributeValue(Attributes.ATTACK_DAMAGE);FitnessService.apply(p);check(first==p.getAttributeValue(Attributes.ATTACK_DAMAGE),"World reopen stacked modifiers");CHECKS.add("Actual world close/reopen preserves levels, routine completion, eight results and daily reward without modifier stacking");});stage++;}
                case 29->{if(ready){check(TrainingHud.interpolatedFrames>0,"HUD never advanced between authoritative snapshots");CHECKS.add("HUD rendered interpolated motion between server snapshots; scoring remains authoritative");complete(true,"Eight actual minigames, RPG menus, custom routine, persistent progression and world leaderboard passed");}}
                case 30->{exercise=cancelMode==0?Exercise.LEG_PRESS.ordinal():cancelMode==1?Exercise.TRICEPS.ordinal():2;mc().setScreen(null);server(()->{var p=player();var a=FitnessData.get(p.getServer()).ledger.athletes.get(playerId);a.restUntil=0;var pos=station(exercise);p.teleportTo(p.serverLevel(),pos.getX()+.5,161,-1.8,0,0);});stage=31;}
                case 31->{if(!ready)return;useMachine();stage=32;next=now+500;}
                case 32->{if(TrainingHud.active()==null||!(mc().player.getVehicle() instanceof GymMount))return;if(cancelMode==2){CHECKS.add("World close while actually mounted is exercised before reopen");stage=26;return;}if(cancelMode==0)TrainingHud.active().keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT,0,0);else server(()->player().level().destroyBlock(station(exercise),true));stage=33;next=now+600;}
                case 33->{if(TrainingHud.active()!=null)return;server(()->{check(!player().isPassenger(),"Cancelled set left player mounted");check(FitnessData.get(player().getServer()).ledger.athletes.get(playerId).results.size()==COUNT,"Cancelled set awarded a result");if(cancelMode==0)check(!player().level().getBlockState(station(exercise)).getValue(GymBlock.WORKING),"Cancelled set left animated machine state");});stage=34;}
                case 34->{if(!ready)return;CHECKS.add(cancelMode==0?"Actual SHIFT input dismounts, restores equipment and creates no reward":"Breaking occupied equipment dismounts the actual player and creates no reward");cancelMode++;stage=30;}
                default->throw new IllegalStateException("Unknown QA stage");
            }
        }catch(Throwable e){e.printStackTrace();complete(false,e.toString());}
    }
    private static final class Gallery extends Screen {
        private final int selected;
        Gallery(int selected){super(Component.literal("Realm Gym"));this.selected=selected;}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float p){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fillGradient(0,0,width,height,0xFF17121F,0xFF0E111B);g.drawCenteredString(font,"REALM GYM",width/2,20,FitnessTheme.ACCENT);
            int cw=selected<0?(width-48)/3:width-48,ch=selected<0?(height-70)/((COUNT+2)/3):height-70;
            for(var e:Exercise.values()){
                if(selected>=0&&e.ordinal()!=selected)continue;int bx=selected<0?24+e.ordinal()%3*cw:24,by=selected<0?45+e.ordinal()/3*ch:45;FitnessTheme.frame(g,bx,by,cw-8,ch-10,0xFF241D2F);
                var item=new ItemStack(FitnessMod.MACHINES.get(e).get());var model=mc().getItemRenderer().getModel(item,null,null,0);check(!model.getParticleIcon().contents().name().getPath().contains("missing"),"Missing machine texture");
                g.pose().pushPose();int scale=selected<0?5:17;g.pose().translate(bx+cw/2-8*scale,by+ch/2-8*scale-10,0);g.pose().scale(scale,scale,scale);g.renderItem(item,0,0);g.pose().popPose();
                g.drawCenteredString(font,FitnessTheme.exercise(e),bx+cw/2-4,by+ch-29,FitnessTheme.INK);
            }
            g.drawCenteredString(font,"Minecraft 1.21.1 · NeoForge · actual models",width/2,height-16,FitnessTheme.MUTED);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
