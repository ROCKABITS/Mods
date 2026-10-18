package pt.realmcasino.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import pt.realmcasino.network.CasinoNetwork;

/** Physical casino menu. Every outcome, balance and payment comes from an authoritative S2C snapshot. */
public final class CasinoScreen extends Screen {
    private enum Tab { PLAY, RULES, OWNER }
    private record BodyWidget(AbstractWidget widget,int y) {}
    private static final String[] SYMBOLS={"crown","ruby","sapphire","emerald","bell","moon","horseshoe","cherry"};
    private static final int[] TRIPLES={100,80,50,32,24,16,12,6};
    private static final int[] WHEEL={0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26};
    private static final int[] RED_NUMBERS={1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36};
    private JsonObject data;
    private BiConsumer<String,JsonObject> requestSender=CasinoNetwork::request;
    private Tab tab=Tab.PLAY;
    private final List<Consumer<GuiGraphics>> paintings=new ArrayList<>();
    private final List<BodyWidget> bodyWidgets=new ArrayList<>();
    private EditBox stakeInput,amountInput,nameInput,minInput,maxInput;
    private String stakeDraft="",amountDraft="100.00",nameDraft="",minDraft="",maxDraft="",selection="red",notice="",animatedRound="";
    private int selectedNumber=17,px,py,pw,ph,bodyTop,bodyBottom,scroll,contentHeight,ticks,waitTicks,reelSounds;
    private long animatedRevision=-1,animationStart;
    private boolean waiting,error,completionSound;
    private boolean lastActive;

