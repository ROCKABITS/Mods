package pt.realmcasino.domain;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** Twelve fair left/right decisions: every path has probability 1/4096. */
public final class Plinko {
    public static final int ROWS=12;
    public static final List<Integer> RETURNS=List.of(32000,12000,4000,2000,1000,500,250,500,1000,2000,4000,12000,32000);
    private Plinko() {}
    public static List<Integer> draw(IntUnaryOperator random) {
        var path=new java.util.ArrayList<Integer>();
        for(int i=0;i<ROWS;i++){int bit=random.applyAsInt(2);if(bit<0||bit>1)throw new DomainException("invalid");path.add(bit);}
        return List.copyOf(path);
    }
    public static int pocket(List<Integer> path) {
        if(path==null||path.size()!=ROWS||path.stream().anyMatch(v->v==null||v<0||v>1))throw new DomainException("invalid");
        return path.stream().mapToInt(Integer::intValue).sum();
    }
    public static long payout(long stake,int pocket) {
        Money.positive(stake);if(pocket<0||pocket>ROWS)throw new DomainException("invalid");
        return Money.ratio(stake,RETURNS.get(pocket),1000);
    }
    public static long maximumPayout(long stake){return payout(stake,0);}
    public static boolean permitted(long stake,long payout){return RETURNS.stream().anyMatch(v->Money.ratio(stake,v,1000)==payout);}
}
