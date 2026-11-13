package pt.realmfitness.client;

import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import pt.realmfitness.domain.Exercise;

/** Gym poses are visible to nearby clients; the player's own skin and outer skin layers are retained. */
@EventBusSubscriber(modid="realmfitness",value=Dist.CLIENT)
public final class ExerciseAnimation {
    @SubscribeEvent public static void render(RenderPlayerEvent.Pre event){
        var motion=FitnessClient.MOTIONS.get(event.getEntity().getUUID());if(motion==null||!(event.getEntity() instanceof AbstractClientPlayer player))return;
        double phase=(motion.tick()+(System.nanoTime()-motion.received())/50_000_000.0)*Math.PI*2/48;float wave=(float)Math.sin(phase);
        var model=event.getRenderer().getModel();var pose=event.getPoseStack();pose.pushPose();
        try{
            pose.mulPose(Axis.YP.rotationDegrees(180-player.yBodyRot));pose.scale(-.9375f,-.9375f,.9375f);pose.translate(0,-1.501,0);
            model.setAllVisible(true);model.crouching=false;model.setupAnim(player,0,0,player.tickCount+event.getPartialTick(),0,0);
            switch(Exercise.valueOf(motion.exercise())){
                case BENCH -> {model.body.xRot=-.55f;model.rightArm.xRot=model.leftArm.xRot=-1.5f-wave*.55f;model.rightLeg.xRot=model.leftLeg.xRot=-.6f;}
                case RUN -> {model.rightArm.xRot=wave;model.leftArm.xRot=-wave;model.rightLeg.xRot=-wave;model.leftLeg.xRot=wave;}
                case BOX -> {model.rightArm.xRot=-1.2f-Math.max(0,wave);model.leftArm.xRot=-1.2f+Math.min(0,wave);model.body.yRot=wave*.15f;}
                case PULL -> {pose.translate(0,-.15f*(1+wave),0);model.rightArm.xRot=model.leftArm.xRot=-2.6f-wave*.4f;model.rightLeg.xRot=model.leftLeg.xRot=.3f;}
                case ROW -> {model.body.xRot=.3f+wave*.25f;model.rightArm.xRot=model.leftArm.xRot=-1.3f+wave*.65f;model.rightLeg.xRot=model.leftLeg.xRot=-1.3f;}
                case SQUAT -> {pose.translate(0,.15f*(1+wave),0);model.body.xRot=.25f*(1+wave);model.rightLeg.xRot=model.leftLeg.xRot=-.7f*(1+wave);model.rightArm.xRot=model.leftArm.xRot=-1.4f;}
            }
            model.hat.copyFrom(model.head);model.jacket.copyFrom(model.body);model.rightSleeve.copyFrom(model.rightArm);model.leftSleeve.copyFrom(model.leftArm);model.rightPants.copyFrom(model.rightLeg);model.leftPants.copyFrom(model.leftLeg);
            model.renderToBuffer(pose,event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture())),event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            event.setCanceled(true);
        }finally{pose.popPose();}
    }
}
