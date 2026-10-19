package pt.lawsjustice.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import pt.lawsjustice.domain.FinanceLedger;

/** Existing Chronicles wallet or real emerald inventory. No separate player currency is created. */
public final class JusticeWallet {
    private static volatile Api resolved;
    private JusticeWallet(){}
    private record Api(AttachmentType<Object> attachment,Class<?> type,Method created,Method balance,Method withBalance,List<Method> stable,Method sync,int syncCode){}
    public static String currentCurrency(){return ModList.get().isLoaded("cronicasrp")?"CHRONICLES":"EMERALD";}
    public static long balance(ServerPlayer player,String currency){
        thread(player);if(currency.equals("EMERALD")){long value=0;for(int n=0;n<player.getInventory().getContainerSize();n++){var stack=player.getInventory().getItem(n);if(stack.is(Items.EMERALD))value+=stack.getCount();}return value;}
        if(!currency.equals("CHRONICLES"))throw failure("finance_currency");Api api=api();return balance(api,profile(api,player));
    }
    /** The caller pairs this mutation with its own persistent receipt before saving the player. */
    public static void debit(ServerPlayer player,String currency,long amount){
        thread(player);if(amount<=0||amount>FinanceLedger.MAX_AMOUNT)throw failure("finance_amount");if(balance(player,currency)<amount)throw failure("insufficient_funds");
        if(currency.equals("EMERALD")){
            long remaining=amount;for(int n=0;n<player.getInventory().getContainerSize()&&remaining>0;n++){var stack=player.getInventory().getItem(n);if(stack.is(Items.EMERALD)){int take=(int)Math.min(remaining,stack.getCount());stack.shrink(take);remaining-=take;}}
            if(remaining!=0)throw failure("finance_wallet");player.getInventory().setChanged();return;
        }
        Api api=api();Object before=profile(api,player);long wanted=balance(api,before)-amount;Object after=invoke(api.withBalance(),before,wanted);
        if(!api.type().isInstance(after)||!(boolean)invoke(api.created(),after)||balance(api,after)!=wanted)throw failure("finance_wallet");
        for(Method field:api.stable())if(!Objects.equals(invoke(field,before),invoke(field,after)))throw failure("finance_wallet");
        player.setData(api.attachment(),after);
    }
    /** Presentation happens after persistence; a failed packet never reverses a funded payment. */
    public static void sync(ServerPlayer player,String currency){
        try{if(currency.equals("CHRONICLES")){Api api=api();invoke(api.sync(),null,player,api.syncCode(),"");}else player.containerMenu.broadcastChanges();}
        catch(RuntimeException|LinkageError ignored){}
    }
    private static Object profile(Api api,ServerPlayer player){Object profile=player.getData(api.attachment());if(!api.type().isInstance(profile)||!(boolean)invoke(api.created(),profile))throw failure("finance_profile");return profile;}
    private static long balance(Api api,Object profile){long value=(long)invoke(api.balance(),profile);if(value<0||value>FinanceLedger.MAX_AMOUNT)throw failure("finance_wallet");return value;}
    @SuppressWarnings("unchecked") private static Api api(){
        if(!ModList.get().isLoaded("cronicasrp"))throw failure("finance_wallet");Api value=resolved;if(value!=null)return value;
        synchronized(JusticeWallet.class){if(resolved!=null)return resolved;try{
            Class<?> profile=Class.forName("pt.cronicasrp.domain.CharacterProfile"),attachments=Class.forName("pt.cronicasrp.ModAttachments"),network=Class.forName("pt.cronicasrp.network.ProfileNetwork"),packets=Class.forName("pt.cronicasrp.network.ProfilePackets"),money=Class.forName("pt.cronicasrp.domain.Money");
            Object source=attachments.getField("PROFILE").get(null);if(!(source instanceof Supplier<?> supplier)||!(supplier.get()instanceof AttachmentType<?> attachment)||!profile.isRecord()||money.getField("MAX_CENTS").getLong(null)!=FinanceLedger.MAX_AMOUNT)throw failure("finance_wallet");
            Method created=profile.getMethod("created"),balance=profile.getMethod("balance"),withBalance=profile.getMethod("withBalance",long.class);if(created.getReturnType()!=boolean.class||balance.getReturnType()!=long.class||withBalance.getReturnType()!=profile)throw failure("finance_wallet");
            List<Method> stable=new ArrayList<>();for(var field:profile.getRecordComponents())if(!field.getName().equals("balance"))stable.add(field.getAccessor());
            resolved=new Api((AttachmentType<Object>)attachment,profile,created,balance,withBalance,List.copyOf(stable),network.getMethod("send",ServerPlayer.class,int.class,String.class),packets.getField("SYNC").getInt(null));return resolved;
        }catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("lawsjustice.error.finance_wallet",error);}}
    }
    private static Object invoke(Method method,Object receiver,Object...args){try{return method.invoke(receiver,args);}catch(InvocationTargetException error){throw new IllegalStateException("lawsjustice.error.finance_wallet",error.getCause());}catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("lawsjustice.error.finance_wallet",error);}}
    private static void thread(ServerPlayer p){if(p==null||p.getServer()==null||!p.getServer().isSameThread())throw failure("thread");}
    private static IllegalStateException failure(String key){return new IllegalStateException("lawsjustice.error."+key);}
}
