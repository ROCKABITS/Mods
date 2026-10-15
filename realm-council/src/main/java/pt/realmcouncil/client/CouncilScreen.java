package pt.realmcouncil.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.domain.CouncilState.*;
import java.util.*;
public final class CouncilScreen extends CouncilBase {
    private int tab,page,resultRound=-1;
    public CouncilScreen(CouncilView v){super(v,"title.book",false);}
    @Override protected void init(){measure();String[] tabs=view.administration()==null?new String[]{"tab.election","tab.candidates","tab.results","tab.government","tab.history"}:new String[]{"tab.election","tab.candidates","tab.results","tab.government","tab.history","tab.admin"};int tw=(w-46)/tabs.length;for(int i=0;i<tabs.length;i++){final int t=i;button(x+23+i*tw,y+39,tw-3,tabs[i],()->{if(t==5)minecraft.setScreen(new AdminScreen(view));else{tab=t;page=0;rebuild();}});}
        button(x+23,footer(),65,"button.close",this::onClose);
        if(tab==0||tab==1){var e=view.election();boolean own=e!=null&&e.candidates().stream().anyMatch(c->c.id().equals(view.viewer()));
            boolean nomination=e!=null&&(e.phase().equals("NOMINATIONS")||e.phase().equals("CAMPAIGN")&&own);
            if(nomination){var b=button(x+w-158,footer(),135,own?"button.edit":"button.nominate",()->minecraft.setScreen(new NominationScreen(view)));b.active=view.eligible();if(!b.active)b.setTooltip(Tooltip.create(Text.c("hint.eligible",view.minimumPlayMinutes())));}
            if(own&&e!=null&&(e.phase().equals("NOMINATIONS")||e.phase().equals("CAMPAIGN")))button(x+94,footer(),76,"button.withdraw",()->minecraft.setScreen(new WithdrawScreen(view)));
        }
        if(tab==1){var cs=view.election()==null?List.<Candidate>of():view.election().candidates();int per=perPage();page=Math.min(page,Math.max(0,(cs.size()-1)/per));for(int i=0;i<per&&page*per+i<cs.size();i++){var c=cs.get(page*per+i);literalButton(x+w-113,body()+i*46+8,87,Text.s("button.read"),()->minecraft.setScreen(new CandidateScreen(view,c.id())));}}
        if(tab==2&&view.election()!=null&&!view.election().results().isEmpty())button(x+w-158,footer(),135,"button.round",()->{var results=view.election().results();int index=resultRound<0?results.size()-1:resultRound;resultRound=(index+1)%results.size();page=0;rebuild();});
        if(tab==1||tab==2||tab==3||tab==4){button(x+w/2-45,footer(),38,"button.previous",()->{page=Math.max(0,page-1);rebuild();});button(x+w/2,footer(),38,"button.next",()->{page++;rebuild();});}
        var m=view.mandate();
        if(tab==3&&m!=null&&!m.decisions.isEmpty()){int hh=footer()-(body()+8)-5,per=Math.max(1,(hh-51)/43);page=Math.min(page,Math.max(0,(m.decisions.size()-1)/per));for(int i=0;i<per&&page*per+i<m.decisions.size();i++){var d=m.decisions.reversed().get(page*per+i);button(x+w-105,body()+8+52+i*43+11,79,"button.read_decision",()->minecraft.setScreen(new DecisionDetailScreen(view,d.text(),d.author(),d.time())));}}
        if(tab==3&&m!=null&&m.active(now())&&m.claimedAt>0&&m.playerId.equals(view.viewer()))button(x+w-158,footer(),135,"button.decision",()->minecraft.setScreen(new DecisionScreen(view)));
    }
    private int perPage(){return Math.max(1,(footer()-body()-8)/46);}
    @Override public void render(GuiGraphics g,int mx,int my,float t){book(g,mx,my,t);int px=x+25,py=body()+8,ww=w-50,hh=footer()-py-5;
        switch(tab){case 0->overview(g,px,py,ww,hh,mx,my);case 1->candidates(g,px,py,ww);case 2->results(g,px,py,ww,hh);case 3->government(g,px,py,ww,hh);case 4->history(g,px,py,ww,hh);}
        super.render(g,mx,my,t);
    }
    private void overview(GuiGraphics g,int px,int py,int ww,int hh,int mx,int my){int portraitW=Math.max(64,ww/4);portrait(g,px,py,portraitW,hh,mx,my);int rx=px+portraitW+14,rw=ww-portraitW-14;var e=view.election();
        heading(g,e==null?Text.s("phase.none"):e.title(),rx,py,rw,INK);line(g,Text.phase(view),rx,py+19,rw,TEAL);
        if(e==null){wrap(g,Text.s("hint.no_election"),rx,py+40,rw,hh-40,INK);return;}
        line(g,Text.s("label.round",e.round()),rx,py+34,rw,MUTED);
        if(!e.phase().equals("FINISHED")&&!e.phase().equals("CANCELLED")){line(g,Text.s("label.remaining",Text.remaining(e.deadline(),now())),rx,py+49,rw,INK);line(g,Text.s("label.deadline",Text.date(e.deadline())),rx,py+63,rw,MUTED);}
        line(g,Text.s("label.turnout",view.turnout(),view.voters()),rx,py+79,rw,INK);
        line(g,view.voted()?Text.s("hint.voted"):view.eligible()?Text.s("hint.ready"):Text.s("hint.not_eligible"),rx,py+94,rw,TEAL);
        if(hh>125)wrap(g,Text.s("hint.urn"),rx,py+113,rw,hh-113,MUTED);
    }
    private void candidates(GuiGraphics g,int px,int py,int ww){var list=view.election()==null?List.<Candidate>of():view.election().candidates();if(list.isEmpty()){wrap(g,Text.s("hint.no_candidates"),px,py,ww,50,INK);return;}
        int per=perPage();for(int i=0;i<per&&page*per+i<list.size();i++){var c=list.get(page*per+i);int cy=py+i*46;frame(g,px,cy,ww,41,0x22785B32,0x55A08352);symbol(g,c.symbol(),px+7,cy+8,25);heading(g,c.name(),px+40,cy+6,ww-142,INK);line(g,c.slogan(),px+40,cy+22,ww-142,MUTED);}}
    private void results(GuiGraphics g,int px,int py,int ww,int hh){var e=view.election();if(e==null||e.results().isEmpty()){wrap(g,e!=null&&!e.outcome().isEmpty()?Text.s("outcome."+e.outcome()):Text.s("hint.hidden_results"),px,py,ww,hh,INK);return;}
        var r=e.results().get(resultRound<0?e.results().size()-1:Math.min(resultRound,e.results().size()-1));heading(g,Text.s("label.results_round",r.round()),px,py,ww,INK);int cy=py+23;
        int per=Math.max(1,(hh-63)/15);var entries=new java.util.ArrayList<>(r.counts().entrySet());page=Math.min(page,Math.max(0,(entries.size()-1)/per));
        for(int i=page*per;i<Math.min(entries.size(),page*per+per);i++){var entry=entries.get(i);var c=e.candidates().stream().filter(v->v.id().equals(entry.getKey())).findFirst().orElse(null);if(c==null)continue;line(g,c.name(),px,cy,ww-55,INK);line(g,String.valueOf(entry.getValue()),px+ww-45,cy,45,TEAL);cy+=15;}
        line(g,Text.s("label.blank",r.blank()),px,py+hh-28,ww,MUTED);if(!e.outcome().isEmpty())line(g,Text.s("outcome."+e.outcome()),px,py+hh-12,ww,TEAL);
    }
    private void government(GuiGraphics g,int px,int py,int ww,int hh){var m=view.mandate();if(m==null){wrap(g,Text.s("hint.no_governor"),px,py,ww,hh,INK);return;}
        heading(g,m.name+" · "+m.title,px,py,ww,INK);line(g,Text.s(m.active(now())?m.claimedAt>0?"hint.sworn":"hint.await_oath":"hint.expired"),px,py+18,ww,TEAL);line(g,Text.s("label.term_end",Text.date(m.endsAt)),px,py+32,ww,MUTED);
        if(m.decisions.isEmpty()){wrap(g,Text.s("hint.no_decisions"),px,py+53,ww,hh-53,INK);return;}
        int per=Math.max(1,(hh-51)/43);page=Math.min(page,Math.max(0,(m.decisions.size()-1)/per));for(int i=0;i<per&&page*per+i<m.decisions.size();i++){var d=m.decisions.reversed().get(page*per+i);int cy=py+52+i*43;line(g,Text.date(d.time()),px,cy,ww,MUTED);var text=d.text();wrap(g,text,px,cy+12,ww-92,27,INK);}
    }
    private void history(GuiGraphics g,int px,int py,int ww,int hh){if(view.history().isEmpty()){wrap(g,Text.s("hint.no_history"),px,py,ww,hh,INK);return;}
        int per=Math.max(1,hh/39);page=Math.min(page,Math.max(0,(view.history().size()-1)/per));for(int i=0;i<per&&page*per+i<view.history().size();i++){var h=view.history().get(page*per+i);int cy=py+i*39;heading(g,h.title(),px,cy,ww,INK);line(g,h.winner().isEmpty()?Text.s("outcome."+h.outcome()):h.winner(),px,cy+14,ww-90,TEAL);line(g,Text.date(h.time()),px+ww-85,cy+14,85,MUTED);}}
}
