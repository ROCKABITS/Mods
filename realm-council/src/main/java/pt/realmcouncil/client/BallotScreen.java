package pt.realmcouncil.client;
import net.minecraft.client.gui.GuiGraphics;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.domain.CouncilState.Candidate;
import java.util.*;
public final class BallotScreen extends CouncilBase {
    private int page;
    public BallotScreen(CouncilView v){super(v,"title.ballot",true);}
    private List<Candidate> choices(){return view.election()==null?List.of():view.election().candidates().stream().filter(c->view.election().contenders().contains(c.id())).toList();}
    private int per(){return Math.max(1,(footer()-body()-33)/27);}
    private boolean allowed(){return view.election()!=null&&(view.election().phase().equals("VOTING")||view.election().phase().equals("RUNOFF"))&&view.eligible()&&!view.voted();}
    @Override protected void init(){measure();var cs=choices();int per=per();page=Math.min(page,Math.max(0,(cs.size()-1)/per));
        if(allowed()){for(int i=0;i<per&&page*per+i<cs.size();i++){var c=cs.get(page*per+i);literalButton(x+39,body()+i*27,w-78,c.name()+" · "+Text.s("symbol."+c.symbol()),()->minecraft.setScreen(new VoteConfirmScreen(view,c.id(),c.name())));}button(x+39,footer()-25,w-78,"button.blank",()->minecraft.setScreen(new VoteConfirmScreen(view,null,Text.s("button.blank"))));}
        button(x+24,footer(),65,"button.close",this::onClose);button(x+w/2-42,footer(),38,"button.previous",()->{page=Math.max(0,page-1);rebuild();});button(x+w/2+3,footer(),38,"button.next",()->{page++;rebuild();});button(x+w-104,footer(),80,"button.book",this::back);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float t){book(g,mx,my,t);if(!allowed())wrap(g,Text.s(view.voted()?"hint.voted":!view.eligible()?"hint.not_eligible":"hint.voting_closed"),x+32,body()+12,w-64,footer()-body()-20,LIGHT);super.render(g,mx,my,t);}
}
