package pt.realmprogression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import pt.realmprogression.domain.ProgressLedger;
import pt.realmprogression.domain.ProgressLedger.*;
import pt.realmprogression.data.ProgressStore;
public final class ProgressLedgerTest {
    private static final UUID ADMIN=UUID.fromString("1ac00000-0000-4000-8000-000000000001");
    @TempDir Path folder;
    private State goal(State s,String mod,long cents){return ProgressLedger.configure(s,mod,Set.of(mod),cents,true,ADMIN,"Administrator",10);}
    @Test void initialCreateGoalIsEightThousandEuros(){assertEquals(800_000,State.fresh().rules().get("create").goal());assertTrue(State.fresh().blocked("create"));}
    @Test void oneCentBelowTheGoalDoesNotUnlock(){assertTrue(ProgressLedger.observe(State.fresh(),799_999,Set.of("create"),20).blocked("create"));}
    @Test void exactBalanceUnlocksWithoutChangingIt(){var state=ProgressLedger.observe(State.fresh(),800_000,Set.of("create"),20);assertFalse(state.blocked("create"));assertEquals(800_000,state.rules().get("create").witnessed());assertEquals(1,state.history().size());}
    @Test void subsequentSpendingDoesNotRelock(){var unlocked=ProgressLedger.observe(State.fresh(),800_000,Set.of("create"),20);assertEquals(unlocked,ProgressLedger.observe(unlocked,0,Set.of("create"),30));}
    @Test void repeatedObservationsDoNotDuplicateUnlocks(){var unlocked=ProgressLedger.observe(State.fresh(),800_000,Set.of("create"),20);assertEquals(unlocked,ProgressLedger.observe(unlocked,900_000,Set.of("create"),40));}
    @Test void raisingTheGoalDoesNotRevokeAPermanentApproval(){var unlocked=ProgressLedger.observe(State.fresh(),800_000,Set.of("create"),20);assertFalse(goal(unlocked,"create",1_000_000).blocked("create"));}
    @Test void loweringAnUnmetGoalAllowsTheNextConfirmedObservation(){var state=goal(State.fresh(),"create",500_000);assertFalse(ProgressLedger.observe(state,500_000,Set.of("create"),50).blocked("create"));}
    @Test void differentModsHaveIndependentGoals(){var state=goal(State.fresh(),"ars_nouveau",1_000_000);var next=ProgressLedger.observe(state,800_000,Set.of("create","ars_nouveau"),20);assertFalse(next.blocked("create"));assertTrue(next.blocked("ars_nouveau"));}
    @Test void absentModsDoNotAcquireApprovalsBeforeInstallation(){assertTrue(ProgressLedger.observe(State.fresh(),9_000_000,Set.of(),20).blocked("create"));}
    @Test void disabledRulesDoNotAcquireApprovals(){var state=ProgressLedger.configure(State.fresh(),"create",Set.of("create"),800_000,false,ADMIN,"Admin",10);assertEquals(state,ProgressLedger.observe(state,900_000,Set.of("create"),20));}
    @Test void disablingThenEnablingDoesNotEraseApproval(){var a=ProgressLedger.observe(State.fresh(),800_000,Set.of("create"),20);var b=ProgressLedger.configure(a,"create",Set.of("create"),900_000,false,ADMIN,"Admin",21);assertFalse(goal(b,"create",900_000).blocked("create"));}
    @Test void essentialModsCannotBeSelected(){for(String mod:ProgressLedger.PROTECTED)assertThrows(IllegalArgumentException.class,()->goal(State.fresh(),mod,100));}
    @Test void essentialOutputNamespacesCannotBeAliased(){assertThrows(IllegalArgumentException.class,()->ProgressLedger.configure(State.fresh(),"other",Set.of("minecraft"),100,true,ADMIN,"Admin",10));}
    @Test void outputNamespaceCanDifferFromTheModId(){var a=ProgressLedger.configure(State.fresh(),"other_mod",Set.of("other_items"),100,true,ADMIN,"Admin",10);assertTrue(a.blocked("other_items"));assertFalse(a.blocked("other_mod"));}
    @Test void overlappingActiveOutputScopesAreRejected(){assertThrows(IllegalArgumentException.class,()->ProgressLedger.configure(State.fresh(),"other",Set.of("create"),100,true,ADMIN,"Admin",10));}
    @Test void approvedOutputScopeCannotBeExpandedForFree(){var a=ProgressLedger.observe(State.fresh(),800_000,Set.of("create"),20);assertThrows(IllegalArgumentException.class,()->ProgressLedger.configure(a,"create",Set.of("create","other"),800_000,true,ADMIN,"Admin",21));}
    @Test void invalidAmountsAndIdentifiersAreRejected(){for(long n:new long[]{0,-1,Long.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->goal(State.fresh(),"other",n));for(String id:List.of("Create","../world","a:b",""))assertThrows(IllegalArgumentException.class,()->goal(State.fresh(),id,1));}
    @Test void actualStoreReloadPreservesApprovalAndAudit(){Path p=folder.resolve("world/data/realm_progression.json");var store=new ProgressStore(p);var unlocked=ProgressLedger.observe(store.state(),800_000,Set.of("create"),20);store.commit(unlocked);assertEquals(unlocked,new ProgressStore(p).state());}
    @Test void invalidJsonIsPreservedInsteadOfReplacingIt()throws Exception{Path p=folder.resolve("broken.json");Files.writeString(p,"{not-valid");assertThrows(IllegalStateException.class,()->new ProgressStore(p));assertEquals("{not-valid",Files.readString(p));}
    @Test void malformedUtf8IsRejected()throws Exception{Path p=folder.resolve("broken.json");Files.write(p,new byte[]{(byte)0xc3,(byte)0x28});assertThrows(IllegalStateException.class,()->new ProgressStore(p));}
    @Test void unsupportedDataVersionIsRejected()throws Exception{Path p=folder.resolve("future.json");Files.writeString(p,"{\"version\":2,\"revision\":0,\"rules\":{},\"history\":[]}");assertThrows(IllegalStateException.class,()->new ProgressStore(p));}
    @Test void failedAtomicReplacementDoesNotChangeTheLivePolicy()throws Exception{Path p=folder.resolve("ledger.json");var store=new ProgressStore(p);Files.createDirectory(p);var target=goal(store.state(),"other",100);assertThrows(IllegalStateException.class,()->store.commit(target));assertEquals(State.fresh(),store.state());assertTrue(Files.isDirectory(p));}
    @Test void removedModApprovalSurvivesItsReinstallation(){var a=ProgressLedger.observe(State.fresh(),800_000,Set.of("create"),20);var b=ProgressLedger.observe(a,0,Set.of(),30);assertEquals(a,b);assertEquals(a,ProgressLedger.observe(b,0,Set.of("create"),40));}
    @Test void sameConfigurationDoesNotSpamTheAudit(){assertEquals(State.fresh(),goal(State.fresh(),"create",800_000));}
    @Test void negativeBudgetDoesNotUnlockAnything(){assertThrows(IllegalArgumentException.class,()->ProgressLedger.observe(State.fresh(),-1,Set.of("create"),20));}
}
