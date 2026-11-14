package pt.realmfitness.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import pt.realmfitness.FitnessNetwork;
import pt.realmfitness.domain.*;
import static pt.realmfitness.client.FitnessTheme.*;
import static pt.realmfitness.client.FitnessClient.*;

/** World HUD and inputs. There is no Screen, cursor or intermediate set menu. */
@Mod.EventBusSubscriber(modid="realmfitness",value=Dist.CLIENT)
public final class TrainingHud {
    private static TrainingHud current;
    public static long renderedFrames,interpolatedFrames,snapshots;
    private int lastRenderedServerTick=-1;
    private double lastRenderedClock;
    private static String toast="";
    private static long toastUntil;
    private final String id;
    private final Exercise exercise;
    private final Minigame pattern;
    private JsonObject state;
    private final TrainingPresentation presentation=new TrainingPresentation();
    private int countdown,keys,sequence,pendingPunch=-1,lastFeedback;
    private double localHitMarker;
    private long received;
    private final CameraType previousCamera;
    private TrainingHud(JsonObject d){
        id=str(d,"id");exercise=Exercise.valueOf(str(d,"exercise"));pattern=new Minigame(UUID.fromString(id),exercise,num(d,"load"),num(d,"repetitions"),time(d,"seed"),d.get("fatigue").getAsDouble(),num(d,"trainingLevel"));presentation.configure(pattern);
        var mc=Minecraft.getInstance();previousCamera=mc.options.getCameraType();mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if(mc.player!=null){mc.player.setYRot(d.has("yaw")?d.get("yaw").getAsFloat()+(exercise==Exercise.BENCH?100:28):mc.player.getYRot());mc.player.setXRot(16);}
        accept(d);
    }
    public static TrainingHud active(){return current;}
    public static void start(JsonObject d){clear();current=new TrainingHud(d);toastUntil=0;}
    public static void clear(){toastUntil=0;if(current!=null){Minecraft.getInstance().options.setCameraType(current.previousCamera);current=null;}resetMovement();}
    public static void finish(JsonObject d){clear();toast=text("set_result",num(d,"valid"),num(d,"repetitions"),num(d,"accuracy"),num(d,"xp"))+(num(d,"bestStreak")>1?" · "+text("game.best_streak",num(d,"bestStreak")):"");toastUntil=System.currentTimeMillis()+4500;}
    private static void resetMovement(){var mc=Minecraft.getInstance();for(var key:new KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift,mc.options.keyAttack,mc.options.keyUse})key.setDown(false);}
    public void accept(JsonObject d){
        if(!id.equals(str(d,"id")))return;snapshots++;countdown=num(d,"countdown");state=d.getAsJsonObject("state");received=System.nanoTime();presentation.accept(num(state,"tick"),state.get("effort").getAsDouble(),state.get("balance").getAsDouble(),state.get("grip").getAsDouble(),received);presentation.acknowledge(num(state,"sequence"),str(state,"stage"));
        if(pendingPunch>=0&&num(state,"sequence")>=pendingPunch)pendingPunch=-1;
        int feedback=num(state,"feedbackId");if(feedback!=lastFeedback&&num(state,"feedbackTicks")>0){lastFeedback=feedback;String grade=str(state,"feedback");var player=Minecraft.getInstance().player;if(player!=null&&(grade.equals("perfect")||grade.equals("good")))player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,.16f,grade.equals("perfect")?1.45f:1.05f);}
    }
    public String session(){return id;}
    public void cancel(){if(Minecraft.getInstance().getConnection()!=null)FitnessNetwork.request("cancel",Map.of());clear();}
    private void update(int bit,boolean down){if(bit==0)return;int next=down?keys|bit:keys&~bit;if(next!=keys){keys=next;int sent=sequence++;if(down&&countdown==0){if(pattern.exercise!=Exercise.RUN||bit==num(state,"nextFoot"))presentation.press(sent,bit);if(exercise==Exercise.BOX&&(bit==Minigame.LEFT||bit==Minigame.RIGHT)&&!state.get("locked").getAsBoolean()&&pendingPunch<0){pendingPunch=sent;localHitMarker=pattern.boxMarkerAt(presentation.clock());}}FitnessNetwork.request("input",Map.of("id",id,"sequence",sent,"keys",keys));}}
    private static int key(int code){return switch(code){case GLFW.GLFW_KEY_SPACE->Minigame.SPACE;case GLFW.GLFW_KEY_A->Minigame.A;case GLFW.GLFW_KEY_D->Minigame.D;case GLFW.GLFW_KEY_W->Minigame.W;case GLFW.GLFW_KEY_S->Minigame.S;default->0;};}
    public boolean keyPressed(int code,int scan,int mods){if(code==GLFW.GLFW_KEY_LEFT_SHIFT||code==GLFW.GLFW_KEY_RIGHT_SHIFT||code==GLFW.GLFW_KEY_ESCAPE){cancel();return true;}update(key(code),true);return key(code)!=0;}
    public boolean keyReleased(int code,int scan,int mods){update(key(code),false);return key(code)!=0;}
    public boolean mouseClicked(double x,double y,int button){if(button<2)update(button==0?Minigame.LEFT:Minigame.RIGHT,true);return true;}
    public boolean mouseReleased(double x,double y,int button){if(button<2)update(button==0?Minigame.LEFT:Minigame.RIGHT,false);return true;}
    public void render(GuiGraphics g){
        int serverTick=num(state,"tick");var smooth=presentation.frame(exercise,keys,countdown>0,System.nanoTime());double tick=smooth.tick();renderedFrames++;if(lastRenderedServerTick==serverTick&&Math.abs(tick-lastRenderedClock)>.00001)interpolatedFrames++;lastRenderedServerTick=serverTick;lastRenderedClock=tick;
        double marker=state.get("locked").getAsBoolean()?state.get("marker").getAsDouble():pendingPunch>=0?localHitMarker:pattern.boxMarkerAt(tick);MinigameHudRenderer.draw(g,pattern,state,smooth,countdown,marker);
    }
    @SubscribeEvent public static void draw(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(mc.options.hideGui)return;if(current!=null)current.render(e.getGuiGraphics());else if(System.currentTimeMillis()<toastUntil)e.getGuiGraphics().drawCenteredString(mc.font,toast,mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()-76,GREEN);}
    @SubscribeEvent public static void input(InputEvent.Key e){if(current==null||Minecraft.getInstance().screen!=null)return;if(e.getAction()==GLFW.GLFW_PRESS)current.keyPressed(e.getKey(),e.getScanCode(),e.getModifiers());else if(e.getAction()==GLFW.GLFW_RELEASE)current.keyReleased(e.getKey(),e.getScanCode(),e.getModifiers());}
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre e){if(current==null||Minecraft.getInstance().screen!=null)return;if(e.getButton()<2){if(e.getAction()==GLFW.GLFW_PRESS)current.mouseClicked(0,0,e.getButton());else if(e.getAction()==GLFW.GLFW_RELEASE)current.mouseReleased(0,0,e.getButton());e.setCanceled(true);}}
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered e){if(current!=null){e.setCanceled(true);e.setSwingHand(false);}}
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e){if(current!=null){e.getInput().forwardImpulse=0;e.getInput().leftImpulse=0;e.getInput().jumping=false;e.getInput().shiftKeyDown=false;}}
    @SubscribeEvent public static void screen(ScreenEvent.Opening e){if(current!=null&&e.getNewScreen()!=null)current.cancel();}
}
