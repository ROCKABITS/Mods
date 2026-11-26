package pt.realmfitness.client;

import com.google.gson.*;
import java.time.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pt.realmfitness.FitnessMod;
import pt.realmfitness.FitnessNetwork;
import pt.realmfitness.domain.*;
import pt.realmfitness.domain.FitnessLedger.*;
import static pt.realmfitness.client.FitnessClient.*;
import static pt.realmfitness.client.FitnessTheme.*;
import static pt.realmfitness.client.GymMenu.*;
import pt.realmfitness.RecoveryView;

public final class AthleteScreen extends Screen {
    private JsonObject data;
    private String page="profile",journal="history";
    private int x,y,w,h,scroll,selected,ticks,load=20,reps=8;
    private Exercise exercise=Exercise.BENCH;
    private String period="all",scope="server";
    private boolean dirtySelection;
    private long received;
    private int profileScroll, stepScroll;
    private Exercise detail;
    public AthleteScreen(JsonObject data){super(tr("title"));accept(data);}
    public JsonObject data(){return data;}
    public void accept(JsonObject d){
        JsonObject previous=data;
        data=d;
        received=System.nanoTime();
        String requested=str(d,"page");
        boolean pageChanged=!requested.isEmpty()&&!requested.equals(page);
        if(pageChanged){page=requested;scroll=0;profileScroll=0;stepScroll=0;detail=null;}
        if(!dirtySelection&&d.has("exercise")){
            exercise=Exercise.valueOf(str(d,"exercise"));load=num(d,"load");reps=num(d,"repetitions");
            period=str(d,"period");scope=str(d,"scope");
        }
        if(minecraft!=null&&(pageChanged||differ(previous,d,"workout")||differ(previous,d,"routines")||differ(previous,d,"board")))
            rebuildWidgets();
    }

    private static boolean differ(JsonObject previous,JsonObject next,String key){
        return previous==null||!Objects.equals(previous.get(key),next.get(key));
    }

    private void switchPage(String name){
        page=name;scroll=profileScroll=stepScroll=0;detail=null;
        if(name.equals("routines")){
            Workout active=workout();
            if(active!=null){
                var plans=array(data,"routines");
                for(int i=0;i<plans.size();i++)
                    if(str(plans.get(i).getAsJsonObject(),"id").equals(active.plan.id().toString())){selected=i;break;}
            }
        }
        rebuildWidgets();
    }

    private net.minecraft.client.gui.components.Button menu(int bx,int by,int bw,String key,Kind kind,Runnable action){
        return addRenderableWidget(GymMenu.button(bx,by,bw,18,tr(key),kind,action));
    }

    private net.minecraft.client.gui.components.Button button(int bx,int by,int bw,String key,Runnable action){
        return menu(bx,by,bw,key,Kind.SECONDARY,action);
    }

    private net.minecraft.client.gui.components.Button literal(int bx,int by,int bw,String text,Kind kind,Runnable action){
        var widget=addRenderableWidget(GymMenu.button(bx,by,bw,18,Component.literal(text),kind,action));
        widget.setTooltip(Tooltip.create(Component.literal(text)));
        return widget;
    }

    private net.minecraft.client.gui.components.Button literal(int bx,int by,int bw,String text,Runnable action){
        return literal(bx,by,bw,text,Kind.SECONDARY,action);
    }

    private int body(){return y+55;}
    private int bottom(){return y+h-25;}
    private boolean compact(){return w<490||h<285;}
    private int portraitWidth(){return Math.min(165,(w-35)/4);}
    private int profileLeft(){return x+12+(compact()?0:portraitWidth()+12);}
    private int right(){return x+w-12;}
    private int nextHeight(){return compact()?66:96;}
    private int attributesTop(){return body()+nextHeight()+(compact()?34:47);}
    private int profileRows(){return Math.max(1,(bottom()-attributesTop()-22)/19);}
    private int nameWidth(){return (right()-profileLeft())*48/100;}
    private int routineSeparator(){return x+12+Math.max(90,(w-36)/3)+12;}
    private int routineStepRows(){return Math.max(1,(bottom()-body()-96)/25);}
    private Workout workout(){return data.has("workout")?FitnessLedger.JSON.fromJson(data.get("workout"),Workout.class):null;}
    private Workout ongoing(){Workout workout=workout();return workout!=null&&workout.next()!=null?workout:null;}

