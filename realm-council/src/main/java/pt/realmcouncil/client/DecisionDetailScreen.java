package pt.realmcouncil.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import pt.realmcouncil.domain.CouncilView;
public final class DecisionDetailScreen extends CouncilBase {
 private final String text,author;private final long time;private int scroll;
 public DecisionDetailScreen(CouncilView v,String text,String author,long time){super(v,"title.decision",false);this.text=text;this.author=author;this.time=time;}
 @Override protected void init(){measure();button(x+24,footer(),90,"button.back",this::back);button(x+w-110,footer(),38,"button.previous",()->scroll=Math.max(0,scroll-5));button(x+w-66,footer(),38,"button.next",()->scroll+=5);}
 @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,scroll-(int)Math.signum(dy)*3);return true;}
 @Override public void render(GuiGraphics g,int mx,int my,float t){book(g,mx,my,t);int px=x+26,py=body(),ww=w-52;heading(g,author,px,py,ww,INK);line(g,Text.date(time),px,py+18,ww,MUTED);var lines=font.split(Component.literal(text),ww);int visible=Math.max(1,(footer()-py-45)/font.lineHeight);scroll=Math.min(scroll,Math.max(0,lines.size()-visible));for(int i=scroll;i<Math.min(lines.size(),scroll+visible);i++)g.drawString(font,lines.get(i),px,py+38+(i-scroll)*font.lineHeight,INK,false);super.render(g,mx,my,t);}
}
