package pt.lawsjustice.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pt.lawsjustice.network.JusticeNetwork;

/** Physical work ledger, sealed pouch, timed production and administrator zone preparation. */
public final class WorkScreen extends Screen {
    private enum Tab { TASKS, PROGRESS, ZONES, RULES, HISTORY }
    private record BodyWidget(AbstractWidget widget,int y){}
    private final List<Consumer<GuiGraphics>> paintings=new ArrayList<>();
    private final List<BodyWidget> bodyWidgets=new ArrayList<>();
    private final Map<String,EditBox> inputs=new HashMap<>();
    private final Map<String,String> drafts=new HashMap<>();
    private JsonObject data;
    private Tab tab=Tab.TASKS;
    private String selectedZone="",selectedPrison="",zoneType="MINE",policyKind="MINING",confirmation="",notice="";
    private int px,py,pw,ph,bodyTop,bodyBottom,scroll,contentHeight,ticks,waitTicks;
    private boolean waiting,error;
    public WorkScreen(JsonObject snapshot){super(tr("title"));data=snapshot.deepCopy();if(!str(data,"station").equals("BOARD"))tab=Tab.PROGRESS;normalize();policyDraft();notice();}
    public JsonObject currentState(){return data.deepCopy();}
    public static void receive(String json){
        if(json==null||json.length()>524288)return;JsonObject snapshot;try{snapshot=JsonParser.parseString(json).getAsJsonObject();}catch(RuntimeException bad){return;}
        var mc=Minecraft.getInstance();mc.execute(()->{if(!flag(snapshot,"open")){if(mc.screen instanceof WorkScreen)mc.setScreen(null);return;}if(mc.level==null||mc.player==null)return;if(mc.screen instanceof WorkScreen current)current.accept(snapshot);else mc.setScreen(new WorkScreen(snapshot));});
    }
    private void accept(JsonObject snapshot){boolean editing=!waiting&&inputs.values().stream().anyMatch(EditBox::isFocused)&&admin()==flag(snapshot,"admin")&&str(data,"nonce").equals(str(snapshot,"nonce"))&&str(snapshot,"error").isEmpty();remember();data=snapshot;waiting=false;waitTicks=0;normalize();notice();if(!admin()&&(tab==Tab.ZONES||tab==Tab.RULES))tab=Tab.PROGRESS;if(!editing)rebuild();}
    private boolean admin(){return flag(data,"admin");}
    private JsonObject job(){return object(data,"job");}
    private JsonObject self(){return object(data,"self");}
    private JsonObject rules(){return object(data,"rules");}
    private JsonObject zone(){return find(array(data,"zones"),selectedZone);}
    private void normalize(){if(find(array(data,"zones"),selectedZone).isEmpty())selectedZone=first(array(data,"zones"));if(find(array(data,"prisons"),selectedPrison).isEmpty())selectedPrison=first(array(data,"prisons"));}
    private static String first(JsonArray array){return array.isEmpty()?"":str(array.get(0).getAsJsonObject(),"id");}
    private void notice(){notice=str(data,"error");error=!notice.isBlank();if(!error)notice=str(data,"message");}
    private static Component tr(String key,Object...args){return Component.translatable("gui.lawsjustice.work."+key,args);}
    private static String str(JsonObject o,String k){try{return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}catch(RuntimeException bad){return "";}}
    private static long num(JsonObject o,String k){try{return o.has(k)?o.get(k).getAsLong():0;}catch(RuntimeException bad){return 0;}}
    private static boolean flag(JsonObject o,String k){try{return o.has(k)&&o.get(k).getAsBoolean();}catch(RuntimeException bad){return false;}}
    private static JsonObject object(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject();}
    private static JsonArray array(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private static List<JsonObject> rows(JsonArray array){List<JsonObject> rows=new ArrayList<>();for(JsonElement e:array)if(e.isJsonObject())rows.add(e.getAsJsonObject());return rows;}
    private static JsonObject find(JsonArray rows,String id){for(JsonObject row:rows(rows))if(str(row,"id").equals(id))return row;return new JsonObject();}
    private static Component kind(String key){return tr("kind_"+key.toLowerCase(Locale.ROOT));}
    private static Component type(String key){return tr("zone_"+key.toLowerCase(Locale.ROOT));}
    private static String zoneFor(String kind){return switch(kind){case "MINING"->"MINE";case "FARMING"->"FARM";case "CARPENTRY","SMITHING"->"WORKSHOP";case "CLEANING"->"YARD";case "TRANSPORT"->"WAREHOUSE";default->"";};}
    private void remember(){inputs.forEach((key,box)->drafts.put(key,box.getValue()));}
    private String draft(String key){return drafts.getOrDefault(key,"");}
    private void policyDraft(){for(JsonObject r:rows(array(rules(),"tasks")))if(str(r,"kind").equals(policyKind)){drafts.put("quantity",Long.toString(num(r,"quantity")));drafts.put("minutes",Long.toString(Math.max(1,num(r,"reductionTicks")/1200)));}drafts.put("percent",Long.toString(num(rules(),"maximumPercent")));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void tick(){ticks++;if(waiting&&++waitTicks>200){waiting=false;notice="gui.lawsjustice.connection_wait";error=true;rebuild();}if(ticks%100==0&&!waiting&&inputs.values().stream().noneMatch(EditBox::isFocused))request("work_refresh",new JsonObject());super.tick();}
    @Override protected void init(){
        paintings.clear();bodyWidgets.clear();inputs.clear();pw=Math.min(810,width-12);ph=Math.min(436,height-12);px=(width-pw)/2;py=(height-ph)/2;int ty=py+(ph<300?38:58);
        List<Tab> tabs=admin()?List.of(Tab.TASKS,Tab.PROGRESS,Tab.ZONES,Tab.RULES,Tab.HISTORY):List.of(Tab.TASKS,Tab.PROGRESS,Tab.HISTORY);int tw=(pw-34-(tabs.size()-1)*4)/tabs.size();
        for(int i=0;i<tabs.size();i++){Tab t=tabs.get(i);button(px+17+i*(tw+4),ty,tw,20,tr(t.name().toLowerCase(Locale.ROOT)),()->{tab=t;scroll=0;confirmation="";rebuild();},t==tab,false);}
        bodyTop=ty+28;bodyBottom=py+ph-40;int x=px+17,y=bodyTop,w=pw-34;
        contentHeight=(switch(tab){case TASKS->tasks(x,y,w);case PROGRESS->progress(x,y,w);case ZONES->zones(x,y,w);case RULES->policy(x,y,w);case HISTORY->history(x,y,w);})-y;
        scroll=Math.clamp(scroll,0,maximumScroll());positionWidgets();button(px+pw-91,py+ph-28,74,19,Component.translatable("gui.lawsjustice.close"),this::onClose,false,false);
        button(px+pw-172,py+ph-28,75,19,Component.translatable("gui.lawsjustice.refresh"),()->request("work_refresh",new JsonObject()),false,false);
    }
    private void rebuild(){clearWidgets();init();}
    private int tasks(int x,int y,int w){
        y=paragraph(tr("tasks_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;y=pager(x,y,w);List<JsonObject> zones=rows(array(data,"zones"));
        if(zones.isEmpty())return paragraph(tr("no_zones"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        if(w>=640){int lw=280;return Math.max(zoneList(x,y,lw),taskList(x+lw+14,y,w-lw-14));}return taskList(x,zoneList(x,y,w)+15,w);
    }
    private int zoneList(int x,int y,int w){label(tr("available_zones"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=22;
        for(JsonObject z:rows(array(data,"zones"))){String id=str(z,"id");bodyButton(x,y,w,31,Component.literal(str(z,"name")),()->{selectedZone=id;confirmation="";rebuild();},id.equals(selectedZone));y+=38;}
        return y;
    }
    private int taskList(int x,int y,int w){JsonObject zone=zone();if(zone.isEmpty())return y;label(type(str(zone,"type")),x+3,y,w-6,JusticeTheme.GOLD,true);y+=24;
        for(JsonObject rule:rows(array(rules(),"tasks"))){String kind=str(rule,"kind");if(!zoneFor(kind).equals(str(zone,"type")))continue;final int top=y;paintings.add(g->JusticeTheme.panel(g,x,top,w,76));label(kind(kind),x+12,y+9,w-24,JusticeTheme.LIGHT,true);label(tr("objective",num(rule,"quantity")),x+12,y+26,w-24,JusticeTheme.MUTED,false);
            label(tr("reward",time(num(rule,"reductionTicks"),true)),x+12,y+42,w-24,JusticeTheme.GREEN,false);
            var accept=bodyButton(x+8,y+78,w-16,24,tr("accept"),()->{var p=new JsonObject();p.addProperty("zone",selectedZone);p.addProperty("kind",kind);request("work_accept",p);},true);accept.active=!waiting&&flag(data,"canWork")&&job().isEmpty()&&str(data,"station").equals("BOARD");y+=113;
            y=paragraph(tr("hint_"+kind.toLowerCase(Locale.ROOT)),x+4,y,w-8,JusticeTheme.MUTED)+17;
        }
        return paragraph(tr("cap_hint",num(rules(),"maximumPercent")),x+3,y,w-6,JusticeTheme.GOLD)+12;
    }
    private int progress(int x,int y,int w){
        if(self().isEmpty())return paragraph(tr("no_sentence"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        JsonObject self=self();int cw=(w-9)/2;stat(x,y,cw,tr("original"),time(num(self,"originalTicks"),true));stat(x+cw+9,y,w-cw-9,tr("served"),time(num(self,"servedTicks"),false));y+=56;
        stat(x,y,cw,tr("reduced"),time(num(self,"reductionTicks"),true));stat(x+cw+9,y,w-cw-9,tr("remaining"),time(Math.max(0,num(self,"originalTicks")-num(self,"servedTicks")-num(self,"reductionTicks")),true));y+=69;
        JsonObject j=job();if(j.isEmpty())return paragraph(tr("no_job"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        label(kind(str(j,"kind")),x+3,y,w-6,JusticeTheme.GOLD,true);y+=23;long goal=num(j,"quantity"),output=num(j,"output");bar(x,y,w,(double)output/Math.max(1,goal),tr("job_progress",output,goal));y+=40;
        if(num(j,"raw")>0||num(j,"fuel")>0){y=paragraph(tr("raw_supplies",num(j,"raw"),num(j,"fuel")),x+3,y,w-6,JusticeTheme.MUTED)+12;}
        y=paragraph(tr("sealed_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        if(num(data,"processingTotal")>0){bar(x,y,w,1-(double)num(data,"processingRemaining")/num(data,"processingTotal"),tr("producing",time(num(data,"processingRemaining"),true)));y+=40;}
        String station=str(data,"station");
        if(station.equals("WORKSHOP")){var produce=bodyButton(x,y,w,25,tr("produce"),()->request("work_produce",new JsonObject()),true);produce.active=!waiting&&num(data,"processingRemaining")==0&&num(j,"raw")>0&&output<goal;y+=34;}
        if(station.equals("WAREHOUSE")){var cargo=bodyButton(x,y,w,25,tr(array(j,"route").isEmpty()?"pickup":"unload"),()->request("work_cargo",new JsonObject()),true);cargo.active=!waiting&&str(j,"kind").equals("TRANSPORT")&&array(j,"route").size()<2;y+=34;}
        if(station.equals("DELIVERY")){var deliver=bodyButton(x,y,w,27,tr("deliver"),()->request("work_deliver",new JsonObject()),true);deliver.active=!waiting&&output==goal&&flag(data,"bundle");y+=36;}
        else y=paragraph(tr("find_delivery"),x+3,y,w-6,JusticeTheme.GOLD)+12;
        var recover=bodyButton(x,y,w,23,tr("recover"),()->request("work_recover",new JsonObject()),false);recover.active=!waiting&&!flag(data,"bundle");y+=32;
        var cancel=bodyButton(x,y,w,23,tr(confirmation.equals("cancel")?"confirm_cancel":"cancel"),()->{if(confirmation.equals("cancel")){confirmation="";request("work_cancel",new JsonObject());}else{confirmation="cancel";rebuild();}},false);cancel.active=!waiting;y+=36;
        if(!array(data,"hints").isEmpty()){label(tr("resource_hints"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=21;for(JsonObject n:rows(array(data,"hints"))){JsonObject at=object(n,"position");label(Component.literal(num(at,"x")+", "+num(at,"y")+", "+num(at,"z")+" · "+tr("resource_"+str(n,"resource").toLowerCase(Locale.ROOT)).getString()),x+3,y,w-6,JusticeTheme.MUTED,false);y+=17;}}
        return y+12;
    }
    private void stat(int x,int y,int w,Component title,String value){paintings.add(g->JusticeTheme.panel(g,x,y,w,47));label(title,x+10,y+8,w-20,JusticeTheme.MUTED,false);label(Component.literal(value),x+10,y+26,w-20,JusticeTheme.GOLD,true);}
    private void bar(int x,int y,int w,double fraction,Component text){double f=Math.clamp(fraction,0,1);paintings.add(g->{JusticeTheme.panel(g,x,y,w,28);g.fill(x+3,y+3,x+3+(int)((w-6)*f),y+25,0xFF3F5D44);g.fill(x+4,y+4,x+3+(int)((w-6)*f),y+5,0xFF8C9961);});centered(text,x+w/2,y+10,w-20,JusticeTheme.LIGHT,true);}
    private int zones(int x,int y,int w){
        y=paragraph(tr("zone_setup_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        y=picker("prison",array(data,"prisons"),selectedPrison,x,y,w,id->selectedPrison=id);y=field("zone_name",tr("zone_name"),x,y,w,48);
        y=cycle(tr("zone_type"),List.of("MINE","FARM","WORKSHOP","WAREHOUSE","YARD"),zoneType,x,y,w,id->{zoneType=id;rebuild();},true);
        JsonObject selection=object(data,"selection");if(selection.isEmpty())y=paragraph(tr("select_corners"),x+3,y,w-6,JusticeTheme.RED)+12;else{y=paragraph(tr("selection",coordinates(object(selection,"min")),coordinates(object(selection,"max"))),x+3,y,w-6,JusticeTheme.GREEN)+12;}
        var save=bodyButton(x,y,w,25,tr("create_zone"),()->{var p=new JsonObject();p.addProperty("prison",selectedPrison);p.addProperty("name",draft("zone_name"));p.addProperty("type",zoneType);request("work_zone",p);},true);save.active=!waiting&&!selectedPrison.isBlank()&&!selection.isEmpty()&&str(data,"station").equals("BOARD");y+=39;y=pager(x,y,w);
        y=picker("selected_zone",array(data,"zones"),selectedZone,x,y,w,id->selectedZone=id);if(!zone().isEmpty()){y=paragraph(tr("resources",num(zone(),"readyNodes"),num(zone(),"spentNodes")),x+3,y,w-6,JusticeTheme.MUTED)+12;}
        var commission=bodyButton(x,y,w,25,tr("commission"),()->{var p=new JsonObject();p.addProperty("zone",selectedZone);request("work_commission",p);},true);commission.active=!waiting&&!selectedZone.isBlank()&&str(data,"station").equals("BOARD");y+=36;return paragraph(tr("renewal_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;
    }
    private int policy(int x,int y,int w){
        y=paragraph(tr("policy_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;y=cycle(tr("policy_kind"),List.of("MINING","FARMING","CARPENTRY","CLEANING","SMITHING","TRANSPORT"),policyKind,x,y,w,id->{policyKind=id;policyDraft();rebuild();},false);
        y=field("quantity",tr("quantity"),x,y,w,4);y=field("minutes",tr("minutes"),x,y,w,5);y=field("percent",tr("percent"),x,y,w,3);
        var save=bodyButton(x,y,w,25,tr("save_policy"),()->{try{var p=new JsonObject();p.addProperty("kind",policyKind);p.addProperty("quantity",Long.parseLong(draft("quantity").strip()));p.addProperty("minutes",Long.parseLong(draft("minutes").strip()));p.addProperty("percent",Long.parseLong(draft("percent").strip()));request("work_policy",p);}catch(NumberFormatException invalid){notice="gui.lawsjustice.work.invalid_numbers";error=true;rebuild();}},true);save.active=!waiting&&str(data,"station").equals("BOARD");return y+37;
    }
    private int history(int x,int y,int w){
        y=paragraph(tr("history_hint"),x+3,y,w-6,JusticeTheme.MUTED)+15;if(array(data,"history").isEmpty())return paragraph(tr("no_history"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        for(JsonObject j:rows(array(data,"history"))){final int top=y;paintings.add(g->JusticeTheme.parchment(g,x,top,w,67));label(kind(str(j,"kind")),x+12,y+10,w-24,JusticeTheme.INK,true);label(tr("status_"+str(j,"status").toLowerCase(Locale.ROOT)),x+12,y+28,w-24,0xFF765B41,false);label(tr("awarded",time(num(j,"awardedTicks"),true)),x+12,y+45,w-24,JusticeTheme.INK,false);y+=78;}return y;
    }
    private static String coordinates(JsonObject p){return num(p,"x")+", "+num(p,"y")+", "+num(p,"z");}
    private static String time(long ticks,boolean ceil){long seconds=Math.max(0,(ticks+(ceil?19:0))/20);return String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);}
    private int pager(int x,int y,int w){JsonObject p=object(data,"pages");int index=(int)num(p,"index"),total=(int)num(p,"total");if(total<=1)return y;
        var previous=bodyButton(x,y,29,20,Component.literal("‹"),()->page(index-1),false);previous.active=!waiting&&index>0;centered(tr("page",index+1,total),x+w/2,y+6,w-70,JusticeTheme.GOLD,false);var next=bodyButton(x+w-29,y,29,20,Component.literal("›"),()->page(index+1),false);next.active=!waiting&&index+1<total;return y+28;
    }
    private void page(int index){var p=new JsonObject();p.addProperty("index",index);request("work_page",p);}
    private int picker(String key,JsonArray choices,String selected,int x,int y,int w,Consumer<String> select){List<String> ids=new ArrayList<>();for(JsonObject row:rows(choices))ids.add(str(row,"id"));label(tr(key),x+3,y,w-6,JusticeTheme.MUTED,false);y+=15;int at=ids.indexOf(selected);String name=str(find(choices,selected),"name");final int index=at;
        var prev=bodyButton(x,y,24,23,Component.literal("‹"),()->{select.accept(ids.get(Math.floorMod(index-1,ids.size())));rebuild();},false);prev.active=!waiting&&!ids.isEmpty();var mid=bodyButton(x+28,y,w-56,23,Component.literal(name.isEmpty()?tr("none").getString():name),()->{select.accept(ids.get(Math.floorMod(index+1,ids.size())));rebuild();},true);mid.active=!waiting&&!ids.isEmpty();var next=bodyButton(x+w-24,y,24,23,Component.literal("›"),()->{select.accept(ids.get(Math.floorMod(index+1,ids.size())));rebuild();},false);next.active=!waiting&&!ids.isEmpty();return y+33;
    }
    private int cycle(Component title,List<String> choices,String selected,int x,int y,int w,Consumer<String> select,boolean zone){int current=choices.indexOf(selected);label(title,x+3,y,w-6,JusticeTheme.MUTED,false);y+=15;bodyButton(x,y,w,24,zone?type(selected):kind(selected),()->select.accept(choices.get(Math.floorMod(current+1,choices.size()))),true);return y+34;}
    private int field(String key,Component title,int x,int y,int w,int max){label(title,x+3,y,w-6,JusticeTheme.MUTED,false);y+=15;final int top=y;paintings.add(g->JusticeTheme.panel(g,x,top,w,27));EditBox box=new EditBox(font,x+7,y+7,w-14,18,title);box.setMaxLength(max);box.setValue(draft(key));box.setBordered(false);box.setTextColor(JusticeTheme.LIGHT);inputs.put(key,box);addRenderableWidget(box);bodyWidgets.add(new BodyWidget(box,y+7));return y+37;}
    private void request(String op,JsonObject payload){if(waiting)return;remember();if(!List.of("work_refresh","work_page","work_close").contains(op)){payload.addProperty("nonce",str(data,"nonce"));payload.addProperty("version",num(data,"version"));}waiting=true;waitTicks=0;JusticeNetwork.request(op,payload);if(!op.equals("work_refresh"))rebuild();}
    @Override public void onClose(){JusticeClientSessions.close(data);JusticeNetwork.request("work_close",new JsonObject());super.onClose();}
    private void label(Component text,int x,int y,int w,int color,boolean bold){paintings.add(g->g.drawString(font,JusticeTheme.fit(font,text,Math.max(1,w),bold),x,y,color,false));}
    private void centered(Component text,int x,int y,int w,int color,boolean bold){paintings.add(g->{Component fitted=JusticeTheme.fit(font,text,Math.max(1,w),bold);g.drawString(font,fitted,x-font.width(fitted)/2,y,color,false);});}
    private int paragraph(Component text,int x,int y,int w,int color){for(var line:font.split(text,Math.max(1,w))){int yy=y;paintings.add(g->g.drawString(font,line,x,yy,color,false));y+=12;}return y;}
    private WorkButton button(int x,int y,int w,int h,Component text,Runnable action,boolean selected,boolean body){var button=addRenderableWidget(new WorkButton(x,y,w,h,text,()->{remember();action.run();},selected));if(body)bodyWidgets.add(new BodyWidget(button,y));return button;}
    private WorkButton bodyButton(int x,int y,int w,int h,Component text,Runnable action,boolean selected){return button(x,y,w,h,text,action,selected,true);}
    private int maximumScroll(){return Math.max(0,contentHeight-(bodyBottom-bodyTop));}
    private void positionWidgets(){for(BodyWidget e:bodyWidgets){var w=e.widget();w.setY(e.y()-scroll);w.visible=w.getY()>=bodyTop&&w.getY()+w.getHeight()<=bodyBottom;if(!w.visible)w.setFocused(false);}}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(x>=px&&x<px+pw&&y>=bodyTop&&y<bodyBottom){scroll=Math.clamp(scroll-(int)Math.round(v*27),0,maximumScroll());positionWidgets();return true;}return super.mouseScrolled(x,y,h,v);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==266||key==267)&&inputs.values().stream().noneMatch(EditBox::isFocused)){scroll=Math.clamp(scroll+(key==267?1:-1)*Math.max(40,bodyBottom-bodyTop-25),0,maximumScroll());positionWidgets();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xA00B1210);JusticeTheme.frame(g,px,py,pw,ph);JusticeTheme.scales(g,px+33,py+(ph<300?23:30),1);g.drawString(font,JusticeTheme.fit(font,tr("title"),pw-90,true),px+58,py+18,JusticeTheme.GOLD,false);if(ph>=300)g.drawString(font,JusticeTheme.fit(font,tr("subtitle"),pw-90,false),px+58,py+33,JusticeTheme.MUTED,false);
        g.enableScissor(px+14,bodyTop,px+pw-14,bodyBottom);g.pose().pushPose();g.pose().translate(0,-scroll,0);for(var painting:paintings)painting.accept(g);g.pose().popPose();g.disableScissor();super.render(g,mx,my,partial);
        if(maximumScroll()>0){int track=bodyBottom-bodyTop,thumb=Math.max(17,track*track/Math.max(track,contentHeight)),at=scroll*(track-thumb)/Math.max(1,maximumScroll());g.fill(px+pw-12,bodyTop,px+pw-10,bodyBottom,0xFF171D19);g.fill(px+pw-12,bodyTop+at,px+pw-10,bodyTop+at+thumb,JusticeTheme.GOLD);}
        Component footer=waiting?tr("waiting"):notice.isBlank()?tr("physical_only"):Component.translatable(notice);g.drawString(font,JusticeTheme.fit(font,footer,pw-193,false),px+17,py+ph-22,error?JusticeTheme.RED:JusticeTheme.MUTED,false);
    }
    private static final class WorkButton extends AbstractButton {
        private final Runnable action;private final boolean selected;
        WorkButton(int x,int y,int w,int h,Component text,Runnable action,boolean selected){super(x,y,w,h,text);this.action=action;this.selected=selected;setTooltip(Tooltip.create(text));}
        @Override public void onPress(){if(active)action.run();}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){int x=getX(),y=getY(),edge=!active?0xFF52594F:selected?JusticeTheme.GOLD:isHoveredOrFocused()?0xFFD7C18A:0xFF6E7866;g.fill(x,y,x+width,y+height,!active?0xFF242C27:isHoveredOrFocused()?0xFF435842:selected?0xFF354937:0xFF29372F);JusticeTheme.border(g,x,y,width,height,edge);g.fill(x+2,y+2,x+width-2,y+3,0x445C7456);if(selected){JusticeTheme.diamond(g,x+6,y+height/2,1,edge);JusticeTheme.diamond(g,x+width-7,y+height/2,1,edge);}var font=Minecraft.getInstance().font;Component text=JusticeTheme.fit(font,getMessage(),Math.max(1,width-18),selected);g.drawString(font,text,x+(width-font.width(text))/2,y+(height-8)/2,!active?0xFF7F887A:JusticeTheme.LIGHT,false);}
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
    }
}
