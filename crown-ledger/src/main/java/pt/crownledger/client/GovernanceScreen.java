package pt.crownledger.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import static pt.crownledger.client.TreasuryArt.*;

/** Public remote ballot; spending controls are enabled only by a fresh server-validated chest session. */
public final class GovernanceScreen extends Screen {
    private JsonObject state;
    private String tab="vote",draft="";
    private EditBox question;
    private int x,y,w,h,top,page,selected;
    private boolean general,waiting;
    private long sentAt;
    public GovernanceScreen(JsonObject state){super(Component.literal("Realm Treasury"));this.state=state;}
    private static Component t(String key,Object...args){return GovernanceClient.tr(key,args);}
    private static boolean flag(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()&&o.get(key).getAsBoolean();}
    private static long n(JsonObject o,String key){return o.has(key)?o.get(key).getAsLong():0;}
    private JsonArray list(String key){return state.has(key)?state.getAsJsonArray(key):new JsonArray();}
    public void accept(JsonObject data){boolean focused=question!=null&&question.isFocused();int cursor=question==null?0:question.getCursorPosition();if(question!=null)draft=question.getValue();state=data;waiting=false;if(!flag(state,"canPropose")&&tab.equals("propose"))tab="vote";rebuildWidgets();if(focused&&question!=null){setFocused(question);question.setCursorPosition(Math.min(cursor,question.getValue().length()));}}
    @Override protected void init(){
        if(question!=null)draft=question.getValue();question=null;
        w=Math.min(650,width-16);h=Math.min(400,height-16);x=(width-w)/2;y=(height-h)/2;top=y+67;
        String[] tabs=flag(state,"canPropose")?new String[]{"vote","benefits","history","propose"}:new String[]{"vote","benefits","history"};
        int tw=(w-24)/tabs.length;
        for(int i=0;i<tabs.length;i++){String key=tabs[i];button(x+10+i*(tw+1),y+35,tw-3,"tab."+key,()->{tab=key;page=0;rebuildWidgets();}).active=!tab.equals(key)&&!waiting;}
        button(x+10,y+h-31,82,"close",this::onClose);
        if(tab.equals("vote")&&state.has("poll")){
            var poll=state.getAsJsonObject("poll");int bw=Math.min(150,(w-124)/2);
            var yes=button(x+w-2*bw-18,y+h-31,bw,"vote.yes",()->vote(true));
            var no=button(x+w-bw-10,y+h-31,bw,"vote.no",()->vote(false));
            yes.active=no.active=flag(poll,"canVote")&&!waiting;
            if(poll.has("ownVote")){if(flag(poll,"ownVote"))yes.setMessage(t("vote.registered_yes"));else no.setMessage(t("vote.registered_no"));}
            if(flag(state,"admin")&&w>=560)button(x+100,y+h-31,Math.min(142,w-2*bw-126),"cancel_vote",()->{var payload=new JsonObject();payload.add("id",poll.get("id"));send("cancel",payload);}).active=!waiting;
        }else if(tab.equals("propose")){
            int fw=Math.min(170,(w-30)/2);
            button(x+12,top,fw,general?"type.general":"type.benefit",()->{general=!general;rebuildWidgets();});
            if(general){
                question=new EditBox(font,x+12,top+46,w-24,20,t("question"));question.setMaxLength(120);question.setValue(draft);addRenderableWidget(question);
            }else if(!list("offers").isEmpty()){
                selected=Math.floorMod(selected,list("offers").size());
                button(x+w-88,top,36,"previous",()->{selected--;rebuildWidgets();});button(x+w-48,top,36,"next",()->{selected++;rebuildWidgets();});
            }
            var announce=button(x+w-160,y+h-31,148,"announce",this::propose);
            announce.active=!waiting&&!state.has("poll")&&!flag(state,"refundPending")&&GovernanceClient.time()>=n(state,"nextProposalAt")&&(general||!list("offers").isEmpty());
        }else if(tab.equals("history")||tab.equals("benefits")){
            int total=tab.equals("history")?list("history").size():list("active").size();int rows=Math.max(1,(h-112)/60);
            var prev=button(x+w-166,y+h-31,74,"previous",()->{page--;rebuildWidgets();});prev.active=!waiting&&page>0;
            var next=button(x+w-88,y+h-31,78,"next",()->{page++;rebuildWidgets();});next.active=!waiting&&(page+1)*rows<total;
        }
    }
    private TreasuryArt.Button button(int bx,int by,int bw,String key,Runnable action){return addRenderableWidget(new TreasuryArt.Button(bx,by,bw,t(key),action));}
    private void send(String action,JsonObject payload){if(waiting)return;waiting=true;sentAt=System.nanoTime();GovernanceClient.request(action,payload);rebuildWidgets();}
    private void vote(boolean yes){var p=new JsonObject();p.addProperty("id",state.getAsJsonObject("poll").get("id").getAsString());p.addProperty("yes",yes);send("vote",p);}
    private void propose(){
        var request=new JsonObject();
        if(general){draft=question==null?draft:question.getValue();request.addProperty("question",draft);}
        else if(!list("offers").isEmpty()){
            var o=list("offers").get(selected).getAsJsonObject();request.addProperty("benefit",o.get("benefit").getAsString());
            request.add("cost",o.get("cost"));request.add("duration",o.get("durationMillis"));request.add("strength",o.get("strength"));request.add("currency",o.get("currency"));
        }else return;
        send("propose",request);
    }
    private String money(long value,String currency){return currency.equals("CHRONICLES")?java.math.BigDecimal.valueOf(value,2).toPlainString()+" €":t("emeralds",value).getString();}
    private void line(GuiGraphics g,Component text,int px,int py,int width,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),width),px,py,color,false);}
    private void wrap(GuiGraphics g,Component text,int px,int py,int width,int lines,int color){var words=font.split(text,width);for(int i=0;i<Math.min(lines,words.size());i++)g.drawString(font,words.get(i),px,py+i*11,color,false);}
    private Component benefit(JsonObject o){return t("benefit."+o.get("benefit").getAsString().toLowerCase(java.util.Locale.ROOT));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xB509100C);g.fill(x+4,y+5,x+w+4,y+h+5,0x55180E07);
        frame(g,x,y,w,h,PAPER,GOLD);frame(g,x+4,y+4,w-8,25,0xFF2D3C31,0xFF4C6145);seal(g,x+20,y+16,9);
        line(g,title,x+38,y+12,w-66,0xFFF5D89A);
        g.fill(x+8,y+60,x+w-8,y+61,0xFFBDA16B);
        switch(tab){case "vote"->renderVote(g);case "propose"->renderPropose(g);case "benefits"->renderBenefits(g);default->renderHistory(g);}
        String message=state.has("message")?state.get("message").getAsString():"";
        if(!message.isBlank())line(g,Component.translatable(message),x+12,y+h-10,w-24,0xFF8A4134);
        super.render(g,mx,my,partial);
    }
    private void renderVote(GuiGraphics g){
        if(!state.has("poll")){
            seal(g,x+w/2,top+32,18);wrap(g,t("vote.empty"),x+20,top+62,w-40,2,MUTED);return;
        }
        var p=state.getAsJsonObject("poll");
        frame(g,x+10,top,w-20,h-108,0xFFF2E6C9,0xFFC6AB74);
        wrap(g,Component.literal(GovernanceClient.question(p)),x+22,top+11,w-44,3,INK);
        line(g,t("vote.author",p.get("author").getAsString()),x+22,top+48,w-44,MUTED);
        if(p.has("offer")){
            var o=p.getAsJsonObject("offer");line(g,t("vote.cost",money(n(o,"cost"),o.get("currency").getAsString()),GovernanceClient.duration(n(o,"durationMillis"))),x+22,top+64,w-44,INK);
        }else line(g,t("vote.consultation"),x+22,top+64,w-44,INK);
        line(g,t("vote.participation",n(p,"participation"),n(p,"electorate"),n(p,"minimumVotes")),x+22,top+80,w-44,MUTED);
        line(g,t("vote.remaining",GovernanceClient.duration(n(p,"deadline")-GovernanceClient.time())),x+22,top+95,w-44,INK);
        if(h>=300){
            wrap(g,t("vote.secret"),x+22,top+122,w-44,2,MUTED);
            if(p.has("ownVote"))line(g,t(flag(p,"ownVote")?"vote.registered_yes":"vote.registered_no"),x+22,top+158,w-44,0xFF355C40);
            else if(!flag(p,"canVote"))wrap(g,t("vote.ineligible"),x+22,top+158,w-44,2,MUTED);
        }
    }
    private void renderPropose(GuiGraphics g){
        if(general){
            line(g,t("question"),x+12,top+30,w-24,INK);
            if(h>=300)wrap(g,t("general.hint"),x+12,top+82,w-24,3,MUTED);
        }else if(!list("offers").isEmpty()){
            var o=list("offers").get(selected).getAsJsonObject();
            line(g,benefit(o),x+12,top+32,w-24,INK);
            if(h>=300)wrap(g,t("description."+o.get("benefit").getAsString().toLowerCase(java.util.Locale.ROOT),n(o,"strength")),x+12,top+54,w-24,2,MUTED);
            int extra=h>=300?34:0;
            line(g,t("vote.cost",money(n(o,"cost"),o.get("currency").getAsString()),GovernanceClient.duration(n(o,"durationMillis"))),x+12,top+55+extra,w-24,INK);
            line(g,t("available",money(n(state,"balance"),state.get("currency").getAsString())),x+12,top+72+extra,w-24,MUTED);
        }
        if(h>=300){
            wrap(g,t("propose.rules",GovernanceClient.duration(n(state,"durationSeconds")*1000),n(state,"quorumPercent")),x+12,y+h-115,w-24,3,MUTED);
            if(state.has("poll")||flag(state,"refundPending"))line(g,t("error.busy"),x+12,y+h-62,w-24,0xFF8A4134);
            else if(n(state,"nextProposalAt")>GovernanceClient.time())line(g,t("cooldown",GovernanceClient.duration(n(state,"nextProposalAt")-GovernanceClient.time())),x+12,y+h-62,w-24,MUTED);
        }
    }
    private void renderBenefits(GuiGraphics g){
        var entries=list("active");int rows=Math.max(1,(h-112)/60);
        if(entries.isEmpty()){wrap(g,t("benefits.empty"),x+14,top+20,w-28,3,MUTED);return;}
        for(int i=0;i<rows&&page*rows+i<entries.size();i++){
            var o=entries.get(page*rows+i).getAsJsonObject();int ry=top+i*60;
            frame(g,x+10,ry,w-20,53,0xFFF1E4C4,0xFFC5AB76);seal(g,x+28,ry+25,11);
            line(g,benefit(o),x+49,ry+10,w-64,INK);line(g,t("expires",GovernanceClient.duration(n(o,"expiresAt")-GovernanceClient.time())),x+49,ry+29,w-64,MUTED);
        }
    }
    private void renderHistory(GuiGraphics g){
        var entries=list("history");int rows=Math.max(1,(h-112)/60);
        if(entries.isEmpty()){wrap(g,t("history.empty"),x+14,top+20,w-28,3,MUTED);return;}
        for(int i=0;i<rows&&page*rows+i<entries.size();i++){
            var o=entries.get(page*rows+i).getAsJsonObject();int ry=top+i*60;
            frame(g,x+10,ry,w-20,53,0xFFF1E4C4,0xFFC5AB76);
            line(g,Component.literal(GovernanceClient.question(o)),x+20,ry+8,w-40,INK);
            line(g,t("outcome."+o.get("outcome").getAsString().toLowerCase(java.util.Locale.ROOT)),x+20,ry+23,w-40,0xFF426044);
            line(g,t("result.tally",n(o,"yes"),n(o,"no"),n(o,"electorate")),x+20,ry+36,w-40,MUTED);
        }
    }
    @Override public void tick(){if(waiting&&System.nanoTime()-sentAt>6_000_000_000L){waiting=false;rebuildWidgets();}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){GovernanceClient.dismiss();super.onClose();}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
}
