package pt.crownledger.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable idempotency keys for already-funded income from other server mods. */
public final class IncomeLedger {
    public static final int MAX_RECEIPTS=65_536;
    public static final long MAX_AMOUNT=900_000_000_000_000_000L;
    public record Receipt(UUID id,Treasury.Currency currency,long amount,UUID payer,String memo) {
        public Receipt {
            Objects.requireNonNull(id,"Receipt ID");Objects.requireNonNull(currency,"Currency");Objects.requireNonNull(payer,"Payer");
            if(amount<0||amount>MAX_AMOUNT||amount==0&&!audit(memo))throw new IllegalArgumentException("Invalid funded income amount");
            if(audit(memo)&&(amount!=0||currency!=Treasury.Currency.CHRONICLES))throw new IllegalArgumentException("Fiscal approvals carry no money and use the Chronicle audit currency");
            if(memo==null||memo.isBlank()||memo.length()>256||memo.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid income description");
        }
    }
    public static boolean validPolicy(String memo){
        if(memo==null||!memo.startsWith("realmvillagereconomy.policy:")||!memo.substring("realmvillagereconomy.policy:".length()).matches("[0-9]{1,5}:[0-9]{1,16}"))return false;
        String[] parts=memo.split(":");try{return Integer.parseInt(parts[1])<=10000&&Long.parseLong(parts[2])>=0;}catch(NumberFormatException invalid){return false;}
    }
    public static boolean audit(String memo){return validPolicy(memo)||validEconomicPolicy(memo);}
    public static final java.util.Set<String> ECONOMIC_SERVICES=java.util.Set.of("VILLAGER","ANVIL_REPAIR","ANVIL_COMBINE","ANVIL_RENAME","ENCHANT","SMITHING","BREWING","BEACON");
    public static boolean validEconomicPolicy(String memo){
        if(memo==null||memo.length()>256||!memo.startsWith("realmeconomy.policy:"))return false;
        String[] p=memo.split(":",-1);if(p.length!=5||!p[2].matches("[0-9]{1,16}"))return false;
        try{if(Long.parseLong(p[2])<0)return false;if(p[1].equals("BEACON_PERIOD"))return validPeriod(p[3])&&validPeriod(p[4]);return ECONOMIC_SERVICES.contains(p[1])&&validSnapshot(p[3])&&validSnapshot(p[4]);}catch(RuntimeException invalid){return false;}
    }
    private static boolean validPeriod(String p){if(!p.matches("[0-9]{1,5}"))return false;int n=Integer.parseInt(p);return n>=10&&n<=86400;}
    private static boolean validSnapshot(String text){
        if(!text.matches("[0-9]+,[0-9]+,[0-9]+,[0-9]+,[0-9]+,[01]"))return false;String[] p=text.split(",");long value=Long.parseLong(p[0]),min=Long.parseLong(p[1]),max=Long.parseLong(p[2]);int tax=Integer.parseInt(p[3]),cap=Integer.parseInt(p[4]);return value>=min&&value<=max&&min>=0&&max<=1_000_000_000_000L&&tax>=0&&tax<=cap&&cap<=10000;
    }
    private final Map<UUID,Receipt> receipts;
    public IncomeLedger(){this(Map.of());}
    public IncomeLedger(Map<UUID,Receipt> supplied){
        Objects.requireNonNull(supplied,"Receipts");if(supplied.size()>MAX_RECEIPTS)throw new IllegalArgumentException("Income receipt capacity reached");
        Map<UUID,Receipt> copy=new LinkedHashMap<>();supplied.forEach((id,receipt)->{if(id==null||receipt==null||!id.equals(receipt.id()))throw new IllegalArgumentException("Invalid income receipt reference");copy.put(id,receipt);});receipts=Collections.unmodifiableMap(copy);
    }
    public Map<UUID,Receipt> receipts(){return receipts;}
    /** A repeated key is harmless only when its complete frozen payment payload matches. */
    public boolean contains(Receipt receipt){Objects.requireNonNull(receipt,"Receipt");Receipt previous=receipts.get(receipt.id());if(previous==null)return false;if(!previous.equals(receipt))throw new IllegalArgumentException("Income receipt payload changed");return true;}
    public IncomeLedger add(Receipt receipt){
        if(contains(receipt))return this;if(receipts.size()>=MAX_RECEIPTS)throw new IllegalStateException("Income receipts are full; preserve existing idempotency keys");
        Map<UUID,Receipt> next=new LinkedHashMap<>(receipts);next.put(receipt.id(),receipt);return new IncomeLedger(next);
    }
}
