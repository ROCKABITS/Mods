package pt.realmfitness.client;

import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import pt.realmfitness.GymMount;

/** Mounted players use full exercise-specific articulated poses, visible to all tracking clients. */
public final class ExerciseAnimation {
    private static FitnessRig normal,slim;
    public static boolean render(AbstractClientPlayer player,com.mojang.blaze3d.vertex.PoseStack stack,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
        if(!(player.getVehicle() instanceof GymMount mount))return false;
        if(normal==null){normal=new FitnessRig(false);slim=new FitnessRig(true);}
        var motion=ExercisePose.sample(mount.exercise(),mount.visualTick(),mount.effort(),mount.action(),mount.actionAge());
        stack.pushPose();try{
            double yaw=mount.actorYaw()*Math.PI/180;stack.translate(-Math.sin(yaw)*motion.forward,motion.hips,Math.cos(yaw)*motion.forward);
            stack.mulPose(Axis.YP.rotationDegrees(180-mount.actorYaw()));stack.scale(-.9375f,-.9375f,.9375f);
            var rig=player.getModelName().equals("slim")?slim:normal;
            rig.render(motion,stack,buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation())),light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);return true;
        }finally{stack.popPose();}
    }
}
