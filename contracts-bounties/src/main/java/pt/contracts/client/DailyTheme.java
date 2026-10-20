package pt.contracts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Ink, emerald lacquer, warm parchment and small hand-drawn heraldic marks. */
public final class DailyTheme {
    public static final int INK=0xFF382C24,MUTED=0xFF807052,PAPER=0xFFF0DEB6,DARK=0xFF183C33,GOLD=0xFFDDBA6D,PALE=0xFFF9E9C5;
    private DailyTheme(){}
    public static Component tr(String key,Object... args){return Component.translatable("daily.contracts."+key,args);}
    public static String money(long cents){return String.format(java.util.Locale.ROOT,"%,d,%02d €",cents/100,cents%100).replace(',',' ').replaceFirst(" ([0-9]{2}) €$",",$1 €");}
    public static void panel(GuiGraphics g,int x,int y,int w,int h){
        g.fill(x+5,y+6,x+w+5,y+h+6,0x80201814);g.fill(x,y,x+w,y+h,0xFF46352A);g.fill(x+2,y+2,x+w-2,y+h-2,GOLD);g.fill(x+4,y+4,x+w-4,y+h-4,DARK);
        for(int sy=y+9;sy<y+h-6;sy+=9)g.fill(x+7,sy,x+w-7,sy+1,0x09101008);
        for(int sx:new int[]{x+8,x+w-10})for(int sy:new int[]{y+8,y+h-10})g.fill(sx,sy,sx+2,sy+2,GOLD);
    }
    public static Button button(int x,int y,int w,int h,Component text,Runnable callback){
        return new Button(x,y,w,h,text,b->callback.run(),s->s.get()){
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
                int edge=active?(isHoveredOrFocused()?0xFFFFD78A:0xFFB59253):0xFF75674E;
                g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),edge);g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,isHoveredOrFocused()&&active?0xFF3B6550:0xFF24473A);
                g.fill(getX()+2,getY()+2,getX()+getWidth()-2,getY()+3,0xFF506D4D);
                var font=Minecraft.getInstance().font;String label=font.plainSubstrByWidth(getMessage().getString(),getWidth()-8);
                g.drawCenteredString(font,label,getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?PALE:0xFF9C927B);
            }
        };
    }
    public static int color(String difficulty){return switch(difficulty){case "EASY"->0xFF91AAA8;case "MEDIUM"->GOLD;case "HARD"->0xFFC79DEC;default->0xFFF3D573;};}
    public static void seal(GuiGraphics g,int x,int y,int size,String difficulty){
        var texture=net.minecraft.resources.ResourceLocation.parse("contracts:textures/gui/daily/seal_"+difficulty.toLowerCase(java.util.Locale.ROOT)+".png");
        g.blit(texture,x,y,size,size,0,0,96,96,96,96);
    }
}
