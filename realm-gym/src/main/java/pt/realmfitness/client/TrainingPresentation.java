package pt.realmfitness.client;

import java.util.*;
import pt.realmfitness.domain.*;

/** Frame-time presentation and acknowledged input prediction; never used to award scores. */
public final class TrainingPresentation {
    public record Frame(double tick,double effort,double balance,double grip,double leftPressure,double rightPressure){}
    private record Tap(int sequence,double gain){}
    private final ArrayDeque<Tap> pending=new ArrayDeque<>();
    private Minigame rules;
    private String stage="";
    private double serverTick,serverEffort=.5,serverBalance=.5,serverGrip=1,shownTick,shownEffort=.5,shownBalance=.5,shownGrip=1;
    private long received,lastFrame;
    private boolean initial=true;
    private double serverLeft,serverRight,shownLeft,shownRight;
    public void pressure(double left,double right){serverLeft=left;serverRight=right;}
    public void configure(Minigame rules){this.rules=rules;}
    public double clock(){return shownTick;}
    public void press(int sequence,int bit){if(rules==null)return;if(rules.exercise==Exercise.BENCH&&bit==Minigame.SPACE)pending.add(new Tap(sequence,rules.benchTapGain()));else if(rules.exercise==Exercise.RUN&&(bit==Minigame.A||bit==Minigame.D))pending.add(new Tap(sequence,.11-rules.fatigue*.015));}
    public void acknowledge(int sequence,String stage){this.stage=stage;while(!pending.isEmpty()&&pending.peek().sequence<=sequence)pending.remove();}
    public void accept(int tick,double effort,double balance,double grip,long now){
        boolean newRep=rules!=null&&(int)(serverTick/rules.repTicks())!=tick/rules.repTicks();
        serverTick=tick;serverEffort=effort;serverBalance=balance;serverGrip=grip;received=now;
        if(initial||newRep){shownTick=tick;shownEffort=effort;shownBalance=balance;shownGrip=grip;lastFrame=now;initial=false;}
    }
    public Frame frame(Exercise exercise,int keys,boolean countdown,long now){
        if(rules==null)rules=new Minigame(new UUID(0,1),exercise,exercise.minimumLoad(),8,0,0);
        double dt=Math.clamp((now-lastFrame)/1_000_000_000.0,0,.1);lastFrame=now;
        if(countdown){shownTick=0;return new Frame(0,shownEffort,shownBalance,shownGrip,shownLeft,shownRight);}
        double elapsed=Math.clamp((now-received)/50_000_000.0,0,2.5),clock=serverTick+elapsed;
        double advanced=shownTick+dt*20;shownTick=Math.max(shownTick,Math.min(serverTick+3.5,advanced+(clock-advanced)*(1-Math.exp(-dt*9))));
        double taps=pending.stream().mapToDouble(Tap::gain).sum(),predicted=serverEffort;
        if(!stage.equals("rest")&&!stage.equals("miss"))predicted=switch(exercise){
            case BENCH->serverEffort+taps-rules.benchDrainAt(serverTick)*elapsed;
            case RUN->serverEffort+taps-rules.runDrag()*elapsed;
            case PULL->serverEffort+((keys&Minigame.SPACE)!=0?rules.pullRiseRate():-.060)*elapsed;
            case ROW->serverEffort+(stage.equals("recover")?((keys&Minigame.S)!=0?-.05:-.006):((keys&Minigame.W)!=0?rules.rowChargeSpeed():0))*elapsed;
            case SQUAT->serverEffort+(((keys&Minigame.W)!=0?.045:0)-((keys&Minigame.S)!=0?rules.squatRiseRate():0))*elapsed;
            case TRICEPS->serverEffort+(stage.equals("rope_pull")?((keys&Minigame.SPACE)!=0?rules.ropeSpeed():-.025):stage.equals("rope_return")?((keys&Minigame.SPACE)!=0?-.008:-.042):0)*elapsed;
            case LEG_PRESS->serverEffort+(stage.equals("leg_return")&&(keys&(Minigame.A|Minigame.D))==0?-.05:0)*elapsed;
            default->serverEffort;
        };
        double alpha=1-Math.exp(-dt/.045);shownEffort+=(Math.clamp(predicted,0,1)-shownEffort)*alpha;shownBalance+=(serverBalance-shownBalance)*alpha;shownGrip+=(serverGrip-shownGrip)*alpha;
        double rate=rules.legPressureRate();shownLeft+=(Math.clamp(serverLeft+((keys&Minigame.A)!=0?rate:-rate*.7)*elapsed,0,1)-shownLeft)*alpha;shownRight+=(Math.clamp(serverRight+((keys&Minigame.D)!=0?rate:-rate*.7)*elapsed,0,1)-shownRight)*alpha;
        return new Frame(shownTick,shownEffort,shownBalance,shownGrip,shownLeft,shownRight);
    }
}
