package pt.realmfitness.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import pt.realmfitness.GymMount;
import pt.realmfitness.GymLayout;
import pt.realmfitness.domain.Minigame;
import pt.realmfitness.domain.Exercise;

/** Production equipment meshes move with the same pose targets as the player's hands. */
public final class GymMountRenderer extends NoopRenderer<GymMount> {
    public GymMountRenderer(EntityRendererProvider.Context context){super(context);}
    public static ResourceLocation model(Exercise exercise,String group){return new ResourceLocation("realmfitness","block/"+exercise.id()+"_"+group);}
    @Override public void render(GymMount mount,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(!mount.isVehicle())return;var pose=ExercisePose.sample(mount.exercise(),mount.visualTick(),mount.effort(),mount.action(),mount.actionAge());var origin=mount.local(0,0,0);
        stack.pushPose();try{
            stack.translate(origin.x-mount.getX(),origin.y-mount.getY(),origin.z-mount.getZ());stack.mulPose(Axis.YP.rotationDegrees(180-mount.facing().toYRot()));
            var e=mount.exercise();var actor=GymLayout.point(e,"actor");
            switch(e){
                case BENCH,SQUAT->{var rest=GymLayout.point(e,"bar_rest");stack.translate(0,actor.y()+pose.barY-rest.y(),pose.barZ-rest.z());mesh(mount,"bar",stack,buffers,light);}
                case RUN->{double travel=(mount.visualTick()*.045)%(3/16.0);stack.translate(0,0,travel);mesh(mount,"belt",stack,buffers,light);}
                case BOX->{
                    var pivot=GymLayout.point(e,"bag_pivot");double age=mount.actionAge();boolean hit=(mount.action()&(Minigame.LEFT|Minigame.RIGHT))!=0;
                    double swing=hit&&age>=0&&age<45?Math.sin(age*.24)*Math.exp(-age/13)*.13:0;
                    stack.translate(pivot.x(),pivot.y(),pivot.z());stack.mulPose(Axis.XP.rotation((float)-swing));stack.translate(-pivot.x(),-pivot.y(),-pivot.z());mesh(mount,"bag",stack,buffers,light);
                }
                case ROW->{
                    stack.pushPose();stack.translate(0,0,pose.seatZ);mesh(mount,"seat",stack,buffers,light);stack.popPose();
                    var rest=GymLayout.point(e,"hand_rest");stack.pushPose();stack.translate(0,pose.barY-rest.y(),pose.barZ-rest.z());mesh(mount,"handle",stack,buffers,light);stack.popPose();
                    var fan=GymLayout.point(e,"flywheel_center");stack.pushPose();stack.translate(fan.x(),fan.y(),fan.z());stack.mulPose(Axis.ZP.rotation((float)(mount.visualTick()*.14)));stack.translate(-fan.x(),-fan.y(),-fan.z());mesh(mount,"flywheel",stack,buffers,light);stack.popPose();
                    var cable=GymLayout.point(e,"cable_origin");double dz=cable.z()-pose.barZ,dy=pose.barY-cable.y(),angle=Math.atan2(dy,dz);stack.translate(cable.x(),cable.y(),cable.z());stack.mulPose(Axis.XP.rotation((float)angle));stack.scale(1,1,(float)(Math.hypot(dz,dy)/(cable.z()-rest.z())));stack.translate(-cable.x(),-cable.y(),-cable.z());mesh(mount,"cable",stack,buffers,light);
                }
                default->{}
            }
        }finally{stack.popPose();}
    }
    private static void mesh(GymMount mount,String group,PoseStack stack,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(model(mount.exercise(),group));stack.pushPose();stack.scale(2,2,2);mc.getBlockRenderer().getModelRenderer().renderModel(stack.last(),buffers.getBuffer(RenderType.cutout()),null,model,1,1,1,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,net.minecraftforge.client.model.data.ModelData.EMPTY,RenderType.cutout());stack.popPose();
    }
}
