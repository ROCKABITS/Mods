package pt.lawsjustice.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Original stone, wrought iron and parchment ornaments drawn as crisp GUI pixels. */
public final class JusticeTheme {
    public static final int GOLD=0xFFD6B56C, LIGHT=0xFFF1E5C7, MUTED=0xFFB6B0A0, PAPER=0xFFE6D6AD;
    public static final int INK=0xFF332C24, RED=0xFFC77C70, GREEN=0xFF9FB99A;
    private JusticeTheme() {}
    public static void frame(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+5,y+7,x+w+5,y+h+7,0x88000000);
        g.fill(x,y,x+w,y+h,0xFF111617);g.fill(x+1,y+1,x+w-1,y+h-1,0xFF807348);
        g.fill(x+3,y+3,x+w-3,y+h-3,0xFF252D2C);
        for(int yy=y+5;yy<y+h-5;yy+=16) {
            g.fill(x+5,yy,x+w-5,yy+1,0xFF1C2423);
            for(int xx=x+5+(((yy-y)/16)%2)*22;xx<x+w-5;xx+=44)g.fill(xx,yy,xx+1,Math.min(yy+16,y+h-5),0xFF1B2222);
        }
        border(g,x+6,y+6,w-12,h-12,0xFF58625B);
        for(int xx:new int[]{x+9,x+w-10})for(int yy:new int[]{y+9,y+h-10}){g.fill(xx-2,yy-2,xx+3,yy+3,0xFF111818);g.fill(xx-1,yy-1,xx+2,yy+2,GOLD);g.fill(xx-1,yy-1,xx,yy,0xFFFAE3A1);}
    }
    public static void border(GuiGraphics g,int x,int y,int w,int h,int color) {
        if(w<1||h<1)return;g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);
    }
    public static void panel(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+2,y+3,x+w+2,y+h+3,0x44000000);g.fill(x,y,x+w,y+h,0xFF6B644E);
        g.fill(x+1,y+1,x+w-1,y+h-1,0xFF1B2423);g.fill(x+2,y+2,x+w-2,y+3,0xFF404C44);
        for(int px=x+7;px<x+w-5;px+=8)g.fill(px,y+h-3,px+2,y+h-2,0xFF323D36);
    }
    public static void parchment(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+2,y+3,x+w+2,y+h+3,0x44000000);g.fill(x,y,x+w,y+h,0xFFAE9365);
        g.fill(x+1,y+1,x+w-1,y+h-1,PAPER);g.fill(x+3,y+3,x+w-3,y+4,0xFFBDA779);
        for(int yy=y+9;yy<y+h-5;yy+=11)g.fill(x+6,yy,x+w-6,yy+1,0x127F6948);
        for(int xx=x+4;xx<x+w-3;xx+=13){g.fill(xx,y,xx+3,y+1,0xFF917B54);g.fill(xx+4,y+h-1,xx+6,y+h,0xFF917B54);}
    }
    public static void diamond(GuiGraphics g,int cx,int cy,int r,int color) {
        for(int yy=-r;yy<=r;yy++){int half=r-Math.abs(yy);g.fill(cx-half,cy+yy,cx+half+1,cy+yy+1,color);}
    }
    public static void scales(GuiGraphics g,int cx,int cy,int size) {
        g.pose().pushPose();g.pose().translate(cx,cy,0);g.pose().scale(size,size,1);
        g.fill(-1,-10,2,11,GOLD);g.fill(-9,10,10,12,GOLD);g.fill(-11,-7,12,-5,GOLD);diamond(g,0,-10,3,GOLD);
        for(int side:new int[]{-1,1}) {int xx=side*8;g.fill(xx,-5,xx+1,2,0xFF9B9D88);g.fill(xx-5,2,xx+6,3,GOLD);g.fill(xx-4,3,xx+5,5,GOLD);g.fill(xx-2,5,xx+3,6,GOLD);}
        g.pose().popPose();
    }
    public static void hourglass(GuiGraphics g,int cx,int cy,double progress,long frame) {
        g.fill(cx-12,cy-20,cx+13,cy-16,GOLD);g.fill(cx-12,cy+16,cx+13,cy+20,GOLD);
        for(int side:new int[]{-1,1})g.fill(cx+side*12-1,cy-16,cx+side*12+1,cy+16,0xFF8B866E);
        for(int yy=0;yy<12;yy++){int half=11-yy;g.fill(cx-half,cy-15+yy,cx+half+1,cy-14+yy,0xFF536760);g.fill(cx-half,cy+14-yy,cx+half+1,cy+15-yy,0xFF536760);}
        int sand=(int)Math.round(11*Math.clamp(progress,0,1));
        for(int yy=0;yy<sand;yy++){int half=11-yy;g.fill(cx-half+1,cy+14-yy,cx+half,cy+15-yy,GOLD);}
        for(int yy=0;yy<11-sand;yy++){int half=11-yy;g.fill(cx-half+1,cy-15+yy,cx+half,cy-14+yy,0xFFB99C61);}
        if(progress<1){int drop=(int)(frame/90%8);g.fill(cx,cy-1+drop,cx+1,cy+1+drop,LIGHT);}
    }
    public static Component fit(Font font,Component value,int width,boolean bold) {
        Component styled=value.copy().withStyle(s->s.withBold(bold));if(font.width(styled)<=width)return styled;
        return Component.literal(font.plainSubstrByWidth(value.getString(),Math.max(1,width-9))+"…").withStyle(s->s.withBold(bold));
    }
}
