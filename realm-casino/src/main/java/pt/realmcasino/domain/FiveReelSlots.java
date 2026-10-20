package pt.realmcasino.domain;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** One payline, paid for three, four or five matching symbols from the left. */
public final class FiveReelSlots {
    public static final List<Integer> THREE=List.of(100,80,60,50,40,30,24,16);
    public static final List<Integer> FOUR=List.of(200,160,120,100,80,60,48,32);
    public static final List<Integer> FIVE=List.of(1000,800,500,300,200,160,100,68);
    public static final int MAX_MULTIPLIER=1000;
    private FiveReelSlots(){}
    public static List<Integer> draw(IntUnaryOperator entropy){var reels=new java.util.ArrayList<Integer>();for(int i=0;i<5;i++){int v=entropy.applyAsInt(8);if(v<0||v>=8)throw new DomainException("invalid");reels.add(v);}return List.copyOf(reels);}
    public static int multiplier(List<Integer> reels){
        if(reels==null||reels.size()!=5||reels.stream().anyMatch(v->v==null||v<0||v>=8))throw new DomainException("invalid");
        int count=1;while(count<5&&reels.get(count).equals(reels.getFirst()))count++;
        return count<3?0:(count==3?THREE:count==4?FOUR:FIVE).get(reels.getFirst());
    }
    public static long payout(long stake,List<Integer> reels){return Money.multiply(Money.positive(stake),multiplier(reels));}
    public static boolean permitted(long stake,long payout){return java.util.stream.Stream.of(THREE,FOUR,FIVE).flatMap(List::stream).anyMatch(m->Money.multiply(stake,m)==payout);}
}