    @Override protected void init(){
        w=Math.min(780,width-16);h=Math.min(450,height-16);x=(width-w)/2;y=(height-h)/2;
        String[] pages={"profile","routines","catalog","history","leaders"};
        int tx=x+10;
        int maximum=(w-24)/pages.length;
        for(String p:pages){
            int tabWidth=Math.min(maximum,font.width(tr("tab."+p))+13);
            var tab=menu(tx,y+26,tabWidth,"tab."+p,p.equals(page)?Kind.SELECTED:Kind.TAB,()->switchPage(p));
            if(p.equals("leaders"))tab.active=data.has("board")&&data.get("board").getAsBoolean();
            tab.setTooltip(Tooltip.create(tr("tab."+p)));
            tx+=tabWidth+2;
        }
        menu(x+w-69,y+5,57,"close",Kind.TAB,this::onClose);
        if(page.equals("profile"))profileButtons();
        if(page.equals("routines"))routineButtons();
        if(page.equals("history")){
            String[] modes={"history","records","progress"};
            for(int i=0;i<modes.length;i++){
                String mode=modes[i];
                menu(x+12+i*(w-24)/3,body(),(w-30)/3,"tab."+mode,mode.equals(journal)?Kind.SELECTED:Kind.TAB,
                    ()->{journal=mode;scroll=0;rebuildWidgets();});
            }
        }
        if(page.equals("leaders"))leaderButtons();
    }

    private void profileButtons(){
        String key=ongoing()==null?"menu.choose_plan":"menu.view_plan";
        menu(profileLeft()+9,body()+nextHeight()-23,font.width(tr(key))+14,key,Kind.PRIMARY,()->switchPage("routines"));
        if(detail!=null){
            menu(right()-65,attributesTop(),65,"back",Kind.SECONDARY,()->{detail=null;rebuildWidgets();});
            return;
        }
        var attributes=Exercise.values();
        int rows=Math.min(profileRows(),attributes.length);
        profileScroll=pt.realmfitness.domain.FitnessMath.clamp(profileScroll,0,Math.max(0,attributes.length-rows));
        for(int i=0;i<rows;i++){
            Exercise e=attributes[i+profileScroll];
            literal(profileLeft(),attributesTop()+20+i*19,nameWidth(),text("stat."+e.id()),Kind.ROW,()->{detail=e;rebuildWidgets();});
        }
    }

