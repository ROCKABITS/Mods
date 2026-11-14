package pt.realmfitness.domain;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class MinigameMechanicsTest {
    private Minigame game(Exercise e,int load,double fatigue,int level){return new Minigame(UUID.randomUUID(),e,load,8,991,fatigue,level);}
    private void play(Minigame g){int sequence=0;while(!g.done()){g.input(sequence++,FitnessRulesTest.controller(g));g.advance();}}
    @Test void holdingSpaceDoesNotMashBenchPress(){var g=game(Exercise.BENCH,20,0,0);for(int n=0;!g.done();n++){g.input(n,Minigame.SPACE);g.advance();}assertEquals(0,g.valid);}
    @Test void benchPressureFallsWhenThePlayerStopsTapping(){var g=game(Exercise.BENCH,20,0,0);g.input(0,Minigame.SPACE);g.advance();double pushed=g.effort;g.input(1,0);for(int i=0;i<8;i++)g.advance();assertTrue(g.effort<pushed);}
    @Test void benchAcceptsShortPressAndReleaseBetweenServerTicks(){var g=game(Exercise.BENCH,20,0,0);g.input(0,Minigame.SPACE);g.input(1,0);g.advance();assertTrue(g.effort>.2);}
    private int tapsForFirstRep(Minigame g){int sequence=0,taps=0;while(g.tick<g.repTicks()&&g.hits==0){int key=g.tick%3==0?Minigame.SPACE:0;if(key!=0)taps++;g.input(sequence++,key);g.advance();}assertEquals(1,g.hits);return taps;}
    @Test void heavierBenchWeightsNeedMoreActualTaps(){assertTrue(tapsForFirstRep(game(Exercise.BENCH,120,0,20))>tapsForFirstRep(game(Exercise.BENCH,20,0,0)));}
    @Test void TrainingMakesTheSameLowWeightEasier(){assertTrue(tapsForFirstRep(game(Exercise.BENCH,20,0,20))<tapsForFirstRep(game(Exercise.BENCH,20,0,0)));}
    @Test void accumulatedFatigueAndLaterRepsIncreasePressureLoss(){var rested=game(Exercise.BENCH,40,0,8);var tired=game(Exercise.BENCH,40,.8,8);assertTrue(tired.benchDrain()>rested.benchDrain());double initial=tired.benchDrain();tired.tick=7*tired.repTicks();assertTrue(tired.benchDrain()>initial);}
    @Test void lowResistanceTimingGetsMoreForgivingWithTraining(){var novice=game(Exercise.BOX,1,0,0);var trained=game(Exercise.BOX,1,0,20);assertTrue(trained.tolerance()>novice.tolerance());assertTrue(game(Exercise.BOX,1,0,1).tolerance()>novice.tolerance());assertTrue(trained.boxPeriod(0)>novice.boxPeriod(0));}
    @Test void boxingMarkerKeepsBouncingUntilThePlayerChoosesToPunch(){var g=game(Exercise.BOX,1,0,0);double low=1,high=0;for(int i=0;i<g.repTicks();i++){low=Math.min(low,g.marker());high=Math.max(high,g.marker());g.advance();}assertTrue(low<.05&&high>.95);assertEquals(0,g.attempted);}
    @Test void boxingFirstMissCannotBeReplacedBySpammingLaterHits(){var g=game(Exercise.BOX,1,0,0);while(Math.abs(g.marker()-.5)<.3)g.advance();int sequence=0;g.input(sequence++,Minigame.LEFT);g.advance();assertTrue(g.locked);while(g.tick<g.repTicks()){g.input(sequence++,Minigame.LEFT);g.input(sequence++,0);g.advance();}assertEquals(0,g.valid);assertEquals(0,g.bestStreak);}
    @Test void boxingCentreHitsBuildAnEightHitPerfectStreak(){var g=game(Exercise.BOX,1,0,0);play(g);assertEquals(8,g.valid);assertEquals(8,g.bestStreak);assertEquals(100,g.accuracy());}
    @Test void boxingMissResetsTheCurrentStreak(){var g=game(Exercise.BOX,1,0,0);int sequence=0;while(g.tick<g.repTicks()){g.input(sequence++,FitnessRulesTest.controller(g));g.advance();}assertEquals(1,g.streak);while(Math.abs(g.marker()-.5)<.3)g.advance();g.input(sequence++,Minigame.RIGHT);g.advance();assertEquals(0,g.streak);assertEquals(1,g.bestStreak);}
    @Test void treadmillCannotBeCompletedUsingOnlyOneFoot(){var g=game(Exercise.RUN,1,0,0);int sequence=0;while(!g.done()){g.input(sequence++,g.tick%4==0?Minigame.A:0);g.advance();}assertEquals(0,g.valid);}
    @Test void pullUpsRequireTheDescentAfterReachingTheTop(){var g=game(Exercise.PULL,1,0,0);int sequence=0;while(!g.done()){g.input(sequence++,Minigame.SPACE|(g.balance>.5?Minigame.A:Minigame.D));g.advance();}assertEquals(0,g.valid);}
    @Test void rowingCannotSkipTheRecoveryStroke(){var g=game(Exercise.ROW,1,0,0);int sequence=0;while(!g.done()){g.input(sequence++,g.step==0&&g.effort<.70?Minigame.W:0);g.advance();}assertEquals(0,g.valid);}
    @Test void holdingTheRowingPullOverchargesEveryStroke(){var g=game(Exercise.ROW,1,0,0);int sequence=0;while(!g.done()){g.input(sequence++,Minigame.W);g.advance();}assertEquals(0,g.valid);}
    @Test void squatsRequireTheBottomPauseBeforeRising(){var g=game(Exercise.SQUAT,20,0,0);int sequence=0;while(!g.done()){g.input(sequence++,g.effort<.73?Minigame.W:Minigame.S);g.advance();}assertEquals(0,g.valid);}
    @ParameterizedTest @EnumSource(Exercise.class) void highestUnlockedLoadRemainsPlayableWithFatigue(Exercise exercise){var g=game(exercise,exercise.maximumLoad(20),.8,20);play(g);assertTrue(g.valid>=6,exercise+": "+g.valid+" valid at "+g.accuracy()+"%");assertTrue(g.accuracy()>=50,exercise.name());}

    @Test void boxingMovesToTheNextTargetSoonAfterThePunch(){var g=game(Exercise.BOX,1,0,0);int calls=0,sequence=0;while(g.completed()==0){g.input(sequence++,FitnessRulesTest.controller(g));g.advance();calls++;}assertTrue(calls<g.repTicks()/2,"Punch feedback waited for the full target timeout");assertEquals(1,g.valid);assertFalse(g.locked);}
    @Test void predictedBenchTapIsRemovedWhenTheServerAcknowledgesIt(){var g=game(Exercise.BENCH,20,0,0);var p=new pt.realmfitness.client.TrainingPresentation();p.configure(g);long now=1_000_000_000L;p.accept(0,.05,.5,1,now);p.acknowledge(-1,"tap");p.press(0,Minigame.SPACE);var predicted=p.frame(Exercise.BENCH,0,false,now+16_000_000);assertTrue(predicted.effort()>.05);p.accept(1,.26,.5,1,now+50_000_000);p.acknowledge(0,"tap");var confirmed=p.frame(Exercise.BENCH,0,false,now+100_000_000);assertTrue(confirmed.effort()<.30,"Acknowledged input was predicted twice");}
}
