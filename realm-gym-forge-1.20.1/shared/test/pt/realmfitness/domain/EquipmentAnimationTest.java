package pt.realmfitness.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import pt.realmfitness.GymLayout;
import pt.realmfitness.client.ExercisePose;

/** Regression checks for real contact geometry, rather than screenshots alone. */
class EquipmentAnimationTest {
    @Test void jointsStayConnectedAndSegmentsKeepTheirLength(){
        for(var e:Exercise.values())for(int frame=0;frame<=100;frame++){
            var p=ExercisePose.sample(e,frame*.7,frame/100.0,Minigame.LEFT,frame%11);
            for(var limb:new ExercisePose.Limb[]{p.arms[0],p.arms[1],p.legs[0],p.legs[1]}){
                assertEquals(6,limb.joint().sub(limb.start()).length(),.00001,e.name());
                assertEquals(6,limb.end().sub(limb.joint()).length(),.00001,e.name());
            }
        }
    }
    @Test void handsReachTheirActualTargetsThroughoutTheStroke(){
        for(var e:Exercise.values())for(int frame=0;frame<=100;frame++){
            var p=ExercisePose.sample(e,frame*.7,frame/100.0,Minigame.LEFT,frame%11);
            for(var arm:p.arms)assertTrue(arm.end().sub(arm.target()).length()*ExercisePose.UNIT<.014,e+" hands outside equipment reach");
        }
    }
    @Test void weightBarsAndRowingHandleMeetTheHands(){
        for(var e:new Exercise[]{Exercise.BENCH,Exercise.SQUAT,Exercise.ROW})for(int frame=0;frame<=100;frame++){
            var p=ExercisePose.sample(e,frame,frame/100.0,0,0);var actor=GymLayout.point(e,"actor");
            for(var arm:p.arms){
                double y=actor.y()+p.hips-arm.end().y()*ExercisePose.UNIT;
                double forward=p.forward-arm.end().z()*ExercisePose.UNIT;
                double z=actor.z()+(e==Exercise.BENCH?-forward:forward);
                assertEquals(actor.y()+p.barY,y,.001,e.name());assertEquals(p.barZ,z,.001,e.name());
            }
        }
    }
    @Test void BenchAndSquatFeetStayOnTheFloorAndBenchFitsThePad(){
        for(var e:new Exercise[]{Exercise.BENCH,Exercise.SQUAT})for(int frame=0;frame<=100;frame++){
            var p=ExercisePose.sample(e,frame,frame/100.0,0,0);
            for(var leg:p.legs){assertEquals(e==Exercise.BENCH?GymLayout.number(e,"foot_y"):0,p.hips-leg.end().y()*ExercisePose.UNIT,.001);assertTrue(leg.end().sub(leg.target()).length()<.001);}
            if(e==Exercise.BENCH)assertTrue(p.hips-2*ExercisePose.UNIT>=GymLayout.number(e,"pad_top"));
        }
    }
}
