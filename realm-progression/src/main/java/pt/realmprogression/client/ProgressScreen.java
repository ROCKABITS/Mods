package pt.realmprogression.client;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import pt.realmprogression.menu.ProgressMenu;
import pt.realmprogression.network.ProgressNetwork;

/** Responsive parchment detail, royal-blue catalogue and explicit administrative confirmation. */
public final class ProgressScreen extends AbstractContainerScreen<ProgressMenu> {
    private JsonObject data=new JsonObject();private EditBox search,goal,namespaces;
    private String draftGoal="",draftNamespaces="",draftId="",searchText="";private boolean waiting,chronicle;private JsonObject confirmation;
    private long lastQuery,searchAt;private BiConsumer<String,JsonObject> requestSender=ProgressNetwork::request;
    private int lx,ly,lw,rx,rw;private boolean compact;
    public ProgressScreen(ProgressMenu m,Inventory i,Component title){super(m,i,title);}
    private static Component tr(String key,Object...args){return Component.translatable("gui.realmprogression."+key,args);}
    public static void receive(String json){var value=JsonParser.parseString(json).getAsJsonObject();if(Minecraft.getInstance().screen instanceof ProgressScreen s&&value.get("container").getAsInt()==s.menu.containerId)s.accept(value);}
    private void accept(JsonObject value){capture();data=value;waiting=false;searchText=value.get("search").getAsString();var selected=selected();String id=selected==null?"":selected.get("id").getAsString();if(!id.equals(draftId)){draftId=id;draftGoal=selected==null?"":money(selected.get("goal").getAsLong());draftNamespaces=selected==null?"":selected.get("namespaces").getAsString();}rebuild();}
    private void capture(){if(search!=null)searchText=search.getValue();if(goal!=null)draftGoal=goal.getValue();if(namespaces!=null)draftNamespaces=namespaces.getValue();}
    @Override protected void init(){capture();imageWidth=Math.min(width-16,850);imageHeight=Math.min(height-16,490);super.init();titleLabelY=-1000;inventoryLabelY=-1000;rebuild();}
    private JsonObject selected(){return data.has("selected")?data.getAsJsonObject("selected"):null;}
    private boolean admin(){return data.has("admin")&&data.get("admin").getAsBoolean();}
    private ResearchArt.Button button(int x,int y,int w,int h,Component text,Runnable action){return addRenderableWidget(new ResearchArt.Button(x,y,w,h,text,action));}
    private EditBox field(int x,int y,int w,int h,Component label,String value,int max){var e=new EditBox(font,x,y,w,h,label);e.setMaxLength(max);e.setValue(value);return addRenderableWidget(e);}
    private void rebuild(){
        if(font==null)return;clearWidgets();search=null;goal=null;namespaces=null;compact=imageHeight<300;
        lx=leftPos+12;ly=topPos+12;lw=Math.max(108,(imageWidth-36)*37/100);rx=lx+lw+12;rw=imageWidth-36-lw;
        button(leftPos+imageWidth-30,topPos+10,18,18,Component.literal("×"),this::onClose);
        if(confirmation!=null){button(lx,topPos+imageHeight-34,(imageWidth-30)/2,20,tr("back"),()->{confirmation=null;rebuild();});button(leftPos+imageWidth/2+3,topPos+imageHeight-34,(imageWidth-30)/2,20,tr("confirm"),()->{var body=confirmation;confirmation=null;request("configure",body);});return;}
        if(!data.has("mods"))return;
        button(lx,topPos+35,lw,18,tr(admin()?(data.get("browse").getAsBoolean()?"browse":"goals"):"goals"),()->{if(admin())query(0,"",!data.get("browse").getAsBoolean());}).active=admin();
        search=field(lx,topPos+57,lw,18,tr("search"),searchText,64);search.setHint(tr("search"));search.setResponder(v->{searchText=v;searchAt=System.currentTimeMillis();});
        int rowHeight=Math.min(31,Math.max(16,(imageHeight-115)/6));int index=0;
        for(var raw:data.getAsJsonArray("mods")){
            var row=raw.getAsJsonObject();String id=row.get("id").getAsString();var b=button(lx,topPos+81+index*rowHeight,lw,rowHeight-2,ResearchArt.fit(font,Component.literal(row.get("name").getAsString()),lw-12),()->{chronicle=false;query(data.get("page").getAsInt(),id,data.get("browse").getAsBoolean());});b.selected=id.equals(draftId);b.setTooltip(Tooltip.create(Component.literal(id).append("\n").append(status(row))));index++;
        }
        button(lx,topPos+imageHeight-31,25,18,Component.literal("‹"),()->query(Math.max(0,data.get("page").getAsInt()-1),draftId,data.get("browse").getAsBoolean())).active=data.get("page").getAsInt()>0;
        button(lx+lw-25,topPos+imageHeight-31,25,18,Component.literal("›"),()->query(data.get("page").getAsInt()+1,draftId,data.get("browse").getAsBoolean())).active=data.get("page").getAsInt()+1<data.get("pages").getAsInt();
        button(rx+rw-66,topPos+36,66,18,tr(chronicle?"details":"history"),()->{capture();chronicle=!chronicle;rebuild();});
        var row=selected();if(row==null||chronicle)return;
        if(admin()){
            int y=topPos+(compact?119:165);goal=field(rx+10,y,rw-20,18,tr("goal"),draftGoal,18);
            namespaces=field(rx+10,topPos+(compact?157:219),rw-20,18,tr("namespaces"),draftNamespaces,520);namespaces.setTooltip(Tooltip.create(tr("namespace_help")));
            boolean configurable=row.get("installed").getAsBoolean()&&!row.get("protected").getAsBoolean();
            button(rx+10,topPos+imageHeight-35,(rw-24)/2,20,tr("save"),()->confirm(true)).active=configurable;
            button(rx+14+(rw-24)/2,topPos+imageHeight-35,(rw-24)/2,20,tr("free"),()->confirm(false)).active=configurable&&row.get("enabled").getAsBoolean();
        }
    }
    private void confirm(boolean enabled){capture();try{
        long cents=new BigDecimal(draftGoal.replace(',','.')).movePointRight(2).longValueExact();if(cents<1)throw new IllegalArgumentException();
        var body=new JsonObject();body.add("nonce",data.get("nonce"));body.add("revision",data.get("revision"));body.addProperty("mod",draftId);body.addProperty("goal",cents);body.addProperty("enabled",enabled);body.addProperty("namespaces",draftNamespaces.replace(" ",""));confirmation=body;rebuild();
    }catch(RuntimeException bad){data.addProperty("error","error.realmprogression.amount");}}
    private void request(String op,JsonObject body){waiting=true;lastQuery=System.currentTimeMillis();requestSender.accept(op,body);}
    private void query(int page,String selected,boolean browse){var body=new JsonObject();body.addProperty("search",searchText);body.addProperty("selected",selected);body.addProperty("page",page);body.addProperty("browse",browse);request("query",body);}
    @Override protected void containerTick(){super.containerTick();long now=System.currentTimeMillis();if(!data.has("mods"))return;if(waiting&&now-lastQuery<5000)return;if(searchAt>0&&now-searchAt>400){searchAt=0;query(0,draftId,data.get("browse").getAsBoolean());}else if(confirmation==null&&now-lastQuery>2500)query(data.get("page").getAsInt(),draftId,data.get("browse").getAsBoolean());}
    private static String money(long cents){return BigDecimal.valueOf(cents,2).toPlainString();}
    private static Component status(JsonObject row){return tr(!row.get("installed").getAsBoolean()?"absent":row.get("protected").getAsBoolean()?"protected":row.get("unlocked").getAsBoolean()?"unlocked":row.get("enabled").getAsBoolean()?"locked":"available");}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){renderBg(g,partial,mx,my);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        ResearchArt.panel(g,leftPos,topPos,imageWidth,imageHeight);g.drawString(font,ResearchArt.fit(font,tr("title"),lw),lx,ly,ResearchArt.GOLD,false);
        if(!data.has("balance")){g.drawCenteredString(font,tr("loading"),leftPos+imageWidth/2,topPos+imageHeight/2,ResearchArt.TEXT);return;}
        g.drawString(font,ResearchArt.fit(font,tr("treasury",money(data.get("balance").getAsLong())),rw-32),rx,ly,ResearchArt.GOLD,false);
        if(confirmation!=null){ResearchArt.paper(g,lx,topPos+38,imageWidth-24,imageHeight-81);ResearchArt.seal(g,leftPos+imageWidth/2,topPos+64,false);var text=tr("confirmation",draftId,money(confirmation.get("goal").getAsLong()),tr(confirmation.get("enabled").getAsBoolean()?"locked":"available"));g.drawWordWrap(font,text,lx+12,topPos+89,imageWidth-48,ResearchArt.INK);return;}
        ResearchArt.paper(g,rx,topPos+33,rw,imageHeight-45);
        if(data.has("pages"))g.drawCenteredString(font,(data.get("page").getAsInt()+1)+" / "+data.get("pages").getAsInt(),lx+lw/2,topPos+imageHeight-25,ResearchArt.MUTED);
        if(chronicle){int y=topPos+65;for(var raw:data.getAsJsonArray("history")){var entry=raw.getAsJsonObject();String text=entry.get("name").getAsString()+" · "+entry.get("mod").getAsString();g.drawString(font,ResearchArt.fit(font,Component.literal(text),rw-20),rx+10,y,ResearchArt.INK,false);y+=11;g.drawString(font,ResearchArt.fit(font,tr(entry.get("action").getAsString().equals("UNLOCK")?"unlocked":"changed"),rw-20),rx+10,y,0xFF796347,false);y+=17;if(y>topPos+imageHeight-45)break;}return;}
        var row=selected();if(row==null){g.drawWordWrap(font,tr("empty"),rx+10,topPos+68,rw-20,ResearchArt.INK);return;}
        g.drawString(font,ResearchArt.fit(font,Component.literal(row.get("name").getAsString()).withStyle(s->s.withBold(true)),rw-90),rx+10,topPos+42,ResearchArt.INK,false);
        g.drawString(font,ResearchArt.fit(font,Component.literal(row.get("id").getAsString()),rw-20),rx+10,topPos+59,0xFF796347,false);
        g.drawString(font,status(row),rx+10,topPos+76,row.get("unlocked").getAsBoolean()?0xFF21624D:ResearchArt.INK,false);
        int by=topPos+91,bw=rw-20;g.fill(rx+10,by,rx+10+bw,by+8,0xFF5A5145);long target=row.get("goal").getAsLong(),balance=data.get("balance").getAsLong();int fill=row.get("unlocked").getAsBoolean()?bw:(int)Math.round(bw*Math.min(1,(double)balance/Math.max(1,target)));g.fill(rx+11,by+1,rx+10+Math.max(1,fill),by+7,row.get("unlocked").getAsBoolean()?0xFF39765F:0xFFD7AB54);
        if(admin()){
            g.drawString(font,tr("goal"),rx+10,topPos+(compact?107:151),ResearchArt.INK,false);g.drawString(font,tr("namespaces"),rx+10,topPos+(compact?145:205),ResearchArt.INK,false);
            if(!compact){g.drawWordWrap(font,tr("coverage",row.get("recipes").getAsInt()),rx+10,topPos+253,rw-20,ResearchArt.INK);g.drawWordWrap(font,tr("scope"),rx+10,topPos+289,rw-20,0xFF796347);}
        }else{
            g.drawWordWrap(font,tr("target",money(target)),rx+10,topPos+112,rw-20,ResearchArt.INK);g.drawWordWrap(font,tr("permanent"),rx+10,topPos+(compact?139:155),rw-20,ResearchArt.INK);if(!compact){ResearchArt.seal(g,rx+rw/2,topPos+245,row.get("unlocked").getAsBoolean());g.drawWordWrap(font,tr("scope"),rx+12,topPos+280,rw-24,0xFF796347);}
        }
        if(!data.get("treasuryAvailable").getAsBoolean())g.drawString(font,tr("treasury_error"),lx,topPos+imageHeight-44,0xFFFFB5A7,false);
        String error=data.has("error")?data.get("error").getAsString():"";if(!error.isEmpty())g.drawString(font,ResearchArt.fit(font,Component.translatable(error),imageWidth-26),lx,topPos+imageHeight-11,0xFFFFB5A7,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
}
