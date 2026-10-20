package pt.realmeconomy.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Original pixel ornament, velvet, brass and felt. No external artwork. */
public final class TradeTheme {
    public static final int GOLD=0xFFE1BB6B,LIGHT=0xFFFFEDBD,MUTED=0xFFBDA988;
    public static final int GREEN=0xFF173F34,GREEN_LIGHT=0xFF285B46,INK=0xFF281E18;
    public static final int RED=0xFFAF4845,VELVET=0xFF49252D,PAPER=0xFFF0DFB9;
    private TradeTheme() {}
    public static void frame(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+5,y+7,x+w+5,y+h+7,0x77000000);
        g.fill(x,y,x+w,y+h,0xFF1C1514);g.fill(x+1,y+1,x+w-1,y+h-1,0xFF96703B);
        g.fill(x+2,y+2,x+w-2,y+h-2,0xFF2D201D);g.fill(x+4,y+4,x+w-4,y+h-4,0xFF34232A);
        for(int row=y+8;row<y+h-7;row+=8)for(int col=x+8;col<x+w-7;col+=8) {
            int offset=((row-y)/8)%2*4;g.fill(col+offset,row,col+offset+1,row+1,0x286E3C43);
        }
        border(g,x+6,y+6,w-12,h-12,0xFF705334);
        corner(g,x+8,y+8,1,1);corner(g,x+w-9,y+8,-1,1);corner(g,x+8,y+h-9,1,-1);corner(g,x+w-9,y+h-9,-1,-1);
    }
    private static void corner(GuiGraphics g,int x,int y,int dx,int dy) {
        for(int i=0;i<11;i++){g.fill(x+dx*i,y,x+dx*i+1,y+1,GOLD);g.fill(x,y+dy*i,x+1,y+dy*i+1,GOLD);}
        diamond(g,x+dx*4,y+dy*4,2,0xFFB28A46);
    }
    public static void border(GuiGraphics g,int x,int y,int w,int h,int color) {
        g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);
    }
    public static void felt(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x,y,x+w,y+h,0xFF100F0D);g.fill(x+1,y+1,x+w-1,y+h-1,0xFFAD8448);
        g.fill(x+3,y+3,x+w-3,y+h-3,GREEN);border(g,x+6,y+6,w-12,h-12,0xFF3E6550);
        for(int py=y+10;py<y+h-8;py+=6)for(int px=x+9;px<x+w-8;px+=11)g.fill(px,py,px+1,py+1,0x183A8562);
    }
    public static void plaque(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+1,y+2,x+w+1,y+h+2,0x55000000);g.fill(x,y,x+w,y+h,0xFF987641);
        g.fill(x+1,y+1,x+w-1,y+h-1,0xFF241B1C);g.fill(x+2,y+2,x+w-2,y+3,0xFF614C35);
    }
    public static void diamond(GuiGraphics g,int cx,int cy,int r,int color) {
        for(int yy=-r;yy<=r;yy++){int half=r-Math.abs(yy);g.fill(cx-half,cy+yy,cx+half+1,cy+yy+1,color);}
    }
    public static void disk(GuiGraphics g,int cx,int cy,int r,int color) {
        for(int yy=-r;yy<=r;yy++){int half=(int)Math.sqrt((double)r*r-yy*yy);g.fill(cx-half,cy+yy,cx+half+1,cy+yy+1,color);}
    }
    public static void chip(GuiGraphics g,int cx,int cy,int radius,int color) {
        disk(g,cx+1,cy+2,radius,0x55000000);disk(g,cx,cy,radius,0xFFE5C579);disk(g,cx,cy,radius-1,color);
        for(int i=0;i<8;i++){double a=i*Math.PI/4;int xx=cx+(int)Math.round(Math.cos(a)*(radius-2)),yy=cy+(int)Math.round(Math.sin(a)*(radius-2));g.fill(xx-1,yy-1,xx+2,yy+2,0xFFEFD7A2);}
        if(radius>7)disk(g,cx,cy,radius-4,color);disk(g,cx,cy,Math.max(1,radius-6),0xFFD3A855);
    }
    public static void crown(GuiGraphics g,int cx,int cy,int scale) {
        g.pose().pushPose();g.pose().translate(cx,cy,0);g.pose().scale(scale,scale,1);
        g.fill(-8,-4,-5,3,GOLD);g.fill(-2,-7,2,3,GOLD);g.fill(5,-4,8,3,GOLD);g.fill(-7,1,7,6,GOLD);
        g.fill(-5,2,5,3,LIGHT);g.fill(-5,5,5,6,0xFF8A6130);diamond(g,0,3,1,RED);g.pose().popPose();
    }
    /** Same original eight glyphs as the physical reel display. */
    public static void symbol(GuiGraphics g,int symbol,int cx,int cy,int scale) {
        g.pose().pushPose();g.pose().translate(cx-8*scale,cy-8*scale,0);g.pose().scale(scale,scale,1);
        g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("realmeconomy","textures/block/symbols.png"),0,0,Math.floorMod(symbol,8)*16,0,16,16,128,16);
        g.pose().popPose();
    }
    public static void card(GuiGraphics g,Font font,String value,int x,int y,int w,int h,boolean hidden,float reveal) {
        if(reveal<=0)return;int shown=Math.max(2,(int)(w*Math.min(1,reveal))),left=x+(w-shown)/2;
        g.fill(left+2,y+3,left+shown+2,y+h+3,0x55000000);g.fill(left,y,left+shown,y+h,0xFFBDA274);
        g.fill(left+1,y+1,left+shown-1,y+h-1,hidden?0xFF5D2938:0xFFFFF1D2);
        if(shown<w*.7)return;
        if(hidden){border(g,left+3,y+3,shown-6,h-6,GOLD);for(int yy=y+7;yy<y+h-5;yy+=7)for(int xx=left+6;xx<left+shown-4;xx+=7)diamond(g,xx,yy,1,0xFFC49862);crown(g,left+shown/2,y+h/2,1);return;}
        String rank=value.length()>1?value.substring(0,value.length()-1):value;char suit=value.isEmpty()?'S':value.charAt(value.length()-1);
        int color=suit=='H'||suit=='D'?0xFFAF4045:0xFF263D37;String suitText=switch(suit){case 'H'->"♥";case 'D'->"♦";case 'C'->"♣";default->"♠";};
        g.drawString(font,rank,left+4,y+4,color,false);g.drawString(font,suitText,left+4,y+14,color,false);
        g.pose().pushPose();g.pose().translate(left+shown/2.0-4,y+h/2.0-5,0);g.pose().scale(1.6f,1.6f,1);g.drawString(font,suitText,0,0,color,false);g.pose().popPose();
        g.drawString(font,rank,left+shown-font.width(rank)-4,y+h-12,color,false);
    }
    public static Component fit(Font font,Component value,int width,boolean bold) {
        Component styled=value.copy().withStyle(s->s.withBold(bold));if(font.width(styled)<=width)return styled;
        String text=value.getString(),result="";for(int i=0;i<text.length();){int cp=text.codePointAt(i);String next=result+new String(Character.toChars(cp));if(font.width(Component.literal(next+"…").withStyle(s->s.withBold(bold)))>width)break;result=next;i+=Character.charCount(cp);}return Component.literal(result+"…").withStyle(s->s.withBold(bold));
    }
}
