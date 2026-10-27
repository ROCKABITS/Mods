package pt.realmeconomy.colony;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.*;

/** Immutable colony budgets. Amounts are cents; real UTC days never move backwards. */
public final class ColonyLedger {
    public static final long MAX=900_000_000_000_000L, DAY=86_400_000L;
    public static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    public record Policy(boolean enabled,long dailyCap,long playerCap,int orders,long buildCents,long recruitCents) {
        public Policy {if(dailyCap<0||dailyCap>10_000_000||playerCap<0||playerCap>10_000_000||orders<1||orders>12||buildCents<0||buildCents>1_000_000||recruitCents<0||recruitCents>1_000_000)throw new IllegalArgumentException("Invalid colony policy");}
        public static Policy defaults(){return new Policy(true,25_000,25_000,5,2_500,2_500);}
        public long construction(int level){if(level<1||level>100)throw new IllegalArgumentException("Invalid level");return Math.multiplyExact(buildCents,Math.multiplyExact((long)level,level));}
    }
    public record Order(String id,String item,int quantity,long cents,boolean delivered) {
        public Order {uuid(id);if(item==null||!item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||quantity<1||quantity>4096||cents<1||cents>1_000_000)throw new IllegalArgumentException("Invalid export");}
    }
    public record Invoice(String id,String kind,String title,String reference,long cents,boolean paid,long at) {
        public Invoice {uuid(id);if(!Set.of("BUILD","UPGRADE","RECRUIT").contains(kind)||title==null||title.length()>180||reference==null||reference.length()>256||cents<0||cents>MAX||at<0)throw new IllegalArgumentException("Invalid colony invoice");}
    }
    public record Entry(String id,String actor,String name,String kind,long cents,long balance,long at,String detail) {
        public Entry {uuid(id);uuid(actor);if(name==null||name.length()>64||kind==null||kind.length()>24||cents< -MAX||cents>MAX||balance<0||balance>MAX||at<0||detail==null||detail.length()>256)throw new IllegalArgumentException("Invalid colony event");}
    }
    public record Quota(long day,long earned) {public Quota {if(day<0||earned<0||earned>MAX)throw new IllegalArgumentException("Invalid export quota");}}
    public record Account(String key,String name,long center,boolean retired,long balance,long day,long earned,List<Order> orders,Map<String,Invoice> invoices,List<Entry> history) {
        public Account {
            if(key==null||!key.matches("[a-z0-9_.-]+:[a-z0-9/._-]+/[1-9][0-9]*(/[0-9a-f-]{36})?")||name==null||name.isBlank()||name.length()>128||balance<0||balance>MAX||day<0||earned<0||earned>MAX)throw new IllegalArgumentException("Invalid colony account");
            orders=List.copyOf(Objects.requireNonNull(orders));invoices=Map.copyOf(Objects.requireNonNull(invoices));history=List.copyOf(Objects.requireNonNull(history));
            if(orders.size()>12||invoices.size()>2048||history.size()>256||orders.stream().map(Order::id).distinct().count()!=orders.size())throw new IllegalArgumentException("Colony capacity exceeded");
            invoices.forEach((id,v)->{if(!id.equals(v.id()))throw new IllegalArgumentException("Invoice identity mismatch");});
        }
        public Invoice invoice(String id){return invoices.get(id);}
    }
    public record State(int version,Policy policy,long highWater,Map<String,Account> accounts,Map<String,Quota> exporters) {
        public State {
            if(version!=1||highWater<0)throw new IllegalArgumentException("Unsupported colony ledger");Objects.requireNonNull(policy);
            accounts=Map.copyOf(Objects.requireNonNull(accounts));exporters=Map.copyOf(Objects.requireNonNull(exporters));
            if(accounts.size()>2048||exporters.size()>10000)throw new IllegalArgumentException("Colony ledger capacity exceeded");
            accounts.forEach((key,a)->{if(!key.equals(a.key()))throw new IllegalArgumentException("Account identity mismatch");});exporters.keySet().forEach(ColonyLedger::uuid);
        }
        public static State empty(){return new State(1,Policy.defaults(),0,Map.of(),Map.of());}
        public Account account(String key){return Objects.requireNonNull(accounts.get(key),"Unknown colony account");}
        public State with(Account account,long now){var m=new TreeMap<>(accounts);m.put(account.key(),account);return new State(1,policy,Math.max(highWater,now),m,exporters);}
        public State policy(Policy next,long now){return new State(1,next,Math.max(highWater,now),accounts,exporters);}
    }
    public static State decode(String json){if(json==null||json.length()>12_000_000||json.isBlank())throw new IllegalArgumentException("Invalid colony ledger");return Objects.requireNonNull(JSON.fromJson(json,State.class));}
    public static String encode(State state){String json=JSON.toJson(state);if(json.length()>12_000_000)throw new IllegalStateException("Colony ledger capacity exceeded");return json;}
    public static State ensure(State s,String key,String name,long center,long now,List<Order> orders){
        Account a=s.accounts.get(key);long day=Math.floorDiv(Math.max(s.highWater,now),DAY);
        if(a!=null&&(a.retired||a.center!=center))throw new IllegalStateException("error.realmeconomy.colony_retired");
        if(a==null)a=new Account(key,name,center,false,0,day,0,orders,Map.of(),List.of());
        else if(a.day<day)a=new Account(key,name,center,false,a.balance,day,0,orders,a.invoices,a.history);
        else if(!a.name.equals(name))a=new Account(key,name,center,false,a.balance,a.day,a.earned,a.orders,a.invoices,a.history);
        return s.with(a,now);
    }
    public static State transfer(State s,String key,UUID actor,String name,long amount,boolean deposit,long now,UUID id){
        if(amount<1||amount>MAX)throw new IllegalArgumentException("Invalid transfer");Account a=s.account(key);long next=deposit?Math.addExact(a.balance,amount):Math.subtractExact(a.balance,amount);
        return update(s,a,next,a.earned,a.orders,a.invoices,actor,name,deposit?"DEPOSIT":"WITHDRAW",deposit?amount:-amount,now,id,"");
    }
    public static State export(State s,String key,UUID actor,String name,String orderId,long now,UUID receipt){
        Account a=s.account(key);long day=Math.floorDiv(Math.max(s.highWater,now),DAY);if(a.day!=day||!s.policy.enabled||a.retired)throw new IllegalStateException("error.realmeconomy.colony_expired");
        Order order=a.orders.stream().filter(o->o.id.equals(orderId)).findFirst().orElseThrow();if(order.delivered)throw new IllegalStateException("error.realmeconomy.colony_delivered");
        Quota old=s.exporters.getOrDefault(actor.toString(),new Quota(day,0));long earned=old.day==day?old.earned:0;
        if(Math.addExact(a.earned,order.cents)>s.policy.dailyCap||Math.addExact(earned,order.cents)>s.policy.playerCap)throw new IllegalStateException("error.realmeconomy.colony_cap");
        var orders=new ArrayList<Order>();for(var o:a.orders)orders.add(o.id.equals(orderId)?new Order(o.id,o.item,o.quantity,o.cents,true):o);
        State next=update(s,a,Math.addExact(a.balance,order.cents),Math.addExact(a.earned,order.cents),orders,a.invoices,actor,name,"EXPORT",order.cents,now,receipt,order.item+" × "+order.quantity);
        var quotas=new TreeMap<>(next.exporters);quotas.put(actor.toString(),new Quota(day,Math.addExact(earned,order.cents)));return new State(1,next.policy,next.highWater,next.accounts,quotas);
    }
    public static State invoice(State s,String key,Invoice invoice,long now){
        Account a=s.account(key);if(a.invoices.containsKey(invoice.id))return s;var invoices=new TreeMap<>(a.invoices);invoices.put(invoice.id,invoice);
        return s.with(new Account(a.key,a.name,a.center,a.retired,a.balance,a.day,a.earned,a.orders,invoices,a.history),now);
    }
    public static State pay(State s,String key,String invoiceId,UUID actor,String name,long now,UUID receipt){
        Account a=s.account(key);Invoice i=Objects.requireNonNull(a.invoices.get(invoiceId));if(i.paid)throw new IllegalStateException("error.realmeconomy.colony_paid");
        var invoices=new TreeMap<>(a.invoices);invoices.put(i.id,new Invoice(i.id,i.kind,i.title,i.reference,i.cents,true,i.at));
        return update(s,a,Math.subtractExact(a.balance,i.cents),a.earned,a.orders,invoices,actor,name,"PAY_"+i.kind,-i.cents,now,receipt,i.title);
    }
    public static State retire(State s,String key,long now){Account a=s.accounts.get(key);if(a==null||a.retired)return s;String archived=key+"/"+UUID.randomUUID();var accounts=new TreeMap<>(s.accounts);accounts.remove(key);accounts.put(archived,new Account(archived,a.name,a.center,true,a.balance,a.day,a.earned,a.orders,a.invoices,a.history));return new State(1,s.policy,Math.max(s.highWater,now),accounts,s.exporters);}
    private static State update(State s,Account a,long balance,long earned,List<Order> orders,Map<String,Invoice> invoices,UUID actor,String name,String kind,long amount,long now,UUID id,String detail){
        if(a.retired||balance<0||balance>MAX)throw new IllegalStateException("error.realmeconomy.insufficient_funds");
        if(a.history.stream().anyMatch(e->e.id.equals(id.toString())))throw new IllegalStateException("Repeated colony receipt");
        var history=new ArrayList<Entry>();history.add(new Entry(id.toString(),actor.toString(),name,kind,amount,balance,Math.max(s.highWater,now),detail));history.addAll(a.history);if(history.size()>256)history.subList(256,history.size()).clear();
        return s.with(new Account(a.key,a.name,a.center,false,balance,a.day,earned,orders,invoices,history),now);
    }
    private static void uuid(String id){UUID.fromString(Objects.requireNonNull(id));}
}
