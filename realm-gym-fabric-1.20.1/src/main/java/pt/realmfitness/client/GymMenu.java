package pt.realmfitness.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Presentation tokens for the menus. Training overlays use their existing theme. */
final class GymMenu {
    static final int STEEL=0xFF1B222A, SHEET=0xFF29343E, TEXT=0xFFF1F3F0, LABEL=0xFFB7C1C9,
        ACTION=0xFFCBB6EA, EDGE=0xFF8396A2, RULE=0xFF404D57;
    private static final ResourceLocation MARK=ResourceLocation.tryParse("realmfitness:textures/gui/realm_gym_mark.png");
    private static final ResourceLocation DISC=ResourceLocation.tryParse("realmfitness:textures/gui/anilha.png");
    private static final ResourceLocation PLATE_FONT=ResourceLocation.tryParse("realmfitness:plate");
    enum Kind { PRIMARY, SECONDARY, TAB, SELECTED, ROW, ROW_SELECTED }

    private GymMenu() {}

    static void panel(GuiGraphics g,int x,int y,int width,int height,int color){
        if(width>0&&height>0)g.fill(x,y,x+width,y+height,color);
    }

    static void header(GuiGraphics g,int x,int y,int width,int height){
        panel(g,x,y,width,height,STEEL);
        art(g,MARK,x+10,y+7,14,16);
        FitnessTheme.label(g,FitnessTheme.text("title"),x+29,y+10,width-110,TEXT);
        panel(g,x+12,y+48,width-24,1,RULE);
    }

    static Button button(int x,int y,int w,int h,Component message,Kind kind,Runnable onPress){
        return new Button(x,y,w,h,message,b->onPress.run(),supplier->supplier.get()){
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
                int x=getX(),y=getY(),w=getWidth(),h=getHeight();
                boolean selected=kind==Kind.SELECTED||kind==Kind.ROW_SELECTED;
                boolean primary=kind==Kind.PRIMARY&&active;
                if(primary)panel(g,x,y,w,h,isHovered()?0xFFDECDF8:ACTION);
                else if(selected||active&&isHovered())panel(g,x,y,w,h,SHEET);
                if(kind==Kind.SECONDARY||isFocused())g.renderOutline(x,y,w,h,isFocused()?TEXT:EDGE);
                if(kind==Kind.ROW_SELECTED)panel(g,x,y,2,h,ACTION);
                else if(selected)panel(g,x,y+h-2,w,2,ACTION);
                var font=Minecraft.getInstance().font;
                String text=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,w-8));
                int start=kind==Kind.ROW||kind==Kind.ROW_SELECTED?x+4:x+(w-font.width(text))/2;
                g.drawString(font,text,start,y+(h-8)/2,!active?EDGE:primary?STEEL:selected?ACTION:TEXT,false);
            }
        };
    }

    private static void art(GuiGraphics g,ResourceLocation texture,int x,int y,int size,int source){
        g.blit(texture,x,y,size,size,0,0,source,source,source,source);
    }

    static void plate(GuiGraphics g,int x,int y,int size,String value,String unit){
        art(g,DISC,x,y,size,176);
        var font=Minecraft.getInstance().font;
        var digits=Component.literal(value).withStyle(Style.EMPTY.withFont(PLATE_FONT));
        float scale=Math.min(size/90f,size*.62f/Math.max(1,font.width(digits)));
        g.pose().pushPose();
        g.pose().translate(x+size/2f,y+size*.53f,0);
        g.pose().scale(scale,scale,1);
        g.drawString(font,digits,-font.width(digits)/2,-23,TEXT,false);
        g.pose().popPose();
        g.drawString(font,unit,x+(size-font.width(unit))/2,y+size*3/4,LABEL,false);
    }
}
