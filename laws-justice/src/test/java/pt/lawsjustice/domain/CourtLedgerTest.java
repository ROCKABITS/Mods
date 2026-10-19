package pt.lawsjustice.domain;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static pt.lawsjustice.domain.CourtLedger.*;

class CourtLedgerTest {
    private static final UUID CASE=id(1), COMPLAINANT=id(2), ACCUSED=id(3), JUDGE=id(4), WARRANT=id(5), ESCORT=id(6), SENTENCE=id(7);
    private static UUID id(long value){return new UUID(0,value);}
    private static CaseFile file(){return file(CASE);}
    private static CaseFile file(UUID id){return new CaseFile(id,COMPLAINANT,"Witness",ACCUSED,"Aetherion","Stole the royal apples",List.of(),OPEN,1000,0,"","","","",0,"");}
    private static CourtLedger filed(){return CourtLedger.empty().file(file());}
    private static CourtLedger hearing(){return filed().hear(CASE,JUDGE,"President",PRESIDENT,2000);}
    private static Evidence evidence(UUID id,long at){return new Evidence(id,COMPLAINANT,"Witness","I saw the accused take an apple",at);}
    private static Warrant warrant(){return warrant(WARRANT,ACCUSED,ACTIVE,"");}
    private static Warrant warrant(UUID id,UUID accused,String status,String caseId){return new Warrant(id,accused,"Accused",JUDGE,"President",PRESIDENT,"Attend the court",1000,301000,status,caseId);}
    private static Restraint restraint(){return restraint(ACCUSED,WARRANT,2000,302000);}
    private static Restraint restraint(UUID player,UUID warrant,long at,long until){return new Restraint(player,ESCORT,warrant,"minecraft:overworld",new JusticeLedger.Point(5,65,5),at,until);}
    private static CourtLedger warranted(){return CourtLedger.empty().issue(warrant());}
    private static CourtLedger restrained(){return warranted().restrain(restraint(ACCUSED,WARRANT,2000,301000),2000);}

    @Test void filingCreatesAnImmutableUnheardUnresolvedCase(){
        CourtLedger empty=CourtLedger.empty(),filed=empty.file(file());
        assertTrue(empty.cases().isEmpty());assertEquals(file(),filed.cases().get(CASE));
        assertThrows(IllegalStateException.class,()->filed.file(file()));
        assertEquals(1,filed.cases().size());
    }

    @Test void onlyAnInitialOpenCaseWithNoEvidenceCanBeFiled(){
        CaseFile withEvidence=new CaseFile(CASE,COMPLAINANT,"Witness",ACCUSED,"Accused","Accusation",List.of(evidence(id(8),1000)),OPEN,1000,0,"","","","",0,"");
        assertThrows(IllegalArgumentException.class,()->CourtLedger.empty().file(withEvidence));
        assertThrows(IllegalArgumentException.class,()->CourtLedger.empty().file(hearing().cases().get(CASE)));
    }

    @Test void evidenceAppendsWithoutChangingPreviousEvidenceOrItsAuthors(){
        Evidence first=evidence(id(8),1100),second=evidence(id(9),2100);
        CourtLedger initial=filed(),one=initial.evidence(CASE,first),two=one.hear(CASE,JUDGE,"President",PRESIDENT,2000).evidence(CASE,second);
        assertTrue(initial.cases().get(CASE).evidence().isEmpty());assertEquals(List.of(first),one.cases().get(CASE).evidence());
        assertEquals(List.of(first,second),two.cases().get(CASE).evidence());
        assertThrows(IllegalStateException.class,()->one.evidence(CASE,first));
    }

    @Test void evidenceCannotBeInjectedAfterAVerdict(){
        CourtLedger decided=hearing().decide(CASE,JUDGE,"President",PRESIDENT,"Not proven","",false,3000);
        assertThrows(IllegalStateException.class,()->decided.evidence(CASE,evidence(id(8),4000)));
        assertThrows(IllegalStateException.class,()->decided.hear(CASE,JUDGE,"President",PRESIDENT,4000));
    }

