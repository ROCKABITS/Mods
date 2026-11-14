package pt.realmfitness.client;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pt.realmfitness.FitnessNetwork;
import pt.realmfitness.domain.*;
import pt.realmfitness.domain.FitnessLedger.*;
import static pt.realmfitness.client.FitnessTheme.*;

/** A visual plan editor: no identifiers, tags or JSON fields exposed to the player. */
public final class RoutineEditorScreen extends Screen {
    private final JsonObject original;
    private final UUID id;
    private final boolean existing;
    private String name;
    private int weekday,index,x,y,w,h;
    private final List<Step> steps=new ArrayList<>();
    private EditBox nameField;
    public RoutineEditorScreen(JsonObject original,JsonObject plan){
        super(tr("edit_plan"));this.original=original;existing=plan!=null;
        if(plan==null){id=UUID.randomUUID();name=text("new_plan");steps.add(new Step(Exercise.BENCH,2,8,20,30));}
        else{Routine r=FitnessLedger.JSON.fromJson(plan,Routine.class);id=r.id();name=r.name().startsWith("fitness.")?text(r.name()):r.name();weekday=r.weekday();steps.addAll(r.steps());}
    }
    private void capture(){if(nameField!=null)name=nameField.getValue();}
    private void rebuild(){capture();rebuildWidgets();}
    private void button(int bx,int by,int bw,String label,Runnable r){addRenderableWidget(FitnessTheme.button(bx,by,bw,18,Component.literal(label),r));}
    @Override protected void init(){
        w=Math.min(620,width-16);h=Math.min(400,height-16);x=(width-w)/2;y=(height-h)/2;
        nameField=addRenderableWidget(new EditBox(font,x+14,y+32,w-28,18,tr("plan_name")));nameField.setMaxLength(40);nameField.setValue(name);
        button(x+14,y+58,(w-34)/2,text("weekday."+weekday),()->{weekday=(weekday+1)%8;rebuild();});
        button(x+20+(w-34)/2,y+58,(w-34)/2,text("add_exercise"),()->{if(steps.size()<8){steps.add(new Step(Exercise.RUN,2,8,1,30));index=steps.size()-1;rebuild();}});
        int yy=y+85;Step s=steps.get(index);
        button(x+14,yy,24,"‹",()->{index=Math.floorMod(index-1,steps.size());rebuild();});
        button(x+44,yy,w-88,exercise(s.exercise()),()->{var e=Exercise.values()[(s.exercise().ordinal()+1)%Exercise.values().length];steps.set(index,new Step(e,s.sets(),s.repetitions(),e.minimumLoad(),s.restSeconds()));rebuild();});
        button(x+w-38,yy,24,"›",()->{index=(index+1)%steps.size();rebuild();});
        int gap=h<300?28:42,top=yy+30,col=(w-34)/2;
        for(int n=0;n<4;n++){int kind=n,bx=x+14+(n%2)*(col+6),by=top+(n/2)*gap;button(bx,by,22,"−",()->change(kind,-1));button(bx+col-22,by,22,"+",()->change(kind,1));}
        int bottom=y+h-28;
        button(x+14,bottom,66,text("back"),this::onClose);
        button(x+86,bottom,Math.max(50,(w-190)/2),text("remove_step"),()->{if(steps.size()>1){steps.remove(index);index=Math.min(index,steps.size()-1);rebuild();}});
        button(x+w-94,bottom,80,text("save"),()->{capture();if(!name.isBlank())FitnessNetwork.request("routine",new Routine(id,name,weekday,steps));});
        if(existing)button(x+w-94,bottom-24,80,text("delete"),()->FitnessNetwork.request("delete",Map.of("id",id.toString())));
    }
    private void change(int kind,int delta){Step s=steps.get(index);int sets=s.sets(),reps=s.repetitions(),load=s.load(),rest=s.restSeconds();switch(kind){case 0->sets=Math.clamp(sets+delta,1,5);case 1->reps=Math.clamp(reps+delta,4,20);case 2->load=Math.clamp(load+delta*s.exercise().loadStep(),s.exercise().minimumLoad(),s.exercise().maximumLoad(20));case 3->rest=Math.clamp(rest+delta*5,5,180);}steps.set(index,new Step(s.exercise(),sets,reps,load,rest));rebuild();}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float p){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xDA100E19);frame(g,x,y,w,h,PANEL);label(g,text("edit_plan")+" · "+(index+1)+"/"+steps.size(),x+14,y+13,w-28,ACCENT);
        Step s=steps.get(index);int gap=h<300?28:42,top=y+115,col=(w-34)/2;
        String[] values={text("sets",s.sets()),text("repetitions",s.repetitions()),load(s.exercise(),s.load()),text("rest_seconds",s.restSeconds())};
        for(int n=0;n<4;n++)label(g,values[n],x+40+(n%2)*(col+6),top+(n/2)*gap+5,col-50,INK);
        int total=steps.stream().mapToInt(Step::sets).sum();long variety=steps.stream().map(Step::exercise).distinct().count();
        if(h>290)label(g,text("plan_summary",total,variety),x+14,y+h-82,w-28,GREEN);
        super.render(g,mx,my,partial);
    }
    @Override public void onClose(){minecraft.setScreen(new AthleteScreen(original));}
    @Override public boolean isPauseScreen(){return false;}
}
