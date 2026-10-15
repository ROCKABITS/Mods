package pt.realmcouncil.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.network.CouncilPackets;
import static pt.realmcouncil.domain.CouncilAdministration.*;

public final class AdminConfirmScreen extends CouncilBase {
    private final CouncilBase previous;
    private final CouncilPackets.Action packet;
    private final String explanation;
    private final List<String> details;
    private boolean waiting;
    public AdminConfirmScreen(CouncilView view,CouncilBase previous,CouncilPackets.Action packet,String title,String explanation,List<String> details){
        super(view,title,true);this.previous=previous;this.packet=packet;this.explanation=explanation;this.details=List.copyOf(details);
    }
    static boolean current(CouncilView v,CouncilPackets.Action p){
        if(v.administration()==null)return false;
        if(p.action()==CREATE&&!v.administration().canCreate())return false;
        if(p.action()==END_TERM)return v.mandate()!=null&&v.mandate().active(v.serverTime())&&v.mandate().electionId.toString().equals(p.election());
        var e=v.election();String phase=p.action()==CREATE?p.choice():p.extra();
        return e==null?p.election().isEmpty()&&p.round()==0&&phase.isEmpty():e.id().toString().equals(p.election())&&e.round()==p.round()&&e.phase().equals(phase);
    }
    @Override protected void init(){measure();button(x+24,footer(),90,"button.back",this::back);
        var confirm=button(x+w-175,footer(),151,waiting?"button.saving":"button.admin_confirm",()->{waiting=true;CouncilClient.send(packet.action(),packet.election(),packet.round(),packet.choice(),packet.text(),packet.extra());rebuild();});confirm.active=!waiting&&current(view,packet);
    }
    @Override protected void back(){previous.accept(view,"");minecraft.setScreen(previous);}
    @Override public void onClose(){back();}
    @Override public void accept(CouncilView v,String message){
        if(v.administration()==null){minecraft.setScreen(new CouncilScreen(v));return;}
        if(message.equals("saved")){if(previous instanceof AdminCreateScreen)minecraft.setScreen(new AdminScreen(v));else{previous.accept(v,message);minecraft.setScreen(previous);}return;}
        if(!message.isEmpty())waiting=false;super.accept(v,message);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float t){
        book(g,mx,my,t);int by=body()+4,available=footer()-by-5;
        int explanationHeight=details.size()>5?24:Math.min(60,available/2);
        wrap(g,explanation,x+30,by,w-60,explanationHeight,LIGHT);
        int top=by+explanationHeight+5;
        if(details.size()>5){int rows=(details.size()+1)/2,cw=(w-72)/2;
            for(int i=0;i<details.size();i++)line(g,details.get(i),x+30+(i/rows)*(cw+12),top+(i%rows)*15,cw,GOLD);
        }else{for(int i=0;i<details.size();i++)wrap(g,details.get(i),x+30,top+i*24,w-60,22,GOLD);}
        super.render(g,mx,my,t);
    }
}