    @Test void evidenceAndAccusationAreBoundedAndRejectControlCharacters(){
        assertThrows(IllegalArgumentException.class,()->new Evidence(id(8),COMPLAINANT,"Witness","",1000));
        assertThrows(IllegalArgumentException.class,()->new Evidence(id(8),COMPLAINANT,"Witness","x".repeat(1201),1000));
        assertEquals("First line\nSecond line\r\nThird line",new Evidence(id(8),COMPLAINANT,"Witness","First line\nSecond line\r\nThird line",1000).text());
        for(char control:new char[]{0,1,9,27,127}) assertThrows(IllegalArgumentException.class,()->new Evidence(id(8),COMPLAINANT,"Witness","forged"+control+"message",1000));
        assertThrows(IllegalArgumentException.class,()->new Evidence(id(8),COMPLAINANT,"forged\nname","Evidence",1000));
        assertThrows(IllegalArgumentException.class,()->new Evidence(id(8),COMPLAINANT,"Witness","Evidence",-1));
        assertThrows(IllegalArgumentException.class,()->new CaseFile(CASE,COMPLAINANT,"Witness",ACCUSED,"Accused","x".repeat(801),List.of(),OPEN,0,0,"","","","",0,""));
    }

    @Test void aHearingIsRequiredBeforeTheJudgeCanDecide(){
        assertThrows(IllegalStateException.class,()->filed().decide(CASE,JUDGE,"President",PRESIDENT,"Guilty",SENTENCE.toString(),true,3000));
        CourtLedger hearing=hearing();CaseFile row=hearing.cases().get(CASE);
        assertEquals(HEARING,row.status());assertEquals(JUDGE.toString(),row.judge());assertEquals(2000,row.heardAt());
        assertThrows(IllegalStateException.class,()->hearing.hear(CASE,JUDGE,"President",PRESIDENT,2500));
    }

    @Test void theActualNewPresidentCanFinishAnEarlierPresidentsHearing(){
        CourtLedger result=hearing().decide(CASE,id(90),"New President",PRESIDENT,"Proven at the hearing",SENTENCE.toString(),true,3000);
        CaseFile row=result.cases().get(CASE);assertEquals(CONVICTED,row.status());assertEquals(id(90).toString(),row.judge());
        assertEquals("New President",row.judgeName());assertEquals(2000,row.heardAt());assertEquals(3000,row.decidedAt());
        assertEquals(SENTENCE.toString(),row.sentence());
    }

    @Test void finalVerdictsCannotBeReplacedOrAppliedTwice(){
        CourtLedger result=hearing().decide(CASE,JUDGE,"President",PRESIDENT,"Not proven","",false,3000);
        assertEquals(ACQUITTED,result.cases().get(CASE).status());assertEquals("",result.cases().get(CASE).sentence());
        assertThrows(IllegalStateException.class,()->result.decide(CASE,JUDGE,"President",PRESIDENT,"Actually guilty",SENTENCE.toString(),true,4000));
    }

    @Test void onlyTheTwoRealAuthorityKindsCanBeRecorded(){
        assertDoesNotThrow(()->filed().hear(CASE,JUDGE,"Emergency administrator",ADMIN_EMERGENCY,2000));
        for(String role:List.of("JUDGE","GUARD","PRESIDENT_ELECT","","president")){
            assertThrows(IllegalArgumentException.class,()->filed().hear(CASE,JUDGE,"Someone",role,2000));
            assertThrows(IllegalArgumentException.class,()->new Warrant(WARRANT,ACCUSED,"Accused",JUDGE,"Someone",role,"Reason",0,1000,ACTIVE,""));
        }
    }

    @Test void guiltyAndAcquittalOutcomesHaveOppositeSentenceReferenceRequirements(){
        assertThrows(IllegalArgumentException.class,()->hearing().decide(CASE,JUDGE,"President",PRESIDENT,"Guilty","",true,3000));
        assertThrows(IllegalArgumentException.class,()->hearing().decide(CASE,JUDGE,"President",PRESIDENT,"Innocent",SENTENCE.toString(),false,3000));
        assertThrows(IllegalArgumentException.class,()->hearing().decide(CASE,JUDGE,"President",PRESIDENT,"",SENTENCE.toString(),true,3000));
    }

