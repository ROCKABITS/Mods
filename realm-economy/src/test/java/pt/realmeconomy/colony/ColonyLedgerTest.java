package pt.realmeconomy.colony;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import pt.realmeconomy.colony.ColonyLedger.*;
import static pt.realmeconomy.colony.ColonyLedger.*;
class ColonyLedgerTest {
 final String key="minecraft:overworld/1";final UUID player=UUID.randomUUID();final long now=20_000*DAY;final String order=UUID.randomUUID().toString();
 State start(){return ensure(State.empty(),key,"Aetheria",50,now,List.of(new Order(order,"minecraft:bread",128,4000,false)));}
 State funded(){return transfer(start(),key,player,"Aveline",10000,true,now,UUID.randomUUID());}
 @Test void accountStartsAtZeroWithIndependentIdentity(){assertEquals(0,start().account(key).balance());assertEquals(1,start().accounts().size());}
 @Test void transferConservesTheExactDepositAndWithdrawal(){var s=transfer(funded(),key,player,"Aveline",2500,false,now,UUID.randomUUID());assertEquals(7500,s.account(key).balance());assertEquals(-2500,s.account(key).history().getFirst().cents());}
 @Test void negativeAndZeroTransfersAreRejected(){for(long n:new long[]{-1,0})assertThrows(IllegalArgumentException.class,()->transfer(start(),key,player,"A",n,true,now,UUID.randomUUID()));}
 @Test void noOverdraftIsPossible(){assertThrows(IllegalStateException.class,()->transfer(start(),key,player,"A",1,false,now,UUID.randomUUID()));}
 @Test void receiptCannotBeReplayed(){UUID id=UUID.randomUUID();var s=transfer(start(),key,player,"A",100,true,now,id);assertThrows(IllegalStateException.class,()->transfer(s,key,player,"A",100,true,now,id));}
 @Test void exportCreditsTheColonyAndPersonalQuota(){var s=export(start(),key,player,"A",order,now,UUID.randomUUID());assertEquals(4000,s.account(key).balance());assertEquals(4000,s.exporters().get(player.toString()).earned());assertTrue(s.account(key).orders().getFirst().delivered());}
 @Test void sameOrderCannotBeDeliveredTwice(){var s=export(start(),key,player,"A",order,now,UUID.randomUUID());assertThrows(IllegalStateException.class,()->export(s,key,player,"A",order,now,UUID.randomUUID()));}
 @Test void perColonyLimitPreventsExport(){var s=start().policy(new Policy(true,3999,25000,5,2500,2500),now);assertThrows(IllegalStateException.class,()->export(s,key,player,"A",order,now,UUID.randomUUID()));}
 @Test void perPlayerLimitSpansDifferentColonies(){var s=export(start().policy(new Policy(true,25000,5000,5,2500,2500),now),key,player,"A",order,now,UUID.randomUUID());String k="minecraft:overworld/2",o=UUID.randomUUID().toString();var next=ensure(s,k,"Second",80,now,List.of(new Order(o,"minecraft:bread",128,4000,false)));assertThrows(IllegalStateException.class,()->export(next,k,player,"A",o,now,UUID.randomUUID()));}
 @Test void resetUsesRealUtcAndPreservesMoney(){var s=export(start(),key,player,"A",order,now,UUID.randomUUID());var next=ensure(s,key,"Aetheria",50,now+DAY,List.of(new Order(UUID.randomUUID().toString(),"minecraft:book",32,4500,false)));assertEquals(4000,next.account(key).balance());assertEquals(0,next.account(key).earned());assertFalse(next.account(key).orders().getFirst().delivered());}
 @Test void clockRollbackCannotRerollOrders(){var s=start();var next=ensure(s,key,"Aetheria",50,now-DAY,List.of());assertEquals(s.account(key).orders(),next.account(key).orders());assertEquals(s.highWater(),next.highWater());}
 @Test void yesterdayOrdersExpireBeforePayment(){assertThrows(IllegalStateException.class,()->export(start(),key,player,"A",order,now+DAY,UUID.randomUUID()));}
 @Test void incomeHasNoEffectOnAnotherColony(){var s=ensure(funded(),"minecraft:the_nether/1","Other dimension",1,now,List.of());assertEquals(10000,s.account(key).balance());assertEquals(0,s.account("minecraft:the_nether/1").balance());}
 @Test void renamePreservesBalanceHistoryAndOrders(){var s=ensure(funded(),key,"New name",50,now,List.of());assertEquals(10000,s.account(key).balance());assertEquals(1,s.account(key).history().size());assertEquals(order,s.account(key).orders().getFirst().id());}
 @Test void centerMismatchCannotAdoptOldFunds(){assertThrows(IllegalStateException.class,()->ensure(funded(),key,"Replacement",51,now,List.of()));}
 @Test void deletedColonyIdsCannotInheritAnArchivedBudget(){var s=retire(funded(),key,now);assertFalse(s.accounts().containsKey(key));assertEquals(10000,s.accounts().values().iterator().next().balance());var next=ensure(s,key,"Replacement",50,now,List.of());assertEquals(0,next.account(key).balance());assertEquals(2,next.accounts().size());}
 @Test void invoiceAmountIsFrozenWhenPolicyChanges(){String id=UUID.randomUUID().toString();var s=invoice(funded(),key,new Invoice(id,"BUILD","Builder","1,2,3",2500,false,now),now);s=s.policy(new Policy(true,25000,25000,5,9000,9000),now);assertEquals(2500,s.account(key).invoice(id).cents());}
 @Test void invoiceApprovalBurnsTheFeeExactlyOnce(){String id=UUID.randomUUID().toString();var s=invoice(funded(),key,new Invoice(id,"UPGRADE","Hospital","1,2,3",4000,false,now),now);var next=pay(s,key,id,player,"A",now,UUID.randomUUID());assertEquals(6000,next.account(key).balance());assertTrue(next.account(key).invoice(id).paid());assertThrows(IllegalStateException.class,()->pay(next,key,id,player,"A",now,UUID.randomUUID()));}
 @Test void invoiceCannotOverdrawAccount(){String id=UUID.randomUUID().toString();var s=invoice(start(),key,new Invoice(id,"RECRUIT","Visitor","id",2500,false,now),now);assertThrows(IllegalStateException.class,()->pay(s,key,id,player,"A",now,UUID.randomUUID()));}
 @Test void allFinancialStateRoundTrips(){var s=export(funded(),key,player,"A",order,now,UUID.randomUUID());assertEquals(s,decode(encode(s)));}
 @Test void invalidVersionsAndUnknownAccountsFail(){assertThrows(IllegalArgumentException.class,()->new State(2,Policy.defaults(),0,Map.of(),Map.of()));assertThrows(NullPointerException.class,()->start().account("missing"));}
 @Test void constructionCostUsesTargetLevelSquared(){assertEquals(2500,Policy.defaults().construction(1));assertEquals(10000,Policy.defaults().construction(2));assertEquals(62500,Policy.defaults().construction(5));}
 @Test void disablingExportsPreservesTheBudget(){var s=funded().policy(new Policy(false,25000,25000,5,2500,2500),now);assertEquals(10000,s.account(key).balance());assertThrows(IllegalStateException.class,()->export(s,key,player,"A",order,now,UUID.randomUUID()));}
 @Test void collectionReferencesCannotMutateTheLedger(){var s=start();assertThrows(UnsupportedOperationException.class,()->s.accounts().clear());assertThrows(UnsupportedOperationException.class,()->s.account(key).orders().clear());}
}
