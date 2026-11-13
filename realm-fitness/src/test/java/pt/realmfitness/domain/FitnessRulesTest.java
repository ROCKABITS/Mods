package pt.realmfitness.domain;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import pt.realmfitness.domain.FitnessLedger.*;

class FitnessRulesTest {
    static final long NOW=Instant.parse("2026-11-13T12:00:00Z").toEpochMilli();
    static final UUID PLAYER=new UUID(1,2),OTHER=new UUID(3,4);
    static int controller(Minigame g){
        if(g.exercise==Exercise.RUN||g.exercise==Exercise.BOX||g.exercise==Exercise.ROW)return g.tick%g.pulseLength()==0?g.expectedKey(g.tick/g.pulseLength()):0;
        int bit=g.exercise==Exercise.SQUAT?(g.effort<g.target()?Minigame.W:Minigame.S):(g.effort<g.target()?Minigame.SPACE:0);
        if(g.exercise==Exercise.PULL)bit|=g.balance>.5?Minigame.A:Minigame.D;
        return bit;
    }
    static Minigame play(Exercise e){Minigame g=new Minigame(UUID.randomUUID(),e,e.minimumLoad(),8,991,.0);int n=0;while(!g.done()){g.input(n++,controller(g));g.advance();}return g;}
    private FitnessLedger ledger(){return new FitnessLedger();}
    private Athlete athlete(FitnessLedger l){return l.athlete(PLAYER,"Rock",NOW);}
    private Award award(FitnessLedger l,Exercise e,long now){return l.finish(PLAYER,"Rock",play(e),now,"minecraft:overworld",12,"0,0");}

