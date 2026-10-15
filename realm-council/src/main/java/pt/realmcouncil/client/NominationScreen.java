package pt.realmcouncil.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.domain.CouncilState.Candidate;
public final class NominationScreen extends CouncilBase {
    private EditBox slogan;private MultiLineEditBox proposals;private String sloganValue="",proposalValue="",symbol="sun";private boolean waiting;private final String electionId;
    public NominationScreen(CouncilView v){super(v,"title.nominate",true);electionId=v.election()==null?"":v.election().id().toString();var e=v.election();if(e!=null)for(var c:e.candidates())if(c.id().equals(v.viewer())){sloganValue=c.slogan();proposalValue=c.proposals();symbol=c.symbol();}}
    private void capture(){if(slogan!=null)sloganValue=slogan.getValue();if(proposals!=null)proposalValue=proposals.getValue();}
    @Override protected void init(){capture();measure();int fw=(w-62)*2/3,fx=x+w-fw-24,by=body();
        slogan=addRenderableWidget(new EditBox(font,fx,by+14,fw,20,Text.c("label.slogan")));slogan.setMaxLength(80);slogan.setValue(sloganValue);slogan.setTextColor(LIGHT);slogan.setHint(Text.c("hint.slogan"));
        String[] symbols={"sun","moon","tree","anvil","dragon"};int sw=(fw-8)/5;for(int i=0;i<symbols.length;i++){String v=symbols[i];button(fx+i*(sw+2),by+41,sw,"symbol."+v,()->{capture();symbol=v;rebuild();});}
        proposals=addRenderableWidget(new CouncilStoryBox(font,fx,by+81,fw-4,Math.max(25,footer()-by-88)));proposals.setCharacterLimit(1000);proposals.setValue(proposalValue);
        button(x+24,footer(),75,"button.back",this::back);
        var send=button(x+w-159,footer(),135,waiting?"button.saving":"button.register",()->{capture();if(view.election()==null)return;waiting=true;CouncilClient.send(1,electionId,view.election().round(),symbol,sloganValue,proposalValue);rebuild();});send.active=!waiting;
        setInitialFocus(slogan);
    }
    @Override public void accept(CouncilView v,String message){if(message.isEmpty()){view=v;receivedAt=System.currentTimeMillis();return;}capture();waiting=false;if(message.equals("saved")){minecraft.setScreen(new CouncilScreen(v));return;}super.accept(v,message);}
    @Override public void render(GuiGraphics g,int mx,int my,float t){book(g,mx,my,t);int fw=(w-62)*2/3,fx=x+w-fw-24,by=body();portrait(g,x+24,by,fx-x-38,footer()-by-8,mx,my);line(g,Text.s("label.slogan"),fx,by,fw,LIGHT);line(g,Text.s("label.proposals"),fx,by+66,fw,LIGHT);symbol(g,symbol,x+37,by+10,24);super.render(g,mx,my,t);}
}
