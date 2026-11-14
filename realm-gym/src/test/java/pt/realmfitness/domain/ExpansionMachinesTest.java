package pt.realmfitness.domain;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import pt.realmfitness.client.ExercisePose;
import pt.realmfitness.GymLayout;

class ExpansionMachinesTest {
    private static final UUID PLAYER=new UUID(7,9);
    private static final long NOW=FitnessRulesTest.NOW;
    @Test void variantsCreditExistingAttributesAndPreserveSixStatSaveShape(){
        var ledger=new FitnessLedger();ledger.finish(PLAYER,"Athlete",FitnessRulesTest.play(Exercise.LEG_PRESS),NOW,"minecraft:overworld",0,"0,0");
        var a=ledger.athletes.get(PLAYER);assertTrue(a.experience.get(Exercise.SQUAT)>0);assertFalse(a.experience.containsKey(Exercise.LEG_PRESS));assertEquals(6,a.experience.size());
        ledger.finish(PLAYER,"Athlete",FitnessRulesTest.play(Exercise.TRICEPS),NOW+20000,"minecraft:overworld",0,"0,0");
        assertTrue(a.experience.get(Exercise.BENCH)>0);assertFalse(a.experience.containsKey(Exercise.TRICEPS));assertEquals(6,a.fatigue.size());assertDoesNotThrow(()->FitnessLedger.fromJson(FitnessLedger.JSON.toJson(ledger)));
    }
    @Test void changingMachinesDoesNotResetSharedMuscleFatigueOrMultiplyBonuses(){
        var ledger=new FitnessLedger();ledger.finish(PLAYER,"Athlete",FitnessRulesTest.play(Exercise.LEG_PRESS),NOW,"minecraft:overworld",0,"0,0");var a=ledger.athletes.get(PLAYER);double fatigue=a.fatigue.get(Exercise.SQUAT);
        ledger.finish(PLAYER,"Athlete",FitnessRulesTest.play(Exercise.SQUAT),NOW+20000,"minecraft:overworld",0,"0,0");assertTrue(a.fatigue.get(Exercise.SQUAT)>fatigue);
        a.experience.put(Exercise.SQUAT,FitnessLedger.threshold(20));assertEquals(.15,ledger.bonus(a,Exercise.SQUAT),.00001);assertEquals(ledger.bonus(a,Exercise.SQUAT),ledger.bonus(a,Exercise.LEG_PRESS));
    }
    @Test void variantsOfTheSameMuscleDoNotCountAsDifferentDailyTrainingGroups(){
        var ledger=new FitnessLedger();ledger.settings.dailySets=2;ledger.settings.dailyVariety=2;
        ledger.finish(PLAYER,"Athlete",FitnessRulesTest.play(Exercise.LEG_PRESS),NOW,"minecraft:overworld",0,"0,0");var award=ledger.finish(PLAYER,"Athlete",FitnessRulesTest.play(Exercise.SQUAT),NOW+20000,"minecraft:overworld",0,"0,0");assertFalse(award.dailyBonus());assertEquals(Set.of(Exercise.SQUAT),ledger.athletes.get(PLAYER).trainedToday);
    }
    @Test void legacySixAttributeSavesUnlockNewMachineDefaultsWithoutErasingHistory(){
        var ledger=new FitnessLedger();var a=ledger.athlete(PLAYER,"Athlete",NOW);a.preferredLoads.remove(Exercise.LEG_PRESS);a.preferredLoads.remove(Exercise.TRICEPS);a.experience.put(Exercise.BENCH,250);
        var copy=FitnessLedger.fromJson(FitnessLedger.JSON.toJson(ledger));var restored=copy.athletes.get(PLAYER);assertEquals(250,restored.experience.get(Exercise.BENCH));assertEquals(40,copy.selectedLoad(restored,Exercise.LEG_PRESS));assertEquals(5,copy.selectedLoad(restored,Exercise.TRICEPS));
    }
    @Test void loadsUseEachMachinesOwnUnlocksAndStepSize(){
        var ledger=new FitnessLedger();var a=ledger.athlete(PLAYER,"Athlete",NOW);a.experience.put(Exercise.SQUAT,FitnessLedger.threshold(2));assertEquals(50,ledger.cycleLoad(a,Exercise.LEG_PRESS));assertEquals(60,ledger.cycleLoad(a,Exercise.LEG_PRESS));assertEquals(40,ledger.cycleLoad(a,Exercise.LEG_PRESS));
        a.experience.put(Exercise.BENCH,FitnessLedger.threshold(2));assertEquals(10,ledger.cycleLoad(a,Exercise.TRICEPS));assertEquals(5,ledger.cycleLoad(a,Exercise.TRICEPS));assertEquals(240,Exercise.LEG_PRESS.maximumLoad(20));assertEquals(55,Exercise.TRICEPS.maximumLoad(20));
    }
    @Test void recordsRemainSpecificToEquipmentEvenWhenExperienceIsShared(){
        var ledger=new FitnessLedger();ledger.finish(PLAYER,"Athlete",FitnessRulesTest.play(Exercise.TRICEPS),NOW,"minecraft:overworld",0,"0,0");assertEquals(1,ledger.leaders(Exercise.TRICEPS,5,8,"all","minecraft:overworld","",NOW).size());assertTrue(ledger.leaders(Exercise.BENCH,20,8,"all","minecraft:overworld","",NOW).isEmpty());
    }
    private Minigame held(Exercise exercise,int keys){var game=new Minigame(UUID.randomUUID(),exercise,exercise.minimumLoad(),8,91,0);for(int i=0;!game.done();i++){game.input(i,keys);game.advance();}return game;}
    @Test void holdingBothLegPressPedalsContinuouslyCannotFinishRepetitions(){assertEquals(0,held(Exercise.LEG_PRESS,Minigame.A|Minigame.D).valid);}
    @Test void oneLegAloneCannotDriveTheSled(){assertEquals(0,held(Exercise.LEG_PRESS,Minigame.A).valid);}
    @Test void tricepsNeedsTheTimedContractionAndRelease(){assertEquals(0,held(Exercise.TRICEPS,Minigame.SPACE).valid);assertEquals(0,held(Exercise.TRICEPS,Minigame.SPACE|Minigame.LEFT).valid);}
    @Test void legPressFeetStayOnTheMovingPlatformAtEveryExtension(){
        var e=Exercise.LEG_PRESS;var actor=GymLayout.point(e,"actor");var foot=GymLayout.point(e,"foot_rest");for(int i=0;i<=100;i++){double u=i/100.0;var pose=ExercisePose.sample(e,i,u,0,0);for(var leg:pose.legs){assertEquals(foot.y()+GymLayout.number(e,"sled_stroke")*u,pose.hips-leg.end().y()*ExercisePose.UNIT,.001);assertEquals(foot.z()+GymLayout.number(e,"sled_stroke")*u,actor.z()+pose.forward-leg.end().z()*ExercisePose.UNIT,.001);assertTrue(leg.end().sub(leg.target()).length()<.001);}}
    }
    @Test void ropeLengthAndHandContactAreMaintainedWhileSplittingTheAttachment(){
        var e=Exercise.TRICEPS;assertEquals(.5,GymLayout.scalar(e,"stack_ratio"),.000001);for(int i=0;i<=100;i++){var pose=ExercisePose.sample(e,i,i/100.0,Minigame.LEFT,i%45);double length=Math.hypot(pose.ropeHalf,pose.ropeForkY-pose.barY);assertEquals(GymLayout.number(e,"rope_length"),length,.000001);for(var arm:pose.arms)assertTrue(arm.end().sub(arm.target()).length()*ExercisePose.UNIT<.014);}
    }
}