    @Test void clockRollbackDoesNotPreventAValidHearingOrVerdict(){
        CourtLedger heard=filed().hear(CASE,JUDGE,"President",PRESIDENT,0);
        assertEquals(1000,heard.cases().get(CASE).heardAt());
        CourtLedger result=heard.evidence(CASE,evidence(id(8),4000)).decide(CASE,JUDGE,"President",PRESIDENT,"Acquitted","",false,0);
        assertEquals(4000,result.cases().get(CASE).decidedAt());
    }

    @Test void corruptCaseFlagsDoNotSilentlyCreateOrRemoveAPenalty(){
        assertThrows(IllegalArgumentException.class,()->new CaseFile(CASE,COMPLAINANT,"Witness",ACCUSED,"Accused","Reason",List.of(),OPEN,1000,1000,JUDGE.toString(),"President",PRESIDENT,"",0,""));
        assertThrows(IllegalArgumentException.class,()->new CaseFile(CASE,COMPLAINANT,"Witness",ACCUSED,"Accused","Reason",List.of(),CONVICTED,1000,2000,JUDGE.toString(),"President",PRESIDENT,"Guilty",3000,""));
        assertThrows(IllegalArgumentException.class,()->new CaseFile(CASE,COMPLAINANT,"Witness",ACCUSED,"Accused","Reason",List.of(),"FINE",1000,0,"","","","",0,""));
    }

    @Test void optionalUuidStringsMustBeEmptyOrCanonicalNeverNullShortOrUppercase(){
        for(String value:new String[]{null,"1-1-1-1-1","ABCDEF00-0000-0000-0000-000000000001","not-a-uuid"}){
            assertThrows(IllegalArgumentException.class,()->new Warrant(WARRANT,ACCUSED,"Accused",JUDGE,"President",PRESIDENT,"Reason",0,1000,ACTIVE,value));
            assertThrows(IllegalArgumentException.class,()->hearing().decide(CASE,JUDGE,"President",PRESIDENT,"Guilty",value,true,3000));
        }
    }

    @Test void warrantsHaveStrictPositiveBoundedValidityAndKnownStatuses(){
        assertDoesNotThrow(()->new Warrant(WARRANT,ACCUSED,"Accused",JUDGE,"President",PRESIDENT,"Reason",0,MAX_WARRANT_MILLIS,ACTIVE,""));
        for(long expiry:new long[]{-1,0,MAX_WARRANT_MILLIS+1})
            assertThrows(IllegalArgumentException.class,()->new Warrant(WARRANT,ACCUSED,"Accused",JUDGE,"President",PRESIDENT,"Reason",0,expiry,ACTIVE,""));
        assertThrows(IllegalArgumentException.class,()->warrant(WARRANT,ACCUSED,"EXPIRED",""));
        assertThrows(IllegalArgumentException.class,()->CourtLedger.empty().issue(warrant(WARRANT,ACCUSED,EXECUTED,"")));
    }

    @Test void validityChecksTargetAndIssueAndExpiryEdgesWithoutDependingOnCurrentPresident(){
        CourtLedger ledger=warranted();assertFalse(ledger.validWarrant(WARRANT,ACCUSED,999));
        assertTrue(ledger.validWarrant(WARRANT,ACCUSED,1000));assertTrue(ledger.validWarrant(WARRANT,ACCUSED,300999));
        assertFalse(ledger.validWarrant(WARRANT,ACCUSED,301000));assertFalse(ledger.validWarrant(WARRANT,id(99),2000));
        assertFalse(ledger.validWarrant(id(99),ACCUSED,2000));assertFalse(ledger.validWarrant(WARRANT,ACCUSED,-1));
    }

