package pt.realmfitness.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import pt.realmfitness.FitnessNetwork;
import pt.realmfitness.domain.*;
import static pt.realmfitness.client.FitnessTheme.*;
import static pt.realmfitness.client.FitnessClient.*;

/** World HUD and inputs. There is no Screen, cursor or intermediate set menu. */
@EventBusSubscriber(modid="realmfitness",value=Dist.CLIENT)
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
    private int countdown,keys,sequence;
    private long received;
    private final CameraType previousCamera;
    private TrainingHud(JsonObject d){
        id=str(d,"id");exercise=Exercise.valueOf(str(d,"exercise"));pattern=new Minigame(UUID.fromString(id),exercise,num(d,"load"),num(d,"repetitions"),time(d,"seed"),d.get("fatigue").getAsDouble());
        var mc=Minecraft.getInstance();previousCamera=mc.options.getCameraType();mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if(mc.player!=null){mc.player.setYRot(d.has("yaw")?d.get("yaw").getAsFloat()+(exercise==Exercise.BENCH?100:28):mc.player.getYRot());mc.player.setXRot(16);}
        accept(d);
    }
    public static TrainingHud active(){return current;}
    public static void start(JsonObject d){clear();current=new TrainingHud(d);toastUntil=0;}
    public static void clear(){toastUntil=0;if(current!=null){Minecraft.getInstance().options.setCameraType(current.previousCamera);current=null;}resetMovement();}
    public static void finish(JsonObject d){clear();toast=text("set_result",num(d,"valid"),num(d,"repetitions"),num(d,"accuracy"),num(d,"xp"));toastUntil=System.currentTimeMillis()+4500;}
    private static void resetMovement(){var mc=Minecraft.getInstance();for(var key:new KeyMapping[]{mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift,mc.options.keyAttack,mc.options.keyUse})key.setDown(false);}
    public void accept(JsonObject d){if(!id.equals(str(d,"id")))return;snapshots++;countdown=num(d,"countdown");state=d.getAsJsonObject("state");received=System.nanoTime();presentation.accept(num(state,"tick"),state.get("effort").getAsDouble(),state.get("balance").getAsDouble(),state.get("grip").getAsDouble(),received);}
    public String session(){return id;}
    public void cancel(){if(Minecraft.getInstance().getConnection()!=null)FitnessNetwork.request("cancel",Map.of());clear();}
    private void update(int bit,boolean down){if(bit==0)return;int next=down?keys|bit:keys&~bit;if(next!=keys){keys=next;FitnessNetwork.request("input",Map.of("id",id,"sequence",sequence++,"keys",keys));}}
    private static int key(int code){return switch(code){case GLFW.GLFW_KEY_SPACE->Minigame.SPACE;case GLFW.GLFW_KEY_A->Minigame.A;case GLFW.GLFW_KEY_D->Minigame.D;case GLFW.GLFW_KEY_W->Minigame.W;case GLFW.GLFW_KEY_S->Minigame.S;default->0;};}
    public boolean keyPressed(int code,int scan,int mods){if(code==GLFW.GLFW_KEY_LEFT_SHIFT||code==GLFW.GLFW_KEY_RIGHT_SHIFT||code==GLFW.GLFW_KEY_ESCAPE){cancel();return true;}update(key(code),true);return key(code)!=0;}
    public boolean keyReleased(int code,int scan,int mods){update(key(code),false);return key(code)!=0;}
    public boolean mouseClicked(double x,double y,int button){if(button<2)update(button==0?Minigame.LEFT:Minigame.RIGHT,true);return true;}
    public boolean mouseReleased(double x,double y,int button){if(button<2)update(button==0?Minigame.LEFT:Minigame.RIGHT,false);return true;}
    public void render(GuiGraphics g){
        var mc=Minecraft.getInstance();var font=mc.font;int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
        int w=Math.min(270,width-16),x=(width-w)/2,y=height-99,h=48;
        g.fillGradient(x-5,y-4,x+w+5,y+h,0x95110F18,0xB0110F18);
        label(g,exercise(exercise)+" · "+load(exercise,pattern.load),x,y,w-70,ACCENT);
        String reps=Math.min(pattern.repetitions,num(state,"tick")/48)+" / "+pattern.repetitions;g.drawString(font,reps,x+w-font.width(reps),y,INK,false);
        int serverTick=num(state,"tick");var smooth=presentation.frame(exercise,keys,countdown>0,System.nanoTime());double tick=smooth.tick();renderedFrames++;if(lastRenderedServerTick==serverTick&&Math.abs(tick-lastRenderedClock)>.00001)interpolatedFrames++;lastRenderedServerTick=serverTick;lastRenderedClock=tick;
        if(countdown>0){g.drawCenteredString(font,text("countdown",(countdown+19)/20),width/2,y+18,INK);}
        else if(exercise==Exercise.RUN||exercise==Exercise.BOX||exercise==Exercise.ROW){
            int length=pattern.pulseLength(),pulse=(int)((tick+length/2.0)/length),centre=x+w/2;
            g.fill(centre-1,y+12,centre+1,y+31,GREEN);
            for(int i=-2;i<=2;i++){int index=pulse+i;if(index<0)continue;int px=centre+(int)((index*length-tick)/(double)length*w/5);if(px<x+12||px>x+w-12)continue;String cue=keyName(pattern.expectedKey(index));g.fill(px-10,y+15,px+11,y+29,index==pulse?0xB05E4D70:0x6541374D);g.drawCenteredString(font,cue,px,y+18,index==pulse?INK:MUTED);}
        }else{
            double target=.5-.34*Math.cos((tick%48)*Math.PI*2/48),tol=state.get("tolerance").getAsDouble(),effort=smooth.effort();
            g.fill(x,y+17,x+w,y+25,0xB02C2735);g.fill(x+(int)(w*Math.max(0,target-tol)),y+17,x+(int)(w*Math.min(1,target+tol)),y+25,0xAD5B8E74);int mark=x+(int)(w*effort);g.fill(mark-1,y+14,mark+2,y+28,ACCENT);
            if(exercise==Exercise.PULL){bar(g,x,y+30,55,smooth.grip(),GREEN);double balance=smooth.balance();g.fill(x+w-56,y+30,x+w,y+33,0xB02C2735);g.fill(x+w-56+(int)(balance*53),y+29,x+w-53+(int)(balance*53),y+34,ACCENT);}
        }
        String hint=countdown>0||serverTick<48?text("compact_controls."+exercise.id()):text("leave_machine");g.drawCenteredString(font,hint,width/2,y+38,MUTED);
        bar(g,x,y+h-2,w,tick/(pattern.repetitions*48),GREEN);
    }
    private static String keyName(int key){return switch(key){case Minigame.A->"A";case Minigame.D->"D";case Minigame.W->"W";case Minigame.S->"S";case Minigame.LEFT->"M1";case Minigame.RIGHT->"M2";default->"SPACE";};}
    @SubscribeEvent public static void draw(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(mc.options.hideGui)return;if(current!=null)current.render(e.getGuiGraphics());else if(System.currentTimeMillis()<toastUntil)e.getGuiGraphics().drawCenteredString(mc.font,toast,mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()-76,GREEN);}
    @SubscribeEvent public static void input(InputEvent.Key e){if(current==null||Minecraft.getInstance().screen!=null)return;if(e.getAction()==GLFW.GLFW_PRESS)current.keyPressed(e.getKey(),e.getScanCode(),e.getModifiers());else if(e.getAction()==GLFW.GLFW_RELEASE)current.keyReleased(e.getKey(),e.getScanCode(),e.getModifiers());}
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre e){if(current==null||Minecraft.getInstance().screen!=null)return;if(e.getButton()<2){if(e.getAction()==GLFW.GLFW_PRESS)current.mouseClicked(0,0,e.getButton());else if(e.getAction()==GLFW.GLFW_RELEASE)current.mouseReleased(0,0,e.getButton());e.setCanceled(true);}}
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered e){if(current!=null){e.setCanceled(true);e.setSwingHand(false);}}
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e){if(current!=null){e.getInput().forwardImpulse=0;e.getInput().leftImpulse=0;e.getInput().jumping=false;e.getInput().shiftKeyDown=false;}}
    @SubscribeEvent public static void screen(ScreenEvent.Opening e){if(current!=null&&e.getNewScreen()!=null)current.cancel();}
}
