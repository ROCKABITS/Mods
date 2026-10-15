package pt.realmcouncil.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
public final class CouncilStoryBox extends MultiLineEditBox {
    public CouncilStoryBox(Font f,int x,int y,int w,int h){super(f,x,y,w,h,Text.c("hint.proposals"),Text.c("label.proposals"));}
    @Override protected void renderDecorations(GuiGraphics g){
        if(scrollbarVisible()){int thumb=Math.max(8,(height-6)*height/Math.max(1,getInnerHeight()));int offset=(int)((height-6-thumb)*scrollAmount()/Math.max(1,getMaxScrollAmount()));g.fill(getX()+width+1,getY()+3,getX()+width+3,getY()+height-3,0x665C8D80);g.fill(getX()+width+1,getY()+3+offset,getX()+width+3,getY()+3+offset+thumb,CouncilBase.GOLD);}
    }
    @Override protected void renderBackground(GuiGraphics g){CouncilBase.frame(g,getX(),getY(),width,height,0xE3152830,isFocused()?CouncilBase.GOLD:0xFF55796E);}
}
