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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pt.lawsjustice.network.JusticeNetwork;

/** The physical royal court and sealed, read-only warrant document. All authority is server supplied. */
public final class CourtScreen extends Screen {
    private enum Tab { CASES, EVIDENCE, JUDGMENT, WARRANTS }
    private record BodyWidget(AbstractWidget widget,int y) {}
    private final List<Consumer<GuiGraphics>> paintings=new ArrayList<>();
    private final List<BodyWidget> bodyWidgets=new ArrayList<>();
    private final Map<String,EditBox> inputs=new HashMap<>();
    private final Map<String,MultiLineEditBox> multiline=new HashMap<>();
    private final Map<String,String> drafts=new HashMap<>();
    private JsonObject data;
    private Tab tab=Tab.CASES;
    private String selectedCase="",selectedWarrant="",selectedPlayer="",selectedPrison="",selectedCell="",search="",confirmation="",notice="";
    private int px,py,pw,ph,bodyTop,bodyBottom,scroll,contentHeight,ticks,waitTicks;
    private boolean waiting,error;

    public CourtScreen(JsonObject snapshot){super(tr("title"));data=snapshot.deepCopy();drafts.put("minutes","30");drafts.put("hours","24");notice();normalize();}
    public JsonObject currentState(){return data.deepCopy();}
    public static void receive(String json){
        if(json==null||json.length()>524288)return;final JsonObject snapshot;
        try{snapshot=JsonParser.parseString(json).getAsJsonObject();}catch(RuntimeException malformed){return;}
        var mc=Minecraft.getInstance();mc.execute(()->{
            if(snapshot.has("open")&&!flag(snapshot,"open")){
                if(mc.screen instanceof CourtScreen)mc.setScreen(null);
                if(mc.player!=null&&!str(snapshot,"error").isBlank())mc.player.displayClientMessage(Component.translatable(str(snapshot,"error")),false);return;
            }
            if(mc.player==null||mc.level==null)return;
            if(mc.screen instanceof CourtScreen current&&str(current.data,"kind").equals(str(snapshot,"kind")))current.accept(snapshot);
            else mc.setScreen(new CourtScreen(snapshot));
        });
    }
    private void accept(JsonObject snapshot){
        boolean editing=!waiting&&editing()&&flag(snapshot,"judge")==judge()&&flag(snapshot,"admin")==admin()
                &&str(snapshot,"nonce").equals(str(data,"nonce"))&&str(snapshot,"error").isBlank()&&str(snapshot,"message").isBlank();
        remember();data=snapshot;waiting=false;waitTicks=0;notice();normalize();if(!judge()&&tab==Tab.JUDGMENT)tab=Tab.CASES;if(!editing)rebuild();
    }
    private boolean editing(){return inputs.values().stream().anyMatch(EditBox::isFocused)||multiline.values().stream().anyMatch(MultiLineEditBox::isFocused);}
    private boolean document(){return str(data,"kind").equals("warrant");}
    private boolean judge(){return flag(data,"judge");}
    private boolean admin(){return flag(data,"admin");}
    private boolean emergency(){return str(data,"authorityKind").equals("ADMIN_EMERGENCY");}
    private void notice(){notice=str(data,"error");error=!notice.isBlank();if(!error)notice=str(data,"message");if(notice.isBlank())notice=str(data,"warning");}
    private static Component tr(String key,Object...args){return Component.translatable("gui.lawsjustice.court."+key,args);}
    private static String str(JsonObject o,String k){try{return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}catch(RuntimeException e){return "";}}
    private static long num(JsonObject o,String k){try{return o.has(k)?o.get(k).getAsLong():0;}catch(RuntimeException e){return 0;}}
    private static boolean flag(JsonObject o,String k){try{return o.has(k)&&o.get(k).getAsBoolean();}catch(RuntimeException e){return false;}}
    private static JsonObject object(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject();}
    private static JsonArray array(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private static List<JsonObject> rows(JsonArray array){List<JsonObject> result=new ArrayList<>();for(JsonElement e:array)if(e.isJsonObject())result.add(e.getAsJsonObject());return result;}
    private static JsonObject find(JsonArray choices,String id){for(JsonObject e:rows(choices))if(str(e,"id").equals(id))return e;return new JsonObject();}
    private JsonObject caseFile(){return find(array(data,"cases"),selectedCase);}
    private JsonObject prison(){return find(array(data,"prisons"),selectedPrison);}
    private JsonObject warrant(){return find(array(data,"warrants"),selectedWarrant);}
    private JsonArray accusedOptions(){
        Map<String,String> names=new LinkedHashMap<>();for(JsonObject p:rows(array(data,"players")))names.put(str(p,"id"),str(p,"name"));
        for(JsonObject c:rows(array(data,"cases")))names.putIfAbsent(str(c,"accused"),str(c,"accusedName"));
        JsonArray result=new JsonArray();names.forEach((id,name)->{JsonObject p=new JsonObject();p.addProperty("id",id);p.addProperty("name",name);result.add(p);});return result;
    }
    private void normalize(){
        if(find(array(data,"cases"),selectedCase).isEmpty())selectedCase=first(array(data,"cases"));
        if(find(array(data,"warrants"),selectedWarrant).isEmpty())selectedWarrant=first(array(data,"warrants"));
        if(find(accusedOptions(),selectedPlayer).isEmpty())selectedPlayer=first(accusedOptions());
        if(find(array(data,"prisons"),selectedPrison).isEmpty())selectedPrison=first(array(data,"prisons"));
        if(find(array(prison(),"cells"),selectedCell).isEmpty())selectedCell=first(array(prison(),"cells"));
    }
    private static String first(JsonArray array){return array.isEmpty()?"":str(array.get(0).getAsJsonObject(),"id");}
    private void remember(){inputs.forEach((key,box)->drafts.put(key,box.getValue()));multiline.forEach((key,box)->drafts.put(key,box.getValue()));}
    private String draft(String key){return drafts.getOrDefault(key,"");}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial){}
    @Override public void tick(){
        ticks++;if(waiting&&++waitTicks>200){waiting=false;error=true;notice="gui.lawsjustice.connection_wait";rebuild();}
        if(!document()&&ticks%100==0&&!waiting&&!editing())request("court_refresh",new JsonObject());super.tick();
    }
    private boolean wide(){return pw>=640;}
    private boolean shortScreen(){return ph<300;}
    @Override protected void init(){
        paintings.clear();bodyWidgets.clear();inputs.clear();multiline.clear();pw=Math.min(810,width-12);ph=Math.min(436,height-12);px=(width-pw)/2;py=(height-ph)/2;
        int tabsY=py+(shortScreen()?38:58);
        if(!document()){
            List<Tab> tabs=judge()?List.of(Tab.CASES,Tab.EVIDENCE,Tab.JUDGMENT,Tab.WARRANTS):List.of(Tab.CASES,Tab.EVIDENCE,Tab.WARRANTS);
            int tw=(pw-34-(tabs.size()-1)*4)/tabs.size();for(int i=0;i<tabs.size();i++){Tab value=tabs.get(i);button(px+17+i*(tw+4),tabsY,tw,20,tr(value.name().toLowerCase(Locale.ROOT)),()->{tab=value;scroll=0;confirmation="";rebuild();},value==tab,false);}
        }
        bodyTop=document()?tabsY:tabsY+28;bodyBottom=py+ph-40;int x=px+17,y=bodyTop,w=pw-34;
        contentHeight=(document()?document(x,y,w):switch(tab){case CASES->cases(x,y,w);case EVIDENCE->evidence(x,y,w);case JUDGMENT->judgment(x,y,w);case WARRANTS->warrants(x,y,w);})-y;
        scroll=Math.clamp(scroll,0,maximumScroll());positionWidgets();
        button(px+pw-91,py+ph-28,74,19,Component.translatable("gui.lawsjustice.close"),this::onClose,false,false);
        if(!document()){var refresh=button(px+pw-172,py+ph-28,75,19,Component.translatable("gui.lawsjustice.refresh"),()->request("court_refresh",new JsonObject()),false,false);refresh.active=!waiting;}
    }
    private void rebuild(){clearWidgets();init();}
    private int authority(int x,int y,int w){
        final int top=y;int h=38;paintings.add(g->JusticeTheme.panel(g,x,top,w,h));
        Component role=emergency()?tr("authority_emergency"):flag(data,"president")?tr("authority_president",str(data,"presidentName")):tr("authority_citizen");
        label(role,x+11,y+9,w-22,emergency()?JusticeTheme.RED:JusticeTheme.GOLD,true);
        label(tr(emergency()?"emergency_audited":judge()?"president_live":"citizen_rights"),x+11,y+23,w-22,JusticeTheme.MUTED,false);return y+h+12;
    }
    private int cases(int x,int y,int w){
        y=authority(x,y,w);
        if(wide()){int lw=308;return Math.max(caseList(x,y,lw),fileForm(x+lw+14,y,w-lw-14));}
        return fileForm(x,caseList(x,y,w)+14,w);
    }
    private int caseList(int x,int y,int w){
        label(tr("case_register"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=21;
        int fy=y;field("search",tr("search"),x,y,w-67,60);var b=bodyButton(x+w-61,y+15,61,25,tr("search_apply"),()->{search=draft("search").strip().toLowerCase(Locale.ROOT);rebuild();},false);b.active=!waiting;y+=51;
        y=serverPager("cases",x,y,w);List<JsonObject> cases=rows(array(data,"cases"));int visible=0;
        for(JsonObject c:cases){if(!search.isBlank()&&!(str(c,"accusedName")+" "+str(c,"complainantName")+" "+str(c,"accusation")).toLowerCase(Locale.ROOT).contains(search))continue;
            String id=str(c,"id");Component text=tr("case_entry",str(c,"accusedName"),tr("status_"+str(c,"status").toLowerCase(Locale.ROOT)));
            bodyButton(x,y,w,32,text,()->{selectedCase=id;confirmation="";rebuild();},id.equals(selectedCase));y+=39;visible++;
        }
        if(visible==0)y=paragraph(tr("no_cases"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        if(!caseFile().isEmpty())y=caseSummary(x,y+5,w,caseFile());return y;
    }
    private int fileForm(int x,int y,int w){
        label(tr("file_title"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=22;y=paragraph(tr("file_hint"),x+3,y,w-6,JusticeTheme.MUTED)+10;
        y=picker("accused",accusedOptions(),selectedPlayer,x,y,w,id->selectedPlayer=id);
        y=multifield("accusation",tr("accusation"),x,y,w,800,76);
        var file=bodyButton(x,y,w,24,tr("file"),()->{JsonObject p=new JsonObject();p.addProperty("accused",selectedPlayer);p.addProperty("accusation",draft("accusation"));request("court_file",p);},true);file.active=!waiting&&!selectedPlayer.isBlank();return y+33;
    }
    private int caseSummary(int x,int y,int w,JsonObject c){
        int top=y;List<net.minecraft.util.FormattedCharSequence> lines=font.split(Component.literal(str(c,"accusation")),Math.max(1,w-26));int height=79+lines.size()*12;
        paintings.add(g->JusticeTheme.parchment(g,x,top,w,height));label(Component.literal(str(c,"accusedName")),x+12,y+10,w-24,JusticeTheme.INK,true);
        label(tr("complainant",str(c,"complainantName")),x+12,y+26,w-24,0xFF736144,false);label(tr("filed_at",date(num(c,"openedAt"))),x+12,y+41,w-24,0xFF736144,false);
        y=paragraph(Component.literal(str(c,"accusation")),x+12,y+60,w-24,JusticeTheme.INK)+18;
        if(!str(c,"verdict").isBlank()){label(tr("verdict_record"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=18;y=paragraph(Component.literal(str(c,"verdict")),x+3,y,w-6,JusticeTheme.LIGHT)+12;}
        return y;
    }
    private int casePicker(int x,int y,int w){
        JsonArray choices=new JsonArray();for(JsonObject c:rows(array(data,"cases"))){JsonObject p=new JsonObject();p.addProperty("id",str(c,"id"));p.addProperty("name",str(c,"accusedName")+" · "+tr("status_"+str(c,"status").toLowerCase(Locale.ROOT)).getString());choices.add(p);}
        y=serverPager("cases",x,y,w);return picker("selected_case",choices,selectedCase,x,y,w,id->selectedCase=id);
    }
    private int evidence(int x,int y,int w){
        y=casePicker(x,y,w);JsonObject c=caseFile();if(c.isEmpty())return paragraph(tr("select_case"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        if(wide()){int lw=(w-14)/2;return Math.max(evidenceList(x,y,lw,c),evidenceForm(x+lw+14,y,w-lw-14,c));}
        return evidenceForm(x,evidenceList(x,y,w,c)+14,w,c);
    }
    private int evidenceList(int x,int y,int w,JsonObject c){
        label(tr("evidence_register"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=22;
        List<JsonObject> evidence=rows(array(c,"evidence"));if(evidence.isEmpty())return paragraph(tr("no_evidence"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        for(JsonObject e:evidence){final int top=y;int h=44+font.split(Component.literal(str(e,"text")),w-24).size()*12;paintings.add(g->JusticeTheme.parchment(g,x,top,w,h));
            label(Component.literal(str(e,"authorName")),x+11,y+9,w-22,JusticeTheme.INK,true);label(Component.literal(date(num(e,"at"))),x+11,y+24,w-22,0xFF7D6D51,false);paragraph(Component.literal(str(e,"text")),x+11,y+41,w-22,JusticeTheme.INK);y+=h+11;
        }return y;
    }
    private int evidenceForm(int x,int y,int w,JsonObject c){
        label(tr("evidence_title"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=22;y=paragraph(tr("evidence_hint"),x+3,y,w-6,JusticeTheme.MUTED)+11;
        y=multifield("evidence",tr("evidence_text"),x,y,w,1200,88);
        var submit=bodyButton(x,y,w,24,tr("evidence_submit"),()->{JsonObject p=new JsonObject();p.addProperty("case",selectedCase);p.addProperty("text",draft("evidence"));request("court_evidence",p);},true);submit.active=!waiting&&unresolved(c)&&array(c,"evidence").size()<16;y+=32;
        if(!unresolved(c))y=paragraph(tr("case_resolved"),x+3,y,w-6,JusticeTheme.MUTED)+10;return y;
    }
    private static boolean unresolved(JsonObject c){return str(c,"status").equals("OPEN")||str(c,"status").equals("HEARING");}
    private int judgment(int x,int y,int w){
        y=authority(x,y,w);y=casePicker(x,y,w);JsonObject c=caseFile();if(c.isEmpty())return paragraph(tr("select_case"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        if(wide()){int lw=(w-14)/2;return Math.max(judgmentRecord(x,y,lw,c),verdictForm(x+lw+14,y,w-lw-14,c));}
        return verdictForm(x,judgmentRecord(x,y,w,c)+14,w,c);
    }
    private int judgmentRecord(int x,int y,int w,JsonObject c){
        y=caseSummary(x,y,w,c)+10;label(tr("hearing_title"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=21;
        y=paragraph(tr("hearing_hint"),x+3,y,w-6,JusticeTheme.MUTED)+11;
        if(num(c,"heardAt")>0){label(tr("heard_at",date(num(c,"heardAt"))),x+3,y,w-6,JusticeTheme.LIGHT,false);y+=21;}
        var hear=bodyButton(x,y,w,24,tr(emergency()?"emergency_hear":"hear"),()->{var p=new JsonObject();p.addProperty("case",selectedCase);request("court_hear",p);},true);hear.active=!waiting&&judge()&&str(c,"status").equals("OPEN");return y+33;
    }
    private int verdictForm(int x,int y,int w,JsonObject c){
        label(tr("verdict_title"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=21;
        y=multifield("verdict_reason",tr("verdict_reason"),x,y,w,800,76);
        boolean heard=str(c,"status").equals("HEARING");
        var acquit=bodyButton(x,y,w,24,tr(emergency()?"emergency_acquit":"acquit"),()->confirm("acquit:"+selectedCase,()->{var p=new JsonObject();p.addProperty("case",selectedCase);p.addProperty("reason",draft("verdict_reason"));request("court_acquit",p);}),false);acquit.active=!waiting&&judge()&&heard;if(confirmation.equals("acquit:"+selectedCase))acquit.setMessage(tr("confirm_acquit"));y+=35;
        y=serverPager("prisons",x,y,w);y=picker("prison",array(data,"prisons"),selectedPrison,x,y,w,id->{selectedPrison=id;selectedCell="";normalize();});
        y=picker("cell",array(prison(),"cells"),selectedCell,x,y,w,id->selectedCell=id);
        y=field("minutes",tr("minutes"),x,y,w,5);
        var convict=bodyButton(x,y,w,24,tr(emergency()?"emergency_convict":"convict"),()->confirm("convict:"+selectedCase,()->{
            if(draft("verdict_reason").strip().length()>300){localError("gui.lawsjustice.court.convict_reason_limit");return;}
            try{int minutes=Integer.parseInt(draft("minutes").strip());if(minutes<1||minutes>43200)throw new IllegalArgumentException();var p=new JsonObject();p.addProperty("case",selectedCase);p.addProperty("prison",selectedPrison);p.addProperty("cell",selectedCell);p.addProperty("minutes",minutes);p.addProperty("reason",draft("verdict_reason"));request("court_convict",p);}catch(RuntimeException invalid){localError("gui.lawsjustice.minutes_invalid");}
        }),true);convict.active=!waiting&&judge()&&heard&&!selectedPrison.isBlank()&&!selectedCell.isBlank();if(confirmation.equals("convict:"+selectedCase))convict.setMessage(tr("confirm_convict"));y+=33;
        return paragraph(tr(heard?"verdict_hint":"hearing_required"),x+3,y,w-6,JusticeTheme.MUTED)+12;
    }
    private int warrants(int x,int y,int w){
        y=authority(x,y,w);if(wide()){int lw=(w-14)/2;return Math.max(warrantList(x,y,lw),judge()?warrantForm(x+lw+14,y,w-lw-14):warrantHelp(x+lw+14,y,w-lw-14));}
        return judge()?warrantForm(x,warrantList(x,y,w)+14,w):warrantHelp(x,warrantList(x,y,w)+14,w);
    }
    private int warrantList(int x,int y,int w){
        label(tr("warrant_register"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=22;y=serverPager("warrants",x,y,w);
        var warrants=rows(array(data,"warrants"));if(warrants.isEmpty())return paragraph(tr("no_warrants"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        for(JsonObject warrant:warrants){String id=str(warrant,"id");bodyButton(x,y,w,31,tr("warrant_entry",str(warrant,"accusedName"),tr("warrant_"+str(warrant,"status").toLowerCase(Locale.ROOT))),()->{selectedWarrant=id;confirmation="";rebuild();},id.equals(selectedWarrant));y+=38;}
        JsonObject warrant=warrant();if(!warrant.isEmpty()){
            y=paperWarrant(x,y+4,w,warrant,false);
            if(judge()){var revoke=bodyButton(x,y,w,23,tr(confirmation.equals("revoke:"+selectedWarrant)?"confirm_revoke":"revoke"),()->confirm("revoke:"+selectedWarrant,()->{var p=new JsonObject();p.addProperty("warrant",selectedWarrant);request("court_revoke",p);}),false);revoke.active=!waiting&&str(warrant,"status").equals("ACTIVE");y+=31;var copy=bodyButton(x,y,w,23,tr("copy_warrant"),()->{var p=new JsonObject();p.addProperty("warrant",selectedWarrant);request("court_copy",p);},false);copy.active=!waiting;y+=31;}
        }return y;
    }
    private int warrantForm(int x,int y,int w){
        label(tr("warrant_issue_title"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=22;
        y=picker("accused",accusedOptions(),selectedPlayer,x,y,w,id->selectedPlayer=id);
        y=multifield("warrant_reason",tr("warrant_reason"),x,y,w,300,64);y=field("hours",tr("hours"),x,y,w,3);
        var issue=bodyButton(x,y,w,24,tr(emergency()?"emergency_issue_warrant":"issue_warrant"),()->{
            try{int hours=Integer.parseInt(draft("hours").strip());if(hours<1||hours>168)throw new IllegalArgumentException();var p=new JsonObject();p.addProperty("accused",selectedPlayer);p.addProperty("reason",draft("warrant_reason"));p.addProperty("hours",hours);request("court_warrant",p);}catch(RuntimeException invalid){localError("gui.lawsjustice.court.hours_invalid");}
        },true);issue.active=!waiting&&judge()&&!selectedPlayer.isBlank();y+=34;return warrantHelp(x,y,w);
    }
    private int warrantHelp(int x,int y,int w){y=paragraph(tr("warrant_help"),x+3,y,w-6,JusticeTheme.MUTED)+13;return paragraph(tr("warrant_limit"),x+3,y,w-6,JusticeTheme.GOLD)+12;}
    private int document(int x,int y,int w){
        JsonObject warrant=object(data,"warrant");int paperWidth=Math.min(w,570),paperX=x+(w-paperWidth)/2;
        int end=paperWarrant(paperX,y,paperWidth,warrant,true);
        end=paragraph(tr(flag(data,"valid")?"document_valid":"document_invalid"),paperX+12,end+3,paperWidth-24,flag(data,"valid")?JusticeTheme.GREEN:JusticeTheme.RED)+14;
        if(!str(data,"warning").isBlank())end=paragraph(Component.translatable(str(data,"warning")),paperX+12,end,paperWidth-24,JusticeTheme.MUTED)+12;return end;
    }
    private int paperWarrant(int x,int y,int w,JsonObject warrant,boolean full){
        int top=y;int h=full?274:218;int reasonLines=font.split(Component.literal(str(warrant,"reason")),Math.max(1,w-36)).size();h+=reasonLines*12;final int paperH=h;paintings.add(g->{JusticeTheme.parchment(g,x,top,w,paperH);seal(g,x+w-36,top+paperH-37,12);JusticeTheme.border(g,x+7,top+7,w-14,paperH-14,0xFFAE986D);});
        if(full){paintings.add(g->JusticeTheme.scales(g,x+w/2,top+31,1));centered(tr("document_title"),x+w/2,y+54,w-26,JusticeTheme.INK,true);y+=79;}
        else {label(tr("document_title"),x+15,y+12,w-30,JusticeTheme.INK,true);y+=36;}
        label(tr("document_accused"),x+16,y,w-32,0xFF75603F,false);y+=16;label(Component.literal(str(warrant,"accusedName")),x+16,y,w-32,JusticeTheme.INK,true);y+=23;
        y=paragraph(Component.literal(str(warrant,"reason")),x+16,y,w-32,JusticeTheme.INK)+15;
        label(tr("document_issued",date(num(warrant,"issuedAt"))),x+16,y,w-32,0xFF75603F,false);y+=17;
        label(tr("document_expires",date(num(warrant,"expiresAt"))),x+16,y,w-32,0xFF75603F,false);y+=21;
        label(tr("document_author",str(warrant,"authorName")),x+16,y,w-32,JusticeTheme.INK,true);y+=16;
        label(tr(str(warrant,"authorityKind").equals("ADMIN_EMERGENCY")?"document_emergency":"document_president"),x+16,y,w-32,0xFF75603F,false);y+=21;
        label(tr("warrant_"+str(warrant,"status").toLowerCase(Locale.ROOT)),x+16,y,w-68,0xFF7D362C,true);return Math.max(y+26,top+paperH)+12;
    }
    private static void seal(GuiGraphics g,int cx,int cy,int radius){
        for(int yy=-radius;yy<=radius;yy++){int half=(int)Math.sqrt(radius*radius-yy*yy);g.fill(cx-half,cy+yy,cx+half+1,cy+yy+1,0xFF6F2B31);}
        JusticeTheme.diamond(g,cx,cy,8,0xFF973E3E);g.fill(cx-1,cy-6,cx+2,cy+6,0xFFE3B977);g.fill(cx-6,cy-3,cx+7,cy-1,0xFFE3B977);g.fill(cx-5,cy+5,cx+6,cy+7,0xFFE3B977);
        for(int side:new int[]{-1,1}){int xx=cx+side*4;g.fill(xx,cy-1,xx+1,cy+3,0xFFE3B977);g.fill(xx-2,cy+2,xx+3,cy+3,0xFFE3B977);}
    }
    private int serverPager(String kind,int x,int y,int w){
        JsonObject page=object(object(data,"pages"),kind);int index=(int)num(page,"index"),total=Math.max(1,(int)num(page,"total"));if(total<2)return y;
        var prev=bodyButton(x,y,29,20,Component.literal("‹«"),()->page(kind,index-1),false);prev.active=!waiting&&index>0;
        centered(tr("server_page",index+1,total),x+w/2,y+6,w-70,JusticeTheme.GOLD,false);
        var next=bodyButton(x+w-29,y,29,20,Component.literal("»›"),()->page(kind,index+1),false);next.active=!waiting&&index+1<total;return y+29;
    }
    private void page(String kind,int index){confirmation="";JsonObject p=new JsonObject();p.addProperty("kind",kind);p.addProperty("index",index);request("court_page",p);}
    private int picker(String key,JsonArray choices,String selected,int x,int y,int w,Consumer<String> select){
        label(tr(key),x+3,y,w-6,JusticeTheme.MUTED,false);y+=15;List<JsonObject> options=rows(choices);int current=-1;for(int i=0;i<options.size();i++)if(str(options.get(i),"id").equals(selected))current=i;
        final int index=current;String name=current<0?tr("none").getString():str(options.get(current),"name");
        var prev=bodyButton(x,y,24,22,Component.literal("‹"),()->{select.accept(str(options.get(Math.floorMod(index-1,options.size())),"id"));confirmation="";rebuild();},false);prev.active=!waiting&&!options.isEmpty();
        var mid=bodyButton(x+28,y,w-56,22,Component.literal(name),()->{select.accept(str(options.get(Math.floorMod(index+1,options.size())),"id"));confirmation="";rebuild();},true);mid.active=!waiting&&!options.isEmpty();mid.setTooltip(Tooltip.create(Component.literal(name)));
        var next=bodyButton(x+w-24,y,24,22,Component.literal("›"),()->{select.accept(str(options.get(Math.floorMod(index+1,options.size())),"id"));confirmation="";rebuild();},false);next.active=!waiting&&!options.isEmpty();return y+32;
    }
    private int field(String key,Component title,int x,int y,int w,int max){
        label(title,x+3,y,w-6,JusticeTheme.MUTED,false);y+=15;EditBox box=new EditBox(font,x+6,y+6,w-12,19,title);box.setMaxLength(max);box.setValue(draft(key));box.setBordered(false);box.setTextColor(JusticeTheme.LIGHT);box.setTooltip(Tooltip.create(title));int top=y;
        paintings.add(g->JusticeTheme.panel(g,x,top,w,25));inputs.put(key,box);addRenderableWidget(box);bodyWidgets.add(new BodyWidget(box,y+6));return y+33;
    }
    private int multifield(String key,Component title,int x,int y,int w,int max,int h){
        label(title,x+3,y,w-6,JusticeTheme.MUTED,false);y+=15;MultiLineEditBox box=new MultiLineEditBox(font,x,y,w,h,Component.empty(),title);box.setCharacterLimit(max);box.setValue(draft(key));box.setTooltip(Tooltip.create(title));multiline.put(key,box);addRenderableWidget(box);bodyWidgets.add(new BodyWidget(box,y));return y+h+11;
    }
    private void confirm(String key,Runnable action){if(confirmation.equals(key)){confirmation="";action.run();}else{confirmation=key;rebuild();}}
    private void localError(String key){notice=key;error=true;rebuild();}
    private void request(String op,JsonObject payload){
        if(waiting)return;remember();if(!List.of("court_refresh","court_page","court_close").contains(op)){payload.addProperty("nonce",str(data,"nonce"));payload.addProperty("version",num(data,"version"));}
        waiting=true;waitTicks=0;JusticeNetwork.request(op,payload);if(!op.equals("court_refresh"))rebuild();
    }
    @Override public void onClose(){JusticeNetwork.request("court_close",new JsonObject());super.onClose();}
    private void label(Component text,int x,int y,int w,int color,boolean bold){paintings.add(g->g.drawString(font,JusticeTheme.fit(font,text,Math.max(1,w),bold),x,y,color,false));}
    private void centered(Component text,int x,int y,int w,int color,boolean bold){paintings.add(g->{Component fitted=JusticeTheme.fit(font,text,Math.max(1,w),bold);g.drawString(font,fitted,x-font.width(fitted)/2,y,color,false);});}
    private int paragraph(Component text,int x,int y,int w,int color){for(var line:font.split(text,Math.max(1,w))){int yy=y;paintings.add(g->g.drawString(font,line,x,yy,color,false));y+=12;}return y;}
    private static String date(long epoch){try{return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch));}catch(RuntimeException invalid){return "—";}}
    private CourtButton button(int x,int y,int w,int h,Component text,Runnable action,boolean selected,boolean body){CourtButton b=addRenderableWidget(new CourtButton(x,y,w,h,text,()->{remember();action.run();},selected));if(body)bodyWidgets.add(new BodyWidget(b,y));return b;}
    private CourtButton bodyButton(int x,int y,int w,int h,Component text,Runnable action,boolean selected){return button(x,y,w,h,text,action,selected,true);}
    private int maximumScroll(){return Math.max(0,contentHeight-(bodyBottom-bodyTop));}
    private void positionWidgets(){for(BodyWidget e:bodyWidgets){AbstractWidget w=e.widget();w.setY(e.y()-scroll);w.visible=w.getY()>=bodyTop&&w.getY()+w.getHeight()<=bodyBottom;if(!w.visible)w.setFocused(false);}}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        // Let long testimony scroll inside its own text box before moving the ledger.
        for(MultiLineEditBox box:multiline.values())if(box.visible&&box.isMouseOver(mouseX,mouseY)&&box.mouseScrolled(mouseX,mouseY,horizontal,vertical))return true;
        if(mouseX>=px&&mouseX<px+pw&&mouseY>=bodyTop&&mouseY<bodyBottom){scroll=Math.clamp(scroll-(int)Math.round(vertical*27),0,maximumScroll());positionWidgets();return true;}return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==266||key==267)&&!editing()){scroll=Math.clamp(scroll+(key==267?1:-1)*Math.max(40,bodyBottom-bodyTop-25),0,maximumScroll());positionWidgets();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xA00F0C10);JusticeTheme.frame(g,px,py,pw,ph);
        int titleY=py+(shortScreen()?17:18);JusticeTheme.scales(g,px+33,py+(shortScreen()?23:30),1);
        g.drawString(font,JusticeTheme.fit(font,tr(document()?"document_title":"title"),pw-92,true),px+58,titleY,JusticeTheme.GOLD,false);
        if(!shortScreen())g.drawString(font,JusticeTheme.fit(font,tr(document()?"document_subtitle":"subtitle"),pw-81,false),px+58,py+33,JusticeTheme.MUTED,false);
        g.fill(px+14,bodyTop-3,px+pw-14,bodyBottom+3,0x331F1723);g.enableScissor(px+14,bodyTop,px+pw-14,bodyBottom);g.pose().pushPose();g.pose().translate(0,-scroll,0);for(var p:paintings)p.accept(g);g.pose().popPose();g.disableScissor();super.render(g,mouseX,mouseY,partial);
        if(maximumScroll()>0){int track=bodyBottom-bodyTop,thumb=Math.max(17,track*track/Math.max(track,contentHeight)),pos=scroll*(track-thumb)/Math.max(1,maximumScroll());g.fill(px+pw-12,bodyTop,px+pw-10,bodyBottom,0xFF171C19);g.fill(px+pw-12,bodyTop+pos,px+pw-10,bodyTop+pos+thumb,JusticeTheme.GOLD);}
        int nw=pw-(document()?112:193);Component text=waiting?tr("waiting"):notice.isBlank()?tr(document()?"document_readonly":"physical_only"):Component.translatable(notice);g.drawString(font,JusticeTheme.fit(font,text,nw,false),px+17,py+ph-22,error?JusticeTheme.RED:JusticeTheme.MUTED,false);
        if(mouseX>=px+17&&mouseX<px+17+nw&&mouseY>=py+ph-28&&mouseY<py+ph-8&&font.width(text)>nw)g.renderTooltip(font,font.split(text,Math.min(300,pw-28)),mouseX,mouseY);
    }
    private static final class CourtButton extends AbstractButton {
        private final Runnable action;private final boolean selected;
        private CourtButton(int x,int y,int w,int h,Component text,Runnable action,boolean selected){super(x,y,w,h,text);this.action=action;this.selected=selected;setTooltip(Tooltip.create(text));}
        @Override public void onPress(){if(active)action.run();}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){int x=getX(),y=getY();boolean hover=isHoveredOrFocused();int edge=!active?0xFF5B5652:selected?JusticeTheme.GOLD:hover?0xFFD5BF8A:0xFF81775F;g.fill(x,y,x+width,y+height,0xFF191518);g.fill(x+1,y+1,x+width-1,y+height-1,!active?0xFF302A2A:hover?0xFF624047:selected?0xFF51343D:0xFF382C31);JusticeTheme.border(g,x,y,width,height,edge);g.fill(x+2,y+2,x+width-2,y+3,0x335B6C53);if(selected){JusticeTheme.diamond(g,x+6,y+height/2,1,edge);JusticeTheme.diamond(g,x+width-7,y+height/2,1,edge);}var font=Minecraft.getInstance().font;Component text=JusticeTheme.fit(font,getMessage(),Math.max(1,width-(selected?18:10)),selected);g.drawString(font,text,x+(width-font.width(text))/2,y+(height-8)/2,!active?0xFF867A75:selected?JusticeTheme.LIGHT:JusticeTheme.MUTED,false);}
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
    }
}
