package pt.realmfitness.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryTest {
    private static final long NOW=FitnessRulesTest.NOW;
    private static final UUID PLAYER=new UUID(1,2);

    private FitnessLedger.Athlete athlete(FitnessLedger ledger,long now){
        return ledger.athlete(PLAYER,"Athlete",now);
    }

    private FitnessLedger.Award set(FitnessLedger ledger,Exercise exercise,long now){
        var game=FitnessRulesTest.play(exercise);
        return ledger.finish(PLAYER,"Athlete",game,now,"minecraft:overworld",0,"0,0");
    }

    @Test void supplementRequiresAQualifiedSetAndTargetsTheLastTrainedGroup(){
        var ledger=new FitnessLedger();
        var athlete=athlete(ledger,NOW);
        assertEquals(Recovery.Status.NEEDS_SET,Recovery.snapshot(athlete,ledger.settings,NOW).status());
        set(ledger,Exercise.BENCH,NOW);
        set(ledger,Exercise.ROW,NOW+20_000);
        double strength=athlete.fatigue.get(Exercise.BENCH);
        var result=Recovery.consume(athlete,ledger.settings,NOW+20_000,0);
        assertNotNull(result);
        assertEquals(Exercise.ROW,result.attribute());
        assertEquals(.10,result.before()-result.after(),1e-9);
        assertEquals(strength,athlete.fatigue.get(Exercise.BENCH));
        assertNull(Recovery.consume(athlete,ledger.settings,NOW+20_000,1));
    }

    @Test void recoveryLimitSurvivesSerializationAndExpiresAtTheTrainingDayBoundary(){
        var ledger=new FitnessLedger();
        set(ledger,Exercise.BENCH,NOW);
        var athlete=athlete(ledger,NOW);
        assertNotNull(Recovery.consume(athlete,ledger.settings,NOW,0));
        var restored=FitnessLedger.fromJson(FitnessLedger.JSON.toJson(ledger));
        assertEquals(Recovery.Status.USED,Recovery.snapshot(athlete(restored,NOW),restored.settings,NOW).status());
        long tomorrow=restored.settings.resetAt(NOW);
        assertEquals(Recovery.Status.NEEDS_SET,Recovery.snapshot(athlete(restored,tomorrow),restored.settings,tomorrow).status());
        set(restored,Exercise.BENCH,tomorrow);
        assertNotNull(Recovery.consume(athlete(restored,tomorrow),restored.settings,tomorrow,0));
    }

    @Test void optionalNutritionImprovesRecoveryWithinTheConfiguredLimit(){
        var ledger=new FitnessLedger();
        ledger.settings.fatiguePerSet=.25;
        set(ledger,Exercise.BENCH,NOW);
        var athlete=athlete(ledger,NOW);
        ledger.settings.supplements.dynamicNutrition=true;
        ledger.settings.supplements.proteinBonusMaximum=.08;
        var result=Recovery.consume(athlete,ledger.settings,NOW,.75);
        assertEquals(.16,result.before()-result.after(),1e-9);
    }

    @Test void exerciseXpMultiplierLeavesCapsAndQualifyingSetsInForce(){
        var ledger=new FitnessLedger();
        ledger.settings.xpMultipliers.put(Exercise.BENCH,0.0);
        ledger.settings.xpMultipliers.put(Exercise.ROW,3.0);
        ledger.settings.dailyXp=20;
        assertEquals(0,set(ledger,Exercise.BENCH,NOW).gained());
        assertEquals(20,set(ledger,Exercise.ROW,NOW+20_000).gained());
        assertEquals(2,athlete(ledger,NOW+20_000).validToday);
    }
}
