package pt.realmfitness.domain;

import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import pt.realmfitness.domain.FitnessLedger.*;

class ConditioningTest {
    private static final long NOW=FitnessRulesTest.NOW,DAY=FitnessLedger.DAY;
    private static final UUID ID=new UUID(51,71);
    private static FitnessLedger ledger(){var l=new FitnessLedger();l.settings.conditioning.enabled=true;var a=l.athlete(ID,"Athlete",NOW);for(var e:Exercise.attributes())a.experience.put(e,threshold(20));return l;}
    private static int threshold(int level){return FitnessLedger.threshold(level);}
    private static Athlete at(FitnessLedger l,long now){return l.athlete(ID,"Athlete",now);}
    private static Settings settings(FitnessLedger l){return FitnessLedger.JSON.fromJson(FitnessLedger.JSON.toJson(l.settings),Settings.class);}
    private static void train(FitnessLedger l,Exercise e,long now,int reps,boolean successful){
        var a=at(l,now);a.restUntil=0;var game=new Minigame(UUID.randomUUID(),e,e.minimumLoad(),reps,991,0);int sequence=0;
        while(!game.done()){if(successful)game.input(sequence++,FitnessRulesTest.controller(game));game.advance();}
        var award=l.finish(ID,"Athlete",game,now,"minecraft:overworld",0,"0,0");
        if(successful)assertTrue(award.result().valid()>=Math.ceil(reps*.6));
    }
    @Test void featureIsDisabledByDefaultAndAllLevelsStayPermanent(){
        var l=new FitnessLedger();assertFalse(l.settings.conditioning.enabled);var a=at(l,NOW);a.experience.put(Exercise.BENCH,threshold(20));
        at(l,NOW+365*DAY);assertEquals(100,l.condition(a,Exercise.BENCH));assertEquals(.10,l.bonus(a,Exercise.BENCH),1e-9);assertTrue(a.condition.isEmpty());
    }
    @Test void sevenDayGraceThenFivePointsPerCompleteDayWithSixtyPercentFloor(){
        var l=ledger();assertEquals(100,l.condition(at(l,NOW+7*DAY),Exercise.BENCH));
        assertEquals(100,l.condition(at(l,NOW+8*DAY-1),Exercise.BENCH));
        assertEquals(95,l.condition(at(l,NOW+8*DAY),Exercise.BENCH));
        assertEquals(80,l.condition(at(l,NOW+11*DAY),Exercise.BENCH));
        assertEquals(60,l.condition(at(l,NOW+15*DAY),Exercise.BENCH));
        assertEquals(60,l.condition(at(l,NOW+500*DAY),Exercise.BENCH));
    }
    @ParameterizedTest @EnumSource(Exercise.class) void everyEquipmentBonusUsesItsAttributeCondition(Exercise e){
        var l=ledger();var a=at(l,NOW+11*DAY);assertEquals(e.maximumBonus*.8,l.bonus(a,e),1e-9);assertEquals(20,l.level(a,e));
    }
    @Test void decayDoesNotChangeExperienceUnlockedLoadsOrPersonalRecords(){
        var l=ledger();train(l,Exercise.BENCH,NOW,8,true);var a=at(l,NOW);a.preferredLoads.put(Exercise.BENCH,Exercise.BENCH.maximumLoad(20));
        var xp=new EnumMap<>(a.experience);var records=new LinkedHashMap<>(a.best);var history=List.copyOf(a.results);
        at(l,NOW+20*DAY);assertEquals(xp,a.experience);assertEquals(records,a.best);assertEquals(history,a.results);
        assertEquals(Exercise.BENCH.maximumLoad(20),l.selectedLoad(a,Exercise.BENCH));assertDoesNotThrow(()->l.ready(a,Exercise.BENCH,l.selectedLoad(a,Exercise.BENCH),8,l.highWater));
    }
    @Test void twoValidEightRepSetsRestoreOnlyTheirAttribute(){
        var l=ledger();var a=at(l,NOW+15*DAY);train(l,Exercise.BENCH,l.highWater,8,true);assertEquals(60,l.condition(a,Exercise.BENCH));
        train(l,Exercise.BENCH,l.highWater+20000,8,true);assertEquals(80,l.condition(a,Exercise.BENCH));assertEquals(60,l.condition(a,Exercise.RUN));
        assertEquals(2,a.condition.get(Exercise.BENCH).sets);assertEquals(a.dailyDay,a.condition.get(Exercise.BENCH).recoveredDay);
    }
    @Test void recoveryIsCappedOncePerAttributePerTrainingDay(){
        var l=ledger();var a=at(l,NOW+15*DAY);
        for(int n=0;n<6;n++)train(l,Exercise.BENCH,NOW+15*DAY+n*20000,8,true);
        assertEquals(80,l.condition(a,Exercise.BENCH));
        train(l,Exercise.BENCH,NOW+16*DAY,8,true);train(l,Exercise.BENCH,NOW+16*DAY+20000,8,true);assertEquals(100,l.condition(a,Exercise.BENCH));
    }
    @Test void setsFromDifferentDaysDoNotCombineIntoMaintenance(){
        var l=ledger();var a=at(l,NOW+15*DAY);train(l,Exercise.BENCH,l.highWater,8,true);train(l,Exercise.BENCH,NOW+16*DAY,8,true);
        assertEquals(60,l.condition(a,Exercise.BENCH));assertEquals(1,a.condition.get(Exercise.BENCH).sets);
        train(l,Exercise.BENCH,l.highWater+20000,8,true);assertEquals(80,l.condition(a,Exercise.BENCH));
    }
    @Test void shortFailedAndUnfinishedSetsNeverMaintainCondition(){
        var l=ledger();var a=at(l,NOW+15*DAY);
        train(l,Exercise.BENCH,l.highWater,4,true);train(l,Exercise.BENCH,l.highWater+20000,7,true);train(l,Exercise.BENCH,l.highWater+20000,8,false);
        assertEquals(0,a.condition.get(Exercise.BENCH).sets);assertEquals(60,l.condition(a,Exercise.BENCH));
        var unfinished=new Minigame(UUID.randomUUID(),Exercise.BENCH,20,8,991,0);
        assertThrows(IllegalArgumentException.class,()->l.finish(ID,"Athlete",unfinished,l.highWater,"minecraft:overworld",0,"0,0"));
    }
    @Test void reachingDailyXpCapStillAllowsValidMaintenance(){
        var l=ledger();l.settings.dailyXp=0;var a=at(l,NOW+15*DAY);train(l,Exercise.BENCH,l.highWater,8,true);train(l,Exercise.BENCH,l.highWater+20000,8,true);
        assertEquals(80,l.condition(a,Exercise.BENCH));assertEquals(0,a.earnedToday);
    }
    @Test void muscleMemoryPausesFurtherLossForTheConfiguredGracePeriod(){
        var l=ledger();var a=at(l,NOW+15*DAY);train(l,Exercise.BENCH,l.highWater,8,true);train(l,Exercise.BENCH,l.highWater+20000,8,true);long maintained=l.highWater;
        at(l,maintained+7*DAY);assertEquals(80,l.condition(a,Exercise.BENCH));at(l,maintained+8*DAY);assertEquals(75,l.condition(a,Exercise.BENCH));
    }
    @Test void permanentModePausesConditionAndReenablingDoesNotChargeDisabledTime(){
        var l=ledger();var a=at(l,NOW+11*DAY);var off=settings(l);off.conditioning.enabled=false;l.configure(off,l.highWater);
        at(l,NOW+200*DAY);assertEquals(100,l.condition(a,Exercise.BENCH));assertEquals(.10,l.bonus(a,Exercise.BENCH),1e-9);
        var on=settings(l);on.conditioning.enabled=true;l.configure(on,l.highWater);assertEquals(80,l.condition(a,Exercise.BENCH));
        at(l,l.highWater+7*DAY);assertEquals(80,l.condition(a,Exercise.BENCH));at(l,l.highWater+DAY);assertEquals(75,l.condition(a,Exercise.BENCH));
    }
    @Test void changingDecayScheduleKeepsCurrentConditionAndStartsANewGracePeriod(){
        var l=ledger();var a=at(l,NOW+11*DAY);var next=settings(l);next.conditioning.graceDays=2;next.conditioning.lossPerDay=3;l.configure(next,l.highWater);
        at(l,l.highWater+2*DAY);assertEquals(80,l.condition(a,Exercise.BENCH));at(l,l.highWater+DAY);assertEquals(77,l.condition(a,Exercise.BENCH));
    }
    @Test void offlineCountingCanBePausedAndDoesNotPersistOnlineSessions(){
        var l=ledger();l.settings.conditioning.countOfflineTime=false;var a=at(l,NOW+50*DAY);assertEquals(100,l.condition(a,Exercise.BENCH));
        l.online(a);at(l,NOW+61*DAY);assertEquals(80,l.condition(a,Exercise.BENCH));l.offline(a,l.highWater);
        var copy=FitnessLedger.fromJson(FitnessLedger.JSON.toJson(l));var restored=at(copy,NOW+161*DAY);assertFalse(restored.conditionClock.online);assertEquals(80,copy.condition(restored,Exercise.BENCH));
        copy.online(restored);at(copy,NOW+162*DAY);assertEquals(75,copy.condition(restored,Exercise.BENCH));
    }
    @Test void reconnectsDoNotErasePreviousOnlineElapsedTime(){
        var l=ledger();l.settings.conditioning.countOfflineTime=false;var a=at(l,NOW);l.online(a);l.offline(a,NOW+6*DAY);
        at(l,NOW+40*DAY);l.online(a);at(l,NOW+45*DAY);assertEquals(80,l.condition(a,Exercise.BENCH));
    }
    @Test void clockRollbackCannotRecoverConditionOrMultiplyDecay(){
        var l=ledger();var a=at(l,NOW+11*DAY);at(l,NOW);assertEquals(80,l.condition(a,Exercise.BENCH));at(l,NOW+11*DAY);assertEquals(80,l.condition(a,Exercise.BENCH));
        at(l,NOW+12*DAY);assertEquals(75,l.condition(a,Exercise.BENCH));
    }
    @Test void repeatedReadsDoNotApplyTheSameDaysLossAgain(){
        var l=ledger();for(int n=0;n<100;n++)assertEquals(80,l.condition(at(l,NOW+11*DAY),Exercise.BENCH));
    }
    @Test void stateAndPartialMaintenancePersistAcrossWorldReloads(){
        var l=ledger();at(l,NOW+15*DAY);train(l,Exercise.BENCH,l.highWater,8,true);String before=FitnessLedger.JSON.toJson(l);
        var copy=FitnessLedger.fromJson(before);assertEquals(before,FitnessLedger.JSON.toJson(copy));train(copy,Exercise.BENCH,copy.highWater+20000,8,true);
        assertEquals(80,copy.condition(copy.athletes.get(ID),Exercise.BENCH));
    }
    @Test void legacySavesReceiveAFullGracePeriodWhenEnabled(){
        var l=new FitnessLedger();var a=at(l,NOW);a.experience.put(Exercise.BENCH,threshold(20));
        var json=JsonParser.parseString(FitnessLedger.JSON.toJson(l)).getAsJsonObject();json.getAsJsonObject("settings").remove("conditioning");
        var athlete=json.getAsJsonObject("athletes").getAsJsonObject(ID.toString());athlete.remove("conditionClock");athlete.remove("condition");
        var copy=FitnessLedger.fromJson(json.toString());var enabled=settings(copy);enabled.conditioning.enabled=true;copy.configure(enabled,NOW+365*DAY);
        var restored=at(copy,copy.highWater);assertEquals(100,copy.condition(restored,Exercise.BENCH));assertEquals(threshold(20),restored.experience.get(Exercise.BENCH));
    }
    @Test void profileReportsEffectiveBonusMaintenanceAndTheNextLoss(){
        var l=ledger();var a=at(l,NOW+11*DAY);train(l,Exercise.BENCH,l.highWater,8,true);
        var json=FitnessLedger.JSON.toJsonTree(l.conditionInfo(a)).getAsJsonObject();var strength=json.getAsJsonObject("attributes").getAsJsonObject("BENCH");
        assertTrue(json.get("enabled").getAsBoolean());assertEquals(80,strength.get("percent").getAsDouble());assertEquals(.08,strength.get("effectiveBonus").getAsDouble(),1e-9);
        assertEquals(1,strength.get("setsToday").getAsInt());assertFalse(strength.get("maintainedToday").getAsBoolean());assertTrue(strength.get("nextLossIn").getAsLong()>0);
    }
    @Test void invalidConditionSettingsAndCorruptSavedValuesAreRejected(){
        var s=new Settings();s.conditioning.graceDays=-1;assertThrows(IllegalArgumentException.class,s::validate);s.conditioning.graceDays=7;s.conditioning.lossPerDay=Double.NaN;assertThrows(IllegalArgumentException.class,s::validate);
        s.conditioning.lossPerDay=5;s.conditioning.minimumPercent=101;assertThrows(IllegalArgumentException.class,s::validate);s.conditioning.minimumPercent=60;s.conditioning.setsRequired=0;assertThrows(IllegalArgumentException.class,s::validate);
        var l=ledger();var a=at(l,NOW);a.condition.get(Exercise.BENCH).percent=Double.NaN;assertThrows(IllegalArgumentException.class,l::validate);
        a.condition.get(Exercise.BENCH).percent=100;a.condition.get(Exercise.BENCH).lossDays=-1;assertThrows(IllegalArgumentException.class,l::validate);
        a.condition.get(Exercise.BENCH).lossDays=0;a.conditionClock.elapsed=-1;assertThrows(IllegalArgumentException.class,l::validate);
    }
}
