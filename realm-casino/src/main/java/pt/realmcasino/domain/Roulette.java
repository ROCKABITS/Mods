package pt.realmcasino.domain;

import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** European roulette: one zero and 36 numbered pockets, with fixed published returns. */
public final class Roulette {
    public static final int POCKETS = 37;
    public static final int MAX_MULTIPLIER = 36;
    private static final Set<Integer> RED = Set.of(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36);
    private Roulette() {}
    public enum Type { NUMBER, RED, BLACK, EVEN, ODD }
    public record Bet(Type type, int number) {
        public Bet {
            Objects.requireNonNull(type, "type");
            if (type == Type.NUMBER) pocket(number);
            else if (number != -1) throw new DomainException("invalid");
        }
        public static Bet number(int number) { return new Bet(Type.NUMBER, number); }
        public static Bet outside(Type type) { return new Bet(type, -1); }
        public boolean wins(int result) {
            pocket(result);
            if (type == Type.NUMBER) return result == number;
            if (result == 0) return false;
            return switch (type) {
                case RED -> isRed(result);
                case BLACK -> !isRed(result);
                case EVEN -> result % 2 == 0;
                case ODD -> result % 2 != 0;
                default -> false;
            };
        }
        public int multiplier(int result) { return wins(result) ? type == Type.NUMBER ? 36 : 2 : 0; }
        public long payout(long stake, int result) { return Money.multiply(Money.positive(stake), multiplier(result)); }
    }
    public static int draw(IntUnaryOperator entropy) {
        int result = Objects.requireNonNull(entropy, "entropy").applyAsInt(POCKETS);
        pocket(result);
        return result;
    }
    public static boolean isRed(int number) { pocket(number); return RED.contains(number); }
    private static void pocket(int value) {
        if (value < 0 || value >= POCKETS) throw new DomainException("invalid");
    }
}
