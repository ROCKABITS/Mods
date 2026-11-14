package pt.realmfitness.mixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import pt.realmfitness.*;
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method="causeFallDamage",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private float realmfitness$fall(float multiplier){return (Object)this instanceof ServerPlayer player?(float)(multiplier*(1-FitnessService.fallReduction(player))):multiplier;}
    @ModifyArg(method="travel",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V",ordinal=0),index=0)
    private float realmfitness$swim(float speed){var self=(LivingEntity)(Object)this;return self.getAttribute(FitnessMod.SWIM_SPEED)==null?speed:(float)(speed*self.getAttributeValue(FitnessMod.SWIM_SPEED));}
}
