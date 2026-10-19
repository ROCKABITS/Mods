package pt.realmcasino.domain;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.realmcasino.data.GameState;
import static org.junit.jupiter.api.Assertions.*;

class ArcadeTest {
    final UUID player=UUID.randomUUID(),owner=UUID.randomUUID(),machine=UUID.randomUUID(),round=UUID.randomUUID();
    GameState road(){return GameState.arcade(round,machine,player,CasinoLedger.Kind.CHICKEN,10000,1000,List.of());}
    CasinoLedger bank(CasinoLedger.Kind kind){return new CasinoLedger().register(machine,owner,kind,"minecraft:overworld",1,"Arcade",1,10000).deposit(machine,owner,1_000_000);}
    @Test void all4096PlinkoPathsHaveThePublishedBinomialDistributionAndExpectedReturn(){
        int[] counts=new int[13];long gross=0;
        for(int bits=0;bits<4096;bits++){
            var path=new java.util.ArrayList<Integer>();for(int i=0;i<12;i++)path.add((bits>>i)&1);
            int pocket=Plinko.pocket(path);counts[pocket]++;gross+=Plinko.payout(1000,pocket);
            assertEquals(Plinko.payout(1000,pocket),Plinko.payout(1000,12-pocket));
        }
        assertArrayEquals(new int[]{1,12,66,220,495,792,924,792,495,220,66,12,1},counts);
        assertEquals(3_773_000,gross); // 92.1142578125% before cent rounding.
    }
    @Test void wheelHasTwentyEquiprobableSectorsAndExactly95PercentReturn(){
        long gross=0;for(int i=0;i<20;i++){final int sector=i;assertEquals(i,FortuneWheel.draw(bound->sector));gross+=FortuneWheel.payout(1000,i);}
        assertEquals(19000,gross);assertEquals(5000,FortuneWheel.maximumPayout(1000));
    }
    @Test void crossingHasFourSafeDrawsAndOneCrash(){int safe=0;for(int i=0;i<5;i++){final int draw=i;if(ChickenRoad.cross(bound->draw))safe++;}assertEquals(4,safe);}
    @Test void successfulCrossingsIncreaseCashoutAndTenCrossingsAutomaticallySettle(){
        GameState game=road();long previous=game.stake();
        for(int s=1;s<=10;s++){game=game.chicken(true,bound->1);assertEquals(s,game.reels().size());long cashout=ChickenRoad.payout(game.stake(),s);assertTrue(cashout>previous);previous=cashout;assertEquals(s==10,game.finished());}
        assertEquals(88475,game.payout());GameState ended=game;assertThrows(IllegalStateException.class,()->ended.chicken(true,bound->1));
    }
    @Test void crashPaysNothingAndCannotBeUndoneOrCashedOut(){GameState crashed=road().chicken(true,bound->1).chicken(true,bound->0);assertTrue(crashed.finished());assertEquals(List.of(1,0),crashed.reels());assertEquals(0,crashed.payout());assertThrows(IllegalStateException.class,()->crashed.chicken(false,bound->1));}
    @Test void closingCashoutUsesNoNewRandomDrawAndNoCrossingRefundsStake(){
        var fresh=road().chicken(false,bound->{fail("Cashout drew randomness");return 0;});assertEquals(10000,fresh.payout());
        var safe=road().chicken(true,bound->1).chicken(true,bound->1).chicken(false,bound->{fail("Cashout drew randomness");return 0;});assertEquals(14843,safe.payout());assertTrue(safe.finished());
    }
    @Test void eachNewGameReservesWorstCaseAndConservesMoneyAfterSettlementAndClaim(){
        for(var kind:List.of(CasinoLedger.Kind.PLINKO,CasinoLedger.Kind.WHEEL,CasinoLedger.Kind.CHICKEN)){
            CasinoLedger ledger=bank(kind);ledger=ledger.startBet(machine,player,1000,round,1,1000);
            assertEquals(CasinoLedger.maximumPayout(kind,1000),ledger.machine(machine).orElseThrow().reserved());
            var held=ledger;assertThrows(DomainException.class,()->held.withdraw(machine,owner,held.machine(machine).orElseThrow().available()+1));
            long payout=CasinoLedger.maximumPayout(kind,1000);ledger=ledger.settle(round,payout,2000);
            assertEquals(1_001_000,ledger.machine(machine).orElseThrow().bank()+ledger.credit(player));assertEquals(payout,ledger.credit(player));
            var paid=ledger;assertThrows(DomainException.class,()->paid.settle(round,payout,3000));ledger=ledger.claim(player,payout);assertEquals(0,ledger.credit(player));
        }
    }
    @Test void roundingUsesExactIntegerArithmeticIncludingLargeSafeStakes(){
        assertEquals(0,Plinko.payout(1,6));assertEquals(0,FortuneWheel.payout(1,1));assertEquals(1,ChickenRoad.payout(1,1));
        long stake=Money.MAX_CENTS/32;assertEquals(stake*32,Plinko.maximumPayout(stake));assertThrows(DomainException.class,()->Plinko.maximumPayout(stake+1));
        assertEquals(0,Money.ratio(Money.MAX_CENTS,0,1000));
    }
    @Test void malformedRandomnessAndPathsAreRejected(){
        assertThrows(DomainException.class,()->Plinko.draw(bound->2));assertThrows(DomainException.class,()->Plinko.pocket(List.of(0)));
        assertThrows(DomainException.class,()->FortuneWheel.draw(bound->20));assertThrows(DomainException.class,()->ChickenRoad.cross(bound->5));
        assertThrows(DomainException.class,()->ChickenRoad.validate(List.of(0,1),true));assertThrows(DomainException.class,()->ChickenRoad.validate(List.of(0),false));
        assertThrows(DomainException.class,()->ChickenRoad.payout(100,11));
    }
    @Test void tamperedPersistentChickenPayoutOrUnfinishedCrashIsRejected(){
        assertThrows(IllegalArgumentException.class,()->new GameState(round,machine,player,CasinoLedger.Kind.CHICKEN,10000,1000,61000,2,null,List.of(1),-1,"",-1,50000,true));
        assertThrows(DomainException.class,()->new GameState(round,machine,player,CasinoLedger.Kind.CHICKEN,10000,1000,61000,2,null,List.of(0),-1,"",-1,0,false));
    }
}
