package pt.realmfitness.client;

import pt.realmfitness.GymLayout;
import pt.realmfitness.domain.*;

/** Three-dimensional two-bone IK: hands and feet target the actual authored equipment. */
public final class ExercisePose {
    public double hips=.703125,forward,lean,twist,sway,head,headOffset;
    public final Limb[] arms=new Limb[2],legs=new Limb[2];
    public double barY,barZ,seatZ,ropeHalf,ropeForkY;
    public static final double UNIT=.9375/16;
    public record V(double x,double y,double z){
        public V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        public V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
        public V scale(double s){return new V(x*s,y*s,z*s);}
        public double dot(V b){return x*b.x+y*b.y+z*b.z;}
        public double length(){return Math.sqrt(dot(this));}
    }
    public record Limb(V start,V joint,V end,V target){}
    private ExercisePose(){}
    public static ExercisePose sample(Exercise e,double tick,double effort,int action,double age){
        var p=new ExercisePose();var actor=GymLayout.point(e,"actor");double u=Math.clamp(effort,0,1),phase=tick*Math.PI*2/24,beat=Math.sin(phase),strike=age>=0&&age<10?Math.pow(Math.sin(age*Math.PI/10),2):0;
        switch(e){
            case BENCH->{
                p.hips=GymLayout.number(e,"pad_top")+2*UNIT+.012;p.lean=-Math.PI/2;p.head=.04;p.headOffset=-2;
                p.barY=p.hips+.17+.48*u;p.barZ=GymLayout.number(e,"bar_work_z");
                p.hands(p.barY,actor.z()-p.barZ,GymLayout.number(e,"grip_half"),new V(1,.6,0));
                p.feet(GymLayout.number(e,"foot_y"),GymLayout.number(e,"foot_forward"),GymLayout.number(e,"foot_half"));
            }
            case RUN->{
                p.hips=.62+.015*Math.cos(phase*2);p.lean=.10;p.head=-.07;
                for(int i=0;i<2;i++){
                    double swing=i==0?beat:-beat;p.armSwing(i,-.25-swing*.62,-1);
                    p.foot(i,.16*Math.max(0,i==0?-Math.cos(phase):Math.cos(phase)),GymLayout.number(e,"stride")*swing,.135);
                }
            }
            case BOX->{
                p.hips=.66;p.lean=.08;double right=(action&Minigame.RIGHT)!=0?strike:0,left=(action&Minigame.LEFT)!=0?strike:0;
                p.twist=(right-left)*.20;p.sway=((action&Minigame.A)!=0?1:(action&Minigame.D)!=0?-1:0)*strike*.20;
                for(int i=0;i<2;i++){double hit=i==0?right:left;p.hand(i,1.24+(GymLayout.number(e,"punch_y")-1.24)*hit,.25+(GymLayout.number(e,"bag_face_z")-actor.z()-.25)*hit,.22-.04*hit,new V(.3,1,.2));}
                p.foot(0,0,.12,.23);p.foot(1,0,-.12,.23);
            }
            case PULL->{
                p.hips+=.50*u;p.lean=.03;p.sway=Math.sin(tick*.09)*.025;
                p.hands(GymLayout.number(e,"bar_y")-actor.y(),GymLayout.number(e,"bar_z")-actor.z(),GymLayout.number(e,"grip_half"),new V(.6,.3,-1));
                for(int i=0;i<2;i++)p.foot(i,.10+.50*u,-.17,.10);
            }
            case ROW->{
                p.hips=GymLayout.number(e,"seat_top")+2*UNIT+.012;p.forward=GymLayout.number(e,"seat_offset")-GymLayout.number(e,"seat_stroke")*u;
                p.lean=.28-.50*u;p.head=-p.lean*.65;p.seatZ=p.forward;p.barY=p.hips+.32+.08*u;p.barZ=1.55-.60*u;
                p.hands(p.barY,p.barZ-actor.z(),.32,new V(.6,.8,.8));
                p.feet(GymLayout.number(e,"foot_y"),GymLayout.number(e,"foot_z")-actor.z(),GymLayout.number(e,"foot_half"));
            }
            case SQUAT->{
                p.hips=.66-.34*u;p.forward=-.24*u;p.lean=.34*u;p.head=-p.lean*.8;
                p.barY=p.hips+11*UNIT*Math.cos(p.lean)+.07;
                double handForward=p.forward+11*UNIT*Math.sin(p.lean)-.05;p.barZ=actor.z()+handForward;
                p.hands(p.barY,handForward,GymLayout.number(e,"grip_half"),new V(.7,1,-.4));
                p.feet(0,GymLayout.number(e,"foot_forward"),GymLayout.number(e,"foot_half"));
            }
            case LEG_PRESS->{
                p.hips=GymLayout.number(e,"seat_top")+2*UNIT+.012;p.lean=-Math.PI/4;p.head=.08;
                var hand=GymLayout.point(e,"hand_rest");p.hands(hand.y(),hand.z()-actor.z(),GymLayout.number(e,"grip_half"),new V(.8,.6,.5));
                var foot=GymLayout.point(e,"foot_rest");double stroke=GymLayout.number(e,"sled_stroke")*u;
                p.feet(foot.y()+stroke,foot.z()+stroke-actor.z(),GymLayout.number(e,"foot_half"));
            }
            case TRICEPS->{
                p.hips=.703125;p.lean=.025;p.head=-.06;
                double extension=Math.clamp(u/.86,0,1),open=(action&(Minigame.LEFT|Minigame.RIGHT))!=0&&age>=0&&age<60?Math.min(1,age/4)*Math.clamp((u-.15)/.45,0,1):0;
                p.barY=GymLayout.number(e,"hand_top")+(GymLayout.number(e,"hand_bottom")-GymLayout.number(e,"hand_top"))*extension;p.barZ=GymLayout.number(e,"hand_z");
                p.ropeHalf=GymLayout.number(e,"grip_closed")+(GymLayout.number(e,"grip_open")-GymLayout.number(e,"grip_closed"))*open;
                p.ropeForkY=p.barY+Math.sqrt(Math.pow(GymLayout.number(e,"rope_length"),2)-p.ropeHalf*p.ropeHalf);
                p.hands(p.barY,p.barZ-actor.z(),p.ropeHalf,new V(.15,.8,-.3));p.foot(0,0,.07,.16);p.foot(1,0,-.07,.16);
            }
        }
        return p;
    }
    private V rotate(V v){
        double cx=Math.cos(lean),sx=Math.sin(lean),cy=Math.cos(twist),sy=Math.sin(twist),cz=Math.cos(sway),sz=Math.sin(sway);
        double y=v.y*cx-v.z*sx,z=v.y*sx+v.z*cx,x=v.x*cy+z*sy;z=-v.x*sy+z*cy;
        return new V(x*cz-y*sz,x*sz+y*cz,z);
    }
    private V target(int side,double y,double f,double half){return new V((side==0?-half:half)/UNIT,-(y-hips)/UNIT,-(f-forward)/UNIT);}
    private void hands(double y,double f,double half,V pole){for(int i=0;i<2;i++)hand(i,y,f,half,pole);}
    private void hand(int side,double y,double f,double half,V pole){int sign=side==0?-1:1;arms[side]=solve(rotate(new V(sign*5,-11,0)),target(side,y,f,half),new V(sign*pole.x,pole.y,pole.z));}
    private void feet(double y,double f,double half){for(int i=0;i<2;i++)foot(i,y,f,half);}
    private void foot(int side,double y,double f,double half){int sign=side==0?-1:1;legs[side]=solve(new V(sign*1.9,0,0),target(side,y,f,half),new V(sign*.15,0,-1));}
    private void armSwing(int side,double angle,double elbow){int sign=side==0?-1:1;V a=rotate(new V(sign*5,-11,0)),b=a.add(rotate(new V(0,6*Math.cos(angle),6*Math.sin(angle)))),c=b.add(rotate(new V(0,6*Math.cos(angle+elbow),6*Math.sin(angle+elbow))));arms[side]=new Limb(a,b,c,c);}
    private static Limb solve(V start,V goal,V pole){
        V delta=goal.sub(start);double length=Math.max(.001,delta.length()),reach=Math.clamp(length,.01,11.999);V axis=delta.scale(1/length),perp=pole.sub(axis.scale(pole.dot(axis)));
        if(perp.length()<.0001){V fallback=Math.abs(axis.x)<.8?new V(1,0,0):new V(0,0,1);perp=fallback.sub(axis.scale(fallback.dot(axis)));}
        V joint=start.add(axis.scale(reach/2)).add(perp.scale(Math.sqrt(36-reach*reach/4)/perp.length()));
        return new Limb(start,joint,start.add(axis.scale(reach)),goal);
    }
}
