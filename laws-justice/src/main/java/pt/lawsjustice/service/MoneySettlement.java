package pt.lawsjustice.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import pt.lawsjustice.compat.JusticeNames;
import pt.lawsjustice.compat.JusticeNotifications;
import pt.lawsjustice.compat.JusticeTreasury;
import pt.lawsjustice.compat.JusticeWallet;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.domain.FinanceLedger;
import pt.lawsjustice.domain.FinanceLedger.Charge;
import pt.lawsjustice.domain.FinanceLedger.Payment;
import pt.lawsjustice.domain.JusticeLedger;

/** Prepared intent -> authoritative player receipt -> funded charge -> idempotent public income. */
public final class MoneySettlement {
    private static final WeakHashMap<MinecraftServer,RuntimeState> RUNTIMES=new WeakHashMap<>();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final class RuntimeState {final Set<UUID> notices=new HashSet<>();final Map<UUID,String> errors=new HashMap<>();int lastTick=Integer.MIN_VALUE;long lastWarning;}
    private MoneySettlement(){}
    private static RuntimeState runtime(MinecraftServer s){return RUNTIMES.computeIfAbsent(s,k->new RuntimeState());}
    private static long now(){return Math.max(0,System.currentTimeMillis());}
    private static void thread(MinecraftServer s){if(s==null||!s.isSameThread())throw error("thread");}
    public static String error(MinecraftServer server,UUID payment){return runtime(server).errors.getOrDefault(payment,"");}

