package pt.realmcouncil.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.network.CouncilPackets;
import static pt.realmcouncil.domain.CouncilAdministration.*;

public final class AdminScreen extends CouncilBase {
    private int tab,candidatePage;
    private EditBox search;
    private String searchValue="";
    public AdminScreen(CouncilView view){super(view,"title.admin",false);if(view.administration()!=null)searchValue=view.administration().query();}
    private void capture(){if(search!=null)searchValue=search.getValue();}
    private boolean live(){var e=view.election();return e!=null&&!e.phase().equals("FINISHED")&&!e.phase().equals("CANCELLED");}
    private boolean nominations(){return live()&&view.election().phase().equals("NOMINATIONS");}
    private CouncilPackets.Action request(int action,String target){var e=view.election();return new CouncilPackets.Action(action,e==null?"":e.id().toString(),e==null?0:e.round(),target,"",e==null?"":e.phase());}
    private void confirm(int action,String title,String hint,String target,String name){
        var packet=action==END_TERM?new CouncilPackets.Action(action,view.mandate().electionId.toString(),0,"","",""):request(action,target);
        var details=name.isEmpty()?List.<String>of():List.of(name);
        minecraft.setScreen(new AdminConfirmScreen(view,this,packet,title,Text.s(hint),details));
    }
    @Override protected void init(){
        capture();search=null;measure();if(view.administration()==null)return;
        String[] tabs={"tab.admin_overview","tab.admin_voters","tab.admin_candidates"};int tw=(w-46)/3;
        for(int i=0;i<3;i++){int index=i;button(x+23+i*tw,y+39,tw-3,tabs[i],()->{capture();tab=index;rebuild();});}
        button(x+24,footer(),75,"button.back",this::back);
        int bx=x+25,by=body(),ww=w-50;
        if(tab==0){int right=bx+ww/2+9,bw=ww/2-9;
            var create=button(right,by+2,bw,"button.admin_create",()->minecraft.setScreen(new AdminCreateScreen(view)));
            create.active=view.administration().canCreate();if(!create.active)create.setTooltip(Tooltip.create(Text.c("hint.admin_create_locked")));
            String next=view.election()==null?"button.admin_advance":switch(view.election().phase()){case "NOMINATIONS"->"button.start_campaign";case "CAMPAIGN"->"button.open_voting";default->"button.close_round";};
            var advance=button(right,by+27,bw,next,()->confirm(ADVANCE,"title.admin_advance","hint.admin_advance","",view.election().title()+" · "+Text.phase(view)));advance.active=live();
            var cancel=button(right,by+52,bw,"button.admin_cancel",()->confirm(CANCEL,"title.admin_cancel","hint.admin_cancel","",view.election().title()));cancel.active=live();
            var term=button(right,by+77,bw,"button.admin_endterm",()->confirm(END_TERM,"title.admin_endterm","hint.admin_endterm","",view.mandate().name));term.active=view.mandate()!=null&&view.mandate().active(now());
            button(x+w-174,footer(),150,"button.admin_blocks",()->CouncilClient.send(BLOCKS,"",0,"","",""));
        }else if(tab==1){
            search=addRenderableWidget(new EditBox(font,bx,by,ww-91,20,Text.c("label.admin_search")));search.setMaxLength(32);search.setValue(searchValue);search.setHint(Text.c("hint.admin_search"));
            button(bx+ww-85,by,85,"button.admin_search",()->{capture();CouncilClient.send(LIST,"",0,"",searchValue,"");});
            var admin=view.administration();
            for(int i=0;i<admin.players().size();i++){var citizen=admin.players().get(i);int cy=by+28+i*24;
                var enrol=button(bx+ww-98,cy+1,98,citizen.eligible()?"button.admin_exclude":"button.admin_enrol",()->{
                    if(citizen.eligible())confirm(EXCLUDE,"title.admin_exclude","hint.admin_exclude",citizen.id().toString(),citizen.name());
                    else{var p=request(ENROL,citizen.id().toString());CouncilClient.send(p.action(),p.election(),p.round(),p.choice(),p.text(),p.extra());}
                });enrol.active=nominations();if(!enrol.active)enrol.setTooltip(Tooltip.create(Text.c("hint.admin_roll_locked")));
            }
            var previous=button(x+w/2-73,footer(),32,"button.previous",()->{capture();CouncilClient.send(LIST,"",admin.page()-1,"",searchValue,"");});previous.active=admin.page()>0;
            var next=button(x+w/2+42,footer(),32,"button.next",()->{capture();CouncilClient.send(LIST,"",admin.page()+1,"",searchValue,"");});next.active=admin.page()+1<admin.pages();
        }else{
            var candidates=view.election()==null?List.<CouncilState.Candidate>of():view.election().candidates();
            candidatePage=Math.min(candidatePage,Math.max(0,(candidates.size()-1)/4));
            for(int i=0;i<4&&candidatePage*4+i<candidates.size();i++){var c=candidates.get(candidatePage*4+i);int cy=by+3+i*29;
                var remove=button(bx+ww-111,cy+3,111,"button.admin_remove",()->confirm(REMOVE_CANDIDATE,"title.admin_remove","hint.admin_remove",c.id().toString(),c.name()+" ("+c.minecraftName()+")"));
                remove.active=live()&&(nominations()||view.election().phase().equals("CAMPAIGN"));
            }
            int pages=Math.max(1,(candidates.size()+3)/4);
            var previous=button(x+w/2-73,footer(),32,"button.previous",()->{candidatePage--;rebuild();});previous.active=candidatePage>0;
            var next=button(x+w/2+42,footer(),32,"button.next",()->{candidatePage++;rebuild();});next.active=candidatePage+1<pages;
        }
    }
    @Override public void accept(CouncilView v,String message){
        capture();if(v.administration()==null){minecraft.setScreen(new CouncilScreen(v));return;}
        if(message.equals("roster")){searchValue=v.administration().query();search=null;view=v;receivedAt=System.currentTimeMillis();rebuild();return;}
        if(message.isEmpty()&&search!=null&&search.isFocused()){view=v;receivedAt=System.currentTimeMillis();return;}
        super.accept(v,message);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float t){
        book(g,mx,my,t);if(view.administration()==null){super.render(g,mx,my,t);return;}
        int bx=x+25,by=body(),ww=w-50;
        if(tab==0){int left=ww/2-8;var e=view.election();
            heading(g,e==null?Text.s("phase.none"):e.title(),bx,by+4,left,INK);
            line(g,Text.phase(view),bx,by+21,left,TEAL);
            line(g,Text.s("label.turnout",view.turnout(),view.voters()),bx,by+36,left,MUTED);
            if(live())line(g,Text.s("label.remaining",Text.remaining(e.deadline(),now())),bx,by+51,left,INK);
            var m=view.mandate();heading(g,m==null?Text.s("hint.no_governor"):m.name,bx,by+76,left,INK);
            if(m!=null)line(g,Text.s(m.active(now())?m.claimedAt>0?"hint.sworn":"hint.await_oath":"hint.expired"),bx,by+92,left,TEAL);
            if(m!=null)line(g,Text.s("label.term_end",Text.date(m.endsAt)),bx,by+107,left,MUTED);
        }else if(tab==1){var admin=view.administration();
            if(admin.players().isEmpty())wrap(g,Text.s("hint.admin_no_players"),bx,by+31,ww,footer()-by-34,INK);
            for(int i=0;i<admin.players().size();i++){var c=admin.players().get(i);int cy=by+28+i*24;frame(g,bx,cy,ww-103,22,0x22785B32,0x55A08352);
                line(g,c.name(),bx+5,cy+2,ww-114,INK);
                line(g,Text.s(c.online()?"label.admin_online":"label.admin_offline")+" · "+Text.s("label.admin_play",c.playMinutes()),bx+5,cy+12,ww-114,MUTED);
            }
            line(g,Text.s("label.admin_page",admin.page()+1,admin.pages()),x+w/2-36,footer()+6,74,MUTED);
        }else{var candidates=view.election()==null?List.<CouncilState.Candidate>of():view.election().candidates();
            if(candidates.isEmpty())wrap(g,Text.s("hint.no_candidates"),bx,by+15,ww,footer()-by-20,INK);
            for(int i=0;i<4&&candidatePage*4+i<candidates.size();i++){var c=candidates.get(candidatePage*4+i);int cy=by+3+i*29;frame(g,bx,cy,ww-117,26,0x22785B32,0x55A08352);symbol(g,c.symbol(),bx+3,cy+3,20);line(g,c.name(),bx+28,cy+3,ww-150,INK);line(g,c.minecraftName(),bx+28,cy+15,ww-150,MUTED);}
            line(g,Text.s("label.admin_page",candidatePage+1,Math.max(1,(candidates.size()+3)/4)),x+w/2-36,footer()+6,74,MUTED);
        }
        super.render(g,mx,my,t);
    }
}
