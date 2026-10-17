package pt.realmnotifications.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;

/** Original pixel artwork and restrained gilded parchment shared by every notification view. */
public final class NotificationTheme {
    public static final int INK=0xFF443323, GOLD=0xFFD9B66B, PALE=0xFFF4E4BF, GREEN=0xFF263F36;
    public static final ResourceLocation ICONS=ResourceLocation.fromNamespaceAndPath("realmnotifications","textures/gui/seals.png");
    private NotificationTheme() {}
    public static int accent(String kind) {
        return switch(kind) {case "money","reward","salary"->0xFFB58A35;case "warning"->0xFFAA5544;case "election","treasury"->0xFF7060A0;case "market"->0xFF4F897A;default->0xFF5F7F54;};
    }
    public static int icon(String kind) {
        return switch(kind) {case "money"->0;case "reward"->1;case "contract"->2;case "election"->3;case "market"->4;case "salary"->5;case "treasury"->6;case "warning"->8;default->7;};
    }
    public static void panel(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+3,y+4,x+w+3,y+h+4,0x66000000);
        g.fill(x,y,x+w,y+h,0xFF191E1A);g.fill(x+1,y+1,x+w-1,y+h-1,0xFF7E6339);
        g.fill(x+2,y+2,x+w-2,y+h-2,0xFF293E32);g.fill(x+3,y+3,x+w-3,y+h-3,0xFF1C3029);
        for(int i=5;i<w-5;i+=11)g.fill(x+i,y+4,x+i+1,y+h-4,0x10203729);
        corner(g,x+4,y+4,1,1);corner(g,x+w-5,y+4,-1,1);corner(g,x+4,y+h-5,1,-1);corner(g,x+w-5,y+h-5,-1,-1);
    }
    private static void corner(GuiGraphics g,int x,int y,int dx,int dy) {
        for(int i=0;i<8;i++) {g.fill(x+dx*i,y,x+dx*i+1,y+1,GOLD);g.fill(x,y+dy*i,x+1,y+dy*i+1,GOLD);}
        g.fill(x+dx*3,y+dy*3,x+dx*3+2,y+dy*3+2,0xFFA8874A);
    }
    public static void icon(GuiGraphics g,String kind,int x,int y) {
        g.blit(ICONS,x,y,icon(kind)*32,0,32,32,288,32);
    }
    public static void card(GuiGraphics g,int x,int y,int w,String kind,Component title,Component body,double remaining,int count) {
        var font=Minecraft.getInstance().font;int h=CardLayout.HEIGHT;
        g.fill(x+3,y+4,x+w+3,y+h+4,0x55000000);
        g.fill(x+1,y,x+w-1,y+h,0xFF382E22);g.fill(x,y+2,x+w,y+h-2,0xFF382E22);
        g.fill(x+2,y+2,x+w-2,y+h-2,0xFFB28C4C);
        g.fill(x+3,y+3,x+w-3,y+25,0xFF243C32);
        g.fill(x+4,y+4,x+w-4,y+5,0xFF516047);
        g.fill(x+3,y+25,x+w-3,y+h-3,0xFFE8D5AA);
        g.fill(x+4,y+26,x+w-4,y+27,0xFFF9EDCF);
        // Sparse deterministic fibre marks preserve readability and a hand-made paper texture.
        for(int i=0;i<22;i++) {
            int px=x+7+(i*43)%(w-14),py=y+31+(i*19)%39;
            g.fill(px,py,px+2,py+1,0x10745A35);
        }
        g.fill(x+3,y+25,x+46,y+h-3,0xFFDDC593);
        g.fill(x+45,y+29,x+46,y+h-8,0xFFBEA474);
        g.fill(x+5,y+6,x+8,y+9,GOLD);g.fill(x+w-8,y+6,x+w-5,y+9,GOLD);
        Component fitted=Component.literal(font.plainSubstrByWidth(title.getString(),w-(count>1?54:28))).withStyle(s->s.withBold(true));
        g.drawString(font,fitted,x+13,y+10,GOLD,false);
        icon(g,kind,x+9,y+31);
        List<FormattedCharSequence> lines=font.split(body,w-62);
        int available=Math.min(3,lines.size());int lineY=y+32+(3-available)*3;
        for(int i=0;i<available;i++) {
            if(i==2 && lines.size()>3) {
                // Leave the ellipsis its own clear column; the complete body is in the journal.
                g.enableScissor(x+53,lineY,x+w-25,lineY+10);
                g.drawString(font,lines.get(i),x+53,lineY,INK,false);g.disableScissor();
                g.drawString(font,Component.literal("…"),x+w-20,lineY,INK,false);
            } else g.drawString(font,lines.get(i),x+53,lineY,INK,false);
            lineY+=12;
        }
        if(count>1)g.drawString(font,"×"+count,x+w-27,y+13,PALE,false);
        g.fill(x+4,y+h-5,x+w-4,y+h-4,0xFFBBA06E);
        g.fill(x+4,y+h-5,x+4+(int)((w-8)*Math.clamp(remaining,0,1)),y+h-4,accent(kind));
        g.fill(x+4,y+h-3,x+w-4,y+h-2,0xFF8A693B);
    }
}
