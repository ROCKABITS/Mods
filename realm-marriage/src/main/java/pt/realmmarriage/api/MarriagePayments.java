package pt.realmmarriage.api;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import pt.realmmarriage.data.MarriageData;
import pt.realmmarriage.domain.MarriageLedger.*;
import static pt.realmmarriage.domain.MarriageLedger.require;

/** Explicit funded escrow. Consumers must persist their own delivery receipt before committing. */
public final class MarriagePayments {
    public static final int VERSION=1;
    public record Consumer(String id){}
    private static final Map<String,Consumer> CONSUMERS=new HashMap<>();
    private MarriagePayments(){}
    public static synchronized Consumer register(String id){require(id!=null&&id.matches("[a-z0-9_.-]{1,64}")&&!CONSUMERS.containsKey(id),"consumer");Consumer c=new Consumer(id);CONSUMERS.put(id,c);return c;}
    private static void allowed(Consumer c){require(c!=null&&CONSUMERS.get(c.id())==c,"consumer");}
    public static long balance(ServerPlayer p){MarriageData d=MarriageData.get(p.getServer());d.available();Union u=d.ledger.current(p.getUUID());return u!=null&&u.phase==Phase.MARRIED&&u.mode!=Mode.SEPARATE?u.balance:0;}
    public static String reserve(Consumer consumer,ServerPlayer p,UUID receipt,UUID marriage,long cents){allowed(consumer);MarriageData d=MarriageData.get(p.getServer());d.available();require(cents>0,"amount");Operation o=d.ledger.reservePayment(receipt,p.getUUID(),marriage,consumer.id(),cents,System.currentTimeMillis());d.persist(p.getServer());return o.state.name();}
    public static void commit(Consumer consumer,MinecraftServer server,UUID receipt){settle(consumer,server,receipt,true);}
    public static void refund(Consumer consumer,MinecraftServer server,UUID receipt){settle(consumer,server,receipt,false);}
    private static void settle(Consumer c,MinecraftServer s,UUID id,boolean commit){allowed(c);MarriageData d=MarriageData.get(s);d.available();d.ledger.payment(id,c.id(),commit,System.currentTimeMillis());d.persist(s);}
}