    private void routineButtons(){
        var plans=array(data,"routines");
        selected=pt.realmfitness.domain.FitnessMath.clamp(selected,0,Math.max(0,plans.size()-1));
        int left=x+12, split=routineSeparator(), listWidth=split-left-17;
        int visible=Math.max(1,(bottom()-body()-50)/22);
        scroll=pt.realmfitness.domain.FitnessMath.clamp(scroll,0,Math.max(0,plans.size()-visible));
        for(int i=0;i<visible&&i+scroll<plans.size();i++){
            int n=i+scroll;
            var plan=plans.get(n).getAsJsonObject();
            literal(left+5,body()+19+i*22,listWidth,planName(str(plan,"name")),n==selected?Kind.ROW_SELECTED:Kind.ROW,
                ()->{selected=n;stepScroll=0;rebuildWidgets();});
        }
        menu(left+5,bottom()-18,Math.min(listWidth,90),"new_plan",Kind.SECONDARY,
            ()->minecraft.setScreen(new RoutineEditorScreen(this,null)));
        if(plans.size()>0){
            var plan=plans.get(selected).getAsJsonObject();
            Workout active=ongoing();
            boolean selectedActive=active!=null&&str(plan,"id").equals(active.plan.id().toString());
            int actionWidth=Math.max(55,(right()-split-6)/2);
            if(selectedActive){
                menu(split,bottom()-18,actionWidth,"menu.back_to_game",Kind.PRIMARY,this::onClose);
                menu(split,bottom()+2,actionWidth,"end_workout",Kind.TAB,()->FitnessNetwork.request("end_workout",Map.of()));
            }else{
                var start=menu(split,bottom()-18,actionWidth,"begin",Kind.PRIMARY,
                    ()->FitnessNetwork.request("begin",Map.of("id",str(plan,"id"))));
                start.active=active==null;
                if(active!=null)
                    menu(split,bottom()+2,actionWidth,"end_workout",Kind.TAB,()->FitnessNetwork.request("end_workout",Map.of()));
            }
            menu(split+actionWidth+6,bottom()-18,actionWidth,"edit",Kind.SECONDARY,
                ()->minecraft.setScreen(new RoutineEditorScreen(this,plan)));
        }else if(ongoing()!=null){
            menu(split,bottom()-18,right()-split,"end_workout",Kind.SECONDARY,()->FitnessNetwork.request("end_workout",Map.of()));
        }
    }
    private void leaderButtons(){
        int cw=(w-32)/3;
        button(x+12,body(),cw,"exercise."+exercise.id(),()->{exercise=Exercise.values()[(exercise.ordinal()+1)%Exercise.values().length];load=exercise.minimumLoad();filter(false);});
        button(x+16+cw,body(),cw,"period."+period,()->{period=switch(period){case "all"->"week";case "week"->"month";default->"all";};filter(false);});
        button(x+20+cw*2,body(),cw,"scope."+scope,()->{scope=scope.equals("server")?"gym":"server";filter(false);});
        literal(x+12,body()+25,25,"−",()->{load=Math.max(exercise.minimumLoad(),load-exercise.loadStep());filter(false);});
        literal(x+110,body()+25,25,"+",()->{load=Math.min(exercise.maximumLoad(20),load+exercise.loadStep());filter(false);});
        literal(x+w/2+4,body()+25,25,"−",()->{reps=Math.max(4,reps-1);filter(false);});literal(x+w-38,body()+25,25,"+",()->{reps=Math.min(20,reps+1);filter(false);});
        var pin=button(x+w/2-64,bottom(),128,"pin_board",()->filter(true));pin.active=data.has("mayPin")&&data.get("mayPin").getAsBoolean();
    }
    private void filter(boolean pin){dirtySelection=true;FitnessNetwork.request(pin?"pin":"filter",Map.of("exercise",exercise.name(),"load",load,"repetitions",reps,"period",period,"scope",scope));}
    private int level(Exercise e){return FitnessLedger.level(data.getAsJsonObject("experience").get(e.attribute().name()).getAsInt());}
    private long serverNow(){return time(data,"now")+(System.nanoTime()-received)/1_000_000;}
    private int remainingRest(){return (int)Math.max(0,(time(data,"restUntil")-serverNow()+999)/1000);}
    @Override public void tick(){if(++ticks%100==0)FitnessNetwork.request("refresh",Map.of());}
    @Override public void renderBackground(GuiGraphics g){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xD8131921);
        GymMenu.header(g,x,y,w,h);
        switch(page){case "profile"->profile(g);case "catalog"->catalog(g,mx,my);case "routines"->routines(g);case "leaders"->leaders(g);default->journal(g);}
        String message=str(data,"message");
        if(!message.isEmpty())label(g,text(message),x+w/2,bottom()+8,w/2-13,ACTION);
        super.render(g,mx,my,partial);
    }

    private void profile(GuiGraphics g){
        int left=profileLeft(), top=body(), width=right()-left, next=nextHeight();
        if(!compact())portrait(g);
        panel(g,left,top,width,next,SHEET);
        Workout current=ongoing();
        if(current==null){
            label(g,text("menu.no_plan"),left+9,top+10,width-18,TEXT);
            label(g,text("menu.choose_plan_hint"),left+9,top+25,width-18,LABEL);
        }else{
            Step step=current.next();
            int plateSize=next-8;
            int textWidth=width-plateSize-24;
            label(g,text("menu.next"),left+9,top+6,textWidth,LABEL);
            label(g,exercise(step.exercise()),left+9,top+19,textWidth,TEXT);
            int completed=current.completed;
            for(int i=0;i<current.nextIndex();i++)completed-=current.plan.steps().get(i).sets();
            label(g,text("menu.series",completed+1,step.sets(),step.repetitions()),left+9,top+32,textWidth,TEXT);
            String hint=remainingRest()>0?text("menu.rest",remainingRest()):text("menu.mount",exercise(step.exercise()));
            if(!compact())label(g,hint,left+9,top+48,textWidth,LABEL);
            else{
                int hintLeft=left+30+font.width(text("menu.view_plan"));
                label(g,hint,hintLeft,top+next-18,textWidth-hintLeft+left,LABEL);
            }
            GymMenu.plate(g,right()-plateSize-7,top+4,plateSize,Integer.toString(current.load(step)),
                step.exercise().weighted?"kg":text("menu.resistance_unit"));
        }
        int daily=top+next+9;
        String sets=text("menu.today",num(data,"dailySets"),num(data,"dailyGoal"));
        String groups=text("menu.groups",num(data,"dailyVariety"),num(data,"varietyGoal"));
        label(g,sets,left,daily,width,TEXT);
        int groupWidth=font.width(groups);
        int groupsX=compact()?right()-groupWidth:left+width/2-groupWidth/2;
        if(groupsX>left+font.width(sets)+6)label(g,groups,groupsX,daily,groupWidth,LABEL);
        else label(g,groups,left,daily+11,width,LABEL);
        int goal=Math.max(1,num(data,"dailyGoal")),gap=2,available=width-gap*(goal-1);
        int barY=daily+(groupsX>left+font.width(sets)+6?15:22);
        for(int i=0;i<goal;i++)
            panel(g,left+i*available/goal+i*gap,barY,(i+1)*available/goal-i*available/goal,4,
                i<num(data,"dailySets")?ACTION:SHEET);
        if(!compact()){
            String xp=text("xp_today",num(data,"dailyXp"),num(data,"dailyXpCap"));
            label(g,xp,right()-font.width(xp),daily,font.width(xp),LABEL);
        }
        if(detail==null)drawAttributes(g);
        else drawAttributeDetail(g);
    }

    private void portrait(GuiGraphics g){
        int left=x+12, top=body(), pw=portraitWidth(), ph=bottom()-top-8;
        panel(g,left,top,pw,ph,SHEET);
        label(g,str(data,"name"),left+9,top+10,pw-18,TEXT);
        int level=Arrays.stream(Exercise.values()).mapToInt(this::level).sum();
        label(g,text("athlete_level",level),left+9,top+25,pw-18,LABEL);
        if(minecraft.player!=null){
            int size=Math.max(12,Math.min(pw/3,(ph-108)*9/20));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g,left+pw/2,top+ph-67,size,0,-12,minecraft.player);
        }
        panel(g,left+9,top+ph-62,pw-18,1,RULE);
        int best=0;
        for(var element:array(data,"records")){
            var record=element.getAsJsonObject();
            if(str(record,"exercise").equals(Exercise.BENCH.name()))best=Math.max(best,num(record,"load"));
        }
        label(g,text("menu.best_bench",best>0?load(Exercise.BENCH,best):"–"),left+9,top+ph-51,pw-18,LABEL);
        g.renderItem(new ItemStack(FitnessMod.PROTEIN_SHAKE.get()),left+7,top+ph-32);
        label(g,text("menu.recovery"),left+27,top+ph-32,pw-34,TEXT);
        label(g,RecoveryView.status().getString(),left+27,top+ph-19,pw-34,LABEL);
    }

    private double effective(Exercise e){
        JsonObject condition=data.getAsJsonObject("conditioning");
        if(condition!=null&&condition.get("enabled").getAsBoolean())
            return condition.getAsJsonObject("attributes").getAsJsonObject(e.name()).get("effectiveBonus").getAsDouble();
        return data.getAsJsonObject("bonuses").get(e.name()).getAsDouble()*level(e)/20;
    }

    private void drawAttributes(GuiGraphics g){
        int left=profileLeft(), top=attributesTop(), statWidth=(right()-left-nameWidth()-9)/3;
        label(g,text("menu.attributes"),left+4,top,right()-left,TEXT);
        String[] labels={"menu.level","menu.bonus","menu.fatigue"};
        for(int i=0;i<labels.length;i++)rightAligned(g,text(labels[i]),right()-6-(2-i)*statWidth,top,LABEL);
        panel(g,left,top+14,right()-left,1,RULE);
        Exercise[] attributes=Exercise.values();
        int visible=Math.min(profileRows(),attributes.length-profileScroll);
        for(int i=0;i<visible;i++){
            Exercise e=attributes[i+profileScroll];
            int yy=top+25+i*19;
            int fatigue=(int)Math.round(data.getAsJsonObject("fatigue").get(e.name()).getAsDouble()*100);
            rightAligned(g,level(e)+"/20",right()-6-2*statWidth,yy,LABEL);
            rightAligned(g,String.format(Locale.ROOT,"+%.1f%%",effective(e)*100),right()-6-statWidth,yy,TEXT);
            rightAligned(g,fatigue+"%",right()-6,yy,LABEL);
        }
        if(visible<attributes.length){
            int span=visible*19;
            panel(g,right()-1,top+20,2,span,SHEET);
            panel(g,right()-1,top+20+span*profileScroll/attributes.length,2,Math.max(1,span*visible/attributes.length),EDGE);
        }
    }

    private void rightAligned(GuiGraphics g,String message,int x,int y,int color){
        label(g,message,x-font.width(message),y,font.width(message),color);
    }

    private void drawAttributeDetail(GuiGraphics g){
        Exercise e=detail;
        int left=profileLeft()+4, top=attributesTop(), width=right()-left-4;
        label(g,text("attribute."+e.id()),left,top+4,width-68,TEXT);
        int experience=data.getAsJsonObject("experience").get(e.name()).getAsInt();
        int level=level(e);
        label(g,text("xp",experience,level==20?experience:FitnessLedger.threshold(level+1)),left,top+24,width,LABEL);
        label(g,text("fatigue",Math.round(data.getAsJsonObject("fatigue").get(e.name()).getAsDouble()*100)),left,top+36,width,LABEL);
        var condition=data.getAsJsonObject("conditioning");
        if(condition!=null&&condition.get("enabled").getAsBoolean()){
            var value=condition.getAsJsonObject("attributes").getAsJsonObject(e.name());
            var information=text("conditioning.short",Math.round(value.get("percent").getAsDouble()));
            label(g,information,left,top+48,width,LABEL);
            if(top+78<bottom()){
                boolean maintained=value.get("maintainedToday").getAsBoolean();
                label(g,maintained?text("conditioning.maintained"):text("conditioning.sets",num(value,"setsToday"),num(condition,"setsRequired")),
                    left,top+60,width,LABEL);
                label(g,text("conditioning.clock",text("conditioning.mode."+str(condition,"timeMode"))),left,top+73,width,LABEL);
            }
        }
    }
    private void catalog(GuiGraphics g,int mx,int my){
        int cols=w<420?1:2,cw=(w-30)/cols,ch=Math.max(38,(h-100)/3),visible=Math.max(1,(h-100)/ch)*cols;
        scroll=pt.realmfitness.domain.FitnessMath.clamp(scroll,0,Math.max(0,Exercise.values().length-visible));Set<String> available=new HashSet<>();array(data,"available").forEach(e->available.add(e.getAsString()));
        for(int i=0;i<visible&&i+scroll<Exercise.values().length;i++){
            Exercise e=Exercise.values()[i+scroll];int bx=x+12+i%cols*(cw+6),by=body()+i/cols*ch;
            panel(g,bx,by,cw-2,ch-5,SHEET);g.renderItem(new ItemStack(FitnessMod.MACHINES.get(e).get()),bx+5,by+7);
            label(g,exercise(e),bx+28,by+7,cw-34,INK);label(g,text(available.contains(e.name())?"nearby":"not_nearby"),bx+28,by+20,cw-34,available.contains(e.name())?GREEN:MUTED);
            if(ch>65)label(g,text("attribute."+e.id()),bx+7,by+39,cw-14,ACCENT);
            if(mx>=bx&&mx<bx+cw&&my>=by&&my<by+ch)g.renderTooltip(font,font.split(tr("help."+e.id()),Math.min(270,width-24)),mx,my);
        }
    }
    private void routines(GuiGraphics g){
        var plans=array(data,"routines");
        int left=x+12, split=routineSeparator(), ww=right()-split, top=body();
        panel(g,left,top,split-left-12,bottom()-top+4,SHEET);
        label(g,text("menu.saved_plans"),left+6,top+5,split-left-23,TEXT);
        if(plans.isEmpty()){
            label(g,text("menu.no_plan"),split,top+9,ww,LABEL);
            return;
        }
        var selectedPlan=plans.get(Math.min(selected,plans.size()-1)).getAsJsonObject();
        Workout active=ongoing();
        boolean current=active!=null&&str(selectedPlan,"id").equals(active.plan.id().toString());
        Routine plan=current?active.plan:FitnessLedger.JSON.fromJson(selectedPlan,Routine.class);
        label(g,planName(plan.name()),split,top+7,ww-28,TEXT);
        label(g,current?text("workout_progress",active.completed,active.total()):text("plan_summary",plan.steps().stream().mapToInt(Step::sets).sum(),plan.steps().size()),
            split,top+21,ww-28,LABEL);
        var art=current?active.next().exercise():plan.steps().get(0).exercise();
        g.renderItem(new ItemStack(FitnessMod.MACHINES.get(art).get()),right()-19,top+8);
        int stepTop=top+42;
        int visible=routineStepRows();
        stepScroll=pt.realmfitness.domain.FitnessMath.clamp(stepScroll,0,Math.max(0,plan.steps().size()-visible));
        for(int i=0;i<visible&&i+stepScroll<plan.steps().size();i++){
            int index=i+stepScroll, yy=stepTop+i*25;
            Step step=plan.steps().get(index);
            boolean next=current&&index==active.nextIndex();
            if(next)panel(g,split,yy,ww,24,SHEET);
            label(g,exercise(step.exercise()),split+4,yy+3,ww-29,TEXT);
            String workload=step.sets()+" × "+step.repetitions()+" · "+load(step.exercise(),next?active.load(step):step.load());
            label(g,workload,split+4,yy+14,ww-8,LABEL);
            if(next)rightAligned(g,text("menu.next"),right()-4,yy+3,ACTION);
        }
        if(current&&remainingRest()>0)
            label(g,text("menu.rest",remainingRest()),split,bottom()-30,ww,LABEL);
    }
    private void journal(GuiGraphics g){
        int by=body()+26;
        if(journal.equals("progress")){
            Set<Long> days=new HashSet<>();array(data,"days").forEach(d->days.add(d.getAsLong()));long today=LocalDate.ofInstant(Instant.ofEpochMilli(time(data,"now")),ZoneOffset.UTC).toEpochDay();int cell=Math.max(10,Math.min(25,(w-34)/14));
            label(g,text("last_28_days"),x+14,by,w-28,ACCENT);
            for(int n=0;n<28;n++){int bx=x+14+n%14*cell,yy=by+20+n/14*cell;g.fill(bx,yy,bx+cell-3,yy+cell-3,days.contains(today-27+n)?ACTION:SHEET);}
            int chartY=by+28+cell*2;label(g,text("attribute_progress"),x+14,chartY,w-28,MUTED);
            int cw=(w-28)/6,maxH=Math.max(12,bottom()-chartY-34);
            for(var e:Exercise.values()){int bh=(int)(maxH*level(e)/20.0),bx=x+14+e.ordinal()*cw;g.fill(bx,chartY+18+maxH-bh,bx+cw-6,chartY+18+maxH,ACCENT);label(g,exercise(e),bx,chartY+21+maxH,cw-5,MUTED);}
            return;
        }
        var entries=array(data,journal.equals("records")?"records":"history");int row=h<300?24:33,visible=Math.max(1,(bottom()-by-8)/row);scroll=pt.realmfitness.domain.FitnessMath.clamp(scroll,0,Math.max(0,entries.size()-visible));
        if(entries.isEmpty())label(g,text("no_history"),x+15,by+15,w-30,MUTED);
        for(int i=0;i<visible&&i+scroll<entries.size();i++){
            var r=entries.get(i+scroll).getAsJsonObject();var e=Exercise.valueOf(str(r,"exercise"));int yy=by+i*row;panel(g,x+12,yy+row-3,w-24,1,RULE);
            String first=exercise(e)+" · "+load(e,num(r,"load"))+" · "+num(r,"valid")+"/"+num(r,"repetitions");if(num(r,"assisted")>0)first+=" · "+text("spotter.count",num(r,"assisted"));label(g,first,x+19,yy+4,w-38,INK);
            String date=Instant.ofEpochMilli(time(r,"at")).atOffset(ZoneOffset.UTC).toLocalDate().toString();label(g,date+" · "+num(r,"accuracy")+"% · +"+num(r,"experience")+" XP",x+19,yy+15,w-38,MUTED);
        }
    }
    private void leaders(GuiGraphics g){
        label(g,load(exercise,load),x+43,body()+30,w/2-50,INK);label(g,text("repetitions",reps),x+w/2+35,body()+30,w/2-78,INK);
        var list=array(data,"leaders");int row=23,visible=Math.max(1,(h-153)/row);scroll=pt.realmfitness.domain.FitnessMath.clamp(scroll,0,Math.max(0,list.size()-visible));
        if(list.isEmpty())label(g,text("no_records"),x+15,body()+65,w-30,MUTED);
        for(int i=0;i<visible&&i+scroll<list.size();i++){var r=list.get(i+scroll).getAsJsonObject();int by=body()+57+i*row;label(g,(i+scroll+1)+". "+str(r,"name"),x+18,by,w/2,INK);label(g,num(r,"valid")+"/"+num(r,"repetitions")+" · "+num(r,"accuracy")+"%",x+w/2,by,w/2-15,ACCENT);}
    }
    private static String planName(String name){return name.startsWith("fitness.")?text(name):name;}
    @Override public boolean mouseScrolled(double mx,double my,double dy){
        if(dy==0||my<body()||my>=bottom())return super.mouseScrolled(mx,my,dy);
        int change=(int)Math.signum(dy);
        if(page.equals("profile")){
            if(detail!=null||mx<profileLeft()||my<attributesTop())return false;
            profileScroll=pt.realmfitness.domain.FitnessMath.clamp(profileScroll-change,0,Math.max(0,Exercise.values().length-profileRows()));
            rebuildWidgets();
        }else if(page.equals("routines")&&mx>=routineSeparator()){
            stepScroll=Math.max(0,stepScroll-change);
        }else{
            scroll=Math.max(0,scroll-change);
            if(page.equals("routines"))rebuildWidgets();
        }
        return true;
    }
    @Override public boolean isPauseScreen(){return false;}
}
