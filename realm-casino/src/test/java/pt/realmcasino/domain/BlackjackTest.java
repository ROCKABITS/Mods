package pt.realmcasino.domain;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackTest {
    static List<Integer> deck(int... prefix) {
        ArrayList<Integer> cards = new ArrayList<>();
        for (int card : prefix) {
            if (cards.contains(card)) throw new IllegalArgumentException("Duplicate test card");
            cards.add(card);
        }
        for (int card = 0; card < 52; card++) if (!cards.contains(card)) cards.add(card);
        return cards;
    }
    @Test void aPlayerNaturalReturnsFiveHalvesIncludingTheStake() {
        Blackjack.State state = Blackjack.deal(deck(0, 8, 12, 6)); // A,K against 9,7
        assertEquals(Blackjack.Outcome.BLACKJACK, state.outcome());
        assertEquals(250, state.payout(100));
        assertEquals(5, state.payout(2));
        assertThrows(DomainException.class, () -> state.payout(1));
    }
    @Test void dealerNaturalLosesAndTwoNaturalsPush() {
        assertEquals(Blackjack.Outcome.LOSS, Blackjack.deal(deck(8, 0, 6, 12)).outcome());
        Blackjack.State tied = Blackjack.deal(deck(0, 13, 12, 25));
        assertEquals(Blackjack.Outcome.PUSH, tied.outcome()); assertEquals(100, tied.payout(100));
    }
    @Test void dealerStandsOnSoftSeventeenWithoutTakingAnotherCard() {
        Blackjack.State result = Blackjack.deal(deck(9, 0, 7, 5, 12)).stand(); // 18 vs A,6
        assertEquals(4, result.cursor()); assertTrue(Blackjack.hand(result.dealer()).soft());
        assertEquals(Blackjack.Outcome.WIN, result.outcome()); assertEquals(200, result.payout(100));
    }
    @Test void dealerDrawsOnSixteenAndAHighCardCanBustIt() {
        Blackjack.State result = Blackjack.deal(deck(9, 8, 7, 6, 12)).stand(); //18 vs9,7,K
        assertEquals(5, result.cursor()); assertTrue(Blackjack.hand(result.dealer()).bust());
        assertEquals(Blackjack.Outcome.WIN, result.outcome());
    }
    @Test void aPlayerBustDoesNotRevealAnExtraDealerDraw() {
        Blackjack.State result = Blackjack.deal(deck(9, 8, 7, 6, 12)).hit();
        assertEquals(Blackjack.Outcome.LOSS, result.outcome()); assertEquals(0, result.payout(100));
        assertEquals(2, result.dealer().size()); assertEquals(5, result.cursor());
    }
    @Test void acesAreReducedOnlyAsNeededAndKeepSoftInformation() {
        Blackjack.Hand soft = Blackjack.hand(List.of(0, 13, 8)); // A,A,9 = soft21
        assertEquals(21, soft.total()); assertTrue(soft.soft()); assertFalse(soft.natural());
        Blackjack.Hand hard = Blackjack.hand(List.of(0, 13, 8, 12));
        assertEquals(21, hard.total()); assertFalse(hard.soft()); assertFalse(hard.bust());
    }
    @Test void hittingTwentyOneAutomaticallyCompletesTheDealerTurn() {
        Blackjack.State result = Blackjack.deal(deck(4, 9, 5, 7, 12)).hit(); //5,6,K vs10,8
        assertTrue(result.finished()); assertEquals(Blackjack.Outcome.WIN, result.outcome());
        assertFalse(Blackjack.hand(result.player()).natural()); assertEquals(200, result.payout(100));
    }
    @Test void anEqualOrdinaryTotalPushes() {
        Blackjack.State result = Blackjack.deal(deck(9, 22, 7, 20)).stand();
        assertEquals(Blackjack.Outcome.PUSH, result.outcome()); assertEquals(100, result.payout(100));
    }
    @Test void terminalHandsCannotBeHitOrStoodAgainAndActiveOnesCannotPay() {
        Blackjack.State active = Blackjack.deal(deck(9, 8, 7, 6));
        assertThrows(DomainException.class, () -> active.payout(100));
        Blackjack.State done = active.stand();
        assertThrows(DomainException.class, done::hit); assertThrows(DomainException.class, done::stand);
    }
    @Test void fisherYatesUsesExactShrinkingBoundsAndNeverReplacesCards() {
        AtomicInteger expected = new AtomicInteger(52);
        List<Integer> deck = Blackjack.shuffledDeck(bound -> { assertEquals(expected.getAndDecrement(), bound); return bound - 1; });
        assertEquals(1, expected.get()); assertEquals(52, new HashSet<>(deck).size());
        for (int i = 0; i < 52; i++) assertEquals(i, deck.get(i));
        assertThrows(DomainException.class, () -> Blackjack.shuffledDeck(bound -> bound));
    }
    @Test void aPersistedHandRoundTripsExactlyAndContinuesWithTheSameNextCard() {
        Blackjack.State original = Blackjack.deal(deck(4, 9, 5, 7, 2, 12)).hit();
        Blackjack.State restored = new Blackjack.State(original.deck(), original.cursor(), original.player(), original.dealer(), original.status(), original.outcome());
        assertEquals(original, restored); assertEquals(original.stand(), restored.stand());
        assertThrows(UnsupportedOperationException.class, () -> restored.deck().set(0, 4));
    }
    @Test void duplicateMissingAndRedistributedCardsCannotLoad() {
        ArrayList<Integer> corrupt = new ArrayList<>(deck()); corrupt.set(1, 0);
        assertThrows(DomainException.class, () -> Blackjack.deal(corrupt));
        List<Integer> deck = deck(4, 9, 5, 7, 2, 12);
        assertThrows(DomainException.class, () -> new Blackjack.State(deck, 6, List.of(4, 5, 12), List.of(9, 7, 2), Blackjack.Status.FINISHED, Blackjack.Outcome.WIN));
    }
    @Test void aFinishedWinWithAnUnfinishedDealerCannotLoad() {
        List<Integer> deck = deck(9, 8, 7, 5); //18 vs15, dealer must draw
        assertThrows(DomainException.class, () -> new Blackjack.State(deck, 4, List.of(9, 7), List.of(8, 5), Blackjack.Status.FINISHED, Blackjack.Outcome.WIN));
    }
    @Test void dealerCannotTakeCardsAfterSeventeenNorPlayerAfterTwentyOne() {
        List<Integer> deck = deck(4, 9, 5, 6, 12, 0); //player hit K=>21; dealer17 cannot draw
        assertThrows(DomainException.class, () -> new Blackjack.State(deck, 6, List.of(4, 5, 12), List.of(9, 6, 0), Blackjack.Status.FINISHED, Blackjack.Outcome.LOSS));
        List<Integer> deck2 = deck(4, 9, 5, 6, 12, 1);
        assertThrows(DomainException.class, () -> new Blackjack.State(deck2, 6, List.of(4, 5, 12, 1), List.of(9, 6), Blackjack.Status.FINISHED, Blackjack.Outcome.LOSS));
    }
    @Test void cardRanksAndSuitsMatchThePublicRenderingContract() {
        assertEquals(1, Blackjack.rank(0)); assertEquals(13, Blackjack.rank(12));
        assertEquals(1, Blackjack.rank(13)); assertEquals(0, Blackjack.suit(12)); assertEquals(3, Blackjack.suit(51));
        assertThrows(DomainException.class, () -> Blackjack.rank(52));
    }
}
