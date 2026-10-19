package pt.lawsjustice.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fines, authorized bail and stable payment intents. Wallets never trust values from a client. */
public final class FinanceLedger {
    public static final long MAX_AMOUNT=900_000_000_000_000_000L;
    public static final int MAX_CHARGES=4096,MAX_PAYMENTS=8192;
    public static final String FINE="FINE",BAIL="BAIL",DUE="DUE",PAID="PAID",REVOKED="REVOKED",EXPIRED="EXPIRED";
    public static final String PREPARED="PREPARED",CANCELLED="CANCELLED",ESCROW="ESCROW",CROWN="CROWN";
    public record Charge(UUID id,String kind,String caseId,String sentence,UUID target,String targetName,
                         UUID author,String authorName,String authority,String reason,String currency,long amount,long issuedAt,
                         String status,long closedAt,String closedBy,String closedName,String closedAuthority,String closingReason) {
        public Charge {
            nn(id);nn(target);nn(author);require(Set.of(FINE,BAIL).contains(kind),"finance_kind");optionalUuid(caseId);optionalUuid(sentence);name(targetName);name(authorName);FinanceLedger.authority(authority);prose(reason,800);FinanceLedger.currency(currency);FinanceLedger.amount(amount);require(issuedAt>=0&&closedAt>=0,"date");
            require(kind.equals(FINE)?!caseId.isEmpty()&&sentence.isEmpty():caseId.isEmpty()&&!sentence.isEmpty(),"finance_reference");
            require(Set.of(DUE,PAID,REVOKED,EXPIRED).contains(status),"finance_status");optionalUuid(closedBy);text(closedName,0,64,false);text(closedAuthority,0,32,false);text(closingReason,0,300,true);
            if(status.equals(DUE))require(closedAt==0&&closedBy.isEmpty()&&closedName.isEmpty()&&closedAuthority.isEmpty()&&closingReason.isEmpty(),"finance_status");
            else{require(closedAt>=issuedAt,"date");prose(closingReason,300);
                if(status.equals(EXPIRED))require(kind.equals(BAIL)&&closedBy.isEmpty()&&closedName.isEmpty()&&closedAuthority.isEmpty(),"finance_status");
                else{require(!closedBy.isEmpty(),"finance_status");name(closedName);if(status.equals(REVOKED))FinanceLedger.authority(closedAuthority);else require(closedAuthority.isEmpty(),"finance_status");}
            }
        }
    }
    public record Payment(UUID id,UUID charge,UUID payer,String payerName,String accountName,String currency,long amount,
                          String memo,long preparedAt,String status,long settledAt,String destination,long transferredAt) {
        public Payment {
            nn(id);nn(charge);nn(payer);name(payerName);require(accountName!=null&&accountName.matches("[A-Za-z0-9_]{1,16}"),"finance_account");FinanceLedger.currency(currency);FinanceLedger.amount(amount);text(memo,1,256,false);
            require(preparedAt>=0&&settledAt>=0&&transferredAt>=0,"date");require(Set.of(PREPARED,PAID,CANCELLED).contains(status),"finance_status");
            if(status.equals(PREPARED))require(settledAt==0&&destination!=null&&destination.isEmpty()&&transferredAt==0,"finance_status");
            else if(status.equals(CANCELLED))require(settledAt>=preparedAt&&destination!=null&&destination.isEmpty()&&transferredAt==0,"finance_status");
            else require(settledAt>=preparedAt&&(destination.equals(ESCROW)&&transferredAt==0||destination.equals(CROWN)&&transferredAt>=settledAt),"finance_status");
        }
    }
    public record State(Map<UUID,Charge> charges,Map<UUID,Payment> payments) {
        public State {
            charges=copy(charges,MAX_CHARGES);payments=copy(payments,MAX_PAYMENTS);Set<String> cases=new HashSet<>(),bails=new HashSet<>();Set<UUID> charged=new HashSet<>();Map<String,Long> reserves=new LinkedHashMap<>();
            for(var e:charges.entrySet()){Charge c=e.getValue();require(e.getKey().equals(c.id()),"reference");if(c.kind().equals(FINE))require(cases.add(c.caseId()),"duplicate");else if(c.status().equals(DUE))require(bails.add(c.sentence()),"bail_exists");}
            Set<UUID> paidCharges=new HashSet<>();
            for(var e:payments.entrySet()){
                Payment p=e.getValue();Charge c=charges.get(p.charge());require(e.getKey().equals(p.id())&&c!=null&&p.amount()==c.amount()&&p.currency().equals(c.currency())&&p.preparedAt()>=c.issuedAt(),"finance_reference");
                if(c.kind().equals(FINE))require(c.target().equals(p.payer()),"finance_payer");
                if(!p.status().equals(CANCELLED)){require(charged.add(c.id()),"payment_exists");require(p.status().equals(PAID)?c.status().equals(PAID):c.status().equals(DUE),"finance_status");}
                if(p.status().equals(PAID)){paidCharges.add(c.id());require(c.closedBy().equals(p.payer().toString())&&c.closedAt()==p.settledAt()&&c.closedName().equals(p.payerName()),"finance_reference");}
                if(p.status().equals(PREPARED)||p.status().equals(PAID)&&p.destination().equals(ESCROW))reserves.merge(p.currency(),p.amount(),Math::addExact);
            }
            for(Charge c:charges.values())require(c.status().equals(PAID)==paidCharges.contains(c.id()),"finance_reference");
        }
    }
    private final State state;
    private FinanceLedger(State state){this.state=nn(state);}
    public static FinanceLedger empty(){return new FinanceLedger(new State(Map.of(),Map.of()));}
    public static FinanceLedger restore(State state){return new FinanceLedger(state);}
    public State state(){return state;}
    public Map<UUID,Charge> charges(){return state.charges();}
    public Map<UUID,Payment> payments(){return state.payments();}
    public Payment pending(UUID charge){return payments().values().stream().filter(p->p.charge().equals(charge)&&p.status().equals(PREPARED)).findFirst().orElse(null);}
    public long preparedCount(){return payments().values().stream().filter(p->p.status().equals(PREPARED)).count();}
    public long escrow(String currency){currency(currency);long total=0;for(Payment p:payments().values())if(p.currency().equals(currency)&&p.status().equals(PAID)&&p.destination().equals(ESCROW))total=Math.addExact(total,p.amount());return total;}
    public FinanceLedger issueFine(UUID id,UUID caseId,UUID target,String targetName,UUID author,String authorName,String authority,String reason,String currency,long amount,long now){return issue(new Charge(id,FINE,nn(caseId).toString(),"",target,targetName,author,authorName,authority,reason,currency,amount,now,DUE,0,"","","",""));}
    public FinanceLedger offerBail(UUID id,UUID sentence,UUID target,String targetName,UUID author,String authorName,String authority,String reason,String currency,long amount,long now){return issue(new Charge(id,BAIL,"",nn(sentence).toString(),target,targetName,author,authorName,authority,reason,currency,amount,now,DUE,0,"","","",""));}
    private FinanceLedger issue(Charge charge){require(!charges().containsKey(charge.id()),"duplicate");Map<UUID,Charge> changed=new LinkedHashMap<>(charges());changed.put(charge.id(),charge);return with(changed,payments());}
    public FinanceLedger revoke(UUID id,UUID actor,String actorName,String authority,String reason,long now){Charge c=charge(id);require(c.status().equals(DUE)&&pending(id)==null,"payment_exists");nn(actor);authority(authority);name(actorName);prose(reason,300);return with(put(close(c,REVOKED,actor.toString(),actorName,authority,reason,now)),payments());}
    public FinanceLedger prepare(UUID id,UUID charge,UUID payer,String payerName,String accountName,long now){
        require(now>=0,"date");require(!payments().containsKey(id),"duplicate");Charge c=charge(charge);require(c.status().equals(DUE)&&pending(charge)==null,"payment_exists");
        String memo=(c.kind().equals(FINE)?"Fine — ":"Bail — ")+c.targetName();Payment p=new Payment(id,c.id(),payer,payerName,accountName,c.currency(),c.amount(),memo,Math.max(now,c.issuedAt()),PREPARED,0,"",0);
        Map<UUID,Payment> changed=new LinkedHashMap<>(payments());changed.put(id,p);return with(charges(),changed);
    }
    public FinanceLedger settle(UUID id,long now){
        require(now>=0,"date");Payment before=payment(id);if(before.status().equals(PAID))return this;require(before.status().equals(PREPARED),"payment_cancelled");Charge c=charge(before.charge());require(c.status().equals(DUE),"finance_status");long at=Math.max(now,before.preparedAt());
        Payment after=new Payment(before.id(),before.charge(),before.payer(),before.payerName(),before.accountName(),before.currency(),before.amount(),before.memo(),before.preparedAt(),PAID,at,ESCROW,0);
        Map<UUID,Payment> changed=new LinkedHashMap<>(payments());changed.put(id,after);return with(put(close(c,PAID,before.payer().toString(),before.payerName(),"","Voluntary payment",at)),changed);
    }
    public FinanceLedger transferred(UUID id,long now){
        require(now>=0,"date");Payment p=payment(id);require(p.status().equals(PAID),"payment_unfunded");if(p.destination().equals(CROWN))return this;
        Payment after=new Payment(p.id(),p.charge(),p.payer(),p.payerName(),p.accountName(),p.currency(),p.amount(),p.memo(),p.preparedAt(),PAID,p.settledAt(),CROWN,Math.max(now,p.settledAt()));Map<UUID,Payment> changed=new LinkedHashMap<>(payments());changed.put(id,after);return with(charges(),changed);
    }
    /** The service must first establish that no authoritative player receipt exists. */
    public FinanceLedger cancelPayment(UUID id,long now){
        require(now>=0,"date");Payment p=payment(id);require(p.status().equals(PREPARED),"payment_exists");Payment after=new Payment(p.id(),p.charge(),p.payer(),p.payerName(),p.accountName(),p.currency(),p.amount(),p.memo(),p.preparedAt(),CANCELLED,Math.max(now,p.preparedAt()),"",0);Map<UUID,Payment> changed=new LinkedHashMap<>(payments());changed.put(id,after);return with(charges(),changed);
    }
    public FinanceLedger reconcile(JusticeLedger custody,long now){
        require(now>=0,"date");Map<UUID,Charge> changed=null;
        for(Charge c:charges().values())if(c.kind().equals(BAIL)&&c.status().equals(DUE)&&pending(c.id())==null){var s=custody.active().get(c.target());if(s==null||!s.id().toString().equals(c.sentence())){if(changed==null)changed=new LinkedHashMap<>(charges());changed.put(c.id(),close(c,EXPIRED,"","","","Sentence ended",now));}}
        return changed==null?this:with(changed,payments());
    }
    private Charge charge(UUID id){Charge c=charges().get(nn(id));require(c!=null,"charge_missing");return c;}
    private Payment payment(UUID id){Payment p=payments().get(nn(id));require(p!=null,"payment_missing");return p;}
    private static Charge close(Charge c,String status,String actor,String name,String authority,String reason,long now){require(now>=0,"date");return new Charge(c.id(),c.kind(),c.caseId(),c.sentence(),c.target(),c.targetName(),c.author(),c.authorName(),c.authority(),c.reason(),c.currency(),c.amount(),c.issuedAt(),status,Math.max(now,c.issuedAt()),actor,name,authority,reason);}
    private Map<UUID,Charge> put(Charge c){Map<UUID,Charge> changed=new LinkedHashMap<>(charges());changed.put(c.id(),c);return changed;}
    private FinanceLedger with(Map<UUID,Charge> charges,Map<UUID,Payment> payments){return new FinanceLedger(new State(charges,payments));}
    private static <T>Map<UUID,T> copy(Map<UUID,T> supplied,int maximum){require(supplied!=null&&supplied.size()<=maximum,"capacity");Map<UUID,T> result=new LinkedHashMap<>();for(var e:supplied.entrySet())result.put(nn(e.getKey()),nn(e.getValue()));return Collections.unmodifiableMap(result);}
    private static void currency(String currency){require("EMERALD".equals(currency)||"CHRONICLES".equals(currency),"finance_currency");}
    private static void amount(long amount){require(amount>0&&amount<=MAX_AMOUNT,"finance_amount");}
    private static void authority(String kind){require(CourtLedger.PRESIDENT.equals(kind)||CourtLedger.ADMIN_EMERGENCY.equals(kind),"court_authority");}
    private static void name(String s){text(s,1,64,false);}
    private static void prose(String s,int max){text(s,1,max,true);}
    private static void text(String s,int min,int max,boolean prose){require(s!=null&&s.length()>=min&&s.length()<=max&&(min==0||!s.isBlank())&&s.codePoints().noneMatch(c->Character.isISOControl(c)&&(!prose||c!='\n'&&c!='\r')),"finance_text");}
    private static void optionalUuid(String value){require(value!=null,"reference");if(!value.isEmpty())require(UUID.fromString(value).toString().equals(value),"reference");}
    private static <T>T nn(T value){require(value!=null,"invalid");return value;}
    private static void require(boolean valid,String key){if(!valid)throw new IllegalArgumentException("lawsjustice.error."+key);}
}
