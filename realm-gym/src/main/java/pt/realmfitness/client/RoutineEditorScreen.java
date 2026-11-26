package pt.realmfitness.client;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pt.realmfitness.FitnessMod;
import pt.realmfitness.FitnessNetwork;
import pt.realmfitness.domain.Exercise;
import pt.realmfitness.domain.FitnessLedger;
import pt.realmfitness.domain.FitnessLedger.Routine;
import pt.realmfitness.domain.FitnessLedger.Step;
import static pt.realmfitness.client.FitnessTheme.*;
import static pt.realmfitness.client.GymMenu.*;

/** Plan editor with a direct machine picker and one draft retained until server acknowledgement. */
public final class RoutineEditorScreen extends Screen {
    private final AthleteScreen parent;
    private final UUID id;
    private final boolean existing;
    private final List<Step> steps=new ArrayList<>();
    private String name, message="";
    private int weekday,index,x,y,w,h,pickerScroll;
    private boolean choosing,saving;
    private EditBox nameField;

    public RoutineEditorScreen(AthleteScreen parent,JsonObject plan){
        super(tr("edit_plan"));
        this.parent=parent;
        existing=plan!=null;
        if(plan==null){id=UUID.randomUUID();name=text("new_plan");steps.add(new Step(Exercise.BENCH,2,8,20,30));}
        else{
            Routine routine=FitnessLedger.JSON.fromJson(plan,Routine.class);
            id=routine.id();name=routine.name().startsWith("fitness.")?text(routine.name()):routine.name();
            weekday=routine.weekday();steps.addAll(routine.steps());
        }
    }

    public void accept(JsonObject snapshot){
        message=FitnessClient.str(snapshot,"message");
        if(saving&&message.isEmpty()&&FitnessClient.str(snapshot,"page").equals("routines")){
            minecraft.setScreen(parent);
            parent.accept(snapshot);
            return;
        }
        saving=false;
    }

    private void capture(){if(nameField!=null)name=nameField.getValue();}
    private void rebuild(){capture();rebuildWidgets();}
    private int formWidth(){return w>=540?(w-36)/2:w-24;}
    private int pickerLeft(){return w>=540?x+formWidth()+24:x+12;}
    private int pickerWidth(){return x+w-12-pickerLeft();}
    private int footer(){return y+h-27;}
    private int pickerRows(){return Math.min(Exercise.values().length,Math.max(1,(footer()-(y+59))/22));}
    private boolean drawForm(){return !choosing||w>=540;}

    private Button control(int bx,int by,int bw,Component caption,Kind kind,Runnable action){
        return addRenderableWidget(GymMenu.button(bx,by,bw,18,caption,kind,action));
    }
    private Button control(int bx,int by,int bw,String caption,Kind kind,Runnable action){
        return control(bx,by,bw,tr(caption),kind,action);
    }

    @Override protected void init(){
        w=Math.min(730,width-16);h=Math.min(432,height-16);x=(width-w)/2;y=(height-h)/2;
        nameField=null;
        if(drawForm())formControls();
        if(choosing)pickerControls();
    }

    private void formControls(){
        int left=x+12,width=formWidth(), half=(width-8)/2;
        nameField=addRenderableWidget(new EditBox(font,left,y+43,width,17,tr("plan_name")));
        nameField.setMaxLength(40);nameField.setValue(name);
        nameField.active=!choosing;
        var day=control(left,y+66,half,"weekday."+weekday,Kind.SECONDARY,()->{weekday=(weekday+1)%8;rebuild();});
        day.active=!choosing;
        var add=control(left+half+8,y+66,half,"add_exercise",Kind.SECONDARY,()->{
            steps.add(new Step(Exercise.RUN,2,8,1,30));index=steps.size()-1;rebuild();
        });
        add.active=!choosing&&steps.size()<8;
        int machineY=y+96;Step step=steps.get(index);
        var previous=control(left,machineY,20,Component.literal("‹"),Kind.SECONDARY,
            ()->{index=Math.floorMod(index-1,steps.size());rebuild();});
        previous.active=!choosing&&steps.size()>1;
        var next=control(left+width-20,machineY,20,Component.literal("›"),Kind.SECONDARY,
            ()->{index=(index+1)%steps.size();rebuild();});
        next.active=!choosing&&steps.size()>1;
        control(left+25,machineY,width-50,Component.literal(exercise(step.exercise())+"  ▾"),
            choosing?Kind.SELECTED:Kind.SECONDARY,()->{
                choosing=!choosing;
                if(choosing)pickerScroll=Math.min(step.exercise().ordinal(),Exercise.values().length-pickerRows());
                rebuild();
            }).setTooltip(Tooltip.create(tr("menu.choose_machine")));

        int top=y+125, gap=h<260?25:34;
        String[] labels={"menu.sets","menu.repetitions","menu.load","menu.rest_label"};
        for(int i=0;i<labels.length;i++){
            int field=i,lx=left+(i%2)*(half+8),yy=top+i/2*gap;
            for(int delta:new int[]{-1,1}){
                int at=delta<0?lx:lx+half-20;
                var button=control(at,yy,20,Component.literal(delta<0?"−":"+"),Kind.SECONDARY,()->change(field,delta));
                button.active=!choosing;
                button.setTooltip(Tooltip.create(tr(delta<0?"menu.decrease":"menu.increase",text(labels[i]))));
            }
        }
        control(left,footer(),Math.min(96,half),"save",Kind.PRIMARY,()->{
            capture();
            if(name.isBlank()||saving)return;
            try{
                var routine=new Routine(id,name,weekday,steps);
                saving=true;message="";
                FitnessNetwork.request("routine",routine);
            }catch(IllegalArgumentException invalid){message="fitness.invalid_plan";}
        }).active=!choosing;
        control(left+Math.min(96,half)+7,footer(),52,"back",Kind.SECONDARY,this::onClose).active=!choosing;
        int manage=footer()-21;
        if(manage>=top+gap+18){
            var remove=control(left,manage,91,"remove_step",Kind.TAB,()->{
                steps.remove(index);index=Math.min(index,steps.size()-1);rebuild();
            });
            remove.active=!choosing&&steps.size()>1;
            if(existing)control(left+97,manage,85,"delete",Kind.TAB,()->{
                if(saving)return;
                saving=true;FitnessNetwork.request("delete",Map.of("id",id.toString()));
            }).active=!choosing;
        }
    }

