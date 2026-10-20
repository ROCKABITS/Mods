package pt.contracts.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class DailyVisualScreen extends Screen {
    private final Screen parent;
    private int x,y,w,h;
    private boolean failed;
    public DailyVisualScreen(Screen parent){super(DailyTheme.tr("visuals"));this.parent=parent;}
    private DailyVisualPreferences p(){return DailyCinematic.preferences();}
    private void changed(){failed=!p().save();rebuildWidgets();}
    @Override protected void init(){
        w=Math.min(440,width-16);h=Math.min(270,height-16);x=(width-w)/2;y=(height-h)/2;int row=Math.min(32,(h-79)/5);
        option(0,row,"animations",p().enabled?"on":"off",()->{p().enabled=!p().enabled;changed();});
        option(1,row,"particles",p().reducedParticles?"reduced":"normal",()->{p().reducedParticles=!p().reducedParticles;changed();});
        option(2,row,"simplified",p().simplified?"on":"off",()->{p().simplified=!p().simplified;changed();});
        option(3,row,"volume",Math.round(p().volume*100)+"%",()->{p().volume=p().volume>=.99f?0:Math.min(1,p().volume+.25f);changed();});
        option(4,row,"duration",p().duration+"s",()->{p().duration=p().duration==5?3:p().duration+1;changed();});
        addRenderableWidget(DailyTheme.button(x+w/2-60,y+h-29,120,20,DailyTheme.tr("back"),this::onClose));
    }
    private void option(int index,int row,String key,String value,Runnable action){addRenderableWidget(DailyTheme.button(x+w-137,y+42+index*row,122,19,key.equals("volume")||key.equals("duration")?net.minecraft.network.chat.Component.literal(value):DailyTheme.tr(value),action));}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float p){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xCB101B16);DailyTheme.panel(g,x,y,w,h);g.drawCenteredString(font,title,x+w/2,y+15,DailyTheme.GOLD);int row=Math.min(32,(h-79)/5);String[] labels={"animations","particles","simplified","volume","duration"};for(int i=0;i<labels.length;i++)g.drawString(font,DailyTheme.tr(labels[i]),x+15,y+47+i*row,DailyTheme.PALE,false);if(failed)g.drawCenteredString(font,DailyTheme.tr("preference_error"),x+w/2,y+h-42,0xFFFFAAAA);super.render(g,mx,my,partial);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){p().save();minecraft.setScreen(parent);}
}
