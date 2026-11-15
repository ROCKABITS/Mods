package pt.realmfitness.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pt.realmfitness.client.TrainingHud;
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method="onPress",at=@At("HEAD"),cancellable=true)
    private void realmfitness$mouse(long window,int button,int action,int mods,CallbackInfo ci){var hud=TrainingHud.active();if(hud==null||Minecraft.getInstance().screen!=null||button>1)return;if(action==GLFW.GLFW_PRESS)hud.mouseClicked(0,0,button);else if(action==GLFW.GLFW_RELEASE)hud.mouseReleased(0,0,button);ci.cancel();}
}
