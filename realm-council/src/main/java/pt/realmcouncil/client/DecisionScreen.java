package pt.realmcouncil.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import pt.realmcouncil.domain.CouncilView;
public final class DecisionScreen extends CouncilBase {
    private MultiLineEditBox text;private String value="";private boolean waiting;private final String mandateId;
    public DecisionScreen(CouncilView v){super(v,"title.decision",true);mandateId=v.mandate()==null?"":v.mandate().electionId.toString();}
    @Override protected void init(){if(text!=null)value=text.getValue();measure();text=addRenderableWidget(new CouncilStoryBox(font,x+28,body()+20,w-60,Math.max(20,footer()-body()-30)));text.setCharacterLimit(500);text.setValue(value);button(x+24,footer(),80,"button.back",this::back);var b=button(x+w-160,footer(),136,waiting?"button.saving":"button.publish",()->{value=text.getValue();waiting=true;CouncilClient.send(5,mandateId,0,"","",value);rebuild();});b.active=!waiting;}
    @Override public void accept(CouncilView v,String message){if(message.isEmpty()){view=v;receivedAt=System.currentTimeMillis();return;}waiting=false;if(message.equals("saved")){minecraft.setScreen(new CouncilScreen(v));return;}super.accept(v,message);}
    @Override public void render(GuiGraphics g,int mx,int my,float t){book(g,mx,my,t);line(g,Text.s("hint.public_decision"),x+28,body(),w-56,LIGHT);super.render(g,mx,my,t);}
}
