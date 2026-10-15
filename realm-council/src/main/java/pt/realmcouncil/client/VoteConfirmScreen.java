package pt.realmcouncil.client;
import net.minecraft.client.gui.GuiGraphics;
import java.util.UUID;
import pt.realmcouncil.domain.CouncilView;
public final class VoteConfirmScreen extends CouncilBase {
    private final UUID choice;private final String name;private final String election;private final int round;private boolean waiting;
    public VoteConfirmScreen(CouncilView v,UUID choice,String name){super(v,"title.seal",true);this.choice=choice;this.name=name;this.election=v.election().id().toString();this.round=v.election().round();}
    @Override protected void init(){measure();button(x+24,footer(),95,"button.back",()->minecraft.setScreen(new BallotScreen(view)));var b=button(x+w-160,footer(),136,waiting?"button.saving":"button.seal",()->{waiting=true;CouncilClient.send(3,election,round,choice==null?"":choice.toString(),"","");rebuild();});b.active=!waiting;}
    @Override public void accept(CouncilView v,String message){if(message.isEmpty()){view=v;receivedAt=System.currentTimeMillis();return;}waiting=false;if(message.equals("saved")){minecraft.setScreen(new CouncilScreen(v));return;}super.accept(v,message);}
    @Override public void render(GuiGraphics g,int mx,int my,float t){book(g,mx,my,t);heading(g,name,x+33,body()+7,w-66,GOLD);wrap(g,Text.s("hint.confirm_vote"),x+33,body()+31,w-66,55,LIGHT);
        int cy=Math.min(footer()-30,body()+110),cx=x+w/2;for(int dy=-16;dy<=16;dy++){int dx=(int)Math.sqrt(256-dy*dy);g.fill(cx-dx,cy+dy,cx+dx,cy+dy+1,0xFFA04446);}symbol(g,"sun",cx-10,cy-10,20);super.render(g,mx,my,t);}
}
