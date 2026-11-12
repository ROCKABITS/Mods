package pt.contracts.daily;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CelebrationQueueTest {
    private CelebrationQueue.Event<String> event(int id,long day,boolean finale){return new CelebrationQueue.Event<>(new UUID(1,id),day,finale,"event"+id);}
    @Test void simultaneousCompletionsPlaySequentially(){var queue=new CelebrationQueue<String>();for(int i=0;i<5;i++)queue.offer(event(i,10,false));for(int i=0;i<5;i++)assertEquals("event"+i,queue.poll().value());assertNull(queue.poll());}
    @Test void duplicateNetworkDeliveryDoesNotReplayAnAnimation(){var queue=new CelebrationQueue<String>();assertTrue(queue.offer(event(1,10,false)));assertFalse(queue.offer(event(1,10,false)));queue.poll();assertFalse(queue.offer(event(1,10,false)));}
    @Test void finaleWaitsBehindIndividualQuestsEvenIfItArrivesFirst(){var queue=new CelebrationQueue<String>();queue.offer(event(5,10,true));queue.offer(event(1,10,false));queue.offer(event(2,10,false));assertFalse(queue.poll().finale());assertFalse(queue.poll().finale());assertTrue(queue.poll().finale());}
    @Test void aDifferentDaysFinaleDoesNotDelayAnotherDaysIndividualReward(){var queue=new CelebrationQueue<String>();queue.offer(event(5,10,true));queue.offer(event(1,11,false));assertTrue(queue.poll().finale());}
    @Test void presentationMemoryIsBoundedAndSessionResetDropsTheQueue(){var queue=new CelebrationQueue<String>();for(int i=0;i<1000;i++)queue.offer(event(i,10,false));assertEquals(32,queue.size());queue.clear();assertEquals(0,queue.size());assertTrue(queue.offer(event(999,10,false)));}
    @Test void aFullQueuePreservesEarlierAnimationsAndCanRetryTheUnacceptedEvent(){var queue=new CelebrationQueue<String>();for(int i=0;i<32;i++)assertTrue(queue.offer(event(i,10,false)));assertFalse(queue.offer(event(99,10,false)));assertEquals("event0",queue.poll().value());assertTrue(queue.offer(event(99,10,false)));for(int i=1;i<32;i++)assertEquals("event"+i,queue.poll().value());assertEquals("event99",queue.poll().value());}
}
