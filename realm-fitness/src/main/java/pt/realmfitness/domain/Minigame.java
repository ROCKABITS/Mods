package pt.realmfitness.domain;

import java.util.UUID;

/** Six authoritative skill games. Packets contain button edges/holds, never a score or a claimed clock. */
public final class Minigame {
    public static final int SPACE=1,A=2,D=4,W=8,S=16,LEFT=32,RIGHT=64,MASK=127,REP_TICKS=48;
    public final UUID id;
    public final Exercise exercise;
    public final int load,repetitions,trainingLevel;
    public final long seed;
    public final double fatigue;
    public int feedbackId,tick,valid,hits,attempted,streak,bestStreak,feedbackTicks,nextFoot=A,step,holdTicks;
    public double effort,balance=.5,grip=1,quality,hitMarker=.5;
    public boolean landed,locked;
    public String feedback="";
    private int held,edges,sequence=-1,lastInputTick=-1,changes,lastTap=-100,lastStep=-100,lockedAt=-100,riseStarted=-1,repActions,recoveryTicks;
    private double repQuality,peak,repScore,powerScore,descentQuality;
    private boolean charging,topReached,failed;

    public Minigame(UUID id,Exercise exercise,int load,int repetitions,long seed,double fatigue){this(id,exercise,load,repetitions,seed,fatigue,0);}
    public Minigame(UUID id,Exercise exercise,int load,int repetitions,long seed,double fatigue,int trainingLevel){
        if(id==null||exercise==null||repetitions<4||repetitions>20||!Double.isFinite(fatigue)||fatigue<0||fatigue>1)throw new IllegalArgumentException("fitness.invalid_series");
        if(trainingLevel<0||trainingLevel>20)throw new IllegalArgumentException("fitness.invalid_series");this.trainingLevel=trainingLevel;exercise.checkLoad(load,20);this.id=id;this.exercise=exercise;this.load=load;this.repetitions=repetitions;this.seed=seed;this.fatigue=fatigue;
        effort=exercise==Exercise.RUN?.35:exercise==Exercise.BENCH?.05:0;
    }
    public void input(int number,int keys){
        if(done()||number<=sequence||number>sequence+128||keys<0||(keys&~MASK)!=0)return;
        if(lastInputTick==tick&&++changes>8)return;if(lastInputTick!=tick){lastInputTick=tick;changes=1;}
        sequence=number;edges|=keys&~held;held=keys;
    }
    public int keys(){return held;}
    public void clearEdges(){edges=0;}
    public int repTicks(){return switch(exercise){case BENCH->64;case BOX->80;default->48;};}
    public int completed(){return tick/repTicks();}
    public int phase(){return tick%repTicks();}
    public double ease(){double capacity=exercise.weighted?exercise.maximumLoad(trainingLevel):1+trainingLevel/4.0;return Math.clamp(1-load/capacity,0,.85);}
    public double benchTapGain(){return (.22-(load-20)*.0006-fatigue*.015)*(1+.40*ease());}
    public double benchDrain(){return benchDrainAt(tick);}
    public double benchDrainAt(double time){return (.0045+(load-20)*.00002+fatigue*.003+(int)(time/repTicks())*.00055)*(1-.35*ease());}
    public double runDrag(){return (.0075+(load-1)*.001+fatigue*.002)*(1-.25*ease());}
    public double pullRiseRate(){return Math.max(.042,.060-(load-1)*.0025-fatigue*.005)+.008*ease();}
    public double pullGripCost(){return (.012+(load-1)*.0015+fatigue*.004)*(1-.3*ease());}
    public double squatRiseRate(){return Math.max(.06,.085-(load-20)*.00015-fatigue*.008)+.01*ease();}
    public int squatHoldRequired(){return 5+(int)(fatigue*2)+Math.min(2,completed()/4);}
    public double rowChargeSpeed(){return .055-(load-1)*.002-fatigue*.006;}
    public double target(){return switch(exercise){case BENCH->1;case BOX,PULL->.5;case RUN->runTarget(tick);case ROW->.72;case SQUAT->.75;};}
    public int terrain(double time){return (int)Math.floorMod(mix(seed+(long)(time/96)),3);}
    public double runTarget(double time){return switch(terrain(time)){case 0->.44;case 1->.57;default->.67;};}
    public double tolerance(){return switch(exercise){case BOX->Math.max(.09,.16-(load-1)*.008-fatigue*.018)+.035*ease();case RUN->Math.max(.13,.20-(load-1)*.007-fatigue*.015)+.04*ease();case ROW->Math.max(.085,.15-(load-1)*.008-fatigue*.02)+.04*ease();case SQUAT->Math.max(.065,.12-(load-20)*.00035-fatigue*.02)+.035*ease();case PULL->.24;default->.1;};}
    public int boxPeriod(double time){int rep=(int)(time/repTicks());return 4*Math.max(5,10+(int)(ease()*3)-(load-1)/2-(int)(fatigue*2)-Math.min(2,rep/3));}
    public double boxMarkerAt(double time){int rep=(int)(Math.max(0,time)/repTicks()),period=boxPeriod(time);int offset=Math.floorMod(mix(seed+rep*71L),period);double position=((time%repTicks()+offset)%period)/period;return 1-Math.abs(2*position-1);}
    public double marker(){return locked?hitMarker:boxMarkerAt(tick);}
    public String stage(){
        if(landed)return "rest";if(failed||exercise==Exercise.BOX&&locked)return "miss";
        return switch(exercise){case BENCH->"tap";case BOX->"aim";case RUN->"pace";case PULL->topReached?"lower":"pull";case ROW->step==0?"charge":"recover";case SQUAT->step==1?"rise":Math.abs(effort-.75)<=tolerance()?"hold":"descend";};
    }
    /** Retained for helpers; timing is now specific to each exercise. */
    public int pulseLength(){return exercise==Exercise.ROW?24:12;}
    public int expectedKey(int pulse){return switch(exercise){case RUN->pulse%2==0?A:D;case BOX->pulse%2==0?LEFT:RIGHT;case ROW->pulse%2==0?W:S;default->SPACE;};}
    public int sequence(){return sequence;}
    private void say(String grade){feedbackId++;feedback=grade;feedbackTicks=16;}
    private void fail(){failed=true;say("miss");}
    public double advance(){
        if(done())return accuracy();int rising=edges;edges=0;if(feedbackTicks>0)feedbackTicks--;
        switch(exercise){
            case BENCH->bench(rising);
            case BOX->box(rising);
            case RUN->run(rising);
            case PULL->pull();
            case ROW->row();
            case SQUAT->squat();
        }
        peak=Math.max(peak,effort);tick++;
        if(exercise==Exercise.BOX&&locked&&tick-lockedAt>=12&&tick%repTicks()!=0)tick=(tick/repTicks()+1)*repTicks();
        if(tick%repTicks()==0){
            double score=switch(exercise){
                case BENCH->landed?repScore:peak*.35;
                case BOX->locked?repScore:0;
                case RUN->repActions>=2?repQuality/repTicks():0;
                case PULL->landed?Math.clamp(.6+.4*repQuality/repTicks(),0,1):0;
                case ROW->landed?Math.clamp(powerScore*.8+.2*Math.min(1,recoveryTicks/12.0),0,1):0;
                case SQUAT->landed?Math.clamp(.65+.35*descentQuality/Math.max(1,Math.min(18,phaseForScore)),0,1):0;
            };
            score=Math.clamp(score,0,1);if(score>=.5)valid++;quality+=score;
            if(exercise==Exercise.BOX&&!locked){streak=0;say("miss");}
            else if(exercise!=Exercise.BOX)say(score>=.9?"perfect":score>=.5?"good":"miss");
            resetRep();
        }
        return effort;
    }
    private int phaseForScore;
    private void bench(int rising){
        if(landed)return;effort=Math.max(0,effort-benchDrain());
        if((rising&SPACE)!=0&&tick-lastTap>=2){lastTap=tick;attempted++;repActions++;effort=Math.min(1,effort+benchTapGain());}
        if(effort>=.999){landed=true;hits++;repScore=1-.15*phase()/repTicks();say("good");}
    }
    private void box(int rising){
        effort=Math.max(0,effort-.08);if(locked)return;int punch=rising&(LEFT|RIGHT);if(punch==0)return;
        attempted++;locked=true;lockedAt=tick;hitMarker=boxMarkerAt(tick);double distance=Math.abs(hitMarker-.5);
        if(punch==(LEFT|RIGHT)||distance>tolerance()){repScore=0;streak=0;fail();return;}
        double perfect=Math.max(.025,.055-(load-1)*.004-fatigue*.01)+.015*ease();repScore=distance<=perfect?1:.55+.45*(1-distance/tolerance());landed=true;effort=1;hits++;streak++;bestStreak=Math.max(bestStreak,streak);say(distance<=perfect?"perfect":"good");
    }
    private void run(int rising){
        double drag=runDrag();effort=Math.max(0,effort-drag);grip=Math.min(1,grip+.0025);
        int feet=rising&(A|D);
        if(feet!=0){attempted++;if(feet==nextFoot&&tick-lastStep>=4){lastStep=tick;nextFoot=nextFoot==A?D:A;effort=Math.min(1,effort+.11-fatigue*.015);grip=Math.max(0,grip-.021);repActions++;hits++;}else{grip=Math.max(0,grip-.085);say("wrong_step");}}
        if(effort>target()+tolerance())grip=Math.max(0,grip-.009);
        double form=Math.clamp(1-Math.abs(effort-target())/tolerance(),0,1);repQuality+=form*(.55+.45*grip);
    }
    private void pull(){
        if(landed||failed){grip=Math.min(1,grip+.018);if(failed)effort=Math.max(0,effort-.08);return;}boolean pushing=(held&SPACE)!=0;
        double wind=Math.sin((tick+Math.floorMod(seed,71))*.11)*.009+Math.sin((tick+Math.floorMod(seed,37))*.037)*.006;
        balance=Math.clamp(balance+wind*(1-.25*ease())+((held&D)!=0?.025:0)-((held&A)!=0?.025:0),0,1);
        if(pushing){grip=Math.max(0,grip-pullGripCost());effort=Math.min(1,effort+pullRiseRate());}
        else{grip=Math.min(1,grip+.022);effort=Math.max(0,effort-.060);}
        repQuality+=Math.clamp(1-Math.abs(balance-.5)*2,0,1)*grip;
        if(grip<=.02||balance<.06||balance>.94){fail();return;}
        if(effort>=.92&&Math.abs(balance-.5)<=tolerance())topReached=true;
        if(topReached&&!pushing&&effort<=.10){landed=true;hits++;say("good");}
    }
    private void row(){
        if(landed||failed)return;boolean pulling=(held&W)!=0,recovering=(held&S)!=0;
        if(pulling&&recovering){fail();return;}
        if(step==0){
            if(pulling){charging=true;effort=Math.min(1,effort+rowChargeSpeed());if(effort>=.99)fail();}
            else if(charging){
                double distance=Math.abs(effort-.72);powerScore=1-distance/(tolerance()*2);if(distance>tolerance()){fail();return;}step=1;say(distance<tolerance()*.35?"perfect":"good");
            }
        }
        if(step==1){if(pulling){fail();return;}if(recovering)recoveryTicks++;effort=Math.max(0,effort-(recovering?.05:.006));if(effort<=.08&&recoveryTicks>=8){landed=true;hits++;}}
    }
    private void squat(){
        if(landed||failed)return;boolean down=(held&W)!=0,up=(held&S)!=0;if(down&&up){fail();return;}
        if(step==0){
            if(up){fail();return;}if(down)effort=Math.min(1,effort+.045);
            double desired=.75*Math.min(1,phase()/18.0);if(phase()<18){descentQuality+=Math.clamp(1-Math.abs(effort-desired)/.20,0,1);phaseForScore++;}
            if(effort>.93){fail();return;}
            if(!down&&Math.abs(effort-.75)<=tolerance()){holdTicks++;if(holdTicks>=squatHoldRequired()){step=1;say("rise");}}
            else holdTicks=0;
        }else if(up){if(riseStarted<0)riseStarted=tick;if(tick-riseStarted>16){fail();return;}effort=Math.max(0,effort-squatRiseRate());if(effort<=.08){landed=true;hits++;}}
    }
    private void resetRep(){
        riseStarted=-1;repQuality=peak=repScore=powerScore=descentQuality=0;repActions=recoveryTicks=holdTicks=phaseForScore=step=0;charging=topReached=failed=landed=locked=false;
        if(exercise!=Exercise.RUN)effort=exercise==Exercise.BENCH?.05:0;
    }
    public boolean done(){return tick>=repetitions*repTicks();}
    public int accuracy(){return (int)Math.round(100*quality/Math.max(1,completed()));}
    private static long mix(long x){x^=x>>>30;x*=0xbf58476d1ce4e5b9L;x^=x>>>27;x*=0x94d049bb133111ebL;return x^(x>>>31);}
}
