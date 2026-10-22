package pt.crownledger.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @ModifyVariable(method="causeFoodExhaustion",at=@At("HEAD"),argsOnly=true)
    private float realmTreasury$banquet(float amount){
        return ((Object)this) instanceof ServerPlayer player?pt.crownledger.service.BenefitEvents.exhaustion(player,amount):amount;
    }
}
