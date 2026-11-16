package pt.realmfitness.domain;

import com.google.gson.JsonObject;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import pt.realmfitness.domain.FitnessLedger.*;

class ConditioningTimeTest {
    private static final long NOW=FitnessRulesTest.NOW,DAY=FitnessLedger.DAY,TICKS=Conditioning.MINECRAFT_DAY_TICKS;
    private static final UUID ID=new UUID(19,83);
    private static FitnessLedger ledger(Conditioning.TimeMode mode){
        var l=new FitnessLedger();l.settings.conditioning.enabled=true;l.settings.conditioning.timeMode=mode;l.observeMinecraft(0);
        var a=l.athlete(ID,"ClockAthlete",NOW);for(var e:Exercise.attributes())a.experience.put(e,FitnessLedger.threshold(20));return l;
    }
    private static Athlete at(FitnessLedger l,long now,long ticks){l.observeMinecraft(ticks);return l.athlete(ID,"ClockAthlete",now);}
    private static Settings settings(FitnessLedger l){return FitnessLedger.JSON.fromJson(FitnessLedger.JSON.toJson(l.settings),Settings.class);}
    private static void train(FitnessLedger l,long now){
        var a=l.athlete(ID,"ClockAthlete",now);a.restUntil=0;
        var game=new Minigame(UUID.randomUUID(),Exercise.BENCH,20,8,991,0);int sequence=0;
        while(!game.done()){game.input(sequence++,FitnessRulesTest.controller(game));game.advance();}
        assertTrue(l.finish(ID,"ClockAthlete",game,now,"minecraft:overworld",0,"0,0").result().valid()>=5);
    }
    @Test void realModeIgnoresWorldTicksAndRetainsTwentyFourHourDays(){
        var l=ledger(Conditioning.TimeMode.REAL);assertEquals(100,l.condition(at(l,NOW,10000*TICKS),Exercise.BENCH));
        assertEquals(95,l.condition(at(l,NOW+8*DAY,10000*TICKS),Exercise.BENCH));
    }
    @Test void minecraftModeIgnoresRealTimeAndUsesExactTwentyFourThousandTickDays(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);
        assertEquals(100,l.condition(at(l,NOW+365*DAY,0),Exercise.BENCH));
        assertEquals(100,l.condition(at(l,NOW+365*DAY,8*TICKS-1),Exercise.BENCH));
        assertEquals(95,l.condition(at(l,NOW+365*DAY,8*TICKS),Exercise.BENCH));
        assertEquals(80,l.condition(at(l,NOW+365*DAY,11*TICKS),Exercise.BENCH));
        assertEquals(60,l.condition(at(l,NOW+365*DAY,15*TICKS),Exercise.BENCH));
    }
    @Test void configurableMinecraftGraceAndLossRemainGradual(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);var s=settings(l);s.conditioning.graceDays=2;s.conditioning.lossPerDay=3;l.configure(s,NOW);
        assertEquals(100,l.condition(at(l,NOW,3*TICKS-1),Exercise.BENCH));
        assertEquals(97,l.condition(at(l,NOW,3*TICKS),Exercise.BENCH));
        assertEquals(94,l.condition(at(l,NOW,4*TICKS),Exercise.BENCH));
    }
    @Test void offlineMinecraftWorldTimeCountsWhenEnabledAndPersistsAcrossRestart(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);l.offline(at(l,NOW,0),NOW);
        var restored=FitnessLedger.fromJson(FitnessLedger.JSON.toJson(l));
        assertEquals(95,restored.condition(at(restored,NOW+DAY,8*TICKS),Exercise.BENCH));
        assertFalse(restored.athletes.get(ID).conditionClock.online);
    }
    @Test void onlineOnlyMinecraftTimePausesWhenTheAthleteDisconnects(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);var s=settings(l);s.conditioning.countOfflineTime=false;l.configure(s,NOW);
        var a=at(l,NOW,0);l.online(a);at(l,NOW+1,4*TICKS);l.offline(a,NOW+1);
        var restored=FitnessLedger.fromJson(FitnessLedger.JSON.toJson(l));a=at(restored,NOW+DAY,100*TICKS);assertEquals(100,restored.condition(a,Exercise.BENCH));
        restored.online(a);assertEquals(95,restored.condition(at(restored,NOW+DAY+1,104*TICKS),Exercise.BENCH));
    }
    @Test void closedOrPausedWorldDoesNotAccumulateMinecraftTime(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);at(l,NOW,4*TICKS);
        var restored=FitnessLedger.fromJson(FitnessLedger.JSON.toJson(l));
        assertEquals(100,restored.condition(at(restored,NOW+300*DAY,4*TICKS),Exercise.BENCH));
    }
    @Test void worldClockRollbackCannotMultiplyDecay(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);assertEquals(95,l.condition(at(l,NOW,8*TICKS),Exercise.BENCH));
        assertEquals(95,l.condition(at(l,NOW+1,0),Exercise.BENCH));
        assertEquals(95,l.condition(at(l,NOW+2,8*TICKS),Exercise.BENCH));
        assertEquals(90,l.condition(at(l,NOW+3,9*TICKS),Exercise.BENCH));
    }
    @Test void newAthletesDoNotInheritAnOldWorldsInactivity(){
        var l=new FitnessLedger();l.settings.conditioning.enabled=true;l.settings.conditioning.timeMode=Conditioning.TimeMode.MINECRAFT;l.observeMinecraft(10000*TICKS);
        var a=l.athlete(ID,"ClockAthlete",NOW);assertEquals(100,l.condition(a,Exercise.BENCH));
    }
    @Test void modeChangesPreserveConditionAndProgressAndStartAFreshGracePeriod(){
        var l=ledger(Conditioning.TimeMode.REAL);var a=at(l,NOW+11*DAY,0);assertEquals(80,l.condition(a,Exercise.BENCH));
        var s=settings(l);s.conditioning.timeMode=Conditioning.TimeMode.MINECRAFT;l.configure(s,l.highWater);
        assertEquals(80,l.condition(at(l,l.highWater,7*TICKS),Exercise.BENCH));
        assertEquals(75,l.condition(at(l,l.highWater,8*TICKS),Exercise.BENCH));
        l.configure(settings(l),l.highWater);assertEquals(70,l.condition(at(l,l.highWater,9*TICKS),Exercise.BENCH));
        s=settings(l);s.conditioning.timeMode=Conditioning.TimeMode.REAL;l.configure(s,l.highWater);
        assertEquals(70,l.condition(at(l,NOW+18*DAY,9*TICKS),Exercise.BENCH));
        assertEquals(65,l.condition(at(l,NOW+19*DAY,9*TICKS),Exercise.BENCH));assertEquals(20,l.level(a,Exercise.BENCH));
    }
    @Test void maintenanceAndRecoveryFollowMinecraftDaysWithoutResettingDailyXp(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);var a=at(l,NOW,0);a.condition.get(Exercise.BENCH).percent=60;
        train(l,NOW);train(l,NOW);assertEquals(80,l.condition(a,Exercise.BENCH));
        train(l,NOW);train(l,NOW);assertEquals(80,l.condition(a,Exercise.BENCH));long daily=a.dailyDay;
        at(l,NOW,TICKS);train(l,NOW);train(l,NOW);assertEquals(100,l.condition(a,Exercise.BENCH));
        assertEquals(daily,a.dailyDay);assertEquals(6,a.validToday);
    }
    @Test void partialMaintenanceDoesNotCarryIntoAnotherMinecraftDay(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);var a=at(l,NOW,0);a.condition.get(Exercise.BENCH).percent=60;
        train(l,NOW);at(l,NOW,TICKS);train(l,NOW);assertEquals(60,l.condition(a,Exercise.BENCH));
        train(l,NOW);assertEquals(80,l.condition(a,Exercise.BENCH));
    }
    @Test void switchingClockClearsIncompatibleMaintenanceDayNumbers(){
        var l=ledger(Conditioning.TimeMode.REAL);var a=at(l,NOW,0);a.condition.get(Exercise.BENCH).percent=60;
        train(l,NOW);train(l,NOW);assertEquals(80,l.condition(a,Exercise.BENCH));
        var s=settings(l);s.conditioning.timeMode=Conditioning.TimeMode.MINECRAFT;l.configure(s,NOW);
        train(l,NOW);train(l,NOW);assertEquals(100,l.condition(a,Exercise.BENCH));
    }
    @Test void legacyProfilesAndSettingsRetainRealTimeModeAndSavedCondition(){
        var l=ledger(Conditioning.TimeMode.REAL);var a=at(l,NOW+11*DAY,0);JsonObject json=FitnessLedger.JSON.toJsonTree(l).getAsJsonObject();
        json.remove("minecraftHighWater");json.getAsJsonObject("settings").getAsJsonObject("conditioning").remove("timeMode");
        json.getAsJsonObject("athletes").getAsJsonObject(ID.toString()).getAsJsonObject("conditionClock").remove("minecraftObservedAt");
        var restored=FitnessLedger.fromJson(json.toString());assertEquals(Conditioning.TimeMode.REAL,restored.settings.conditioning.timeMode);
        assertEquals(80,restored.condition(restored.athletes.get(ID),Exercise.BENCH));assertEquals(l.level(a,Exercise.BENCH),restored.level(restored.athletes.get(ID),Exercise.BENCH));
    }
    @Test void longLivedMinecraftClocksMayExceedTheRealTimestampWithoutCorruptingSaves(){
        var l=new FitnessLedger();l.settings.conditioning.enabled=true;l.settings.conditioning.timeMode=Conditioning.TimeMode.MINECRAFT;l.observeMinecraft(0);
        var a=l.athlete(ID,"ClockAthlete",1);l.observeMinecraft(1000*TICKS);l.athlete(ID,"ClockAthlete",2);
        assertTrue(a.conditionClock.elapsed>l.highWater);assertDoesNotThrow(l::validate);assertDoesNotThrow(()->FitnessLedger.fromJson(FitnessLedger.JSON.toJson(l)));
    }
    @Test void worldClockOverflowSaturatesAndInvalidFutureBaselinesAreRejected(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);var a=at(l,NOW,Long.MAX_VALUE);assertEquals(Long.MAX_VALUE,a.conditionClock.elapsed);assertEquals(60,l.condition(a,Exercise.BENCH));assertDoesNotThrow(l::validate);
        a.conditionClock.minecraftObservedAt=Long.MAX_VALUE;l.minecraftHighWater=0;assertThrows(IllegalArgumentException.class,l::validate);
    }
    @Test void profileExplainsTheChosenClockAndItsMaintenanceWindow(){
        var l=ledger(Conditioning.TimeMode.MINECRAFT);var a=at(l,NOW,0);train(l,NOW);
        var info=FitnessLedger.JSON.toJsonTree(l.conditionInfo(a)).getAsJsonObject();assertEquals("minecraft",info.get("timeMode").getAsString());
        assertEquals(1,info.getAsJsonObject("attributes").getAsJsonObject("BENCH").get("setsToday").getAsInt());
    }
}
