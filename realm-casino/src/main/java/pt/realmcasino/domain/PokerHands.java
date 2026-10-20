package pt.realmcasino.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Standard five-card hand ranking; exhaustive best-of-seven, including the ace-low straight. */
public final class PokerHands {
    private PokerHands(){}
    public record Rank(int category,List<Integer> kickers) implements Comparable<Rank>{
        public Rank{kickers=List.copyOf(kickers);if(category<0||category>8||kickers.isEmpty()||kickers.size()>5||kickers.stream().anyMatch(n->n<2||n>14))throw new DomainException("invalid");}
        @Override public int compareTo(Rank other){int result=Integer.compare(category,other.category);if(result!=0)return result;for(int i=0;i<Math.min(kickers.size(),other.kickers.size());i++){result=Integer.compare(kickers.get(i),other.kickers.get(i));if(result!=0)return result;}return Integer.compare(kickers.size(),other.kickers.size());}
    }
    public static Rank best(List<Integer> cards){
        if(cards==null||cards.size()<5||cards.size()>7||new HashSet<>(cards).size()!=cards.size())throw new DomainException("invalid");cards.forEach(Blackjack::rank);
        Rank best=null;for(int a=0;a<cards.size()-4;a++)for(int b=a+1;b<cards.size()-3;b++)for(int c=b+1;c<cards.size()-2;c++)for(int d=c+1;d<cards.size()-1;d++)for(int e=d+1;e<cards.size();e++){
            Rank rank=five(List.of(cards.get(a),cards.get(b),cards.get(c),cards.get(d),cards.get(e)));if(best==null||rank.compareTo(best)>0)best=rank;
        }return best;
    }
    private static Rank five(List<Integer> cards){
        int[] counts=new int[15];boolean flush=true;int suit=Blackjack.suit(cards.getFirst());for(int card:cards){int n=Blackjack.rank(card);counts[n==1?14:n]++;flush&=Blackjack.suit(card)==suit;}
        var descending=new ArrayList<Integer>();for(int n=14;n>=2;n--)for(int i=0;i<counts[n];i++)descending.add(n);
        int straight=0;for(int high=14;high>=5;high--){boolean run=true;for(int k=0;k<5;k++)run&=counts[high-k==1?14:high-k]>0;if(run){straight=high;break;}}
        if(flush&&straight>0)return new Rank(8,List.of(straight));
        int four=0,three=0;var pairs=new ArrayList<Integer>();for(int n=14;n>=2;n--){if(counts[n]==4)four=n;if(counts[n]==3)three=n;if(counts[n]==2)pairs.add(n);}
        if(four>0)return new Rank(7,List.of(four,highestOther(counts,four)));
        if(three>0&&!pairs.isEmpty())return new Rank(6,List.of(three,pairs.getFirst()));
        if(flush)return new Rank(5,descending);if(straight>0)return new Rank(4,List.of(straight));
        if(three>0){var k=new ArrayList<Integer>();k.add(three);for(int n:descending)if(n!=three)k.add(n);return new Rank(3,k);}
        if(pairs.size()==2)return new Rank(2,List.of(pairs.get(0),pairs.get(1),descending.stream().filter(n->!pairs.contains(n)).findFirst().orElseThrow()));
        if(pairs.size()==1){var k=new ArrayList<Integer>();k.add(pairs.getFirst());for(int n:descending)if(n!=pairs.getFirst())k.add(n);return new Rank(1,k);}
        return new Rank(0,descending);
    }
    private static int highestOther(int[] counts,int excluded){for(int n=14;n>=2;n--)if(n!=excluded&&counts[n]>0)return n;throw new DomainException("invalid");}
}
