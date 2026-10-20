package pt.realmeconomy.mixin;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BrewingStandBlockEntity.class) public abstract class BrewingGate {
 @Inject(method="serverTick",at=@At("HEAD"),cancellable=true) private static void economy$guard(Level level,BlockPos pos,BlockState state,BrewingStandBlockEntity stand,CallbackInfo callback){
  if(pt.realmeconomy.service.ServiceGates.enabled(level,pt.realmeconomy.domain.ServiceKind.BREWING))callback.cancel();
 }
}
