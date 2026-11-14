package pt.realmfitness.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import pt.realmfitness.domain.*;
import static pt.realmfitness.client.FitnessTheme.*;
import static pt.realmfitness.client.FitnessClient.*;

/** Compact, exercise-specific world cues. All judgements and streaks come from the server. */
final class MinigameHudRenderer {
    private static final int TRACK=0xB02C2735,GOOD=0xB95B8E74,GOLD=0xFFE9CC86,RED=0xFFE09A9B;
    private MinigameHudRenderer(){}
    static void draw(GuiGraphics g,Minigame rules,JsonObject state,TrainingPresentation.Frame frame,int countdown,double boxMarker){
        var mc=Minecraft.getInstance();var font=mc.font;int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
        int w=Math.min(292,width-16),x=(width-w)/2,y=height-109,h=58,by=y+17;String stage=str(state,"stage");
        g.fillGradient(x-5,y-4,x+w+5,y+h,0x95110F18,0xB0110F18);
        String progress=Math.min(rules.repetitions,num(state,"tick")/rules.repTicks())+" / "+rules.repetitions;
        label(g,exercise(rules.exercise)+" · "+load(rules.exercise,rules.load),x,y,w-67,ACCENT);g.drawString(font,progress,x+w-font.width(progress),y,INK,false);
        if(countdown>0){g.drawCenteredString(font,text("countdown",(countdown+19)/20),width/2,by+4,INK);}
        else{
            double value=frame.effort(),target=state.get("target").getAsDouble(),tolerance=state.get("tolerance").getAsDouble();
            switch(rules.exercise){
                case BENCH->{
                    track(g,x,by,w,11);fill(g,x,by,w,11,value,stage.equals("rest")?GREEN:ACCENT);g.fill(x+w-2,by-2,x+w,by+13,GOLD);
                    label(g,text("game.pressure"),x,by+17,w/2,MUTED);g.drawString(font,"SPACE",x+w-font.width("SPACE"),by+17,stage.equals("rest")?MUTED:INK,false);
                }
                case BOX->{
                    track(g,x,by,w,12);zone(g,x,by,w,.5,tolerance,GOOD);zone(g,x,by,w,.5,Math.max(.025,.055-(rules.load-1)*.004-rules.fatigue*.01)+.015*rules.ease(),0xD4B99750);
                    int mark=x+(int)(w*boxMarker);g.fill(mark-1,by-3,mark+2,by+15,stage.equals("miss")?RED:INK);g.fill(x+w/2,by-2,x+w/2+1,by+14,GOLD);
                    label(g,text("game.streak",num(state,"streak"),num(state,"bestStreak")),x,by+19,w,ACCENT);
                }
                case RUN->{
                    track(g,x,by,w,11);zone(g,x,by,w,target,tolerance,GOOD);marker(g,x,by,w,value,ACCENT);
                    String foot=num(state,"nextFoot")==Minigame.A?"A":"D";g.drawString(font,text("game.next_step",foot)+" · "+text("game.terrain."+rules.terrain(frame.tick())),x,by+18,INK,false);bar(g,x+w-67,by+21,67,frame.grip(),GREEN);
                }
                case PULL->{
                    track(g,x,by,w,11);fill(g,x,by,w,11,value,ACCENT);g.fill(x+(int)(w*.92),by,x+w,by+11,0x655B8E74);
                    bar(g,x,by+21,70,frame.grip(),GREEN);g.fill(x+w-70,by+20,x+w,by+24,TRACK);g.fill(x+w-53,by+20,x+w-17,by+24,GOOD);int balanceMark=x+w-70+(int)(70*Math.clamp(frame.balance(),0,1));g.fill(balanceMark-1,by+18,balanceMark+2,by+25,ACCENT);
                }
                case ROW->{
                    track(g,x,by,w,11);zone(g,x,by,w,stage.equals("recover")?.055:.72,stage.equals("recover")?.055:tolerance,GOOD);fill(g,x,by+3,w,5,value,ACCENT);marker(g,x,by,w,value,INK);
                    label(g,text(stage.equals("recover")?"game.row_recover":"game.row_power"),x,by+18,w,MUTED);
                }
                case SQUAT->{
                    track(g,x,by,w,11);zone(g,x,by,w,.75,tolerance,GOOD);marker(g,x,by,w,value,ACCENT);
                    label(g,text("game.pause",num(state,"holdTicks"),num(state,"holdRequired")),x,by+18,w/2,MUTED);bar(g,x+w-67,by+21,67,Math.min(1,num(state,"holdTicks")/(double)Math.max(1,num(state,"holdRequired"))),GREEN);
                }
                case LEG_PRESS->{
                    int half=(w-18)/2;for(int side=0;side<2;side++){int bx=x+side*(half+18);double pressure=side==0?frame.leftPressure():frame.rightPressure();track(g,bx,by,half,11);zone(g,bx,by,half,target,tolerance,GOOD);marker(g,bx,by,half,pressure,ACCENT);}
                    label(g,"A",x,by+18,20,INK);label(g,"D",x+w-9,by+18,20,INK);bar(g,x+22,by+21,w-44,value,GREEN);
                }
                case TRICEPS->{
                    track(g,x,by,w,11);zone(g,x,by,w,.86,tolerance,GOOD);marker(g,x,by,w,value,ACCENT);
                    label(g,text(stage.equals("rope_hold")?"game.rope_hold":"game.rope_timing"),x,by+18,w,MUTED);
                }
            }
        }
        String footer=countdown>0?text("compact_controls."+rules.exercise.id()):text("game.stage."+stage);
        if(num(state,"feedbackTicks")>0&&!str(state,"feedback").isEmpty())footer=text("game.feedback."+str(state,"feedback"))+" · "+footer;
        boolean badFeedback=str(state,"feedback").equals("miss")||str(state,"feedback").equals("wrong_step");label(g,footer,x,y+46,w,num(state,"feedbackTicks")>0?(badFeedback?RED:GREEN):MUTED);
        bar(g,x,y+h-2,w,frame.tick()/(rules.repetitions*rules.repTicks()),GREEN);
    }
    private static void track(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,TRACK);}
    private static void fill(GuiGraphics g,int x,int y,int w,int h,double amount,int color){g.fill(x,y,x+(int)(w*Math.clamp(amount,0,1)),y+h,color);}
    private static void zone(GuiGraphics g,int x,int y,int w,double target,double tolerance,int color){g.fill(x+(int)(w*Math.max(0,target-tolerance)),y,x+(int)(w*Math.min(1,target+tolerance)),y+11,color);}
    private static void marker(GuiGraphics g,int x,int y,int w,double amount,int color){int p=x+(int)(w*Math.clamp(amount,0,1));g.fill(p-1,y-2,p+2,y+13,color);}
}