    public static UUID pay(ServerPlayer payer,UUID chargeId){
        MinecraftServer server=payer.getServer();thread(server);JusticeData data=JusticeData.get(server);Charge charge=data.finance().charges().get(chargeId);
        if(charge==null||!charge.status().equals(FinanceLedger.DUE))throw error("charge_missing");
        if(charge.kind().equals(FinanceLedger.FINE)&&!charge.target().equals(payer.getUUID()))throw error("finance_payer");
        Payment pending=data.finance().pending(chargeId);
        if(pending!=null){if(!pending.payer().equals(payer.getUUID()))throw error("payment_exists");progress(server,pending.id(),true);return pending.id();}
        if(charge.kind().equals(FinanceLedger.BAIL)){var sentence=data.ledger().active().get(charge.target());if(sentence==null||!sentence.id().toString().equals(charge.sentence()))throw error("bail_sentence");}
        if(JusticeWallet.balance(payer,charge.currency())<charge.amount())throw error("insufficient_funds");
        UUID id=UUID.randomUUID();FinanceLedger next=data.finance().prepare(id,chargeId,payer.getUUID(),JusticeNames.name(payer),payer.getGameProfile().getName(),now());data.replaceFinance(next);
        progress(server,id,true);return id;
    }
    public static void cancel(ServerPlayer payer,UUID id){
        MinecraftServer server=payer.getServer();thread(server);JusticeData data=JusticeData.get(server);Payment payment=data.finance().payments().get(id);
        if(payment==null||!payment.payer().equals(payer.getUUID())||!payment.status().equals(FinanceLedger.PREPARED))throw error("payment_missing");
        if(PaymentReceipts.live(payer,payment)||PaymentReceipts.seen(server,payment))throw error("payment_applied");
        data.replaceFinance(data.finance().cancelPayment(id,now()));runtime(server).errors.remove(id);
    }
    /** A disconnected payer can finish an already-durable payment, but is never debited offline. */
    public static boolean progress(MinecraftServer server,UUID id,boolean authorizedOnlineDebit){
        thread(server);JusticeData data=JusticeData.get(server);Payment payment=data.finance().payments().get(id);if(payment==null||!payment.status().equals(FinanceLedger.PREPARED))return false;
        try{
            ServerPlayer payer=server.getPlayerList().getPlayer(payment.payer());
            boolean durable=PaymentReceipts.durable(server,payment);
            if(!durable&&payer!=null&&(authorizedOnlineDebit||PaymentReceipts.live(payer,payment))){
                Charge offered=data.finance().charges().get(payment.charge());
                if(offered.kind().equals(FinanceLedger.BAIL)&&!PaymentReceipts.live(payer,payment)){
                    var active=data.ledger().active().get(offered.target());
                    if(active==null||!active.id().toString().equals(offered.sentence())){
                        data.replaceFinance(data.finance().cancelPayment(id,now()));throw error("bail_sentence");
                    }
                }
                durable=PaymentReceipts.applyOnline(payer,payment);
            }
            if(!durable){runtime(server).errors.put(id,"lawsjustice.error.payment_pending");return false;}
            FinanceLedger next=data.finance().settle(id,now());Charge charge=next.charges().get(payment.charge());JusticeLedger custody=data.ledger();
            if(charge.kind().equals(FinanceLedger.BAIL)){
                var sentence=custody.active().get(charge.target());
                if(sentence!=null&&sentence.id().toString().equals(charge.sentence())){
                    var prison=custody.prisons().get(sentence.prison());JusticeLedger released=custody.judicialRelease(charge.target(),charge.author(),charge.authorName(),"Authorized bail paid",now(),true);
                    data.replaceFinanceAndQueue(released,next,charge.target(),new JusticeData.ReleaseTarget(prison.area().dimension(),prison.release()));
                    ServerPlayer target=server.getPlayerList().getPlayer(charge.target());if(target!=null)JusticeService.login(target);
                }else data.replaceFinance(next);
            }else data.replaceFinance(next);
            runtime(server).errors.remove(id);
            if(runtime(server).notices.add(id)){
                if(payer!=null)JusticeNotifications.send(payer,"lawsjustice.notification.payment_paid");
                ServerPlayer target=server.getPlayerList().getPlayer(charge.target());if(charge.kind().equals(FinanceLedger.BAIL)&&target!=null&&target!=payer)JusticeNotifications.send(target,"lawsjustice.notification.bail_paid");
            }
            return true;
        }catch(RuntimeException|LinkageError failure){runtime(server).errors.put(id,key(failure));return false;}
    }
    public static void login(ServerPlayer p){thread(p.getServer());JusticeData data=JusticeData.get(p.getServer());for(Payment payment:List.copyOf(data.finance().payments().values()))if(payment.payer().equals(p.getUUID())&&payment.status().equals(FinanceLedger.PREPARED))progress(p.getServer(),payment.id(),false);}
    public static void tick(MinecraftServer server){
        thread(server);RuntimeState r=runtime(server);int tick=server.getTickCount();if(r.lastTick==tick)return;r.lastTick=tick;if(tick%20!=0)return;
        JusticeData data=JusticeData.get(server);
        for(Payment payment:List.copyOf(data.finance().payments().values()))if(payment.status().equals(FinanceLedger.PREPARED)){
            if(progress(server,payment.id(),false))continue;
            Charge charge=data.finance().charges().get(payment.charge());
            if(charge!=null&&charge.kind().equals(FinanceLedger.BAIL)){
                var sentence=data.ledger().active().get(charge.target());if(sentence!=null&&sentence.id().toString().equals(charge.sentence()))continue;
                try{ServerPlayer p=server.getPlayerList().getPlayer(payment.payer());if((p==null||!PaymentReceipts.live(p,payment))&&!PaymentReceipts.seen(server,payment))data.replaceFinance(data.finance().cancelPayment(payment.id(),now()));}catch(RuntimeException unavailable){r.errors.put(payment.id(),key(unavailable));}
            }
        }
        if(tick%100==0&&JusticeTreasury.loaded())for(Payment payment:List.copyOf(data.finance().payments().values()))if(payment.status().equals(FinanceLedger.PAID)&&payment.destination().equals(FinanceLedger.ESCROW)){
            try{JusticeTreasury.credit(server,payment);data.replaceFinance(data.finance().transferred(payment.id(),now()));r.errors.remove(payment.id());}
            catch(RuntimeException|LinkageError failure){r.errors.put(payment.id(),"lawsjustice.error.finance_treasury");if(now()-r.lastWarning>30_000){r.lastWarning=now();LOG.warn("Paid justice funds remain secured until the public treasury accepts their receipt",failure);}}
        }
    }
    public static void stop(MinecraftServer server){RUNTIMES.remove(server);}
    private static String key(Throwable error){String message=error.getMessage();return message!=null&&message.startsWith("lawsjustice.error.")?message:"lawsjustice.error.finance_storage";}
    private static IllegalStateException error(String key){return new IllegalStateException("lawsjustice.error."+key);}
}
