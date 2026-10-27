package pt.realmeconomy.mixin.colony;
import com.minecolonies.core.colony.ColonyManager;
import com.minecolonies.api.colony.IColony;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ColonyManager.class,remap=false)
public abstract class ColonyDeletionMixin {
 @Inject(method="deleteColony",at=@At("HEAD"),cancellable=true)
 private void economy$protectBudget(IColony colony,boolean destroy,CallbackInfo ci){if(!pt.realmeconomy.colony.ColonyHooks.canDelete(colony))ci.cancel();}
}
