package pt.realmfitness.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import pt.realmfitness.*;
import pt.realmfitness.domain.Exercise;

public final class RecordBoardRenderer implements BlockEntityRenderer<RecordBoardEntity> {
    public RecordBoardRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(RecordBoardEntity board,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(-board.getBlockState().getValue(RecordBoardBlock.FACING).toYRot()));pose.translate(0,0,.438);pose.scale(.0055f,-.0055f,.0055f);
        var font=Minecraft.getInstance().font;String title=FitnessTheme.text("records_title");font.drawInBatch(title,-font.width(title)/2,-66,FitnessTheme.ACCENT,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
        try{
            Exercise exercise=Exercise.valueOf(board.exercise);String sub=FitnessTheme.exercise(exercise)+" · "+FitnessTheme.load(exercise,board.load);font.drawInBatch(font.plainSubstrByWidth(sub,148),-73,-51,FitnessTheme.MUTED,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
            var rows=JsonParser.parseString(board.display).getAsJsonArray();
            if(rows.isEmpty())font.drawInBatch(FitnessTheme.text("no_records"),-73,-20,FitnessTheme.MUTED,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
            for(int i=0;i<Math.min(5,rows.size());i++){var r=rows.get(i).getAsJsonObject();String name=(i+1)+". "+FitnessClient.str(r,"name");head(pose,buffers,java.util.UUID.fromString(FitnessClient.str(r,"player")),-73,-32+i*15,light,overlay);font.drawInBatch(font.plainSubstrByWidth(name,94),-60,-31+i*15,FitnessTheme.INK,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);font.drawInBatch(FitnessClient.num(r,"points")+"",39,-31+i*15,FitnessTheme.GREEN,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);}
            String period=FitnessTheme.text("period."+board.period);font.drawInBatch(period,-font.width(period)/2,52,FitnessTheme.MUTED,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
        }catch(RuntimeException invalid){/* Loading old/custom board data cannot break world rendering. */}
        pose.popPose();
    }
    private static void head(PoseStack pose,MultiBufferSource buffers,java.util.UUID player,float x,float y,int light,int overlay){
        var mc=Minecraft.getInstance();var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(player);
        var skin=info==null?net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(player):info.getSkinLocation();
        var v=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(skin));
        for(int layer=0;layer<2;layer++){
            float u=(layer==0?8:40)/64f,t=(layer==0?8:8)/64f,z=layer*.01f;
            vertex(v,pose,x,y,z,u,t,light,overlay);vertex(v,pose,x,y+10,z,u,t+.125f,light,overlay);vertex(v,pose,x+10,y+10,z,u+.125f,t+.125f,light,overlay);vertex(v,pose,x+10,y,z,u+.125f,t,light,overlay);
        }
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer v,PoseStack pose,float x,float y,float z,float u,float t,int light,int overlay){v.vertex(pose.last().pose(),x,y,z).color(255,255,255,255).uv(u,t).overlayCoords(overlay).uv2(light).normal(pose.last().normal(),0,0,1).endVertex();}
}