    private CasinoScreen(JsonObject snapshot) {
        super(Component.translatable("gui.realmcasino.title"));data=snapshot;
        stakeDraft=decimal(Math.max(1,num(data,"minBet")));nameDraft=str(data,"name");minDraft=decimal(num(data,"minBet"));maxDraft=decimal(num(data,"maxBet"));
        readNotice();beginAnimation(false);
    }
    public static void receive(String json) {
        if(json==null||json.length()>65536)return;final JsonObject snapshot;
        try {snapshot=JsonParser.parseString(json).getAsJsonObject();}catch(RuntimeException malformed){return;}
        var mc=Minecraft.getInstance();mc.execute(()->{
            if(flag(snapshot,"closed")){if(mc.screen instanceof CasinoScreen)mc.setScreen(null);if(mc.player!=null&&!str(snapshot,"error").isEmpty())mc.player.displayClientMessage(Component.translatable(str(snapshot,"error")),false);return;}
            if(mc.player==null||mc.level==null)return;
            if(mc.screen instanceof CasinoScreen current&&str(current.data,"machineId").equals(str(snapshot,"machineId")))current.accept(snapshot);
            else mc.setScreen(new CasinoScreen(snapshot));
        });
    }
    private void accept(JsonObject snapshot) {
        remember();data=snapshot;waiting=false;waitTicks=0;readNotice();beginAnimation(true);
        if(!flag(data,"owner"))tab=tab==Tab.OWNER?Tab.PLAY:tab;
        if("gui.realmcasino.configured".equals(str(data,"message"))){nameDraft=str(data,"name");minDraft=decimal(num(data,"minBet"));maxDraft=decimal(num(data,"maxBet"));}
        rebuild();
    }
    private void readNotice(){notice=str(data,"error");error=!notice.isBlank();if(!error)notice=str(data,"message");}
    private static String str(JsonObject o,String k){try{return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}catch(RuntimeException e){return "";}}
    private static long num(JsonObject o,String k){try{return o.has(k)?o.get(k).getAsLong():0;}catch(RuntimeException e){return 0;}}
    private static boolean flag(JsonObject o,String k){try{return o.has(k)&&o.get(k).getAsBoolean();}catch(RuntimeException e){return false;}}
    private static JsonArray array(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private JsonObject round(){return data.has("round")&&data.get("round").isJsonObject()?data.getAsJsonObject("round"):new JsonObject();}
    private String game(){return str(data,"game");}
    private boolean activeHand(){return "active".equals(str(round(),"phase"));}
    private static Component tr(String key,Object...args){return Component.translatable("gui.realmcasino."+key,args);}
    public static String decimal(long cents){return BigDecimal.valueOf(cents,2).setScale(2).toPlainString();}
    private static Component money(long cents){return tr("money_format",decimal(cents));}
    private static long parseAmount(String text){return new BigDecimal(text.strip().replace(',','.')).setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();}
    private static long now(){return System.nanoTime()/1_000_000;}
    private void beginAnimation(boolean received) {
        JsonObject r=round();String id=str(r,"id");long revision=num(r,"revision");
        if(id.isBlank()||id.equals(animatedRound)&&revision==animatedRevision)return;
        boolean recent=num(data,"serverNow")-num(r,"startedAt")<10_000;
        animatedRound=id;animatedRevision=revision;lastActive=activeHand();reelSounds=0;completionSound=false;
        animationStart=(received||recent)&&CasinoPreferences.get().motion?now():now()-10_000;
        if(received)sound(0);
    }
    private long duration(){return game().equals("slots")?2_600:game().equals("roulette")?3_200:850;}
    private double progress(){return Math.clamp((now()-animationStart)/(double)duration(),0,1);}
    private boolean animating(){return !str(round(),"id").isBlank()&&progress()<1;}
    private void sound(int kind){if(!CasinoPreferences.get().sound)return;var event=kind==2?SoundEvents.EXPERIENCE_ORB_PICKUP:kind==1?SoundEvents.NOTE_BLOCK_HAT.value():SoundEvents.UI_BUTTON_CLICK.value();Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event,kind==2?1.25f:kind==1?0.8f:0.85f,kind==2?0.13f:0.09f));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial){}
    @Override public void tick(){
        ticks++;if(waiting&&++waitTicks>200){remember();waiting=false;notice="gui.realmcasino.connection_wait";error=true;rebuild();}
        if(!str(round(),"id").isBlank()){
            double p=progress();if(game().equals("slots")){int stops=(p>=.68?1:0)+(p>=.84?1:0)+(p>=1?1:0);while(reelSounds<stops){sound(1);reelSounds++;}}
            if(p>=1&&!completionSound){remember();completionSound=true;if(!activeHand()&&num(round(),"payout")>num(round(),"stake"))sound(2);rebuild();}
        }
        // Refresh also detects a timeout, another user's busy hand, or a dismantled physical machine.
        if(ticks%120==0&&!waiting&&!animating()&&bodyWidgets.stream().noneMatch(e->e.widget() instanceof EditBox box&&box.isFocused()))request("refresh",new JsonObject());
        super.tick();
    }
    private boolean wide(){return pw>=600;}
    private boolean compact(){return ph<300;}
    @Override protected void init(){
        paintings.clear();bodyWidgets.clear();stakeInput=null;amountInput=null;nameInput=null;minInput=null;maxInput=null;
        pw=Math.min(780,width-12);ph=Math.min(430,height-12);px=(width-pw)/2;py=(height-ph)/2;
        int tabsY=py+(compact()?34:51);bodyTop=tabsY+27;bodyBottom=py+ph-(compact()?34:41);
        int count=flag(data,"owner")?3:2,tw=Math.min(142,(pw-36-(count-1)*5)/count);
        for(int i=0;i<count;i++){Tab value=Tab.values()[i];button(px+17+i*(tw+5),tabsY,tw,20,tr(value.name().toLowerCase(Locale.ROOT)),()->{remember();tab=value;scroll=0;rebuild();},value==tab,false);}
        int x=px+17,w=pw-34,y=bodyTop;
        if(tab==Tab.PLAY){
            if(wide()){int side=230,gw=w-side-12;int a=gamePanel(x,y,gw),b=summary(x+gw+12,y,side);contentHeight=Math.max(a,b)-y;}
            else {int end=gamePanel(x,y,w);contentHeight=summary(x,end+10,w)-y;}
        }else if(tab==Tab.RULES)contentHeight=rules(x,y,w)-y;
        else contentHeight=owner(x,y,w)-y;
        int comfortX=px+pw-(compact()?90:204),comfortY=py+14;
        var soundButton=button(comfortX,comfortY,compact()?33:86,17,compact()?Component.literal(CasinoPreferences.get().sound?"♫":"×♫"):tr(CasinoPreferences.get().sound?"sound_on":"sound_off"),()->{remember();CasinoPreferences.get().sound=!CasinoPreferences.get().sound;CasinoPreferences.get().save();rebuild();},false,false);
        soundButton.setTooltip(Tooltip.create(tr(CasinoPreferences.get().sound?"sound_on":"sound_off")));
        var motionButton=button(comfortX+(compact()?37:92),comfortY,compact()?33:91,17,compact()?Component.literal(CasinoPreferences.get().motion?"»":"−"):tr(CasinoPreferences.get().motion?"motion_on":"motion_off"),()->{remember();CasinoPreferences.get().motion=!CasinoPreferences.get().motion;CasinoPreferences.get().save();if(!CasinoPreferences.get().motion)animationStart=now()-10_000;rebuild();},false,false);
        motionButton.setTooltip(Tooltip.create(tr(CasinoPreferences.get().motion?"motion_on":"motion_off")));
        scroll=Math.clamp(scroll,0,maximumScroll());positionWidgets();
        button(px+pw-92,py+ph-27,75,18,tr("close"),this::onClose,false,false);
    }
    private void remember(){if(stakeInput!=null)stakeDraft=stakeInput.getValue();if(amountInput!=null)amountDraft=amountInput.getValue();if(nameInput!=null)nameDraft=nameInput.getValue();if(minInput!=null)minDraft=minInput.getValue();if(maxInput!=null)maxDraft=maxInput.getValue();}
    private void rebuild(){clearWidgets();init();}
    private int gamePanel(int x,int y,int w){
        int h=game().equals("roulette")?249:game().equals("blackjack")?238:225;
        final int top=y;paintings.add(g->{CasinoTheme.felt(g,x,top,w,h);if(game().equals("slots"))drawSlots(g,x,top,w,h);else if(game().equals("roulette"))drawRoulette(g,x,top,w,h);else drawBlackjack(g,x,top,w,h);});
        y+=h+8;
        if(game().equals("roulette")){
            label(tr("bet_choice"),x+3,y,w,CasinoTheme.MUTED,false);y+=15;
            int cell=(w-16)/5;for(int i=0;i<5;i++){String choice=List.of("red","black","even","odd","number").get(i);bodyButton(x+i*(cell+4),y,cell,19,tr(choice),()->{remember();selection=choice;rebuild();},selection.equals(choice));}y+=25;
            if(selection.equals("number")){int ww=(w-12)/9;for(int n=0;n<=36;n++){final int pocket=n;int row=n/9,col=n%9;bodyButton(x+col*(ww+1),y+row*20,ww,18,Component.literal(""+n),()->{remember();selectedNumber=pocket;rebuild();},selectedNumber==n);}y+=103;}
        }
        if(activeHand()&&game().equals("blackjack")){
            label(tr("your_turn"),x+4,y,w-8,CasinoTheme.LIGHT,true);y+=17;
            var hit=bodyButton(x,y,(w-5)/2,23,tr("hit"),()->handAction("hit"),false);hit.active=flag(round(),"canHit")&&!waiting&&!animating();
            var stand=bodyButton(x+(w+5)/2,y,(w-5)/2,23,tr("stand"),()->handAction("stand"),true);stand.active=flag(round(),"canStand")&&!waiting&&!animating();y+=30;
        }else if(!wide())y=stakeControls(x,y,w);
        return y;
    }
    private int stakeControls(int x,int y,int w){
            label(tr("stake"),x+3,y,w-6,CasinoTheme.MUTED,false);y+=14;
            stakeInput=input(x,y,Math.max(60,w-176),stakeDraft,22,tr("stake"));
            int bx=x+Math.max(60,w-176)+5,bw=(w-Math.max(60,w-176)-10)/2;
            bodyButton(bx,y,bw,20,tr("half"),()->adjustStake(false),false);bodyButton(bx+bw+5,y,bw,20,tr("double"),()->adjustStake(true),false);y+=27;
            String key=game().equals("slots")?"spin":game().equals("roulette")?"roulette_spin":"deal";
            var play=bodyButton(x,y,w,25,tr(key),this::bet,true);play.active=canBet();y+=32;
            Component restriction=!flag(data,"economyAvailable")?tr("chronicles_required"):flag(data,"busy")?tr("busy"):!affordable()?tr("bank_unavailable"):tr("rules_hint");
            y=paragraph(restriction,x+3,y,w-6,!flag(data,"economyAvailable")||flag(data,"busy")?0xFFDEA286:CasinoTheme.MUTED)+4;
        return y;
    }
    private int summary(int x,int y,int w){
        final int top=y;paintings.add(g->CasinoTheme.plaque(g,x,top,w,111));
        stat(tr("wallet"),money(num(data,"balance")),x+12,y+12,w-24,CasinoTheme.LIGHT);stat(tr("bank"),money(num(data,"bank")),x+12,y+42,w-24,CasinoTheme.GOLD);stat(tr("available"),money(num(data,"available")),x+12,y+74,w-24,CasinoTheme.MUTED);y+=121;
        if(wide()&&!activeHand())y=stakeControls(x,y,w)+7;
        label(tr("bet_limits",money(num(data,"minBet")),money(num(data,"maxBet"))),x+3,y,w-6,CasinoTheme.MUTED,false);y+=19;
        if(num(data,"pending")>0&&!animating()){label(tr("pending",money(num(data,"pending"))),x+3,y,w-6,CasinoTheme.GOLD,true);y+=17;var claim=bodyButton(x,y,w,22,tr("claim"),()->{var a=new JsonObject();a.addProperty("amount",0);request("claim",a);},true);claim.active=!waiting&&flag(data,"economyAvailable");y+=30;}
        y=paragraph(tr("virtual_only"),x+3,y,w-6,CasinoTheme.MUTED)+10;
        label(tr("history"),x+3,y,w-6,CasinoTheme.GOLD,true);y+=18;
        JsonArray history=animating()?new JsonArray():array(data,"history");if(history.isEmpty())y=paragraph(tr(animating()?"settling":"no_history"),x+3,y,w-6,CasinoTheme.MUTED)+8;
        for(int i=0;i<Math.min(7,history.size());i++)if(history.get(i).isJsonObject()){
            var row=history.get(i).getAsJsonObject();final int yy=y;paintings.add(g->g.fill(x,yy,x+w,yy+30,iColor(yy)));
            label(Component.literal(str(row,"playerName")),x+6,y+4,w-12,CasinoTheme.MUTED,false);
            label(Component.translatable("gui.realmcasino.history_"+str(row,"kind")),x+6,y+17,w-90,CasinoTheme.LIGHT,false);label(money(num(row,"amount")),x+w-86,y+17,80,CasinoTheme.GOLD,false);y+=34;
        }
        return y;
    }
    private static int iColor(int y){return (y/34)%2==0?0x3326181A:0x33483231;}
    private void stat(Component title,Component value,int x,int y,int w,int color){label(title,x,y,w,CasinoTheme.MUTED,false);label(value,x,y+12,w,color,true);}
    private int rules(int x,int y,int w){
        label(tr("rules_title"),x+7,y+3,w-14,CasinoTheme.GOLD,true);y+=25;
        int textW=wide()?w-32:w-14;
        y=paragraph(tr(game()+"_rules"),x+7,y,textW,CasinoTheme.LIGHT)+12;
        if(game().equals("slots")){
            y=paragraph(tr("slots_rtp"),x+7,y,textW,CasinoTheme.GOLD)+14;label(tr("slots_triples"),x+7,y,textW,CasinoTheme.LIGHT,true);y+=19;
            int cols=wide()?2:1,cell=(w-12)/cols;
            for(int i=0;i<8;i++){int sx=x+6+(i%cols)*cell,sy=y+(i/cols)*38,idx=i;paintings.add(g->{CasinoTheme.plaque(g,sx,sy,cell-6,33);CasinoTheme.symbol(g,idx,sx+18,sy+16,1);});label(tr("symbol_"+SYMBOLS[i]),sx+37,sy+7,cell-96,CasinoTheme.LIGHT,false);label(Component.literal(TRIPLES[i]+"×"),sx+cell-53,sy+12,42,CasinoTheme.GOLD,true);}
            y+=((8+cols-1)/cols)*38+6;y=paragraph(tr("slots_probability"),x+7,y,textW,CasinoTheme.MUTED)+9;y=paragraph(tr("slots_pair"),x+7,y,textW,CasinoTheme.LIGHT)+6;y=paragraph(tr("slots_none"),x+7,y,textW,CasinoTheme.MUTED)+12;
        }else if(game().equals("roulette"))y=paragraph(tr("roulette_rtp"),x+7,y,textW,CasinoTheme.GOLD)+15;
        else {y=paragraph(tr("blackjack_returns"),x+7,y,textW,CasinoTheme.GOLD)+12;y=paragraph(tr("blackjack_rtp"),x+7,y,textW,CasinoTheme.MUTED)+12;y=paragraph(tr("blackjack_leaving"),x+7,y,textW,CasinoTheme.LIGHT)+12;}
        y=paragraph(tr("payout_explanation"),x+7,y,textW,CasinoTheme.LIGHT)+12;y=paragraph(tr("server_only"),x+7,y,textW,CasinoTheme.MUTED)+12;y=paragraph(tr("bank_cover"),x+7,y,textW,CasinoTheme.MUTED)+12;return paragraph(tr("virtual_only"),x+7,y,textW,CasinoTheme.GOLD)+12;
    }
    private int owner(int x,int y,int w){
        label(tr("owner_controls"),x+5,y+3,w-10,CasinoTheme.GOLD,true);y+=25;
        if(wide()){
            int cw=(w-14)/2;int end=ownerConfig(x,y,cw);int other=ownerFunds(x+cw+14,y,cw);return Math.max(end,other);
        }
        int end=ownerConfig(x,y,w);return ownerFunds(x,end+17,w);
    }
    private int ownerConfig(int x,int y,int w){
        y=paragraph(tr("owner_rules"),x+4,y,w-8,CasinoTheme.MUTED)+13;
        label(tr("name"),x+3,y,w-6,CasinoTheme.MUTED,false);y+=15;nameInput=input(x,y,w,nameDraft,48,tr("name"));y+=29;
        label(tr("minimum"),x+3,y,w-6,CasinoTheme.MUTED,false);y+=15;minInput=input(x,y,w,minDraft,22,tr("minimum"));y+=29;
        label(tr("maximum"),x+3,y,w-6,CasinoTheme.MUTED,false);y+=15;maxInput=input(x,y,w,maxDraft,22,tr("maximum"));y+=29;
        var save=bodyButton(x,y,w,23,tr("configure"),this::configure,true);save.active=!waiting;y+=31;
        return paragraph(tr("server_limits",money(num(data,"serverMin")),money(num(data,"serverMax"))),x+3,y,w-6,CasinoTheme.MUTED)+6;
    }
    private int ownerFunds(int x,int y,int w){
        final int top=y;paintings.add(g->CasinoTheme.plaque(g,x,top,w,112));stat(tr("bank"),money(num(data,"bank")),x+10,y+10,w-20,CasinoTheme.GOLD);stat(tr("locked"),money(num(data,"reserved")),x+10,y+42,w-20,CasinoTheme.MUTED);stat(tr("available"),money(num(data,"available")),x+10,y+75,w-20,CasinoTheme.LIGHT);y+=123;
        label(tr("amount"),x+3,y,w-6,CasinoTheme.MUTED,false);y+=15;amountInput=input(x,y,w,amountDraft,22,tr("amount"));y+=28;
        var deposit=bodyButton(x,y,w,23,tr("deposit"),()->funds("deposit"),true);deposit.active=flag(data,"economyAvailable")&&!waiting;y+=29;
        var withdraw=bodyButton(x,y,w,23,tr("withdraw"),()->funds("withdraw"),false);withdraw.active=flag(data,"economyAvailable")&&!waiting&&num(data,"available")>0;y+=31;
        if(num(data,"pending")>0&&!animating()){label(tr("pending",money(num(data,"pending"))),x+3,y,w-6,CasinoTheme.GOLD,false);y+=17;var claim=bodyButton(x,y,w,22,tr("claim"),()->{JsonObject p=new JsonObject();p.addProperty("amount",0);request("claim",p);},true);claim.active=flag(data,"economyAvailable")&&!waiting;y+=29;}
        y=paragraph(tr("owner_bank_hint"),x+3,y,w-6,CasinoTheme.MUTED)+10;return paragraph(tr("owner_limits"),x+3,y,w-6,CasinoTheme.MUTED)+6;
    }
    private void adjustStake(boolean up){remember();try{long current=parseAmount(stakeDraft),next=up?Math.multiplyExact(current,2):current/2;next=Math.clamp(next,num(data,"minBet"),num(data,"maxBet"));if(game().equals("blackjack")&&next%2!=0)next=Math.max(num(data,"minBet"),next-1);stakeDraft=decimal(next);rebuild();}catch(RuntimeException invalid){localAmountError();}}
    private long draftStake(){try{return parseAmount(stakeInput!=null?stakeInput.getValue():stakeDraft);}catch(RuntimeException invalid){return -1;}}
    private boolean affordable(){long amount=draftStake();if(amount<=0)return false;try{long max=game().equals("slots")?Math.multiplyExact(amount,100):game().equals("roulette")?Math.multiplyExact(amount,36):Math.addExact(Math.multiplyExact(amount,2),amount/2);return Math.addExact(num(data,"available"),amount)>=max;}catch(ArithmeticException overflow){return false;}}
    private boolean canBet(){long amount=draftStake();return !waiting&&!animating()&&!flag(data,"busy")&&flag(data,"economyAvailable")&&amount>=num(data,"minBet")&&amount<=num(data,"maxBet")&&amount>0&&amount<=num(data,"balance")&&affordable()&&(!game().equals("blackjack")||amount%2==0);}
    private void bet(){remember();if(!canBet())return;JsonObject args=new JsonObject();args.addProperty("amount",parseAmount(stakeDraft));if(game().equals("roulette")){args.addProperty("selection",selection);if(selection.equals("number"))args.addProperty("number",selectedNumber);}request("bet",args);}
    private void handAction(String operation){JsonObject args=new JsonObject();args.addProperty("round",str(round(),"id"));args.addProperty("revision",num(round(),"revision"));request(operation,args);}
    private void funds(String operation){remember();try{long amount=parseAmount(amountDraft);if(amount<=0)throw new IllegalArgumentException();JsonObject args=new JsonObject();args.addProperty("amount",amount);request(operation,args);}catch(RuntimeException invalid){localAmountError();}}
    private void configure(){remember();try{long min=parseAmount(minDraft),max=parseAmount(maxDraft);if(min<=0||max<min)throw new IllegalArgumentException();JsonObject args=new JsonObject();args.addProperty("name",nameDraft);args.addProperty("minBet",min);args.addProperty("maxBet",max);request("configure",args);}catch(RuntimeException invalid){localAmountError();}}
    private void localAmountError(){notice="gui.realmcasino.positive_amount";error=true;rebuild();}
    private void request(String operation,JsonObject args){if(waiting)return;remember();if(!List.of("refresh","close").contains(operation)){args.addProperty("nonce",str(data,"nonce"));args.addProperty("version",num(data,"version"));}waiting=true;waitTicks=0;requestSender.accept(operation,args);if(!operation.equals("refresh"))rebuild();}
    @Override public void onClose(){requestSender.accept("close",new JsonObject());super.onClose();}
    private void label(Component value,int x,int y,int width,int color,boolean bold){paintings.add(g->g.drawString(font,CasinoTheme.fit(font,value,Math.max(1,width),bold),x,y,color,false));}
    private int paragraph(Component value,int x,int y,int width,int color){var lines=font.split(value,Math.max(1,width));for(var line:lines){int yy=y;paintings.add(g->g.drawString(font,line,x,yy,color,false));y+=12;}return y;}
    private EditBox input(int x,int y,int w,String value,int max,Component description){EditBox box=new EditBox(font,x,y,w,20,description);box.setMaxLength(max);box.setValue(value);box.setBordered(false);box.setTextColor(CasinoTheme.LIGHT);box.setTextColorUneditable(CasinoTheme.MUTED);box.setTooltip(Tooltip.create(description));paintings.add(g->{CasinoTheme.plaque(g,x-1,y-2,w+2,24);g.fill(x+3,y+19,x+w-3,y+20,0xFF6B5140);});box.setY(y+6);box.setX(x+5);box.setWidth(Math.max(10,w-10));addRenderableWidget(box);bodyWidgets.add(new BodyWidget(box,y+6));return box;}
    private CasinoButton button(int x,int y,int w,int h,Component value,Runnable action,boolean selected,boolean body){CasinoButton b=addRenderableWidget(new CasinoButton(x,y,w,h,value,action,selected));if(body)bodyWidgets.add(new BodyWidget(b,y));return b;}
    private CasinoButton bodyButton(int x,int y,int w,int h,Component value,Runnable action,boolean selected){return button(x,y,w,h,value,action,selected,true);}
    private int maximumScroll(){return Math.max(0,contentHeight-(bodyBottom-bodyTop));}
    private void positionWidgets(){for(var entry:bodyWidgets){var widget=entry.widget();widget.setY(entry.y()-scroll);widget.visible=widget.getY()>=bodyTop&&widget.getY()+widget.getHeight()<=bodyBottom;if(!widget.visible)widget.setFocused(false);}}
    private void centered(GuiGraphics g,Component value,int cx,int y,int maxWidth,int color,boolean bold){Component fitted=CasinoTheme.fit(font,value,maxWidth,bold);g.drawString(font,fitted,cx-font.width(fitted)/2,y,color,false);}
    private void drawSlots(GuiGraphics g,int x,int y,int w,int h){
        centered(g,tr("slots_subtitle"),x+w/2,y+15,w-24,CasinoTheme.MUTED,false);
        int rw=Math.min(87,(w-46)/3),gap=9,total=rw*3+gap*2,left=x+(w-total)/2,top=y+42,rh=110;
        CasinoTheme.plaque(g,left-7,top-8,total+14,rh+16);
        JsonArray results=array(round(),"results");double p=progress();
        for(int reel=0;reel<3;reel++){
            int rx=left+reel*(rw+gap);g.fill(rx,top,rx+rw,top+rh,0xFFBDA276);g.fill(rx+2,top+2,rx+rw-2,top+rh-2,0xFFF4E2BA);
            g.enableScissor(rx+3,top+3-scroll,rx+rw-3,top+rh-3-scroll);
            int result=results.size()==3?results.get(reel).getAsInt():reel;
            double local=Math.clamp(p/(reel==0?.68:reel==1?.84:1),0,1),ease=1-Math.pow(1-local,3);
            double offset=result*43+(results.size()==3?(1-ease)*43*8*(3+reel):0),fraction=offset/43-Math.floor(offset/43);int base=(int)Math.floor(offset/43);
            for(int row=-2;row<=2;row++)CasinoTheme.symbol(g,Math.floorMod(base+row,8),rx+rw/2,(int)Math.round(top+rh/2+row*43-fraction*43),Math.max(1,Math.min(2,rw/30)));
            g.disableScissor();g.fill(rx+2,top+2,rx+rw-2,top+12,0x442D2019);g.fill(rx+2,top+rh-12,rx+rw-2,top+rh-2,0x442D2019);
            if(local>=1&&results.size()==3)CasinoTheme.border(g,rx+1,top+rh/2-23,rw-2,46,0xFFB58A42);
        }
        int line=top+rh/2;g.fill(left-6,line,left-1,line+1,CasinoTheme.GOLD);CasinoTheme.diamond(g,left-10,line,3,CasinoTheme.GOLD);g.fill(left+total+1,line,left+total+6,line+1,CasinoTheme.GOLD);CasinoTheme.diamond(g,left+total+10,line,3,CasinoTheme.GOLD);
        centered(g,tr("gross"),x+w/2,y+h-49,w-24,CasinoTheme.MUTED,false);drawOutcome(g,x,y+h-32,w);
    }
    private static int pocketColor(int number){if(number==0)return 0xFF2F7752;for(int red:RED_NUMBERS)if(red==number)return 0xFFA44244;return 0xFF25282B;}
    private static final List<int[]> WHEEL_PIXELS=wheelPixels();
    private static List<int[]> wheelPixels(){var spans=new ArrayList<int[]>();int r=75;for(int yy=-r;yy<=r;yy++){int begin=-r,last=0;for(int xx=-r;xx<=r+1;xx++){double distance=Math.hypot(xx,yy);int color=0;if(xx<=r&&distance<=r&&distance>=35){double a=Math.atan2(yy,xx);if(a<0)a+=Math.PI*2;int index=(int)(a/(Math.PI*2)*37)%37;color=pocketColor(WHEEL[index]);}if(color!=last){if(last!=0)spans.add(new int[]{begin,yy,xx,last});begin=xx;last=color;}}}return List.copyOf(spans);}
    private void drawRoulette(GuiGraphics g,int x,int y,int w,int h){
        centered(g,tr("roulette_subtitle"),x+w/2,y+14,w-24,CasinoTheme.MUTED,false);
        int cx=x+w/2,cy=y+118;double p=progress();JsonArray results=array(round(),"results");int result=results.size()==1?results.get(0).getAsInt():0,index=0;for(int i=0;i<37;i++)if(WHEEL[i]==result)index=i;
        double finalAngle=-Math.PI/2-(index+.5)*Math.PI*2/37,spin=results.size()==1?(1-Math.pow(1-p,4))*Math.PI*8:0;
        double wheelAngle=results.size()==1?finalAngle+Math.PI*8-spin:-Math.PI/2;
        CasinoTheme.disk(g,cx+2,cy+4,84,0x55000000);CasinoTheme.disk(g,cx,cy,83,0xFF513829);CasinoTheme.disk(g,cx,cy,80,0xFFE1BB6B);CasinoTheme.disk(g,cx,cy,77,0xFF8B6336);
        g.pose().pushPose();g.pose().translate(cx,cy,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)wheelAngle));
        for(int[] span:WHEEL_PIXELS)g.fill(span[0],span[1],span[2],span[1]+1,span[3]);
        for(int i=0;i<37;i++){
            double angle=(i+.5)*Math.PI*2/37;String text=""+WHEEL[i];
            g.pose().pushPose();g.pose().translate(Math.cos(angle)*64,Math.sin(angle)*64,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)(angle+Math.PI/2)));g.pose().scale(.72f,.72f,1);g.drawString(font,text,-font.width(text)/2,-4,CasinoTheme.LIGHT,false);g.pose().popPose();
        }
        g.pose().popPose();CasinoTheme.disk(g,cx,cy,34,0xFFD2A45D);CasinoTheme.disk(g,cx,cy,31,0xFF65462E);CasinoTheme.disk(g,cx,cy,25,0xFF9C6D3C);CasinoTheme.crown(g,cx,cy,2);
        double ballAngle=results.size()==1?-Math.PI/2+(1-Math.pow(1-p,3))*Math.PI*-10+Math.PI*10:-Math.PI/2;
        int radius=(int)Math.round(79-8*Math.pow(p,4));int bx=cx+(int)Math.round(Math.cos(ballAngle)*radius),by=cy+(int)Math.round(Math.sin(ballAngle)*radius);
        CasinoTheme.disk(g,bx+1,by+1,3,0x77000000);CasinoTheme.disk(g,bx,by,3,CasinoTheme.LIGHT);g.fill(bx-1,by-2,bx+1,by,0xFFFFFFFF);
        centered(g,selection.equals("number")?tr("selected_number",selectedNumber):tr(selection),cx,y+h-37,w-24,CasinoTheme.MUTED,false);drawOutcome(g,x,y+h-22,w);
    }
    private static String cardText(int id){if(id<0)return "";int rank=id%13+1;String value=rank==1?"A":rank==11?"J":rank==12?"Q":rank==13?"K":""+rank;return value+"SHCD".charAt(id/13);}
    private static int total(JsonArray cards){int sum=0,aces=0;for(var value:cards){int id=value.getAsInt();if(id<0)return -1;int rank=id%13+1;if(rank==1){sum+=11;aces++;}else sum+=Math.min(10,rank);}while(sum>21&&aces-->0)sum-=10;return sum;}
    private void drawBlackjack(GuiGraphics g,int x,int y,int w,int h){
        JsonArray dealer=array(round(),"dealerCards"),player=array(round(),"playerCards");
        centered(g,tr("dealer"),x+w/2,y+13,w-20,CasinoTheme.GOLD,true);drawHand(g,dealer,x+14,y+30,w-28,63,true);
        centered(g,tr("you"),x+w/2,y+118,w-20,CasinoTheme.GOLD,true);drawHand(g,player,x+14,y+135,w-28,63,false);
        if(player.isEmpty()){centered(g,tr("blackjack_subtitle"),x+w/2,y+83,w-24,CasinoTheme.MUTED,false);centered(g,tr("ready"),x+w/2,y+199,w-24,CasinoTheme.MUTED,false);}
        else {int playerTotal=total(player);centered(g,tr("total",playerTotal),x+w/2,y+204,w-24,CasinoTheme.LIGHT,true);}
        drawOutcome(g,x,y+h-16,w);
    }
    private void drawHand(GuiGraphics g,JsonArray cards,int x,int y,int w,int h,boolean dealer){
        if(cards.isEmpty()){CasinoTheme.border(g,x+w/2-21,y,42,h,0xFF41694F);CasinoTheme.diamond(g,x+w/2,y+h/2,4,0xFF41694F);return;}
        int cw=42,spacing=Math.min(49,Math.max(12,(w-cw)/Math.max(1,cards.size()-1))),full=cw+(cards.size()-1)*spacing,left=x+(w-full)/2;
        double p=progress();for(int i=0;i<cards.size();i++){
            int id=cards.get(i).getAsInt();boolean hidden=id<0;double local=CasinoPreferences.get().motion?Math.clamp(p*2.2-i*.16-(dealer?.12:0),0,1):1;
            int offset=(int)Math.round((1-local)*30);float reveal=(float)Math.min(1,local*1.7);
            CasinoTheme.card(g,font,cardText(id),left+i*spacing+offset,y-offset/3,cw,h,hidden,reveal);
        }
        if(dealer){int value=total(cards);if(value>=0&&!animating())centered(g,tr("total",value),x+w/2,y+h+6,w,CasinoTheme.MUTED,false);}
    }
    private void drawOutcome(GuiGraphics g,int x,int y,int w){
        JsonObject r=round();if(str(r,"id").isBlank()){centered(g,tr("no_round"),x+w/2,y,w-24,CasinoTheme.GOLD,true);return;}
        if(animating()){centered(g,tr("settling"),x+w/2,y,w-20,CasinoTheme.MUTED,false);return;}
        if(activeHand()){centered(g,tr("your_turn"),x+w/2,y,w-20,CasinoTheme.LIGHT,false);return;}
        long payout=num(r,"payout"),stake=num(r,"stake");Component text=payout>stake?tr("won",money(payout-stake)):payout==stake?tr("push"):tr("lost",money(stake));
        centered(g,text,x+w/2,y,w-20,payout>=stake?CasinoTheme.GOLD:0xFFD6A49B,true);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0x770B0D0C);CasinoTheme.frame(g,px,py,pw,ph);
        if(!compact()){CasinoTheme.crown(g,px+35,py+26,2);g.drawString(font,CasinoTheme.fit(font,Component.literal(str(data,"name")),pw-275,true),px+63,py+16,CasinoTheme.LIGHT,false);g.drawString(font,CasinoTheme.fit(font,tr(game()),pw-280,false),px+63,py+31,CasinoTheme.MUTED,false);}
        else g.drawString(font,CasinoTheme.fit(font,Component.literal(str(data,"name")),pw-109,true),px+17,py+16,CasinoTheme.LIGHT,false);
        g.enableScissor(px+12,bodyTop,px+pw-12,bodyBottom);g.pose().pushPose();g.pose().translate(0,-scroll,0);for(var paint:paintings)paint.accept(g);g.pose().popPose();g.disableScissor();
        int max=maximumScroll();if(max>0){int track=bodyBottom-bodyTop,thumb=Math.max(12,track*track/Math.max(1,contentHeight)),top=bodyTop+(track-thumb)*scroll/max;g.fill(px+pw-11,bodyTop,px+pw-9,bodyBottom,0xFF664745);g.fill(px+pw-12,top,px+pw-9,top+thumb,CasinoTheme.GOLD);}
        g.fill(px+17,bodyBottom+5,px+pw-17,bodyBottom+6,0xFF795E3F);
        Component status=waiting?tr("waiting"):!notice.isBlank()?Component.translatable(notice):tr("virtual_only");g.drawString(font,CasinoTheme.fit(font,status,pw-122,false),px+17,py+ph-23,error?0xFFF1AAA0:CasinoTheme.MUTED,false);
        super.render(g,mouseX,mouseY,partial);
        if(stakeInput!=null){for(var entry:bodyWidgets)if(entry.widget() instanceof CasinoButton b&&List.of(tr("spin").getString(),tr("roulette_spin").getString(),tr("deal").getString()).contains(b.getMessage().getString()))b.active=canBet();}
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){if(mouseX>=px&&mouseX<px+pw&&mouseY>=bodyTop&&mouseY<bodyBottom){remember();scroll=Math.clamp(scroll-(int)(vertical*29),0,maximumScroll());positionWidgets();return true;}return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);}
    private final class CasinoButton extends AbstractButton{
        private final Runnable press;private final boolean selected;
        CasinoButton(int x,int y,int w,int h,Component text,Runnable press,boolean selected){super(x,y,w,h,text);this.press=press;this.selected=selected;setTooltip(Tooltip.create(text));}
        @Override public void onPress(){if(active)press.run();}
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();boolean hover=active&&isHoveredOrFocused();
            g.fill(x,y,x+w,y+h,hover?0xFFFFDA8D:selected?CasinoTheme.GOLD:0xFF8D6D42);
            g.fill(x+1,y+1,x+w-1,y+h-1,!active?0xFF3C3435:selected?0xFF356048:hover?0xFF613744:CasinoTheme.VELVET);g.fill(x+2,y+2,x+w-2,y+3,!active?0xFF524346:selected?0xFF6D8658:0xFF845365);
            if(selected&&active){g.fill(x+4,y+h-3,x+w-4,y+h-2,0xFF8C9A63);CasinoTheme.diamond(g,x+8,y+h/2,1,CasinoTheme.GOLD);CasinoTheme.diamond(g,x+w-9,y+h/2,1,CasinoTheme.GOLD);}
            Component fitted=CasinoTheme.fit(font,getMessage(),w-(w<45?4:selected?20:10),true);g.drawString(font,fitted,x+(w-font.width(fitted))/2,y+(h-8)/2,active?CasinoTheme.LIGHT:0xFF9D9181,false);
        }
    }
}
