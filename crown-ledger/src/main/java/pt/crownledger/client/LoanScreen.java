package pt.crownledger.client;

import com.google.gson.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import pt.crownledger.network.LoanPackets;

/** A physical royal credit counter: readable terms, explicit signatures and a scrolling repayment schedule. */
public final class LoanScreen extends Screen {
    private static final ResourceLocation PAPER=ResourceLocation.fromNamespaceAndPath("kingdomtreasury","textures/gui/loan_parchment.png"),SEAL=ResourceLocation.fromNamespaceAndPath("kingdomtreasury","textures/gui/loan_seal.png");
    private static final int GOLD=0xFFD8B26C,CREAM=0xFFF1E1BC,MUTED=0xFFBBA98B,INK=0xFF483A2B,GREEN=0xFF234F43;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private JsonObject data;private String selected="",draftKey="",status="";private final Map<String,String> drafts=new HashMap<>();
    private int x,y,w,h,top,bottom,left,offset,docX,docW,docH;private boolean creating,waiting;private long stampAt;private int ticks;
    private EditBox amountInput,purposeInput,interestInput,countInput,daysInput;
    public LoanScreen(JsonObject data){super(tr("title"));this.data=data;creating=rows().isEmpty()&&flag(data,"canApply");}
    public static void receive(String payload){
        var incoming=JsonParser.parseString(payload).getAsJsonObject();var mc=Minecraft.getInstance();
        if(flag(incoming,"close")){if(mc.screen instanceof LoanScreen)mc.setScreen(null);return;}
        if(mc.screen instanceof LoanScreen s){if(!str(s.data,"view").equals(str(incoming,"view"))){s.creating=false;s.selected="";s.offset=0;}s.data=incoming;s.waiting=false;String message=str(incoming,"message");if(!message.isEmpty()){s.status=message;if(message.equals("realm.treasury.loan.message.requested"))s.creating=false;if(message.equals("realm.treasury.loan.message.accepted")){s.stampAt=System.currentTimeMillis();mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,1F));}}s.rebuild();}
        else if(flag(incoming,"open"))mc.setScreen(new LoanScreen(incoming));
    }
    private static MutableComponent tr(String key,Object...args){return Component.translatable("realm.treasury.loan."+key,args);}
    private static String str(JsonObject j,String k){return j.has(k)&&!j.get(k).isJsonNull()?j.get(k).getAsString():"";}
    private static long num(JsonObject j,String k){return j.has(k)&&!j.get(k).isJsonNull()?j.get(k).getAsLong():0;}
    private static boolean flag(JsonObject j,String k){return j.has(k)&&!j.get(k).isJsonNull()&&j.get(k).getAsBoolean();}
    private JsonArray rows(){return data.has("rows")?data.getAsJsonArray("rows"):new JsonArray();}
    private JsonObject chosen(){for(var r:rows()){var row=r.getAsJsonObject();if(str(row,"id").equals(selected))return row;}return new JsonObject();}
    private boolean compact(){return w<640||h<360;}
    private boolean tight(){return docH<160;}
    private int visible(){return Math.max(1,(bottom-top-49)/45);}
    private String currency(JsonObject row){return str(row,"currency").isEmpty()?str(data,"currency"):str(row,"currency");}
    private String money(long amount,String currency){return currency.equals("EMERALD")?tr("emeralds",amount).getString():BigDecimal.valueOf(amount,2).toPlainString()+" €";}
    private String money(long amount){return money(amount,str(data,"currency"));}
    private String date(long value){return value<=0?"—":DATE.format(Instant.ofEpochMilli(value));}
    private void rebuild(){if(minecraft!=null)init(minecraft,width,height);}
    private void resetDrafts(String key){if(!draftKey.equals(key)){drafts.clear();draftKey=key;}}
    private EditBox input(String key,int xx,int yy,int width,String value,int max){var box=new EditBox(font,xx,yy,width,20,tr(key));box.setMaxLength(max);box.setValue(drafts.getOrDefault(key,value));box.setResponder(s->drafts.put(key,s));box.setTextColor(0xFFEEDDB9);box.setBordered(true);addRenderableWidget(box);return box;}
    private Button button(String key,int xx,int yy,int width,Runnable action,boolean enabled,boolean primary){var b=Button.builder(tr(key),ignored->action.run()).bounds(xx,yy,width,22).build(builder->new CreditButton(builder,primary));b.active=enabled;return addRenderableWidget(b);}
    @Override protected void init(){
        String focus=amountInput!=null&&amountInput.isFocused()?"amount":purposeInput!=null&&purposeInput.isFocused()?"purpose":interestInput!=null&&interestInput.isFocused()?"interest":countInput!=null&&countInput.isFocused()?"installments":daysInput!=null&&daysInput.isFocused()?"interval_days":"";
        clearWidgets();amountInput=purposeInput=interestInput=countInput=daysInput=null;
        w=Math.min(820,width-16);h=Math.min(466,height-16);x=(width-w)/2;y=(height-h)/2;left=compact()?Math.max(112,w/3):Math.max(218,w/3);top=y+(compact()?62:82);bottom=y+h-34;docX=x+left+14;docW=w-left-28;docH=bottom-top;
        int tabsY=y+(compact()?34:51),tabWidth=Math.min(139,(w-38)/4);
        button("tab_mine",x+12,tabsY,tabWidth,()->view("mine",0),!waiting,false);
        int tabX=x+18+tabWidth;if(flag(data,"manager")){button("tab_review",tabX,tabsY,tabWidth,()->view("review",0),!waiting,false);tabX+=tabWidth+6;}
        button("tab_archive",tabX,tabsY,tabWidth,()->view("archive",0),!waiting,false);
        button("close",x+w-80,y+h-27,66,this::onClose,true,false);
        offset=Math.clamp(offset,0,Math.max(0,rows().size()-visible()));
        if(!creating&&(chosen().size()==0)&&!rows().isEmpty())selected=str(rows().get(0).getAsJsonObject(),"id");
        if(!waiting&&rows().isEmpty()&&str(data,"view").equals("mine")&&flag(data,"canApply"))creating=true;
        for(int i=0;i<visible()&&offset+i<rows().size();i++){
            var row=rows().get(offset+i).getAsJsonObject();int yy=top+3+i*45;
            addRenderableWidget(new OfferButton(x+13,yy,left-14,row,()->{selected=str(row,"id");creating=false;rebuild();}));
        }
        int navY=bottom-42;
        button("previous",x+13,navY,27,()->view(str(data,"view"),(int)num(data,"page")-1),!waiting&&num(data,"page")>0,false);
        button("next",x+left-29,navY,27,()->view(str(data,"view"),(int)num(data,"page")+1),!waiting&&num(data,"page")+1<num(data,"pages"),false);
        button("new_request",x+13,bottom-16,left-15,()->{creating=true;selected="";resetDrafts("request");rebuild();},!waiting&&flag(data,"canApply"),true);
        JsonObject loan=chosen();if(creating){resetDrafts("request");initRequest();}
        else if(!loan.entrySet().isEmpty()){
            if(str(loan,"state").equals("REQUESTED")&&flag(data,"manager")){
                resetDrafts(selected);initOffer(loan);
            }else initContract(loan);
        }
        EditBox focusBox=switch(focus){case "amount"->amountInput;case "purpose"->purposeInput;case "interest"->interestInput;case "installments"->countInput;case "interval_days"->daysInput;default->null;};if(focusBox!=null)setFocused(focusBox);
    }
    private void initRequest(){
        int xx=docX+17,ww=docW-34;
        amountInput=input("amount",xx,top+(tight()?52:compact()?56:78),tight()?(ww-6)/2:ww,str(data,"currency").equals("EMERALD")?"20":"100.00",18);
        purposeInput=input("purpose",tight()?xx+(ww+6)/2:xx,top+(tight()?52:compact()?100:130),tight()?(ww-6)/2:ww,"",160);purposeInput.setHint(tr("purpose_hint"));
        button("submit",xx,bottom-29,ww,()->{try{var p=new JsonObject();BigDecimal amount=new BigDecimal(amountInput.getValue().trim().replace(',','.'));p.addProperty("amount",str(data,"currency").equals("EMERALD")?amount.longValueExact():amount.movePointRight(2).longValueExact());p.addProperty("purpose",purposeInput.getValue());send("apply",p);}catch(RuntimeException error){status="realm.treasury.loan.error.amount";}},!waiting&&flag(data,"canApply"),true);
    }
    private void initOffer(JsonObject loan){
        int xx=docX+17,ww=docW-34,fieldW=(ww-12)/3,fieldY=top+(tight()?75:compact()?82:104);var limits=data.getAsJsonObject("limits");
        interestInput=input("interest",xx,fieldY,fieldW,BigDecimal.valueOf(Math.min(1000,num(limits,"maximumInterest")),2).stripTrailingZeros().toPlainString(),6);
        countInput=input("installments",xx+fieldW+6,fieldY,fieldW,Long.toString(Math.min(5,Math.min(num(limits,"maximumInstallments"),num(loan,"principal")))),2);
        daysInput=input("interval_days",xx+2*(fieldW+6),fieldY,fieldW,"1",3);
        int bodyY=fieldY+29,bodyH=bottom-38-bodyY;if(bodyH>22)addRenderableWidget(new PaperText(xx,bodyY,ww,bodyH,Component.literal(str(loan,"purpose")).append("\n\n").append(tr("approval_hint")).append("\n").append(tr("limits_hint",BigDecimal.valueOf(num(limits,"maximumInterest"),2).stripTrailingZeros().toPlainString(),num(limits,"maximumInstallments"),num(limits,"maximumIntervalDays")))));
        boolean self=str(loan,"borrower").equals(str(data,"viewer"));
        button("approve",xx,bottom-29,(ww-6)/2,()->{try{var p=id(loan);p.addProperty("interest",new BigDecimal(interestInput.getValue().replace(',','.')).movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).intValueExact());p.addProperty("installments",Integer.parseInt(countInput.getValue()));p.addProperty("days",Integer.parseInt(daysInput.getValue()));send("offer",p);}catch(RuntimeException bad){status="realm.treasury.loan.error.limits";}},!waiting&&(!self||flag(data,"admin")),true);
        button("decline",xx+(ww+6)/2,bottom-29,(ww-6)/2,()->send("decline",id(loan)),!waiting,false);
    }
    private void initContract(JsonObject loan){
        int xx=docX+17,ww=docW-34;String state=str(loan,"state");boolean own=flag(loan,"own");
        int textTop=top+(compact()?45:108);addRenderableWidget(new PaperText(xx,textTop,ww,Math.max(30,bottom-38-textTop),contract(loan)));
        if(state.equals("OFFERED")&&own){button("sign",xx,bottom-29,(ww-6)/2,()->send("accept",id(loan)),!waiting,true);button("decline_offer",xx+(ww+6)/2,bottom-29,(ww-6)/2,()->send("cancel",id(loan)),!waiting,false);}
        else if(state.equals("ACTIVE")&&own){button("pay",xx,bottom-29,(ww-6)/2,()->send("pay",id(loan)),!waiting,true);button("settle",xx+(ww+6)/2,bottom-29,(ww-6)/2,()->send("settle",id(loan)),!waiting,false);}
        else if(state.equals("REQUESTED")&&own)button("cancel",xx,bottom-29,ww,()->send("cancel",id(loan)),!waiting,false);
        else if(state.equals("OFFERED")&&flag(data,"manager"))button("withdraw_offer",xx,bottom-29,ww,()->send("decline",id(loan)),!waiting,false);
    }
    private Component contract(JsonObject loan){
        String c=currency(loan),state=str(loan,"state");var t=Component.empty();
        t.append(tr("borrower",str(loan,"name"))).append("\n");t.append(tr("principal_value",money(num(loan,"principal"),c))).append("\n");
        if(!state.equals("REQUESTED")){
            t.append(tr("fixed_interest",BigDecimal.valueOf(num(loan,"interest"),2).stripTrailingZeros().toPlainString())).append("\n");
            t.append(tr("total_value",money(num(loan,"total"),c)).withStyle(s->s.withBold(true))).append("\n");
            if(num(loan,"installments")>0)t.append(tr("schedule_terms",num(loan,"installments"),num(loan,"intervalDays"))).append("\n");
            t.append(tr("approved_by",str(loan,"approverName"))).append("\n");
            if(state.equals("OFFERED"))t.append(tr("offer_until",date(num(loan,"expiresAt")))).append("\n");
        }
        if(state.equals("ACTIVE")||state.equals("SETTLED")){
            t.append("\n").append(tr("paid_value",money(num(loan,"paid"),c))).append("\n").append(tr("remaining_value",money(num(loan,"remaining"),c))).append("\n");
            if(state.equals("ACTIVE"))t.append(tr("next_payment",money(num(loan,"nextPayment"),c),date(num(loan,"nextDueAt")))).append("\n");
            if(flag(loan,"overdue"))t.append(tr("overdue_hint").withStyle(s->s.withColor(0x9C343B))).append("\n");
            t.append("\n").append(tr("schedule_title").withStyle(s->s.withBold(true))).append("\n");
            int n=(int)num(loan,"installments");long total=num(loan,"total");for(int i=1;i<=n;i++){long amount=total/n+(i<=total%n?1:0),due=num(loan,"signedAt")+(long)i*num(loan,"intervalDays")*86_400_000L;t.append(tr("schedule_row",i,money(amount,c),date(due),tr(i<=num(loan,"completed")?"paid":"pending").getString())).append("\n");}
        }
        if(!str(loan,"purpose").isBlank())t.append("\n").append(tr("purpose")).append(": ").append(str(loan,"purpose")).append("\n");
        t.append("\n").append(tr(state.equals("OFFERED")?"signature_terms":"fixed_terms"));
        if(data.has("events")){t.append("\n\n").append(tr("audit_title").withStyle(s->s.withBold(true)));for(var raw:data.getAsJsonArray("events")){var e=raw.getAsJsonObject();if(str(e,"loan").equals(str(loan,"id")))t.append("\n").append(Component.literal(date(num(e,"at"))+" · ")).append(tr("action_"+str(e,"action"))).append(" · ").append(str(e,"name"));}}
        return t;
    }
    private JsonObject id(JsonObject row){var p=new JsonObject();p.addProperty("id",str(row,"id"));return p;}
    private void view(String view,int page){creating=false;selected="";offset=0;var p=new JsonObject();p.addProperty("view",view);p.addProperty("page",page);send("view",p);}
    private void send(String operation,JsonObject payload){if(waiting)return;payload.addProperty("nonce",str(data,"nonce"));waiting=true;PacketDistributor.sendToServer(new LoanPackets.Request(operation,payload.toString()));rebuild();}
    @Override public void onClose(){try{var p=new JsonObject();p.addProperty("nonce",str(data,"nonce"));PacketDistributor.sendToServer(new LoanPackets.Request("leave",p.toString()));}catch(RuntimeException ignored){}super.onClose();}
    @Override public void tick(){if(++ticks%100==0&&!waiting){var p=new JsonObject();p.addProperty("nonce",str(data,"nonce"));PacketDistributor.sendToServer(new LoanPackets.Request("refresh",p.toString()));}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(mx<docX&&mx>=x&&my>=top&&my<=bottom){offset=Math.clamp(offset+(vertical<0?1:-1),0,Math.max(0,rows().size()-visible()));rebuild();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    private static Component fit(net.minecraft.client.gui.Font font,Component value,int max){if(font.width(value)<=max)return value;return Component.literal(font.plainSubstrByWidth(value.getString(),Math.max(1,max-font.width("…")))+"…");}
    private void text(GuiGraphics g,Component value,int xx,int yy,int max,int color){g.drawString(font,fit(font,value,Math.max(8,max)),xx,yy,color,false);}
    private void para(GuiGraphics g,Component value,int xx,int yy,int width,int color,int limit){for(var line:font.split(value,Math.max(8,width))){if(limit--<=0)break;g.drawString(font,line,xx,yy,color,false);yy+=12;}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height,0xD0101718,0xE01F1719);TreasuryArt.frame(g,x+3,y+5,w,h,0x88000000,0x66000000);TreasuryArt.frame(g,x,y,w,h,0xFF261D1C,GOLD);TreasuryArt.frame(g,x+3,y+3,w-6,h-6,0xFF33282A,0xFF80623C);
        g.fill(x+7,y+7,x+w-7,top-8,0xFF263A34);g.fill(x+9,top-8,x+w-9,top-7,GOLD);
        g.blit(SEAL,x+13,y+9,28,28,0,0,64,64,64,64);text(g,tr("title"),x+49,y+13,w/2-50,CREAM);
        if(!compact())text(g,tr("subtitle"),x+49,y+29,w/2-50,MUTED);
        String shownCurrency=!creating&&!chosen().entrySet().isEmpty()?currency(chosen()):str(data,"currency");long balance=data.has("wallets")?num(data.getAsJsonObject("wallets"),shownCurrency):-1;text(g,tr("wallet",balance<0?"—":money(balance,shownCurrency)),x+w/2+20,y+13,w/2-36,CREAM);
        if(!compact()&&data.has("balances"))text(g,tr("public_balance",money(num(data.getAsJsonObject("balances"),shownCurrency),shownCurrency)),x+w/2+20,y+29,w/2-36,GOLD);
        TreasuryArt.frame(g,x+10,top-2,left+1,bottom-top+30,0xFF1E2926,0xFF876B42);
        g.blit(PAPER,docX,top,docW,docH,0,0,256,256,256,256);
        if(rows().isEmpty())para(g,tr("empty_"+str(data,"view")),x+22,top+22,left-30,MUTED,5);
        text(g,tr("page",num(data,"page")+1,num(data,"pages")),x+48,bottom-35,left-86,MUTED);
        JsonObject loan=chosen();
        if(creating){
            text(g,tr("request_title"),docX+17,top+15,docW-34,INK);
            text(g,tr("amount"),docX+17,top+(tight()?38:compact()?42:62),tight()?(docW-40)/2:docW-34,INK);text(g,tr("purpose"),tight()?docX+17+(docW-28)/2:docX+17,top+(tight()?38:compact()?86:114),tight()?(docW-40)/2:docW-34,INK);
            if(data.has("limits"))para(g,tr("request_hint",money(num(data.getAsJsonObject("limits"),"maximum"))),docX+17,top+(tight()?79:compact()?128:166),docW-34,0xFF786548,tight()?1:compact()?2:5);
            if(!compact())seal(g,docX+docW-47,bottom-84,44,0);
        }else if(!loan.entrySet().isEmpty()){
            text(g,tr(str(loan,"state").equals("REQUESTED")&&flag(data,"manager")?"review_title":"contract_title"),docX+17,top+14,docW-72,INK);
            text(g,tr("state_"+str(loan,"state").toLowerCase(Locale.ROOT)),docX+17,top+29,docW-72,flag(loan,"overdue")?0xFF96323C:0xFF5D6F48);
            seal(g,docX+docW-31,top+25,31,stampAt>0&&System.currentTimeMillis()-stampAt<1500?stampAt:0);
            if(str(loan,"state").equals("REQUESTED")&&flag(data,"manager")){
                text(g,tight()?Component.literal(str(loan,"name")+" · "+money(num(loan,"principal"),currency(loan))):tr("borrower",str(loan,"name")),docX+17,top+46,docW-34,INK);if(!tight())text(g,tr("principal_value",money(num(loan,"principal"),currency(loan))),docX+17,top+59,docW-34,INK);
                int fw=(docW-46)/3,yy=top+(tight()?62:compact()?69:91);text(g,tr("interest"),docX+17,yy,fw,INK);text(g,tr("installments"),docX+23+fw,yy,fw,INK);text(g,tr("interval_days"),docX+29+2*fw,yy,fw,INK);
            }else if(!compact()){
                int cardW=(docW-44)/2;moneyCard(g,tr("amount"),money(num(loan,"principal"),currency(loan)),docX+17,top+50,cardW);
                moneyCard(g,tr("total_label"),money(num(loan,"total"),currency(loan)),docX+27+cardW,top+50,cardW);
            }
        }else{para(g,tr("select_hint"),docX+20,top+29,docW-40,INK,6);seal(g,docX+docW/2,bottom-67,46,0);}
        Component footer=waiting?tr("waiting"):status.isEmpty()?tr("footer"):Component.translatable(status);text(g,footer,x+14,y+h-20,w-110,status.contains(".error.")?0xFFEAA29C:MUTED);
        super.render(g,mx,my,partial);
        if(!creating&&str(loan,"state").equals("REQUESTED")&&flag(data,"manager")&&mx>=docX&&mx<docX+docW&&my>=top&&my<top+59)g.renderTooltip(font,font.split(Component.literal(str(loan,"purpose")).append("\n").append(tr("approval_hint")),Math.min(300,width-20)),mx,my);
    }
    private void moneyCard(GuiGraphics g,Component label,String value,int xx,int yy,int width){TreasuryArt.frame(g,xx,yy,width,43,0xFFE5D0A4,0xFFB49356);text(g,label,xx+7,yy+6,width-14,0xFF7B694D);text(g,Component.literal(value).withStyle(s->s.withBold(true)),xx+7,yy+23,width-14,INK);}
    private void seal(GuiGraphics g,int cx,int cy,int size,long started){float elapsed=started==0?1:Math.clamp((System.currentTimeMillis()-started)/500F,0,1);if(minecraft.options.screenEffectScale().get()==0)elapsed=1;float scale=elapsed<1?1+.5F*(1-elapsed)*(float)Math.cos(elapsed*Math.PI*2):1;g.pose().pushPose();g.pose().translate(cx,cy-8*(1-elapsed),0);g.pose().scale(scale,scale,1);g.blit(SEAL,-size/2,-size/2,size,size,0,0,64,64,64,64);g.pose().popPose();}
    private final class OfferButton extends AbstractButton {
        private final JsonObject row;private final Runnable action;
        OfferButton(int x,int y,int w,JsonObject row,Runnable action){super(x,y,w,42,Component.literal(str(row,"name")));this.row=row;this.action=action;}
        @Override public void onPress(){if(!waiting)action.run();}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){boolean chosen=str(row,"id").equals(selected)&&!creating;TreasuryArt.frame(g,getX(),getY(),width,height,chosen?0xFF38584A:isHoveredOrFocused()?0xFF30483D:0xFF263830,chosen?GOLD:0xFF5C6550);text(g,Component.literal(str(row,"name")),getX()+8,getY()+5,width-16,CREAM);text(g,Component.literal(money(num(row,"principal"),currency(row))),getX()+8,getY()+18,width-16,GOLD);text(g,tr(flag(row,"overdue")?"state_overdue":"state_"+str(row,"state").toLowerCase(Locale.ROOT)),getX()+8,getY()+30,width-16,flag(row,"overdue")?0xFFEEAAA2:MUTED);}
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);output.add(NarratedElementType.HINT,contract(row));}
    }
    private static final class CreditButton extends Button {
        private final boolean primary;CreditButton(Builder b,boolean primary){super(b);this.primary=primary;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){TreasuryArt.frame(g,getX(),getY(),width,height,!active?0xFF4D4840:isHoveredOrFocused()?0xFF446451:primary?GREEN:0xFF40362E,active?GOLD:0xFF837158);var font=Minecraft.getInstance().font;var message=fit(font,getMessage(),width-10);g.drawString(font,message,getX()+(width-font.width(message))/2,getY()+7,active?CREAM:0xFFB5A68C,false);}
    }
    private static final class PaperText extends AbstractScrollWidget {
        private final List<FormattedCharSequence> lines;
        PaperText(int x,int y,int width,int height,Component text){super(x,y,width,height,text);lines=Minecraft.getInstance().font.split(text,Math.max(8,width-14));}
        @Override protected int getInnerHeight(){return lines.size()*12;}
        @Override protected double scrollRate(){return 12;}
        @Override protected void renderBackground(GuiGraphics g){}
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float partial){for(int i=0;i<lines.size();i++)g.drawString(Minecraft.getInstance().font,lines.get(i),getX()+innerPadding(),getY()+innerPadding()+i*12,INK,false);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){out.add(NarratedElementType.TITLE,getMessage());}
    }
}
