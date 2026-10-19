package pt.realmcasino.client;

import com.google.gson.JsonArray;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import pt.realmcasino.domain.FortuneWheel;
import pt.realmcasino.domain.Plinko;

/** Original walnut, velvet and brass illustrations. Animation follows the committed server path. */
final class CasinoArcadeArt {
    static final ResourceLocation WHEEL=ResourceLocation.fromNamespaceAndPath("realmcasino","textures/block/fortune_face.png");
    private CasinoArcadeArt() {}
    static String multiplier(int thousandths){return java.math.BigDecimal.valueOf(thousandths,3).stripTrailingZeros().toPlainString()+"×";}
    private static void text(GuiGraphics g,Font font,String value,int cx,int y,int color,float scale){
        g.pose().pushPose();g.pose().translate(cx,y,0);g.pose().scale(scale,scale,1);g.drawString(font,value,-font.width(value)/2,0,color,false);g.pose().popPose();
    }
    static void plinko(GuiGraphics g,Font font,int x,int y,int w,JsonArray path,double progress){
        int cx=x+w/2,top=y+42;double gap=Math.min(24,(w-35)/13.0);
        CasinoTheme.plaque(g,(int)(cx-gap*6.7),top-9,(int)(gap*13.4),190);
        g.fill((int)(cx-gap*6.6),top-7,(int)(cx+gap*6.6),top+178,0xFF153D34);
        for(int row=0;row<12;row++)for(int pin=0;pin<=row;pin++){
            int px=(int)Math.round(cx+(pin-row/2.0)*gap),py=top+12+row*12;
            CasinoTheme.disk(g,px+1,py+1,2,0x88000000);CasinoTheme.disk(g,px,py,2,CasinoTheme.GOLD);g.fill(px-1,py-1,px,py,0xFFFFEDBD);
        }
        int pocket=-1;if(path.size()==12){pocket=0;for(var bit:path)pocket+=bit.getAsInt();}
        for(int i=0;i<=12;i++){
            int left=(int)Math.round(cx+(i-6.5)*gap),right=(int)Math.round(cx+(i-5.5)*gap);
            boolean selected=progress>=1&&i==pocket;
            g.fill(left,top+151,right,top+177,selected?0xFFB58940:i==0||i==12?0xFF6A2937:0xFF254C3D);
            CasinoTheme.border(g,left,top+151,right-left,26,selected?CasinoTheme.LIGHT:0xFF9E793D);
            String label=multiplier(Plinko.RETURNS.get(i));float scale=(float)Math.min(.82,(right-left-2)/(double)Math.max(1,font.width(label)));
            text(g,font,label,(left+right)/2,top+160,selected?0xFF201C14:CasinoTheme.LIGHT,scale);
        }
        if(path.size()==12){
            double travel=progress*13,px=cx,py=top-2;
            int done=Math.min(12,(int)travel);for(int i=0;i<done;i++)px+=(path.get(i).getAsInt()==1?.5:-.5)*gap;
            if(done<12){double f=travel-done;px+=(path.get(done).getAsInt()==1?.5:-.5)*gap*f;py=top-2+travel*12-Math.sin(f*Math.PI)*3;}
            else py=top+142+Math.min(1,travel-12)*12;
            if(progress>=1)py=top+153;
            CasinoTheme.disk(g,(int)px+2,(int)py+3,4,0x66000000);CasinoTheme.disk(g,(int)px,(int)py,4,CasinoTheme.GOLD);CasinoTheme.disk(g,(int)px,(int)py,3,0xFFFFF3CF);
            g.fill((int)px-1,(int)py-2,(int)px+1,(int)py,0xFFFFFFFF);
        }else {CasinoTheme.disk(g,cx,top-2,4,CasinoTheme.LIGHT);CasinoTheme.diamond(g,cx,top-13,3,CasinoTheme.GOLD);}
    }
    static void wheel(GuiGraphics g,Font font,int x,int y,int w,JsonArray results,double progress){
        int cx=x+w/2,cy=y+124,r=84;int sector=results.size()==1?results.get(0).getAsInt():0;
        CasinoTheme.disk(g,cx+3,cy+5,r+4,0x66000000);CasinoTheme.disk(g,cx,cy,r+3,0xFF583A28);
        double angle=results.isEmpty()?0:-sector*Math.PI*2/20+(1-Math.pow(1-progress,4))*Math.PI*8;
        g.pose().pushPose();g.pose().translate(cx,cy,0);g.pose().mulPose(Axis.ZP.rotation((float)angle));g.pose().scale(r/64f,r/64f,1);
        g.blit(WHEEL,-64,-64,0,0,128,128,128,128);g.pose().popPose();
        for(int i=0;i<10;i++)g.fill(cx-7+i/2,cy-r-8+i,cx+8-i/2,cy-r-7+i,0xFFEDCC7A);
        CasinoTheme.diamond(g,cx,cy-r-7,3,0xFFAF4845);
        CasinoTheme.disk(g,cx,cy,10,0xFFE1BB6B);CasinoTheme.disk(g,cx,cy,7,0xFF5B382A);CasinoTheme.diamond(g,cx,cy,3,0xFFAF4845);
        if(!results.isEmpty()&&progress>=1)text(g,font,multiplier(FortuneWheel.RETURNS.get(sector)),cx,y+221,CasinoTheme.GOLD,1.2f);
    }
    static void chicken(GuiGraphics g,Font font,int x,int y,int w,int steps,int fromStep,boolean crashed,double progress,long elapsed,boolean motion){
        int left=x+16,top=y+48,ww=w-32,h=136;double lane=ww/11.0;
        CasinoTheme.plaque(g,left-3,top-3,ww+6,h+6);g.fill(left,top,left+ww,top+h,0xFF315139);
        for(int row=0;row<7;row++)for(int col=0;col<ww/8;col++)if((col+row*3)%5==0)g.fill(left+col*8,top+row*21,left+col*8+2,top+row*21+2,0xFF68834B);
        for(int i=1;i<=10;i++){
            int a=(int)(left+i*lane),b=(int)(left+(i+1)*lane-2);
            g.fill(a,top+2,b,top+h-2,0xFF4A4F45);
            for(int yy=top+6;yy<top+h-3;yy+=13){g.fill(a+2,yy,b-1,yy+1,0xFF686C57);if(b-a>14)g.fill(a+(b-a)/2,yy,a+(b-a)/2+1,yy+6,0xFF363D35);}
            if(i<=steps&&!(crashed&&i==steps))CasinoTheme.diamond(g,(a+b)/2,top+11,2,CasinoTheme.GOLD);
            int cartY=top+20+(int)((motion?elapsed*(.018+i*.002):i*14)+i*31)%100;
            if(crashed&&i==steps)cartY=top+h/2-8;
            else if(i<=steps&&Math.abs(cartY-(top+h/2-8))<18)cartY=top+20;
            cart(g,(a+b)/2,cartY,Math.max(5,Math.min(11,(b-a)-4)),i%3==0);
            text(g,font,""+i,(a+b)/2,top+h-12,i<=steps?CasinoTheme.GOLD:CasinoTheme.MUTED,.7f);
        }
        double ease=1-Math.pow(1-progress,3),position=fromStep+(steps-fromStep)*ease;
        int cx=(int)(left+(position+.5)*lane),cy=top+h/2-(int)(Math.sin(progress*Math.PI)*8);
        CasinoTheme.disk(g,cx,top+h/2+8,6,0x55000000);
        chickenGlyph(g,cx,cy,crashed&&progress>=.7);
        if(crashed&&progress>.7){CasinoTheme.diamond(g,cx-12,cy-8,2,CasinoTheme.RED);CasinoTheme.diamond(g,cx+12,cy+2,2,CasinoTheme.RED);g.fill(cx-7,cy-13,cx-4,cy-11,CasinoTheme.LIGHT);g.fill(cx+8,cy-10,cx+11,cy-8,CasinoTheme.LIGHT);}
        // The model is a road diorama: movement timing illustrates, rather than decides, each crossing.
        CasinoTheme.border(g,left,top,ww,h,0xFFA88748);
    }
    private static void cart(GuiGraphics g,int cx,int y,int w,boolean red){
        g.fill(cx-w/2-2,y+2,cx-w/2,y+6,0xFF201C18);g.fill(cx+w/2,y+2,cx+w/2+2,y+6,0xFF201C18);
        g.fill(cx-w/2-2,y+11,cx-w/2,y+15,0xFF201C18);g.fill(cx+w/2,y+11,cx+w/2+2,y+15,0xFF201C18);
        g.fill(cx-w/2,y,cx+w/2+1,y+17,0xFF987344);g.fill(cx-w/2+1,y+2,cx+w/2,y+15,red?0xFF8F4245:0xFF66724B);g.fill(cx-w/2+1,y+2,cx+w/2,y+3,CasinoTheme.GOLD);
    }
    private static void chickenGlyph(GuiGraphics g,int cx,int cy,boolean crashed){
        g.fill(cx-6,cy-3,cx+4,cy+5,0xFFCEBD92);g.fill(cx-6,cy-4,cx+3,cy+3,0xFFFFF0CB);
        g.fill(cx,cy-8,cx+6,cy-2,0xFFFFF0CB);g.fill(cx+6,cy-5,cx+9,cy-2,0xFFE6AF48);
        g.fill(cx+1,cy-10,cx+5,cy-8,0xFFB34B47);g.fill(cx-5,cy-1,cx-1,cy+2,0xFFE1D3AC);
        g.fill(cx-4,cy+5,cx-2,cy+8,0xFFE2AD4A);g.fill(cx+1,cy+5,cx+3,cy+8,0xFFE2AD4A);
        g.fill(cx+3,cy-6,cx+4,cy-4,crashed?CasinoTheme.RED:0xFF272A22);
    }
}
