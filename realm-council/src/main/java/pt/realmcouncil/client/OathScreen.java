package pt.realmcouncil.client;
import net.minecraft.client.gui.GuiGraphics;
import pt.realmcouncil.domain.CouncilView;
public final class OathScreen extends CouncilBase {
    private boolean waiting;
    public OathScreen(CouncilView v){super(v,"title.oath",true);}
    @Override protected void init(){measure();button(x+24,footer(),80,"button.back",this::back);var b=button(x+w-160,footer(),136,waiting?"button.saving":"button.swear",()->{waiting=true;CouncilClient.send(4,view.mandate().electionId.toString(),0,"","","");rebuild();});b.active=!waiting&&view.mandate()!=null&&view.mandate().active(now())&&view.mandate().claimedAt==0;}
    @Override public void accept(CouncilView v,String message){if(message.isEmpty()){view=v;receivedAt=System.currentTimeMillis();return;}waiting=false;if(message.equals("saved")){minecraft.setScreen(new CouncilScreen(v));return;}super.accept(v,message);}
    @Override public void render(GuiGraphics g,int mx,int my,float t){book(g,mx,my,t);var m=view.mandate();int pw=(w-62)/3;portrait(g,x+24,body(),pw,footer()-body()-8,mx,my);int fx=x+pw+39,fw=w-pw-63;if(m!=null){heading(g,m.title,fx,body()+5,fw,GOLD);wrap(g,Text.s("hint.oath",m.name),fx,body()+30,fw,footer()-body()-35,LIGHT);}super.render(g,mx,my,t);}
}
