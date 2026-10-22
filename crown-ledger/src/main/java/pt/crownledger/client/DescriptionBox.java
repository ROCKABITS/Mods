package pt.crownledger.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Scrollable, noneditable proposal text. The whole description remains accessible at small GUI sizes. */
public final class DescriptionBox extends AbstractScrollWidget {
    private final String value;
    private final List<FormattedCharSequence> lines;
    public DescriptionBox(int x,int y,int width,int height,String value){
        super(x,y,width,height,Component.literal(value));this.value=value;
        lines=Minecraft.getInstance().font.split(Component.literal(value),Math.max(1,width-16));
    }
    public String getValue(){return value;}
    @Override protected int getInnerHeight(){return lines.size()*11;}
    @Override protected double scrollRate(){return 11;}
    @Override protected void renderBackground(GuiGraphics g){
        TreasuryArt.frame(g,getX(),getY(),getWidth(),getHeight(),0xFFF7EBCD,isFocused()?0xFF55704F:0xFFC6AB74);
    }
    @Override protected void renderContents(GuiGraphics g,int mx,int my,float partial){
        var font=Minecraft.getInstance().font;
        for(int i=0;i<lines.size();i++){
            int py=getY()+innerPadding()+i*11;
            if(py+9>=getY()+scrollAmount()&&py<=getY()+getHeight()+scrollAmount())
                g.drawString(font,lines.get(i),getX()+innerPadding(),py,TreasuryArt.INK,false);
        }
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){output.add(NarratedElementType.TITLE,getMessage());}
}
