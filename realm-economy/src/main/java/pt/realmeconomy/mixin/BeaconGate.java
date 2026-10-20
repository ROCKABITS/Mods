package pt.realmeconomy.mixin;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BeaconBlockEntity.class) public abstract class BeaconGate {
 @Inject(method="applyEffects",at=@At("HEAD"),cancellable=true) private static void economy$guard(Level level,BlockPos pos,int layers,Holder<MobEffect> first,Holder<MobEffect> second,CallbackInfo callback){
  if(pt.realmeconomy.service.ServiceGates.enabled(level,pt.realmeconomy.domain.ServiceKind.BEACON)){callback.cancel();try{pt.realmeconomy.service.BeaconService.apply(level,pos,layers);}catch(RuntimeException|LinkageError frozen){}}
 }
}
