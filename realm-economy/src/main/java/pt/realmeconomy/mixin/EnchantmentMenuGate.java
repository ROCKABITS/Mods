package pt.realmeconomy.mixin;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EnchantmentMenu.class) public abstract class EnchantmentMenuGate {
 @Inject(method="clickMenuButton",at=@At("HEAD"),cancellable=true) private void economy$guard(Player player,int choice,CallbackInfoReturnable<Boolean> callback){
  if(pt.realmeconomy.service.ServiceGates.enabled(player.level(),pt.realmeconomy.domain.ServiceKind.ENCHANT))callback.setReturnValue(false);
 }
}
