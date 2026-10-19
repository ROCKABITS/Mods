package pt.realmcasino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import pt.realmcasino.blockentity.CasinoBlockEntity;

/** Public, server-committed result surfaces. This renderer contains no random source or money logic. */
public final class CasinoRenderer implements BlockEntityRenderer<CasinoBlockEntity> {
    private static final ResourceLocation SYMBOLS=ResourceLocation.fromNamespaceAndPath("realmcasino","textures/block/symbols.png");
    private static final ResourceLocation WHEEL=ResourceLocation.fromNamespaceAndPath("realmcasino","textures/block/roulette_wheel.png");
    private static final java.util.Map<CasinoBlockEntity,Display> DISPLAYS=new java.util.WeakHashMap<>();
    private record Display(java.util.UUID id,int resultHash,long receivedAt,int fromStep,int steps) {}
    private static final int[] POCKETS={0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26};
    public CasinoRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(CasinoBlockEntity machine,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        int[] result=machine.result();long local=System.nanoTime()/1_000_000;Display display=DISPLAYS.get(machine);int hash=java.util.Arrays.hashCode(result);
        if(display==null){long age=Math.clamp(System.currentTimeMillis()-machine.startedAt(),0,10_000);display=new Display(machine.animationId(),hash,local-age,Math.max(0,result.length-1),result.length);DISPLAYS.put(machine,display);}
        else if(!display.id().equals(machine.animationId())||display.resultHash()!=hash){display=new Display(machine.animationId(),hash,local,display.id().equals(machine.animationId())?display.steps():0,result.length);DISPLAYS.put(machine,display);}
        if(result.length==0&&!java.util.List.of("roulette","wheel","chicken").contains(machine.game()))return;
        pose.pushPose();pose.translate(.5,0,.5);
        float angle=switch(machine.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)){case EAST->-90;case SOUTH->180;case WEST->90;default->0;};
        pose.mulPose(Axis.YP.rotationDegrees(angle));pose.translate(-.5,0,-.5);
        double elapsed=Math.max(0,local-display.receivedAt());
        if(machine.game().equals("slots")&&result.length==3)slots(result,elapsed,pose,buffers,light,overlay);
        else if(machine.game().equals("roulette"))roulette(result.length==1?result[0]:0,result.length==1?elapsed:10_000,pose,buffers,light,overlay);
        else if(machine.game().equals("blackjack"))cards(result,elapsed,pose,buffers,light);
        else if(machine.game().equals("plinko"))plinko(result,elapsed,pose,buffers,light,overlay);
        else if(machine.game().equals("wheel"))fortune(result,elapsed,pose,buffers,light,overlay);
        else if(machine.game().equals("chicken"))chicken(result,display.fromStep(),elapsed,pose,buffers,light,overlay);
        pose.popPose();
    }
    private static void vertex(VertexConsumer v,PoseStack.Pose pose,float x,float y,float z,float u,float vv,int light,int overlay){v.addVertex(pose.pose(),x,y,z).setColor(255,255,255,255).setUv(u,vv).setOverlay(overlay).setLight(light).setNormal(0,1,0);}
    private static void front(PoseStack pose,VertexConsumer v,float x0,float y0,float x1,float y1,float z,float u0,float v0,float u1,float v1,int light,int overlay){
        var p=pose.last();vertex(v,p,x0,y0,z,u1,v1,light,overlay);vertex(v,p,x1,y0,z,u0,v1,light,overlay);vertex(v,p,x1,y1,z,u0,v0,light,overlay);vertex(v,p,x0,y1,z,u1,v0,light,overlay);
    }
    private static void slots(int[] result,double elapsed,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        VertexConsumer v=buffers.getBuffer(RenderType.entityCutoutNoCull(SYMBOLS));
        for(int reel=0;reel<3;reel++){
            double p=CasinoPreferences.get().motion?Math.clamp(elapsed/(reel==0?1768:reel==1?2184:2600),0,1):1,ease=1-Math.pow(1-p,3);
            double offset=result[reel]+(1-ease)*8*(3+reel),fraction=offset-Math.floor(offset);int base=(int)Math.floor(offset);
            float left=(float)((3.5+reel*2.72)/16),right=left+2.25f/16;
            for(int row=-2;row<=2;row++){
                int symbol=Math.floorMod(base+row,8);float top=(float)(19.75/16.0+(row-fraction)*.155+.0703),bottom=top-.1406f;
                float min=Math.max(17.05f/16,bottom),max=Math.min(22.45f/16,top);if(min>=max)continue;
                float cropTop=(top-max)/.1406f,cropBottom=(top-min)/.1406f;
                front(pose,v,left,min,right,max,1.94f/16,symbol/8f,cropTop,(symbol+1)/8f,cropBottom,light,overlay);
            }
        }
    }
    private static void roulette(int result,double elapsed,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        int index=0;for(int i=0;i<POCKETS.length;i++)if(POCKETS[i]==result)index=i;
        double p=CasinoPreferences.get().motion?Math.clamp(elapsed/3200,0,1):1,rotation=(index+.5)*Math.PI*2/37+(1-Math.pow(1-p,4))*Math.PI*8;
        pose.pushPose();pose.translate(11.65/16,.838,6.4/16);pose.mulPose(Axis.YP.rotation((float)rotation));
        float radius=3.10f/16;var v=buffers.getBuffer(RenderType.entityCutoutNoCull(WHEEL));var matrix=pose.last();
        vertex(v,matrix,-radius,0,-radius,0,0,light,overlay);vertex(v,matrix,-radius,0,radius,0,1,light,overlay);vertex(v,matrix,radius,0,radius,1,1,light,overlay);vertex(v,matrix,radius,0,-radius,1,0,light,overlay);pose.popPose();
        // An ivory ball settles at the front pointer; its trajectory only illustrates the committed pocket.
        double ballAngle=-Math.PI/2+(1-Math.pow(1-p,3))*Math.PI*-10+Math.PI*10;
        pose.pushPose();pose.translate(11.65/16+Math.cos(ballAngle)*.172,.845,6.4/16+Math.sin(ballAngle)*.172);
        pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(.005f,.005f,.005f);
        Minecraft.getInstance().font.drawInBatch("●",-3,-3,0xFFF4E6BC,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,light);pose.popPose();
    }
    private static void cards(int[] cards,double elapsed,PoseStack pose,MultiBufferSource buffers,int light){
        int count=Math.min(cards.length,12);double p=CasinoPreferences.get().motion?Math.clamp(elapsed/850,0,1):1;
        for(int i=0;i<count;i++){
            int id=cards[i];String text;if(id<0)text="◆";else {int rank=id%13+1;String face=rank==1?"A":rank==11?"J":rank==12?"Q":rank==13?"K":""+rank;text=face+"♠♥♣♦".charAt(id/13);}
            double reveal=Math.clamp(p*2.2-i*.12,0,1);if(reveal<=0)continue;
            pose.pushPose();pose.translate((3.1+(i%6)*1.35)/16,.806+(i*.0001),(4.8+(i/6)*3.8)/16+(1-reveal)*.12);
            pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(.0075f,.0075f,.0075f);
            int color=id<0?0xFFE1BB6B:id/13==1||id/13==3?0xFFFFC1AD:0xFFF8EBC4;
            Minecraft.getInstance().font.drawInBatch(text,0,0,color,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,light);pose.popPose();
        }
    }
    private static final ResourceLocation BALL=ResourceLocation.fromNamespaceAndPath("realmcasino","textures/block/ivory_ball.png");
    private static final ResourceLocation FORTUNE=ResourceLocation.fromNamespaceAndPath("realmcasino","textures/block/fortune_face.png");
    private static void plinko(int[] path,double elapsed,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(path.length!=12)return;
        double p=CasinoPreferences.get().motion?Math.clamp(elapsed/3000,0,1):1,travel=p*13,x=8,y=28;
        int done=Math.min(12,(int)travel);for(int i=0;i<done;i++)x+=(path[i]==1?.44:-.44);
        if(done<12){double f=travel-done;x+=(path[done]==1?.44:-.44)*f;y=28-travel*1.27+Math.sin(f*Math.PI)*.2;}
        else y=12.76-Math.min(1,travel-12)*1.5;
        var v=buffers.getBuffer(RenderType.entityCutoutNoCull(BALL));front(pose,v,(float)(x-.26)/16,(float)(y-.26)/16,(float)(x+.26)/16,(float)(y+.26)/16,2.05f/16,0,0,1,1,light,overlay);
    }
    private static void fortune(int[] result,double elapsed,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        int sector=result.length==1?result[0]:0;double p=CasinoPreferences.get().motion&&result.length==1?Math.clamp(elapsed/3200,0,1):1;
        double rotation=-sector*Math.PI*2/20+(1-Math.pow(1-p,4))*Math.PI*8;
        pose.pushPose();pose.translate(.5,21.0/16,3.0/16);pose.mulPose(Axis.ZP.rotation((float)rotation));
        front(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(FORTUNE)),-6.3f/16,-6.3f/16,6.3f/16,6.3f/16,0,0,0,1,1,light,overlay);pose.popPose();
    }
    private static void mini(PoseStack pose,MultiBufferSource buffers,int light,int overlay,net.minecraft.world.level.block.Block block,double x,double y,double z,double w,double h,double d){
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.scale((float)(w/16),(float)(h/16),(float)(d/16));
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(),pose,buffers,light,overlay);pose.popPose();
    }
    private static void chicken(int[] path,int from,double elapsed,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        double p=CasinoPreferences.get().motion?Math.clamp(elapsed/650,0,1):1,ease=1-Math.pow(1-p,3),step=from+(path.length-from)*ease;
        boolean crash=path.length>0&&path[path.length-1]==0;
        pose.pushPose();pose.translate((1.9+step*1.12)/16,(12.78+Math.sin(p*Math.PI)*.5)/16,7.8/16);
        if(crash&&p>.7)pose.mulPose(Axis.ZP.rotationDegrees(65));
        var cream=net.minecraft.world.level.block.Blocks.SMOOTH_SANDSTONE;
        mini(pose,buffers,light,overlay,cream,-.30,0,-.38,.62,.5,.78);
        mini(pose,buffers,light,overlay,cream,.10,.48,-.32,.38,.4,.38);
        mini(pose,buffers,light,overlay,net.minecraft.world.level.block.Blocks.YELLOW_TERRACOTTA,.48,.54,-.28,.22,.17,.22);
        mini(pose,buffers,light,overlay,net.minecraft.world.level.block.Blocks.RED_TERRACOTTA,.20,.88,-.25,.16,.17,.2);
        mini(pose,buffers,light,overlay,net.minecraft.world.level.block.Blocks.BLACK_WOOL,.40,.72,-.335,.08,.08,.03);
        pose.popPose();
    }
    @Override public int getViewDistance(){return 48;}
}
