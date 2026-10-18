package pt.realmcasino.domain;

import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Three independent uniform eight-symbol reels. All returns include the original stake. */
public final class Slots {
    public static final int SYMBOL_COUNT = 8;
    public static final List<String> SYMBOLS = List.of("crown", "ruby", "sapphire", "emerald", "bell", "moon", "horseshoe", "cherry");
    public static final List<Integer> TRIPLE_RETURNS = List.of(100, 80, 50, 32, 24, 16, 12, 6);
    public static final int MAX_MULTIPLIER = 100;
    public static final int RETURN_NUMERATOR = 488;
    public static final int RETURN_DENOMINATOR = 512;
    public static final String RTP_PERCENT = "95.3125";
    private Slots() {}

    public record Result(int first, int second, int third, int multiplier) {
        public Result {
            symbol(first); symbol(second); symbol(third);
            if (multiplier != Slots.multiplier(first, second, third))
                throw new DomainException("invalid");
        }
        public List<Integer> reels() { return List.of(first, second, third); }
        public long payout(long stake) { return Money.multiply(Money.positive(stake), multiplier); }
    }
    public static Result result(int first, int second, int third) {
        return new Result(first, second, third, multiplier(first, second, third));
    }
    public static Result draw(IntUnaryOperator entropy) {
        Objects.requireNonNull(entropy, "entropy");
        return result(entropy.applyAsInt(SYMBOL_COUNT), entropy.applyAsInt(SYMBOL_COUNT), entropy.applyAsInt(SYMBOL_COUNT));
    }
    public static int multiplier(int first, int second, int third) {
        symbol(first); symbol(second); symbol(third);
        if (first == second && second == third) return TRIPLE_RETURNS.get(first);
        return first == second || first == third || second == third ? 1 : 0;
    }
    private static void symbol(int value) {
        if (value < 0 || value >= SYMBOL_COUNT) throw new DomainException("invalid");
    }
}
