package pt.realmfitness.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Skin-preserving articulated arms and legs, with separate elbow and knee joints. */
final class FitnessRig {
    private final ModelPart root,torso,head;
    private final ModelPart[] arms=new ModelPart[2],elbows=new ModelPart[2],legs=new ModelPart[2],knees=new ModelPart[2];
    FitnessRig(boolean slim){
        MeshDefinition mesh=new MeshDefinition();PartDefinition r=mesh.getRoot();
        var body=r.addOrReplaceChild("torso",CubeListBuilder.create().texOffs(16,16).addBox(-4,-12,-2,8,12,4).texOffs(16,32).addBox(-4,-12,-2,8,12,4,new CubeDeformation(.25f)),PartPose.ZERO);
        body.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8).texOffs(32,0).addBox(-4,-8,-4,8,8,8,new CubeDeformation(.5f)),PartPose.offset(0,-12,0));
        for(int i=0;i<2;i++){
            int sign=i==0?-1:1;float width=slim?3:4;
            int armU=i==0?40:32,armV=i==0?16:48,coatU=i==0?40:48,coatV=i==0?32:48;
            var arm=body.addOrReplaceChild("arm"+i,limb(armU,armV,coatU,coatV,width),PartPose.offset(sign*5,-11,0));arm.addOrReplaceChild("elbow",limb(armU,armV+6,coatU,coatV+6,width),PartPose.offset(0,6,0));
            var leg=r.addOrReplaceChild("leg"+i,limb(i==0?0:16,i==0?16:48,0,i==0?32:48,4),PartPose.offset(sign*1.9f,0,0));leg.addOrReplaceChild("knee",limb(i==0?0:16,i==0?22:54,0,i==0?38:54,4),PartPose.offset(0,6,0));
        }
        root=LayerDefinition.create(mesh,64,64).bakeRoot();torso=root.getChild("torso");head=torso.getChild("head");for(int i=0;i<2;i++){arms[i]=torso.getChild("arm"+i);elbows[i]=arms[i].getChild("elbow");legs[i]=root.getChild("leg"+i);knees[i]=legs[i].getChild("knee");}
    }
    private static CubeListBuilder limb(int u,int v,int overlayU,int overlayV,float width){return CubeListBuilder.create().texOffs(u,v).addBox(-width/2,0,-2,width,6,4).texOffs(overlayU,overlayV).addBox(-width/2,0,-2,width,6,4,new CubeDeformation(.25f));}
    void render(ExercisePose pose,PoseStack stack,VertexConsumer buffer,int light,int overlay){
        torso.xRot=(float)pose.lean;torso.yRot=(float)pose.twist;torso.zRot=(float)pose.sway;head.xRot=(float)pose.head;
        for(int i=0;i<2;i++){int sign=i==0?1:-1;arms[i].xRot=(float)pose.arms[i*2];arms[i].zRot=(float)(sign*pose.armSpread);elbows[i].xRot=(float)pose.arms[i*2+1];legs[i].xRot=(float)pose.legs[i*2];legs[i].zRot=(float)(sign*pose.legSpread);knees[i].xRot=(float)pose.legs[i*2+1];}
        root.render(stack,buffer,light,overlay);
    }
}
