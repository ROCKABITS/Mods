package pt.realmfitness.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import pt.realmfitness.FitnessNetwork;
import pt.realmfitness.domain.*;
import static pt.realmfitness.client.FitnessTheme.*;
import static pt.realmfitness.client.FitnessClient.*;

/** World HUD and inputs. There is no Screen, cursor or intermediate set menu. */
public final class TrainingHud {
    private static TrainingHud current;
    public static long renderedFrames,interpolatedFrames,snapshots;
    private int lastRenderedServerTick=-1;
    private double lastRenderedClock;
    private static String toast="";
    private static long toastUntil;
    private final String id;
    private final boolean spotting;
    private final Exercise exercise;
    private final Minigame pattern;
    private JsonObject state;
    private final TrainingPresentation presentation=new TrainingPresentation();
    private int countdown,keys,sequence,pendingPunch=-1,lastFeedback;
    private double localHitMarker;
    private long received;
    private final CameraType previousCamera;
    private TrainingHud(JsonObject d){
        spotting=d.has("spotting")&&d.get("spotting").getAsBoolean();
        id=str(d,"id");exercise=Exercise.valueOf(str(d,"exercise"));pattern=new Minigame(UUID.fromString(id),exercise,num(d,"load"),num(d,"repetitions"),time(d,"seed"),d.get("fatigue").getAsDouble(),num(d,"trainingLevel"));presentation.configure(pattern);
        var mc=Minecraft.getInstance();previousCamera=mc.options.getCameraType();mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if(mc.player!=null){mc.player.setYRot(d.has("yaw")?d.get("yaw").getAsFloat()+(spotting?0:exercise==Exercise.BENCH?100:28):mc.player.getYRot());mc.player.setXRot(16);}
        accept(d);
    }
    public static TrainingHud active(){return current;}
    public static void start(JsonObject d){clear();current=new TrainingHud(d);toastUntil=0;}
    public static void clear(){toastUntil=0;if(current!=null){Minecraft.getInstance().options.setCameraType(current.previousCamera);current=null;}resetMovement();}
    public static void finish(JsonObject d){
        clear();
        int assisted=num(d,"assisted");
        if(d.has("spotting")&&d.get("spotting").getAsBoolean())toast=text("spotter.summary",assisted);
        else{
            toast=text("set_result",num(d,"valid"),num(d,"repetitions"),num(d,"accuracy"),num(d,"xp"));
            if(num(d,"bestStreak")>1)toast+=" · "+text("game.best_streak",num(d,"bestStreak"));
            if(assisted>0)toast+=" · "+text("spotter.count",assisted);
        }
        toastUntil=System.currentTimeMillis()+4500;
    }
    private static void resetMovement(){var mc=Minecraft.getInstance();for(var key:new KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift,mc.options.keyAttack,mc.options.keyUse})key.setDown(false);}
    public void accept(JsonObject d){
        if(!id.equals(str(d,"id")))return;snapshots++;countdown=num(d,"countdown");state=d.getAsJsonObject("state");received=System.nanoTime();presentation.accept(num(state,"tick"),state.get("effort").getAsDouble(),state.get("balance").getAsDouble(),state.get("grip").getAsDouble(),received);presentation.acknowledge(num(state,"sequence"),str(state,"stage"));
        presentation.pressure(state.get("leftPressure").getAsDouble(),state.get("rightPressure").getAsDouble());
        if(pendingPunch>=0&&num(state,"sequence")>=pendingPunch)pendingPunch=-1;
        int feedback=num(state,"feedbackId");if(feedback!=lastFeedback&&num(state,"feedbackTicks")>0){lastFeedback=feedback;String grade=str(state,"feedback");var player=Minecraft.getInstance().player;if(player!=null&&(grade.equals("perfect")||grade.equals("good")))player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,.16f,grade.equals("perfect")?1.45f:1.05f);}
    }
    public String session(){return id;}
    public void cancel(){if(Minecraft.getInstance().getConnection()!=null)FitnessNetwork.request("cancel",Map.of());clear();}
    private void update(int bit,boolean down){if(bit==0)return;int next=down?keys|bit:keys&~bit;if(next!=keys){keys=next;int sent=sequence++;if(!spotting&&down&&countdown==0){if(pattern.exercise!=Exercise.RUN||bit==num(state,"nextFoot"))presentation.press(sent,bit);if(exercise==Exercise.BOX&&(bit==Minigame.LEFT||bit==Minigame.RIGHT)&&!state.get("locked").getAsBoolean()&&pendingPunch<0){pendingPunch=sent;localHitMarker=pattern.boxMarkerAt(presentation.clock());}}FitnessNetwork.request("input",Map.of("id",id,"sequence",sent,"keys",keys));}}
    private static int key(int code){return switch(code){case GLFW.GLFW_KEY_SPACE->Minigame.SPACE;case GLFW.GLFW_KEY_A->Minigame.A;case GLFW.GLFW_KEY_D->Minigame.D;case GLFW.GLFW_KEY_W->Minigame.W;case GLFW.GLFW_KEY_S->Minigame.S;default->0;};}
    public boolean keyPressed(int code,int scan,int mods){if(code==GLFW.GLFW_KEY_LEFT_SHIFT||code==GLFW.GLFW_KEY_RIGHT_SHIFT||code==GLFW.GLFW_KEY_ESCAPE){cancel();return true;}update(key(code),true);return key(code)!=0;}
    public boolean keyReleased(int code,int scan,int mods){update(key(code),false);return key(code)!=0;}
    public boolean mouseClicked(double x,double y,int button){if(button<2)update(button==0?Minigame.LEFT:Minigame.RIGHT,true);return true;}
    public boolean mouseReleased(double x,double y,int button){if(button<2)update(button==0?Minigame.LEFT:Minigame.RIGHT,false);return true;}
    public void render(GuiGraphics g){
        if(spotting){renderSpotter(g);return;}
        int serverTick=num(state,"tick");var smooth=presentation.frame(exercise,keys,countdown>0,System.nanoTime());double tick=smooth.tick();renderedFrames++;if(lastRenderedServerTick==serverTick&&Math.abs(tick-lastRenderedClock)>.00001)interpolatedFrames++;lastRenderedServerTick=serverTick;lastRenderedClock=tick;
        double marker=state.get("locked").getAsBoolean()?state.get("marker").getAsDouble():pendingPunch>=0?localHitMarker:pattern.boxMarkerAt(tick);MinigameHudRenderer.draw(g,pattern,state,smooth,countdown,marker);
        if(flag("spotterPresent")){
            String cue=flag("helpRequested")?"spotter.requested":flag("canRequestHelp")?"spotter.call":"spotter.ready";
            spotterLabel(g,text(cue),flag("canRequestHelp")?GREEN:INK,123);
        }
    }
    private boolean flag(String name){return state.has(name)&&state.get(name).getAsBoolean();}
    private void renderSpotter(GuiGraphics graphics){
        boolean called=flag("helpRequested");
        spotterLabel(graphics,text(called?"spotter.catch":"spotter.wait"),called?GREEN:INK,91);
        spotterLabel(graphics,text("spotter.count",num(state,"assisted"))+" · "+text("spotter.leave"),MUTED,78);
    }
    private void spotterLabel(GuiGraphics graphics,String message,int color,int bottom){
        var mc=Minecraft.getInstance();
        int center=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()-bottom;
        int half=(mc.font.width(message)+1)/2;
        graphics.fill(center-half-6,y-3,center+half+6,y+10,0xCC211B2A);
        graphics.drawCenteredString(mc.font,message,center,y,color);
    }
    public static void draw(GuiGraphics graphics){var mc=Minecraft.getInstance();if(mc.options.hideGui)return;if(current!=null)current.render(graphics);else if(System.currentTimeMillis()<toastUntil)graphics.drawCenteredString(mc.font,toast,mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()-76,GREEN);}
}
