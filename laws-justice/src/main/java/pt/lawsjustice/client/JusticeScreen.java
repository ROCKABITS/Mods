package pt.lawsjustice.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

/** Physical prison ledger. This client presents server state and never decides custody or elapsed time. */
public final class JusticeScreen extends Screen {
    private enum Tab { OVERVIEW, PRISONS, CELLS, SENTENCES, HISTORY }
    private record BodyWidget(AbstractWidget widget,int y) {}
    private final List<Consumer<GuiGraphics>> paintings=new ArrayList<>();
    private final List<BodyWidget> bodyWidgets=new ArrayList<>();
    private final Map<String,EditBox> inputs=new HashMap<>();
    private final Map<String,String> drafts=new HashMap<>();
    private JsonObject data;
    private Tab tab=Tab.OVERVIEW;
    private String selectedPrison="",selectedCell="",selectedPlayer="",notice="",confirmation="";
    private int px,py,pw,ph,bodyTop,bodyBottom,scroll,contentHeight,ticks,waitTicks,listPage,historyPage;
    private boolean waiting,error;

    public JusticeScreen(JsonObject snapshot) {
        super(tr("title"));data=snapshot.deepCopy();drafts.put("capacity","1");drafts.put("minutes","30");readNotice();normalizeSelection();
    }
    public JsonObject currentState(){return data.deepCopy();}
    public static void receive(String json) {
        if(json==null||json.length()>524288)return;final JsonObject snapshot;
        try{snapshot=JsonParser.parseString(json).getAsJsonObject();}catch(RuntimeException malformed){return;}
        var mc=Minecraft.getInstance();mc.execute(()->{
            if(snapshot.has("open")&&!flag(snapshot,"open")){
                if(mc.screen instanceof JusticeScreen)mc.setScreen(null);
                if(mc.player!=null&&!str(snapshot,"error").isBlank())mc.player.displayClientMessage(Component.translatable(str(snapshot,"error")),false);
                return;
            }
            if(mc.player==null||mc.level==null)return;
            if(mc.screen instanceof JusticeScreen current)current.accept(snapshot);else mc.setScreen(new JusticeScreen(snapshot));
        });
    }
    private void accept(JsonObject snapshot){
        // The server broadcasts the custody clock every second. Keep an edit box and its
        // cursor/selection intact while typing; an action result or authority change still
        // rebuilds immediately. Every subsequent action uses the newly received version.
        boolean editing=!waiting&&inputs.values().stream().anyMatch(EditBox::isFocused)
                &&flag(snapshot,"admin")==admin()&&str(snapshot,"nonce").equals(str(data,"nonce"))
                &&str(snapshot,"error").isBlank()&&str(snapshot,"message").isBlank();
        remember();data=snapshot;waiting=false;waitTicks=0;readNotice();normalizeSelection();if(!admin())tab=Tab.OVERVIEW;if(!editing)rebuild();
    }
    private void readNotice(){notice=str(data,"error");error=!notice.isBlank();if(!error)notice=str(data,"message");}
    private boolean admin(){return flag(data,"admin");}
    private static String str(JsonObject o,String k){try{return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}catch(RuntimeException e){return "";}}
    private static long num(JsonObject o,String k){try{return o.has(k)?o.get(k).getAsLong():0;}catch(RuntimeException e){return 0;}}
    private static boolean flag(JsonObject o,String k){try{return o.has(k)&&o.get(k).getAsBoolean();}catch(RuntimeException e){return false;}}
    private static JsonObject object(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject();}
    private static JsonArray array(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private static List<JsonObject> rows(JsonArray a){List<JsonObject> result=new ArrayList<>();for(JsonElement e:a)if(e.isJsonObject())result.add(e.getAsJsonObject());return result;}
    private static Component tr(String key,Object...args){return Component.translatable("gui.lawsjustice."+key,args);}
    private static JsonObject find(JsonArray rows,String id){for(JsonElement e:rows)if(e.isJsonObject()&&id.equals(str(e.getAsJsonObject(),"id")))return e.getAsJsonObject();return new JsonObject();}
    private JsonObject prison(){return find(array(data,"prisons"),selectedPrison);}
    private JsonObject cell(){return find(array(prison(),"cells"),selectedCell);}
    private void normalizeSelection(){
        var prisons=array(data,"prisons");if(find(prisons,selectedPrison).isEmpty())selectedPrison=prisons.isEmpty()?"":str(prisons.get(0).getAsJsonObject(),"id");
        var cells=array(prison(),"cells");if(find(cells,selectedCell).isEmpty())selectedCell=cells.isEmpty()?"":str(cells.get(0).getAsJsonObject(),"id");
        var players=array(data,"players");if(find(players,selectedPlayer).isEmpty())selectedPlayer=players.isEmpty()?"":str(players.get(0).getAsJsonObject(),"id");
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial){}
    @Override public void tick(){
        ticks++;if(waiting&&++waitTicks>200){waiting=false;notice="gui.lawsjustice.connection_wait";error=true;rebuild();}
        if(ticks%80==0&&!waiting&&inputs.values().stream().noneMatch(EditBox::isFocused))request("refresh",new JsonObject());
        super.tick();
    }
    private boolean wide(){return pw>=640;}
    private boolean shortScreen(){return ph<300;}
    @Override protected void init(){
        paintings.clear();bodyWidgets.clear();inputs.clear();pw=Math.min(810,width-12);ph=Math.min(436,height-12);px=(width-pw)/2;py=(height-ph)/2;
        int tabsY=py+(shortScreen()?38:58),tabRows=admin()&&pw<460?2:1;
        if(admin()){
            int count=5,columns=tabRows==2?3:5,tw=(pw-34-(columns-1)*4)/columns;
            for(int i=0;i<count;i++){Tab value=Tab.values()[i];button(px+17+(i%columns)*(tw+4),tabsY+(i/columns)*23,tw,20,tr(value.name().toLowerCase(Locale.ROOT)),()->switchTab(value),value==tab,false);}
        }else button(px+17,tabsY,Math.min(190,pw-34),20,tr("own_status"),()->{},true,false);
        bodyTop=tabsY+tabRows*23+7;bodyBottom=py+ph-40;
        int x=px+17,y=bodyTop,w=pw-34;
        contentHeight=switch(tab){case OVERVIEW->overview(x,y,w);case PRISONS->prisons(x,y,w);case CELLS->cells(x,y,w);case SENTENCES->sentences(x,y,w);case HISTORY->history(x,y,w);}-y;
        scroll=Math.clamp(scroll,0,maximumScroll());positionWidgets();
        button(px+pw-91,py+ph-28,74,19,tr("close"),this::onClose,false,false);
        var refresh=button(px+pw-172,py+ph-28,75,19,tr("refresh"),()->request("refresh",new JsonObject()),false,false);refresh.active=!waiting;
    }
    private void switchTab(Tab next){remember();tab=next;scroll=0;confirmation="";rebuild();}
    private void remember(){inputs.forEach((key,box)->drafts.put(key,box.getValue()));}
    private void rebuild(){clearWidgets();init();}
    private String draft(String key){return drafts.getOrDefault(key,"");}
    private int overview(int x,int y,int w){
        if(wide()){
            int left=(w*3)/5;int a=status(x,y,left),b=overviewInfo(x+left+13,y,w-left-13);return Math.max(a,b);
        }
        y=status(x,y,w)+12;return overviewInfo(x,y,w);
    }
    private int status(int x,int y,int w){
        JsonObject self=object(data,"self");boolean active=!self.isEmpty();final int top=y;int h=active?270:166;
        paintings.add(g->{JusticeTheme.panel(g,x,top,w,h);JusticeTheme.scales(g,x+w/2,top+31,1);});
        centered(tr(active?"sentence_active":"no_sentence"),x+w/2,y+55,w-22,active?JusticeTheme.GOLD:JusticeTheme.GREEN,true);
        if(!active){paragraph(tr("free_hint"),x+16,y+83,w-32,JusticeTheme.MUTED);return y+h;}
        y+=79;label(Component.literal(str(self,"prisonName")+" · "+str(self,"cellName")),x+13,y,w-26,JusticeTheme.LIGHT,true);y+=19;
        long original=num(self,"originalTicks"),served=num(self,"servedTicks"),discount=num(self,"reductionTicks"),remaining=num(self,"remainingTicks");
        int by=y;paintings.add(g->{JusticeTheme.hourglass(g,x+32,by+26,original<=0?0:(double)(served+discount)/original,System.currentTimeMillis());});
        label(tr("remaining"),x+61,y+4,w-72,JusticeTheme.MUTED,false);label(duration(remaining),x+61,y+20,w-72,JusticeTheme.GOLD,true);y+=60;
        final int barY=y,barX=x+14,barW=w-28;double progress=original<=0?0:Math.clamp((double)(served+discount)/original,0,1);
        paintings.add(g->{g.fill(barX,barY,barX+barW,barY+8,0xFF0E1715);g.fill(barX+1,barY+1,barX+1+(int)((barW-2)*progress),barY+7,0xFF96A677);JusticeTheme.border(g,barX,barY,barW,8,0xFF697252);for(int xx=barX+4;xx<barX+barW-3;xx+=12)g.fill(xx,barY+3,xx+3,barY+5,0x33434E37);});y+=17;
        label(tr("original_compact",duration(original)),x+14,y,w-28,JusticeTheme.MUTED,false);y+=14;
        label(tr("served_compact",durationElapsed(served)),x+14,y,w-28,JusticeTheme.LIGHT,false);y+=14;
        label(tr("discount_compact",durationElapsed(discount)),x+14,y,w-28,JusticeTheme.MUTED,false);y+=20;
        return Math.max(top+h,paragraph(tr("online_only"),x+12,y,w-24,JusticeTheme.MUTED)+7);
    }
    private int overviewInfo(int x,int y,int w){
        label(tr(admin()?"administrator":"custody_rules"),x+3,y+3,w-6,JusticeTheme.GOLD,true);y+=24;
        if(admin()){
            final int top=y;paintings.add(g->JusticeTheme.parchment(g,x,top,w,66));
            stat(tr("registered_prisons"),""+recordCount("prisons"),x+12,y+10,w-24,JusticeTheme.INK);
            stat(tr("active_sentences"),""+recordCount("active"),x+12,y+35,w-24,JusticeTheme.INK);y+=78;
            y=paragraph(tr("admin_hint"),x+3,y,w-6,JusticeTheme.MUTED)+14;
            y=selection(x,y,w);
        }else{
            y=paragraph(tr("prisoner_hint"),x+3,y,w-6,JusticeTheme.LIGHT)+14;
            JsonObject self=object(data,"self");if(!self.isEmpty()){label(tr("reason"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=17;y=paragraph(Component.literal(str(self,"reason")),x+3,y,w-6,JusticeTheme.LIGHT)+13;}
            y=paragraph(tr("no_escape_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        }
        return y;
    }
    private int selection(int x,int y,int w){
        label(tr("selection_title"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=19;
        JsonObject selection=object(data,"selection");
        label(tr("point_a",position(selection,"a")),x+3,y,w-6,JusticeTheme.LIGHT,false);y+=15;
        label(tr("point_b",position(selection,"b")),x+3,y,w-6,JusticeTheme.LIGHT,false);y+=15;
        label(tr("anchor",position(selection,"anchor")),x+3,y,w-6,JusticeTheme.LIGHT,false);y+=17;
        if(!str(selection,"dimension").isBlank()){label(Component.literal(str(selection,"dimension")),x+3,y,w-6,JusticeTheme.MUTED,false);y+=17;}
        return paragraph(tr("selection_help"),x+3,y,w-6,JusticeTheme.MUTED)+11;
    }
    private static Component position(JsonObject data,String key){if(!data.has(key)||!data.get(key).isJsonObject())return tr("not_selected");JsonObject p=data.getAsJsonObject(key);return Component.literal(num(p,"x")+", "+num(p,"y")+", "+num(p,"z"));}
    private int prisons(int x,int y,int w){
        label(tr("prisons_title"),x+3,y+2,w-6,JusticeTheme.GOLD,true);y+=23;
        y=paragraph(tr("prisons_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        if(wide()){int lw=292,a=prisonList(x,y,lw),b=prisonEditor(x+lw+14,y,w-lw-14);return Math.max(a,b);}
        y=prisonList(x,y,w)+12;return prisonEditor(x,y,w);
    }
    private int prisonList(int x,int y,int w){
        y=serverPager("prisons",x,y,w);
        var all=rows(array(data,"prisons"));int pages=Math.max(1,(all.size()+7)/8);listPage=Math.clamp(listPage,0,pages-1);
        bodyButton(x,y,w,21,tr("new_prison"),()->{remember();selectedPrison="";selectedCell="";drafts.put("prison_name","");confirmation="";rebuild();},selectedPrison.isBlank());y+=29;
        if(all.isEmpty())return paragraph(tr("no_prisons"),x+3,y,w-6,JusticeTheme.MUTED)+10;
        for(int i=listPage*8;i<Math.min(all.size(),(listPage+1)*8);i++){
            JsonObject row=all.get(i);String id=str(row,"id");final int top=y;
            bodyButton(x,y,w,34,Component.literal(str(row,"name")),()->{remember();selectedPrison=id;selectedCell="";drafts.put("prison_name",str(row,"name"));normalizeSelection();confirmation="";rebuild();},id.equals(selectedPrison));
            y+=40;
        }
        if(pages>1)y=pager(x,y,w,listPage,pages,delta->{listPage+=delta;rebuild();});return y;
    }
    private int prisonEditor(int x,int y,int w){
        label(tr(selectedPrison.isBlank()?"create_prison":"edit_prison"),x+3,y,w-6,JusticeTheme.LIGHT,true);y+=22;
        y=field("prison_name",tr("prison_name"),x,y,w,48);y+=5;
        y=selection(x,y,w);
        y=paragraph(tr("prison_anchor_help"),x+3,y,w-6,JusticeTheme.MUTED)+11;
        var save=bodyButton(x,y,w,23,tr("save_prison"),()->{
            JsonObject p=new JsonObject();if(!selectedPrison.isBlank())p.addProperty("id",selectedPrison);p.addProperty("name",draft("prison_name"));request("save_prison",p);
        },true);save.active=!waiting;y+=31;
        if(!selectedPrison.isBlank()){
            JsonObject p=prison();y=paragraph(tr("prison_bounds",position(p,"min"),position(p,"max")),x+3,y,w-6,JusticeTheme.MUTED)+10;
            label(tr("release_at",position(p,"release")),x+3,y,w-6,JusticeTheme.MUTED,false);y+=22;
            var remove=bodyButton(x,y,w,22,tr(confirmation.equals("prison:"+selectedPrison)?"confirm_remove_prison":"remove_prison"),()->confirm("prison:"+selectedPrison,()->{var a=new JsonObject();a.addProperty("prison",selectedPrison);request("remove_prison",a);}),false);remove.active=!waiting;y+=30;
        }
        return y;
    }
    private int cells(int x,int y,int w){
        label(tr("cells_title"),x+3,y+2,w-6,JusticeTheme.GOLD,true);y+=24;
        y=serverPager("prisons",x,y,w);
        y=picker("prison_pick",array(data,"prisons"),selectedPrison,x,y,w,id->{selectedPrison=id;selectedCell="";normalizeSelection();drafts.put("cell_name","");confirmation="";});
        if(prison().isEmpty())return paragraph(tr("select_prison_first"),x+3,y+6,w-6,JusticeTheme.MUTED)+15;
        if(wide()){int lw=292;return Math.max(cellList(x,y,lw),cellEditor(x+lw+14,y,w-lw-14));}
        return cellEditor(x,cellList(x,y,w)+12,w);
    }
    private int cellList(int x,int y,int w){
        bodyButton(x,y,w,21,tr("new_cell"),()->{remember();selectedCell="";drafts.put("cell_name","");drafts.put("capacity","1");confirmation="";rebuild();},selectedCell.isBlank());y+=29;
        var cells=rows(array(prison(),"cells"));if(cells.isEmpty())return paragraph(tr("no_cells"),x+3,y,w-6,JusticeTheme.MUTED)+10;
        int pages=Math.max(1,(cells.size()+7)/8);listPage=Math.clamp(listPage,0,pages-1);
        for(int i=listPage*8;i<Math.min(cells.size(),(listPage+1)*8);i++){
            JsonObject row=cells.get(i);String id=str(row,"id");
            bodyButton(x,y,w,28,tr("cell_entry",str(row,"name"),num(row,"occupied"),num(row,"capacity")),()->{remember();selectedCell=id;drafts.put("cell_name",str(row,"name"));drafts.put("capacity",""+num(row,"capacity"));confirmation="";rebuild();},id.equals(selectedCell));y+=34;
        }
        if(pages>1)y=pager(x,y,w,listPage,pages,delta->{listPage+=delta;rebuild();});return y;
    }
    private int cellEditor(int x,int y,int w){
        label(tr(selectedCell.isBlank()?"create_cell":"edit_cell"),x+3,y,w-6,JusticeTheme.LIGHT,true);y+=22;
        y=field("cell_name",tr("cell_name"),x,y,w,48);y=field("capacity",tr("capacity"),x,y,w,2);y+=5;
        y=selection(x,y,w);y=paragraph(tr("cell_anchor_help"),x+3,y,w-6,JusticeTheme.MUTED)+11;
        var save=bodyButton(x,y,w,23,tr("save_cell"),()->{
            try{var p=new JsonObject();p.addProperty("prison",selectedPrison);if(!selectedCell.isBlank())p.addProperty("id",selectedCell);p.addProperty("name",draft("cell_name"));int capacity=Integer.parseInt(draft("capacity").strip());if(capacity<1||capacity>16)throw new IllegalArgumentException();p.addProperty("capacity",capacity);request("save_cell",p);}catch(RuntimeException invalid){localError("capacity_invalid");}
        },true);save.active=!waiting;y+=31;
        if(!selectedCell.isBlank()){
            JsonObject c=cell();y=paragraph(tr("cell_bounds",position(c,"min"),position(c,"max")),x+3,y,w-6,JusticeTheme.MUTED)+10;
            var remove=bodyButton(x,y,w,22,tr(confirmation.equals("cell:"+selectedCell)?"confirm_remove_cell":"remove_cell"),()->confirm("cell:"+selectedCell,()->{var p=new JsonObject();p.addProperty("prison",selectedPrison);p.addProperty("cell",selectedCell);request("remove_cell",p);}),false);remove.active=!waiting;y+=30;
        }
        return y;
    }
    private int sentences(int x,int y,int w){
        label(tr("sentences_title"),x+3,y+2,w-6,JusticeTheme.GOLD,true);y+=24;
        y=paragraph(tr("sentences_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        if(wide()){int lw=(w-14)/2;return Math.max(sentenceForm(x,y,lw),activeSentences(x+lw+14,y,w-lw-14));}
        return activeSentences(x,sentenceForm(x,y,w)+19,w);
    }
    private int sentenceForm(int x,int y,int w){
        label(tr("new_sentence"),x+3,y,w-6,JusticeTheme.LIGHT,true);y+=21;
        y=picker("player_pick",array(data,"players"),selectedPlayer,x,y,w,id->selectedPlayer=id);
        y=serverPager("prisons",x,y,w);
        y=picker("prison_pick",array(data,"prisons"),selectedPrison,x,y,w,id->{selectedPrison=id;selectedCell="";normalizeSelection();});
        y=picker("cell_pick",array(prison(),"cells"),selectedCell,x,y,w,id->selectedCell=id);
        y=field("minutes",tr("minutes"),x,y,w,5);y=field("reason",tr("reason"),x,y,w,300);
        y=paragraph(tr("sentence_safety"),x+3,y,w-6,JusticeTheme.MUTED)+10;
        var issue=bodyButton(x,y,w,24,tr("issue_sentence"),()->{
            try{int minutes=Integer.parseInt(draft("minutes").strip());if(minutes<1||minutes>43200)throw new IllegalArgumentException();var p=new JsonObject();p.addProperty("player",selectedPlayer);p.addProperty("prison",selectedPrison);p.addProperty("cell",selectedCell);p.addProperty("minutes",minutes);p.addProperty("reason",draft("reason"));request("sentence",p);}catch(RuntimeException invalid){localError("minutes_invalid");}
        },true);issue.active=!waiting&&!selectedPlayer.isBlank()&&!selectedPrison.isBlank()&&!selectedCell.isBlank();return y+33;
    }
    private int activeSentences(int x,int y,int w){
        label(tr("active_sentences"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=21;
        y=serverPager("active",x,y,w);
        y=field("release_reason",tr("release_reason"),x,y,w,300);y+=5;
        var active=rows(array(data,"active"));if(active.isEmpty())return paragraph(tr("no_active"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        int pages=Math.max(1,(active.size()+7)/8);listPage=Math.clamp(listPage,0,pages-1);
        for(int i=listPage*8;i<Math.min(active.size(),(listPage+1)*8);i++){
            JsonObject row=active.get(i);final int top=y;paintings.add(g->JusticeTheme.panel(g,x,top,w,98));
            label(Component.literal(str(row,"playerName")),x+10,y+10,w-20,JusticeTheme.LIGHT,true);
            label(Component.literal(str(row,"prisonName")+" · "+str(row,"cellName")),x+10,y+27,w-20,JusticeTheme.MUTED,false);
            label(tr("remaining_compact",duration(num(row,"remainingTicks"))),x+10,y+44,w-20,JusticeTheme.GOLD,false);
            String target=str(row,"player");var release=bodyButton(x+9,y+67,w-18,21,tr(confirmation.equals("release:"+target)?"confirm_release":"release"),()->confirm("release:"+target,()->{var p=new JsonObject();p.addProperty("player",target);p.addProperty("reason",draft("release_reason"));request("release",p);}),false);release.active=!waiting;y+=107;
        }
        if(pages>1)y=pager(x,y,w,listPage,pages,delta->{listPage+=delta;rebuild();});return y;
    }
    private int history(int x,int y,int w){
        label(tr("history_title"),x+3,y+2,w-6,JusticeTheme.GOLD,true);y+=24;
        y=paragraph(tr("history_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        var history=rows(array(data,"history"));if(history.isEmpty())return paragraph(tr("no_history"),x+3,y,w-6,JusticeTheme.MUTED)+10;
        y=serverPager("history",x,y,w);
        int pages=Math.max(1,(history.size()+9)/10);historyPage=Math.clamp(historyPage,0,pages-1);y=pager(x,y,w,historyPage,pages,delta->{historyPage+=delta;scroll=0;rebuild();})+7;
        for(int i=historyPage*10;i<Math.min(history.size(),(historyPage+1)*10);i++){
            JsonObject row=history.get(i);final int top=y;
            int rh=67+font.split(Component.literal(str(row,"reason")),w-26).size()*12;paintings.add(g->JusticeTheme.parchment(g,x,top,w,rh));
            label(Component.literal(str(row,"playerName")),x+11,y+10,w-22,JusticeTheme.INK,true);
            label(tr("action_"+str(row,"action").toLowerCase(Locale.ROOT)),x+11,y+26,w-22,0xFF65522E,true);
            label(tr("decision_author",str(row,"authorityName"),date(num(row,"at"))),x+11,y+41,w-22,0xFF6E6350,false);
            paragraph(Component.literal(str(row,"reason")),x+11,y+56,w-22,JusticeTheme.INK);y+=rh+11;
        }
        return y;
    }
    private static String date(long epoch){try{return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch));}catch(RuntimeException invalid){return "—";}}
    private static Component durationElapsed(long ticks){return duration(Math.max(0,ticks)/20*20);}
    private static Component duration(long ticks){long seconds=Math.max(0,(ticks+19)/20),h=seconds/3600,m=seconds/60%60,s=seconds%60;return h>0?tr("duration_hms",h,m,s):tr("duration_ms",m,s);}
    private int picker(String title,JsonArray choices,String selected,int x,int y,int w,Consumer<String> onSelect){
        label(tr(title),x+3,y,w-6,JusticeTheme.MUTED,false);y+=15;
        List<JsonObject> options=rows(choices);int current=-1;for(int i=0;i<options.size();i++)if(selected.equals(str(options.get(i),"id")))current=i;
        final int index=current;JsonObject chosen=current>=0?options.get(current):new JsonObject();String text=chosen.isEmpty()?tr("none_available").getString():str(chosen,"name");
        var prev=bodyButton(x,y,24,22,Component.literal("‹"),()->{remember();int next=Math.floorMod(index-1,options.size());onSelect.accept(str(options.get(next),"id"));confirmation="";rebuild();},false);prev.active=!waiting&&!options.isEmpty();
        var pick=bodyButton(x+28,y,w-56,22,Component.literal(text),()->{remember();int next=Math.floorMod(index+1,options.size());onSelect.accept(str(options.get(next),"id"));confirmation="";rebuild();},true);pick.active=!waiting&&!options.isEmpty();pick.setTooltip(Tooltip.create(tr("picker_hint",Math.max(0,current+1),options.size(),text)));
        var next=bodyButton(x+w-24,y,24,22,Component.literal("›"),()->{remember();int n=Math.floorMod(index+1,options.size());onSelect.accept(str(options.get(n),"id"));confirmation="";rebuild();},false);next.active=!waiting&&!options.isEmpty();return y+32;
    }
    private void confirm(String key,Runnable action){remember();if(confirmation.equals(key)){confirmation="";action.run();}else{confirmation=key;rebuild();}}
    private int recordCount(String kind){JsonObject page=object(object(data,"pages"),kind);return page.has("records")?(int)Math.max(0,num(page,"records")):array(data,kind).size();}
    private int serverPager(String kind,int x,int y,int w){
        JsonObject page=object(object(data,"pages"),kind);int index=(int)num(page,"index"),total=Math.max(1,(int)num(page,"total"));if(total<2)return y;
        var prev=bodyButton(x,y,29,20,Component.literal("‹«"),()->requestPage(kind,index-1),false);prev.active=!waiting&&index>0;
        centered(tr("server_page",index+1,total),x+w/2,y+6,w-70,JusticeTheme.GOLD,false);
        var next=bodyButton(x+w-29,y,29,20,Component.literal("»›"),()->requestPage(kind,index+1),false);next.active=!waiting&&index+1<total;
        prev.setTooltip(Tooltip.create(tr("server_page_hint")));next.setTooltip(Tooltip.create(tr("server_page_hint")));return y+29;
    }
    private void requestPage(String kind,int index){listPage=0;historyPage=0;confirmation="";JsonObject page=new JsonObject();page.addProperty("kind",kind);page.addProperty("index",index);request("page",page);}
    private int pager(int x,int y,int w,int page,int pages,Consumer<Integer> move){
        var prev=bodyButton(x,y,37,20,Component.literal("‹"),()->{remember();move.accept(-1);},false);prev.active=page>0;
        centered(tr("page",page+1,pages),x+w/2,y+6,w-88,JusticeTheme.MUTED,false);
        var next=bodyButton(x+w-37,y,37,20,Component.literal("›"),()->{remember();move.accept(1);},false);next.active=page+1<pages;return y+27;
    }
    private int field(String key,Component description,int x,int y,int w,int max){
        label(description,x+3,y,w-6,JusticeTheme.MUTED,false);y+=15;EditBox box=new EditBox(font,x+6,y+6,Math.max(20,w-12),19,description);box.setMaxLength(max);box.setValue(draft(key));box.setBordered(false);box.setTextColor(JusticeTheme.LIGHT);box.setTextColorUneditable(JusticeTheme.MUTED);box.setTooltip(Tooltip.create(description));
        final int top=y;paintings.add(g->{JusticeTheme.panel(g,x,top,w,25);g.fill(x+5,top+22,x+w-5,top+23,0xFF766A48);});inputs.put(key,box);addRenderableWidget(box);bodyWidgets.add(new BodyWidget(box,y+6));return y+33;
    }
    private void localError(String key){notice="gui.lawsjustice."+key;error=true;rebuild();}
    private void request(String operation,JsonObject payload){
        if(waiting)return;remember();if(!operation.equals("refresh")&&!operation.equals("close")&&!operation.equals("page")){payload.addProperty("nonce",str(data,"nonce"));payload.addProperty("version",num(data,"version"));}
        waiting=true;waitTicks=0;JusticeNetwork.request(operation,payload);if(!operation.equals("refresh"))rebuild();
    }
    @Override public void onClose(){JusticeClientSessions.close(data);JusticeNetwork.request("close",new JsonObject());super.onClose();}
    private void label(Component value,int x,int y,int w,int color,boolean bold){paintings.add(g->g.drawString(font,JusticeTheme.fit(font,value,Math.max(1,w),bold),x,y,color,false));}
    private void centered(Component value,int x,int y,int w,int color,boolean bold){paintings.add(g->{Component fitted=JusticeTheme.fit(font,value,Math.max(1,w),bold);g.drawString(font,fitted,x-font.width(fitted)/2,y,color,false);});}
    private int paragraph(Component value,int x,int y,int w,int color){for(var line:font.split(value,Math.max(1,w))){int yy=y;paintings.add(g->g.drawString(font,line,x,yy,color,false));y+=12;}return y;}
    private void stat(Component title,String value,int x,int y,int w,int color){label(title,x,y,w-40,color,false);label(Component.literal(value),x+w-35,y,35,color,true);}
    private JusticeButton button(int x,int y,int w,int h,Component value,Runnable action,boolean selected,boolean body){JusticeButton b=addRenderableWidget(new JusticeButton(x,y,w,h,value,()->{remember();action.run();},selected));if(body)bodyWidgets.add(new BodyWidget(b,y));return b;}
    private JusticeButton bodyButton(int x,int y,int w,int h,Component value,Runnable action,boolean selected){return button(x,y,w,h,value,action,selected,true);}
    private int maximumScroll(){return Math.max(0,contentHeight-(bodyBottom-bodyTop));}
    private void positionWidgets(){for(BodyWidget entry:bodyWidgets){AbstractWidget widget=entry.widget();widget.setY(entry.y()-scroll);widget.visible=widget.getY()>=bodyTop&&widget.getY()+widget.getHeight()<=bodyBottom;if(!widget.visible)widget.setFocused(false);}}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        if(mouseX>=px&&mouseX<=px+pw&&mouseY>=bodyTop&&mouseY<=bodyBottom){scroll=Math.clamp(scroll-(int)Math.round(vertical*27),0,maximumScroll());positionWidgets();return true;}return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==266||key==267){scroll=Math.clamp(scroll+(key==267?1:-1)*Math.max(40,bodyBottom-bodyTop-25),0,maximumScroll());positionWidgets();return true;}return super.keyPressed(key,scan,modifiers);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xA008100F);JusticeTheme.frame(g,px,py,pw,ph);
        if(!shortScreen()){JusticeTheme.scales(g,px+35,py+30,1);g.drawString(font,JusticeTheme.fit(font,tr("title"),pw-107,true),px+61,py+18,JusticeTheme.GOLD,false);g.drawString(font,JusticeTheme.fit(font,tr(admin()?"subtitle_admin":"subtitle_prisoner"),pw-81,false),px+61,py+33,JusticeTheme.MUTED,false);}
        else{JusticeTheme.scales(g,px+29,py+22,1);g.drawString(font,JusticeTheme.fit(font,tr("title"),pw-69,true),px+51,py+17,JusticeTheme.GOLD,false);}
        g.fill(px+14,bodyTop-3,px+pw-14,bodyBottom+3,0x44202C26);
        g.enableScissor(px+14,bodyTop,px+pw-14,bodyBottom);g.pose().pushPose();g.pose().translate(0,-scroll,0);for(var p:paintings)p.accept(g);g.pose().popPose();g.disableScissor();
        super.render(g,mouseX,mouseY,partial);
        if(maximumScroll()>0){int track=bodyBottom-bodyTop,thumb=Math.max(17,track*track/Math.max(track,contentHeight)),pos=scroll*(track-thumb)/Math.max(1,maximumScroll());g.fill(px+pw-12,bodyTop,px+pw-10,bodyBottom,0xFF1C241F);g.fill(px+pw-12,bodyTop+pos,px+pw-10,bodyTop+pos+thumb,JusticeTheme.GOLD);}
        int noticeW=pw-193;Component text=waiting?tr("waiting"):notice.isBlank()?tr("physical_only"):Component.translatable(notice);
        g.drawString(font,JusticeTheme.fit(font,text,noticeW,false),px+17,py+ph-22,error?JusticeTheme.RED:JusticeTheme.MUTED,false);
        if(mouseX>=px+17&&mouseX<px+17+noticeW&&mouseY>=py+ph-28&&mouseY<py+ph-8&&font.width(text)>noticeW)g.renderTooltip(font,font.split(text,Math.min(300,pw-28)),mouseX,mouseY);
    }
    private static final class JusticeButton extends AbstractButton {
        private final Runnable action;private final boolean selected;
        private JusticeButton(int x,int y,int w,int h,Component message,Runnable action,boolean selected){super(x,y,w,h,message);this.action=action;this.selected=selected;setTooltip(Tooltip.create(message));}
        @Override public void onPress(){if(active)action.run();}
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
            int x=getX(),y=getY();boolean hover=isHoveredOrFocused();int edge=!active?0xFF535A4F:selected?JusticeTheme.GOLD:hover?0xFFC5B17F:0xFF6B755F;
            g.fill(x,y,x+width,y+height,0xFF101916);g.fill(x+1,y+1,x+width-1,y+height-1,!active?0xFF27302A:hover?0xFF455547:selected?0xFF3B4837:0xFF2B3931);JusticeTheme.border(g,x,y,width,height,edge);g.fill(x+2,y+2,x+width-2,y+3,0x335B6C53);
            if(selected){JusticeTheme.diamond(g,x+6,y+height/2,1,edge);JusticeTheme.diamond(g,x+width-7,y+height/2,1,edge);}
            var font=Minecraft.getInstance().font;Component fitted=JusticeTheme.fit(font,getMessage(),Math.max(1,width-(selected?18:10)),selected);g.drawString(font,fitted,x+(width-font.width(fitted))/2,y+(height-8)/2,!active?0xFF737E6E:selected?JusticeTheme.LIGHT:JusticeTheme.MUTED,false);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
    }
}
