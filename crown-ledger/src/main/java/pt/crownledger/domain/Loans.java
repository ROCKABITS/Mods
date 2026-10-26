package pt.crownledger.domain;

import java.util.*;
import pt.crownledger.domain.Treasury.Currency;
import pt.crownledger.domain.Treasury.Kind;

/** Fixed-interest public credit. Offered principal is escrow, not spendable treasury money. */
public final class Loans {
    public static final int MAX_LOANS=4096,MAX_EVENTS=512;
    public static final long DAY=86_400_000L,MAX_PRINCIPAL=1_000_000_000L;
    public enum State { REQUESTED,OFFERED,ACTIVE,SETTLED,DECLINED,CANCELLED,EXPIRED }
    public record Limits(long maximum,int maximumInterest,int maximumInstallments,int maximumIntervalDays,
                         long offerLifetime,long requestLifetime,long requestCooldown) {
        public Limits {
            if(maximum<1||maximum>MAX_PRINCIPAL||maximumInterest<0||maximumInterest>10000||maximumInstallments<1||maximumInstallments>36||maximumIntervalDays<1||maximumIntervalDays>365||offerLifetime<1000||offerLifetime>30*DAY||requestLifetime<1000||requestLifetime>30*DAY||requestCooldown<0||requestCooldown>DAY)throw error("limits");
        }
    }
    public record Loan(UUID id,UUID borrower,String name,String purpose,Currency currency,long principal,State state,
                       long requestedAt,long offeredAt,long expiresAt,UUID approver,String approverName,
                       int interest,int installments,int intervalDays,long signedAt,long paid,long closedAt) {
        public Loan {
            Objects.requireNonNull(id);Objects.requireNonNull(borrower);Objects.requireNonNull(currency);Objects.requireNonNull(state);
            name=clean(name,64,false);purpose=clean(purpose,160,true);approverName=clean(approverName,64,true);
            if(principal<1||principal>MAX_PRINCIPAL||requestedAt<0||offeredAt<0||expiresAt<0||signedAt<0||closedAt<0||interest<0||interest>10000||installments<0||installments>36||intervalDays<0||intervalDays>365||paid<0)throw error("invalid");
            boolean terms=approver!=null;
            if(terms&&(offeredAt<requestedAt||approverName.isBlank()||installments<1||intervalDays<1||installments>Loans.total(principal,interest)))throw error("invalid");
            if(!terms&&(offeredAt!=0||!approverName.isEmpty()||interest!=0||installments!=0||intervalDays!=0||signedAt!=0||paid!=0))throw error("invalid");
            if((state==State.OFFERED||state==State.ACTIVE||state==State.SETTLED)&&!terms)throw error("invalid");
            if(state==State.REQUESTED&&(terms||expiresAt<=requestedAt))throw error("invalid");
            if(state==State.OFFERED&&(expiresAt<=offeredAt||signedAt!=0||paid!=0))throw error("invalid");
            if((state==State.ACTIVE||state==State.SETTLED)&&(signedAt<offeredAt||signedAt==0||expiresAt!=0))throw error("invalid");
            if(paid>Loans.total(principal,interest)||state==State.SETTLED&&paid!=Loans.total(principal,interest)||state==State.ACTIVE&&paid==Loans.total(principal,interest))throw error("invalid");
            if(state!=State.ACTIVE&&state!=State.SETTLED&&(signedAt!=0||paid!=0))throw error("invalid");
            boolean closed=state==State.SETTLED||state==State.DECLINED||state==State.CANCELLED||state==State.EXPIRED;
            if(closed!=(closedAt>0)||closedAt>0&&closedAt<requestedAt)throw error("invalid");
            if(terms&&signedAt>0)Math.addExact(signedAt,Math.multiplyExact((long)installments*intervalDays,DAY));
        }
        public long total(){return Loans.total(principal,interest);}
        public long remaining(){return state==State.ACTIVE||state==State.SETTLED?total()-paid:0;}
        public boolean open(){return state==State.REQUESTED||state==State.OFFERED||state==State.ACTIVE;}
        public long installment(int number){if(number<1||number>installments)throw error("invalid");return total()/installments+(number<=total()%installments?1:0);}
        public long cumulative(int number){if(number<0||number>installments)throw error("invalid");return Math.addExact(total()/installments*number,Math.min(number,total()%installments));}
        public int completed(){int result=0;while(result<installments&&paid>=cumulative(result+1))result++;return result;}
        public long nextPayment(){return state==State.ACTIVE?cumulative(completed()+1)-paid:0;}
        public long dueAt(int number){if(signedAt==0||number<1||number>installments)return 0;return Math.addExact(signedAt,Math.multiplyExact((long)number*intervalDays,DAY));}
        public long nextDueAt(){return state==State.ACTIVE?dueAt(completed()+1):0;}
        public boolean overdue(long now){return state==State.ACTIVE&&now>nextDueAt();}
    }
    public record Event(UUID id,UUID loan,UUID actor,String name,String action,long amount,long at) {
        public Event {Objects.requireNonNull(id);Objects.requireNonNull(loan);Objects.requireNonNull(actor);name=clean(name,64,false);action=clean(action,24,false);if(!Set.of("request","offer","accept","repay","settle","decline","cancel","expire").contains(action)||amount<0||at<0)throw error("invalid");}
    }
    public record Snapshot(Map<UUID,Loan> loans,List<Event> events,long lastObservedAt){}
    private final Map<UUID,Loan> loans=new LinkedHashMap<>();
    private final List<Event> events=new ArrayList<>();
    private long lastObservedAt;
    public Loans(){}
    public Loans(Snapshot snapshot){
        Objects.requireNonNull(snapshot);if(snapshot.lastObservedAt()<0||snapshot.loans()==null||snapshot.loans().size()>MAX_LOANS||snapshot.events()==null||snapshot.events().size()>MAX_EVENTS)throw error("invalid");
        Set<UUID> open=new HashSet<>();snapshot.loans().forEach((id,loan)->{if(id==null||loan==null||!id.equals(loan.id())||loan.open()&&!open.add(loan.borrower()))throw error("invalid");loans.put(id,loan);});
        Set<UUID> seen=new HashSet<>();for(var event:snapshot.events()){if(event==null||!loans.containsKey(event.loan())||!seen.add(event.id()))throw error("invalid");events.add(event);}
        lastObservedAt=snapshot.lastObservedAt();
        for(var loan:loans.values())if(Math.max(Math.max(loan.requestedAt(),loan.offeredAt()),Math.max(loan.signedAt(),loan.closedAt()))>lastObservedAt)throw error("invalid");
        for(var event:events)if(event.at()>lastObservedAt)throw error("invalid");
    }
    public Snapshot snapshot(){return new Snapshot(Map.copyOf(loans),List.copyOf(events),lastObservedAt);}
    public Loans copy(){return new Loans(snapshot());}
    public long lastObservedAt(){return lastObservedAt;}
    public Collection<Loan> all(){return List.copyOf(loans.values());}
    public List<Event> events(){return List.copyOf(events);}
    public Loan get(UUID id){var loan=loans.get(id);if(loan==null)throw error("missing");return loan;}
    public Loan open(UUID borrower){return loans.values().stream().filter(l->l.borrower().equals(borrower)&&l.open()).findFirst().orElse(null);}
    public long observe(long now){if(now<0)throw error("invalid");lastObservedAt=Math.max(now,lastObservedAt);return lastObservedAt;}
    public boolean hasDue(long now){return loans.values().stream().anyMatch(l->(l.state()==State.REQUESTED||l.state()==State.OFFERED)&&l.expiresAt()<=now);}
    public UUID request(UUID borrower,String name,String purpose,Currency currency,long principal,Limits limits,long now){
        Objects.requireNonNull(limits);if(open(borrower)!=null)throw error("one_open");if(loans.size()>=MAX_LOANS)throw error("capacity");
        if(principal<1||principal>limits.maximum())throw error("amount");now=observe(now);
        long last=loans.values().stream().filter(l->l.borrower().equals(borrower)).mapToLong(Loan::requestedAt).max().orElse(-1);
        if(last>=0&&now-last<limits.requestCooldown())throw error("cooldown");
        UUID id=UUID.randomUUID();var loan=new Loan(id,borrower,name,purpose,currency,principal,State.REQUESTED,now,0,Math.addExact(now,limits.requestLifetime()),null,"",0,0,0,0,0,0);
        loans.put(id,loan);audit(loan,borrower,name,"request",principal,now);return id;
    }
    public void offer(Treasury treasury,UUID id,UUID actor,String name,int interest,int count,int days,Limits limits,boolean emergency,long now){
        Loan loan=get(id);now=observe(now);if(loan.state()!=State.REQUESTED||now>=loan.expiresAt())throw error("stale");
        if(actor.equals(loan.borrower())&&!emergency)throw error("self_approval");
        if(loan.principal()>limits.maximum()||interest<0||interest>limits.maximumInterest()||count<1||count>limits.maximumInstallments()||days<1||days>limits.maximumIntervalDays())throw error("limits");
        Loan next=new Loan(id,loan.borrower(),loan.name(),loan.purpose(),loan.currency(),loan.principal(),State.OFFERED,loan.requestedAt(),now,Math.addExact(now,limits.offerLifetime()),actor,name,interest,count,days,0,0,0);
        treasury.removeFunds(loan.currency(),loan.principal(),Kind.LOAN_RESERVED,actor,loan.name(),now);
        loans.put(id,next);audit(next,actor,name,"offer",loan.principal(),now);
    }
    public void accept(UUID id,UUID borrower,String name,long now){
        Loan loan=owned(id,borrower);now=observe(now);if(loan.state()!=State.OFFERED||now>=loan.expiresAt())throw error("stale");
        Loan next=new Loan(id,borrower,loan.name(),loan.purpose(),loan.currency(),loan.principal(),State.ACTIVE,loan.requestedAt(),loan.offeredAt(),0,loan.approver(),loan.approverName(),loan.interest(),loan.installments(),loan.intervalDays(),now,0,0);
        loans.put(id,next);audit(next,borrower,name,"accept",loan.principal(),now);
    }
    public long payment(UUID id,UUID borrower,boolean full){Loan loan=owned(id,borrower);if(loan.state()!=State.ACTIVE)throw error("stale");return full?loan.remaining():loan.nextPayment();}
    public void repay(Treasury treasury,UUID id,UUID borrower,String name,long amount,long now){
        Loan loan=owned(id,borrower);if(loan.state()!=State.ACTIVE||amount!=loan.nextPayment()&&amount!=loan.remaining())throw error("stale");
        now=observe(now);long paid=Math.addExact(loan.paid(),amount);boolean settled=paid==loan.total();
        Loan next=new Loan(id,borrower,loan.name(),loan.purpose(),loan.currency(),loan.principal(),settled?State.SETTLED:State.ACTIVE,loan.requestedAt(),loan.offeredAt(),0,loan.approver(),loan.approverName(),loan.interest(),loan.installments(),loan.intervalDays(),loan.signedAt(),paid,settled?now:0);
        treasury.addFunds(loan.currency(),amount,Kind.LOAN_REPAYMENT,borrower,loan.name(),now);treasury.settlePending(now);
        loans.put(id,next);audit(next,borrower,name,settled?"settle":"repay",amount,now);
    }
    public void close(Treasury treasury,UUID id,UUID actor,String name,boolean manager,long now){
        Loan loan=get(id);now=observe(now);if(loan.state()!=State.REQUESTED&&loan.state()!=State.OFFERED)throw error("stale");
        if(!manager&&!loan.borrower().equals(actor))throw error("authority");
        State state=manager?State.DECLINED:State.CANCELLED;close(treasury,loan,state,actor,name,now);
    }
    public boolean expire(Treasury treasury,long now){now=observe(now);boolean changed=false;for(var loan:List.copyOf(loans.values()))if((loan.state()==State.REQUESTED||loan.state()==State.OFFERED)&&loan.expiresAt()<=now){close(treasury,loan,State.EXPIRED,loan.borrower(),loan.name(),now);changed=true;}return changed;}
    private void close(Treasury treasury,Loan loan,State state,UUID actor,String name,long now){
        if(loan.state()==State.OFFERED)treasury.addFunds(loan.currency(),loan.principal(),Kind.LOAN_RETURNED,actor,loan.name(),now);
        var next=new Loan(loan.id(),loan.borrower(),loan.name(),loan.purpose(),loan.currency(),loan.principal(),state,loan.requestedAt(),loan.offeredAt(),loan.expiresAt(),loan.approver(),loan.approverName(),loan.interest(),loan.installments(),loan.intervalDays(),0,0,now);
        loans.put(loan.id(),next);audit(next,actor,name,state==State.EXPIRED?"expire":state==State.DECLINED?"decline":"cancel",loan.principal(),now);
    }
    private Loan owned(UUID id,UUID borrower){Loan loan=get(id);if(!loan.borrower().equals(borrower))throw error("authority");return loan;}
    private void audit(Loan loan,UUID actor,String name,String action,long amount,long now){events.add(0,new Event(UUID.randomUUID(),loan.id(),actor,name,action,amount,now));while(events.size()>MAX_EVENTS)events.removeLast();}
    public static long total(long principal,int interest){return Math.addExact(principal,Math.addExact(Math.multiplyExact(principal,interest),5000)/10000);}
    private static String clean(String text,int max,boolean blank){Objects.requireNonNull(text);if(text.length()>max||(!blank&&text.isBlank())||text.chars().anyMatch(c->Character.isISOControl(c)||c=='§'))throw error("invalid");return text;}
    private static IllegalStateException error(String key){return new IllegalStateException("realm.treasury.loan.error."+key);}
}
