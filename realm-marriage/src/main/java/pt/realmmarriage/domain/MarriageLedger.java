package pt.realmmarriage.domain;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure state machine. No client-supplied name, balance, role or timer is authoritative. */
public final class MarriageLedger {
    public static final Gson JSON=new Gson();
    public static final long MAX_MONEY=900_000_000_000_000_000L;
    public enum Mode { JOINT, SEPARATE, SHARED }
    public enum Phase { PROPOSED, ENGAGED, OPENING, INTRODUCTION, OBJECTIONS, SUSPENDED, VOWS, COMMITTING, MARRIED, DIVORCED, CANCELLED }
    public enum Kind { FUND, DEPOSIT, WITHDRAW, CREDIT, DOCUMENTS, PAYMENT }
    public enum OpState { PREPARED, HELD, DONE, CANCELLED }
    public static final class Settings {
        public int objectionSeconds=10,guestRadius=20,participantRadius=12,ceremonyMinutes=30,witnessLimit=2,proposalMinutes=15;
        public long fee=0,feeCap=50000;
        public void validate(){require(objectionSeconds>=3&&objectionSeconds<=60&&guestRadius>=4&&guestRadius<=64&&guestRadius>=participantRadius&&participantRadius>=3&&participantRadius<=32&&ceremonyMinutes>=2&&ceremonyMinutes<=180&&witnessLimit>=0&&witnessLimit<=2&&proposalMinutes>=1&&proposalMinutes<=120&&fee>=0&&fee<=feeCap&&feeCap<=10000000,"settings");}
    }
    public static final class Citizen {
        public UUID id,current; public long lastProposalAt; public String name; public boolean divorced,publicSpouse=true;
        public Map<UUID,Long> credits=new LinkedHashMap<>();
        public long credit(){return credits.values().stream().reduce(0L,Math::addExact);}
    }
    public static final class Union {
        public UUID id,a,b,priest,objector;
        public String nameA,nameB,priestName="",dimension="",reason="",speaker="",error="";
        public Phase phase=Phase.PROPOSED,resume=Phase.VOWS;
        public Mode mode=Mode.SEPARATE;
        public List<UUID> witnesses=new ArrayList<>();
        public Map<UUID,String> witnessNames=new LinkedHashMap<>();
        public Set<UUID> invited=new HashSet<>(),confirmedWitnesses=new HashSet<>(),plan=new HashSet<>(),continueVotes=new HashSet<>(),vows=new HashSet<>(),claimed=new HashSet<>();
        public Map<UUID,UUID> ringTokens=new LinkedHashMap<>(),certificateTokens=new LinkedHashMap<>();
        public long createdAt,expiresAt,objectionUntil,marriedAt,divorcedAt,fee,balance,feeEscrow,revision,dividedA,dividedB;
        public int shareA=5000,x,y,z,vowTurn;
        public boolean priestAccepted,objected,fundedA,fundedB,feeTransferred,feeAttempted,cancelRequested;
        public boolean party(UUID p){return a.equals(p)||b.equals(p);}
        public UUID spouse(UUID p){require(party(p),"party");return a.equals(p)?b:a;}
        public long feePart(UUID p){require(party(p),"party");return p.equals(a)?fee/2:fee-fee/2;}
        public boolean active(){return phase!=Phase.CANCELLED&&phase!=Phase.DIVORCED;}
        public boolean ceremony(){return switch(phase){case OPENING,INTRODUCTION,OBJECTIONS,SUSPENDED,VOWS->true;default->false;};}
    }
    public static final class Operation {
        public UUID id,actor,union;public Kind kind;public OpState state=OpState.PREPARED;
        public long amount,fee,at;public String consumer="",error="";
    }
    public record Audit(long at,UUID actor,UUID union,String action,String detail){}
    public Settings settings=new Settings();
    public long revision,highWater;
    public Map<UUID,Citizen> citizens=new LinkedHashMap<>();
    public Map<UUID,Union> unions=new LinkedHashMap<>();
    public Map<UUID,Operation> operations=new LinkedHashMap<>();
    public List<Audit> audit=new ArrayList<>();
    public static void require(boolean condition,String key){if(!condition)throw new IllegalStateException("marriage.error."+key);}
    public static UUID key(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    public long now(long clock){require(clock>=0,"clock");return highWater=Math.max(clock,highWater);}
    public void note(UUID actor,Union u,String action,String detail,long at){revision++;if(u!=null)u.revision++;audit.addFirst(new Audit(now(at),actor,u==null?null:u.id,action,detail));while(audit.size()>1000)audit.removeLast();}
    public Citizen citizen(UUID id,String name){require(id!=null&&name!=null&&!name.isBlank()&&name.length()<=64,"player");require(citizens.containsKey(id)||citizens.size()<10000,"capacity");Citizen c=citizens.computeIfAbsent(id,k->{Citizen n=new Citizen();n.id=k;return n;});c.name=name;return c;}
    public Citizen citizen(UUID id){Citizen c=citizens.get(id);require(c!=null,"player");return c;}
    public Union union(UUID id){Union u=unions.get(id);require(u!=null,"union");return u;}
    public Union current(UUID p){Citizen c=citizens.get(p);return c==null||c.current==null?null:union(c.current);}
    public Union propose(UUID a,UUID b,long clock){
        require(!a.equals(b),"self");require(current(a)==null&&current(b)==null,"occupied");require(unions.size()<10000,"capacity");
        Union u=new Union();u.id=UUID.randomUUID();u.a=a;u.b=b;u.nameA=citizen(a).name;u.nameB=citizen(b).name;u.createdAt=now(clock);u.expiresAt=u.createdAt+settings.proposalMinutes*60000L;
        unions.put(u.id,u);citizen(a).current=u.id;citizen(b).current=u.id;note(a,u,"proposal","",clock);return u;
    }
    public void answer(Union u,UUID actor,boolean accept,long clock){require(u.phase==Phase.PROPOSED&&actor.equals(u.b)&&now(clock)<u.expiresAt,"proposal");if(!accept){cancel(u,actor,clock);return;}u.phase=Phase.ENGAGED;u.expiresAt=0;note(actor,u,"engaged","",clock);}
    public void configure(Union u,UUID actor,UUID priest,List<UUID> witnesses,Mode mode,int shareA,long fee,long clock){
        require(u.party(actor)&&u.phase==Phase.ENGAGED,"planning");require(priest!=null&&!u.party(priest)&&citizens.containsKey(priest),"priest");
        require(witnesses!=null&&witnesses.size()<=settings.witnessLimit&&new HashSet<>(witnesses).size()==witnesses.size()&&witnesses.stream().allMatch(p->!u.party(p)&&!p.equals(priest)&&citizens.containsKey(p)),"witnesses");
        require(mode!=null&&shareA>=0&&shareA<=10000&&fee>=0&&fee<=settings.feeCap,"agreement");
        u.priest=priest;u.priestName=citizen(priest).name;u.witnesses=new ArrayList<>(witnesses);u.witnessNames.clear();witnesses.forEach(p->u.witnessNames.put(p,citizen(p).name));u.mode=mode;u.shareA=shareA;u.fee=fee;
        u.plan.clear();u.invited.clear();u.priestAccepted=false;u.vows.clear();u.confirmedWitnesses.clear();note(actor,u,"agreement_changed",mode+"/"+shareA+"/"+fee,clock);
    }
    public void agree(Union u,UUID p,long clock){require(u.party(p)&&u.phase==Phase.ENGAGED&&u.priest!=null,"planning");if(!u.plan.add(p))return;note(p,u,"agreement_accepted","",clock);}
    public void invitation(Union u,UUID p,boolean accept,long clock){require(u.phase==Phase.ENGAGED&&u.plan.containsAll(List.of(u.a,u.b))&&(p.equals(u.priest)||u.witnesses.contains(p)),"invitation");if(p.equals(u.priest))u.priestAccepted=accept;else if(accept)u.invited.add(p);else u.invited.remove(p);note(p,u,accept?"invitation_accepted":"invitation_declined","",clock);}
    public void start(Union u,UUID p,String dimension,int x,int y,int z,long clock){
        require(u.phase==Phase.ENGAGED&&p.equals(u.priest)&&u.priestAccepted&&u.plan.containsAll(List.of(u.a,u.b))&&u.invited.containsAll(u.witnesses),"ready");
        u.dimension=dimension;u.x=x;u.y=y;u.z=z;u.phase=Phase.OPENING;u.expiresAt=now(clock)+settings.ceremonyMinutes*60000L;note(p,u,"opening","",clock);
    }
    public void next(Union u,UUID p,long clock){require(p.equals(u.priest)&&u.priestAccepted,"priest");if(u.phase==Phase.OPENING)u.phase=Phase.INTRODUCTION;else if(u.phase==Phase.INTRODUCTION){u.phase=Phase.OBJECTIONS;u.objectionUntil=now(clock)+settings.objectionSeconds*1000L;}else throw new IllegalStateException("marriage.error.phase");note(p,u,"ceremony_step",u.phase.name(),clock);}
    public void objection(Union u,UUID p,long clock){require(u.phase==Phase.OBJECTIONS&&now(clock)<u.objectionUntil&&!u.party(p)&&!u.objected,"objection");u.objected=true;u.objector=p;u.reason="";suspend(u,"objection",clock);note(p,u,"objection","",clock);}
    public void reason(Union u,UUID p,String text,long clock){require(u.phase==Phase.SUSPENDED&&p.equals(u.objector)&&text!=null&&text.length()<=256&&text.codePoints().noneMatch(Character::isISOControl),"reason");u.reason=text;note(p,u,"objection_reason",text,clock);}
    public void suspend(Union u,String reason,long clock){require(u.ceremony(),"phase");if(u.phase!=Phase.SUSPENDED){u.resume=u.phase==Phase.OBJECTIONS?Phase.VOWS:u.phase;u.phase=Phase.SUSPENDED;}u.continueVotes.clear();u.vows.clear();u.confirmedWitnesses.clear();u.vowTurn=0;u.error=reason;note(null,u,"suspended",reason,clock);}
    public void continueVote(Union u,UUID p,long clock){require(u.phase==Phase.SUSPENDED&&u.party(p),"party");u.continueVotes.add(p);note(p,u,"continue_vote","",clock);}
    public void resume(Union u,UUID p,long clock){require(u.phase==Phase.SUSPENDED&&p.equals(u.priest)&&u.continueVotes.containsAll(List.of(u.a,u.b)),"resume");u.phase=u.resume;u.error="";note(p,u,"resumed","",clock);}
    public void ask(Union u,UUID p,long clock){require(u.phase==Phase.VOWS&&p.equals(u.priest),"priest");require(u.vowTurn==0||u.vowTurn==1&&u.vows.contains(u.a),"vows");u.vowTurn++;note(p,u,"ask_vow",u.vowTurn==1?u.a.toString():u.b.toString(),clock);}
    public void vow(Union u,UUID p,long clock){require(u.phase==Phase.VOWS&&(u.vowTurn==1&&p.equals(u.a)||u.vowTurn==2&&p.equals(u.b)),"vows");u.vows.add(p);note(p,u,"vow_accepted","",clock);}
    public void witness(Union u,UUID p,long clock){require(u.phase==Phase.VOWS&&u.witnesses.contains(p)&&u.invited.contains(p),"witnesses");u.confirmedWitnesses.add(p);note(p,u,"witness_signed","",clock);}
    public void seal(Union u,UUID p,long clock){require(u.phase==Phase.VOWS&&p.equals(u.priest)&&u.vows.containsAll(List.of(u.a,u.b))&&u.confirmedWitnesses.containsAll(u.witnesses),"vows");u.phase=Phase.COMMITTING;note(p,u,"financial_seal","",clock);}
    public void finish(Union u,long clock){require(u.phase==Phase.COMMITTING&&u.fundedA&&u.fundedB&&(u.fee==0||u.feeTransferred)&&u.feeEscrow==0,"pending");u.phase=Phase.MARRIED;u.error="";u.marriedAt=now(clock);for(UUID p:List.of(u.a,u.b)){u.ringTokens.put(p,UUID.randomUUID());u.certificateTokens.put(p,UUID.randomUUID());}u.priestAccepted=false;note(null,u,"married","",clock);if(u.cancelRequested)divorce(u,u.a,clock);}
    public void cancel(Union u,UUID p,long clock){require(u.party(p)||p.equals(u.priest)&&u.phase!=Phase.MARRIED,"party");require(u.phase!=Phase.MARRIED&&u.phase!=Phase.DIVORCED&&u.phase!=Phase.CANCELLED,"phase");if(u.phase==Phase.COMMITTING){u.cancelRequested=true;note(p,u,"cancel_pending","Funds will settle before dissolution",clock);return;}u.phase=Phase.CANCELLED;u.priestAccepted=false;clearCurrent(u);note(p,u,"cancelled","",clock);}
    public void abortFinancial(Union u,long clock){require(u.phase==Phase.COMMITTING&&u.cancelRequested&&!u.feeAttempted,"pending");for(Operation o:operations.values())if(Objects.equals(o.union,u.id)&&o.kind==Kind.FUND&&o.state==OpState.DONE)credit(o.actor,u.id,o.amount);u.balance=0;u.feeEscrow=0;u.phase=Phase.CANCELLED;u.priestAccepted=false;clearCurrent(u);note(null,u,"financial_cancelled","Refunds reserved for their original owners",clock);}
    public void cancelCommitted(Union u,UUID actor,long clock){require(u.phase==Phase.COMMITTING&&u.cancelRequested&&u.feeAttempted&&u.fundedA&&u.fundedB,"pending");split(u,u.balance);u.balance=0;u.phase=Phase.CANCELLED;u.priestAccepted=false;clearCurrent(u);note(actor,u,"cancelled_after_fee_attempt","Previously authorised fee remains in settlement; shared assets reserved for owners",clock);}
    public void divorce(Union u,UUID p,long clock){require(u.party(p)&&u.phase==Phase.MARRIED,"married");u.phase=Phase.DIVORCED;u.divorcedAt=now(clock);split(u,u.balance);u.balance=0;clearCurrent(u);citizen(u.a).divorced=true;citizen(u.b).divorced=true;note(p,u,"divorced","",clock);}
    private void clearCurrent(Union u){for(UUID p:List.of(u.a,u.b))if(u.id.equals(citizen(p).current))citizen(p).current=null;}
    private void split(Union u,long amount){long total=Math.addExact(Math.addExact(u.dividedA,u.dividedB),amount);long entitled=total/10000*u.shareA+(total%10000)*u.shareA/10000;long first=entitled-u.dividedA;require(first>=0&&first<=amount,"amount");credit(u.a,u.id,first);credit(u.b,u.id,amount-first);u.dividedA=entitled;u.dividedB=total-entitled;}
    public void credit(UUID p,UUID origin,long amount){require(amount>=0&&amount<=MAX_MONEY,"amount");if(amount>0)citizen(p).credits.merge(origin,amount,Math::addExact);}
    public Operation op(UUID id,UUID actor,Union u,Kind kind,long amount,long fee,String consumer,long clock){require(operations.size()<65536&&!operations.containsKey(id)&&amount>=0&&amount<=MAX_MONEY&&fee>=0&&fee<=amount,"transaction");Operation o=new Operation();o.id=id;o.actor=actor;o.union=u==null?null:u.id;o.kind=kind;o.amount=amount;o.fee=fee;o.consumer=consumer;o.at=now(clock);operations.put(id,o);return o;}
    public void settled(Operation o,long clock){require(o.state==OpState.PREPARED,"transaction");Union u=o.union==null?null:union(o.union);switch(o.kind){case FUND->{if(u.phase==Phase.CANCELLED){credit(o.actor,u.id,o.amount);break;}require(u.phase==Phase.COMMITTING,"phase");if(o.actor.equals(u.a)){require(!u.fundedA,"transaction");u.fundedA=true;}else{require(o.actor.equals(u.b)&&!u.fundedB,"transaction");u.fundedB=true;}u.feeEscrow=Math.addExact(u.feeEscrow,o.fee);u.balance=Math.addExact(u.balance,o.amount-o.fee);}case DEPOSIT->{if(u.phase==Phase.MARRIED)u.balance=Math.addExact(u.balance,o.amount);else split(u,o.amount);}case DOCUMENTS->u.claimed.add(o.actor);default->{}}o.state=OpState.DONE;o.error="";note(o.actor,u,"transaction_"+o.kind.name(),o.id+":"+o.amount,clock);}
    public void withdrawReserve(Union u,UUID p,long amount){require(u.phase==Phase.MARRIED&&u.party(p)&&u.mode!=Mode.SEPARATE&&amount>0&&amount<=u.balance,"funds");u.balance-=amount;}
    public Operation reservePayment(UUID id,UUID p,UUID union,String consumer,long amount,long clock){Operation old=operations.get(id);if(old!=null){require(old.kind==Kind.PAYMENT&&old.actor.equals(p)&&Objects.equals(old.union,union)&&old.consumer.equals(consumer)&&old.amount==amount,"transaction");return old;}require(id!=null&&operations.size()<65536&&amount>0&&amount<=MAX_MONEY,"transaction");Union u=union(union);withdrawReserve(u,p,amount);Operation o=op(id,p,u,Kind.PAYMENT,amount,0,consumer,clock);o.state=OpState.HELD;note(p,u,"payment_reserved",id.toString(),clock);return o;}
    public void payment(UUID id,String consumer,boolean commit,long clock){Operation o=operations.get(id);require(o!=null&&o.kind==Kind.PAYMENT&&o.consumer.equals(consumer),"transaction");if(o.state!=OpState.HELD){require(o.state==(commit?OpState.DONE:OpState.CANCELLED),"transaction");return;}Union u=union(o.union);if(!commit){if(u.phase==Phase.MARRIED)u.balance=Math.addExact(u.balance,o.amount);else split(u,o.amount);}o.state=commit?OpState.DONE:OpState.CANCELLED;note(o.actor,u,commit?"payment_committed":"payment_refunded",id.toString(),clock);}
    public void tick(long clock){long now=now(clock);for(Union u:unions.values()){if(u.phase==Phase.PROPOSED&&now>=u.expiresAt)cancel(u,u.a,now);else if(u.ceremony()&&now>=u.expiresAt)cancel(u,u.a,now);else if(u.phase==Phase.OBJECTIONS&&now>=u.objectionUntil){u.phase=Phase.VOWS;note(null,u,"objections_closed","",now);}}}
    public String status(UUID p){Union u=current(p);return u==null?(citizen(p).divorced?"DIVORCED":"SINGLE"):u.phase==Phase.MARRIED?"MARRIED":u.phase==Phase.PROPOSED?"SINGLE":"ENGAGED";}
    public MarriageLedger copy(){return fromJson(JSON.toJson(this));}
    public static MarriageLedger fromJson(String json){MarriageLedger l=JSON.fromJson(json,MarriageLedger.class);l.validate();return l;}
    public void validate(){
        require(settings!=null&&citizens!=null&&unions!=null&&operations!=null&&audit!=null&&citizens.size()<=10000&&unions.size()<=10000&&operations.size()<=65536&&audit.size()<=1000&&highWater>=0&&revision>=0,"storage");settings.validate();
        for(var e:citizens.entrySet()){Citizen c=e.getValue();require(c!=null&&e.getKey().equals(c.id)&&c.name!=null&&c.name.length()<=64&&c.credits!=null&&c.credit()>=0&&c.credit()<=MAX_MONEY,"storage");if(c.current!=null){Union u=union(c.current);require(u.active()&&u.party(c.id),"storage");}for(var v:c.credits.entrySet())require(unions.containsKey(v.getKey())&&v.getValue()>=0,"storage");}
        for(var e:unions.entrySet()){Union u=e.getValue();require(u!=null&&e.getKey().equals(u.id)&&u.a!=null&&u.b!=null&&!u.a.equals(u.b)&&citizens.containsKey(u.a)&&citizens.containsKey(u.b)&&u.phase!=null&&u.mode!=null&&u.balance>=0&&u.balance<=MAX_MONEY&&u.fee>=0&&u.fee<=10000000&&u.feeEscrow>=0&&u.feeEscrow<=u.fee&&u.shareA>=0&&u.shareA<=10000&&u.dividedA>=0&&u.dividedB>=0&&u.dividedA<=MAX_MONEY&&u.dividedB<=MAX_MONEY&&u.witnesses!=null&&u.witnesses.size()<=2&&new HashSet<>(u.witnesses).size()==u.witnesses.size()&&u.plan!=null&&u.plan.stream().allMatch(u::party)&&u.vows!=null&&u.vows.stream().allMatch(u::party),"storage");if(u.active())require(u.id.equals(citizen(u.a).current)&&u.id.equals(citizen(u.b).current),"storage");if(u.phase==Phase.MARRIED)require(u.marriedAt>0&&u.fundedA&&u.fundedB&&u.feeEscrow==0&&u.ringTokens.keySet().equals(Set.of(u.a,u.b))&&u.certificateTokens.keySet().equals(Set.of(u.a,u.b)),"storage");}
        for(var e:operations.entrySet()){Operation o=e.getValue();require(o!=null&&e.getKey().equals(o.id)&&o.actor!=null&&citizens.containsKey(o.actor)&&o.kind!=null&&o.state!=null&&o.amount>=0&&o.amount<=MAX_MONEY&&o.fee>=0&&o.fee<=o.amount&&o.consumer!=null&&o.consumer.length()<=64&&(o.union==null||unions.containsKey(o.union)),"storage");}
    }
}
