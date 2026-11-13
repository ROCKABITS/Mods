package pt.realmfitness.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import pt.realmfitness.GymMount;
import pt.realmfitness.domain.Exercise;

/** Production equipment meshes move with the same pose targets as the player's hands. */
public final class GymMountRenderer extends NoopRenderer<GymMount> {
    public GymMountRenderer(EntityRendererProvider.Context context){super(context);}
    public static ModelResourceLocation model(Exercise exercise,String group){return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("realmfitness","block/"+exercise.id()+"_"+group));}
    @Override public void render(GymMount mount,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(!mount.isVehicle())return;var pose=ExercisePose.sample(mount.exercise(),mount.visualTick(),mount.effort(),mount.action(),mount.actionAge());var origin=mount.local(0,0,0);
        stack.pushPose();try{
            stack.translate(origin.x-mount.getX(),origin.y-mount.getY(),origin.z-mount.getZ());stack.mulPose(Axis.YP.rotationDegrees(180-mount.facing().toYRot()));
            switch(mount.exercise()){
                case BENCH->{stack.translate(0,pose.barY-30.25/16,pose.barZ-35.75/16);mesh(mount,"bar",stack,buffers,light);}
                case SQUAT->{stack.translate(0,.15+pose.barY-31.8/16,pose.barZ-19/16.0);mesh(mount,"bar",stack,buffers,light);}
                case ROW->{
                    stack.pushPose();stack.translate(0,0,pose.seatZ);mesh(mount,"seat",stack,buffers,light);stack.popPose();
                    stack.pushPose();stack.translate(0,pose.barY-13.5/16,pose.barZ-14/16.0);mesh(mount,"handle",stack,buffers,light);stack.popPose();
                    double dz=34/16.0-pose.barZ,dy=pose.barY-13.5/16,angle=Math.atan2(dy,dz);stack.translate(1,13.5/16,34/16.0);stack.mulPose(Axis.XP.rotation((float)angle));stack.scale(1,1,(float)(Math.hypot(dz,dy)/(20/16.0)));stack.translate(-1,-13.5/16,-34/16.0);mesh(mount,"cable",stack,buffers,light);
                }
                default->{}
            }
        }finally{stack.popPose();}
    }
    private static void mesh(GymMount mount,String group,PoseStack stack,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(model(mount.exercise(),group));stack.pushPose();stack.scale(2,2,2);mc.getBlockRenderer().getModelRenderer().renderModel(stack.last(),buffers.getBuffer(RenderType.cutout()),null,model,1,1,1,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,net.neoforged.neoforge.client.model.data.ModelData.EMPTY,RenderType.cutout());stack.popPose();
    }
}
