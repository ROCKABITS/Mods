package pt.crownledger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

final class TreasuryArt {
    static final int INK=0xFF3B3024,MUTED=0xFF786B56,GOLD=0xFFB28A4D,PAPER=0xFFE8D8B5;
    static void frame(GuiGraphics g,int x,int y,int w,int h,int fill,int border){
        g.fill(x,y,x+w,y+h,fill);g.fill(x,y,x+w,y+1,border);g.fill(x,y+h-1,x+w,y+h,border);g.fill(x,y,x+1,y+h,border);g.fill(x+w-1,y,x+w,y+h,border);
    }
    static void seal(GuiGraphics g,int cx,int cy,int r){
        for(int i=-r;i<=r;i++){int half=(int)Math.sqrt(Math.max(0,r*r-i*i));g.fill(cx-half,cy+i,cx+half+1,cy+i+1,0xFFAE8548);}
        g.fill(cx-r+4,cy+3,cx+r-3,cy+5,0xFFF0D49A);
        for(int dx:new int[]{-4,0,4})g.fill(cx+dx-1,cy-4+(dx==0?-2:0),cx+dx+2,cy+2,0xFFF0D49A);
    }
    static final class Button extends AbstractButton {
        private final Runnable action;
        Button(int x,int y,int w,Component label,Runnable action){super(x,y,w,20,label);this.action=action;}
        @Override public void onPress(){if(active)action.run();}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            frame(g,getX(),getY(),width,height,active&&isHoveredOrFocused()?0xFFF7EDCF:active?0xFFDCC69C:0xFFD4C8AF,active&&isHoveredOrFocused()?0xFF4F6B51:GOLD);
            var font=Minecraft.getInstance().font;float s=Math.min(1F,(width-10F)/Math.max(1,font.width(getMessage())));
            g.pose().pushPose();g.pose().translate(getX()+(width-font.width(getMessage())*s)/2,getY()+(20-8*s)/2,0);g.pose().scale(s,s,1);
            g.drawString(font,getMessage(),0,0,active?INK:MUTED,false);g.pose().popPose();
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
    }
    private TreasuryArt(){}
}
