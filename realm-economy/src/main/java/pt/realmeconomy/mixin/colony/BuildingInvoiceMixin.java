package pt.realmeconomy.mixin.colony;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IWorkManager;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pt.realmeconomy.colony.*;
@Mixin(value=WorkOrderBuilding.class,remap=false)
public abstract class BuildingInvoiceMixin implements ColonyOrderAccess {
 @Unique private UUID realmEconomy$invoice;
 public UUID realmEconomy$invoice(){return realmEconomy$invoice;}
 @Inject(method="onAdded",at=@At("HEAD"))
 private void economy$added(IColony c,boolean reading,CallbackInfo ci){if(!reading&&realmEconomy$invoice==null&&pt.realmeconomy.compat.ChroniclesBridge.loaded()&&c!=null&&c.getWorld()!=null&&!c.getWorld().isClientSide&&pt.realmeconomy.data.EconomyData.get(c.getWorld().getServer()).colonies().policy().enabled())realmEconomy$invoice=UUID.randomUUID();realmEconomy$invoice=ColonyHooks.added((WorkOrderBuilding)(Object)this,c,reading,realmEconomy$invoice);}
 @Inject(method="write",at=@At("TAIL"))
 private void economy$write(CompoundTag tag,CallbackInfo ci){if(realmEconomy$invoice!=null)tag.putUUID("realmEconomyInvoice",realmEconomy$invoice);}
 @Inject(method="read",at=@At("TAIL"))
 private void economy$read(CompoundTag tag,IWorkManager manager,CallbackInfo ci){realmEconomy$invoice=tag.hasUUID("realmEconomyInvoice")?tag.getUUID("realmEconomyInvoice"):null;}
 @Inject(method="canBuildIgnoringDistance",at=@At("HEAD"),cancellable=true)
 private void economy$canBuild(IBuilding builder,BlockPos location,int level,CallbackInfoReturnable<Boolean> cir){if(!ColonyHooks.allowed((WorkOrderBuilding)(Object)this,builder.getColony(),realmEconomy$invoice))cir.setReturnValue(false);}
}
