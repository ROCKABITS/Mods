package pt.realmfitness.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import pt.realmfitness.domain.Exercise;

/** Realm Origins creator palette, kept local so the gym has no mandatory companion. */
public final class FitnessTheme {
    public static final int INK=0xFFE7E2EE,MUTED=0xFFA49CAF,ACCENT=0xFFC7B6DE,GREEN=0xFF9DC2B0,PANEL=0xF0181422,BORDER=0xFF494052;
    private FitnessTheme(){}
    public static Component tr(String key,Object... args){return Component.translatable(key.startsWith("fitness.")?key:"fitness."+key,args);}
    public static String text(String key,Object... args){return tr(key,args).getString();}
    public static String exercise(Exercise e){return text("exercise."+e.id());}
    public static String load(Exercise e,int value){return e.weighted?value+" kg":text("resistance",value);}
    public static void frame(GuiGraphics g,int x,int y,int w,int h,int fill){g.fill(x,y,x+w,y+h,fill);g.renderOutline(x,y,w,h,BORDER);for(int dx:new int[]{3,w-6})for(int dy:new int[]{3,h-6})g.fill(x+dx,y+dy,x+dx+3,y+dy+3,ACCENT);}
    public static void label(GuiGraphics g,String value,int x,int y,int width,int color){var f=Minecraft.getInstance().font;g.drawString(f,f.plainSubstrByWidth(value,Math.max(1,width)),x,y,color,false);}
    public static void bar(GuiGraphics g,int x,int y,int width,double progress,int color){g.fill(x,y,x+width,y+3,0xFF38313F);g.fill(x,y,x+(int)(width*Math.clamp(progress,0,1)),y+3,color);}
    public static Button button(int x,int y,int w,int h,Component label,Runnable action){
        return new Button(x,y,w,h,label,b->action.run(),supplier->supplier.get()){
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){frame(g,getX(),getY(),getWidth(),getHeight(),!active?0xFF24212A:isHoveredOrFocused()?0xFF51435F:0xFF332A41);var f=Minecraft.getInstance().font;String s=f.plainSubstrByWidth(getMessage().getString(),getWidth()-8);g.drawCenteredString(f,s,getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?INK:MUTED);}
        };
    }
}
