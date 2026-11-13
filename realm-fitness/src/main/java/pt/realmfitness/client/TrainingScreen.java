package pt.realmfitness.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import pt.realmfitness.FitnessNetwork;
import pt.realmfitness.domain.*;
import static pt.realmfitness.client.FitnessTheme.*;
import static pt.realmfitness.client.FitnessClient.*;

/** Small live HUD over the world. All feedback comes from authoritative simulation snapshots. */
public final class TrainingScreen extends Screen {
    private final String id;
    private final Exercise exercise;
    private final Minigame pattern;
    private JsonObject state;
    private int countdown;
    private net.minecraft.client.CameraType previousCamera;
    private int keys,sequence;
    private long received;
    public TrainingScreen(JsonObject d){
        super(tr("training"));id=str(d,"id");exercise=Exercise.valueOf(str(d,"exercise"));
        pattern=new Minigame(UUID.fromString(id),exercise,num(d,"load"),num(d,"repetitions"),time(d,"seed"),d.get("fatigue").getAsDouble());accept(d);
    }
    public String session(){return id;}
    public void accept(JsonObject d){if(!id.equals(str(d,"id")))return;countdown=num(d,"countdown");state=d.getAsJsonObject("state");received=System.nanoTime();}
    @Override protected void init(){if(previousCamera==null){previousCamera=minecraft.options.getCameraType();minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);}addRenderableWidget(FitnessTheme.button(width-80,10,70,18,tr("stop"),this::onClose));}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        int w=Math.min(500,width-16),h=112,x=(width-w)/2,y=height-h-10;
        frame(g,x,y,w,h,0xED181422);label(g,exercise(exercise)+" · "+load(exercise,pattern.load),x+12,y+10,w-24,ACCENT);
        int serverTick=num(state,"tick"),tick=serverTick+(int)Math.min(4,(System.nanoTime()-received)/50_000_000);
        label(g,countdown>0?text("countdown",(countdown+19)/20):text("live_reps",serverTick/Minigame.REP_TICKS,pattern.repetitions,num(state,"valid")),x+12,y+24,w-24,INK);
        if(exercise==Exercise.RUN||exercise==Exercise.BOX||exercise==Exercise.ROW){
            int length=pattern.pulseLength(),pulse=(tick+length/2)/length;int bx=x+12,by=y+43,bw=w-24;
            for(int i=-2;i<=3;i++){int index=pulse+i;if(index<0)continue;double offset=(index*length-tick)/(double)length;int px=bx+bw/2+(int)(offset*bw/7);String key=keyName(pattern.expectedKey(index));g.fill(px-13,by,px+14,by+20,index==pulse?0xFF675078:0xFF332A41);g.drawCenteredString(font,key,px,by+6,index==pulse?INK:MUTED);}
            g.fill(bx+bw/2-1,by-3,bx+bw/2+1,by+23,GREEN);
            label(g,text("cue_hint"),x+12,y+72,w-24,MUTED);
        }else{
            int bx=x+12,by=y+47,bw=w-24;double target=state.get("target").getAsDouble(),tolerance=state.get("tolerance").getAsDouble(),effort=state.get("effort").getAsDouble();
            g.fill(bx,by,bx+bw,by+16,0xFF332A41);g.fill(bx+(int)(bw*Math.max(0,target-tolerance)),by,bx+(int)(bw*Math.min(1,target+tolerance)),by+16,0xFF476956);g.fill(bx+(int)(bw*effort)-2,by-3,bx+(int)(bw*effort)+2,by+19,ACCENT);
            label(g,text("controls."+exercise.id()),x+12,y+73,w-24,MUTED);
            if(exercise==Exercise.PULL)label(g,text("grip",(int)(state.get("grip").getAsDouble()*100)),x+12,y+87,w-24,MUTED);
        }
        bar(g,x+12,y+h-12,w-24,serverTick/(double)(pattern.repetitions*Minigame.REP_TICKS),GREEN);
        super.render(g,mx,my,partial);
    }
    private static String keyName(int key){return switch(key){case Minigame.A->"A";case Minigame.D->"D";case Minigame.W->"W";case Minigame.S->"S";case Minigame.LEFT->"LMB";case Minigame.RIGHT->"RMB";default->"SPACE";};}
    private int key(int code){return switch(code){case GLFW.GLFW_KEY_SPACE->Minigame.SPACE;case GLFW.GLFW_KEY_A->Minigame.A;case GLFW.GLFW_KEY_D->Minigame.D;case GLFW.GLFW_KEY_W->Minigame.W;case GLFW.GLFW_KEY_S->Minigame.S;default->0;};}
    private void update(int bit,boolean down){int next=down?keys|bit:keys&~bit;if(next!=keys){keys=next;FitnessNetwork.request("input",Map.of("id",id,"sequence",sequence++,"keys",keys));}}
    @Override public boolean keyPressed(int code,int scan,int mods){if(code==GLFW.GLFW_KEY_F5){minecraft.options.setCameraType(minecraft.options.getCameraType().cycle());return true;}if(code==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}int bit=key(code);if(bit!=0){update(bit,true);return true;}return super.keyPressed(code,scan,mods);}
    @Override public boolean keyReleased(int code,int scan,int mods){int bit=key(code);if(bit!=0){update(bit,false);return true;}return super.keyReleased(code,scan,mods);}
    @Override public boolean mouseClicked(double mx,double my,int button){if(super.mouseClicked(mx,my,button))return true;if(button<2){update(button==0?Minigame.LEFT:Minigame.RIGHT,true);return true;}return false;}
    @Override public boolean mouseReleased(double mx,double my,int button){if(button<2){update(button==0?Minigame.LEFT:Minigame.RIGHT,false);return true;}return super.mouseReleased(mx,my,button);}
    @Override public void onClose(){FitnessNetwork.request("cancel",Map.of());minecraft.setScreen(null);}
    @Override public void removed(){if(previousCamera!=null)minecraft.options.setCameraType(previousCamera);}
    @Override public boolean isPauseScreen(){return false;}
}
