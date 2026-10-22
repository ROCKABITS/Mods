package pt.seasoneight.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pt.seasoneight.SeasonConfig;

@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void seasonEight$title(CallbackInfoReturnable<String> ci) {
        if (SeasonConfig.enabled(SeasonConfig.TITLE)) ci.setReturnValue(SeasonConfig.title());
    }
}
