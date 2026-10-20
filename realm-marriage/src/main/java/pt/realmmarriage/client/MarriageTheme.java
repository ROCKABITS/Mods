package pt.realmmarriage.client;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
public final class MarriageTheme {
    public static final int PAPER=0xFFF0DFB6,INK=0xFF49362D,GOLD=0xFFD6B36A,WINE=0xFF753A4D,GREEN=0xFF35584A,MUTED=0xFF827253;
    private MarriageTheme(){}
    public static Component tr(String k,Object... args){return Component.translatable("marriage."+k,args);}
    public static String money(long amount){return String.format(Locale.ROOT,"%d,%02d €",amount/100,amount%100);}
    public static void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x+5,y+7,x+w+5,y+h+7,0x80201618);g.fill(x,y,x+w,y+h,0xFF432C35);g.fill(x+2,y+2,x+w-2,y+h-2,GOLD);g.fill(x+4,y+4,x+w-4,y+h-4,PAPER);g.fill(x+7,y+7,x+w-7,y+32,WINE);for(int xx:new int[]{x+8,x+w-11})for(int yy:new int[]{y+37,y+h-11}){g.fill(xx,yy,xx+3,yy+3,GOLD);}}
    public static Button button(int x,int y,int w,int h,Component label,Runnable action){return new Button(x,y,w,h,label,b->action.run(),s->s.get()){@Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),active?GOLD:0xFFB0A487);g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,active?(isHoveredOrFocused()?0xFF8D4960:WINE):0xFFBCAE91);g.fill(getX()+2,getY()+2,getX()+getWidth()-2,getY()+3,0xFFB16E7E);var f=Minecraft.getInstance().font;g.drawCenteredString(f,f.plainSubstrByWidth(getMessage().getString(),getWidth()-8),getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?0xFFFFEBC9:0xFF746650);}};}
    public static void rings(GuiGraphics g,int x,int y,int size){g.blit(ResourceLocation.parse("realmmarriage:textures/gui/rings.png"),x,y,size,size,0,0,128,128,128,128);}
}
