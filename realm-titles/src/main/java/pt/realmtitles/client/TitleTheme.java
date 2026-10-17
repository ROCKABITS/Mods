package pt.realmtitles.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Original heraldic pixel ornaments, forest velvet and warm parchment. */
public final class TitleTheme {
    public static final int INK=0xFF3C3025, MUTED=0xFF7D6D50, GOLD=0xFFD6B576;
    public static final int GREEN=0xFF233C32, LIGHT_GREEN=0xFF3E5B45, PAPER=0xFFEADAB5;
    public static final int LIGHT=0xFFF7EDCF, RED=0xFF954B3D;
    public static final int LABEL=0xFFCEC8A5, WARNING=0xFFF0A18B;
    private TitleTheme() {}

    public static void frame(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+4,y+5,x+w+4,y+h+5,0x66000000);
        g.fill(x,y,x+w,y+h,0xFF17251E);
        g.fill(x+1,y+1,x+w-1,y+h-1,0xFF8E7345);
        g.fill(x+2,y+2,x+w-2,y+h-2,0xFF263F34);
        g.fill(x+4,y+4,x+w-4,y+h-4,0xFF1D322A);
        for(int i=11;i<w-8;i+=13)g.fill(x+i,y+5,x+i+1,y+h-5,0x102B563D);
        corner(g,x+7,y+7,1,1);corner(g,x+w-8,y+7,-1,1);
        corner(g,x+7,y+h-8,1,-1);corner(g,x+w-8,y+h-8,-1,-1);
    }
    private static void corner(GuiGraphics g,int x,int y,int dx,int dy) {
        for(int i=0;i<9;i++) {
            int xx=x+dx*i, yy=y+dy*i;
            g.fill(xx,y,xx+1,y+1,GOLD);g.fill(x,yy,x+1,yy+1,GOLD);
        }
        g.fill(x+dx*3,y+dy*3,x+dx*3+2,y+dy*3+2,0xFFAD8D50);
    }
    public static void paper(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+2,y+3,x+w+2,y+h+3,0x33000000);
        g.fill(x,y,x+w,y+h,0xFFAA8B55);
        g.fill(x+1,y+1,x+w-1,y+h-1,PAPER);
        g.fill(x+2,y+2,x+w-2,y+3,LIGHT);
        g.fill(x+2,y+h-3,x+w-2,y+h-2,0xFFC9B384);
        for(int i=0;i<w/12;i++) {
            int px=x+5+(i*43)%Math.max(1,w-10),py=y+6+(i*29)%Math.max(1,h-12);
            g.fill(px,py,px+2,py+1,0x10745A35);
        }
    }
    /** Wax seal with a small gold crown; all pixels belong to this mod. */
    public static void seal(GuiGraphics g,int cx,int cy,int scale,boolean wax) {
        int shade=wax?0xFF743E32:0xFF51674A, light=wax?0xFFA75B45:0xFF82915A;
        g.pose().pushPose();g.pose().translate(cx,cy,0);g.pose().scale(scale,scale,1);
        g.fill(-7,6,-2,17,0xFFB69653);g.fill(2,6,7,17,0xFFB69653);
        g.fill(-7,14,-5,18,0xFF1D322A);g.fill(5,14,7,18,0xFF1D322A);
        g.fill(-9,-6,9,7,shade);g.fill(-6,-9,6,10,shade);g.fill(-11,-3,11,4,shade);
        g.fill(-7,-6,7,6,light);g.fill(-5,-8,5,8,light);
        g.fill(-6,-4,-3,1,GOLD);g.fill(-1,-6,2,1,GOLD);g.fill(4,-4,7,1,GOLD);
        g.fill(-5,0,6,4,GOLD);g.fill(-4,5,5,6,0xFF705D37);
        g.fill(-4,1,5,2,0xFFF5DFA0);
        g.pose().popPose();
    }
    public static Component fit(Font font,Component text,int width,boolean bold) {
        Component styled=text.copy().withStyle(s->s.withBold(bold));
        if(font.width(styled)<=width)return styled;
        // The same styled font measures, trims and renders the label, including bold glyph advances.
        String source=text.getString(),result="";
        for(int i=0;i<source.length();) {
            int cp=source.codePointAt(i);String next=result+new String(Character.toChars(cp));
            if(font.width(Component.literal(next+"…").withStyle(s->s.withBold(bold)))>width)break;
            result=next;i+=Character.charCount(cp);
        }
        return Component.literal(result+"…").withStyle(s->s.withBold(bold));
    }
}
