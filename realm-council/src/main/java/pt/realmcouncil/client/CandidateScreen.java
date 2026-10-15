package pt.realmcouncil.client;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.domain.CouncilState.Candidate;
public final class CandidateScreen extends CouncilBase {
    private final UUID id;private int scroll;
    public CandidateScreen(CouncilView v,UUID id){super(v,"title.candidate",false);this.id=id;}
    private Candidate candidate(){return view.election()==null?null:view.election().candidates().stream().filter(c->c.id().equals(id)).findFirst().orElse(null);}
    @Override protected void init(){measure();button(x+24,footer(),90,"button.back",this::back);button(x+w-110,footer(),38,"button.previous",()->scroll=Math.max(0,scroll-5));button(x+w-66,footer(),38,"button.next",()->scroll+=5);}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,scroll-(int)Math.signum(dy)*3);return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float t){book(g,mx,my,t);var c=candidate();if(c==null){super.render(g,mx,my,t);return;}int px=x+25,py=body(),ww=w-50;
        var profile=new GameProfile(c.id(),c.minecraftName());if(c.skinValue()!=null&&!c.skinValue().isEmpty())profile.getProperties().put("textures",new com.mojang.authlib.properties.Property("textures",c.skinValue(),c.skinSignature()));var info=minecraft.getConnection()==null?null:minecraft.getConnection().getPlayerInfo(c.id());var skin=info==null?minecraft.getSkinManager().getInsecureSkin(profile):info.getSkin();PlayerFaceRenderer.draw(g,skin.texture(),px,py,40);
        symbol(g,c.symbol(),px+ww-28,py+4,24);heading(g,c.name(),px+49,py+3,ww-82,INK);line(g,c.slogan(),px+49,py+19,ww-82,MUTED);
        if(c.roleplay())line(g,Text.s("label.character",Text.race(c.race()),c.age()),px+49,py+33,ww-55,TEAL);
        var lines=font.split(Component.literal(c.proposals()),ww-4);int visible=Math.max(1,(footer()-py-59)/font.lineHeight);scroll=Math.min(scroll,Math.max(0,lines.size()-visible));for(int i=scroll;i<Math.min(lines.size(),scroll+visible);i++)g.drawString(font,lines.get(i),px,py+54+(i-scroll)*font.lineHeight,INK,false);super.render(g,mx,my,t);
    }
}
