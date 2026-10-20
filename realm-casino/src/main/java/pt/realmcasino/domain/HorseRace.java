package pt.realmcasino.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Four equally likely miniature horses; 3.8x gross return, 95% theoretical return before cent rounding. */
public final class HorseRace {
    private HorseRace(){}
    public static List<Integer> draw(IntUnaryOperator random){var order=new ArrayList<>(List.of(0,1,2,3));for(int i=3;i>0;i--){int j=random.applyAsInt(i+1);if(j<0||j>i)throw new DomainException("invalid");java.util.Collections.swap(order,i,j);}return List.copyOf(order);}
    public static void validate(List<Integer> order){if(order==null||order.size()!=4||!new HashSet<>(order).equals(java.util.Set.of(0,1,2,3)))throw new DomainException("invalid");}
    public static long payout(long stake,int selection,List<Integer> order){Money.positive(stake);validate(order);if(selection<0||selection>3)throw new DomainException("invalid");return order.getFirst()==selection?Money.ratio(stake,38,10):0;}
    public static long maximumPayout(long stake){return Money.ratio(Money.positive(stake),38,10);}
}
