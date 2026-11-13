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

public final class AthleteScreen extends Screen {
    private JsonObject data;
    private String page="profile",journal="history";
    private int x,y,w,h,scroll,selected,ticks,load=20,reps=8;
    private Exercise exercise=Exercise.BENCH;
    private String period="all",scope="server";
    private boolean dirtySelection;
    private long received;
    public AthleteScreen(JsonObject data){super(tr("title"));accept(data);}
    public JsonObject data(){return data;}
    public void accept(JsonObject d){
        data=d;received=System.nanoTime();if(!str(d,"page").isEmpty()){page=str(d,"page");scroll=0;}
        if(!dirtySelection&&d.has("exercise")){exercise=Exercise.valueOf(str(d,"exercise"));load=num(d,"load");reps=num(d,"repetitions");period=str(d,"period");scope=str(d,"scope");}
        if(minecraft!=null)rebuildWidgets();
    }
    private void switchPage(String p){page=p;scroll=0;rebuildWidgets();}
    private net.minecraft.client.gui.components.Button button(int bx,int by,int bw,String key,Runnable a){return addRenderableWidget(FitnessTheme.button(bx,by,bw,18,tr(key),a));}
    private net.minecraft.client.gui.components.Button literal(int bx,int by,int bw,String label,Runnable a){return addRenderableWidget(FitnessTheme.button(bx,by,bw,18,Component.literal(label),a));}
    private int body(){return y+58;}
    private int bottom(){return y+h-30;}
    @Override protected void init(){
        w=Math.min(780,width-16);h=Math.min(450,height-16);x=(width-w)/2;y=(height-h)/2;
        String[] pages={"profile","routines","catalog","history","leaders"};int tw=(w-24)/5;
        for(int i=0;i<5;i++){String p=pages[i];var b=button(x+10+i*tw,y+31,tw-3,"tab."+p,()->switchPage(p));b.setTooltip(Tooltip.create(tr("tab."+p)));if(p.equals("leaders"))b.active=data.has("board")&&data.get("board").getAsBoolean();}
        button(x+w-78,bottom(),66,"close",this::onClose);
        button(x+12,bottom(),76,"refresh",()->FitnessNetwork.request("refresh",Map.of()));
        if(page.equals("routines"))routineButtons();
        if(page.equals("history")){String[] modes={"history","records","progress"};for(int i=0;i<3;i++){String mode=modes[i];button(x+12+i*(w-24)/3,body(),(w-30)/3,"tab."+mode,()->{journal=mode;scroll=0;rebuildWidgets();});}}
        if(page.equals("leaders"))leaderButtons();
    }
    private void routineButtons(){
        var routines=array(data,"routines");selected=Math.clamp(selected,0,Math.max(0,routines.size()-1));
        int left=x+12,listWidth=Math.max(80,(w-36)/3),visible=Math.max(1,(h-138)/27);scroll=Math.clamp(scroll,0,Math.max(0,routines.size()-visible));
        for(int i=0;i<visible&&i+scroll<routines.size();i++){int n=i+scroll;var r=routines.get(n).getAsJsonObject();literal(left,body()+5+i*27,listWidth,font.plainSubstrByWidth(planName(str(r,"name")),listWidth-10),()->{selected=n;rebuildWidgets();});}
        int rx=left+listWidth+10,rw=w-listWidth-36;
        if(routines.size()>0){var r=routines.get(selected).getAsJsonObject();button(rx,bottom()-25,Math.max(40,(rw-6)/2),"begin",()->FitnessNetwork.request("begin",Map.of("id",str(r,"id"))));button(rx+(rw+6)/2,bottom()-25,Math.max(40,(rw-6)/2),"edit",()->minecraft.setScreen(new RoutineEditorScreen(data,r)));}
        button(left,bottom()-25,listWidth,"new_plan",()->minecraft.setScreen(new RoutineEditorScreen(data,null)));
        if(data.has("workout"))button(x+w/2-65,bottom(),130,"end_workout",()->FitnessNetwork.request("end_workout",Map.of()));
    }
    private void leaderButtons(){
        int cw=(w-32)/3;
        button(x+12,body(),cw,"exercise."+exercise.id(),()->{exercise=Exercise.values()[(exercise.ordinal()+1)%6];load=exercise.minimumLoad();filter(false);});
        button(x+16+cw,body(),cw,"period."+period,()->{period=switch(period){case "all"->"week";case "week"->"month";default->"all";};filter(false);});
        button(x+20+cw*2,body(),cw,"scope."+scope,()->{scope=scope.equals("server")?"gym":"server";filter(false);});
        literal(x+12,body()+25,25,"−",()->{load=Math.max(exercise.minimumLoad(),load-(exercise.weighted?5:1));filter(false);});
        literal(x+110,body()+25,25,"+",()->{load=Math.min(exercise.maximumLoad(20),load+(exercise.weighted?5:1));filter(false);});
        literal(x+w/2+4,body()+25,25,"−",()->{reps=Math.max(4,reps-1);filter(false);});literal(x+w-38,body()+25,25,"+",()->{reps=Math.min(20,reps+1);filter(false);});
        var pin=button(x+w/2-64,bottom(),128,"pin_board",()->filter(true));pin.active=data.has("mayPin")&&data.get("mayPin").getAsBoolean();
    }
    private void filter(boolean pin){dirtySelection=true;FitnessNetwork.request(pin?"pin":"filter",Map.of("exercise",exercise.name(),"load",load,"repetitions",reps,"period",period,"scope",scope));}
    private int level(Exercise e){return FitnessLedger.level(data.getAsJsonObject("experience").get(e.name()).getAsInt());}
    private long serverNow(){return time(data,"now")+(System.nanoTime()-received)/1_000_000;}
    private int remainingRest(){return (int)Math.max(0,(time(data,"restUntil")-serverNow()+999)/1000);}
    @Override public void tick(){if(++ticks%100==0)FitnessNetwork.request("refresh",Map.of());}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height,0xD5100D19,0xF00B0D14);frame(g,x,y,w,h,PANEL);label(g,"REALM GYM",x+14,y+12,w/2,ACCENT);label(g,str(data,"name"),x+w/2,y+12,w/2-15,MUTED);
        switch(page){case "profile"->profile(g,mx,my);case "catalog"->catalog(g,mx,my);case "routines"->routines(g);case "leaders"->leaders(g);default->journal(g);}
        String message=str(data,"message");if(!message.isEmpty())label(g,text(message),x+96,bottom()+5,w-185,GREEN);
        super.render(g,mx,my,partial);
    }
    private void profile(GuiGraphics g,int mx,int my){
        int left=x+12,pw=Math.max(78,w*30/100),top=body()+3,ph=Math.max(42,h-150);
        frame(g,left,top,pw,ph,0xFF211A2C);g.fillGradient(left+4,top+4,left+pw-4,top+ph-4,0x10292A44,0x804D385F);
        if(minecraft.player!=null)InventoryScreen.renderEntityInInventoryFollowsMouse(g,left+3,top+3,left+pw-3,top+ph-15,Math.max(12,Math.min(pw/3,(ph-20)*9/20)),.05f,mx,my,minecraft.player);
        int average=Arrays.stream(Exercise.values()).mapToInt(this::level).sum();label(g,text("athlete_level",average),left+7,top+ph-13,pw-12,ACCENT);
        int rx=left+pw+12,rw=w-pw-38,row=Math.max(15,Math.min(42,(h-142)/6));
        for(var e:Exercise.values()){
            int yy=top+e.ordinal()*row,xp=data.getAsJsonObject("experience").get(e.name()).getAsInt(),lv=level(e);double max=data.getAsJsonObject("bonuses").get(e.name()).getAsDouble(),fatigue=data.getAsJsonObject("fatigue").get(e.name()).getAsDouble();
            g.pose().pushPose();g.pose().translate(rx,yy-3,0);g.pose().scale(.7f,.7f,1);g.renderItem(new ItemStack(FitnessMod.MACHINES.get(e).get()),0,0);g.pose().popPose();
            label(g,text("stat."+e.id())+" · "+lv+"/20",rx+15,yy,row<24?rw-64:rw-15,INK);
            String value=String.format(Locale.ROOT,"+%.1f%%",max*lv/20*100);label(g,value,rx+rw-font.width(value),yy+(row<24?0:10),font.width(value),GREEN);
            bar(g,rx+15,yy+row-5,Math.max(20,rw-70),lv==20?1:(xp-FitnessLedger.threshold(lv))/(double)(FitnessLedger.threshold(lv+1)-FitnessLedger.threshold(lv)),ACCENT);
            if(mx>=rx&&mx<rx+rw&&my>=yy&&my<yy+row)g.renderTooltip(font,List.of(tr("attribute."+e.id()),tr("xp",xp,lv==20?xp:FitnessLedger.threshold(lv+1)),tr("fatigue",(int)(fatigue*100))),Optional.empty(),mx,my);
        }
        int fy=bottom()-38;label(g,text("daily_progress",num(data,"dailySets"),num(data,"dailyGoal"),num(data,"dailyVariety"),num(data,"varietyGoal")),left,fy,w-24,ACCENT);
        bar(g,left,fy+13,w-24,num(data,"dailySets")/(double)Math.max(1,num(data,"dailyGoal")),GREEN);
        label(g,text("xp_today",num(data,"dailyXp"),num(data,"dailyXpCap")),left,fy+22,w-24,MUTED);
    }
    private void catalog(GuiGraphics g,int mx,int my){
        int cols=w<420?1:2,cw=(w-30)/cols,ch=Math.max(38,(h-100)/3),visible=Math.max(1,(h-100)/ch)*cols;
        scroll=Math.clamp(scroll,0,Math.max(0,6-visible));Set<String> available=new HashSet<>();array(data,"available").forEach(e->available.add(e.getAsString()));
        for(int i=0;i<visible&&i+scroll<6;i++){
            Exercise e=Exercise.values()[i+scroll];int bx=x+12+i%cols*(cw+6),by=body()+i/cols*ch;
            frame(g,bx,by,cw-2,ch-5,0xFF251E30);g.renderItem(new ItemStack(FitnessMod.MACHINES.get(e).get()),bx+5,by+7);
            label(g,exercise(e),bx+28,by+7,cw-34,INK);label(g,text(available.contains(e.name())?"nearby":"not_nearby"),bx+28,by+20,cw-34,available.contains(e.name())?GREEN:MUTED);
            if(ch>65)label(g,text("attribute."+e.id()),bx+7,by+39,cw-14,ACCENT);
            if(mx>=bx&&mx<bx+cw&&my>=by&&my<by+ch)g.renderTooltip(font,font.split(tr("help."+e.id()),Math.min(270,width-24)),mx,my);
        }
    }
    private void routines(GuiGraphics g){
        var list=array(data,"routines");if(list.isEmpty())return;JsonObject r=list.get(Math.min(selected,list.size()-1)).getAsJsonObject();int lw=Math.max(80,(w-36)/3),rx=x+lw+34,rw=w-lw-48,by=body()+4;
        label(g,planName(str(r,"name")),rx,by,rw,ACCENT);var steps=array(r,"steps");int row=Math.max(24,Math.min(42,(h-145)/Math.max(1,steps.size())));
        for(int i=0;i<steps.size()&&by+24+i*row<bottom()-35;i++){var s=steps.get(i).getAsJsonObject();var e=Exercise.valueOf(str(s,"exercise"));int yy=by+24+i*row;label(g,exercise(e),rx,yy,rw,INK);label(g,num(s,"sets")+" × "+num(s,"repetitions")+" · "+load(e,num(s,"load")),rx,yy+11,rw,MUTED);}
        if(data.has("workout")){var workout=FitnessLedger.JSON.fromJson(data.get("workout"),Workout.class);String next=workout.next()==null?text("workout_done"):exercise(workout.next().exercise());label(g,text("workout_progress",workout.completed,workout.total())+" · "+next,x+12,bottom()-46,w-24,GREEN);}
    }
    private void journal(GuiGraphics g){
        int by=body()+26;
        if(journal.equals("progress")){
            Set<Long> days=new HashSet<>();array(data,"days").forEach(d->days.add(d.getAsLong()));long today=LocalDate.ofInstant(Instant.ofEpochMilli(time(data,"now")),ZoneOffset.UTC).toEpochDay();int cell=Math.max(10,Math.min(25,(w-34)/14));
            label(g,text("last_28_days"),x+14,by,w-28,ACCENT);
            for(int n=0;n<28;n++){int bx=x+14+n%14*cell,yy=by+20+n/14*cell;g.fill(bx,yy,bx+cell-3,yy+cell-3,days.contains(today-27+n)?GREEN:0xFF393143);}
            int chartY=by+28+cell*2;label(g,text("attribute_progress"),x+14,chartY,w-28,MUTED);
            int cw=(w-28)/6,maxH=Math.max(12,bottom()-chartY-34);
            for(var e:Exercise.values()){int bh=(int)(maxH*level(e)/20.0),bx=x+14+e.ordinal()*cw;g.fill(bx,chartY+18+maxH-bh,bx+cw-6,chartY+18+maxH,ACCENT);label(g,exercise(e),bx,chartY+21+maxH,cw-5,MUTED);}
            return;
        }
        var entries=array(data,journal.equals("records")?"records":"history");int row=h<300?24:33,visible=Math.max(1,(bottom()-by-8)/row);scroll=Math.clamp(scroll,0,Math.max(0,entries.size()-visible));
        if(entries.isEmpty())label(g,text("no_history"),x+15,by+15,w-30,MUTED);
        for(int i=0;i<visible&&i+scroll<entries.size();i++){
            var r=entries.get(i+scroll).getAsJsonObject();var e=Exercise.valueOf(str(r,"exercise"));int yy=by+i*row;frame(g,x+12,yy,w-24,row-3,0xFF251E30);
            String first=exercise(e)+" · "+load(e,num(r,"load"))+" · "+num(r,"valid")+"/"+num(r,"repetitions");label(g,first,x+19,yy+4,w-38,INK);
            String date=Instant.ofEpochMilli(time(r,"at")).atOffset(ZoneOffset.UTC).toLocalDate().toString();label(g,date+" · "+num(r,"accuracy")+"% · +"+num(r,"experience")+" XP",x+19,yy+15,w-38,MUTED);
        }
    }
    private void leaders(GuiGraphics g){
        label(g,load(exercise,load),x+43,body()+30,w/2-50,INK);label(g,text("repetitions",reps),x+w/2+35,body()+30,w/2-78,INK);
        var list=array(data,"leaders");int row=23,visible=Math.max(1,(h-153)/row);scroll=Math.clamp(scroll,0,Math.max(0,list.size()-visible));
        if(list.isEmpty())label(g,text("no_records"),x+15,body()+65,w-30,MUTED);
        for(int i=0;i<visible&&i+scroll<list.size();i++){var r=list.get(i+scroll).getAsJsonObject();int by=body()+57+i*row;label(g,(i+scroll+1)+". "+str(r,"name"),x+18,by,w/2,INK);label(g,num(r,"valid")+"/"+num(r,"repetitions")+" · "+num(r,"accuracy")+"%",x+w/2,by,w/2-15,ACCENT);}
    }
    private static String planName(String name){return name.startsWith("fitness.")?text(name):name;}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,scroll-(int)Math.signum(dy));if(page.equals("routines"))rebuildWidgets();return true;}
    @Override public boolean isPauseScreen(){return false;}
}
