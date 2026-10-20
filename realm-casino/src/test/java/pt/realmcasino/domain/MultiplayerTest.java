package pt.realmcasino.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerTest {
    static final UUID A=new UUID(0,1),B=new UUID(0,2),C=new UUID(0,3),D=new UUID(0,4),M=new UUID(0,5);
    TableGame lobby(CasinoLedger.Kind kind,long stake){return TableGame.lobby(UUID.randomUUID(),M,kind,A,stake,0,0,1000).join(B,stake);}
    TableGame poker(long seed){return lobby(CasinoLedger.Kind.POKER,1000).start(2000,new Random(seed)::nextInt);}
    TableGame passive(TableGame game){int actions=0;while(!game.finished()){assertTrue(actions++<100,"Hand did not terminate");var seat=game.seats().get(game.actor());game=game.act(seat.player(),game.kind()==CasinoLedger.Kind.POKER?(seat.streetBet()<game.currentBet()?"call":"check"):"stand",0,2001+actions);}return game;}
    @Test void fiveReelAll32768OutcomesMatchThePublishedReturn(){long gross=0;int wins=0;for(int n=0;n<32768;n++){var reels=new ArrayList<Integer>();int value=n;for(int k=0;k<5;k++){reels.add(value%8);value/=8;}int m=FiveReelSlots.multiplier(reels);gross+=m;if(m>0)wins++;}assertEquals(31128,gross);assertEquals(512,wins);assertEquals(1000,FiveReelSlots.multiplier(List.of(0,0,0,0,0)));assertEquals(100,FiveReelSlots.multiplier(List.of(0,0,0,1,0)));assertEquals(0,FiveReelSlots.multiplier(List.of(1,0,0,0,0)));}
    @Test void horsePermutationIsFairAndEveryRankAndPayoutIsValidated(){int[] winners=new int[4];for(int first=0;first<4;first++)for(int second=0;second<3;second++)for(int third=0;third<2;third++){int[] sequence={first,second,third};int[] index={0};var order=HorseRace.draw(bound->sequence[index[0]++]);winners[order.getFirst()]++;for(int choice=0;choice<4;choice++)assertEquals(choice==order.getFirst()?380:0,HorseRace.payout(100,choice,order));}assertArrayEquals(new int[]{6,6,6,6},winners);assertThrows(DomainException.class,()->HorseRace.validate(List.of(0,0,1,2)));}
    @Test void pokerRanksRoyalFlushWheelAndFullHouseCorrectly(){
        assertEquals(8,PokerHands.best(List.of(9,10,11,12,0,14,28)).category());
        var wheel=PokerHands.best(List.of(0,14,28,42,4,20,34));assertEquals(4,wheel.category());assertEquals(List.of(5),wheel.kickers());
        var house=PokerHands.best(List.of(0,13,26,1,14,7,35));assertEquals(6,house.category());
        var flush=PokerHands.best(List.of(0,2,5,8,11,14,29));assertEquals(5,flush.category());assertTrue(house.compareTo(flush)>0);
        assertThrows(DomainException.class,()->PokerHands.best(List.of(0,0,1,2,3)));
    }
    @Test void boardRoyalFlushTiesEvenWithDifferentHoleCards(){var board=List.of(9,10,11,12,0);var a=new ArrayList<>(board);a.addAll(List.of(14,28));var b=new ArrayList<>(board);b.addAll(List.of(15,29));assertEquals(0,PokerHands.best(a).compareTo(PokerHands.best(b)));}
    @Test void blindsAndHeadsUpTurnOrderAreRealPoker(){var game=poker(1);assertEquals(0,game.actor());assertEquals(20,game.currentBet());assertEquals(10,game.seats().get(0).streetBet());assertEquals(20,game.seats().get(1).streetBet());game=game.act(A,"call",0,3000);assertEquals(B,game.seats().get(game.actor()).player());game=game.act(B,"check",0,3001);assertEquals(1,game.street());assertEquals(3,game.board().size());assertEquals(B,game.seats().get(game.actor()).player());}
    @Test void checkCannotAvoidCallingAndOutOfTurnActionCannotChangeState(){var game=poker(1);assertThrows(DomainException.class,()->game.act(A,"check",0,3000));assertThrows(DomainException.class,()->game.act(B,"raise",50,3000));assertEquals(30,game.pot());}
    @Test void raisesEnforceMinimumAndAvailableStack(){var game=poker(1);assertThrows(DomainException.class,()->game.act(A,"raise",30,3000));assertThrows(DomainException.class,()->game.act(A,"raise",1001,3000));var raised=game.act(A,"raise",40,3000);assertEquals(40,raised.currentBet());assertEquals(B,raised.seats().get(raised.actor()).player());assertEquals(20,raised.minimumRaise());}
    @Test void foldAwardsOnlyCommittedMoneyAndReturnsUnusedStacks(){var result=poker(1).act(A,"fold",0,3000);assertTrue(result.finished());assertEquals(990,result.payouts().get(A));assertEquals(1010,result.payouts().get(B));assertEquals(2000,result.payouts().values().stream().mapToLong(Long::longValue).sum());}
    @Test void headsUpAllInCallsRunOutTheBoardWithoutExtraBets(){var game=poker(3).act(A,"allin",0,3000);assertFalse(game.finished());game=game.act(B,"call",0,3001);assertTrue(game.finished());assertEquals(5,game.board().size());assertEquals(2000,game.payouts().values().stream().mapToLong(Long::longValue).sum());}
    @Test void hundredsOfActualHandsConserveTheWholeBuyInAndNeverReuseACard(){for(int count=0;count<300;count++){var game=passive(poker(count));assertEquals(2000,game.payouts().values().stream().mapToLong(Long::longValue).sum());assertEquals(5,game.board().size());assertEquals(52,java.util.stream.Stream.concat(java.util.stream.Stream.concat(game.deck().stream(),game.board().stream()),game.seats().stream().flatMap(s->s.cards().stream())).distinct().count());}}
    @Test void sixPlayerMixedRaisesFoldsAndAllInsAlwaysConserveEscrow(){for(int seed=0;seed<100;seed++){var random=new Random(seed);var game=lobby(CasinoLedger.Kind.POKER,1000).join(C,1000).join(D,1000).join(new UUID(0,6),1000).join(new UUID(0,7),1000).start(2000,random::nextInt);int actions=0;while(!game.finished()){assertTrue(actions++<500);var seat=game.seats().get(game.actor());String move=random.nextInt(8)==0?"fold":random.nextInt(5)==0&&game.raiseRights().contains(seat.player())?"allin":seat.streetBet()<game.currentBet()?"call":"check";game=game.act(seat.player(),move,0,3000+actions);}assertEquals(6000,game.payouts().values().stream().mapToLong(Long::longValue).sum());}}
    @Test void shortAllInDoesNotReopenAnAlreadyUsedRaiseRight(){
        TableGame game=lobby(CasinoLedger.Kind.POKER,100).join(C,100).start(2000,new Random(9)::nextInt);
        var seats=new ArrayList<>(game.seats());var small=seats.get(1);seats.set(1,new TableGame.Seat(small.player(),25,75,1,false,false,small.cards()));
        var first=seats.get(0);seats.set(0,new TableGame.Seat(first.player(),100,0,0,false,false,first.cards()));
        // A previously built side pot may leave one seat with less than a full raise in the next street.
        // Exercise the rule through a valid street snapshot with all values conserved.
        seats= new ArrayList<>(List.of(new TableGame.Seat(A,100,0,0,false,false,game.seats().get(0).cards()),new TableGame.Seat(B,25,75,0,false,false,game.seats().get(1).cards()),new TableGame.Seat(C,100,0,0,false,false,game.seats().get(2).cards())));
        game=new TableGame(game.id(),M,game.kind(),100,1000,61000,5,TableGame.Phase.PLAYING,0,0,0,0,20,0,seats,game.deck(),game.board(),List.of(A,B,C),List.of(A,B,C),List.of(),Map.of());
        game=game.act(A,"raise",20,3000);game=game.act(B,"allin",0,3001);assertEquals(25,game.currentBet());assertFalse(game.raiseRights().contains(A));assertTrue(game.raiseRights().contains(C));game=game.act(C,"call",0,3002);var restricted=game;assertThrows(DomainException.class,()->restricted.act(A,"raise",60,3003));
    }
    @Test void nonActingDepartureDoesNotSkipTheCurrentPlayersTurn(){var game=lobby(CasinoLedger.Kind.POKER,1000).join(C,1000).start(2000,new Random(2)::nextInt);UUID current=game.seats().get(game.actor()).player();UUID other=game.seats().stream().map(TableGame.Seat::player).filter(id->!id.equals(current)).findFirst().orElseThrow();var after=game.departure(other,3000);assertFalse(after.finished());assertEquals(current,after.seats().get(after.actor()).player());}
    @Test void pokerTimeoutChecksWhenFreeAndFoldsWhenFacingABet(){var game=poker(2).timeout(62000);assertTrue(game.finished());var free=poker(2).act(A,"call",0,3000).act(B,"check",0,3001);var after=free.timeout(63000);assertFalse(after.seats().get(free.actor()).folded());}
    @Test void multiplayerBlackjackHasPrivateHandsAndConservesThePot(){for(int seed=0;seed<100;seed++){var game=lobby(CasinoLedger.Kind.BLACKJACK_PVP,100).join(C,100).start(2000,new Random(seed)::nextInt);var finished=passive(game);assertEquals(300,finished.payouts().values().stream().mapToLong(Long::longValue).sum());assertTrue(finished.finished());}}
    @Test void blackjackDepartureStandsWithoutRedealing(){var game=lobby(CasinoLedger.Kind.BLACKJACK_PVP,100).start(2000,new Random(7)::nextInt);var cards=game.seats().get(0).cards();game=game.departure(A,3000);assertEquals(cards,game.seats().get(0).cards());assertTrue(game.seats().get(0).stood());}
    @Test void coinAndDicePayOnlyFundedBetsAndSplitTies(){var coin=lobby(CasinoLedger.Kind.COIN,101).start(2000,bound->0);assertEquals(202,coin.payouts().get(A));assertEquals(0,coin.payouts().get(B));var dice=lobby(CasinoLedger.Kind.DICE,101).join(C,101).start(2000,bound->3);assertEquals(101,dice.payouts().get(A));assertEquals(101,dice.payouts().get(B));assertEquals(101,dice.payouts().get(C));}
    @Test void lobbyCannotStartWithOneOrJoinAfterTheDeal(){var game=TableGame.lobby(UUID.randomUUID(),M,CasinoLedger.Kind.POKER,A,100,0,0,1000);assertThrows(DomainException.class,()->game.start(2000,new Random()::nextInt));var playing=poker(2);assertThrows(DomainException.class,()->playing.join(C,1000));assertThrows(DomainException.class,()->lobby(CasinoLedger.Kind.COIN,100).join(C,100));}
    @Test void ledgerSeatsReserveMoneyWithoutUsingTheOwnersBank(){var ledger=new CasinoLedger().register(M,A,CasinoLedger.Kind.POKER,"minecraft:overworld",1,"Poker",2,1000);ledger=ledger.joinTable(M,A,1000,UUID.randomUUID(),0,0,1000);assertEquals(0,ledger.machine(M).orElseThrow().bank());assertEquals(java.math.BigInteger.valueOf(1000),ledger.total());assertTrue(ledger.tableBusy(M));var held=ledger;assertThrows(DomainException.class,()->held.remove(M,A));assertThrows(DomainException.class,()->held.joinTable(M,A,1000,UUID.randomUUID(),1,0,1100));ledger=ledger.joinTable(M,B,1000,UUID.randomUUID(),1,0,1200);var game=ledger.tables().get(M).start(2000,new Random(1)::nextInt);ledger=ledger.updateTable(game,2000);game=game.act(A,"fold",0,3000);ledger=ledger.updateTable(game,3000);assertEquals(java.math.BigInteger.valueOf(2000),ledger.total());assertFalse(ledger.tableBusy(M));var settled=ledger;var finishedGame=game;assertThrows(DomainException.class,()->settled.updateTable(finishedGame,3001));}
    @Test void lobbyRefundIsOnceAndAnotherGameCannotDropEscrow(){var ledger=new CasinoLedger().register(M,A,CasinoLedger.Kind.DICE,"minecraft:overworld",1,"Dice",1,1000);ledger=ledger.joinTable(M,A,100,UUID.randomUUID(),0,0,1000);ledger=ledger.configure(M,A,"Changed",1,1000);assertEquals(100,ledger.tables().get(M).escrow());ledger=ledger.leaveTable(M,A,2000);assertEquals(100,ledger.credit(A));assertTrue(ledger.tables().isEmpty());var returned=ledger;assertThrows(DomainException.class,()->returned.leaveTable(M,A,2001));}
    @Test void casinoTaxOnlyAppliesAfterCapitalHasBeenRecovered(){var ledger=new CasinoLedger().register(M,A,CasinoLedger.Kind.WHEEL,"minecraft:overworld",1,"Crown",1,100).deposit(M,A,1000);UUID round=UUID.randomUUID();ledger=ledger.startBet(M,B,100,round,1,1000).settle(round,0,1001);assertEquals(0,ledger.taxableProfit(M,1000));UUID receipt=UUID.randomUUID();ledger=ledger.withdrawTax(M,A,1100,5,receipt);assertEquals(1095,ledger.credit(A));assertEquals(5,ledger.taxes().get(receipt).amount());assertEquals(0,ledger.machine(M).orElseThrow().capital());assertEquals(java.math.BigInteger.valueOf(1100),ledger.total());ledger=ledger.taxPaid(receipt);assertEquals(java.math.BigInteger.valueOf(1095),ledger.total());var paid=ledger;assertThrows(DomainException.class,()->paid.taxPaid(receipt));}
    @Test void lossesCarryForwardAndDepositsAreNeverTaxedAsProfit(){var ledger=new CasinoLedger().register(M,A,CasinoLedger.Kind.WHEEL,"minecraft:overworld",1,"Crown",1,100).deposit(M,A,1000);var round=UUID.randomUUID();ledger=ledger.startBet(M,B,100,round,1,1000).settle(round,200,1001);assertEquals(1000,ledger.machine(M).orElseThrow().capital());assertEquals(0,ledger.taxableProfit(M,900));ledger=ledger.deposit(M,A,100);assertEquals(1100,ledger.machine(M).orElseThrow().capital());assertEquals(0,ledger.taxableProfit(M,1000));}
    @Test void unequalPokerBuyInsPayMainAndSidePotsOnlyToEligibleHands(){
        var a=new TableGame.Seat(A,0,100,0,false,false,List.of(0,13));
        var b=new TableGame.Seat(B,0,200,0,false,false,List.of(12,25));
        var c=new TableGame.Seat(C,100,200,0,false,false,List.of(11,24));
        var board=List.of(1,28,19,46,8);var used=new java.util.HashSet<>(board);for(var seat:List.of(a,b,c))used.addAll(seat.cards());var deck=java.util.stream.IntStream.range(0,52).filter(n->!used.contains(n)).boxed().toList();
        var game=new TableGame(UUID.randomUUID(),M,CasinoLedger.Kind.POKER,100,1000,61000,12,TableGame.Phase.PLAYING,0,2,3,0,2,0,List.of(a,b,c),deck,board,List.of(C),List.of(C),List.of(),Map.of());
        game=game.act(C,"check",0,3000);assertEquals(300,game.payouts().get(A));assertEquals(200,game.payouts().get(B));assertEquals(100,game.payouts().get(C));
        var lobby=lobby(CasinoLedger.Kind.POKER,100).join(C,300);assertEquals(500,lobby.escrow());
    }

    @Test void reducingLimitsCannotReleaseCreditCapacityAlreadyReservedForSeatedPlayers(){
        var ledger=new CasinoLedger(List.of(),List.of(),Map.of(A,Money.MAX_CENTS-6000),List.of())
                .register(M,A,CasinoLedger.Kind.POKER,"minecraft:overworld",1,"Poker",2,1000)
                .joinTable(M,A,1000,UUID.randomUUID(),0,0,1000).configure(M,A,"Low limits",2,2);
        assertEquals(1000,ledger.tables().get(M).escrow());
        // Capacity includes the old seated buy-in even after limits are reduced.
        var source=ledger;assertThrows(DomainException.class,()->new CasinoLedger(source.machines().values(),source.rounds().values(),Map.of(A,Money.MAX_CENTS-5999),source.history(),source.tables(),source.taxes()));
    }
}
