package pt.realmnotifications.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pt.realmnotifications.domain.NotificationBurst.Action.*;

class NotificationBurstTest {
    @Test void ordinaryNoticesKeepTheirIndividualPresentation() {
        var burst = new NotificationBurst();
        for (int n=1;n<=3;n++) {
            var decision=burst.receive(100+n*100);
            assertEquals(INDIVIDUAL,decision.action()); assertEquals(n,decision.total());
        }
    }
    @Test void modpackLoginFloodProducesOneSummaryInsteadOfHundredsOfCards() {
        var burst = new NotificationBurst();int individuals=0,summaries=0,updates=0;
        for(int i=0;i<500;i++)switch(burst.receive(1_000+i).action()) {
            case INDIVIDUAL -> individuals++;
            case SUMMARIZE -> summaries++;
            case UPDATE_SUMMARY -> updates++;
        }
        assertEquals(3,individuals);assertEquals(1,summaries);assertEquals(496,updates);
    }
    @Test void sustainedTrickleCannotStartANewSummaryEveryThreeSeconds() {
        var burst = new NotificationBurst();int summaries=0;
        for(int i=0;i<120;i++)if(burst.receive(i*1_000L).action()==SUMMARIZE)summaries++;
        assertEquals(1,summaries);
        assertEquals(UPDATE_SUMMARY,burst.receive(120_000).action());
    }
    @Test void quietPeriodRestoresTheNextOrdinaryNotification() {
        var burst = new NotificationBurst();NotificationBurst.Decision last=null;
        for(int i=0;i<100;i++)last=burst.receive(100);
        var next=burst.receive(3_100);
        assertEquals(INDIVIDUAL,next.action());assertEquals(1,next.total());
        assertNotEquals(last.group(),next.group());
    }
    @Test void almostQuietStillBelongsToTheSameBurst() {
        var burst=new NotificationBurst();var first=burst.receive(10);
        for(int i=0;i<3;i++)burst.receive(10);
        var next=burst.receive(3_009);
        assertEquals(UPDATE_SUMMARY,next.action());assertEquals(first.group(),next.group());
    }
    @Test void clearingForAnotherWorldDropsBothSuppressionAndGroupIdentity() {
        var burst=new NotificationBurst();NotificationBurst.Decision before=null;
        for(int i=0;i<100;i++)before=burst.receive(1_000);
        burst.clear();var after=burst.receive(1_001);
        assertEquals(INDIVIDUAL,after.action());assertEquals(1,after.total());
        assertNotEquals(before.group(),after.group());
    }
    @Test void staleDeadlineUsesElapsedTimeAndKeepsTheExactBoundaryConsistent() {
        assertFalse(NotificationBurst.stale(500,30_499));
        assertTrue(NotificationBurst.stale(500,30_500));
        assertTrue(NotificationBurst.stale(500,90_000));
    }
}