    private void pickerControls(){
        int left=pickerLeft(),width=pickerWidth(),rows=pickerRows();
        var machines=Exercise.values();
        pickerScroll=Math.clamp(pickerScroll,0,Math.max(0,machines.length-rows));
        for(int row=0;row<rows;row++){
            Exercise machine=machines[row+pickerScroll];
            control(left+8,y+52+row*22,width-16,Component.literal("    "+exercise(machine)),
                machine==steps.get(index).exercise()?Kind.ROW_SELECTED:Kind.ROW,()->{
                    Step step=steps.get(index);
                    if(machine!=step.exercise())
                        steps.set(index,new Step(machine,step.sets(),step.repetitions(),machine.minimumLoad(),step.restSeconds()));
                    choosing=false;rebuild();
                });
        }
        control(left+8,footer(),Math.min(72,width-16),"back",Kind.SECONDARY,()->{choosing=false;rebuild();});
    }

    private void change(int field,int delta){
        Step step=steps.get(index);
        int sets=step.sets(),reps=step.repetitions(),load=step.load(),rest=step.restSeconds();
        switch(field){
            case 0->sets=Math.clamp(sets+delta,1,5);
            case 1->reps=Math.clamp(reps+delta,4,20);
            case 2->load=Math.clamp(load+delta*step.exercise().loadStep(),step.exercise().minimumLoad(),step.exercise().maximumLoad(20));
            case 3->rest=Math.clamp(rest+delta*5,5,180);
        }
        steps.set(index,new Step(step.exercise(),sets,reps,load,rest));
    }

    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){
        if(!choosing||dy==0||mx<pickerLeft()||my<y+52||my>=footer())return super.mouseScrolled(mx,my,dx,dy);
        pickerScroll-=Math.signum(dy);
        rebuild();
        return true;
    }

    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}

    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xD8131921);
        panel(g,x,y,w,h,STEEL);
        label(g,text("edit_plan"),x+12,y+10,w-24,TEXT);
        if(drawForm())drawFields(g);
        if(choosing){
            var machines=Exercise.values();
            int left=pickerLeft(),width=pickerWidth();
            panel(g,left,y+31,width,footer()-y-31,SHEET);
            label(g,text("menu.choose_machine"),left+9,y+37,width-18,TEXT);
            label(g,text("menu.machine_resets_load"),left+78,footer()+6,width-82,LABEL);
        }
        super.render(g,mx,my,partial);
        if(choosing){
            var machines=Exercise.values();
            for(int row=0;row<pickerRows();row++)
                g.renderItem(new ItemStack(FitnessMod.MACHINES.get(machines[row+pickerScroll]).get()),pickerLeft()+11,y+53+row*22);
        }
    }

    private void drawFields(GuiGraphics g){
        int left=x+12,width=formWidth(),half=(width-8)/2;
        label(g,text("plan_name"),left,y+29,width,LABEL);
        label(g,text("menu.exercise_index",index+1,steps.size()),left,y+87,width,LABEL);
        Step step=steps.get(index);
        String[] values={text("sets",step.sets()),text("repetitions",step.repetitions()),
            load(step.exercise(),step.load()),text("rest_seconds",step.restSeconds())};
        int top=y+125,gap=h<260?25:34;
        for(int i=0;i<values.length;i++){
            int start=left+(i%2)*(half+8);
            String value=values[i];
            label(g,value,start+Math.max(22,(half-font.width(value))/2),top+i/2*gap+5,half-43,TEXT);
        }
        if(!message.isEmpty())label(g,text(message),left,y+20,width,ACTION);
    }

    @Override public void onClose(){
        if(choosing){choosing=false;rebuild();}
        else minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen(){return false;}
}
