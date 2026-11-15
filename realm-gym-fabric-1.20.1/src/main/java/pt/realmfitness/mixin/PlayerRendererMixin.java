package pt.realmfitness.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pt.realmfitness.client.ExerciseAnimation;
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method="render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"),cancellable=true)
    private void realmfitness$render(AbstractClientPlayer player,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){if(ExerciseAnimation.render(player,pose,buffers,light))ci.cancel();}
}
