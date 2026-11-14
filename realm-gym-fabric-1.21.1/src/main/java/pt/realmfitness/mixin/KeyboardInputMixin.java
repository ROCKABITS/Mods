package pt.realmfitness.mixin;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pt.realmfitness.client.TrainingHud;
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method="tick",at=@At("TAIL"))
    private void realmfitness$movement(boolean slow,float factor,CallbackInfo ci){if(TrainingHud.active()!=null){forwardImpulse=leftImpulse=0;jumping=shiftKeyDown=false;}}
}
