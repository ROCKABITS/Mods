package pt.cronicasrp.mixins;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pt.cronicasrp.RpNames;

/** Vanilla decorates team prefixes too; replace its root hover without changing account identity. */
@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void cronicasrp$decoratePublicName(CallbackInfoReturnable<Component> callback) {
        Player player = (Player) (Object) this;
        String name = player instanceof ServerPlayer serverPlayer ? RpNames.publicName(serverPlayer)
                : player.level().isClientSide() ? RpNames.clientName(player.getUUID()) : "";
        if (!name.isEmpty()) callback.setReturnValue(RpNames.withPublicMetadata(player, callback.getReturnValue().copy(), name));
    }
}
