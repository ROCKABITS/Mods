package pt.contracts.daily;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import pt.contracts.compat.ChroniclesBridge;

/** Constructs one immutable profile containing money, reputation and mission count before mutation. */
public final class DailyWallet {
    private DailyWallet(){}
    @SuppressWarnings("unchecked") private static AttachmentType<Object> attachment()throws ReflectiveOperationException{return (AttachmentType<Object>)((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();}
    @SuppressWarnings("unchecked") private static Codec<Object> codec()throws ReflectiveOperationException{return (Codec<Object>)Class.forName("pt.cronicasrp.domain.ProfileStorage").getField("CODEC").get(null);}
    public static CompoundTag current(ServerPlayer p){try{return (CompoundTag)codec().encodeStart(NbtOps.INSTANCE,p.getData(attachment())).getOrThrow();}catch(ReflectiveOperationException e){throw new IllegalStateException("daily.contracts.wallet_error",e);}}
    public static CompoundTag awarded(ServerPlayer p,DailyLedger.Reward reward){
        try{
            if(!ChroniclesBridge.available(p))throw new IllegalStateException("daily.contracts.profile_required");
            Object before=p.getData(attachment());Class<?> type=before.getClass();long balance=(long)type.getMethod("balance").invoke(before);
            if(reward.net>ChroniclesBridge.MAX_CENTS-balance)throw new IllegalStateException("daily.contracts.wallet_full");
            Object after=type.getMethod("withBalance",long.class).invoke(before,balance+reward.net);
            Object progress=type.getMethod("progress").invoke(before);Class<?> pt=progress.getClass();
            int reputation=(int)pt.getMethod("reputation").invoke(progress),missions=(int)pt.getMethod("missions").invoke(progress);
            if(reward.reputation>0)progress=pt.getMethod("withReputation",int.class).invoke(progress,Math.min(100,reputation+reward.reputation));
            int added=Math.min(reward.missions,1_000_000-missions);if(added>0)progress=pt.getMethod("completeMissions",int.class).invoke(progress,added);
            after=type.getMethod("withProgress",pt).invoke(after,progress);
            for(var field:type.getRecordComponents())if(!field.getName().equals("balance")&&!field.getName().equals("progress"))
                if(!java.util.Objects.equals(field.getAccessor().invoke(before),field.getAccessor().invoke(after)))throw new IllegalStateException("Profile preservation failed");
            return (CompoundTag)codec().encodeStart(NbtOps.INSTANCE,after).getOrThrow();
        }catch(ReflectiveOperationException e){throw new IllegalStateException("daily.contracts.wallet_error",e);}
    }
    public static void install(ServerPlayer p,CompoundTag expected,CompoundTag next){
        if(!current(p).equals(expected))throw new IllegalStateException("daily.contracts.wallet_ambiguous");
        try{p.setData(attachment(),codec().parse(NbtOps.INSTANCE,next).getOrThrow());}catch(ReflectiveOperationException e){throw new IllegalStateException("daily.contracts.wallet_error",e);}
    }
    public static void sync(ServerPlayer p){try{var c=Class.forName("pt.cronicasrp.network.ProfileNetwork");int sync=Class.forName("pt.cronicasrp.network.ProfilePackets").getField("SYNC").getInt(null);c.getMethod("send",ServerPlayer.class,int.class,String.class).invoke(null,p,sync,"");}catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}}
    public static String name(ServerPlayer p){try{if(ChroniclesBridge.available(p)){Object profile=p.getData(attachment());return (String)profile.getClass().getMethod("name").invoke(profile);}}catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}return p.getGameProfile().getName();}
}
