package pt.realmcasino.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** One fresh 52-card deck per hand. Dealer stands on all 17s, including soft 17. */
public final class Blackjack {
    private Blackjack() {}
    public enum Status { PLAYER_TURN, FINISHED }
    public enum Outcome { NONE, LOSS, PUSH, WIN, BLACKJACK }
    public record Hand(int total, boolean soft, boolean natural, boolean bust) {}
    public record State(List<Integer> deck, int cursor, List<Integer> player, List<Integer> dealer,
                        Status status, Outcome outcome) {
        public State {
            deck = List.copyOf(Objects.requireNonNull(deck, "deck"));
            player = List.copyOf(Objects.requireNonNull(player, "player"));
            dealer = List.copyOf(Objects.requireNonNull(dealer, "dealer"));
            Objects.requireNonNull(status, "status"); Objects.requireNonNull(outcome, "outcome");
            validateDeck(deck);
            if (player.size() < 2 || dealer.size() < 2 || cursor != player.size() + dealer.size()
                    || cursor > 52) throw new DomainException("invalid");
            ArrayList<Integer> consumed = new ArrayList<>(player); consumed.addAll(dealer);
            if (new HashSet<>(consumed).size() != cursor
                    || !new HashSet<>(consumed).equals(new HashSet<>(deck.subList(0, cursor))))
                throw new DomainException("invalid");
            if (player.get(0).intValue() != deck.get(0) || dealer.get(0).intValue() != deck.get(1)
                    || player.get(1).intValue() != deck.get(2) || dealer.get(1).intValue() != deck.get(3))
                throw new DomainException("invalid");
            for (int i = 2; i < player.size(); i++) {
                if (player.get(i).intValue() != deck.get(i + 2)
                        || hand(player.subList(0, i)).total() >= 21)
                    throw new DomainException("invalid");
            }
            for (int i = 2; i < dealer.size(); i++) {
                if (dealer.get(i).intValue() != deck.get(player.size() + i)
                        || hand(dealer.subList(0, i)).total() >= 17)
                    throw new DomainException("invalid");
            }
            boolean initialNatural = hand(player.subList(0, 2)).natural() || hand(dealer.subList(0, 2)).natural();
            if (initialNatural && cursor != 4) throw new DomainException("invalid");
            if ((status == Status.PLAYER_TURN) != (outcome == Outcome.NONE))
                throw new DomainException("invalid");
            if (status == Status.PLAYER_TURN && (hand(player).total() >= 21 || hand(player).natural()
                    || hand(dealer).natural() || dealer.size() != 2))
                throw new DomainException("invalid");
            if (status == Status.FINISHED && outcome != resolve(player, dealer))
                throw new DomainException("invalid");
            if (status == Status.FINISHED && !initialNatural && !hand(player).bust() && hand(dealer).total() < 17)
                throw new DomainException("invalid");
            if (hand(player).bust() && dealer.size() != 2)
                throw new DomainException("invalid");
        }
        public State hit() { return Blackjack.hit(this); }
        public State stand() { return Blackjack.stand(this); }
        public boolean finished() { return status == Status.FINISHED; }
        public long payout(long stake) {
            requireStake(stake);
            if (!finished()) throw new DomainException("unfinished");
            return switch (outcome) {
                case LOSS -> 0;
                case PUSH -> stake;
                case WIN -> Money.multiply(stake, 2);
                case BLACKJACK -> Money.checked(Math.addExact(Money.multiply(stake, 2), stake / 2));
                default -> throw new DomainException("invalid");
            };
        }
    }
    public static List<Integer> shuffledDeck(IntUnaryOperator entropy) {
        Objects.requireNonNull(entropy, "entropy");
        ArrayList<Integer> deck = new ArrayList<>();
        for (int card = 0; card < 52; card++) deck.add(card);
        for (int end = 51; end > 0; end--) {
            int other = entropy.applyAsInt(end + 1);
            if (other < 0 || other > end) throw new DomainException("invalid");
            int previous = deck.get(end); deck.set(end, deck.get(other)); deck.set(other, previous);
        }
        return List.copyOf(deck);
    }
    public static State deal(IntUnaryOperator entropy) { return deal(shuffledDeck(entropy)); }
    public static State deal(List<Integer> deck) {
        validateDeck(deck);
        List<Integer> player = List.of(deck.get(0), deck.get(2));
        List<Integer> dealer = List.of(deck.get(1), deck.get(3));
        boolean terminal = hand(player).natural() || hand(dealer).natural();
        return new State(deck, 4, player, dealer, terminal ? Status.FINISHED : Status.PLAYER_TURN,
                terminal ? resolve(player, dealer) : Outcome.NONE);
    }
    private static State hit(State state) {
        requireTurn(state);
        ArrayList<Integer> player = new ArrayList<>(state.player());
        player.add(state.deck().get(state.cursor()));
        int cursor = state.cursor() + 1;
        if (hand(player).bust()) return new State(state.deck(), cursor, player, state.dealer(), Status.FINISHED, Outcome.LOSS);
        if (hand(player).total() == 21) return dealerTurn(state.deck(), cursor, player, state.dealer());
        return new State(state.deck(), cursor, player, state.dealer(), Status.PLAYER_TURN, Outcome.NONE);
    }
    private static State stand(State state) {
        requireTurn(state);
        return dealerTurn(state.deck(), state.cursor(), state.player(), state.dealer());
    }
    private static State dealerTurn(List<Integer> deck, int cursor, List<Integer> player, List<Integer> previousDealer) {
        ArrayList<Integer> dealer = new ArrayList<>(previousDealer);
        while (hand(dealer).total() < 17) dealer.add(deck.get(cursor++));
        return new State(deck, cursor, player, dealer, Status.FINISHED, resolve(player, dealer));
    }
    public static Hand hand(List<Integer> cards) {
        Objects.requireNonNull(cards, "cards");
        if (cards.size() > 52) throw new DomainException("invalid");
        int total = 0, aces = 0;
        for (int card : cards) {
            int rank = rank(card);
            if (rank == 1) { total += 11; aces++; }
            else total += Math.min(rank, 10);
        }
        while (total > 21 && aces > 0) { total -= 10; aces--; }
        return new Hand(total, aces > 0, cards.size() == 2 && total == 21, total > 21);
    }
    public static int rank(int card) {
        if (card < 0 || card >= 52) throw new DomainException("invalid");
        return card % 13 + 1;
    }
    public static int suit(int card) { rank(card); return card / 13; }
    public static long maximumPayout(long stake) {
        requireStake(stake);
        return Money.checked(Math.addExact(Money.multiply(stake, 2), stake / 2));
    }
    public static void requireStake(long stake) {
        if (Money.positive(stake) % 2 != 0) throw new DomainException("blackjack_even");
    }
    private static Outcome resolve(List<Integer> player, List<Integer> dealer) {
        Hand p = hand(player), d = hand(dealer);
        if (p.bust()) return Outcome.LOSS;
        if (p.natural() || d.natural()) return p.natural() && d.natural() ? Outcome.PUSH : p.natural() ? Outcome.BLACKJACK : Outcome.LOSS;
        if (d.bust() || p.total() > d.total()) return Outcome.WIN;
        return p.total() == d.total() ? Outcome.PUSH : Outcome.LOSS;
    }
    private static void requireTurn(State state) {
        Objects.requireNonNull(state, "state");
        if (state.finished()) throw new DomainException("round_finished");
    }
    private static void validateDeck(List<Integer> deck) {
        Objects.requireNonNull(deck, "deck");
        if (deck.size() != 52 || new HashSet<>(deck).size() != 52) throw new DomainException("invalid");
        deck.forEach(Blackjack::rank);
    }
}
