package pt.realmfitness.client;

import pt.realmfitness.domain.*;

/** Frame-time presentation only; authoritative scores never use these predicted values. */
public final class TrainingPresentation {
    public record Frame(double tick,double effort,double balance,double grip){}
    private double serverTick,serverEffort=.5,serverBalance=.5,serverGrip=1,shownTick,shownEffort=.5,shownBalance=.5,shownGrip=1;
    private long received,lastFrame;
    private boolean initial=true;
    public void accept(int tick,double effort,double balance,double grip,long now){
        serverTick=tick;serverEffort=effort;serverBalance=balance;serverGrip=grip;received=now;
        if(initial){shownTick=tick;shownEffort=effort;shownBalance=balance;shownGrip=grip;lastFrame=now;initial=false;}
    }
    public Frame frame(Exercise exercise,int keys,boolean countdown,long now){
        double dt=Math.clamp((now-lastFrame)/1_000_000_000.0,0,.1);lastFrame=now;
        if(countdown){shownTick=0;return new Frame(0,shownEffort,shownBalance,shownGrip);}
        double elapsed=Math.clamp((now-received)/50_000_000.0,0,2.5),clock=serverTick+elapsed;
        double advanced=shownTick+dt*20;shownTick=Math.max(shownTick,Math.min(serverTick+3.5,advanced+(clock-advanced)*(1-Math.exp(-dt*9))));
        int direction=exercise==Exercise.SQUAT?((keys&Minigame.W)!=0?1:0)-((keys&Minigame.S)!=0?1:0):((keys&Minigame.SPACE)!=0?1:-1);
        double predicted=exercise==Exercise.BENCH||exercise==Exercise.PULL||exercise==Exercise.SQUAT?Math.clamp(serverEffort+direction*.042*elapsed,0,1):serverEffort;
        double alpha=1-Math.exp(-dt/0.055);shownEffort+=(predicted-shownEffort)*alpha;shownBalance+=(serverBalance-shownBalance)*alpha;shownGrip+=(serverGrip-shownGrip)*alpha;
        return new Frame(shownTick,shownEffort,shownBalance,shownGrip);
    }
}
