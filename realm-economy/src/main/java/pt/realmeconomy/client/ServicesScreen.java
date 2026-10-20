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
import net.minecraft.world.item.ItemStack;
import pt.realmeconomy.menu.ServicesMenu;
import pt.realmeconomy.network.TradeNetwork;

/** Original medieval service ledger. Inventory images are selections, never client-owned containers. */
public final class ServicesScreen extends AbstractContainerScreen<ServicesMenu> {
 private static JsonObject pending;
 private java.util.function.BiConsumer<String,JsonObject> requestSender=TradeNetwork::request;
 private JsonObject data=new JsonObject();private int x,y,w,h,top,bottom,left,activeSlot,selectedRule,page,ticks,inventoryPage;private String tab="service";private boolean waiting;
 private EditBox nameInput,priceInput,taxInput,minInput,maxInput,capInput,periodInput;private boolean enabled,adminOptions;
 public ServicesScreen(ServicesMenu menu,Inventory inventory,Component title){super(menu,inventory,title);if(pending!=null&&number(pending,"container")==menu.containerId){data=pending;pending=null;}if(text(data,"context").equals("DESK"))tab="policy";}
 public static void receive(String json){var value=JsonParser.parseString(json).getAsJsonObject();var mc=Minecraft.getInstance();if(mc.screen instanceof ServicesScreen screen&&number(value,"container")==screen.menu.containerId){screen.data=value;screen.waiting=false;screen.rebuild();}else pending=value;}
 private static Component tr(String key,Object...args){return Component.translatable("gui.realmeconomy."+key,args);}
 private static String text(JsonObject o,String key){return o.has(key)?o.get(key).getAsString():"";}
 private static long number(JsonObject o,String key){return o.has(key)?o.get(key).getAsLong():0;}
 private static boolean yes(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
 private static String money(long n){return n<0?"—":BigDecimal.valueOf(n,2).toPlainString()+" €";}
 private static String decimal(long n){return BigDecimal.valueOf(n,2).stripTrailingZeros().toPlainString();}
 private static Component service(String value){return Component.translatable("service.realmeconomy."+value.toLowerCase(Locale.ROOT));}
 private JsonArray array(String name){return data.has(name)?data.getAsJsonArray(name):new JsonArray();}
 private boolean compact(){return w<550||h<310;}
 private boolean desk(){return text(data,"context").equals("DESK");}
 private boolean brewing(){return text(data,"context").equals("BREWING");}
 private int inputCount(){return switch(text(data,"context")){case "ANVIL_REPAIR"->2;case "SMITHING"->3;case "BREWING"->4;default->1;};}
 private ItemStack item(JsonObject o,String key){try{return ItemStack.OPTIONAL_CODEC.parse(minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),o.get(key)).getOrThrow();}catch(RuntimeException invalid){return ItemStack.EMPTY;}}
 private ItemStack inventory(int slot){return slot>=0&&slot<array("inventory").size()?item(array("inventory").get(slot).getAsJsonObject(),"item"):ItemStack.EMPTY;}
 private void rebuild(){if(minecraft!=null)init(minecraft,width,height);}
 private Button button(Component label,int bx,int by,int bw,int bh,Runnable action){var button=addRenderableWidget(Button.builder(label,b->action.run()).bounds(bx,by,Math.max(12,bw),bh).build(LedgerButton::new));if(!label.getString().isEmpty())button.setTooltip(Tooltip.create(label));return button;}
 private EditBox field(int bx,int by,int bw,String value){var input=new EditBox(font,bx,by,Math.max(20,bw),20,Component.empty());input.setMaxLength(50);input.setValue(value);return addRenderableWidget(input);}
 private void tab(String value){tab=value;page=0;rebuild();}
 @Override protected void init(){
  w=Math.min(760,width-16);h=Math.min(424,height-16);x=(width-w)/2;y=(height-h)/2;top=y+(compact()?52:66);bottom=y+h-30;left=compact()?112:Math.max(280,(int)(w*.43));clearWidgets();
  int bx=x+12,by=y+(compact()?28:40),tabWidth=compact()?61:112;
  if(desk()){button(tr("policies"),bx,by,tabWidth,17,()->tab("policy"));if(yes(data,"administrator"))button(tr("admin"),bx+tabWidth+4,by,tabWidth,17,()->tab("admin"));if(yes(data,"manager"))button(tr("history"),bx+2*(tabWidth+4),by,tabWidth,17,()->tab("audit"));}
  else button(tr("operation"),bx,by,tabWidth,17,()->tab("service"));
  if(desk()||brewing())button(tr("batches"),x+w-(compact()?70:124),by,compact()?58:110,17,()->tab("jobs"));
  button(tr("close"),x+w-68,y+h-23,55,16,this::onClose);
  if(tab.equals("jobs")){initJobs();return;}if(tab.equals("audit")){initAudit();return;}if(desk()){initPolicy();return;}
  initService();
 }
 private void send(String operation,JsonObject body){if(waiting)return;body.addProperty("nonce",text(data,"nonce"));body.addProperty("quote",text(data,"quote"));waiting=true;requestSender.accept(operation,body);rebuild();}
 private JsonObject selection(){var body=new JsonObject();body.add("slots",data.has("slots")?data.getAsJsonArray("slots").deepCopy():new JsonArray());body.addProperty("name",nameInput!=null?nameInput.getValue():text(data,"name"));body.addProperty("rename",yes(data,"rename"));body.addProperty("choice",number(data,"choice"));body.addProperty("secondary",number(data,"secondary"));return body;}
 private void select(int slot){var body=selection();var slots=body.getAsJsonArray("slots");while(slots.size()<4)slots.add(-1);slots.set(activeSlot,new JsonPrimitive(slot));send("svcPreview",body);}
 private void initService(){
  String context=text(data,"context");int xx=x+14;
  if(context.equals("BEACON")){
   int bw=compact()?Math.max(70,(left-28)):left-28;for(int i=0;i<5;i++){final int index=i;button(Component.translatable(pt.realmeconomy.service.BeaconService.effect(i).value().getDescriptionId()),xx,top+18+i*(compact()?19:29),bw,compact()?17:23,()->{var body=selection();body.addProperty("choice",index);send("svcPreview",body);});}
   for(int i=0;i<3;i++){final int n=i;button(compact()?Component.literal(new String[]{"I","II","+R"}[i]):tr("secondary_"+i),compact()?xx+i*28:xx,compact()?top+119:top+181+i*19,compact()?25:bw,17,()->{var body=selection();body.addProperty("secondary",n);send("svcPreview",body);});}
  }else{
   int count=inputCount();int bw=(left-31)/count;
   for(int i=0;i<count;i++){final int index=i;button(Component.literal(Integer.toString(i+1)),xx+i*(bw+3),top+15,bw,compact()?21:34,()->{activeSlot=index;rebuild();});}
   int columns=compact()?6:9,cell=compact()?15:26,gridTop=top+(compact()?43:72);
   for(int slot=compact()?inventoryPage*18:0;slot<(compact()?(inventoryPage+1)*18:36);slot++){final int index=slot;int position=compact()?slot-inventoryPage*18:slot;int col=position%columns,row=position/columns;button(Component.empty(),xx+col*cell,gridTop+row*cell,cell-1,cell-1,()->select(index));}
   if(compact()){button(Component.literal("‹"),xx,top+92,38,15,()->{inventoryPage=0;rebuild();});button(Component.literal("›"),xx+43,top+92,38,15,()->{inventoryPage=1;rebuild();});}
   if(!compact())button(tr("clear_input"),xx,gridTop+4*cell+12,left-30,19,()->select(-1));
   if(context.equals("ENCHANT")){for(int i=0;i<3;i++){final int choice=i;button(compact()?Component.literal(new String[]{"I","II","III"}[i]):tr("enchant_option",i+1),compact()?xx+i*28:xx,compact()?top+112:top+217+i*20,compact()?25:left-30,18,()->{var body=selection();body.addProperty("choice",choice);send("svcPreview",body);});}}
   if(context.equals("ANVIL_REPAIR")){
    int fieldY=top+(compact()?112:218);nameInput=field(xx,fieldY,compact()?40:left-130,text(data,"name"));nameInput.setMaxLength(50);
    button(tr("rename"),compact()?xx+45:xx+left-119,fieldY,compact()?40:88,19,()->{var body=selection();body.addProperty("rename",true);send("svcPreview",body);});
   }
   if(brewing())button(tr("stored_items"),xx,top+(compact()?112:240),left-30,20,()->send("svcStorage",new JsonObject()));
  }
  button(tr(brewing()?"start_batch":"confirm_service"),x+left+10,bottom-24,w-left-24,22,()->send("svcConfirm",new JsonObject())).active=!waiting&&yes(data,"canConfirm");
 }
 private JsonObject rule(){var rules=array("rules");return rules.isEmpty()?new JsonObject():rules.get(Math.clamp(selectedRule,0,rules.size()-1)).getAsJsonObject();}
 private void initPolicy(){
  int rows=Math.min(array("rules").size(),compact()?8:8),rowH=compact()?16:31;
  for(int i=0;i<rows;i++){final int n=i;button((i==selectedRule?Component.literal("› "):Component.empty()).append(service(text(array("rules").get(i).getAsJsonObject(),"service"))),x+12,top+10+i*rowH,left-24,rowH-2,()->{selectedRule=n;rebuild();});}
  var r=rule();if(r.size()==0)return;int xx=x+left+12,ww=w-left-28,yy=top+(compact()?16:39);enabled=yes(r,"enabled");
  if(tab.equals("admin")){
   int fieldWidth=compact()?ww:Math.min(180,ww-15);
   if(adminOptions){
    addRenderableWidget(Button.builder(tr(enabled?"enabled":"disabled"),b->{enabled=!enabled;b.setMessage(tr(enabled?"enabled":"disabled"));}).bounds(xx,yy,ww,20).build(LedgerButton::new));
    periodInput=field(xx,yy+(compact()?31:43),fieldWidth,Long.toString(number(data,"beaconSeconds")));
    button(tr("save_period"),xx,yy+(compact()?55:67),ww,compact()?17:19,()->{try{var body=new JsonObject();body.addProperty("seconds",Integer.parseInt(periodInput.getValue()));send("svcPeriod",body);}catch(RuntimeException invalid){data.addProperty("error","error.realmeconomy.bounds");}});
    button(tr("save_switch"),xx,yy+(compact()?76:90),ww,compact()?17:19,()->{var body=new JsonObject();body.addProperty("service",text(r,"service"));body.addProperty("minimum",number(r,"minimum"));body.addProperty("maximum",number(r,"maximum"));body.addProperty("cap",number(r,"cap"));body.addProperty("enabled",enabled);send("svcAdmin",body);});
    button(tr("back_bounds"),xx,bottom-23,ww,21,()->{adminOptions=false;rebuild();});
   }else{
    minInput=field(xx,yy,fieldWidth,decimal(number(r,"minimum")));maxInput=field(xx,yy+(compact()?30:43),fieldWidth,decimal(number(r,"maximum")));capInput=field(xx,yy+(compact()?60:86),fieldWidth,decimal(number(r,"cap")));
    button(tr("admin_options"),xx,compact()?top+99:yy+126,ww,17,()->{adminOptions=true;rebuild();});
    button(tr("save_limits"),xx,bottom-23,ww,21,()->{try{var body=new JsonObject();body.addProperty("service",text(r,"service"));body.addProperty("minimum",cents(minInput));body.addProperty("maximum",cents(maxInput));body.addProperty("cap",cents(capInput));body.addProperty("enabled",enabled);send("svcAdmin",body);}catch(RuntimeException invalid){data.addProperty("error","error.realmeconomy.bounds");}}).active=!waiting;
   }
  }else{
   priceInput=field(xx,yy,ww,decimal(number(r,"value")));taxInput=field(xx,yy+(compact()?36:58),ww,decimal(number(r,"approvedTax")));
   button(tr("save_policy"),xx,bottom-23,ww,21,()->{try{var body=new JsonObject();body.addProperty("service",text(r,"service"));body.addProperty("value",cents(priceInput));body.addProperty("tax",cents(taxInput));send("svcPolicy",body);}catch(RuntimeException invalid){data.addProperty("error","error.realmeconomy.bounds");}}).active=!waiting&&yes(data,"manager");
  }
 }
 private static long cents(EditBox input){return new BigDecimal(input.getValue().replace(',','.')).movePointRight(2).longValueExact();}
 private int rows(){return Math.max(1,(bottom-top-26)/(compact()?28:47));}
 private void initJobs(){var jobs=array("jobs");page=Math.clamp(page,0,Math.max(0,(jobs.size()-1)/rows()));for(int i=0;i<rows()&&page*rows()+i<jobs.size();i++){var row=jobs.get(page*rows()+i).getAsJsonObject();int yy=top+8+i*(compact()?28:47);button(tr("collect"),x+w-100,yy,80,23,()->{var body=new JsonObject();body.addProperty("job",text(row,"id"));send("svcClaim",body);}).active=!waiting&&yes(row,"ready");}pager(jobs.size());}
 private void initAudit(){pager(array("audit").size());}
 private void pager(int size){page=Math.clamp(page,0,Math.max(0,(size-1)/rows()));button(Component.literal("‹"),x+15,bottom-21,32,18,()->{page--;rebuild();}).active=page>0;button(Component.literal("›"),x+w-48,bottom-21,32,18,()->{page++;rebuild();}).active=(page+1)*rows()<size;}
 private void line(GuiGraphics g,Component c,int xx,int yy,int max,int color){g.drawString(font,TradeTheme.fit(font,c,Math.max(8,max),false),xx,yy,color,false);}
 private void line(GuiGraphics g,String c,int xx,int yy,int max,int color){line(g,Component.literal(c),xx,yy,max,color);}
 private void wrap(GuiGraphics g,Component c,int xx,int yy,int max,int count){for(var row:font.split(c,max)){if(count--==0)break;g.drawString(font,row,xx,yy,TradeTheme.MUTED,false);yy+=11;}}
 @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
  g.fill(0,0,width,height,0xAA111819);TradeTheme.frame(g,x,y,w,h);if(!compact())TradeTheme.crown(g,x+28,y+22,1);
  line(g,desk()?tr("economy_desk"):service(text(data,"context").equals("ANVIL_REPAIR")?"ANVIL":text(data,"context")),x+(compact()?14:49),y+13,w-(compact()?120:280),TradeTheme.GOLD);line(g,tr("purse",money(number(data,"balance"))),x+w-(compact()?108:230),y+13,compact()?96:214,TradeTheme.LIGHT);
  if(tab.equals("jobs")){renderJobs(g);return;}if(tab.equals("audit")){renderAudit(g);return;}
  TradeTheme.plaque(g,x+10,top,left-13,bottom-top+3);TradeTheme.felt(g,x+left+5,top,w-left-15,bottom-top+3);
  if(desk()){renderPolicy(g);return;}
  String context=text(data,"context");if(!context.equals("BEACON")){
   line(g,tr("select_input",activeSlot+1),x+14,top+3,left-28,TradeTheme.GOLD);int count=inputCount(),bw=(left-31)/count;
   for(int i=0;i<count;i++){int slot=data.has("slots")?data.getAsJsonArray("slots").get(i).getAsInt():-1;var stack=inventory(slot);if(!compact()){g.renderItem(stack,x+20+i*(bw+3),top+23);g.renderItemDecorations(font,stack,x+20+i*(bw+3),top+23);}if(i==activeSlot)TradeTheme.border(g,x+14+i*(bw+3),top+15,bw,compact()?21:34,TradeTheme.GOLD);if(!compact())line(g,tr("input_"+context.toLowerCase(java.util.Locale.ROOT)+"_"+i),x+14+i*(bw+3),top+54,bw,TradeTheme.MUTED);}
   int columns=compact()?6:9,cell=compact()?15:26,gridTop=top+(compact()?43:72);
   for(int slot=compact()?inventoryPage*18:0;slot<(compact()?(inventoryPage+1)*18:36);slot++){int position=compact()?slot-inventoryPage*18:slot;int gx=x+14+(position%columns)*cell,gy=gridTop+(position/columns)*cell;g.fill(gx,gy,gx+cell-1,gy+cell-1,0xFF17252B);if(compact()){g.pose().pushPose();g.pose().translate(gx,gy,0);g.pose().scale(.82F,.82F,1);g.renderItem(inventory(slot),0,0);g.pose().popPose();}else{g.renderItem(inventory(slot),gx+4,gy+4);g.renderItemDecorations(font,inventory(slot),gx+4,gy+4);}}
  }else if(!compact())line(g,tr("beacon_pyramid",number(data,"layers")),x+15,top+3,left-30,TradeTheme.GOLD);
  renderPrice(g);
 }
 private void renderPrice(GuiGraphics g){
  int xx=x+left+17,ww=w-left-40,yy=top+12;var result=item(data,"result");if(!compact()){TradeTheme.plaque(g,xx,yy,ww,60);g.pose().pushPose();g.pose().translate(xx+10,yy+10,0);g.pose().scale(2.2F,2.2F,1);g.renderItem(result,0,0);g.pose().popPose();line(g,result.isEmpty()?tr("choose_materials"):result.getHoverName(),xx+58,yy+13,ww-65,TradeTheme.LIGHT);line(g,service(text(data,"service")),xx+58,yy+32,ww-65,TradeTheme.MUTED);yy+=76;}
  else{line(g,result.isEmpty()?tr("choose_materials"):result.getHoverName(),xx,yy,ww,TradeTheme.LIGHT);yy+=24;}
  line(g,tr("base",money(number(data,"base"))),xx,yy,ww,TradeTheme.LIGHT);yy+=compact()?14:23;line(g,tr("tax",decimal(number(data,"rate"))+"%",money(number(data,"tax"))),xx,yy,ww,TradeTheme.MUTED);yy+=compact()?17:28;line(g,tr("total",money(number(data,"total"))),xx,yy,ww,TradeTheme.GOLD);yy+=compact()?16:29;
  if(!compact()){
   if(text(data,"context").equals("BREWING")){wrap(g,tr("batch_hint",number(data,"fuel")),xx,yy,ww,3);yy+=41;}
   else if(text(data,"context").equals("BEACON")){wrap(g,tr("beacon_hint",number(data,"beaconSeconds"),number(data,"remaining")),xx,yy,ww,3);yy+=41;}
   else{wrap(g,text(data,"context").equals("SMITHING")?tr("smithing_hint"):tr("xp_replaced",number(data,"units")),xx,yy,ww,2);yy+=30;}
   if(text(data,"context").equals("ENCHANT")&&!text(data,"detail").isEmpty()){var parts=text(data,"detail").split(":");try{var registry=minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);var holder=registry.asHolderIdMap().byId(Integer.parseInt(parts[0]));if(holder!=null)line(g,net.minecraft.world.item.enchantment.Enchantment.getFullname(holder,Integer.parseInt(parts[1])),xx,yy,ww,TradeTheme.LIGHT);yy+=14;line(g,tr("lapis_needed",parts[2]),xx,yy,ww,TradeTheme.MUTED);}catch(RuntimeException ignored){}}
   String problem=text(data,"problem");if(!problem.isEmpty())wrap(g,Component.translatable(problem),xx,bottom-58,ww,2);else if(number(data,"balance")<number(data,"total"))line(g,tr("insufficient"),xx,bottom-47,ww,0xFFEDAA92);
  }
 }
 private void renderPolicy(GuiGraphics g){
  var r=rule();int xx=x+left+12,ww=w-left-28,yy=top+(compact()?16:39);if(!compact())line(g,service(text(r,"service")),xx,top+5,ww,TradeTheme.GOLD);
  if(tab.equals("admin")){if(adminOptions)line(g,tr("period_seconds"),xx,yy+(compact()?20:31),ww,TradeTheme.MUTED);else{line(g,tr("minimum"),xx,yy-12,ww,TradeTheme.MUTED);line(g,tr("maximum"),xx,yy+(compact()?18:31),ww,TradeTheme.MUTED);line(g,tr("tax_ceiling"),xx,yy+(compact()?48:74),ww,TradeTheme.MUTED);}}
  else{line(g,tr("price_euros"),xx,yy-12,ww,TradeTheme.MUTED);line(g,tr("tax_percent"),xx,yy+(compact()?24:46),ww,TradeTheme.MUTED);if(!compact()){line(g,tr("allowed_range",money(number(r,"minimum")),money(number(r,"maximum"))),xx,yy+96,ww,TradeTheme.MUTED);line(g,tr("limit",decimal(number(r,"cap"))+"%"),xx,yy+115,ww,TradeTheme.MUTED);wrap(g,tr("rule_units_"+text(r,"service").toLowerCase(Locale.ROOT)),xx,yy+142,ww,3);wrap(g,tr(yes(data,"treasury")?"policy_hint":"no_treasury"),xx,yy+188,ww,3);}}
 }
 private void renderJobs(GuiGraphics g){var jobs=array("jobs");if(jobs.isEmpty()){wrap(g,tr("no_batches"),x+20,top+25,w-40,4);return;}for(int i=0;i<rows()&&page*rows()+i<jobs.size();i++){var row=jobs.get(page*rows()+i).getAsJsonObject();int yy=top+8+i*(compact()?28:47);TradeTheme.plaque(g,x+12,yy-2,w-24,compact()?27:42);g.renderItem(item(row,"item"),x+20,yy+4);line(g,tr("batch_count",number(row,"count")),x+45,yy+3,w-155,TradeTheme.LIGHT);if(!compact())line(g,yes(row,"ready")?tr("ready"):tr("remaining",number(row,"remaining")),x+45,yy+19,w-155,TradeTheme.MUTED);}}
 private void renderAudit(GuiGraphics g){var audit=array("audit");for(int i=0;i<rows()&&page*rows()+i<audit.size();i++){var row=audit.get(page*rows()+i).getAsJsonObject();int yy=top+8+i*(compact()?28:47);TradeTheme.plaque(g,x+12,yy-2,w-24,compact()?27:42);String date=java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(number(row,"at")));line(g,date+" · "+text(row,"name")+" · "+service(text(row,"service")).getString(),x+20,yy+3,w-40,TradeTheme.LIGHT);if(!compact())line(g,auditDetails(row).getFirst(),x+20,yy+20,w-40,TradeTheme.MUTED);}}
 private List<Component> auditDetails(JsonObject row){
  var out=new ArrayList<Component>();try{if(text(row,"service").equals("BEACON_PERIOD")){out.add(tr("period_change",text(row,"old"),text(row,"next")));return out;}var old=pt.realmeconomy.domain.EconomyRule.decode(text(row,"old"));var next=pt.realmeconomy.domain.EconomyRule.decode(text(row,"next"));out.add(tr("price_change",money(old.value()),money(next.value())).copy().append(" · ").append(tr("tax_change",decimal(old.tax()),decimal(next.tax()))));out.add(tr("min_change",money(old.minimum()),money(next.minimum())));out.add(tr("max_change",money(old.maximum()),money(next.maximum())));out.add(tr("cap_change",decimal(old.taxCap()),decimal(next.taxCap())));out.add(tr("state_change",old.enabled()?tr("enabled"):tr("disabled"),next.enabled()?tr("enabled"):tr("disabled")));}catch(RuntimeException invalid){out.add(tr("history"));}return out;
 }
 @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);String error=text(data,"error");line(g,waiting?tr("waiting"):error.isEmpty()?tr("server_quote"):Component.translatable(error),x+13,y+h-18,w-90,error.isEmpty()?TradeTheme.MUTED:0xFFF1A894);
  if(tab.equals("audit")){for(int i=0;i<rows()&&page*rows()+i<array("audit").size();i++){int yy=top+8+i*(compact()?28:47);if(mx>=x+12&&mx<x+w-12&&my>=yy&&my<yy+(compact()?27:42))g.renderComponentTooltip(font,auditDetails(array("audit").get(page*rows()+i).getAsJsonObject()),mx,my);}}
  if(tab.equals("service")&&!text(data,"context").equals("BEACON")){int cols=compact()?6:9,cell=compact()?15:26,gy=top+(compact()?43:72);for(int slot=compact()?inventoryPage*18:0;slot<(compact()?(inventoryPage+1)*18:36);slot++){int position=compact()?slot-inventoryPage*18:slot;int sx=x+14+position%cols*cell,sy=gy+position/cols*cell;if(mx>=sx&&mx<sx+cell&&my>=sy&&my<sy+cell&&!inventory(slot).isEmpty())g.renderTooltip(font,inventory(slot),mx,my);}}
 }
 @Override protected void containerTick(){ticks++;if(!waiting&&ticks%40==0&&(getFocused()==null||!(getFocused() instanceof EditBox)))requestSender.accept("svcRefresh",new JsonObject());if(waiting&&ticks%200==0){waiting=false;requestSender.accept("svcRefresh",new JsonObject());}super.containerTick();}
 @Override public boolean isPauseScreen(){return false;}
 private static final class LedgerButton extends Button {
  LedgerButton(Button.Builder b){super(b);}
  @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){if(getMessage().getString().isEmpty()){if(isHoveredOrFocused())TradeTheme.border(g,getX(),getY(),getWidth(),getHeight(),TradeTheme.GOLD);return;}g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),!active?0xFF273136:isHoveredOrFocused()?0xFF28576A:0xFF203A49);TradeTheme.border(g,getX(),getY(),getWidth(),getHeight(),active?TradeTheme.GOLD:0xFF645D4C);var font=Minecraft.getInstance().font;var label=TradeTheme.fit(font,getMessage(),getWidth()-8,true);g.drawString(font,label,getX()+(getWidth()-font.width(label))/2,getY()+(getHeight()-8)/2,active?TradeTheme.LIGHT:TradeTheme.MUTED,false);}
 }
}
