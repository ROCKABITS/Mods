package pt.realmfitness.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pt.realmfitness.client.TrainingHud;
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method="setScreen",at=@At("HEAD"))
    private void realmfitness$screen(Screen screen,CallbackInfo ci){if(screen!=null&&TrainingHud.active()!=null)TrainingHud.active().cancel();}
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void realmfitness$attack(CallbackInfoReturnable<Boolean> ci){if(TrainingHud.active()!=null)ci.setReturnValue(false);}
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void realmfitness$attackHeld(boolean held,CallbackInfo ci){if(TrainingHud.active()!=null)ci.cancel();}
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void realmfitness$use(CallbackInfo ci){if(TrainingHud.active()!=null)ci.cancel();}
}
