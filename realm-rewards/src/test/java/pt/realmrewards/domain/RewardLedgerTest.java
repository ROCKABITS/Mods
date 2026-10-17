package pt.realmrewards.domain;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RewardLedgerTest {
    private static final String ID="minecraft:story/root";
    @Test void taskGoalChallengeAndBackgroundDefaultsAreDistinct() {
        var ledger=new RewardLedger();
        assertEquals(200,ledger.amount(ID,RewardLedger.Kind.TASK));
        assertEquals(500,ledger.amount(ID,RewardLedger.Kind.GOAL));
        assertEquals(1000,ledger.amount(ID,RewardLedger.Kind.CHALLENGE));
        assertEquals(0,ledger.amount("minecraft:recipes/tools/wooden_pickaxe",RewardLedger.Kind.BACKGROUND));
    }
    @Test void repeatedCompletionCannotMintMoneyEvenAfterPolicyChange() {
        var ledger=new RewardLedger(); var player=UUID.randomUUID();
        assertTrue(ledger.earn(player,ID,RewardLedger.Kind.TASK));
        ledger.configure(ID,9900);
        assertFalse(ledger.earn(player,ID,RewardLedger.Kind.TASK));
        assertEquals(200,ledger.account(player).pending());
        ledger.paid(player,200); ledger.reset(ID);
        assertFalse(ledger.earn(player,ID,RewardLedger.Kind.TASK));
        assertEquals(0,ledger.account(player).pending());
    }
    @Test void twoPlayersWithTheSameNameRemainIndependentByUuid() {
        var ledger=new RewardLedger(); var first=UUID.randomUUID(); var second=UUID.randomUUID();
        ledger.earn(first,ID,RewardLedger.Kind.TASK); ledger.earn(second,ID,RewardLedger.Kind.TASK);
        ledger.paid(first,200);
        assertEquals(0,ledger.account(first).pending()); assertEquals(200,ledger.account(second).pending());
    }
    @Test void zeroRewardStillConsumesTheOnceOnlyCompletion() {
        var ledger=new RewardLedger(); var player=UUID.randomUUID(); ledger.configure(ID,0);
        assertTrue(ledger.earn(player,ID,RewardLedger.Kind.TASK)); ledger.configure(ID,1000);
        assertFalse(ledger.earn(player,ID,RewardLedger.Kind.TASK)); assertEquals(0,ledger.account(player).pending());
    }
    @Test void firstLoginBaselineDoesNotPayOldAchievementsOrErasePendingMoney() {
        var ledger=new RewardLedger(); var player=UUID.randomUUID();
        ledger.earn(player,ID,RewardLedger.Kind.TASK);
        ledger.baseline(player,List.of(ID,"a_new_mod:quest/finished"));
        assertFalse(ledger.earn(player,"a_new_mod:quest/finished",RewardLedger.Kind.CHALLENGE));
        assertEquals(200,ledger.account(player).pending());
    }
    @Test void changingDefaultValuesAffectsOnlyFutureEarnings() {
        var ledger=new RewardLedger(); var player=UUID.randomUUID(); ledger.earn(player,ID,RewardLedger.Kind.TASK);
        ledger.defaults(400,800,1200); ledger.earn(player,"new_mod:task",RewardLedger.Kind.TASK);
        assertEquals(600,ledger.account(player).pending());
    }
    @Test void copiedLedgerDoesNotMutateTheOriginalOrExposeMutableCollections() {
        var ledger=new RewardLedger(); var player=UUID.randomUUID(); ledger.earn(player,ID,RewardLedger.Kind.TASK);
        var copy=ledger.copy(); copy.paid(player,50); copy.configure(ID,500);
        assertEquals(200,ledger.account(player).pending()); assertEquals(150,copy.account(player).pending());
        assertThrows(UnsupportedOperationException.class,()->ledger.account(player).completed().clear());
        assertThrows(UnsupportedOperationException.class,()->ledger.accounts().clear());
    }
    @Test void partialPaymentsAndRetriesConserveTheExactReservedAmount() {
        var ledger=new RewardLedger(); var player=UUID.randomUUID(); ledger.earn(player,ID,RewardLedger.Kind.TASK);
        ledger.paid(player,30); ledger.paid(player,170);
        assertEquals(0,ledger.account(player).pending()); assertTrue(ledger.account(player).completed().contains(ID));
        assertThrows(IllegalArgumentException.class,()->ledger.paid(player,1));
        assertThrows(IllegalArgumentException.class,()->ledger.paid(player,0));
        assertThrows(IllegalArgumentException.class,()->ledger.paid(player,-1));
    }
    @Test void overridingAndRestoringOneModIdDoesNotAffectOthers() {
        var ledger=new RewardLedger(); ledger.configure(ID,123); ledger.configure("other:story/root",456);
        ledger.reset(ID);
        assertEquals(200,ledger.amount(ID,RewardLedger.Kind.TASK)); assertEquals(456,ledger.amount("other:story/root",RewardLedger.Kind.TASK));
    }
    @Test void euroParsingUsesExactCentsAndRejectsFractionsAndScientificNotation() {
        assertEquals(125,RewardLedger.parse("1,25")); assertEquals(125,RewardLedger.parse("1.25"));
        assertEquals(0,RewardLedger.parse("0")); assertEquals(100_000_000,RewardLedger.parse("1000000"));
        for (String value:List.of("-1","NaN","1e3","1.001","1000001","1,2,3","true",""))
            assertThrows(IllegalArgumentException.class,()->RewardLedger.parse(value),value);
    }
    @Test void invalidIdsAndCorruptedAccountsAreRejected() {
        for (String value:List.of("bad","Minecraft:UPPER","a:bad id","a:../bad\\path")) assertThrows(IllegalArgumentException.class,()->RewardLedger.id(value));
        assertThrows(IllegalArgumentException.class,()->new RewardLedger.Account(Set.of(ID),-1));
        assertThrows(IllegalArgumentException.class,()->new RewardLedger.Account(Set.of(ID),RewardLedger.MAX_BALANCE+1));
    }
    @Test void fullPendingBalanceRejectsNewCompletionWithoutMarkingItPaid() {
        var player=UUID.randomUUID(); var base=new RewardLedger();
        var ledger=new RewardLedger(base.defaults(),Map.of(),Map.of(player,new RewardLedger.Account(Set.of(),RewardLedger.MAX_BALANCE)));
        assertThrows(IllegalStateException.class,()->ledger.earn(player,ID,RewardLedger.Kind.TASK));
        assertFalse(ledger.account(player).completed().contains(ID)); assertEquals(RewardLedger.MAX_BALANCE,ledger.account(player).pending());
    }
}
