package pt.realmcasino.domain;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** Twenty equally likely sectors. Gross theoretical return is exactly 95%, before cent rounding. */
public final class FortuneWheel {
    public static final List<Integer> RETURNS=List.of(0,500,1000,0,1500,500,0,2000,500,1000,0,500,3000,0,500,1000,0,500,5000,1500);
    private FortuneWheel() {}
    public static int draw(IntUnaryOperator random){int n=random.applyAsInt(RETURNS.size());if(n<0||n>=RETURNS.size())throw new DomainException("invalid");return n;}
    public static long payout(long stake,int sector){Money.positive(stake);if(sector<0||sector>=RETURNS.size())throw new DomainException("invalid");return Money.ratio(stake,RETURNS.get(sector),1000);}
    public static long maximumPayout(long stake){Money.positive(stake);return Money.multiply(stake,5);}
    public static boolean permitted(long stake,long payout){return RETURNS.stream().anyMatch(v->Money.ratio(stake,v,1000)==payout);}
}
