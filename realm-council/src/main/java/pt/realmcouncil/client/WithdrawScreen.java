package pt.realmcouncil.client;
import net.minecraft.client.gui.GuiGraphics;
import pt.realmcouncil.domain.CouncilView;
public final class WithdrawScreen extends CouncilBase {
    public WithdrawScreen(CouncilView v){super(v,"title.withdraw",true);}
    @Override protected void init(){measure();button(x+24,footer(),90,"button.back",this::back);button(x+w-160,footer(),136,"button.withdraw",()->CouncilClient.send(2,view.election().id().toString(),0,"","",""));}
    @Override public void accept(CouncilView v,String message){if(message.equals("saved")){minecraft.setScreen(new CouncilScreen(v));return;}super.accept(v,message);}
    @Override public void render(GuiGraphics g,int mx,int my,float t){book(g,mx,my,t);wrap(g,Text.s("hint.withdraw"),x+32,body()+15,w-64,footer()-body()-20,LIGHT);super.render(g,mx,my,t);}
}
