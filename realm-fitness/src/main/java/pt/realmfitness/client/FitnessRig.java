package pt.realmfitness.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import org.joml.*;

/** Skin-preserving articulated rig. Independent segments meet exactly at solved joints. */
final class FitnessRig {
    private final ModelPart torso,head;
    private final ModelPart[] arms=new ModelPart[2],elbows=new ModelPart[2],legs=new ModelPart[2],knees=new ModelPart[2];
    FitnessRig(boolean slim){
        MeshDefinition mesh=new MeshDefinition();PartDefinition r=mesh.getRoot();
        var body=r.addOrReplaceChild("torso",CubeListBuilder.create().texOffs(16,16).addBox(-4,-12,-2,8,12,4).texOffs(16,32).addBox(-4,-12,-2,8,12,4,new CubeDeformation(.25f)),PartPose.ZERO);
        body.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8).texOffs(32,0).addBox(-4,-8,-4,8,8,8,new CubeDeformation(.5f)),PartPose.offset(0,-12,0));
        for(int i=0;i<2;i++){
            float width=slim?3:4;int u=i==0?40:32,v=i==0?16:48,cu=i==0?40:48,cv=i==0?32:48;
            r.addOrReplaceChild("arm"+i,limb(u,v,cu,cv,width),PartPose.ZERO);r.addOrReplaceChild("elbow"+i,limb(u,v+6,cu,cv+6,width),PartPose.ZERO);
            r.addOrReplaceChild("leg"+i,limb(i==0?0:16,i==0?16:48,0,i==0?32:48,4),PartPose.ZERO);r.addOrReplaceChild("knee"+i,limb(i==0?0:16,i==0?22:54,0,i==0?38:54,4),PartPose.ZERO);
        }
        var root=LayerDefinition.create(mesh,64,64).bakeRoot();torso=root.getChild("torso");head=torso.getChild("head");for(int i=0;i<2;i++){arms[i]=root.getChild("arm"+i);elbows[i]=root.getChild("elbow"+i);legs[i]=root.getChild("leg"+i);knees[i]=root.getChild("knee"+i);}
    }
    private static CubeListBuilder limb(int u,int v,int ou,int ov,float width){return CubeListBuilder.create().texOffs(u,v).addBox(-width/2,0,-2,width,6,4).texOffs(ou,ov).addBox(-width/2,0,-2,width,6,4,new CubeDeformation(.25f));}
    void render(ExercisePose p,PoseStack stack,VertexConsumer buffer,int light,int overlay){
        torso.xRot=(float)p.lean;torso.yRot=(float)p.twist;torso.zRot=(float)p.sway;head.xRot=(float)p.head;head.z=(float)p.headOffset;torso.render(stack,buffer,light,overlay);
        for(int i=0;i<2;i++){segment(arms[i],p.arms[i].start(),p.arms[i].joint(),stack,buffer,light,overlay);segment(elbows[i],p.arms[i].joint(),p.arms[i].end(),stack,buffer,light,overlay);segment(legs[i],p.legs[i].start(),p.legs[i].joint(),stack,buffer,light,overlay);segment(knees[i],p.legs[i].joint(),p.legs[i].end(),stack,buffer,light,overlay);}
    }
    private static void segment(ModelPart part,ExercisePose.V from,ExercisePose.V to,PoseStack stack,VertexConsumer buffer,int light,int overlay){
        stack.pushPose();stack.translate(from.x()/16,from.y()/16,from.z()/16);var delta=to.sub(from);stack.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f((float)delta.x(),(float)delta.y(),(float)delta.z()).normalize()));part.render(stack,buffer,light,overlay);stack.popPose();
    }
}
