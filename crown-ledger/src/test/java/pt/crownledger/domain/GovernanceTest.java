package pt.crownledger.domain;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pt.crownledger.domain.Treasury.Currency.*;
import static pt.crownledger.domain.Governance.*;

class GovernanceTest {
    final UUID king=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    final Set<UUID> people=Set.of(king,a,b,c);
    final Treasury treasury=new Treasury();final Governance votes=new Governance();
    final Offer offer=new Offer(Benefit.WORKS,EMERALD,400,60_000,1);
    UUID open(Offer o){return votes.propose(treasury,king,"Aetherion","Royal works",o,people,50,2,10_000,5_000,1_000);}
    void funds(){treasury.addFunds(EMERALD,1_000,Treasury.Kind.DEPOSIT,king,"Test funded treasury",0);}
    @Test void freezesElectorateQuorumAndCostAndHoldsOnlyAvailableMoney(){funds();var eligible=new HashSet<>(people);votes.propose(treasury,king,"King","Works",offer,eligible,51,2,10_000,0,1000);eligible.clear();assertEquals(4,votes.current().eligible().size());assertEquals(3,votes.current().minimumVotes());assertEquals(600,treasury.balance(EMERALD));assertEquals(offer,votes.current().offer());assertThrows(UnsupportedOperationException.class,()->votes.current().eligible().clear());}
    @Test void insufficientFundsLeavesNoProposalOrDebit(){assertThrows(IllegalStateException.class,()->open(offer));assertNull(votes.current());assertEquals(0,treasury.balance(EMERALD));assertEquals(0,votes.nextProposalAt());}
    @Test void oneMutableVotePerCitizenAndLateJoinersCannotVote(){var id=open(null);votes.vote(id,a,true,2_000);votes.vote(id,a,false,3_000);assertEquals(1,votes.current().ballots().size());assertEquals(0,votes.current().yes());assertEquals(1,votes.current().no());assertThrows(IllegalStateException.class,()->votes.vote(id,UUID.randomUUID(),true,3_001));assertThrows(IllegalStateException.class,()->votes.vote(UUID.randomUUID(),b,true,3_002));}
    @Test void successfulPollPaysOnceAndActivatesFrozenBenefit(){funds();var id=open(offer);votes.vote(id,a,true,2_000);votes.vote(id,b,true,2_001);assertFalse(votes.advance(treasury,10_999));assertTrue(votes.advance(treasury,11_000));assertEquals(600,treasury.balance(EMERALD));assertEquals(Outcome.APPROVED,votes.history().getFirst().outcome());assertTrue(votes.active(Benefit.WORKS,11_000));assertEquals(1,votes.strength(Benefit.WORKS,11_000));assertFalse(votes.advance(treasury,11_001));assertEquals(1,votes.history().size());assertThrows(IllegalStateException.class,()->votes.vote(id,c,true,11_002));}
    @Test void tieRejectsAndReturnsEscrowOnlyOnce(){funds();var id=open(offer);votes.vote(id,a,true,2_000);votes.vote(id,b,false,2_001);votes.advance(treasury,11_000);assertEquals(Outcome.REJECTED,votes.history().getFirst().outcome());assertEquals(1_000,treasury.balance(EMERALD));votes.advance(treasury,40_000);assertEquals(1_000,treasury.balance(EMERALD));assertFalse(votes.active(Benefit.WORKS,12_000));}
    @Test void majorityWithoutQuorumRefunds(){funds();var id=open(offer);votes.vote(id,a,true,2_000);votes.advance(treasury,11_000);assertEquals(Outcome.NO_QUORUM,votes.history().getFirst().outcome());assertEquals(1000,treasury.balance(EMERALD));}
    @Test void advisoryPollNeverCreatesOrConsumesFunds(){var id=open(null);votes.vote(id,a,true,2_000);votes.vote(id,b,true,2_001);votes.advance(treasury,11_000);assertEquals(0,treasury.balance(EMERALD));assertTrue(votes.active().isEmpty());assertEquals(Outcome.APPROVED,votes.history().getFirst().outcome());}
    @Test void cancelRefundsAndIsAudited(){funds();var id=open(offer);votes.vote(id,a,true,2_000);votes.cancel(treasury,c,3_000);assertEquals(1_000,treasury.balance(EMERALD));assertEquals(Outcome.ADMIN_CANCELLED,votes.history().getFirst().outcome());assertEquals(c,votes.history().getFirst().cancelledBy());assertThrows(IllegalStateException.class,()->votes.cancel(treasury,c,3_001));}
    @Test void restoredVoteSettlesOnlyOnceAndRetainsBallots(){funds();var id=open(offer);votes.vote(id,a,true,2_000);votes.vote(id,b,true,2_001);var restored=new Governance(votes.snapshot());restored.advance(treasury,11_000);var reopened=new Governance(restored.snapshot());reopened.advance(treasury,11_100);assertEquals(600,treasury.balance(EMERALD));assertEquals(1,reopened.history().size());assertEquals(2,reopened.history().getFirst().yes());}
    @Test void expiredOfflineBuffNeverRestartsOnReopening(){funds();var id=open(offer);votes.vote(id,a,true,2_000);votes.vote(id,b,true,2_001);var restored=votes.copy();restored.advance(treasury,90_000);assertTrue(restored.active().isEmpty());assertEquals(600,treasury.balance(EMERALD));assertEquals(Outcome.APPROVED,restored.history().getFirst().outcome());}
    @Test void benefitExpiresAtOriginalDeadlineAndClockRollbackCannotExtendIt(){funds();var id=open(offer);votes.vote(id,a,true,2_000);votes.vote(id,b,true,2_001);votes.advance(treasury,11_000);assertEquals(71_000,votes.active().getFirst().expiresAt());votes.advance(treasury,71_000);assertFalse(votes.active(Benefit.WORKS,12_000));assertTrue(votes.active().isEmpty());assertThrows(IllegalStateException.class,()->votes.vote(id,a,true,2_500));}
    @Test void fundedClockCheckpointsWithoutSavingIdleState(){assertFalse(votes.advance(treasury,1_000_000));funds();var id=open(offer);votes.vote(id,a,true,2000);votes.vote(id,b,true,2001);votes.advance(treasury,11000);assertTrue(votes.advance(treasury,41000));assertEquals(41000,votes.lastObservedAt());assertFalse(votes.advance(treasury,12000));assertEquals(41000,votes.lastObservedAt());}
    @Test void noReproposalDuringVoteCooldownOrSameActiveBenefit(){funds();var id=open(offer);assertThrows(IllegalStateException.class,()->votes.propose(treasury,king,"K","Q",null,people,50,2,10000,0,2000));votes.vote(id,a,true,2000);votes.vote(id,b,true,2001);votes.advance(treasury,11000);assertThrows(IllegalStateException.class,()->votes.propose(treasury,king,"K","Q",null,people,50,2,10000,0,12000));assertThrows(IllegalStateException.class,()->votes.propose(treasury,king,"K","Q",offer,people,50,2,10000,0,16000));assertEquals(600,treasury.balance(EMERALD));}
    @Test void refundAtBalanceLimitWaitsForHeadroomInsteadOfDestroyingMoney(){funds();open(offer);treasury.addFunds(EMERALD,Long.MAX_VALUE-600,Treasury.Kind.DEPOSIT,king,"Headroom",2000);votes.advance(treasury,11000);assertNotNull(votes.refund());assertEquals(Long.MAX_VALUE,treasury.balance(EMERALD));assertThrows(IllegalStateException.class,()->votes.propose(treasury,king,"K","Q",null,people,50,2,10000,0,16000));treasury.removeFunds(EMERALD,500,Treasury.Kind.DEPOSIT,king,"Space",17000);votes.advance(treasury,17000);assertNull(votes.refund());assertEquals(Long.MAX_VALUE-100,treasury.balance(EMERALD));votes.advance(treasury,18000);assertEquals(Long.MAX_VALUE-100,treasury.balance(EMERALD));}
    @Test void invalidInputsCannotDeductFunds(){funds();for(String invalid:List.of("","x".repeat(121),"§aFake","line\nbreak"))assertThrows(RuntimeException.class,()->votes.propose(treasury,king,"K",invalid,offer,people,50,2,10000,0,1000));assertEquals(1000,treasury.balance(EMERALD));assertNull(votes.current());assertThrows(RuntimeException.class,()->new Offer(Benefit.WORKS,EMERALD,-1,60000,1));assertThrows(RuntimeException.class,()->votes.propose(treasury,king,"K","Q",offer,Set.of(),50,2,10000,0,1000));}
    @Test void ballotAtDeadlineAndRewoundClockIsRejected(){var id=open(null);assertThrows(IllegalStateException.class,()->votes.vote(id,a,true,11000));votes.vote(id,a,true,9000);assertThrows(IllegalStateException.class,()->votes.vote(id,b,true,8000));assertEquals(1,votes.current().yes());}
    @Test void highQuorumRoundsUpAndServerSnapshotIsImmutable(){votes.propose(treasury,king,"K","Q",null,Set.of(a,b,c),50,2,10000,0,1000);assertEquals(2,votes.current().minimumVotes());var snapshot=votes.snapshot();assertThrows(UnsupportedOperationException.class,()->snapshot.active().clear());assertThrows(RuntimeException.class,()->new Snapshot(snapshot.current(),List.of(),List.of(),new Refund(UUID.randomUUID(),EMERALD,1),11000,1000));}
    @Test void chronologicalHistoryIsBoundedAndRetainsNoBallotMap(){for(int i=0;i<70;i++){long now=1000+i*2000L;var id=votes.propose(treasury,king,"K","Q"+i,null,people,50,2,1000,0,now);votes.vote(id,a,true,now+1);votes.vote(id,b,true,now+2);votes.advance(treasury,now+1000);}assertEquals(64,votes.history().size());assertEquals("Q69",votes.history().getFirst().question());assertNull(votes.current());assertTrue(Arrays.stream(Result.class.getRecordComponents()).noneMatch(r->r.getType()==Map.class));}

