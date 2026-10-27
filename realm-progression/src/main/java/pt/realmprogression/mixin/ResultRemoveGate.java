package pt.realmprogression.mixin;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ResultSlot.class)
public abstract class ResultRemoveGate {
    @Shadow @Final private Player player;
    @Inject(method="remove",at=@At("HEAD"),cancellable=true)
    private void progression$remove(int amount,CallbackInfoReturnable<ItemStack> ci){if(pt.realmprogression.service.CraftGate.blocked(player.level(),((Slot)(Object)this).getItem()))ci.setReturnValue(ItemStack.EMPTY);}
}
