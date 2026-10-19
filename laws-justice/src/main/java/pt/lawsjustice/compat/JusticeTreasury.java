package pt.lawsjustice.compat;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import pt.lawsjustice.domain.FinanceLedger.Payment;

/** Optional, receipt-based Crown income. Paid funds remain held safely while it is unavailable. */
public final class JusticeTreasury {
    private JusticeTreasury(){}
    public static boolean loaded(){return ModList.get().isLoaded("kingdomtreasury");}
    public static void credit(MinecraftServer server,Payment payment){
        if(!loaded())throw new IllegalStateException("lawsjustice.error.finance_treasury");
        try{
            Class<?> api=Class.forName("pt.crownledger.api.CrownIncomeApi");if(api.getField("VERSION").getInt(null)!=1)throw new IllegalStateException("Unsupported Crown income API");
            Object result=api.getMethod("credit",MinecraftServer.class,UUID.class,String.class,long.class,UUID.class,String.class).invoke(null,server,payment.id(),payment.currency(),payment.amount(),payment.payer(),payment.memo());
            if(!(result instanceof Boolean))throw new IllegalStateException("Invalid Crown income result");
        }catch(InvocationTargetException failure){throw new IllegalStateException("lawsjustice.error.finance_treasury",failure.getCause());}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){throw new IllegalStateException("lawsjustice.error.finance_treasury",failure);}
    }
}
