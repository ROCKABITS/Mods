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

/** Voluntary fines, bail and bound public records in the same royal court visual language. */
public final class FinanceScreen extends Screen {
    private enum Tab { FINES, BAILS, RECORDS, AUDIT, AUTHORITY }
    private record BodyWidget(AbstractWidget widget,int y){}
    private final List<Consumer<GuiGraphics>> paintings=new ArrayList<>();
    private final List<BodyWidget> bodyWidgets=new ArrayList<>();
    private final Map<String,EditBox> inputs=new HashMap<>();
    private final Map<String,MultiLineEditBox> multiline=new HashMap<>();
    private final Map<String,String> drafts=new HashMap<>();
    private JsonObject data;private Tab tab=Tab.FINES;
    private String selectedCharge="",selectedSentence="",confirmation="",notice="";
    private int px,py,pw,ph,bodyTop,bodyBottom,scroll,contentHeight,ticks,waitTicks;private boolean waiting,error;
    public FinanceScreen(JsonObject snapshot){super(tr("title"));data=snapshot.deepCopy();if(book())tab=Tab.RECORDS;drafts.put("amount",str(data,"currency").equals("CHRONICLES")?"20.00":"20");normalize();notice();}
    public JsonObject currentState(){return data.deepCopy();}
    public static void receive(String raw){if(raw==null||raw.length()>524288)return;final JsonObject snapshot;try{snapshot=JsonParser.parseString(raw).getAsJsonObject();}catch(RuntimeException invalid){return;}var mc=Minecraft.getInstance();mc.execute(()->{if(!flag(snapshot,"open")){if(mc.screen instanceof FinanceScreen)mc.setScreen(null);if(mc.player!=null&&!str(snapshot,"error").isBlank())mc.player.displayClientMessage(Component.translatable(str(snapshot,"error")),false);return;}if(mc.player==null||mc.level==null)return;if(mc.screen instanceof FinanceScreen current)current.accept(snapshot);else mc.setScreen(new FinanceScreen(snapshot));});}
    private void accept(JsonObject snapshot){boolean keepEditing=!waiting&&editing()&&flag(snapshot,"judge")==judge()&&str(snapshot,"nonce").equals(str(data,"nonce"))&&str(snapshot,"error").isBlank()&&str(snapshot,"message").isBlank();remember();data=snapshot;waiting=false;waitTicks=0;notice();normalize();if(!judge()&&tab==Tab.AUTHORITY){tab=Tab.FINES;keepEditing=false;}if(!keepEditing)rebuild();}
    private boolean judge(){return flag(data,"judge");}private boolean book(){return flag(data,"book");}private boolean shortScreen(){return ph<300;}private boolean wide(){return pw>=640;}
    private boolean editing(){return inputs.values().stream().anyMatch(EditBox::isFocused)||multiline.values().stream().anyMatch(MultiLineEditBox::isFocused);}
    private static Component tr(String key,Object...args){return Component.translatable("gui.lawsjustice.finance."+key,args);}
    private static String str(JsonObject o,String k){try{return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}catch(RuntimeException e){return "";}}
    private static long num(JsonObject o,String k){try{return o.has(k)?o.get(k).getAsLong():0;}catch(RuntimeException e){return 0;}}
    private static boolean flag(JsonObject o,String k){try{return o.has(k)&&o.get(k).getAsBoolean();}catch(RuntimeException e){return false;}}
    private static JsonObject object(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject();}
    private static JsonArray array(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private static List<JsonObject> rows(JsonArray a){List<JsonObject> result=new ArrayList<>();for(var e:a)if(e.isJsonObject())result.add(e.getAsJsonObject());return result;}
    private static JsonObject find(JsonArray a,String id){for(var e:rows(a))if(str(e,"id").equals(id))return e;return new JsonObject();}
    private JsonArray charges(){JsonArray a=new JsonArray();for(var row:rows(array(data,"charges")))if(str(row,"kind").equals(tab==Tab.FINES?"FINE":"BAIL"))a.add(row);return a;}
    private JsonObject charge(){return find(charges(),selectedCharge);}
    private void normalize(){if(find(charges(),selectedCharge).isEmpty())selectedCharge=charges().isEmpty()?"":str(charges().get(0).getAsJsonObject(),"id");if(find(array(data,"sentences"),selectedSentence).isEmpty())selectedSentence=array(data,"sentences").isEmpty()?"":str(array(data,"sentences").get(0).getAsJsonObject(),"id");}
    private void notice(){notice=str(data,"error");error=!notice.isBlank();if(!error)notice=str(data,"message");}
    private void remember(){inputs.forEach((k,v)->drafts.put(k,v.getValue()));multiline.forEach((k,v)->drafts.put(k,v.getValue()));}
    private String draft(String key){return drafts.getOrDefault(key,"");}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public void tick(){ticks++;if(waiting&&++waitTicks>200){waiting=false;notice="gui.lawsjustice.connection_wait";error=true;rebuild();}if(ticks%100==0&&!waiting&&!editing())request("finance_refresh",new JsonObject());super.tick();}
    @Override protected void init(){
        paintings.clear();bodyWidgets.clear();inputs.clear();multiline.clear();pw=Math.min(810,width-12);ph=Math.min(436,height-12);px=(width-pw)/2;py=(height-ph)/2;int tabsY=py+(shortScreen()?38:58);
        List<Tab> tabs=book()?List.of(Tab.RECORDS,Tab.AUDIT):judge()?List.of(Tab.FINES,Tab.BAILS,Tab.RECORDS,Tab.AUDIT,Tab.AUTHORITY):List.of(Tab.FINES,Tab.BAILS,Tab.RECORDS,Tab.AUDIT);
        int tw=(pw-34-(tabs.size()-1)*4)/tabs.size();for(int i=0;i<tabs.size();i++){Tab value=tabs.get(i);button(px+17+i*(tw+4),tabsY,tw,20,tr(value.name().toLowerCase(Locale.ROOT)),()->{tab=value;scroll=0;confirmation="";normalize();rebuild();},tab==value,false);}
        bodyTop=tabsY+28;bodyBottom=py+ph-40;int x=px+17,y=bodyTop,w=pw-34;contentHeight=switch(tab){case FINES,BAILS->payments(x,y,w);case RECORDS->records(x,y,w);case AUDIT->audit(x,y,w);case AUTHORITY->authority(x,y,w);}-y;scroll=Math.clamp(scroll,0,maximumScroll());positionWidgets();
        button(px+pw-91,py+ph-28,74,19,Component.translatable("gui.lawsjustice.close"),this::onClose,false,false);
        var back=button(px+pw-172,py+ph-28,75,19,tr(book()?"refresh":"court"),()->request(book()?"finance_refresh":"finance_court",new JsonObject()),false,false);back.active=!waiting;
    }
    private void rebuild(){clearWidgets();init();}
    private Component money(JsonObject o){return amount(num(o,"amount"),str(o,"currency"));}
    private Component amount(long value,String currency){return currency.equals("CHRONICLES")?Component.literal(pt.lawsjustice.domain.FinanceAmounts.format(value,currency)):tr("emeralds",value);}
    private int payments(int x,int y,int w){
        y=paragraph(tr(tab==Tab.FINES?"fines_hint":"bails_hint"),x+3,y,w-6,JusticeTheme.MUTED)+10;
        if(flag(data,"wallet")){label(tr("wallet",amount(num(data,"balance"),str(data,"currency"))),x+3,y,w-6,JusticeTheme.GOLD,true);y+=25;}else y=paragraph(tr("wallet_missing"),x+3,y,w-6,JusticeTheme.RED)+12;
        if(wide()){int lw=300;return Math.max(chargeList(x,y,lw),chargeDetail(x+lw+14,y,w-lw-14));}return chargeDetail(x,chargeList(x,y,w)+12,w);
    }
    private int chargeList(int x,int y,int w){y=serverPager("charges",x,y,w);if(charges().isEmpty())return paragraph(tr("no_charges"),x+3,y,w-6,JusticeTheme.MUTED)+12;for(var c:rows(charges())){String id=str(c,"id");bodyButton(x,y,w,33,tr("charge_entry",str(c,"targetName"),money(c),tr("status_"+str(c,"status").toLowerCase(Locale.ROOT))),()->{selectedCharge=id;confirmation="";rebuild();},id.equals(selectedCharge));y+=40;}return y;}
    private int chargeDetail(int x,int y,int w){JsonObject c=charge();if(c.isEmpty())return y;
        int top=y,h=91+font.split(Component.literal(str(c,"reason")),w-24).size()*12;paintings.add(g->JusticeTheme.parchment(g,x,top,w,h));label(Component.literal(str(c,"targetName")),x+12,y+10,w-24,JusticeTheme.INK,true);label(money(c),x+12,y+29,w-24,0xFF815027,true);label(tr("author",str(c,"authorName")),x+12,y+48,w-24,0xFF75603F,false);y=paragraph(Component.literal(str(c,"reason")),x+12,y+68,w-24,JusticeTheme.INK)+23;y=Math.max(y,top+h+9);
        label(tr("status_"+str(c,"status").toLowerCase(Locale.ROOT)),x+3,y,w-6,JusticeTheme.GOLD,true);y+=23;
        if(str(c,"status").equals("DUE")){
            y=paragraph(tr(flag(c,"busy")?flag(c,"mine")?"pending_hint":"busy_hint":"pay_hint"),x+3,y,w-6,JusticeTheme.MUTED)+12;
            var pay=bodyButton(x,y,w,25,tr(flag(c,"mine")?"retry":"pay",money(c)),()->confirm("pay:"+selectedCharge,()->{var req=new JsonObject();req.addProperty("charge",selectedCharge);request("finance_pay",req);}),true);pay.active=!waiting&&flag(c,"payable");if(confirmation.equals("pay:"+selectedCharge))pay.setMessage(tr("confirm_pay",money(c)));y+=35;
            if(flag(c,"mine")){var cancel=bodyButton(x,y,w,23,tr("cancel_pending"),()->confirm("cancel:"+selectedCharge,()->{var req=new JsonObject();req.addProperty("payment",str(c,"payment"));request("finance_cancel",req);}),false);cancel.active=!waiting;if(confirmation.equals("cancel:"+selectedCharge))cancel.setMessage(tr("confirm_cancel"));y+=32;}
            if(judge()){y=multifield("revoke_reason",tr("reason"),x,y,w,300,54);var revoke=bodyButton(x,y,w,24,tr("revoke"),()->confirm("revoke:"+selectedCharge,()->{var req=new JsonObject();req.addProperty("charge",selectedCharge);req.addProperty("reason",draft("revoke_reason"));request("finance_revoke",req);}),false);revoke.active=!waiting&&!flag(c,"busy");if(confirmation.equals("revoke:"+selectedCharge))revoke.setMessage(tr("confirm_revoke"));y+=34;}
        }else if(str(c,"status").equals("PAID")){y=paragraph(tr(str(c,"destination").equals("CROWN")?"destination_crown":"destination_escrow"),x+3,y,w-6,JusticeTheme.GREEN)+12;}
        return y;
    }
    private int records(int x,int y,int w){y=paragraph(tr("records_hint"),x+3,y,w-6,JusticeTheme.MUTED)+13;y=serverPager("records",x,y,w);if(array(data,"records").isEmpty())return paragraph(tr("no_records"),x+3,y,w-6,JusticeTheme.MUTED)+12;
        for(var record:rows(array(data,"records"))){int top=y,h=90+font.split(Component.literal(str(record,"reason")),w-24).size()*12;paintings.add(g->JusticeTheme.parchment(g,x,top,w,h));label(tr("record_entry",str(record,"name"),Component.translatable("gui.lawsjustice.court.status_"+str(record,"status").toLowerCase(Locale.ROOT))),x+12,y+10,w-24,JusticeTheme.INK,true);label(tr("author",str(record,"judge")),x+12,y+29,w-24,0xFF75603F,false);label(Component.literal(date(num(record,"date"))),x+12,y+46,w-24,0xFF75603F,false);y=paragraph(Component.literal(str(record,"reason")),x+12,y+65,w-24,JusticeTheme.INK)+25;if(record.has("amount")){label(tr("record_fine",money(record),tr("status_"+str(record,"paymentStatus").toLowerCase(Locale.ROOT))),x+3,y,w-6,JusticeTheme.GOLD,false);y+=24;}if(str(record,"status").equals("CONVICTED")){label(tr(num(record,"originalTicks")>0?"record_sentence":"record_sentence_legacy",(num(record,"originalTicks")+1199)/1200),x+3,y,w-6,JusticeTheme.GOLD,false);y+=24;}y+=12;}return y;
    }
    private int audit(int x,int y,int w){y=paragraph(tr("audit_hint"),x+3,y,w-6,JusticeTheme.MUTED)+13;y=serverPager("audit",x,y,w);if(array(data,"audit").isEmpty())return paragraph(tr("no_records"),x+3,y,w-6,JusticeTheme.MUTED)+12;for(var row:rows(array(data,"audit"))){label(Component.literal(str(row,"playerName")),x+3,y,w-6,JusticeTheme.GOLD,true);y+=18;label(Component.translatable("gui.lawsjustice.history_action."+str(row,"action").toLowerCase(Locale.ROOT)),x+3,y,w-6,JusticeTheme.LIGHT,false);y+=18;y=paragraph(Component.literal(str(row,"reason")),x+3,y,w-6,JusticeTheme.MUTED)+7;label(tr("audit_author",str(row,"authorityName"),date(num(row,"at"))),x+3,y,w-6,JusticeTheme.MUTED,false);y+=30;}return y;}
    private int authority(int x,int y,int w){
        y=paragraph(tr(str(data,"authorityKind").equals("ADMIN_EMERGENCY")?"authority_emergency":"authority_president"),x+3,y,w-6,JusticeTheme.GOLD)+13;
        if(wide()){int lw=(w-14)/2;return Math.max(sentencePanel(x,y,lw),treasuryPanel(x+lw+14,y,w-lw-14));}return treasuryPanel(x,sentencePanel(x,y,w)+14,w);
    }
    private int sentencePanel(int x,int y,int w){
        y=serverPager("sentences",x,y,w);JsonArray choices=new JsonArray();for(var sentence:rows(array(data,"sentences"))){var c=new JsonObject();c.addProperty("id",str(sentence,"id"));c.addProperty("name",str(sentence,"playerName"));choices.add(c);}y=picker("prisoner",choices,selectedSentence,x,y,w,id->selectedSentence=id);
        JsonObject sentence=find(array(data,"sentences"),selectedSentence);if(!sentence.isEmpty()){long left=num(sentence,"originalTicks")-num(sentence,"servedTicks")-num(sentence,"reductionTicks");label(tr("remaining",(left+1199)/1200),x+3,y,w-6,JusticeTheme.LIGHT,false);y+=25;}
        y=field("amount",tr(str(data,"currency").equals("CHRONICLES")?"amount_euros":"amount_emeralds"),x,y,w,22);y=multifield("reason",tr("reason"),x,y,w,300,62);
        var offer=bodyButton(x,y,w,25,tr("offer_bail"),()->confirm("bail:"+selectedSentence,()->{var req=new JsonObject();req.addProperty("sentence",selectedSentence);req.addProperty("amount",draft("amount"));req.addProperty("reason",draft("reason"));request("finance_bail",req);}),true);offer.active=!waiting&&!selectedSentence.isEmpty();if(confirmation.equals("bail:"+selectedSentence))offer.setMessage(tr("confirm_bail"));y+=35;
        var release=bodyButton(x,y,w,24,tr("release"),()->confirm("release:"+selectedSentence,()->{var req=new JsonObject();req.addProperty("sentence",selectedSentence);req.addProperty("reason",draft("reason"));request("finance_release",req);}),false);release.active=!waiting&&!selectedSentence.isEmpty();if(confirmation.equals("release:"+selectedSentence))release.setMessage(tr("confirm_release"));return y+35;
    }
    private int treasuryPanel(int x,int y,int w){label(tr("treasury_title"),x+3,y,w-6,JusticeTheme.GOLD,true);y+=23;y=paragraph(tr(flag(data,"treasury")?"treasury_connected":"treasury_standalone"),x+3,y,w-6,JusticeTheme.MUTED)+16;label(tr("secured",amount(num(data,"chroniclesEscrow"),"CHRONICLES")),x+3,y,w-6,JusticeTheme.LIGHT,false);y+=23;label(tr("secured",amount(num(data,"emeraldEscrow"),"EMERALD")),x+3,y,w-6,JusticeTheme.LIGHT,false);y+=27;return paragraph(tr("treasury_receipts"),x+3,y,w-6,JusticeTheme.MUTED)+12;}
    private int serverPager(String kind,int x,int y,int w){
        JsonObject page=object(object(data,"pages"),kind);int index=(int)num(page,"index"),total=Math.max(1,(int)num(page,"total"));if(total<2)return y;
        var prev=bodyButton(x,y,29,20,Component.literal("‹«"),()->page(kind,index-1),false);prev.active=!waiting&&index>0;
        centered(tr("server_page",index+1,total),x+w/2,y+6,w-70,JusticeTheme.GOLD,false);
        var next=bodyButton(x+w-29,y,29,20,Component.literal("»›"),()->page(kind,index+1),false);next.active=!waiting&&index+1<total;return y+29;
    }
    private void page(String kind,int index){confirmation="";JsonObject p=new JsonObject();p.addProperty("kind",kind);p.addProperty("index",index);request("finance_page",p);}
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
        if(waiting)return;remember();if(!List.of("finance_refresh","finance_page","finance_close").contains(op)){payload.addProperty("nonce",str(data,"nonce"));payload.addProperty("version",num(data,"version"));}
        waiting=true;waitTicks=0;JusticeNetwork.request(op,payload);if(!op.equals("finance_refresh"))rebuild();
    }
    @Override public void onClose(){JusticeClientSessions.close(data);JusticeNetwork.request("finance_close",new JsonObject());super.onClose();}
    private void label(Component text,int x,int y,int w,int color,boolean bold){paintings.add(g->g.drawString(font,JusticeTheme.fit(font,text,Math.max(1,w),bold),x,y,color,false));}
    private void centered(Component text,int x,int y,int w,int color,boolean bold){paintings.add(g->{Component fitted=JusticeTheme.fit(font,text,Math.max(1,w),bold);g.drawString(font,fitted,x-font.width(fitted)/2,y,color,false);});}
    private int paragraph(Component text,int x,int y,int w,int color){for(var line:font.split(text,Math.max(1,w))){int yy=y;paintings.add(g->g.drawString(font,line,x,yy,color,false));y+=12;}return y;}
    private static String date(long epoch){try{return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch));}catch(RuntimeException invalid){return "—";}}
    private FinanceButton button(int x,int y,int w,int h,Component text,Runnable action,boolean selected,boolean body){FinanceButton b=addRenderableWidget(new FinanceButton(x,y,w,h,text,()->{remember();action.run();},selected));if(body)bodyWidgets.add(new BodyWidget(b,y));return b;}
    private FinanceButton bodyButton(int x,int y,int w,int h,Component text,Runnable action,boolean selected){return button(x,y,w,h,text,action,selected,true);}
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
        g.drawString(font,JusticeTheme.fit(font,tr(book()?"book_title":"title"),pw-92,true),px+58,titleY,JusticeTheme.GOLD,false);
        if(!shortScreen())g.drawString(font,JusticeTheme.fit(font,tr(book()?"book_subtitle":"subtitle"),pw-81,false),px+58,py+33,JusticeTheme.MUTED,false);
        g.fill(px+14,bodyTop-3,px+pw-14,bodyBottom+3,0x331F1723);g.enableScissor(px+14,bodyTop,px+pw-14,bodyBottom);g.pose().pushPose();g.pose().translate(0,-scroll,0);for(var p:paintings)p.accept(g);g.pose().popPose();g.disableScissor();super.render(g,mouseX,mouseY,partial);
        if(maximumScroll()>0){int track=bodyBottom-bodyTop,thumb=Math.max(17,track*track/Math.max(track,contentHeight)),pos=scroll*(track-thumb)/Math.max(1,maximumScroll());g.fill(px+pw-12,bodyTop,px+pw-10,bodyBottom,0xFF171C19);g.fill(px+pw-12,bodyTop+pos,px+pw-10,bodyTop+pos+thumb,JusticeTheme.GOLD);}
        int nw=pw-193;Component text=waiting?tr("waiting"):notice.isBlank()?tr("physical_only"):Component.translatable(notice);g.drawString(font,JusticeTheme.fit(font,text,nw,false),px+17,py+ph-22,error?JusticeTheme.RED:JusticeTheme.MUTED,false);
        if(mouseX>=px+17&&mouseX<px+17+nw&&mouseY>=py+ph-28&&mouseY<py+ph-8&&font.width(text)>nw)g.renderTooltip(font,font.split(text,Math.min(300,pw-28)),mouseX,mouseY);
    }
    private static final class FinanceButton extends AbstractButton {
        private final Runnable action;private final boolean selected;
        private FinanceButton(int x,int y,int w,int h,Component text,Runnable action,boolean selected){super(x,y,w,h,text);this.action=action;this.selected=selected;setTooltip(Tooltip.create(text));}
        @Override public void onPress(){if(active)action.run();}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){int x=getX(),y=getY();boolean hover=isHoveredOrFocused();int edge=!active?0xFF5B5652:selected?JusticeTheme.GOLD:hover?0xFFD5BF8A:0xFF81775F;g.fill(x,y,x+width,y+height,0xFF191518);g.fill(x+1,y+1,x+width-1,y+height-1,!active?0xFF302A2A:hover?0xFF624047:selected?0xFF51343D:0xFF382C31);JusticeTheme.border(g,x,y,width,height,edge);g.fill(x+2,y+2,x+width-2,y+3,0x335B6C53);if(selected){JusticeTheme.diamond(g,x+6,y+height/2,1,edge);JusticeTheme.diamond(g,x+width-7,y+height/2,1,edge);}var font=Minecraft.getInstance().font;Component text=JusticeTheme.fit(font,getMessage(),Math.max(1,width-(selected?18:10)),selected);g.drawString(font,text,x+(width-font.width(text))/2,y+(height-8)/2,!active?0xFF867A75:selected?JusticeTheme.LIGHT:JusticeTheme.MUTED,false);}
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
    }
}
