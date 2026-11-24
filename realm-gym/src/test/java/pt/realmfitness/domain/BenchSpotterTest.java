package pt.realmfitness.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchSpotterTest {
    private static final UUID ATHLETE=UUID.fromString("79c61468-1af5-4fc9-a51f-35c9add7bb07");

    @Test void onlyAFreshSpotterPressAfterTheLifterCallsCanRescueARep(){
        var set=new Minigame(UUID.randomUUID(),Exercise.BENCH,20,4,1,0);
        set.setSpotterPresent(true);
        int sequence=stall(set,0);
        set.spotterInput(0,Minigame.SPACE);
        set.advance();
        assertEquals(0,set.assistedRepetitions());
        set.input(sequence,Minigame.W);
        set.advance();
        assertTrue(set.helpRequested());
        set.spotterInput(1,Minigame.SPACE);
        set.advance();
        assertEquals(0,set.assistedRepetitions(),"Holding Space in advance must not catch the bar");
        set.spotterInput(2,0);
        set.spotterInput(2,Minigame.SPACE);
        set.advance();
        assertEquals(0,set.assistedRepetitions(),"A repeated packet sequence must not catch the bar");
        set.spotterInput(3,Minigame.SPACE);
        set.advance();
        assertEquals(1,set.assistedRepetitions());
        assertFalse(set.helpRequested());
        while(set.completed()==0)set.advance();
        assertEquals(0,set.valid);
        assertEquals("assisted",set.feedback);
    }

    @Test void requestExpiresAndLeavingClearsAnyQueuedCatch(){
        var set=new Minigame(UUID.randomUUID(),Exercise.BENCH,20,4,2,0);
        set.setSpotterPresent(true);
        int sequence=stall(set,0);
        set.input(sequence++,Minigame.W);
        set.advance();
        while(set.completed()==0)set.advance();
        assertFalse(set.helpRequested());
        sequence=stall(set,sequence);
        set.input(sequence,Minigame.W);set.advance();
        set.spotterInput(0,Minigame.SPACE);
        set.setSpotterPresent(false);set.advance();
        assertFalse(set.helpRequested());
        assertEquals(0,set.assistedRepetitions());
    }

    @Test void assistedSetKeepsItsHistoryAndCreditForSoloRepsButDoesNotRank(){
        var set=new Minigame(UUID.randomUUID(),Exercise.BENCH,20,8,3,0);
        set.setSpotterPresent(true);
        int sequence=stall(set,0);
        set.input(sequence++,Minigame.W);set.advance();
        set.spotterInput(0,Minigame.SPACE);set.advance();
        while(set.completed()==0)set.advance();
        for(int seq=sequence;!set.done();seq++){
            set.input(seq,set.phase()%2==0?Minigame.SPACE:0);set.advance();
        }
        assertEquals(7,set.valid);
        long now=FitnessRulesTest.NOW;
        var ledger=new FitnessLedger();
        var award=ledger.finish(ATHLETE,"Bench athlete",set,now,"minecraft:overworld",0,"0,0");
        assertTrue(award.gained()>0);
        assertEquals(1,award.result().assisted());
        var restored=FitnessLedger.fromJson(FitnessLedger.JSON.toJson(ledger));
        assertEquals(1,restored.athletes.get(ATHLETE).results.get(0).assisted());
        assertTrue(restored.athletes.get(ATHLETE).best.isEmpty());
        for(String period:new String[]{"all","week","month"})
            assertTrue(restored.leaders(Exercise.BENCH,20,8,period,"minecraft:overworld","",now).isEmpty());
    }

    private static int stall(Minigame set,int sequence){
        while(set.phase()<32){
            boolean tap=set.phase()==20||set.phase()==24;
            set.input(sequence++,tap?Minigame.SPACE:0);
            set.advance();
        }
        assertTrue(set.canRequestHelp());
        return sequence;
    }
}
