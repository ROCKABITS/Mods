package pt.realmeconomy.domain;

/** A bounded policy, owned by the world. Values are exact cents; taxes are basis points. */
public record EconomyRule(long value,long minimum,long maximum,int tax,int taxCap,boolean enabled){
    public static final long MAX_VALUE=1_000_000_000_000L;
    public EconomyRule {
        if(minimum<0||maximum<minimum||maximum>MAX_VALUE||value<minimum||value>maximum||tax<0||tax>taxCap||taxCap>10000)throw new IllegalArgumentException("Invalid economic bounds");
    }
    public EconomyRule price(long next,int nextTax){return new EconomyRule(next,minimum,maximum,nextTax,taxCap,enabled);}
    public EconomyRule limits(long min,long max,int cap,boolean active){return new EconomyRule(Math.clamp(value,min,max),min,max,Math.min(tax,cap),cap,active);}
    public String encode(){return value+","+minimum+","+maximum+","+tax+","+taxCap+","+(enabled?1:0);}
    public static EconomyRule decode(String text){
        if(text==null||text.length()>120||!text.matches("[0-9]+,[0-9]+,[0-9]+,[0-9]+,[0-9]+,[01]"))throw new IllegalArgumentException("Invalid economic policy");
        String[] p=text.split(",");return new EconomyRule(Long.parseLong(p[0]),Long.parseLong(p[1]),Long.parseLong(p[2]),Integer.parseInt(p[3]),Integer.parseInt(p[4]),p[5].equals("1"));
    }
    public TradePrice quote(long units,boolean treasury){
        if(units<0||units>1_000_000)throw new IllegalArgumentException("Invalid service quantity");
        long base=Math.multiplyExact(value,units);int rate=treasury?tax:0;
        long surcharge=java.math.BigInteger.valueOf(base).multiply(java.math.BigInteger.valueOf(rate)).divide(java.math.BigInteger.valueOf(10000)).longValueExact();
        return new TradePrice(base,surcharge,Math.addExact(base,surcharge),rate);
    }
}
