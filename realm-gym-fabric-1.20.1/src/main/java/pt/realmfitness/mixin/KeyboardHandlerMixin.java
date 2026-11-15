package pt.realmfitness.mixin;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pt.realmfitness.client.TrainingHud;
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void realmfitness$key(long window,int key,int scan,int action,int mods,CallbackInfo ci){var hud=TrainingHud.active();if(hud==null||Minecraft.getInstance().screen!=null)return;if(action==GLFW.GLFW_PRESS&&hud.keyPressed(key,scan,mods)||action==GLFW.GLFW_RELEASE&&hud.keyReleased(key,scan,mods)||action==GLFW.GLFW_REPEAT&&(key==GLFW.GLFW_KEY_SPACE||key==GLFW.GLFW_KEY_A||key==GLFW.GLFW_KEY_D||key==GLFW.GLFW_KEY_W||key==GLFW.GLFW_KEY_S))ci.cancel();}
}