    @Test void duplicateOrRevokedWarrantsCannotBeIssuedOrExecutedAgain(){
        CourtLedger ledger=warranted();assertThrows(IllegalStateException.class,()->ledger.issue(warrant()));
        CourtLedger revoked=ledger.revoke(WARRANT);assertEquals(REVOKED,revoked.warrants().get(WARRANT).status());
        assertFalse(revoked.validWarrant(WARRANT,ACCUSED,2000));assertThrows(IllegalStateException.class,()->revoked.revoke(WARRANT));
        assertThrows(IllegalStateException.class,()->revoked.restrain(restraint(ACCUSED,WARRANT,2000,301000),2000));
    }

    @Test void warrantCaseReferencesMustExistAndIdentifyTheSameAccused(){
        assertThrows(IllegalStateException.class,()->CourtLedger.empty().issue(warrant(WARRANT,ACCUSED,ACTIVE,CASE.toString())));
        assertThrows(IllegalArgumentException.class,()->filed().issue(warrant(WARRANT,id(99),ACTIVE,CASE.toString())));
        assertTrue(filed().issue(warrant(WARRANT,ACCUSED,ACTIVE,CASE.toString())).validWarrant(WARRANT,ACCUSED,2000));
    }

    @Test void acquittalInvalidatesAssociatedWarrantsWithoutDestroyingTheirAudit(){
        CourtLedger before=hearing().issue(warrant(WARRANT,ACCUSED,ACTIVE,CASE.toString()));
        CourtLedger after=before.decide(CASE,JUDGE,"President",PRESIDENT,"Acquitted","",false,3000);
        assertEquals(ACTIVE,after.warrants().get(WARRANT).status());assertFalse(after.validWarrant(WARRANT,ACCUSED,3000));
        assertThrows(IllegalStateException.class,()->after.restrain(restraint(ACCUSED,WARRANT,3000,301000),3000));
        assertThrows(IllegalStateException.class,()->after.issue(warrant(id(90),ACCUSED,ACTIVE,CASE.toString())));
    }

    @Test void restraintConsumesExactlyOneServerWarrantAuthorization(){
        CourtLedger before=warranted(),after=restrained();
        assertEquals(ACTIVE,before.warrants().get(WARRANT).status());assertEquals(EXECUTED,after.warrants().get(WARRANT).status());
        assertTrue(after.restraints().containsKey(ACCUSED));assertFalse(after.validWarrant(WARRANT,ACCUSED,2000));
        assertThrows(IllegalStateException.class,()->after.restrain(restraint(ACCUSED,WARRANT,2000,301000),2000));
        CourtLedger free=after.releaseRestraint(ACCUSED);assertThrows(IllegalStateException.class,()->free.restrain(restraint(ACCUSED,WARRANT,3000,301000),3000));
    }

    @Test void oneTargetCannotBeRestrainedTwiceEvenUsingAnotherLawfulWarrant(){
        CourtLedger ledger=restrained().issue(warrant(id(90),ACCUSED,ACTIVE,""));
        assertThrows(IllegalStateException.class,()->ledger.restrain(restraint(ACCUSED,id(90),2000,301000),2000));
        assertEquals(ACTIVE,ledger.warrants().get(id(90)).status());
    }

    @Test void restraintsCannotBeSelfEscortFutureDatedExpiredOrLongerThanFiveMinutes(){
        assertThrows(IllegalArgumentException.class,()->new Restraint(ACCUSED,ACCUSED,WARRANT,"minecraft:overworld",new JusticeLedger.Point(0,64,0),0,100));
        assertThrows(IllegalArgumentException.class,()->restraint(ACCUSED,WARRANT,0,MAX_RESTRAINT_MILLIS+1));
        assertThrows(IllegalArgumentException.class,()->warranted().restrain(restraint(ACCUSED,WARRANT,2000,301000),1999));
        assertThrows(IllegalArgumentException.class,()->warranted().restrain(restraint(ACCUSED,WARRANT,1000,2000),2000));
        assertThrows(IllegalArgumentException.class,()->restraint(ACCUSED,WARRANT,-1,2000));
    }

