package pt.realmfitness;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import pt.realmfitness.domain.FitnessLedger;
import pt.realmfitness.domain.Recovery;
import java.util.Locale;

/** Read-only tooltip status from the owning player's server snapshot. */
public final class RecoveryView {
    private static Recovery.Snapshot status;
    private static long remainingMillis, receivedAt;

    private RecoveryView() {}

    public static void update(JsonObject json){
        if(!json.has("recovery"))return;
        status=FitnessLedger.JSON.fromJson(json.get("recovery"),Recovery.Snapshot.class);
        remainingMillis=status.resetsAt()-json.get("now").getAsLong();
        receivedAt=System.nanoTime();
    }

    public static void clear(){status=null;}

    public static Component status(){
        Recovery.Status current=status==null?Recovery.Status.NEEDS_SET:status.status();
        if(current!=Recovery.Status.DISABLED&&(System.nanoTime()-receivedAt)/1_000_000L>=remainingMillis)
            current=Recovery.Status.NEEDS_SET;
        return Component.translatable("fitness.recovery."+current.name().toLowerCase(Locale.ROOT));
    }

    public static Component amount(){
        double reduction=status==null?.10:status.reduction();
        return Component.translatable("fitness.recovery.amount",Math.round(reduction*100));
    }
}
