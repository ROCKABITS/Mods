package pt.realmfitness.domain;

import java.util.UUID;

/** Authoritative tick simulation. Inputs contain keys only, never a claimed score or timestamp. */
public final class Minigame {
    public static final int SPACE=1, A=2, D=4, W=8, S=16, LEFT=32, RIGHT=64, MASK=127;
    public static final int REP_TICKS=48;
    public final UUID id;
    public final Exercise exercise;
    public final int load, repetitions;
    public final long seed;
    public final double fatigue;
    public int tick, valid, hits, attempted;
    public double effort=.5, balance=.5, grip=1, quality;
    private double repQuality;
    private int held, edges, sequence=-1, lastInputTick=-1, changes;
    private long lastPulse=-1;

    public Minigame(UUID id, Exercise exercise, int load, int repetitions, long seed, double fatigue) {
        if (id==null || exercise==null || repetitions<4 || repetitions>20 || !Double.isFinite(fatigue) || fatigue<0 || fatigue>1)
            throw new IllegalArgumentException("fitness.invalid_series");
        exercise.checkLoad(load, 20);
        this.id=id;this.exercise=exercise;this.load=load;this.repetitions=repetitions;this.seed=seed;this.fatigue=fatigue;
    }
    public void input(int number, int keys) {
        if (done() || number<=sequence || number>sequence+128 || keys<0 || (keys & ~MASK)!=0) return;
        if (lastInputTick==tick && ++changes>8) return;
        if (lastInputTick!=tick) {lastInputTick=tick;changes=1;}
        sequence=number;edges|=keys&~held;held=keys;
    }
    public int keys(){return held;}
    public int phase() { return tick % REP_TICKS; }
    public double target() { return .5-.34*Math.cos(phase()*Math.PI*2/REP_TICKS); }
    public double tolerance() { return Math.max(.11, .31-(exercise.weighted ? (load-20)/1000.0 : (load-1)*.018)-fatigue*.035); }
    public int pulseLength() { return exercise==Exercise.ROW ? 24 : 12; }
    public int expectedKey(int pulse) {
        return switch(exercise) {
            case RUN -> pulse%2==0 ? A : D;
            case ROW -> pulse%2==0 ? W : S;
            case BOX -> switch((int)(mix(seed+Math.floorDiv(pulse,4)*71L+Math.floorMod(pulse,4)*997L)&3)) {
                case 0 -> LEFT; case 1 -> RIGHT; case 2 -> A; default -> D;
            };
            default -> SPACE;
        };
    }
    public double advance() {
        if(done())return accuracy();
        int rising=edges;edges=0;
        double sample;
        if(exercise==Exercise.RUN || exercise==Exercise.BOX || exercise==Exercise.ROW) {
            int length=pulseLength(), pulse=(tick+length/2)/length;
            int distance=Math.abs(tick-pulse*length);
            int relevant=exercise==Exercise.RUN ? A|D : exercise==Exercise.ROW ? W|S : LEFT|RIGHT|A|D;
            int edge=rising&relevant;
            if(edge!=0) {
                attempted++;
                int window=Math.max(1,4-(load-1)/2-(int)(fatigue*2));
                if(distance<=window && edge==expectedKey(pulse) && pulse!=lastPulse) {
                    double hit=1-distance/6.0;repQuality+=hit;hits++;lastPulse=pulse;
                } else repQuality=Math.max(0,repQuality-.35);
            }
            sample=0;
        } else {
            int direction=exercise==Exercise.SQUAT ? ((held&W)!=0?1:0)-((held&S)!=0?1:0) : ((held&SPACE)!=0?1:-1);
            effort=Math.clamp(effort+direction*.042,0,1);
            if(exercise==Exercise.PULL) {
                grip=Math.clamp(grip+((held&SPACE)!=0?-.0035:.004),0,1);
                balance=Math.clamp(balance+Math.sin((tick+seed%37)*.13)*.009+((held&D)!=0?.025:0)-((held&A)!=0?.025:0),0,1);
            }
            sample=Math.clamp(1-Math.abs(effort-target())/tolerance(),0,1);
            if(exercise==Exercise.PULL)sample*=Math.clamp(1-Math.abs(balance-.5),0,1)*(.5+grip*.5);
            repQuality+=sample;
        }
        tick++;
        if(tick%REP_TICKS==0) {
            double score=(exercise==Exercise.RUN||exercise==Exercise.BOX||exercise==Exercise.ROW)
                    ? Math.clamp(repQuality/(REP_TICKS/(double)pulseLength()),0,1) : repQuality/REP_TICKS;
            if(score>=.5)valid++;
            quality+=score;repQuality=0;
        }
        return sample;
    }
    public boolean done(){return tick>=repetitions*REP_TICKS;}
    public int accuracy(){return (int)Math.round(100*quality/Math.max(1,tick/REP_TICKS));}
    private static long mix(long x){x^=x>>>30;x*=0xbf58476d1ce4e5b9L;x^=x>>>27;x*=0x94d049bb133111ebL;return x^(x>>>31);}
}