    @Test void generalTitleAndMultilineDescriptionRemainFrozenThroughVotingAndHistory(){
        String description="Construir uma ponte junto à vila.\nUsar pedra e madeira, com acesso público.";
        var id=votes.propose(treasury,king,"Aetherion","Nova ponte",description,null,people,50,2,10000,0,1000);
        votes.vote(id,a,true,2000);votes.vote(id,b,true,2001);
        assertEquals(description,votes.current().description());
        var restored=votes.copy();restored.advance(treasury,11000);
        assertEquals("Nova ponte",restored.history().getFirst().question());
        assertEquals(description,restored.history().getFirst().description());
        assertEquals(Outcome.APPROVED,restored.history().getFirst().outcome());
        assertEquals(0,treasury.balance(EMERALD));assertTrue(restored.active().isEmpty());
    }
    @Test void oversizedOrFormattedDescriptionsCannotReserveMoney(){
        funds();
        for(String invalid:List.of("x".repeat(1001),"§aForged","hidden\u0000text"))
            assertThrows(RuntimeException.class,()->votes.propose(treasury,king,"King","Title",invalid,offer,people,50,2,10000,0,1000));
        assertNull(votes.current());assertEquals(1000,treasury.balance(EMERALD));
    }
    @Test void descriptionNormalizesLineEndingsAndLegacyRecordsHaveAnEmptyDescription(){
        votes.propose(treasury,king,"King","Title","First\r\nSecond\rThird",null,people,50,2,10000,0,1000);
        assertEquals("First\nSecond\nThird",votes.current().description());
        var legacy=new Proposal(UUID.randomUUID(),king,"K","Previous vote",null,1000,11000,11000,1,Set.of(a),Map.of());
        assertEquals("",legacy.description());
    }
}
