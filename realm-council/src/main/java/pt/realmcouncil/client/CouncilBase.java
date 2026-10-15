package pt.realmcouncil.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import pt.realmcouncil.domain.CouncilView;
import java.util.*;
public abstract class CouncilBase extends Screen {
    static final int INK=0xFF382D25,MUTED=0xFF807059,GOLD=0xFFE6C07C,LIGHT=0xFFF4E6CB,TEAL=0xFF245454;
    static final ResourceLocation BOOK=ResourceLocation.fromNamespaceAndPath("realmcouncil","textures/gui/council_book.png"),SCROLL=ResourceLocation.fromNamespaceAndPath("realmcouncil","textures/gui/council_scroll.png"),FONT=ResourceLocation.fromNamespaceAndPath("realmcouncil","journal");
    protected CouncilView view;protected int x,y,w,h;protected String notice="";protected long receivedAt=System.currentTimeMillis(),noticeAt;
    protected boolean dark;
    protected CouncilBase(CouncilView view,String title,boolean dark){super(Text.c(title));this.view=view;this.dark=dark;}
    protected void measure(){w=Math.min(620,width-16);h=Math.min(350,height-16);x=(width-w)/2;y=(height-h)/2;}
    protected int body(){return y+65;}protected int footer(){return y+h-33;}
    public void accept(CouncilView v,String message){view=v;receivedAt=System.currentTimeMillis();if(!message.isEmpty()){notice=message.startsWith("error.")?Component.translatable("error.realmcouncil."+message.substring(6)).getString():Component.translatable("message.realmcouncil."+message).getString();noticeAt=receivedAt;}rebuild();}
    protected void rebuild(){clearWidgets();init();}
    protected long now(){return view.serverTime()+System.currentTimeMillis()-receivedAt;}
    protected void book(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xB9081114);
        // Keep the header and footer at a fixed height while the writing area stretches.
        // Scaling the whole image made its footer rule cross buttons in short windows.
        var texture=dark?SCROLL:BOOK;
        g.blit(texture,x,y,w,65,0F,0F,1024,112,1024,580);
        g.blit(texture,x,y+65,w,h-107,0F,112F,1024,388,1024,580);
        g.blit(texture,x,y+h-42,w,42,0F,500F,1024,80,1024,580);
        heading(g,getTitle().getString(),x+34,y+20,w-68,dark?GOLD:INK);
        if(!notice.isEmpty()&&System.currentTimeMillis()-noticeAt<10000)line(g,notice,x+23,y+h-14,w-46,dark?LIGHT:TEAL);
    }
    protected void heading(GuiGraphics g,String value,int px,int py,int width,int color){var c=Component.literal(value).withStyle(s->s.withFont(FONT));g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(c,Math.max(1,width))),px,py,color,false);}
    protected void line(GuiGraphics g,String value,int px,int py,int width,int color){g.drawString(font,font.plainSubstrByWidth(value,Math.max(1,width)),px,py,color,false);}
    protected void wrap(GuiGraphics g,String value,int px,int py,int width,int height,int color){var lines=font.split(Component.literal(value),Math.max(1,width));for(int i=0;i<Math.min(lines.size(),Math.max(0,height/font.lineHeight));i++)g.drawString(font,lines.get(i),px,py+i*font.lineHeight,color,false);}
    static void frame(GuiGraphics g,int px,int py,int width,int height,int color,int edge){g.fill(px,py,px+width,py+height,color);g.fill(px,py,px+width,py+1,edge);g.fill(px,py+height-1,px+width,py+height,edge);g.fill(px,py,px+1,py+height,edge);g.fill(px+width-1,py,px+width,py+height,edge);}
    protected CouncilButton button(int px,int py,int width,String key,Runnable action){return addRenderableWidget(new CouncilButton(px,py,width,20,Text.s(key),dark,action));}
    protected CouncilButton literalButton(int px,int py,int width,String label,Runnable action){return addRenderableWidget(new CouncilButton(px,py,width,20,label,dark,action));}
    protected void portrait(GuiGraphics g,int px,int py,int width,int height,int mx,int my){
        frame(g,px,py,width,height,dark?0x881A333D:0x22785836,dark?0x886C9D98:0x887D633E);
        if(minecraft.player!=null&&height>=60)InventoryScreen.renderEntityInInventoryFollowsMouse(g,px+3,py+3,px+width-3,py+height-16,Math.max(12,Math.min(width/2-6,(height-22)*9/20)),0.05F,mx,my,minecraft.player);
        line(g,view.identity().name(),px+5,py+height-12,width-10,dark?LIGHT:INK);
    }
    protected void symbol(GuiGraphics g,String symbol,int px,int py,int size){g.blit(ResourceLocation.fromNamespaceAndPath("realmcouncil","textures/gui/symbol_"+symbol+".png"),px,py,0,0,size,size,size,size);}
    protected void back(){minecraft.setScreen(new CouncilScreen(view));}
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick) {}
    @Override public boolean isPauseScreen(){return false;}
    static final class CouncilButton extends AbstractButton {
        final Runnable action;final boolean dark;
        CouncilButton(int x,int y,int w,int h,String title,boolean dark,Runnable action){super(x,y,w,h,Component.literal(title));this.action=action;this.dark=dark;}
        @Override public void onPress(){action.run();}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float t){int edge=isHoveredOrFocused()?GOLD:dark?0xFF779890:0xFFAA8A56;frame(g,getX(),getY(),width,height,dark?0xC91C333C:0x44795830,edge);var font=net.minecraft.client.Minecraft.getInstance().font;float scale=Math.min(1F,(width-10F)/Math.max(1,font.width(getMessage())));g.pose().pushPose();g.pose().translate(getX()+(width-font.width(getMessage())*scale)/2,getY()+(height-9*scale)/2,0);g.pose().scale(scale,scale,1);g.drawString(font,getMessage(),0,0,active?(dark?LIGHT:INK):MUTED,false);g.pose().popPose();}
        @Override protected void updateWidgetNarration(NarrationElementOutput o){defaultButtonNarrationText(o);}
    }
}
