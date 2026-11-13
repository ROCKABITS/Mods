package pt.realmfitness.client;

import pt.realmfitness.domain.*;

/** Authored poses in metres and radians. Two-bone IK keeps hands and feet on their targets. */
public final class ExercisePose {
    public double hips=.703125,forward,lean,twist,sway,head;
    public final double[] arms=new double[4],legs=new double[4];
    public double armSpread=.08,legSpread=.05;
    public double barY,barZ,seatZ;
    private static final double UNIT=.9375/16;
    private ExercisePose(){}
    public static ExercisePose sample(Exercise exercise,double tick,double effort,int action,double age){
        var p=new ExercisePose();double u=Math.clamp(effort,0,1),phase=tick*Math.PI*2/24,beat=Math.sin(phase),strike=age>=0&&age<10?Math.pow(Math.sin(age*Math.PI/10),2):0;
        switch(exercise){
            case BENCH->{
                p.hips=1.13;p.lean=-Math.PI/2;p.head=.04;p.barY=1.29+.50*u;p.barZ=1.78;p.armSpread=.12;p.legSpread=.12;
                p.hands(p.barY,-.78);p.feet(.45,.25);
            }
            case RUN->{
                p.hips+=.023*Math.cos(phase*2);p.lean=.10;p.head=-.07;
                p.arms[0]=-.25-beat*.62;p.arms[1]=-1.0;p.arms[2]=-.25+beat*.62;p.arms[3]=-1.0;
                p.legs[0]=beat*.75;p.legs[1]=.12+Math.max(0,-beat)*1.15;p.legs[2]=-beat*.75;p.legs[3]=.12+Math.max(0,beat)*1.15;
            }
            case BOX->{
                p.hips-=.035;p.lean=.08;p.legSpread=.12;
                double right=(action&Minigame.RIGHT)!=0?strike:0,left=(action&Minigame.LEFT)!=0?strike:0;
                p.twist=(right-left)*.20;p.sway=((action&Minigame.A)!=0?1:(action&Minigame.D)!=0?-1:0)*strike*.20;
                p.hand(0,1.24+.08*right,.28+.40*right);p.hand(1,1.24+.08*left,.28+.40*left);p.feet(0,.08);
            }
            case PULL->{
                p.hips+=.50*u;p.lean=.03;p.sway=Math.sin(tick*.09)*.025;p.armSpread=.18;p.hands(2.0,0);
                p.legs[0]=p.legs[2]=-.18-.18*u;p.legs[1]=p.legs[3]=.40+.35*u;
            }
            case ROW->{
                p.hips=.95;p.forward=.35-.18*u;p.lean=.28-.50*u;p.head=-p.lean*.65;p.armSpread=.06;p.legSpread=.22;
                p.seatZ=p.forward;p.barY=1.06+.12*u;p.barZ=1.30-.30*u;
                p.hands(p.barY,p.barZ-.65);p.feet(.60,.75);
            }
            case SQUAT->{
                p.hips-=.32*u;p.forward=-.22*u;p.lean=.33*u;p.head=-p.lean*.8;p.armSpread=.40;p.legSpread=.12;
                p.barY=1.40-.32*u;p.barZ=1.14;p.hands(p.barY,-.0475);p.feet(0,.055);
            }
        }
        return p;
    }
    private void hands(double y,double z){hand(0,y,z);hand(1,y,z);}
    private void hand(int side,double y,double forwardTarget){
        // Convert a fixed world target into the leaning torso's shoulder coordinates.
        double dy=-(y-hips)/UNIT,dz=-(forwardTarget-forward)/UNIT,c=Math.cos(lean),s=Math.sin(lean);
        double localY=dy*c+dz*s+11,localZ=-dy*s+dz*c;
        solve(localY,localZ,-1,arms,side*2);
    }
    private void feet(double y,double forwardTarget){for(int side=0;side<2;side++)solve((hips-y)/UNIT,-(forwardTarget-forward)/UNIT,1,legs,side*2);}
    private static void solve(double y,double z,int bend,double[] out,int index){
        double distance=Math.clamp(Math.hypot(y,z),.25,11.98),elbow=bend*Math.acos(Math.clamp((distance*distance-72)/72,-1,1));
        out[index]=Math.atan2(z==0?-.00001:z,y)-Math.atan2(6*Math.sin(elbow),6+6*Math.cos(elbow));out[index+1]=elbow;
    }
}
