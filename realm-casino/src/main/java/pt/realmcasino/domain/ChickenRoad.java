package pt.realmcasino.domain;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** Each crossing succeeds with probability 4/5. A stage-n cashout returns 95%/(.8^n) gross. */
public final class ChickenRoad {
    public static final int MAX_STEPS=10;
    private ChickenRoad() {}
    public static boolean cross(IntUnaryOperator random){int n=random.applyAsInt(5);if(n<0||n>=5)throw new DomainException("invalid");return n!=0;}
    public static long payout(long stake,int steps){
        Money.positive(stake);if(steps<0||steps>MAX_STEPS)throw new DomainException("invalid");
        if(steps==0)return stake;
        long five=1,four=1;for(int i=0;i<steps;i++){five*=5;four*=4;}
        return Money.ratio(stake,95L*five,100L*four);
    }
    public static long maximumPayout(long stake){return payout(stake,MAX_STEPS);}
    public static boolean permitted(long stake,long payout){for(int s=0;s<=MAX_STEPS;s++)if(payout(stake,s)==payout)return true;return false;}
    public static boolean crashed(List<Integer> path){return !path.isEmpty()&&path.getLast()==0;}
    public static void validate(List<Integer> path,boolean finished){
        if(path==null||path.size()>MAX_STEPS)throw new DomainException("invalid");
        for(int i=0;i<path.size();i++)if(path.get(i)==null||(path.get(i)!=1&&!(finished&&i==path.size()-1&&path.get(i)==0)))throw new DomainException("invalid");
        if(!finished&&path.size()==MAX_STEPS)throw new DomainException("invalid");
    }
}
