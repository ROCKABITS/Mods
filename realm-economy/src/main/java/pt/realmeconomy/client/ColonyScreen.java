package pt.realmeconomy.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import pt.realmeconomy.menu.ColonyMenu;
import pt.realmeconomy.network.TradeNetwork;

/** Original blue-and-brass municipal ledger. Every payment has an explicit confirmation. */
public final class ColonyScreen extends AbstractContainerScreen<ColonyMenu> {
    private static JsonObject pending;
    private JsonObject data=new JsonObject();private int x,y,w,h,top,bottom,page,ticks;private String tab="account",amountDraft="",confirmation="",operation="";private JsonObject proposed;
    private boolean waiting;private EditBox amount;private final Map<String,EditBox> fields=new LinkedHashMap<>();private final Map<String,String> drafts=new HashMap<>();private boolean enabled;
    private java.util.function.BiConsumer<String,JsonObject> requestSender=TradeNetwork::request;
    private static final int BLUE=0xFF152B3B,CREAM=0xFFF2E6CC,GOLD=0xFFE1BB6B,MUTED=0xFFACB9B6;
    public ColonyScreen(ColonyMenu menu,Inventory inventory,Component title){super(menu,inventory,title);if(pending!=null&&num(pending,"container")==menu.containerId){data=pending;pending=null;}}
    public static void receive(String json){var next=JsonParser.parseString(json).getAsJsonObject();var mc=Minecraft.getInstance();if(mc.screen instanceof ColonyScreen s&&num(next,"container")==s.menu.containerId){s.preserve();s.data=next;s.waiting=false;s.rebuild();}else pending=next;}
    private static Component tr(String key,Object...args){return Component.translatable("gui.realmeconomy.colony_"+key,args);}
    private static String str(JsonObject o,String key){return o.has(key)?o.get(key).getAsString():"";}
    private static long num(JsonObject o,String key){return o.has(key)?o.get(key).getAsLong():0;}
    private static boolean flag(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    private static String money(long n){return n<0?"—":BigDecimal.valueOf(n,2).toPlainString()+" €";}
    private JsonArray list(String key){return data.has(key)?data.getAsJsonArray(key):new JsonArray();}
    private JsonObject policy(){return data.has("policy")?data.getAsJsonObject("policy"):new JsonObject();}
    private boolean compact(){return w<550||h<320;}
    private int rowHeight(){return compact()?31:49;}
    private int rowCount(){return Math.max(1,(bottom-top-25)/rowHeight());}
    private void preserve(){if(amount!=null)amountDraft=amount.getValue();fields.forEach((k,v)->drafts.put(k,v.getValue()));}
    private void rebuild(){if(minecraft!=null)init(minecraft,width,height);}
    private void change(String value){preserve();tab=value;page=0;confirmation="";rebuild();}
    private Button button(Component text,int bx,int by,int bw,int bh,Runnable action){var b=addRenderableWidget(Button.builder(text,z->action.run()).bounds(bx,by,Math.max(15,bw),bh).build(BankButton::new));b.setTooltip(Tooltip.create(text));return b;}
    private EditBox input(int xx,int yy,int ww,String value,Component label){var e=new EditBox(font,xx,yy,Math.max(25,ww),19,label);e.setMaxLength(16);e.setValue(value);return addRenderableWidget(e);}
    @Override protected void init(){
        clearWidgets();amount=null;fields.clear();w=Math.min(800,width-16);h=Math.min(450,height-16);x=(width-w)/2;y=(height-h)/2;top=y+(compact()?59:74);bottom=y+h-36;
        var tabs=new ArrayList<>(List.of("account","exports","invoices","history"));if(flag(data,"administrator"))tabs.add("admin");int tw=(w-24-(tabs.size()-1)*3)/tabs.size();
        for(int i=0;i<tabs.size();i++){String value=tabs.get(i);button(tr(value),x+12+i*(tw+3),y+(compact()?33:45),tw,17,()->change(value)).active=!waiting&&!tab.equals(value);}
        button(tr("close"),x+w-67,y+h-25,54,16,this::onClose);
        if(!confirmation.isEmpty()){confirmControls();return;}
        if(tab.equals("account")){accountControls();return;}if(tab.equals("admin")){adminControls();return;}
        var rows=list(tab.equals("exports")?"exports":tab.equals("invoices")?"invoices":"history");page=Math.clamp(page,0,Math.max(0,(rows.size()-1)/rowCount()));
        for(int i=0;i<rowCount()&&page*rowCount()+i<rows.size();i++){
            var row=rows.get(page*rowCount()+i).getAsJsonObject();int yy=top+3+i*rowHeight();
            if(tab.equals("exports"))button(tr(flag(row,"delivered")?"delivered":"deliver"),x+w-(compact()?72:112),yy+5,compact()?56:94,compact()?19:29,()->{
                var body=new JsonObject();body.addProperty("order",str(row,"id"));ask("clnExport",body,tr("export_confirmation",num(row,"quantity"),item(row).getHoverName().getString(),money(num(row,"cents"))).getString());
            }).active=!waiting&&flag(data,"manager")&&flag(data,"walletAvailable")&&!flag(row,"delivered")&&flag(policy(),"enabled")&&num(row,"available")>=num(row,"quantity")&&num(data,"earned")+num(row,"cents")<=num(policy(),"dailyCap")&&num(data,"personalEarned")+num(row,"cents")<=num(policy(),"playerCap");
            if(tab.equals("invoices"))button(tr(flag(row,"paid")?"paid":"pay"),x+w-(compact()?72:112),yy+5,compact()?56:94,compact()?19:29,()->{
                var body=new JsonObject();body.addProperty("invoice",str(row,"id"));ask("clnPay",body,tr("pay_confirmation",str(row,"title"),money(num(row,"cents"))).getString());
            }).active=!waiting&&flag(data,"manager")&&flag(data,"walletAvailable")&&flag(policy(),"enabled")&&!flag(row,"paid")&&num(data,"balance")>=num(row,"cents");
        }
        button(Component.literal("‹"),x+13,bottom-19,27,17,()->{page--;rebuild();}).active=page>0;
        button(Component.literal("›"),x+44,bottom-19,27,17,()->{page++;rebuild();}).active=(page+1)*rowCount()<rows.size();
    }
    private void accountControls(){
        int yy=top+(compact()?68:124),ww=w-32;amount=input(x+17,yy,compact()?ww:ww/2,amountDraft,tr("amount"));
        int by=yy+25,bw=(ww-5)/2;
        button(tr("deposit"),x+17,by,bw,22,()->transfer(true)).active=!waiting&&flag(data,"walletAvailable");
        button(tr("withdraw"),x+22+bw,by,bw,22,()->transfer(false)).active=!waiting&&flag(data,"walletAvailable")&&flag(data,"manager");
    }
    private void transfer(boolean deposit){try{var n=new BigDecimal(amount.getValue().replace(',','.')).movePointRight(2).longValueExact();if(n<1)throw new IllegalArgumentException();var body=new JsonObject();body.addProperty("amount",n);ask(deposit?"clnDeposit":"clnWithdraw",body,tr(deposit?"deposit_confirmation":"withdraw_confirmation",money(n)).getString());}catch(RuntimeException invalid){data.addProperty("error","error.realmeconomy.bounds");}}
    private void ask(String op,JsonObject body,String description){preserve();operation=op;proposed=body;confirmation=description;rebuild();}
    private void confirmControls(){button(tr("cancel"),x+18,bottom-25,(w-43)/2,22,()->{confirmation="";rebuild();});button(tr("confirm"),x+25+(w-43)/2,bottom-25,(w-43)/2,22,()->{String op=operation;var body=proposed;confirmation="";send(op,body);}).active=!waiting;}
    private void send(String op,JsonObject body){if(waiting)return;body.addProperty("nonce",str(data,"nonce"));preserve();waiting=true;requestSender.accept(op,body);rebuild();}
    private void adminControls(){
        String[] keys={"dailyCap","playerCap","buildCents","recruitCents","orders"};int cw=(w-44)/2,dy=compact()?36:52;
        for(int i=0;i<keys.length;i++){String key=keys[i];String value=drafts.getOrDefault(key,key.equals("orders")?Long.toString(num(policy(),key)):BigDecimal.valueOf(num(policy(),key),2).toPlainString());fields.put(key,input(x+17+(i%2)*(cw+10),top+15+(i/2)*dy,cw,value,tr(key)));}
        enabled=flag(policy(),"enabled");button(tr(enabled?"enabled":"disabled"),x+27+cw,top+15+2*dy,cw,19,()->{var body=policyPayload();body.addProperty("enabled",!enabled);ask("clnPolicy",body,tr("policy_confirmation").getString());});
        button(tr("save"),x+17,bottom-23,w-34,21,()->{try{ask("clnPolicy",policyPayload(),tr("policy_confirmation").getString());}catch(RuntimeException invalid){data.addProperty("error","error.realmeconomy.bounds");}}).active=!waiting;
    }
    private JsonObject policyPayload(){var body=new JsonObject();for(var entry:fields.entrySet()){BigDecimal value=new BigDecimal(entry.getValue().getValue().replace(',','.'));body.addProperty(entry.getKey(),entry.getKey().equals("orders")?value.longValueExact():value.movePointRight(2).longValueExact());}body.addProperty("enabled",enabled);return body;}
    private ItemStack item(JsonObject row){try{return ItemStack.OPTIONAL_CODEC.parse(minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),row.get("item")).getOrThrow();}catch(RuntimeException e){return ItemStack.EMPTY;}}
    private static String reference(JsonObject row){String value=str(row,"reference");int split=value.indexOf(" | ");return split>=0?value.substring(0,split):value;}
    private void line(GuiGraphics g,Component text,int xx,int yy,int max,int color){g.drawString(font,TradeTheme.fit(font,text,Math.max(5,max),false),xx,yy,color,false);}
    private void line(GuiGraphics g,String text,int xx,int yy,int max,int color){line(g,Component.literal(text),xx,yy,max,color);}
    private void wrap(GuiGraphics g,Component text,int xx,int yy,int max,int color,int limit){for(var row:font.split(text,max)){if(limit--<=0)break;g.drawString(font,row,xx,yy,color,false);yy+=12;}}
    private void panel(GuiGraphics g,int xx,int yy,int ww,int hh){g.fill(xx,yy,xx+ww,yy+hh,BLUE);TradeTheme.border(g,xx,yy,ww,hh,0xFF63726B);g.fill(xx+1,yy+1,xx+ww-1,yy+2,0xFFBCA76F);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){renderBg(g,partial,mx,my);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(0,0,width,height,0xB3081118);TradeTheme.frame(g,x,y,w,h);g.fill(x+9,y+9,x+w-9,y+h-9,0xFF142532);TradeTheme.border(g,x+10,y+10,w-20,h-20,0xFF657267);
        if(!compact())TradeTheme.crown(g,x+30,y+26,1);line(g,str(data,"name").isEmpty()?tr("title"):Component.literal(str(data,"name")),x+(compact()?16:50),y+15,w-(compact()?32:220),GOLD);
        if(!compact())line(g,tr("municipal_ledger"),x+w-200,y+17,180,MUTED);
        if(!confirmation.isEmpty()){panel(g,x+14,top,w-28,bottom-top+2);TradeTheme.crown(g,x+w/2,top+22,1);wrap(g,Component.literal(confirmation),x+28,top+44,w-56,CREAM,compact()?4:10);return;}
        if(tab.equals("account")){renderAccount(g);return;}if(tab.equals("admin")){String[] keys={"dailyCap","playerCap","buildCents","recruitCents","orders"};int cw=(w-44)/2,dy=compact()?36:52;for(int i=0;i<keys.length;i++)line(g,tr(keys[i]),x+17+(i%2)*(cw+10),top+3+(i/2)*dy,cw,MUTED);return;}
        renderRows(g,mx,my);
    }
    private void renderAccount(GuiGraphics g){
        int gap=8,cw=(w-34-gap)/2,ch=compact()?50:89;panel(g,x+17,top+2,cw,ch);panel(g,x+17+cw+gap,top+2,cw,ch);
        line(g,tr("balance"),x+25,top+11,cw-16,MUTED);line(g,money(num(data,"balance")),x+25,top+(compact()?27:37),cw-16,GOLD);
        line(g,tr("wallet"),x+25+cw+gap,top+11,cw-16,MUTED);line(g,money(num(data,"wallet")),x+25+cw+gap,top+(compact()?27:37),cw-16,CREAM);
        if(!compact()){line(g,tr("citizens",num(data,"citizens")),x+25,top+63,cw-16,MUTED);line(g,tr("owner",str(data,"owner")),x+25+cw+gap,top+63,cw-16,MUTED);}
        line(g,tr("amount"),x+17,top+(compact()?56:111),w-34,MUTED);
        if(!compact()){wrap(g,tr("account_hint"),x+19,top+194,w-38,MUTED,3);long cap=num(policy(),"dailyCap"),earned=num(data,"earned");int py=bottom-27;line(g,tr("daily_total",money(earned),money(cap)),x+19,py-14,w-38,MUTED);g.fill(x+19,py,x+w-19,py+5,0xFF0C171D);g.fill(x+19,py,x+19+(int)((w-38)*Math.min(1,cap>0?(double)earned/cap:0)),py+5,GOLD);}
    }
    private void renderRows(GuiGraphics g,int mx,int my){
        var rows=list(tab.equals("exports")?"exports":tab.equals("invoices")?"invoices":"history");if(rows.isEmpty()){wrap(g,tr(tab.equals("invoices")?"no_invoices":"empty"),x+22,top+23,w-44,MUTED,5);return;}
        for(int i=0;i<rowCount()&&page*rowCount()+i<rows.size();i++){
            var row=rows.get(page*rowCount()+i).getAsJsonObject();int yy=top+3+i*rowHeight();panel(g,x+13,yy,w-26,rowHeight()-3);int right=x+w-(compact()?80:125),max=right-x-38;
            if(tab.equals("exports")){var stack=item(row);g.renderItem(stack,x+20,yy+7);line(g,num(row,"quantity")+" × "+stack.getHoverName().getString(),x+42,yy+5,max-22,CREAM);line(g,money(num(row,"cents"))+(compact()?"":" · "+tr("available",num(row,"available")).getString()),x+42,yy+(compact()?17:24),max-22,GOLD);}
            else if(tab.equals("invoices")){line(g,str(row,"title"),x+22,yy+5,max,CREAM);line(g,tr("kind_"+str(row,"kind").toLowerCase(Locale.ROOT)).getString()+" · "+money(num(row,"cents")),x+22,yy+(compact()?17:24),max,GOLD);}
            else{line(g,str(row,"name")+" · "+tr("event_"+str(row,"kind").toLowerCase(Locale.ROOT)).getString(),x+22,yy+5,w-44,CREAM);line(g,(num(row,"cents")>0?"+":"")+money(num(row,"cents"))+" → "+money(num(row,"balance")),x+22,yy+(compact()?17:24),w-44,GOLD);}
        }
        long secs=num(data,"reset");String time=String.format(Locale.ROOT,"%02d:%02d:%02d",secs/3600,secs/60%60,secs%60);line(g,tab.equals("exports")?tr("reset",time):tr("page",page+1,Math.max(1,(rows.size()+rowCount()-1)/rowCount())),x+81,bottom-14,w-100,MUTED);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);String error=str(data,"error");line(g,waiting?tr("waiting"):error.isEmpty()?tr(flag(data,"manager")?"manager_hint":"member_hint"):Component.translatable(error),x+15,y+h-23,w-95,error.isEmpty()?MUTED:0xFFF0A39B);
        if(!confirmation.isEmpty())return;if(tab.equals("exports")||tab.equals("invoices")||tab.equals("history")){var rows=list(tab.equals("exports")?"exports":tab.equals("invoices")?"invoices":"history");for(int i=0;i<rowCount()&&page*rowCount()+i<rows.size();i++){int yy=top+3+i*rowHeight();if(mx>=x+14&&mx<x+w-(compact()?77:120)&&my>=yy&&my<yy+rowHeight()-3){var row=rows.get(page*rowCount()+i).getAsJsonObject();if(tab.equals("exports"))g.renderComponentTooltip(font,List.of(item(row).getHoverName(),tr("export_confirmation",num(row,"quantity"),item(row).getHoverName().getString(),money(num(row,"cents"))),tr("available",num(row,"available"))),mx,my);else g.renderComponentTooltip(font,List.of(Component.literal(str(row,tab.equals("invoices")?"title":"name")),Component.literal((tab.equals("invoices")?reference(row):str(row,"detail"))),Component.literal(java.time.Instant.ofEpochMilli(num(row,"at")).toString())),mx,my);}}}
    }
    @Override protected void containerTick(){super.containerTick();ticks++;if(ticks%100==0&&!waiting&&confirmation.isEmpty()&&!(getFocused() instanceof EditBox))requestSender.accept("clnRefresh",new JsonObject());if(waiting&&ticks%200==0)requestSender.accept("clnRefresh",new JsonObject());}
    @Override public boolean isPauseScreen(){return false;}
    private static final class BankButton extends Button {
        BankButton(Button.Builder b){super(b);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){g.fill(getX(),getY(),getX()+width,getY()+height,!active?0xFF243139:isHoveredOrFocused()?0xFF36535B:0xFF203C4B);TradeTheme.border(g,getX(),getY(),width,height,active?GOLD:0xFF54635F);var f=Minecraft.getInstance().font;var label=TradeTheme.fit(f,getMessage(),width-8,false);g.drawString(f,label,getX()+(width-f.width(label))/2,getY()+(height-8)/2,active?CREAM:MUTED,false);}
    }
}
