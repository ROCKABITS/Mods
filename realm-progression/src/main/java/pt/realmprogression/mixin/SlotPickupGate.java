package pt.realmprogression.mixin;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Slot.class)
public abstract class SlotPickupGate {
    @Inject(method="mayPickup",at=@At("HEAD"),cancellable=true)
    private void progression$pickup(Player p,CallbackInfoReturnable<Boolean> ci){if((Object)this instanceof ResultSlot slot&&pt.realmprogression.service.CraftGate.blocked(p.level(),slot.getItem()))ci.setReturnValue(false);}
}
