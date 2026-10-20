package pt.contracts.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pt.contracts.daily.*;
import pt.contracts.network.ContractsNetwork;

/** Five simultaneous quests on the physical board, with a private journal and no remote acceptance. */
public final class DailyScreen extends Screen {
    private JsonObject data;
    private int x,y,w,h,rowHeight,historyPage,ticks;
    private boolean history,waiting;
    private long receivedAt,waitingSince;
    private Component hover;
    public DailyScreen(JsonObject data){super(DailyTheme.tr("title"));accept(data);}
    public JsonObject data(){return data;}
    public void accept(JsonObject data){this.data=data;receivedAt=System.nanoTime();waiting=false;if(minecraft!=null)rebuildWidgets();}
    public static void receive(String json){
        try{DailyRules.jsonEnvelope(json,65536);JsonObject data=JsonParser.parseString(json).getAsJsonObject();var mc=Minecraft.getInstance();
            if(data.has("close")&&data.get("close").getAsBoolean()){if(mc.screen instanceof DailyScreen||mc.screen instanceof DailyAdminScreen)mc.setScreen(null);return;}
            if(mc.screen instanceof DailyVisualScreen)return;
            if(mc.screen instanceof DailyAdminScreen screen)screen.accept(data);
            else if(mc.screen instanceof DailyScreen screen)screen.accept(data);else mc.setScreen(new DailyScreen(data));
        }catch(RuntimeException ignored){}
    }
    private JsonObject round(){if(history){var list=array(data,"history");if(!list.isEmpty()){historyPage=Math.clamp(historyPage,0,list.size()-1);return list.get(historyPage).getAsJsonObject();}}return data.getAsJsonObject("round");}
    @Override protected void init(){
        w=Math.min(720,width-16);h=Math.min(410,height-16);x=(width-w)/2;y=(height-h)/2;
        int tabs=5,tw=(w-28)/tabs,ty=y+(h<300?27:34);
        button(x+10,ty,tw,"quests",()->{history=false;rebuildWidgets();});
        button(x+14+tw,ty,tw,"history",()->{history=!history;historyPage=0;rebuildWidgets();});
        var govern=button(x+18+tw*2,ty,tw,"government",()->minecraft.setScreen(new DailyAdminScreen(this,data,false)));govern.active=bool(data,"governor");
        var admin=button(x+22+tw*3,ty,tw,"admin",()->minecraft.setScreen(new DailyAdminScreen(this,data,true)));admin.active=bool(data,"admin");
        button(x+26+tw*4,ty,tw,"visuals",()->minecraft.setScreen(new DailyVisualScreen(this)));
        JsonObject round=round();if(round==null)return;var quests=array(round,"quests");rowHeight=Math.max(18,(h-(h<300?99:135))/5);
        for(int i=0;i<quests.size()&&i<5;i++){
            JsonObject q=quests.get(i).getAsJsonObject(),template=q.getAsJsonObject("template");String state=str(q,"state");
            String key=state.equals("AVAILABLE")?"accept":state.equals("ACTIVE")&&str(template,"category").equals("DELIVERY")?"deliver":state.equals("PENDING")?"pending":"state."+state.toLowerCase(Locale.ROOT);
            int bh=Math.min(21,rowHeight-6),bw=w<450?64:105;
            var b=button(x+w-bw-16,y+(h<300?64:82)+i*rowHeight+(rowHeight-bh)/2,bw,key,()->{JsonObject request=new JsonObject();request.addProperty("id",str(q,"id"));waiting=true;waitingSince=System.nanoTime();DailyNetwork.request(state.equals("AVAILABLE")?"accept":"deliver",request);rebuildWidgets();});
            b.active=!history&&!waiting&&(state.equals("AVAILABLE")||state.equals("ACTIVE")&&str(template,"category").equals("DELIVERY"));
            b.setTooltip(Tooltip.create(details(q,round)));
        }
        int fy=y+h-30;button(x+10,fy,Math.min(110,(w-28)/3),"back",()->ContractsNetwork.send("refresh","","","",0,0,""));
        var retry=button(x+w/2-Math.min(110,(w-28)/3)/2,fy,Math.min(110,(w-28)/3),"retry",()->request("retry"));retry.active=!waiting&&number(data,"pending")>0;
        button(x+w-Math.min(110,(w-28)/3)-10,fy,Math.min(110,(w-28)/3),"close",this::onClose);
    }
    private net.minecraft.client.gui.components.Button button(int bx,int by,int bw,String key,Runnable callback){return addRenderableWidget(DailyTheme.button(bx,by,bw,h<300?17:21,DailyTheme.tr(key),callback));}
    private void request(String operation){waiting=true;waitingSince=System.nanoTime();DailyNetwork.request(operation,new JsonObject());}
    @Override public void tick(){if(waiting&&System.nanoTime()-waitingSince>5_000_000_000L){waiting=false;rebuildWidgets();}if(++ticks%100==0&&!waiting)request("refresh");}
    private Component details(JsonObject quest,JsonObject round){
        JsonObject t=quest.getAsJsonObject("template");String detail=label(str(t,"title")).getString()+"\n"+DailyTheme.tr("category."+str(t,"category").toLowerCase(Locale.ROOT)).getString()+" · "+DailyTheme.tr("difficulty."+str(t,"difficulty").toLowerCase(Locale.ROOT)).getString();
        detail+="\n"+DailyTheme.tr("objective",number(t,"quantity"),target(t)).getString()+"\n"+DailyTheme.tr("gross",amount(round,number(quest,"gross"))).getString();
        detail+="\n"+DailyTheme.tr("bonus",amount(round,number(quest,"bonus"))).getString()+"\n"+DailyTheme.tr("reputation",number(quest,"reputation")).getString();
        JsonObject r=reward(str(quest,"id"));long tax=r==null?DailyRules.tax(number(quest,"gross"),bool(data,"treasury")?(int)number(data.getAsJsonObject("settings"),"taxBps"):0):number(r,"tax");
        detail+="\n"+DailyTheme.tr("tax",amount(round,tax)).getString()+"\n"+DailyTheme.tr("net",amount(round,number(quest,"gross")-tax)).getString();
        if(str(round,"currency").equals("EMERALD"))detail+="\n"+DailyTheme.tr("emerald_mode").getString();
        if(r!=null&&!str(r,"error").isBlank())detail+="\n"+Component.translatable(str(r,"error")).getString();
        return Component.literal(detail);
    }
    private JsonObject reward(String id){for(var e:array(data,"rewards"))if(str(e.getAsJsonObject(),"id").equals(id))return e.getAsJsonObject();return null;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        hover=null;g.fill(0,0,width,height,0xC5121D19);DailyTheme.panel(g,x,y,w,h);
        DailyTheme.seal(g,x+12,y+7,h<300?20:27,"MEDIUM");g.drawString(font,DailyTheme.tr("title"),x+47,y+13,DailyTheme.GOLD,false);
        String name=font.plainSubstrByWidth(str(data,"name"),w/3);g.drawString(font,name,x+w-15-font.width(name),y+14,DailyTheme.PALE,false);
        JsonObject round=round();if(round!=null){
            long elapsed=(System.nanoTime()-receivedAt)/1_000_000,remaining=Math.max(0,number(round,"resetAt")-number(data,"now")-elapsed);
            String clock=String.format(Locale.ROOT,"%02d:%02d:%02d",remaining/3600000,remaining/60000%60,remaining/1000%60);
            var heading=history?DailyTheme.tr("archive_day",java.time.Instant.ofEpochMilli(number(round,"generatedAt")).atOffset(java.time.ZoneOffset.UTC).toLocalDate()):DailyTheme.tr("reset_in",clock);
            g.drawString(font,heading,x+13,y+(h<300?49:64),DailyTheme.PALE,false);
            var cap=DailyTheme.tr("daily_total",amount(round,number(round,"grossCompleted")),amount(round,number(round,"cap")));
            if(w>500)g.drawString(font,cap,x+w-13-font.width(cap),y+(h<300?49:64),DailyTheme.GOLD,false);
            var quests=array(round,"quests");
            for(int i=0;i<quests.size()&&i<5;i++){
                JsonObject q=quests.get(i).getAsJsonObject(),t=q.getAsJsonObject("template");int top=y+(h<300?62:80)+i*rowHeight;
                int right=x+w-(w<450?88:130),color=DailyTheme.color(str(t,"difficulty"));
                g.fill(x+11,top,x+w-11,top+rowHeight-3,DailyTheme.PAPER);g.fill(x+11,top,x+15,top+rowHeight-3,color);
                if(rowHeight>32)DailyTheme.seal(g,x+20,top+9,27,str(t,"difficulty"));
                int left=x+(rowHeight>32?57:22),available=right-left;
                String title=font.plainSubstrByWidth(label(str(t,"title")).getString(),available-5);
                g.drawString(font,title,left,top+6,DailyTheme.INK,false);
                int quantity=(int)number(t,"quantity"),progress=(int)number(q,"progress");
                if(rowHeight>32){
                    String line=DailyTheme.tr("category."+str(t,"category").toLowerCase(Locale.ROOT)).getString()+" · "+DailyTheme.tr("difficulty."+str(t,"difficulty").toLowerCase(Locale.ROOT)).getString();
                    g.drawString(font,font.plainSubstrByWidth(line,Math.max(30,available-115)),left,top+20,DailyTheme.MUTED,false);
                    String value=amount(round,number(q,"gross"));g.drawString(font,value,right-font.width(value),top+20,DailyTheme.INK,false);
                    int barWidth=Math.max(20,available-65);g.fill(left,top+rowHeight-11,left+barWidth,top+rowHeight-7,0xFFC6B68D);g.fill(left,top+rowHeight-11,left+(int)(barWidth*Math.min(1.0,progress/(double)quantity)),top+rowHeight-7,0xFF42684E);
                    g.drawString(font,progress+"/"+quantity,left+barWidth+6,top+rowHeight-14,DailyTheme.MUTED,false);
                }else{String progressText=progress+"/"+quantity+" · "+amount(round,number(q,"gross"));g.drawString(font,font.plainSubstrByWidth(progressText,available),left,top+16,DailyTheme.MUTED,false);}
                if(mx>=x+11&&mx<right&&my>=top&&my<top+rowHeight-3)hover=details(q,round);
            }
            if(history&&h>=300)g.drawCenteredString(font,DailyTheme.tr("archive_scroll",historyPage+1,array(data,"history").size()),x+w/2,y+h-47,DailyTheme.GOLD);
            else if(h>=300){String notice=str(data,"message");Component footer=!notice.isBlank()?Component.translatable(notice):bool(data,"clockBackwards")?DailyTheme.tr("clock_warning"):DailyTheme.tr("net_today",amount(round,number(data,"netToday")));g.drawCenteredString(font,font.plainSubstrByWidth(footer.getString(),w-25),x+w/2,y+h-47,notice.isBlank()?DailyTheme.GOLD:0xFFFFC1A1);}
        }
        super.render(g,mx,my,partial);if(hover!=null)g.renderTooltip(font,font.split(hover,Math.min(330,width-24)),mx,my);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(history&&!array(data,"history").isEmpty()){historyPage=Math.clamp(historyPage-(int)Math.signum(vertical),0,array(data,"history").size()-1);rebuildWidgets();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public boolean isPauseScreen(){return false;}
    public static String str(JsonObject o,String key){return o!=null&&o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    public static long number(JsonObject o,String key){return o!=null&&o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsLong():0;}
    public static boolean bool(JsonObject o,String key){return o!=null&&o.has(key)&&!o.get(key).isJsonNull()&&o.get(key).getAsBoolean();}
    public static JsonArray array(JsonObject o,String key){return o!=null&&o.has(key)&&o.get(key).isJsonArray()?o.getAsJsonArray(key):new JsonArray();}
    private static String amount(JsonObject round,long cents){return str(round,"currency").equals("EMERALD")?DailyTheme.tr("emerald_amount",cents/100).getString():DailyTheme.money(cents);}
    public static Component label(String key){return Component.translatableWithFallback(key,key);}
    public static String target(JsonObject t){String target=str(t,"target");if(target.startsWith("#")){String key="daily.contracts.target."+target.substring(target.indexOf(':')+1);return Component.translatableWithFallback(key,target.substring(1)).getString();}if(target.startsWith("*"))return DailyTheme.tr("target."+target.substring(1)).getString();String category=str(t,"category");String validation=str(t,"validation");String special="daily.contracts.target."+target.replace(':','.');if(net.minecraft.client.resources.language.I18n.exists(special))return Component.translatable(special).getString();String prefix=validation.equals("NEW_BIOME")?"biome.":validation.equals("NEW_STRUCTURE")?"structure.":category.equals("COMBAT")?"entity.":category.equals("MINING")||category.equals("FARMING")?"block.":"item.";String translation=prefix+target.replace(':','.').replace('/','.');return Component.translatableWithFallback(translation,target).getString();}
}
