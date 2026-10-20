package pt.realmmarriage.client;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pt.realmmarriage.network.MarriageNetwork;
import static pt.realmmarriage.client.MarriageTheme.*;

/** Scrollable native controls on parchment; financial state always comes from the server. */
public final class MarriageScreen extends Screen {
    private record Row(String label,String value,String field,Runnable action,boolean enabled){}
    private JsonObject data;
    private String tab="home",lastAction="";
    private final boolean document;
    private final Map<String,String> form=new HashMap<>();
    private final Map<String,EditBox> fields=new LinkedHashMap<>();
    private List<Row> rows=new ArrayList<>();
    private int x,y,w,h,offset,visible,ticks;
    private boolean waiting;
    private static long nextRequest;
    private long waitingAt,waitingRequest;
    private Runnable confirmation;
    public MarriageScreen(JsonObject data,boolean document){super(tr(document?"certificate":"title"));this.data=data;this.document=document;}
    public JsonObject data(){return data;}
    public void accept(JsonObject next){capture();data=next;if(number(next,"reply")==waitingRequest)waiting=false;rebuildWidgets();}
    public static String str(JsonObject o,String k){return o!=null&&o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    public static long number(JsonObject o,String k){return o!=null&&o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsLong():0;}
    public static boolean bool(JsonObject o,String k){return o!=null&&o.has(k)&&!o.get(k).isJsonNull()&&o.get(k).getAsBoolean();}
    private static JsonArray array(JsonObject o,String k){return o!=null&&o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private JsonObject union(){return data.has("union")&&!data.get("union").isJsonNull()?data.getAsJsonObject("union"):null;}
    private boolean self(String id){return str(data,"me").equals(id);}
    private boolean party(){JsonObject u=union();return u!=null&&(self(str(u,"a"))||self(str(u,"b")));}
    private boolean priest(){return union()!=null&&self(str(union(),"priest"))&&bool(union(),"priestAccepted");}
    private boolean contains(JsonObject o,String key,String value){return array(o,key).asList().stream().anyMatch(e->e.getAsString().equals(value));}
    private String name(String id){JsonObject u=union();if(u!=null){if(str(u,"a").equals(id))return str(u,"nameA");if(str(u,"b").equals(id))return str(u,"nameB");if(str(u,"priest").equals(id))return str(u,"priestName");}for(var p:array(data,"players"))if(str(p.getAsJsonObject(),"id").equals(id))return str(p.getAsJsonObject(),"name");return id.isBlank()?tr("none").getString():id.substring(0,Math.min(12,id.length()));}
    private void capture(){fields.forEach((k,v)->form.put(k,v.getValue()));}
    private void change(String tab){capture();this.tab=tab;offset=0;confirmation=null;rebuildWidgets();}
    private void text(String key,String value){rows.add(new Row(key,value,null,null,true));}
    private void field(String label,String key,String initial){form.putIfAbsent(key,initial);rows.add(new Row(label,"",key,null,true));}
    private void button(String label,String key,Runnable action,boolean enabled){rows.add(new Row(label,tr(key).getString(),null,action,enabled&&!waiting));}
    private void action(String label,String key,String op,boolean enabled){button(label,key,()->send(op,new JsonObject()),enabled);}
    private void send(String op,JsonObject payload){capture();payload.addProperty("revision",number(data,"revision"));waitingRequest=++nextRequest;payload.addProperty("request",waitingRequest);waiting=true;waitingAt=System.nanoTime();lastAction=op;MarriageNetwork.request(op,payload);rebuildWidgets();}
    private void acceptBoolean(String op,boolean accept){JsonObject p=new JsonObject();p.addProperty("accept",accept);send(op,p);}
    private static long cents(String text){return new BigDecimal(text.strip().replace(',','.')).movePointRight(2).longValueExact();}
    private void amount(String op){try{capture();JsonObject payload=new JsonObject();payload.addProperty("amount",cents(form.getOrDefault("amount","0")));send(op,payload);}catch(RuntimeException e){data.addProperty("message","marriage.error.amount");rebuildWidgets();}}
    private void confirm(Runnable action){confirmation=action;offset=0;rebuildWidgets();}
    private void cyclePlayer(String field,boolean none){capture();JsonObject u=union();List<String> ids=new ArrayList<>();if(none)ids.add("");for(var row:array(data,"players")){String id=str(row.getAsJsonObject(),"id");if(u!=null&&!id.equals(str(u,"a"))&&!id.equals(str(u,"b")))ids.add(id);}if(ids.isEmpty())return;int i=ids.indexOf(form.getOrDefault(field,""));form.put(field,ids.get((i+1)%ids.size()));rebuildWidgets();}
    private void cycleMode(){String old=form.getOrDefault("mode","SEPARATE");List<String> modes=bool(data,"chronicles")?List.of("SEPARATE","JOINT","SHARED"):List.of("SEPARATE");form.put("mode",modes.get((modes.indexOf(old)+1)%modes.size()));rebuildWidgets();}
    private void configure(){try{capture();JsonObject in=new JsonObject();in.addProperty("priest",form.getOrDefault("priest",""));in.addProperty("mode",form.getOrDefault("mode","SEPARATE"));in.addProperty("shareA",Math.toIntExact(cents(form.getOrDefault("shareA","50"))));JsonArray witnesses=new JsonArray();for(String key:List.of("witness0","witness1")){String id=form.getOrDefault(key,"");if(!id.isBlank())witnesses.add(id);}in.add("witnesses",witnesses);send("configure",in);}catch(RuntimeException e){data.addProperty("message","marriage.error.agreement");rebuildWidgets();}}
    private void rows(){rows=new ArrayList<>();JsonObject u=union();String phase=str(u,"phase");boolean physical=bool(data,"physical");
        if(document){text("couple",str(data,"a")+" & "+str(data,"b"));text("priest",str(data,"priest"));text("phase",tr("phase."+str(data,"phase")).getString());text("mode",tr("mode."+str(data,"mode")).getString());text("date",java.time.Instant.ofEpochMilli(number(data,"marriedAt")).atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString());for(var witness:array(data,"witnesses"))text("witness",witness.getAsString());text("verified",str(data,"id"));return;}
        if(confirmation!=null){text("confirmation",tr("confirm_hint").getString());button("confirmation","confirm",confirmation,true);button("back","back",()->{confirmation=null;rebuildWidgets();},true);return;}
        if(tab.equals("home")){
            text("civil_status",tr("status."+str(data,"status")).getString());
            if(u==null)text("proposal_hint",tr("proposal_instructions").getString());else{text("couple",str(u,"nameA")+" & "+str(u,"nameB"));text("phase",tr("phase."+phase).getString());if(phase.equals("PROPOSED")){if(self(str(u,"b"))){button("proposal_received","accept",()->acceptBoolean("answer",true),true);button("proposal_received","decline",()->acceptBoolean("answer",false),true);}else text("waiting",tr("recipient").getString());}if(phase.equals("ENGAGED")){button("terms","terms",()->change("terms"),physical&&party());action("agreement","agree","agree",physical&&party()&&!contains(u,"plan",str(data,"me")));if(contains(u,"plan",str(u,"a"))&&contains(u,"plan",str(u,"b"))&&!party()){button("invitation","accept",()->acceptBoolean("invitation",true),true);button("invitation","decline",()->acceptBoolean("invitation",false),true);}}
                if(phase.equals("MARRIED")){action("documents","claim_documents","documents",party()&&physical&&!contains(u,"claimed",str(data,"me")));button("divorce","divorce",()->confirm(()->send("divorce",new JsonObject())),party());}else if(!List.of("DIVORCED","CANCELLED").contains(phase))button("cancel","cancel",()->confirm(()->send("cancel",new JsonObject())),party()||priest());}
            button("privacy",bool(data,"publicSpouse")?"public":"private",()->{JsonObject in=new JsonObject();in.addProperty("public",!bool(data,"publicSpouse"));send("privacy",in);},true);
            for(var row:array(data,"invitations")){JsonObject invitation=row.getAsJsonObject();rows.add(new Row(str(invitation,"a")+" & "+str(invitation,"b"),tr("view").getString(),null,()->{JsonObject in=new JsonObject();in.addProperty("union",str(invitation,"id"));send("select",in);},true));}
        }else if(tab.equals("terms")){
            if(u==null){text("terms",tr("engagement_first").getString());return;}
            form.putIfAbsent("priest",str(u,"priest"));form.putIfAbsent("mode",str(u,"mode"));form.putIfAbsent("shareA",new BigDecimal(number(u,"shareA")).movePointLeft(2).toPlainString());for(int i=0;i<2;i++)form.putIfAbsent("witness"+i,array(u,"witnesses").size()>i?array(u,"witnesses").get(i).getAsString():"");
            rows.add(new Row("priest",name(form.get("priest")),null,()->cyclePlayer("priest",false),physical&&party()&&phase.equals("ENGAGED")));
            for(int i=0;i<2;i++){String k="witness"+i;rows.add(new Row("witness",name(form.get(k)),null,()->cyclePlayer(k,true),physical&&party()&&phase.equals("ENGAGED")));}
            rows.add(new Row("mode",tr("mode."+form.get("mode")).getString(),null,this::cycleMode,physical&&party()&&phase.equals("ENGAGED")));
            field("share_a","shareA",form.get("shareA"));text("share_hint",tr("split_rule").getString());text("fee_quote",money(bool(data,"crown")?number(data.getAsJsonObject("settings"),"fee"):0));
            button("agreement","save",this::configure,physical&&party()&&phase.equals("ENGAGED"));text("frozen_fee",money(number(u,"fee")));text("accepted_a",tr(contains(u,"plan",str(u,"a"))?"yes":"no").getString());text("accepted_b",tr(contains(u,"plan",str(u,"b"))?"yes":"no").getString());action("agreement","agree","agree",physical&&party()&&phase.equals("ENGAGED")&&!contains(u,"plan",str(data,"me")));text("joint_hint",tr("joint_details").getString());
        }else if(tab.equals("ceremony")){
            if(u==null){text("ceremony",tr("engagement_first").getString());return;}text("couple",str(u,"nameA")+" & "+str(u,"nameB"));text("phase",tr("phase."+phase).getString());text("priest",name(str(u,"priest")));text("priest_accepted",tr(bool(u,"priestAccepted")?"yes":"no").getString());
            if(phase.equals("ENGAGED"))action("ceremony","start","start",physical&&priest());
            if(phase.equals("OPENING")||phase.equals("INTRODUCTION"))action("ceremony","next","next",priest());
            if(phase.equals("OBJECTIONS")){text("objection",tr("hud.objections").getString());text("remaining",tr("seconds",Math.max(0,(number(u,"objectionUntil")-number(data,"now")+999)/1000)).getString());}
            if(phase.equals("SUSPENDED")){text("objection",name(str(u,"objector")));text("reason",str(u,"reason"));text("speaker",tr("speaker."+str(u,"speaker")).getString());if(priest())for(String who:List.of("objector","couple")){button("speaker","speaker."+who,()->{JsonObject in=new JsonObject();in.addProperty("speaker",who);send("speaker",in);},true);}action("decision","continue","continue",party());action("ceremony","resume","resume",priest());}
            if(phase.equals("VOWS")){action("vows","ask","ask",priest()&&(number(u,"vowTurn")==0||number(u,"vowTurn")==1&&contains(u,"vows",str(u,"a"))));boolean turn=number(u,"vowTurn")==1&&self(str(u,"a"))||number(u,"vowTurn")==2&&self(str(u,"b"));action("vows","i_do","vow",turn&&!contains(u,"vows",str(data,"me")));action("witness","sign","witness",contains(u,"witnesses",str(data,"me")));action("ceremony","seal","seal",priest()&&contains(u,"vows",str(u,"a"))&&contains(u,"vows",str(u,"b")));}
            text("error",str(u,"error").isBlank()?"—":Component.translatable(str(u,"error")).getString());if(party()&&!phase.equals("MARRIED"))button("cancel","cancel",()->confirm(()->send("cancel",new JsonObject())),true);
        }else if(tab.equals("account")){
            text("personal_balance",money(number(data,"wallet")));text("shared_balance",money(number(u,"balance")));text("pending_credits",money(number(data,"credits")));field("amount","amount","10,00");boolean active=physical&&party()&&phase.equals("MARRIED")&&!str(u,"mode").equals("SEPARATE");button("account","deposit",()->amount("deposit"),active);button("account","withdraw",()->amount("withdraw"),active);action("account","consolidate","consolidate",active&&str(u,"mode").equals("JOINT"));button("pending_credits","claim_credits",()->amount("credits"),number(data,"credits")>0);text("api_hint",tr("payment_api_hint").getString());
        }else if(tab.equals("effects")){
            var preferences=MarriageClient.preferences();button("animations",preferences.enabled?"on":"off",()->{preferences.enabled=!preferences.enabled;preferences.save();rebuildWidgets();},true);button("particles",preferences.reduced?"reduced":"normal",()->{preferences.reduced=!preferences.reduced;preferences.save();rebuildWidgets();},true);rows.add(new Row("volume",Math.round(preferences.volume*100)+"%",null,()->{preferences.volume=preferences.volume>=.99f?0:Math.round((preferences.volume+.1f)*10)/10f;preferences.save();rebuildWidgets();},true));
        }else if(tab.equals("admin")){
            JsonObject settings=data.getAsJsonObject("settings");text("fee_current",money(number(settings,"fee")));field("fee","fee",money(number(settings,"fee")).replace(" €",""));button("fee","save",()->{try{capture();JsonObject in=new JsonObject();in.addProperty("amount",cents(form.get("fee")));send("fee",in);}catch(RuntimeException e){data.addProperty("message","marriage.error.amount");}},bool(data,"governor"));
            if(bool(data,"admin")){for(String key:List.of("objectionSeconds","guestRadius","participantRadius","ceremonyMinutes","witnessLimit","proposalMinutes","feeCap"))field("setting."+key,key,Long.toString(number(settings,key)));button("settings","save",()->{try{capture();JsonObject next=settings.deepCopy();for(String key:List.of("objectionSeconds","guestRadius","participantRadius","ceremonyMinutes","witnessLimit","proposalMinutes","feeCap"))next.addProperty(key,new BigDecimal(form.get(key)).longValueExact());JsonObject in=new JsonObject();in.add("settings",next);send("settings",in);}catch(RuntimeException e){data.addProperty("message","marriage.error.settings");}},true);action("recovery","retry","retry",true);field("union_id","adminUnion","");field("reason","adminReason","");button("admin_cancel","cancel",()->confirm(()->{JsonObject in=new JsonObject();in.addProperty("union",form.get("adminUnion"));in.addProperty("reason",form.get("adminReason"));send("admin_cancel",in);}),true);for(var e:array(data,"records")){var r=e.getAsJsonObject();text("record",str(r,"id")+" · "+str(r,"a")+" & "+str(r,"b")+" · "+str(r,"phase"));}}
        }else for(var entry:array(data,"history")){JsonObject row=entry.getAsJsonObject();String action=str(row,"action"),detail=str(row,"detail");if(action.startsWith("transaction_")&&detail.contains(":")){try{detail=money(Long.parseLong(detail.substring(detail.lastIndexOf(':')+1)));}catch(NumberFormatException ignored){}}else if(action.startsWith("payment_"))detail="";text("audit."+action,java.time.Instant.ofEpochMilli(number(row,"at")).atOffset(java.time.ZoneOffset.UTC).toLocalDateTime().toString()+" · "+detail);}
    }
    @Override protected void init(){capture();fields.clear();w=Math.min(680,width-16);h=Math.min(410,height-16);x=(width-w)/2;y=(height-h)/2;rows();int header=document?48:72;visible=Math.max(1,(h-header-44)/26);offset=Math.clamp(offset,0,Math.max(0,rows.size()-visible));
        if(!document){List<String> tabs=new ArrayList<>(List.of("home","ceremony","terms","account","journal","effects"));if(bool(data,"admin")||bool(data,"governor"))tabs.add("admin");int bw=(w-20)/tabs.size();for(int i=0;i<tabs.size();i++){String key=tabs.get(i);addRenderableWidget(MarriageTheme.button(x+10+i*bw,y+38,bw-3,20,tr(key),()->change(key)));}}
        for(int i=0;i<visible&&offset+i<rows.size();i++){Row row=rows.get(offset+i);int yy=y+header+i*26;int right=x+w/2;int rw=w/2-15;if(row.field!=null){EditBox edit=new EditBox(font,right,yy+2,rw,20,tr(row.label));edit.setMaxLength(row.field.equals("adminReason")?256:64);edit.setValue(form.getOrDefault(row.field,""));fields.put(row.field,edit);addRenderableWidget(edit);}else if(row.action!=null){var b=addRenderableWidget(MarriageTheme.button(right,yy+2,rw,20,Component.literal(row.value),row.action));b.active=row.enabled&&!waiting;b.setTooltip(Tooltip.create(Component.literal(row.value)));}}
        int fy=y+h-29;addRenderableWidget(MarriageTheme.button(x+10,fy,75,20,tr("close"),this::onClose));var previous=addRenderableWidget(MarriageTheme.button(x+w/2-53,fy,27,20,Component.literal("‹"),()->{offset=Math.max(0,offset-visible);rebuildWidgets();}));previous.active=offset>0;var next=addRenderableWidget(MarriageTheme.button(x+w/2+26,fy,27,20,Component.literal("›"),()->{offset=Math.min(Math.max(0,rows.size()-visible),offset+visible);rebuildWidgets();}));next.active=offset+visible<rows.size();if(!document)addRenderableWidget(MarriageTheme.button(x+w-85,fy,75,20,tr("refresh"),()->send("refresh",new JsonObject())));
    }
    @Override public void tick(){if(waiting&&System.nanoTime()-waitingAt>5000000000L){waiting=false;rebuildWidgets();}if(!document&&++ticks%100==0&&!waiting)send("refresh",new JsonObject());}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xB51D1520);panel(g,x,y,w,h);rings(g,x+10,y+7,23);g.drawString(font,title,x+39,y+16,0xFFFFEBC9,false);String name=font.plainSubstrByWidth(str(data,"name"),w/3);g.drawString(font,name,x+w-14-font.width(name),y+16,0xFFFFEBC9,false);int header=document?48:72;Component tooltip=null;
        for(int i=0;i<visible&&offset+i<rows.size();i++){Row row=rows.get(offset+i);int yy=y+header+i*26;if(i%2==0)g.fill(x+10,yy,x+w-10,yy+25,0x15934D56);Component label=Component.translatableWithFallback("marriage."+row.label,row.label);g.drawString(font,font.plainSubstrByWidth(label.getString(),w/2-20),x+15,yy+8,INK,false);if(row.action==null&&row.field==null){g.drawString(font,font.plainSubstrByWidth(row.value,w/2-25),x+w/2,yy+8,GREEN,false);}if(mx>x+10&&mx<x+w-10&&my>=yy&&my<yy+25)tooltip=Component.literal(label.getString()+"\n"+row.value);}
        if(!str(data,"message").isBlank())g.drawString(font,font.plainSubstrByWidth(Component.translatable(str(data,"message")).getString(),w-20),x+11,y+h-43,0xFF9D3143,false);g.drawCenteredString(font,(Math.min(rows.size(),offset+1))+"–"+Math.min(rows.size(),offset+visible)+" / "+rows.size(),x+w/2,y+h-22,MUTED);super.render(g,mx,my,partial);if(tooltip!=null)g.renderTooltip(font,font.split(tooltip,Math.min(330,width-20)),mx,my);
    }
    @Override public boolean mouseScrolled(double mx,double my,double hx,double vy){offset=Math.clamp(offset-(int)Math.signum(vy),0,Math.max(0,rows.size()-visible));rebuildWidgets();return true;}
    @Override public void onClose(){if(!document)MarriageNetwork.request("close",new JsonObject());super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
}
