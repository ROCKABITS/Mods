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
    private static final Path OUT=Path.of(System.getProperty("fitness.qaOutput"));
    private static final long START=System.currentTimeMillis();
    private static final List<String> CHECKS=new ArrayList<>(),CAPTURES=new ArrayList<>();
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile UUID playerId,planId;
    private static volatile String folder;
    private static int stage,exercise,held,lastStage=-1;
    private static long next,stageSince;
    private static boolean done,shot;
    private static JsonObject profile;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static ServerPlayer player(){return mc().getSingleplayerServer().getPlayerList().getPlayer(playerId);}
    private static BlockPos station(int i){return new BlockPos(i*3-8,161,0);}
    private static final BlockPos BOARD=new BlockPos(0,161,5);
    private static void server(Runnable task){ready=false;mc().getSingleplayerServer().execute(()->{try{task.run();ready=true;}catch(Throwable e){failure=e;}});}
    private static boolean click(String key){if(mc().screen==null)return false;String label=key.startsWith("selectWorld.")||key.startsWith("gui.")?Component.translatable(key).getString():FitnessTheme.text(key);for(var c:List.copyOf(mc().screen.children()))if(c instanceof AbstractButton b&&b.active&&b.visible&&b.getMessage().getString().equals(label)){b.onPress();return true;}return false;}
    private static void screenshot(String name)throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}if(!CAPTURES.contains(name))CAPTURES.add(name);}
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void prepare(){
        var p=player();var server=p.getServer();p.setGameMode(GameType.SURVIVAL);server.setDifficulty(Difficulty.PEACEFUL,true);
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);server.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,server);
        for(int x=-13;x<=12;x++)for(int z=-5;z<=9;z++){for(int y=161;y<=164;y++)p.level().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());p.level().setBlockAndUpdate(new BlockPos(x,160,z),Blocks.POLISHED_DEEPSLATE.defaultBlockState());}
        for(var e:Exercise.values()){var pos=station(e.ordinal());var state=FitnessMod.MACHINES.get(e).get().defaultBlockState();p.level().setBlock(pos,state,2);p.level().setBlock(pos.above(),state.setValue(GymBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);}
        p.level().setBlockAndUpdate(BOARD,FitnessMod.BOARD.get().defaultBlockState());
        p.teleportTo(p.serverLevel(),.5,161,-3.5,0,0);p.getInventory().clearContent();
        var d=FitnessData.get(server);d.ledger=new FitnessLedger();var a=d.ledger.athlete(p.getUUID(),p.getGameProfile().getName(),System.currentTimeMillis());
        // Controlled baseline exposes a level-up from each genuinely played series.
        for(var e:Exercise.values())a.experience.put(e,90);
        d.persist(server);FitnessService.apply(p);FitnessService.open(p,null,false);
        folder=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
    }
    private static void exercise(){
        var p=player();var a=FitnessData.get(p.getServer()).ledger.athletes.get(p.getUUID());a.restUntil=0;
        var pos=station(exercise);p.teleportTo(p.serverLevel(),pos.getX()+.5,161,-1.8,0,0);FitnessService.open(p,pos,false);
    }
    private static void feed(TrainingScreen screen)throws Exception{
        var state=(JsonObject)field(screen,"state");var pattern=(Minigame)field(screen,"pattern");long received=(long)field(screen,"received");
        int t=FitnessClient.num(state,"tick")+(int)Math.min(4,(System.nanoTime()-received)/50_000_000)+1;
        if((int)field(screen,"countdown")>0){release(screen);return;}
        int bits;
        if(pattern.exercise==Exercise.RUN||pattern.exercise==Exercise.BOX||pattern.exercise==Exercise.ROW){int length=pattern.pulseLength(),pulse=(t+length/2)/length;bits=Math.abs(t-pulse*length)<=2?pattern.expectedKey(pulse):0;}
        else{double target=.5-.34*Math.cos((t%48)*Math.PI*2/48),effort=state.get("effort").getAsDouble();int direction=pattern.exercise==Exercise.SQUAT?((held&Minigame.W)!=0?1:0)-((held&Minigame.S)!=0?1:0):((held&Minigame.SPACE)!=0?1:-1);effort=Math.clamp(effort+direction*.042*Math.min(3,(System.nanoTime()-received)/50_000_000.0+1),0,1);bits=pattern.exercise==Exercise.SQUAT?(effort<target?Minigame.W:Minigame.S):(effort<target?Minigame.SPACE:0);if(pattern.exercise==Exercise.PULL)bits|=state.get("balance").getAsDouble()>.5?Minigame.A:Minigame.D;}
        for(int bit:new int[]{1,2,4,8,16,32,64})if((bits&bit)!=(held&bit)){
            boolean down=(bits&bit)!=0;
            if(bit==32||bit==64){if(down)screen.mouseClicked(0,mc().getWindow().getGuiScaledHeight()/2.0,bit==32?0:1);else screen.mouseReleased(0,mc().getWindow().getGuiScaledHeight()/2.0,bit==32?0:1);}
            else{int code=switch(bit){case 1->GLFW.GLFW_KEY_SPACE;case 2->GLFW.GLFW_KEY_A;case 4->GLFW.GLFW_KEY_D;case 8->GLFW.GLFW_KEY_W;default->GLFW.GLFW_KEY_S;};if(down)screen.keyPressed(code,0,0);else screen.keyReleased(code,0,0);}
        }
        held=bits;
    }
    private static void release(TrainingScreen screen){for(int code:new int[]{GLFW.GLFW_KEY_SPACE,GLFW.GLFW_KEY_A,GLFW.GLFW_KEY_D,GLFW.GLFW_KEY_W,GLFW.GLFW_KEY_S})screen.keyReleased(code,0,0);screen.mouseReleased(0,0,0);screen.mouseReleased(0,0,1);held=0;}
    private static void complete(boolean success,String message){done=true;try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",success,"stage",stage,"message",message,"checks",CHECKS,"screenshots",CAPTURES,"actualMinecraftClient",true,"actualIntegratedServer",true,"controlledBaselineXp",90,"syntheticSetResults",false)));}catch(Exception e){e.printStackTrace();}System.out.println(success?"FITNESS_CLIENT_PASSED":"FITNESS_CLIENT_FAILED "+message);mc().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(done)return;long now=System.currentTimeMillis();
        try{
            if(failure!=null)throw new IllegalStateException("Integrated server check failed",failure);
            check(now-START<900000,"Fitness QA timed out at stage "+stage);
            if(mc().screen instanceof TrainingScreen s){feed(s);if(!shot&&FitnessClient.num((JsonObject)field(s,"state"),"tick")>90){screenshot("fitness-minigame-"+Exercise.values()[exercise].id()+".png");shot=true;}}
            if(now<next||mc().getOverlay()!=null)return;next=now+180;
            if(stage!=lastStage){lastStage=stage;stageSince=now;System.out.println("FITNESS_QA_STAGE "+stage);}else check(now-stageSince<150000,"Fitness stage stalled "+stage);
            switch(stage){
                case 0->{if(mc().screen instanceof TitleScreen){mc().getWindow().setWindowed(1600,900);mc().options.pauseOnLostFocus=false;mc().options.renderDistance().set(3);mc().options.simulationDistance().set(5);mc().options.guiScale().set(2);mc().resizeDisplay();mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);CreateWorldScreen.openFresh(mc(),mc().screen);stage++;}else if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility")){for(var child:List.copyOf(mc().screen.children()))if(child instanceof AbstractButton b&&b.getMessage().getString().equals(Component.translatable("gui.continue").getString()))b.onPress();}}
                case 1->{if(mc().screen instanceof CreateWorldScreen world){world.getUiState().setName("Realm Fitness client QA");world.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);world.getUiState().setAllowCommands(true);check(click("selectWorld.create"),"Create world button missing");stage++;}}
                case 2->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){playerId=mc().player.getUUID();server(FitnessVisualQA::prepare);stage++;}}
                case 3->{if(!ready||!(mc().screen instanceof AthleteScreen))return;profile=((AthleteScreen)mc().screen).data();mc().setScreen(new Gallery());stage++;next=now+2500;}
                case 4->{screenshot("fitness-six-machines.png");mc().setScreen(new AthleteScreen(profile));stage++;next=now+1500;}
                case 5->{screenshot("fitness-athlete-profile.png");check(click("tab.catalog"),"Catalog tab missing");stage++;next=now+800;}
                case 6->{screenshot("fitness-machine-catalog.png");check(click("tab.routines"),"Routine tab missing");stage++;next=now+700;}
                case 7->{screenshot("fitness-routines.png");check(click("new_plan"),"Visual plan editor missing");stage++;next=now+500;}
                case 8->{check(mc().screen instanceof RoutineEditorScreen,"Editor failed to open");((EditBox)field(mc().screen,"nameField")).setValue("QA daily six");screenshot("fitness-plan-editor.png");check(click("save"),"Save routine widget missing");stage++;next=now+800;}
                case 9->{if(!(mc().screen instanceof AthleteScreen screen))return;check(screen.data().getAsJsonArray("routines").asList().stream().anyMatch(e->FitnessClient.str(e.getAsJsonObject(),"name").equals("QA daily six")),"Routine was not saved over the real channel");
                    // Use an explicit six-exercise plan; completed records still come exclusively from real game inputs.
                    planId=UUID.randomUUID();List<Step> steps=Arrays.stream(Exercise.values()).map(e->new Step(e,1,8,e.minimumLoad(),5)).toList();FitnessNetwork.request("routine",new Routine(planId,"QA daily six",5,steps));stage++;next=now+600;}
                case 10->{FitnessNetwork.request("begin",Map.of("id",planId.toString()));stage++;next=now+600;}
                case 11->{server(FitnessVisualQA::exercise);stage++;}
                case 12->{if(!ready||!(mc().screen instanceof AthleteScreen screen)||!FitnessClient.str(screen.data(),"machine").equals(Exercise.values()[exercise].name()))return;check(click("start_series"),"Start series widget missing");shot=false;held=0;stage++;}
                case 13->{if(mc().screen instanceof TrainingScreen)return;if(!(mc().screen instanceof AthleteScreen))return;int index=exercise;server(()->{
                    var a=FitnessData.get(player().getServer()).ledger.athletes.get(playerId);check(a.results.size()==index+1,"Set result missing");var r=a.results.getLast();check(r.valid()>=5&&r.accuracy()>=50,"Real "+r.exercise()+" controls did not qualify: "+r.valid()+"/8 at "+r.accuracy()+"%");check(a.workout.completed==index+1,"Active routine did not advance exactly once");CHECKS.add("Actual "+r.exercise()+" minigame: "+r.valid()+"/8 valid at "+r.accuracy()+"%, saved through real player inputs");
                });stage++;}
                case 14->{if(!ready)return;if(++exercise<6){stage=11;return;}server(()->{var p=player();var a=FitnessData.get(p.getServer()).ledger.athletes.get(playerId);check(a.dailyComplete&&a.validToday==6&&a.completedDays.size()==1,"Daily completion was not recorded once");for(var e:Exercise.values())check(FitnessLedger.level(a.experience.get(e))>=1,"Permanent level missing for "+e);check(p.getAttribute(Attributes.ATTACK_DAMAGE).getModifiers().stream().anyMatch(m->m.id().getNamespace().equals("realmfitness")),"Permanent damage modifier missing");FitnessService.open(p,null,false);CHECKS.add("Six real sets complete a custom routine and exactly one daily bonus; permanent modifiers applied");});stage++;next=now+1000;}
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
                case 25->{screenshot("fitness-live-record-board.png");server(()->player().getServer().saveEverything(true,false,true));stage++;}
                case 26->{if(!ready)return;mc().level.disconnect();mc().disconnect();mc().setScreen(new TitleScreen());stage++;}
                case 27->{if(mc().getSingleplayerServer()!=null||mc().level!=null)return;mc().createWorldOpenFlows().openWorld(folder,()->mc().setScreen(new TitleScreen()));stage++;}
                case 28->{if(mc().player==null||mc().getSingleplayerServer()==null)return;server(()->{var p=player();var a=FitnessData.get(p.getServer()).ledger.athletes.get(playerId);check(a.results.size()==6&&a.dailyComplete&&a.workout.completed==6,"World reopen lost or duplicated workout records");for(var e:Exercise.values())check(FitnessLedger.level(a.experience.get(e))>=1,"World reopen lost permanent levels");FitnessService.apply(p);double first=p.getAttributeValue(Attributes.ATTACK_DAMAGE);FitnessService.apply(p);check(first==p.getAttributeValue(Attributes.ATTACK_DAMAGE),"World reopen stacked modifiers");CHECKS.add("Actual world close/reopen preserves levels, routine completion, six results and daily reward without modifier stacking");});stage++;}
                case 29->{if(ready)complete(true,"Six actual minigames, RPG menus, custom routine, persistent progression and world leaderboard passed");}
                default->throw new IllegalStateException("Unknown QA stage");
            }
        }catch(Throwable e){e.printStackTrace();complete(false,e.toString());}
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Realm Fitness"));}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float p){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fillGradient(0,0,width,height,0xFF17121F,0xFF0E111B);g.drawCenteredString(font,"REALM FITNESS",width/2,20,FitnessTheme.ACCENT);
            int cw=(width-48)/3,ch=(height-70)/2;
            for(var e:Exercise.values()){
                int bx=24+e.ordinal()%3*cw,by=45+e.ordinal()/3*ch;FitnessTheme.frame(g,bx,by,cw-8,ch-10,0xFF241D2F);
                var item=new ItemStack(FitnessMod.MACHINES.get(e).get());var model=mc().getItemRenderer().getModel(item,null,null,0);check(!model.getParticleIcon().contents().name().getPath().contains("missing"),"Missing machine texture");
                g.pose().pushPose();g.pose().translate(bx+cw/2-40,by+ch/2-45,0);g.pose().scale(5,5,5);g.renderItem(item,0,0);g.pose().popPose();
                g.drawCenteredString(font,FitnessTheme.exercise(e),bx+cw/2-4,by+ch-29,FitnessTheme.INK);
            }
            g.drawCenteredString(font,"Minecraft 1.21.1 · NeoForge · actual models",width/2,height-16,FitnessTheme.MUTED);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
