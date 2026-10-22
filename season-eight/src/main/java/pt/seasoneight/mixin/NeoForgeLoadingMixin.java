package pt.seasoneight.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pt.seasoneight.client.LoadingPresentation;

@Mixin(value = NeoForgeLoadingOverlay.class, remap = false)
public abstract class NeoForgeLoadingMixin {
    @Shadow private float currentProgress;
    @Inject(method = "render", at = @At("TAIL"))
    private void seasonEight$paint(GuiGraphics g, int x, int y, float partial, CallbackInfo ci) {
        LoadingPresentation.paint(g, currentProgress, "neoforge");
    }
}
