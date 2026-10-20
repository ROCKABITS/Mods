package pt.realmmarriage.compat;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import static pt.realmmarriage.domain.MarriageLedger.require;

/** Explicit personal-wallet operations; never replaces or intercepts other mods' wallets. */
public final class WalletBridge {
    private WalletBridge(){}
    public static boolean loaded(){return ModList.get().isLoaded("cronicasrp");}
    @SuppressWarnings("unchecked") private static AttachmentType<Object> attachment()throws ReflectiveOperationException{return(AttachmentType<Object>)((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();}
    @SuppressWarnings("unchecked") private static Codec<Object> codec()throws ReflectiveOperationException{return(Codec<Object>)Class.forName("pt.cronicasrp.domain.ProfileStorage").getField("CODEC").get(null);}
    private static Object profile(ServerPlayer p)throws ReflectiveOperationException{require(loaded(),"chronicles");Object profile=p.getData(attachment());require((boolean)profile.getClass().getMethod("created").invoke(profile),"profile");return profile;}
    public static long balance(ServerPlayer p){try{Object v=profile(p);return(long)v.getClass().getMethod("balance").invoke(v);}catch(ReflectiveOperationException e){throw new IllegalStateException("marriage.error.wallet",e);}}
    public static String name(ServerPlayer p){try{Object v=profile(p);return(String)v.getClass().getMethod("name").invoke(v);}catch(ReflectiveOperationException|RuntimeException|LinkageError e){return p.getGameProfile().getName();}}
    public static CompoundTag current(ServerPlayer p){try{return(CompoundTag)codec().encodeStart(NbtOps.INSTANCE,profile(p)).getOrThrow();}catch(ReflectiveOperationException e){throw new IllegalStateException("marriage.error.wallet",e);}}
    public static CompoundTag changed(ServerPlayer p,long delta){try{Object before=profile(p);long amount=Math.addExact(balance(p),delta);require(amount>=0,"funds");Object after=before.getClass().getMethod("withBalance",long.class).invoke(before,amount);for(var c:before.getClass().getRecordComponents())if(!c.getName().equals("balance"))require(java.util.Objects.equals(c.getAccessor().invoke(before),c.getAccessor().invoke(after)),"wallet");return(CompoundTag)codec().encodeStart(NbtOps.INSTANCE,after).getOrThrow();}catch(ReflectiveOperationException e){throw new IllegalStateException("marriage.error.wallet",e);}}
    public static void install(ServerPlayer p,CompoundTag before,CompoundTag after){require(current(p).equals(before),"ambiguous");try{p.setData(attachment(),codec().parse(NbtOps.INSTANCE,after).getOrThrow());}catch(ReflectiveOperationException e){throw new IllegalStateException("marriage.error.wallet",e);}}
    public static void sync(ServerPlayer p){try{int sync=Class.forName("pt.cronicasrp.network.ProfilePackets").getField("SYNC").getInt(null);Class.forName("pt.cronicasrp.network.ProfileNetwork").getMethod("send",ServerPlayer.class,int.class,String.class).invoke(null,p,sync,"");}catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}}
}
