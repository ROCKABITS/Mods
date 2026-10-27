package pt.realmprogression.client;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
public final class ResearchArt {
    public static final int GOLD=0xFFE2BB69,TEXT=0xFFF4E6C5,MUTED=0xFFB4B9BB,INK=0xFF3E3028,PAPER=0xFFE8D7AF;
    public static void border(GuiGraphics g,int x,int y,int w,int h,int c){g.fill(x,y,x+w,y+1,c);g.fill(x,y+h-1,x+w,y+h,c);g.fill(x,y,x+1,y+h,c);g.fill(x+w-1,y,x+w,y+h,c);}
    public static void panel(GuiGraphics g,int x,int y,int w,int h){
        g.fill(x+3,y+4,x+w+3,y+h+4,0x77000000);g.fill(x,y,x+w,y+h,0xFF211A19);border(g,x+1,y+1,w-2,h-2,0xFF976D3D);border(g,x+3,y+3,w-6,h-6,0xFF4C3432);g.fill(x+5,y+5,x+w-5,y+h-5,0xFF142B38);border(g,x+7,y+7,w-14,h-14,0xFF677771);
        for(int dx:new int[]{9,w-10})for(int dy:new int[]{9,h-10}){int sx=dx<w/2?1:-1,sy=dy<h/2?1:-1;for(int k=0;k<8;k++){g.fill(x+dx+sx*k,y+dy,x+dx+sx*k+1,y+dy+1,GOLD);g.fill(x+dx,y+dy+sy*k,x+dx+1,y+dy+sy*k+1,GOLD);}}
    }
    public static void paper(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0xFF8C7145);g.fill(x+1,y+1,x+w-1,y+h-1,PAPER);border(g,x+4,y+4,w-8,h-8,0xFFBBA575);for(int yy=y+11;yy<y+h-6;yy+=8)g.fill(x+8,yy,x+w-8,yy+1,0x0C6C4F35);}
    public static void disk(GuiGraphics g,int x,int y,int r,int c){for(int yy=-r;yy<=r;yy++){int w=(int)Math.sqrt(r*r-yy*yy);g.fill(x-w,y+yy,x+w+1,y+yy+1,c);}}
    public static void seal(GuiGraphics g,int x,int y,boolean unlocked){disk(g,x+1,y+2,12,0x44000000);disk(g,x,y,12,GOLD);disk(g,x,y,10,unlocked?0xFF296958:0xFF5A3649);g.fill(x-5,y-3,x-3,y+4,TEXT);g.fill(x-1,y-6,x+1,y+4,TEXT);g.fill(x+3,y-3,x+5,y+4,TEXT);g.fill(x-4,y+2,x+4,y+5,TEXT);}
    public static Component fit(net.minecraft.client.gui.Font f,Component c,int width){return f.width(c)<=width?c:Component.literal(f.plainSubstrByWidth(c.getString(),Math.max(0,width-8))+"…");}
    public static final class Button extends AbstractButton {
        private final Runnable action;public boolean selected;
        public Button(int x,int y,int w,int h,Component text,Runnable action){super(x,y,w,h,text);this.action=action;}
        public void onPress(){action.run();}
        protected void renderWidget(GuiGraphics g,int mx,int my,float partial){int x=getX(),y=getY(),w=getWidth(),h=getHeight();g.fill(x,y,x+w,y+h,active?(isHoveredOrFocused()?0xFF345267:selected?0xFF3F4148:0xFF203C4C):0xFF303B40);border(g,x,y,w,h,active?GOLD:0xFF65706E);if(selected)g.fill(x+2,y+2,x+4,y+h-2,GOLD);var f=Minecraft.getInstance().font;g.drawCenteredString(f,fit(f,getMessage(),w-10),x+w/2,y+(h-8)/2,active?TEXT:MUTED);}
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    private ResearchArt(){}
}
