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
            if(amount<1||amount>MAX_AMOUNT)throw new IllegalArgumentException("Invalid funded income amount");
            if(memo==null||memo.isBlank()||memo.length()>256||memo.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid income description");
        }
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