    @Test void restraintsCannotOutliveTheirWarrantOrStartBeforeItsIssue(){
        assertThrows(IllegalArgumentException.class,()->warranted().restrain(restraint(),2000));
        assertThrows(IllegalArgumentException.class,()->warranted().restrain(restraint(ACCUSED,WARRANT,999,2000),1000));
        assertTrue(warranted().restrain(restraint(ACCUSED,WARRANT,2000,301000),2000).restraints().containsKey(ACCUSED));
    }

    @Test void expiryUsesBoundedWallTimeAndRetainsTheConsumedAuthorization(){
        CourtLedger ledger=restrained();assertSame(ledger,ledger.expire(300999));
        CourtLedger free=ledger.expire(301000);assertTrue(free.restraints().isEmpty());
        assertEquals(EXECUTED,free.warrants().get(WARRANT).status());assertSame(free,free.expire(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->ledger.expire(-1));
    }

    @Test void releasingAMissingRestraintIsAnIdempotentNoOp(){
        CourtLedger ledger=restrained(),released=ledger.releaseRestraint(ACCUSED);
        assertTrue(released.restraints().isEmpty());assertSame(released,released.releaseRestraint(ACCUSED));
        assertEquals(1,ledger.restraints().size());assertEquals(EXECUTED,released.warrants().get(WARRANT).status());
    }

    @Test void restoredRestraintsRequireAnExecutedMatchingWarrant(){
        Restraint row=restraint(ACCUSED,WARRANT,2000,301000);
        assertThrows(IllegalArgumentException.class,()->new State(Map.of(),Map.of(),Map.of(ACCUSED,row)));
        assertThrows(IllegalArgumentException.class,()->new State(Map.of(),Map.of(WARRANT,warrant()),Map.of(ACCUSED,row)));
        assertThrows(IllegalArgumentException.class,()->new State(Map.of(),Map.of(WARRANT,warrant(WARRANT,id(99),EXECUTED,"")),Map.of(ACCUSED,row)));
    }

    @Test void restoresDoNotResetExpiredArrestsOrRearmConsumedWarrants(){
        CourtLedger before=restrained();CourtLedger loaded=CourtLedger.restore(before.state());
        assertEquals(before.state(),loaded.state());assertEquals(2000,loaded.restraints().get(ACCUSED).appliedAt());
        assertEquals(301000,loaded.restraints().get(ACCUSED).expiresAt());assertFalse(loaded.validWarrant(WARRANT,ACCUSED,2000));
        assertTrue(loaded.expire(Long.MAX_VALUE).restraints().isEmpty());
    }

    @Test void restoredMapsMustHaveTheKeysAdvertisedByTheirRecords(){
        assertThrows(IllegalArgumentException.class,()->new State(Map.of(id(99),file()),Map.of(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->new State(Map.of(),Map.of(id(99),warrant()),Map.of()));
        assertThrows(IllegalArgumentException.class,()->new State(Map.of(),restrained().warrants(),Map.of(id(99),restrained().restraints().get(ACCUSED))));
    }

    @Test void allSuppliedCollectionsAreCopiedAndAllExposedViewsAreImmutable(){
        ArrayList<Evidence> evidence=new ArrayList<>(List.of(evidence(id(8),1000)));
        CaseFile row=new CaseFile(CASE,COMPLAINANT,"Witness",ACCUSED,"Accused","Accusation",evidence,OPEN,1000,0,"","","","",0,"");
        evidence.clear();assertEquals(1,row.evidence().size());assertThrows(UnsupportedOperationException.class,()->row.evidence().clear());
        LinkedHashMap<UUID,CaseFile> supplied=new LinkedHashMap<>();supplied.put(CASE,row);State state=new State(supplied,Map.of(),Map.of());supplied.clear();
        CourtLedger ledger=CourtLedger.restore(state);assertEquals(1,ledger.cases().size());
        assertThrows(UnsupportedOperationException.class,()->ledger.cases().clear());assertThrows(UnsupportedOperationException.class,()->ledger.warrants().clear());
        assertThrows(UnsupportedOperationException.class,()->ledger.restraints().clear());
    }

    @Test void evidenceCapacityNeverSilentlyDiscardsEarlierProof(){
        CourtLedger ledger=filed();for(int i=0;i<MAX_EVIDENCE;i++)ledger=ledger.evidence(CASE,evidence(id(100+i),1000+i));
        CourtLedger full=ledger;assertThrows(IllegalArgumentException.class,()->full.evidence(CASE,evidence(id(999),3000)));
        assertEquals(MAX_EVIDENCE,full.cases().get(CASE).evidence().size());assertEquals(id(100),full.cases().get(CASE).evidence().getFirst().id());
    }

    @Test void caseCapacityPreservesEveryResolvedAndUnresolvedCase(){
        LinkedHashMap<UUID,CaseFile> cases=new LinkedHashMap<>();for(int i=0;i<MAX_CASES;i++){CaseFile row=file(id(100+i));cases.put(row.id(),row);}
        CourtLedger full=CourtLedger.restore(new State(cases,Map.of(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->full.file(file()));assertEquals(cases,full.cases());
    }

    @Test void warrantCapacityRejectsAnExtraDocumentWithoutDroppingConsumedOrRevokedRecords(){
        LinkedHashMap<UUID,Warrant> warrants=new LinkedHashMap<>();for(int i=0;i<MAX_WARRANTS;i++){Warrant row=warrant(id(100+i),ACCUSED,i%2==0?EXECUTED:REVOKED,"");warrants.put(row.id(),row);}
        CourtLedger full=CourtLedger.restore(new State(Map.of(),warrants,Map.of()));
        assertThrows(IllegalArgumentException.class,()->full.issue(warrant()));assertEquals(warrants,full.warrants());
    }

    @Test void restraintCapacityRejectsAnExtraArrestWithoutConsumingItsWarrant(){
        LinkedHashMap<UUID,Warrant> warrants=new LinkedHashMap<>();LinkedHashMap<UUID,Restraint> restraints=new LinkedHashMap<>();
        for(int i=0;i<MAX_RESTRAINTS;i++){
            UUID target=id(1000+i),warrantId=id(2000+i);warrants.put(warrantId,warrant(warrantId,target,EXECUTED,""));
            restraints.put(target,restraint(target,warrantId,2000,301000));
        }
        warrants.put(WARRANT,warrant());CourtLedger full=CourtLedger.restore(new State(Map.of(),warrants,restraints));
        assertThrows(IllegalArgumentException.class,()->full.restrain(restraint(ACCUSED,WARRANT,2000,301000),2000));
        assertEquals(ACTIVE,full.warrants().get(WARRANT).status());assertEquals(MAX_RESTRAINTS,full.restraints().size());
    }

    @Test void twoFinalCasesCannotClaimTheSameCustodySentenceId(){
        CourtLedger first=hearing().decide(CASE,JUDGE,"President",PRESIDENT,"Guilty",SENTENCE.toString(),true,3000);
        CourtLedger second=first.file(file(id(90))).hear(id(90),JUDGE,"President",PRESIDENT,4000);
        assertThrows(IllegalArgumentException.class,()->second.decide(id(90),JUDGE,"President",PRESIDENT,"Guilty",SENTENCE.toString(),true,5000));
        assertEquals(HEARING,second.cases().get(id(90)).status());
    }

    @Test void absentCasesAndWarrantsCannotBeMutatedIntoPhantomRecords(){
        CourtLedger ledger=CourtLedger.empty();assertThrows(IllegalStateException.class,()->ledger.hear(CASE,JUDGE,"President",PRESIDENT,1000));
        assertThrows(IllegalStateException.class,()->ledger.evidence(CASE,evidence(id(8),1000)));
        assertThrows(IllegalStateException.class,()->ledger.revoke(WARRANT));assertTrue(ledger.cases().isEmpty());assertTrue(ledger.warrants().isEmpty());
    }
}
