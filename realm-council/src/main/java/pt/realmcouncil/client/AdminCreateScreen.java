package pt.realmcouncil.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.network.CouncilPackets;
import static pt.realmcouncil.domain.CouncilAdministration.*;

public final class AdminCreateScreen extends CouncilBase {
    private final String[] values=new String[9];
    private final EditBox[] fields=new EditBox[9];
    private EditBox titleField;
    private String titleValue="";
    private int page;
    private final String electionId,phase;
    private final int round;
    private static final String[] LABELS={"label.admin_minplay","label.admin_nominations","label.admin_campaign","label.admin_voting","label.admin_term","label.admin_runoff","label.admin_minvotes","label.admin_maxcandidates","label.admin_maxrounds"};
    public AdminCreateScreen(CouncilView v){
        super(v,"title.admin_create",true);var e=v.election();electionId=e==null?"":e.id().toString();phase=e==null?"":e.phase();round=e==null?0:e.round();
        var s=v.administration().defaults();int[] defaults={s.minimumPlayMinutes(),s.nominationMinutes(),s.campaignMinutes(),s.votingMinutes(),s.termMinutes(),s.runoffMinutes(),s.minimumVotes(),s.maximumCandidates(),s.maximumRounds()};
        for(int i=0;i<9;i++)values[i]=String.valueOf(defaults[i]);
    }
    private void capture(){if(titleField!=null)titleValue=titleField.getValue();for(int i=0;i<9;i++)if(fields[i]!=null)values[i]=fields[i].getValue();}
    private void number(int index,int px,int py,int width){
        fields[index]=addRenderableWidget(new EditBox(font,px,py,width,20,Text.c(LABELS[index])));fields[index].setMaxLength(6);fields[index].setFilter(s->s.matches("[0-9]*"));fields[index].setValue(values[index]);fields[index].setTextColor(LIGHT);
    }
    @Override protected void init(){
        capture();Arrays.fill(fields,null);titleField=null;measure();int cw=(w-62)/2,fx=x+25,by=body();
        button(x+24,y+39,(w-51)/2,"tab.admin_rules",()->{page=0;rebuild();});button(x+27+(w-51)/2,y+39,(w-51)/2,"tab.admin_dates",()->{page=1;rebuild();});
        if(page==0){
            titleField=addRenderableWidget(new EditBox(font,fx,by+14,w-50,20,Text.c("label.admin_title")));titleField.setMaxLength(64);titleField.setValue(titleValue);titleField.setTextColor(LIGHT);titleField.setHint(Text.c("hint.admin_title"));
            number(0,fx,by+50,cw);number(6,fx+cw+12,by+50,cw);number(7,fx,by+86,cw);number(8,fx+cw+12,by+86,cw);setInitialFocus(titleField);
        }else{int[] indices={1,2,3,4,5};for(int i=0;i<indices.length;i++)number(indices[i],fx+(i%2)*(cw+12),by+14+(i/2)*36,cw);setInitialFocus(fields[1]);}
        button(x+24,footer(),75,"button.back",this::back);button(x+w-174,footer(),150,"button.admin_review",this::review);
    }
    private void review(){
        capture();try{
            String title=Rules.text(titleValue,3,64,false);int[] n=Arrays.stream(values).mapToInt(Integer::parseInt).toArray();
            var s=new Settings(n[0],n[1],n[2],n[3],n[4],n[5],n[6],n[7],n[8]).validated();
            var p=new CouncilPackets.Action(CREATE,electionId,round,phase,title,CouncilState.JSON.toJson(s));
            var details=new ArrayList<String>();for(int i=0;i<9;i++)details.add(Text.s(LABELS[i])+": "+n[i]);
            minecraft.setScreen(new AdminConfirmScreen(view,this,p,"title.admin_review",Text.s("hint.admin_review",title),details));
        }catch(Rules.RuleException|NumberFormatException ex){notice=Component.translatable("error.realmcouncil."+(ex instanceof Rules.RuleException r?r.code:"options")).getString();noticeAt=System.currentTimeMillis();}
    }
    @Override protected void back(){minecraft.setScreen(new AdminScreen(view));}
    @Override public void onClose(){back();}
    @Override public void accept(CouncilView v,String message){if(v.administration()==null){minecraft.setScreen(new CouncilScreen(v));return;}view=v;receivedAt=System.currentTimeMillis();if(!message.isEmpty()){notice=Component.translatable(message.startsWith("error.")?"error.realmcouncil."+message.substring(6):"message.realmcouncil."+message).getString();noticeAt=receivedAt;}}
    @Override public void render(GuiGraphics g,int mx,int my,float t){
        book(g,mx,my,t);int cw=(w-62)/2,fx=x+25,by=body();
        if(page==0){line(g,Text.s("label.admin_title"),fx,by+2,w-50,LIGHT);int[] indices={0,6,7,8};for(int i=0;i<4;i++)line(g,Text.s(LABELS[indices[i]]),fx+(i%2)*(cw+12),by+38+(i/2)*36,cw,LIGHT);}
        else{int[] indices={1,2,3,4,5};for(int i=0;i<indices.length;i++)line(g,Text.s(LABELS[indices[i]]),fx+(i%2)*(cw+12),by+2+(i/2)*36,cw,LIGHT);}
        super.render(g,mx,my,t);
    }
}
