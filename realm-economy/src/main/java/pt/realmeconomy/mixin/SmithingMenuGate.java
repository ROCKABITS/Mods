package pt.realmeconomy.mixin;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SmithingMenu.class) public abstract class SmithingMenuGate {
 @Inject(method="mayPickup",at=@At("HEAD"),cancellable=true) private void economy$guard(Player player,boolean ready,CallbackInfoReturnable<Boolean> callback){
  if(pt.realmeconomy.service.ServiceGates.enabled(player.level(),pt.realmeconomy.domain.ServiceKind.SMITHING))callback.setReturnValue(false);
 }
}
