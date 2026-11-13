package pt.realmfitness.client;

import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import pt.realmfitness.GymMount;

/** Mounted players use full exercise-specific articulated poses, visible to all tracking clients. */
@EventBusSubscriber(modid="realmfitness",value=Dist.CLIENT)
public final class ExerciseAnimation {
    private static FitnessRig normal,slim;
    @SubscribeEvent public static void render(RenderPlayerEvent.Pre event){
        if(!(event.getEntity() instanceof AbstractClientPlayer player)||!(player.getVehicle() instanceof GymMount mount))return;
        if(normal==null){normal=new FitnessRig(false);slim=new FitnessRig(true);}
        var motion=ExercisePose.sample(mount.exercise(),mount.visualTick(),mount.effort(),mount.action(),mount.actionAge());var stack=event.getPoseStack();
        stack.pushPose();try{
            double yaw=mount.actorYaw()*Math.PI/180;stack.translate(-Math.sin(yaw)*motion.forward,motion.hips,Math.cos(yaw)*motion.forward);
            stack.mulPose(Axis.YP.rotationDegrees(180-mount.actorYaw()));stack.scale(-.9375f,-.9375f,.9375f);
            var rig=player.getSkin().model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM?slim:normal;
            rig.render(motion,stack,event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture())),event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);event.setCanceled(true);
        }finally{stack.popPose();}
    }
}