    @ParameterizedTest @EnumSource(Exercise.class) void eachMinigameCanBeCompletedByItsDocumentedControls(Exercise e){var g=play(e);assertEquals(8,g.valid,e.name());assertTrue(g.accuracy()>=70,e+" accuracy "+g.accuracy());}
    @ParameterizedTest @EnumSource(Exercise.class) void doingNothingNeverCompletesASet(Exercise e){var g=new Minigame(UUID.randomUUID(),e,e.minimumLoad(),8,991,0);while(!g.done())g.advance();assertTrue(g.valid<5,e+" idle valid "+g.valid);var l=ledger();var a=athlete(l);var r=l.finish(PLAYER,"Rock",g,NOW,"minecraft:overworld",12,"0,0");assertEquals(0,r.gained());assertEquals(0,a.validToday);}
    @Test void holdingAllButtonsCannotSucceedAtBoxing(){var g=new Minigame(UUID.randomUUID(),Exercise.BOX,1,8,9,0);for(int n=0;!g.done();n++){g.input(n,127);g.advance();}assertEquals(0,g.valid);}
    @Test void aQuickTapIsNotLostWhenReleasedBeforeTheNextServerTick(){var g=new Minigame(UUID.randomUUID(),Exercise.RUN,1,8,9,0);g.input(0,Minigame.A);g.input(1,0);g.advance();assertEquals(1,g.hits);}
    @Test void staleSequenceAndInvalidKeysCannotReplaceAcceptedInput(){var g=new Minigame(UUID.randomUUID(),Exercise.BENCH,20,4,0,0);g.input(0,Minigame.SPACE);g.input(0,0);g.input(1,128);g.advance();assertTrue(g.effort>.5);}
    @Test void unfinishedSetCannotAwardExperience(){var l=ledger();assertThrows(IllegalArgumentException.class,()->l.finish(PLAYER,"Rock",new Minigame(UUID.randomUUID(),Exercise.RUN,1,8,9,0),NOW,"minecraft:overworld",0,"0,0"));}
    @Test void repeatResultNeverPaysTwice(){var l=ledger();var g=play(Exercise.BENCH);l.finish(PLAYER,"Rock",g,NOW,"minecraft:overworld",0,"0,0");int before=athlete(l).experience.get(Exercise.BENCH);assertThrows(IllegalArgumentException.class,()->l.finish(PLAYER,"Rock",g,NOW+20000,"minecraft:overworld",0,"0,0"));assertEquals(before,athlete(l).experience.get(Exercise.BENCH));}
    @Test void dailyXpCeilingSurvivesRepeatedTraining(){var l=ledger();l.settings.dailyXp=35;for(int i=0;i<8;i++)award(l,Exercise.BENCH,NOW+i*20000L);assertEquals(35,athlete(l).earnedToday);assertEquals(35,athlete(l).experience.get(Exercise.BENCH));}
    @Test void dailyBonusRequiresSixSetsAndThreeDifferentExercises(){var l=ledger();for(int i=0;i<5;i++)assertFalse(award(l,Exercise.BENCH,NOW+i*20000L).dailyBonus());assertFalse(award(l,Exercise.RUN,NOW+100000).dailyBonus());assertTrue(award(l,Exercise.BOX,NOW+120000).dailyBonus());assertFalse(award(l,Exercise.BOX,NOW+140000).dailyBonus());assertEquals(1,athlete(l).completedDays.size());}
    @Test void nextUtcDayResetsDailyAllowanceAndKeepsAllLevels(){var l=ledger();award(l,Exercise.BENCH,NOW);int xp=athlete(l).experience.get(Exercise.BENCH);var a=l.athlete(PLAYER,"Rock",NOW+FitnessLedger.DAY);assertEquals(xp,a.experience.get(Exercise.BENCH));assertEquals(0,a.earnedToday);}
    @Test void clockRollbackCannotResetDailyAllowance(){var l=ledger();award(l,Exercise.BENCH,NOW);int before=athlete(l).earnedToday;l.athlete(PLAYER,"Rock",NOW-FitnessLedger.DAY);assertEquals(before,athlete(l).earnedToday);assertEquals(NOW,l.highWater);}
    @Test void configuredResetMinuteUsesStoredRealDay(){var l=ledger();l.settings.resetMinute=720;long before=NOW-1;var a=l.athlete(PLAYER,"Rock",before);a.earnedToday=10;l.athlete(PLAYER,"Rock",NOW);assertEquals(0,a.earnedToday);}
    @Test void fatigueRecoversWhileExperienceRemainsPermanent(){var l=ledger();award(l,Exercise.BENCH,NOW);var a=athlete(l);assertTrue(a.fatigue.get(Exercise.BENCH)>0);int xp=a.experience.get(Exercise.BENCH);l.athlete(PLAYER,"Rock",NOW+1800000);assertEquals(0,a.fatigue.get(Exercise.BENCH));assertEquals(xp,a.experience.get(Exercise.BENCH));}
    @Test void onlyTheMatchingPhysicalExerciseCanAdvanceTheActivePlan(){var l=ledger();var a=athlete(l);UUID id=UUID.randomUUID();l.saveRoutine(a,new Routine(id,"A",0,List.of(new Step(Exercise.BENCH,2,8,20,30))));l.begin(a,id,NOW);assertThrows(IllegalArgumentException.class,()->l.ready(a,Exercise.BOX,1,8,NOW));assertThrows(IllegalArgumentException.class,()->l.ready(a,Exercise.BENCH,20,9,NOW));award(l,Exercise.BENCH,NOW);assertEquals(1,a.workout.completed);assertThrows(IllegalArgumentException.class,()->l.ready(a,Exercise.BENCH,20,8,NOW+1000));assertTrue(award(l,Exercise.BENCH,NOW+30000).workoutCompleted());}
    @Test void editingRoutinePreservesRunningWorkoutSnapshot(){var l=ledger();var a=athlete(l);UUID id=UUID.randomUUID();l.saveRoutine(a,new Routine(id,"A",1,List.of(new Step(Exercise.BENCH,2,8,20,30))));l.begin(a,id,NOW);l.saveRoutine(a,new Routine(id,"Changed",2,List.of(new Step(Exercise.BOX,1,4,1,5))));assertEquals(Exercise.BENCH,a.workout.next().exercise());assertEquals("A",a.workout.plan.name());}
    @Test void lockedWeightsCannotStartViaRoutine(){var l=ledger();var a=athlete(l);UUID id=UUID.randomUUID();l.saveRoutine(a,new Routine(id,"Heavy",0,List.of(new Step(Exercise.BENCH,2,8,120,30))));assertThrows(IllegalArgumentException.class,()->l.begin(a,id,NOW));}
    @Test void oversizedAndEmptyPlansAreRejected(){assertThrows(IllegalArgumentException.class,()->new Routine(UUID.randomUUID(),"",0,List.of()));assertThrows(IllegalArgumentException.class,()->new Step(Exercise.BENCH,6,8,20,30));assertThrows(IllegalArgumentException.class,()->new Routine(UUID.randomUUID(),"Too long",0,Collections.nCopies(8,new Step(Exercise.BENCH,5,8,20,30))));}
    @Test void planLimitPreventsUnboundedStoredRoutines(){var l=ledger();var a=athlete(l);for(int i=0;i<12;i++)l.saveRoutine(a,new Routine(UUID.randomUUID(),"A",0,List.of(new Step(Exercise.RUN,1,8,1,5))));assertThrows(IllegalArgumentException.class,()->l.saveRoutine(a,new Routine(UUID.randomUUID(),"B",0,List.of(new Step(Exercise.BOX,1,8,1,5)))));}
    @Test void independentAthletesNeverShareExperienceOrPlans(){var l=ledger();award(l,Exercise.BENCH,NOW);var other=l.athlete(OTHER,"Other",NOW);assertEquals(0,other.experience.get(Exercise.BENCH));assertTrue(other.routines.isEmpty());}
    @Test void fullJsonRoundTripKeepsRecordsRoutineAndPermanentProgress(){var l=ledger();var a=athlete(l);l.saveRoutine(a,new Routine(UUID.randomUUID(),"Routine",3,List.of(new Step(Exercise.BENCH,1,8,20,30))));award(l,Exercise.BENCH,NOW);String before=FitnessLedger.JSON.toJson(l);var restored=FitnessLedger.fromJson(before);assertEquals(before,FitnessLedger.JSON.toJson(restored));}
    @Test void corruptProgressAndInvalidSettingsAreRejected(){var l=ledger();athlete(l).experience.put(Exercise.BENCH,-1);assertThrows(IllegalArgumentException.class,()->FitnessLedger.fromJson(FitnessLedger.JSON.toJson(l)));var s=new Settings();s.bonuses.put(Exercise.RUN,Double.NaN);assertThrows(IllegalArgumentException.class,s::validate);}
    @Test void levelThresholdsAndMaximumBonusesAreBounded(){var l=ledger();var a=athlete(l);for(var e:Exercise.values()){a.experience.put(e,FitnessLedger.threshold(20));assertEquals(20,l.level(a,e));assertEquals(e.maximumBonus,l.bonus(a,e),.00001);}assertEquals(0,FitnessLedger.level(0));assertEquals(1,FitnessLedger.level(100));}
    @Test void boardsCompareOnlyEquivalentCategoriesAndKeepOneEntryPerAthlete(){var l=ledger();award(l,Exercise.BENCH,NOW);award(l,Exercise.BENCH,NOW+20000);assertEquals(1,l.leaders(Exercise.BENCH,20,8,"all","minecraft:overworld","",NOW+30000).size());assertTrue(l.leaders(Exercise.BENCH,25,8,"all","minecraft:overworld","",NOW).isEmpty());assertTrue(l.leaders(Exercise.BENCH,20,9,"all","minecraft:overworld","",NOW).isEmpty());}
    @Test void gymFilteringDoesNotMixDimensionsOrZones(){var l=ledger();award(l,Exercise.BENCH,NOW);assertEquals(1,l.leaders(Exercise.BENCH,20,8,"all","minecraft:overworld","0,0",NOW).size());assertTrue(l.leaders(Exercise.BENCH,20,8,"all","minecraft:the_nether","0,0",NOW).isEmpty());assertTrue(l.leaders(Exercise.BENCH,20,8,"all","minecraft:overworld","1,0",NOW).isEmpty());}
    @Test void allTimeGymRecordsSurviveHistoryRetention(){var l=ledger();award(l,Exercise.BENCH,NOW);l.athlete(PLAYER,"Rock",NOW+80*FitnessLedger.DAY);assertTrue(athlete(l).results.isEmpty());assertEquals(1,l.leaders(Exercise.BENCH,20,8,"all","minecraft:overworld","0,0",l.highWater).size());assertTrue(l.leaders(Exercise.BENCH,20,8,"month","minecraft:overworld","0,0",l.highWater).isEmpty());}
}
