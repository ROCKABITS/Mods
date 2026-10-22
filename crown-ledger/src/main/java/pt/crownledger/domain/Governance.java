package pt.crownledger.domain;

import java.util.*;
import pt.crownledger.domain.Treasury.Currency;
import pt.crownledger.domain.Treasury.Kind;

/** Server-independent, persisted ballots. Money and this state must be committed in one treasury file. */
public final class Governance {
    public enum Benefit { HARVEST, WORKS, FEAST, KNOWLEDGE }
    public enum Outcome { APPROVED, REJECTED, NO_QUORUM, ADMIN_CANCELLED }
    public record Offer(Benefit benefit, Currency currency, long cost, long durationMillis, int strength) {
        public Offer {
            Objects.requireNonNull(benefit); Objects.requireNonNull(currency);
            if (cost < 1 || cost > 1_000_000_000L || durationMillis < 1000 || durationMillis > 604_800_000L || strength < 1 || strength > 100)
                throw new IllegalArgumentException("Invalid benefit offer");
        }
    }
    public record Proposal(UUID id, UUID author, String authorName, String question, String description, Offer offer,
                           long openedAt, long deadline, long cooldownUntil, int minimumVotes,
                           Set<UUID> eligible, Map<UUID, Boolean> ballots) {
        public Proposal {
            Objects.requireNonNull(id); Objects.requireNonNull(author);
            authorName = text(authorName, 64); question = text(question, 120); description = Governance.description(description);
            eligible = Set.copyOf(eligible); ballots = Map.copyOf(ballots);
            if (openedAt < 0 || deadline <= openedAt || deadline-openedAt > 1_800_000 || cooldownUntil < deadline ||
                    eligible.isEmpty() || eligible.size() > 2048 || minimumVotes < 1 || minimumVotes > eligible.size() ||
                    !eligible.containsAll(ballots.keySet())) throw new IllegalArgumentException("Invalid ballot");
        }
        public Proposal(UUID id,UUID author,String authorName,String question,Offer offer,long openedAt,long deadline,long cooldownUntil,int minimumVotes,Set<UUID> eligible,Map<UUID,Boolean> ballots){
            this(id,author,authorName,question,"",offer,openedAt,deadline,cooldownUntil,minimumVotes,eligible,ballots);
        }
        public int yes() { return (int)ballots.values().stream().filter(Boolean::booleanValue).count(); }
        public int no() { return ballots.size()-yes(); }
    }
    public record Active(UUID proposalId, Benefit benefit, long startsAt, long expiresAt, int strength) {
        public Active {
            Objects.requireNonNull(proposalId);Objects.requireNonNull(benefit);
            if(startsAt<0 || expiresAt<=startsAt || expiresAt-startsAt>604_800_000L || strength<1 || strength>100)
                throw new IllegalArgumentException("Invalid active benefit");
        }
    }
    public record Result(UUID id, UUID author, String authorName, String question, String description, Offer offer, Outcome outcome,
                         int yes, int no, int electorate, int minimumVotes, long finishedAt, UUID cancelledBy) {
        public Result {
            Objects.requireNonNull(id);Objects.requireNonNull(author);Objects.requireNonNull(outcome);
            authorName=text(authorName,64);question=text(question,120);description=Governance.description(description);
            if (yes<0 || no<0 || electorate<1 || electorate>2048 || yes+no>electorate || minimumVotes<1 || minimumVotes>electorate || finishedAt<0 ||
                    (outcome==Outcome.APPROVED && (yes<=no || yes+no<minimumVotes)) ||
                    (outcome==Outcome.ADMIN_CANCELLED)!=(cancelledBy!=null)) throw new IllegalArgumentException("Invalid ballot result");
        }
    }
    public record Refund(UUID proposalId, Currency currency, long amount) {
        public Refund { Objects.requireNonNull(proposalId);Objects.requireNonNull(currency);if(amount<1||amount>1_000_000_000L)throw new IllegalArgumentException("Invalid refund"); }
    }
    public record Snapshot(Proposal current, List<Active> active, List<Result> history, Refund refund,
                           long nextProposalAt, long lastObservedAt) {
        public Snapshot {
            active=List.copyOf(active);history=List.copyOf(history);
            if(active.size()>4 || history.size()>64 || nextProposalAt<0 || lastObservedAt<0 || current!=null&&refund!=null)
                throw new IllegalArgumentException("Invalid governance snapshot");
            if(active.stream().map(Active::benefit).distinct().count()!=active.size() ||
                    history.stream().map(Result::id).distinct().count()!=history.size() ||
                    current!=null && (current.openedAt()>lastObservedAt || nextProposalAt!=current.cooldownUntil() || history.stream().anyMatch(r->r.id().equals(current.id()))) ||
                    refund!=null && history.stream().noneMatch(r->r.id().equals(refund.proposalId()) && r.outcome()!=Outcome.APPROVED && r.offer()!=null && r.offer().currency()==refund.currency() && r.offer().cost()==refund.amount()))
                throw new IllegalArgumentException("Inconsistent governance snapshot");
        }
    }
    private Proposal current;
    private final List<Active> active=new ArrayList<>();
    private final List<Result> history=new ArrayList<>();
    private Refund refund;
    private long nextProposalAt,lastObservedAt;
    public Governance() {}
    public Governance(Snapshot s) { current=s.current();active.addAll(s.active());history.addAll(s.history());refund=s.refund();nextProposalAt=s.nextProposalAt();lastObservedAt=s.lastObservedAt(); }
    public Snapshot snapshot(){return new Snapshot(current,active,history,refund,nextProposalAt,lastObservedAt);}
    public Governance copy(){return new Governance(snapshot());}
    public Proposal current(){return current;}
    public List<Active> active(){return List.copyOf(active);}
    public List<Result> history(){return List.copyOf(history);}
    public Refund refund(){return refund;}
    public long nextProposalAt(){return nextProposalAt;}
    public long lastObservedAt(){return lastObservedAt;}
    public UUID propose(Treasury treasury,UUID author,String authorName,String question,Offer offer,Set<UUID> eligible,
                        int quorumPercent,int maxActive,long votingMillis,long cooldownMillis,long now){
        return propose(treasury,author,authorName,question,"",offer,eligible,quorumPercent,maxActive,votingMillis,cooldownMillis,now);
    }
    public UUID propose(Treasury treasury,UUID author,String authorName,String question,String description,Offer offer,Set<UUID> eligible,
                        int quorumPercent,int maxActive,long votingMillis,long cooldownMillis,long now){
        if(now<lastObservedAt || now<nextProposalAt)throw fail("cooldown");
        if(current!=null||refund!=null)throw fail("busy");
        if(quorumPercent<1||quorumPercent>100||maxActive<1||maxActive>4||votingMillis<1000||votingMillis>1_800_000||cooldownMillis<0||cooldownMillis>86_400_000)throw fail("invalid");
        if(offer!=null&&(active.stream().filter(a->a.expiresAt()>now).count()>=maxActive || active.stream().anyMatch(a->a.benefit()==offer.benefit()&&a.expiresAt()>now)))throw fail("active_limit");
        long deadline=Math.addExact(now,votingMillis),next=Math.addExact(deadline,cooldownMillis);
        if(offer!=null)Math.addExact(deadline,offer.durationMillis());
        Proposal p=new Proposal(UUID.randomUUID(),author,authorName,question,description,offer,now,deadline,next,
                Math.max(1,(int)((eligible.size()*(long)quorumPercent+99)/100)),eligible,Map.of());
        if(offer!=null)treasury.removeFunds(offer.currency(),offer.cost(),Kind.VOTE_RESERVED,author,p.id()+":"+offer.benefit(),now);
        current=p;nextProposalAt=next;lastObservedAt=now;return p.id();
    }
    public void vote(UUID proposal,UUID voter,boolean yes,long now){
        if(current==null || !current.id().equals(proposal) || now>=current.deadline() || now<current.openedAt() || now<lastObservedAt)throw fail("closed");
        if(!current.eligible().contains(voter))throw fail("ineligible");
        Map<UUID,Boolean> votes=new HashMap<>(current.ballots());votes.put(voter,yes);
        current=new Proposal(current.id(),current.author(),current.authorName(),current.question(),current.description(),current.offer(),current.openedAt(),current.deadline(),current.cooldownUntil(),current.minimumVotes(),current.eligible(),votes);
        lastObservedAt=now;
    }
    /** Returns true only for a persistent state change. Clock rollback cannot reopen a poll or extend a benefit. */
    public boolean advance(Treasury treasury,long now){
        if(now<0)throw fail("invalid");
        long effective=Math.max(now,lastObservedAt);boolean changed=active.removeIf(a->a.expiresAt()<=effective);
        if(current!=null && effective>=current.deadline()){
            var p=current;Outcome outcome=p.ballots().size()<p.minimumVotes()?Outcome.NO_QUORUM:p.yes()>p.no()?Outcome.APPROVED:Outcome.REJECTED;
            finish(treasury,p,outcome,null,effective);changed=true;
        }
        if(refund!=null && treasury.balance(refund.currency())<=Long.MAX_VALUE-refund.amount()){
            treasury.addFunds(refund.currency(),refund.amount(),Kind.VOTE_RETURNED,null,refund.proposalId().toString(),effective);refund=null;changed=true;
        }
        // Checkpoint real time while a funded vote or benefit exists. Do not rewrite an idle treasury.
        if((current!=null||!active.isEmpty()||refund!=null)&&effective-lastObservedAt>=30_000)changed=true;
        if(changed)lastObservedAt=effective;
        return changed;
    }
    public void cancel(Treasury treasury,UUID admin,long now){
        Objects.requireNonNull(admin);
        if(current==null || now<lastObservedAt || now>=current.deadline())throw fail("closed");
        finish(treasury,current,Outcome.ADMIN_CANCELLED,admin,now);advance(treasury,now);
    }
    private void finish(Treasury treasury,Proposal p,Outcome outcome,UUID admin,long now){
        if(outcome==Outcome.APPROVED && p.offer()!=null){
            long expires=Math.addExact(p.deadline(),p.offer().durationMillis());
            if(expires>now)active.add(new Active(p.id(),p.offer().benefit(),p.deadline(),expires,p.offer().strength()));
        }else if(p.offer()!=null)refund=new Refund(p.id(),p.offer().currency(),p.offer().cost());
        history.addFirst(new Result(p.id(),p.author(),p.authorName(),p.question(),p.description(),p.offer(),outcome,p.yes(),p.no(),p.eligible().size(),p.minimumVotes(),now,admin));
        if(history.size()>64)history.removeLast();
        treasury.recordGovernance(outcome==Outcome.APPROVED?Kind.VOTE_APPROVED:Kind.VOTE_REJECTED,p.author(),p.id()+":"+outcome,now);
        current=null;lastObservedAt=now;
    }
    public boolean active(Benefit benefit,long now){return active.stream().anyMatch(a->a.benefit()==benefit&&a.startsAt()<=now&&a.expiresAt()>Math.max(now,lastObservedAt));}
    public int strength(Benefit benefit,long now){return active.stream().filter(a->a.benefit()==benefit&&a.startsAt()<=now&&a.expiresAt()>Math.max(now,lastObservedAt)).mapToInt(Active::strength).max().orElse(0);}
    private static String description(String value){
        if(value==null)return ""; // Versions through 0.3.0 stored only the title.
        value=value.replace("\r\n","\n").replace('\r','\n').strip();
        if(value.length()>1000||value.codePoints().anyMatch(c->c=='§'||Character.isISOControl(c)&&c!='\n'))throw fail("invalid");
        return value;
    }
    private static String text(String value,int limit){if(value==null||(value=value.strip()).isBlank()||value.length()>limit||value.codePoints().anyMatch(c->Character.isISOControl(c)||c=='§'))throw fail("invalid");return value;}
    private static IllegalStateException fail(String key){return new IllegalStateException("realm.treasury.error."+key);}
}
