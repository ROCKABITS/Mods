package pt.realmvillagereconomy.domain;

import java.math.BigInteger;

/** Exact cents. Public tax is rounded down once on the base price of one confirmed trade. */
public record TradePrice(long base,long tax,long total,int rate){
    public static final long MAX=900_000_000_000_000_000L;
    public TradePrice{if(base<0||tax<0||total<0||total>MAX||rate<0||rate>10000||Math.addExact(base,tax)!=total)throw new IllegalArgumentException("Invalid price");}
    public static TradePrice of(int emeralds,long centsPerEmerald,int approved,int cap,boolean treasury){
        if(emeralds<0||emeralds>128||centsPerEmerald<1||centsPerEmerald>MAX/128||approved<0||approved>10000||cap<0||cap>10000)throw new IllegalArgumentException("Invalid conversion");
        int rate=treasury?Math.min(approved,cap):0;long base=Math.multiplyExact(emeralds,centsPerEmerald);
        long tax=BigInteger.valueOf(base).multiply(BigInteger.valueOf(rate)).divide(BigInteger.valueOf(10000)).longValueExact();
        return new TradePrice(base,tax,Math.addExact(base,tax),rate);
    }
}
