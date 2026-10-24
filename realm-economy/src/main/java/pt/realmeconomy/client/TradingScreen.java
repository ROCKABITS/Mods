package pt.realmeconomy.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import pt.realmeconomy.menu.EconomyMenu;
import pt.realmeconomy.network.TradeNetwork;

/** An illuminated merchant ledger: quotes stay visible until a single explicit confirmation. */
public final class TradingScreen extends AbstractContainerScreen<EconomyMenu> {
    private static JsonObject pending;
    private java.util.function.BiConsumer<String,JsonObject> requestSender=TradeNetwork::request;
    private JsonObject data=new JsonObject();private int x,y,w,h,top,bottom,left,selected,page,ticks;private boolean fiscal,waiting;private EditBox rateInput;
    public TradingScreen(EconomyMenu menu,Inventory inventory,Component title){super(menu,inventory,title);if(pending!=null&&num(pending,"container")==menu.containerId){data=pending;pending=null;}}
    public static void receive(String json){var snapshot=JsonParser.parseString(json).getAsJsonObject();var mc=Minecraft.getInstance();if(mc.screen instanceof TradingScreen screen&&num(snapshot,"container")==screen.menu.containerId){screen.data=snapshot;screen.waiting=false;screen.rebuild();}else pending=snapshot;}
    private static Component tr(String key,Object...args){return Component.translatable("gui.realmeconomy."+key,args);}
    private static String str(JsonObject obj,String key){return obj.has(key)?obj.get(key).getAsString():"";}
    private static long num(JsonObject obj,String key){return obj.has(key)?obj.get(key).getAsLong():0;}
    private static boolean flag(JsonObject obj,String key){return obj.has(key)&&obj.get(key).getAsBoolean();}
    private static String money(long cents){return cents<0?"—":BigDecimal.valueOf(cents,2).toPlainString()+" €";}
    private static String percent(long rate){return BigDecimal.valueOf(rate,2).stripTrailingZeros().toPlainString()+"%";}
    private JsonArray offers(){return data.has("offers")?data.getAsJsonArray("offers"):new JsonArray();}
    private JsonObject offer(){return selected>=0&&selected<offers().size()?offers().get(selected).getAsJsonObject():new JsonObject();}
    private boolean compact(){return w<550||h<310;}
    private int visibleRows(){return Math.max(1,(bottom-top-30)/42);}
    private void rebuild(){if(minecraft!=null)init(minecraft,width,height);}
    private ItemStack item(JsonObject row,String field){try{return ItemStack.OPTIONAL_CODEC.parse(minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),row.get(field)).getOrThrow();}catch(RuntimeException missing){return ItemStack.EMPTY;}}
    /** Registry-backed names retain translated enchantments, levels and curse formatting. */
    private Component enchantmentSummary(ItemStack stack){
        var stored=stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY);
        var text=Component.empty();
        for(var entry:stored.entrySet()){
            if(!text.getSiblings().isEmpty())text.append(Component.literal(", "));
            text.append(Enchantment.getFullname(entry.getKey(),entry.getIntValue()));
        }
        return text;
    }
    private ItemStack hoveredItem(double mx,double my){
        if(fiscal)return ItemStack.EMPTY;
        if(mx>=x+14&&mx<x+left-4){
            for(int i=0;i<visibleRows()&&page*visibleRows()+i<offers().size();i++){
                int yy=top+i*42;
                if(my>=yy&&my<yy+38)return item(offers().get(page*visibleRows()+i).getAsJsonObject(),"result");
            }
        }
        int xx=x+left+20,yy=top+12,ww=w-left-46;
        if(mx>=xx&&mx<xx+ww&&my>=yy&&my<yy+(compact()?22:76))return item(offer(),"result");
        return ItemStack.EMPTY;
    }
    @Override protected void init(){
        w=Math.min(760,width-16);h=Math.min(424,height-16);x=(width-w)/2;y=(height-h)/2;top=y+(compact()?55:72);bottom=y+h-28;left=compact()?Math.max(100,w/3):Math.max(220,(int)(w*.38));
        clearWidgets();int tabW=Math.min(140,(w-40)/3);addRenderableWidget(Button.builder(tr("offers"),b->{fiscal=false;rebuild();}).bounds(x+14,y+(compact()?29:43),tabW,18).build(LedgerButton::new));
        if(flag(data,"manager"))addRenderableWidget(Button.builder(tr("fiscal"),b->{fiscal=true;rebuild();}).bounds(x+20+tabW,y+(compact()?29:43),tabW,18).build(LedgerButton::new));
        addRenderableWidget(Button.builder(tr("close"),b->onClose()).bounds(x+w-70,y+h-23,56,16).build(LedgerButton::new));
        if(fiscal){initFiscal();return;}
        page=Math.clamp(page,0,Math.max(0,(offers().size()-1)/visibleRows()));selected=Math.clamp(selected,0,Math.max(0,offers().size()-1));
        for(int i=0;i<visibleRows()&&page*visibleRows()+i<offers().size();i++){
            int index=page*visibleRows()+i;var row=offers().get(index).getAsJsonObject();int yy=top+i*42;
            var b=Button.builder(Component.empty(),button->{selected=index;rebuild();}).bounds(x+14,yy,left-18,38).build(LedgerButton::new);addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.literal("‹"),b->{page--;rebuild();}).bounds(x+14,bottom-22,28,18).build(LedgerButton::new)).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("›"),b->{page++;rebuild();}).bounds(x+left-32,bottom-22,28,18).build(LedgerButton::new)).active=(page+1)*visibleRows()<offers().size();
        JsonObject selectedOffer=offer();String action=flag(selectedOffer,"purchase")?"confirm_buy":flag(selectedOffer,"sale")?"confirm_sell":"confirm_barter";
        addRenderableWidget(Button.builder(tr(action),b->{var payload=new JsonObject();payload.addProperty("offer",selected);send("trade",payload);}).bounds(x+left+15,bottom-25,w-left-29,23).build(LedgerButton::new)).active=!waiting&&flag(selectedOffer,"canTrade");
    }
    private void initFiscal(){
        int fieldW=Math.min(180,(w-50)/2);rateInput=new EditBox(font,x+20,top+18,fieldW,22,tr("tax_percent"));rateInput.setMaxLength(8);rateInput.setValue(BigDecimal.valueOf(num(data,"approvedRate"),2).stripTrailingZeros().toPlainString());addRenderableWidget(rateInput);
        addRenderableWidget(Button.builder(tr("save_tax"),b->{try{int rate=new BigDecimal(rateInput.getValue().replace(',','.')).movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).intValueExact();if(rate<0||rate>num(data,"maxRate"))throw new IllegalArgumentException();var payload=new JsonObject();payload.addProperty("rate",rate);send("policy",payload);}catch(RuntimeException bad){data.addProperty("error","error.realmeconomy.tax_limit");}}).bounds(x+30+fieldW,top+18,Math.min(180,w-fieldW-50),22).build(LedgerButton::new)).active=!waiting;
    }
    private void send(String operation,JsonObject payload){if(waiting)return;payload.add("nonce",data.get("nonce"));payload.add("quote",data.get("quote"));waiting=true;requestSender.accept(operation,payload);rebuild();}
    private void line(GuiGraphics g,Component text,int xx,int yy,int max,int color){g.drawString(font,TradeTheme.fit(font,text,Math.max(10,max),false),xx,yy,color,false);}
    private void line(GuiGraphics g,String text,int xx,int yy,int max,int color){line(g,Component.literal(text),xx,yy,max,color);}
    private int paragraph(GuiGraphics g,Component text,int xx,int yy,int max,int color,int lines){for(var row:font.split(text,max)){if(lines--<=0)break;g.drawString(font,row,xx,yy,color,false);yy+=11;}return yy;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(0,0,width,height,0x88151510);TradeTheme.frame(g,x,y,w,h);
        if(!compact()){TradeTheme.crown(g,x+30,y+24,1);line(g,tr("title"),x+49,y+14,w-270,TradeTheme.GOLD);line(g,str(data,"name")+" · "+Component.translatable(str(data,"profession").equals("wandering_trader")?"entity.minecraft.wandering_trader":"entity.minecraft.villager."+str(data,"profession")).getString()+" · "+tr("level",num(data,"level")).getString(),x+49,y+28,w-270,TradeTheme.MUTED);line(g,tr("purse",money(num(data,"balance"))),x+w-230,y+18,215,TradeTheme.LIGHT);}
        else {line(g,tr("title"),x+15,y+14,w-125,TradeTheme.GOLD);line(g,money(num(data,"balance")),x+w-105,y+14,90,TradeTheme.LIGHT);}
        if(fiscal){renderFiscal(g);return;}
        TradeTheme.plaque(g,x+11,top-3,left-12,bottom-top+5);TradeTheme.felt(g,x+left+5,top-3,w-left-15,bottom-top+5);
        for(int i=0;i<visibleRows()&&page*visibleRows()+i<offers().size();i++){
            int index=page*visibleRows()+i,yy=top+i*42;var row=offers().get(index).getAsJsonObject();var result=item(row,"result");
            if(index==selected)TradeTheme.border(g,x+13,yy-1,left-16,40,TradeTheme.GOLD);
            g.renderItem(result,x+20,yy+8);g.renderItemDecorations(font,result,x+20,yy+8);
            line(g,result.getHoverName(),x+42,yy+7,left-48,TradeTheme.LIGHT);
            Component enchantments=enchantmentSummary(result);boolean enchanted=!enchantments.getString().isEmpty();
            if(enchanted)line(g,enchantments,x+42,yy+17,left-48,TradeTheme.GOLD);
            line(g,flag(row,"purchase")?money(num(row,"total")):tr(flag(row,"sale")?"emerald_sale":"barter").getString(),x+42,yy+(enchanted?28:21),left-48,num(row,"stock")==0?0xFFC17E72:TradeTheme.MUTED);
        }
        line(g,tr("offer_count",Math.min(offers().size(),selected+1),offers().size()),x+48,bottom-17,left-90,TradeTheme.MUTED);
        renderQuote(g);
    }
    private void renderQuote(GuiGraphics g){
        JsonObject row=offer();if(row.size()==0){line(g,tr("loading"),x+left+20,top+15,w-left-40,TradeTheme.LIGHT);return;}
        int xx=x+left+20,ww=w-left-46,yy=top+12;ItemStack result=item(row,"result");Component enchantments=enchantmentSummary(result);boolean enchanted=!enchantments.getString().isEmpty();
        if(!compact()){
            TradeTheme.plaque(g,xx,yy,ww,76);g.pose().pushPose();g.pose().translate(xx+12,yy+13,0);g.pose().scale(2.4F,2.4F,1);g.renderItem(result,0,0);g.pose().popPose();line(g,result.getHoverName(),xx+66,yy+13,ww-76,TradeTheme.LIGHT);
            if(enchanted)line(g,enchantments,xx+66,yy+28,ww-76,TradeTheme.GOLD);
            line(g,tr("quantity",result.getCount()),xx+66,yy+(enchanted?44:31),ww-76,TradeTheme.MUTED);line(g,tr("stock",num(row,"stock")),xx+66,yy+(enchanted?59:48),ww-76,TradeTheme.MUTED);yy+=89;
        }else{g.renderItem(result,xx,yy);line(g,enchanted?enchantments:result.getHoverName(),xx+23,yy+3,ww-23,TradeTheme.LIGHT);yy+=26;}
        if(flag(row,"purchase")){
            line(g,tr("base",money(num(row,"base"))),xx,yy,ww,TradeTheme.LIGHT);yy+=compact()?13:22;
            line(g,tr("tax",percent(num(data,"rate")),money(num(row,"tax"))),xx,yy,ww,TradeTheme.MUTED);yy+=compact()?16:28;
            line(g,tr("total",money(num(row,"total"))),xx,yy,ww,TradeTheme.GOLD);yy+=compact()?14:28;
            if(compact()){var material=!item(row,"costA").is(net.minecraft.world.item.Items.EMERALD)?item(row,"costA"):item(row,"costB");if(!material.isEmpty()&&!material.is(net.minecraft.world.item.Items.EMERALD))line(g,tr("material",material.getCount(),material.getHoverName()),xx,yy,ww,TradeTheme.MUTED);}
            if(!compact()){var a=item(row,"costA");var b=item(row,"costB");if(!a.is(net.minecraft.world.item.Items.EMERALD)&&!a.isEmpty()){line(g,tr("material",a.getCount(),a.getHoverName()),xx,yy,ww,TradeTheme.MUTED);yy+=14;}if(!b.is(net.minecraft.world.item.Items.EMERALD)&&!b.isEmpty()){line(g,tr("material",b.getCount(),b.getHoverName()),xx,yy,ww,TradeTheme.MUTED);yy+=14;}paragraph(g,tr("conversion",money(num(data,"conversion"))),xx,yy+3,ww,TradeTheme.MUTED,2);}
        }else{
            var a=item(row,"costA");var b=item(row,"costB");line(g,tr("material",a.getCount(),a.getHoverName()),xx,yy,ww,TradeTheme.LIGHT);yy+=16;
            if(!b.isEmpty()){line(g,tr("material",b.getCount(),b.getHoverName()),xx,yy,ww,TradeTheme.LIGHT);yy+=16;}
            paragraph(g,tr("untaxed_sale"),xx,yy+3,ww,TradeTheme.GOLD,compact()?2:3);
        }
        if(!compact()&&!flag(row,"canTrade"))line(g,tr(num(row,"stock")==0?"sold_out":flag(row,"purchase")&&num(data,"balance")<num(row,"total")?"insufficient":"requirements"),xx,bottom-46,ww,0xFFEDAA92);
    }
    private void renderFiscal(GuiGraphics g){
        line(g,tr("tax_percent"),x+20,top+3,w-40,TradeTheme.GOLD);int yy=top+53;
        line(g,tr("limit",percent(num(data,"maxRate"))),x+20,yy,w-40,TradeTheme.LIGHT);yy+=15;
        line(g,tr("effective_rate",percent(num(data,"rate"))),x+20,yy,w-40,TradeTheme.MUTED);yy+=16;
        yy=paragraph(g,tr(flag(data,"treasury")?"policy_hint":"no_treasury"),x+20,yy,w-40,TradeTheme.MUTED,compact()?2:4)+14;
        if(!compact()&&data.has("history")){line(g,tr("history"),x+20,yy,w-40,TradeTheme.GOLD);yy+=18;for(var element:data.getAsJsonArray("history")){var e=element.getAsJsonObject();if(yy>bottom-14)break;String date=java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(num(e,"at")));line(g,date+" · "+str(e,"name")+" · "+percent(num(e,"rate")),x+20,yy,w-40,TradeTheme.LIGHT);yy+=17;}}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);String error=str(data,"error");Component status=waiting?tr("waiting"):error.isEmpty()?tr("server_quote"):Component.translatable(error);line(g,status,x+15,y+h-19,w-100,error.isEmpty()?TradeTheme.MUTED:0xFFF3AD96);var hovered=hoveredItem(mx,my);if(!hovered.isEmpty())g.renderTooltip(font,hovered,mx,my);}
    @Override protected void containerTick(){if(++ticks%120==0&&!waiting&&(rateInput==null||!rateInput.isFocused()))requestSender.accept("refresh",new JsonObject());if(waiting&&ticks%200==0){waiting=false;requestSender.accept("refresh",new JsonObject());}super.containerTick();}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(!fiscal&&mx<x+left){page=Math.max(0,page+(vertical<0?1:-1));rebuild();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public boolean isPauseScreen(){return false;}
    private static final class LedgerButton extends Button {
        LedgerButton(Button.Builder builder){super(builder);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            if(getMessage().getString().isEmpty()){if(isHoveredOrFocused())TradeTheme.border(g,getX(),getY(),getWidth(),getHeight(),TradeTheme.GOLD);return;}
            int color=!active?0xFF30272A:isHoveredOrFocused()?TradeTheme.GREEN_LIGHT:TradeTheme.VELVET;
            g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),color);TradeTheme.border(g,getX(),getY(),getWidth(),getHeight(),active?TradeTheme.GOLD:0xFF6C5740);
            var font=Minecraft.getInstance().font;var text=TradeTheme.fit(font,getMessage(),getWidth()-10,true);g.drawString(font,text,getX()+(getWidth()-font.width(text))/2,getY()+(getHeight()-8)/2,active?TradeTheme.LIGHT:TradeTheme.MUTED,false);
        }
    }
}
