package pt.realmprogression.mixin;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({CraftingMenu.class,InventoryMenu.class})
public abstract class QuickCraftGate {
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void progression$shift(Player p,int index,CallbackInfoReturnable<ItemStack> ci){if(index==0&&pt.realmprogression.service.CraftGate.blocked(p.level(),((AbstractContainerMenu)(Object)this).getSlot(0).getItem()))ci.setReturnValue(ItemStack.EMPTY);}
}
