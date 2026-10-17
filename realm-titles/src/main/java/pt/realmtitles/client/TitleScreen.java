package pt.realmtitles.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
import net.minecraft.network.chat.Component;
import pt.realmtitles.network.TitleNetwork;

/** A public register and the governor's working ledger, opened only by a physical seal desk. */
public final class TitleScreen extends Screen {
    private enum Tab { REGISTRY, HONOURS, CATALOG }
    private record BodyWidget(AbstractWidget widget,int y) {}
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",Locale.ROOT).withZone(ZoneOffset.UTC);
    private JsonObject data;
    private BiConsumer<String,JsonObject> requestSender=TitleNetwork::request;
    private Tab tab=Tab.REGISTRY;
    private final List<Consumer<GuiGraphics>> paintings=new ArrayList<>();
    private final List<BodyWidget> bodyWidgets=new ArrayList<>();
    private String selectedTitle="",selectedTitleName="",selectedTitleDescription="",editingTitle="",draftName="",draftDescription="",dSearch="",pSearch="",days="30",notice="",pendingOperation="";
    private boolean waiting,temporary,definitionPicker,citizenPicker,deleteConfirm,error,newMode;
    private int px,py,pw,ph,bodyTop,bodyBottom,scroll,contentHeight,ticks,waitingTicks;
    private EditBox nameInput,descriptionInput,daysInput,definitionSearch,citizenSearch;

    private TitleScreen(JsonObject snapshot) {
        super(tr("title"));data=snapshot;readView();
        notice=string(data,"error");error=!notice.isEmpty();
        if(!error)notice=string(data,"message");
    }
    public static void receive(String json) {
        if(json==null||json.length()>65536)return;
        final JsonObject snapshot;
        try {snapshot=JsonParser.parseString(json).getAsJsonObject();}
        catch(RuntimeException malformed) {return;}
        Minecraft mc=Minecraft.getInstance();
        mc.execute(()->{
            if(snapshot.size()==0||flag(snapshot,"closed")) {
                if(mc.screen instanceof TitleScreen)mc.setScreen(null);
                if(mc.player!=null&&!string(snapshot,"error").isEmpty())mc.player.displayClientMessage(Component.translatable(string(snapshot,"error")),false);
                return;
            }
            if(mc.player==null||mc.level==null)return;
            if(mc.screen instanceof TitleScreen current)current.accept(snapshot);
            else mc.setScreen(new TitleScreen(snapshot));
        });
    }
    private void accept(JsonObject snapshot) {
        rememberFields();boolean wasManager=canManage();data=snapshot;waiting=false;waitingTicks=0;
        notice=string(data,"error");error=!notice.isEmpty();
        if(!error)notice=string(data,"message");
        readView();
        if(!error&&"create".equals(pendingOperation))for(var entry:array("definitions")) {
            if(entry.isJsonObject()&&draftName.equals(string(entry.getAsJsonObject(),"name"))) {
                selectedTitle=string(entry.getAsJsonObject(),"id");newMode=false;readSelectedTitle();
            }
        }
        pendingOperation="";
        if(!canManage()) {tab=Tab.REGISTRY;deleteConfirm=false;definitionPicker=false;}
        if(wasManager!=canManage())scroll=0;
        if(!selectedTitle.isEmpty()&&definition(selectedTitle)==null&&"gui.realmtitles.deleted".equals(notice))newDefinition();
        rebuildWidgets();
    }
    private void readView() {
        dSearch=string(data,"dSearch");pSearch=string(data,"pSearch");
        if(!newMode&&selectedTitle.isEmpty()&&!array("definitions").isEmpty()&&array("definitions").get(0).isJsonObject())selectedTitle=string(array("definitions").get(0).getAsJsonObject(),"id");
        readSelectedTitle();
    }
    private void readSelectedTitle() {
        JsonObject selected=definition(selectedTitle);
        if(selected!=null){selectedTitleName=string(selected,"name");selectedTitleDescription=string(selected,"description");}
    }
    private static Component tr(String key,Object...args) {return Component.translatable("gui.realmtitles."+key,args);}
    private static String string(JsonObject object,String key) {
        try {return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():"";}
        catch(RuntimeException malformed) {return "";}
    }
    private static long number(JsonObject object,String key) {
        try {return object.has(key)?object.get(key).getAsLong():0;}
        catch(RuntimeException malformed) {return 0;}
    }
    private static boolean flag(JsonObject object,String key) {
        try {return object.has(key)&&object.get(key).getAsBoolean();}
        catch(RuntimeException malformed) {return false;}
    }
    private JsonArray array(String key) {return data.has(key)&&data.get(key).isJsonArray()?data.getAsJsonArray(key):new JsonArray();}
    private boolean canManage() {return flag(data,"canManage");}
    private boolean wide() {return pw>=600;}
    private boolean compact() {return ph<300;}
    private JsonObject definition(String id) {
        for(var entry:array("definitions"))if(entry.isJsonObject()&&id.equals(string(entry.getAsJsonObject(),"id")))return entry.getAsJsonObject();
        return null;
    }
    private String titleName() {
        return selectedTitleName.isEmpty()?tr("choose_title").getString():selectedTitleName;
    }
    private boolean selectedEligible() {
        for(var entry:array("citizens"))if(entry.isJsonObject()&&string(data,"selectedPlayer").equals(string(entry.getAsJsonObject(),"id")))return flag(entry.getAsJsonObject(),"eligible");
        return true; // The server checks the selected public profile again before every grant.
    }

    @Override protected void init() {
        paintings.clear();bodyWidgets.clear();
        nameInput=null;descriptionInput=null;daysInput=null;definitionSearch=null;citizenSearch=null;
        pw=Math.min(764,width-12);ph=Math.min(424,height-12);px=(width-pw)/2;py=(height-ph)/2;
        int tabsY=py+(compact()?34:50);
        bodyTop=tabsY+27;bodyBottom=py+ph-(compact()?40:47);
        int tabCount=canManage()?3:1,tabWidth=Math.min(152,(pw-32-(tabCount-1)*5)/tabCount);
        for(int i=0;i<tabCount;i++) {
            Tab target=Tab.values()[i];
            addRenderableWidget(new SealButton(px+16+i*(tabWidth+5),tabsY,tabWidth,20,tr("tab_"+target.name().toLowerCase(Locale.ROOT)),()->{
                rememberFields();tab=target;scroll=0;deleteConfirm=false;rebuildWidgets();
            },target==tab));
        }
        int inner=pw-32,left=px+16,start=bodyTop;
        if(tab==Tab.CATALOG) {
            if(wide()) {
                int listWidth=230,editorX=left+listWidth+12,editorWidth=inner-listWidth-12;
                contentHeight=Math.max(catalog(left,start,listWidth,true),editor(editorX,start,editorWidth))-start;
            } else {
                int end=start;
                end=fold(left,end,inner,"catalogue",titleName(),definitionPicker,()->{rememberFields();definitionPicker=!definitionPicker;scroll=0;rebuildWidgets();});
                if(definitionPicker)end=catalog(left,end+5,inner,true)+10;
                contentHeight=editor(left,end+7,inner)-start;
            }
        } else {
            if(wide()) {
                int listWidth=224,detailX=left+listWidth+12,detailWidth=inner-listWidth-12;
                contentHeight=Math.max(citizens(left,start,listWidth),character(detailX,start,detailWidth))-start;
            } else {
                int end=fold(left,start,inner,"citizens",string(data,"playerName"),citizenPicker,()->{rememberFields();citizenPicker=!citizenPicker;scroll=0;rebuildWidgets();});
                if(citizenPicker)end=citizens(left,end+5,inner)+9;
                contentHeight=character(left,end+8,inner)-start;
            }
        }
        scroll=Math.clamp(scroll,0,maximumScroll());positionWidgets();
        addRenderableWidget(new SealButton(px+pw-88,py+ph-28,72,18,tr("close"),this::onClose,false));
    }
    private void rememberFields() {
        if(nameInput!=null)draftName=nameInput.getValue();
        if(descriptionInput!=null)draftDescription=descriptionInput.getValue();
        if(daysInput!=null)days=daysInput.getValue();
        if(definitionSearch!=null)dSearch=definitionSearch.getValue();
        if(citizenSearch!=null)pSearch=citizenSearch.getValue();
    }
    private int fold(int x,int y,int w,String key,String value,boolean open,Runnable action) {
        paper(x,y,w,43);label(tr(key),x+10,y+7,w-47,TitleTheme.MUTED,false);
        bodyButton(x+7,y+20,w-14,18,Component.literal((open?"− ":"+ ")+value),action,false).setTooltip(Tooltip.create(tr(open?"collapse":"expand")));
        return y+43;
    }
    private int citizens(int x,int y,int w) {
        int top=y;heading(tr("citizens"),x,y,w,tr("citizens_count",number(data,"citizenTotal")));y+=34;
        citizenSearch=input(x+8,y,w-61,pSearch,64,tr("search_citizens"),null);
        bodyButton(x+w-47,y,39,19,tr("find"),()->search(false),false);y+=28;
        var entries=array("citizens");
        if(entries.isEmpty()) {paper(x+3,y,w-6,48);paragraph(tr("no_citizens"),x+11,y+7,w-22,TitleTheme.MUTED);y+=53;}
        for(var entry:entries) {
            if(!entry.isJsonObject())continue;
            JsonObject person=entry.getAsJsonObject();String id=string(person,"id"),name=string(person,"name");
            boolean chosen=id.equals(string(data,"selectedPlayer"));
            paper(x+3,y,w-6,34);if(chosen)stripe(x+3,y,3,34,TitleTheme.GREEN);
            label(Component.literal(name),x+12,y+6,w-54,TitleTheme.INK,true);
            label(tr(!flag(person,"eligible")?"no_profile":flag(person,"online")?"online":"offline"),x+12,y+20,w-55,flag(person,"online")?0xFF4E7048:TitleTheme.MUTED,false);
            bodyButton(x+w-37,y+8,28,19,Component.literal(chosen?"◆":"›"),()->{
                rememberFields();JsonObject args=new JsonObject();args.addProperty("selectedPlayer",id);request("list",args);
                if(!wide()) {citizenPicker=false;scroll=0;}
            },chosen);y+=39;
        }
        y=pager(x,y+3,w,false);return Math.max(y+4,top+100);
    }
    private int catalog(int x,int y,int w,boolean edit) {
        heading(tr("catalogue"),x,y,w,tr("definitions_count",number(data,"definitionTotal")));y+=34;
        definitionSearch=input(x+8,y,w-61,dSearch,48,tr("search_titles"),null);
        bodyButton(x+w-47,y,39,19,tr("find"),()->search(true),false);y+=28;
        var entries=array("definitions");
        if(entries.isEmpty()) {paper(x+3,y,w-6,48);paragraph(tr("no_definitions"),x+11,y+7,w-22,TitleTheme.MUTED);y+=53;}
        for(var entry:entries) {
            if(!entry.isJsonObject())continue;
            JsonObject definition=entry.getAsJsonObject();String id=string(definition,"id"),name=string(definition,"name");
            bodyButton(x+3,y,w-6,27,Component.literal(name),()->{
                rememberFields();selectedTitle=id;newMode=false;readSelectedTitle();deleteConfirm=false;definitionPicker=false;
                if(edit){editingTitle=id;draftName=name;draftDescription=string(definition,"description");}
                scroll=0;rebuildWidgets();
            },id.equals(selectedTitle)).setTooltip(Tooltip.create(Component.literal(string(definition,"description"))));y+=32;
        }
        return pager(x,y+3,w,true)+4;
    }
    private int pager(int x,int y,int w,boolean definitions) {
        String pageKey=definitions?"dPage":"pPage",totalKey=definitions?"definitionTotal":"citizenTotal";
        int page=(int)number(data,pageKey),total=(int)number(data,totalKey),pages=Math.max(1,(total+7)/8);
        bodyButton(x+5,y,28,18,Component.literal("‹"),()->changePage(definitions,page-1),false).active=!waiting&&page>0;
        label(tr("page",page+1,pages),x+38,y+6,w-76,TitleTheme.LABEL,false);
        bodyButton(x+w-33,y,28,18,Component.literal("›"),()->changePage(definitions,page+1),false).active=!waiting&&page+1<pages;
        return y+23;
    }
    private void search(boolean definitions) {
        rememberFields();JsonObject args=new JsonObject();args.addProperty(definitions?"dPage":"pPage",0);request("list",args);
    }
    private void changePage(boolean definitions,int page) {
        rememberFields();JsonObject args=new JsonObject();args.addProperty(definitions?"dPage":"pPage",page);request("list",args);
    }
    private int character(int x,int y,int w) {
        int headerY=y;
        paper(x,y,w,58);stripe(x,y,w,3,TitleTheme.GOLD);
        paintings.add(g->TitleTheme.seal(g,x+w-26,headerY+25-scroll,1,false));
        label(tr("public_record"),x+12,y+10,w-59,TitleTheme.MUTED,false);
        label(Component.literal(string(data,"playerName")),x+12,y+26,w-59,TitleTheme.INK,true);
        label(tr("active_count",array("grants").size()),x+12,y+42,w-58,TitleTheme.MUTED,false);y+=67;
        if(tab==Tab.HONOURS&&canManage())y=grantForm(x,y,w)+10;
        heading(tr("active_titles"),x,y,w,tr(tab==Tab.HONOURS?"revoke_hint":"public_hint"));y+=35;
        var entries=array("grants");
        if(entries.isEmpty()) {
            int emptyY=y;paper(x,y,w,82);paintings.add(g->TitleTheme.seal(g,x+27,emptyY+29-scroll,1,false));
            paragraph(tr("no_grants"),x+51,y+20,w-65,TitleTheme.MUTED);return y+92;
        }
        for(var entry:entries) {
            if(!entry.isJsonObject())continue;
            JsonObject grant=entry.getAsJsonObject();String id=string(grant,"id");
            int lines=font.split(Component.literal(string(grant,"description")),w-28).size();
            int height=49+Math.max(1,lines)*11+(tab==Tab.HONOURS&&canManage()?25:0);
            paper(x,y,w,height);stripe(x,y,3,height,0xFF9A7D43);
            label(Component.literal(string(grant,"name")),x+12,y+10,w-24,TitleTheme.INK,true);
            int lineEnd=paragraph(Component.literal(string(grant,"description")),x+12,y+25,w-24,TitleTheme.MUTED);
            long expires=number(grant,"expiresAt");
            label(expires==0?tr("permanent_honour"):tr("expires",date(expires)),x+12,lineEnd+6,w-24,TitleTheme.GREEN,false);
            if(tab==Tab.HONOURS&&canManage())bodyButton(x+w-102,y+height-25,90,18,tr("revoke"),()->{
                JsonObject args=new JsonObject();args.addProperty("id",id);request("revoke",args);
            },false).active=!waiting;
            y+=height+9;
        }
        return y+3;
    }
    private int grantForm(int x,int y,int w) {
        int top=y;heading(tr("grant_title"),x,y,w,tr("grant_hint"));y+=35;
        label(tr("selected_title"),x+10,y,w-20,TitleTheme.LABEL,false);y+=13;
        bodyButton(x+7,y,w-14,23,Component.literal(titleName()+"  ▾"),()->{
            rememberFields();definitionPicker=!definitionPicker;rebuildWidgets();
        },true);y+=30;
        if(definitionPicker)y=catalog(x+4,y,w-8,false)+7;
        label(tr("duration"),x+10,y,w-20,TitleTheme.LABEL,false);y+=14;
        int half=(w-21)/2;
        bodyButton(x+7,y,half,20,tr("permanent"),()->{rememberFields();temporary=false;rebuildWidgets();},!temporary);
        bodyButton(x+14+half,y,half,20,tr("temporary"),()->{rememberFields();temporary=true;rebuildWidgets();},temporary);y+=27;
        if(temporary) {
            daysInput=input(x+8,y,48,days,3,tr("days"),value->value.matches("[0-9]{0,3}"));
            label(tr("days_range"),x+64,y+6,w-74,TitleTheme.LABEL,false);y+=27;
        }
        bodyButton(x+7,y,w-14,23,tr("grant"),()->{
            rememberFields();int duration=0;
            if(temporary)try {duration=Integer.parseInt(days);if(duration<1||duration>365)throw new NumberFormatException();}
            catch(NumberFormatException invalid){notice="gui.realmtitles.days_invalid";error=true;return;}
            JsonObject args=new JsonObject();args.addProperty("selectedPlayer",string(data,"selectedPlayer"));
            args.addProperty("titleId",selectedTitle);args.addProperty("days",duration);request("grant",args);
        },false).active=!waiting&&!selectedTitle.isEmpty()&&selectedEligible();y+=30;
        if(!selectedEligible())y=paragraph(tr("profile_required"),x+10,y,w-20,TitleTheme.WARNING)+8;
        stripe(x+4,y,w-8,1,0xFF8C7548);return Math.max(y+5,top+100);
    }
    private int editor(int x,int y,int w) {
        int top=y;boolean existing=!selectedTitle.isEmpty();
        heading(tr(existing?"edit_title":"new_title"),x,y,w,tr("definition_hint"));y+=36;
        if(existing&&!selectedTitle.equals(editingTitle)) {editingTitle=selectedTitle;draftName=selectedTitleName;draftDescription=selectedTitleDescription;}
        label(tr("name"),x+9,y,w-18,TitleTheme.LABEL,false);y+=14;
        nameInput=input(x+8,y,w-16,draftName,48,tr("name"),null);y+=29;
        label(tr("description"),x+9,y,w-18,TitleTheme.LABEL,false);y+=14;
        descriptionInput=input(x+8,y,w-16,draftDescription,256,tr("description"),null);y+=29;
        label(tr("preview"),x+10,y,w-20,TitleTheme.LABEL,false);y+=14;
        int previewHeight=47+font.split(Component.literal("W".repeat(256)),w-30).size()*11;
        paper(x+3,y,w-6,previewHeight);stripe(x+3,y,3,previewHeight,TitleTheme.GREEN);
        int previewY=y;
        paintings.add(g->{
            String liveName=nameInput==null?draftName:nameInput.getValue();
            String liveDescription=descriptionInput==null?draftDescription:descriptionInput.getValue();
            g.drawString(font,TitleTheme.fit(font,Component.literal(liveName.isBlank()?tr("untitled").getString():liveName),w-30,true),x+15,previewY+10-scroll,TitleTheme.INK,false);
            int lineY=previewY+27-scroll;
            for(var line:font.split(Component.literal(liveDescription),w-30)){g.drawString(font,line,x+15,lineY,TitleTheme.MUTED,false);lineY+=11;}
        });y+=previewHeight+12;
        bodyButton(x+7,y,w-14,23,tr(existing?"save":"create"),()->{
            rememberFields();JsonObject args=new JsonObject();args.addProperty("name",draftName);args.addProperty("description",draftDescription);
            if(existing)args.addProperty("id",selectedTitle);request(existing?"edit":"create",args);
        },false).active=!waiting;y+=30;
        if(existing) {
            int half=(w-21)/2;
            bodyButton(x+7,y,half,20,tr("new"),()->{rememberFields();newDefinition();scroll=0;rebuildWidgets();},false).active=!waiting;
            bodyButton(x+14+half,y,half,20,tr(deleteConfirm?"confirm_delete":"delete"),()->{
                if(!deleteConfirm){deleteConfirm=true;rebuildWidgets();return;}
                JsonObject args=new JsonObject();args.addProperty("id",selectedTitle);request("delete",args);deleteConfirm=false;
            },false).active=!waiting;y+=28;
            if(deleteConfirm)y=paragraph(tr("delete_hint"),x+10,y,w-20,TitleTheme.WARNING)+6;
        }
        return Math.max(y+7,top+170);
    }
    private void newDefinition() {newMode=true;selectedTitle="";selectedTitleName="";selectedTitleDescription="";editingTitle="";draftName="";draftDescription="";deleteConfirm=false;}
    private static String date(long epoch) {
        try {return DATE.format(Instant.ofEpochMilli(epoch));}catch(RuntimeException invalid){return "—";}
    }
    private void heading(Component title,int x,int y,int w,Component hint) {
        paintings.add(g->{
            g.fill(x,y-scroll,x+w,y+29-scroll,TitleTheme.GREEN);
            g.fill(x,y-scroll,x+w,y+1-scroll,0xFF8E794B);
            g.drawString(font,TitleTheme.fit(font,title,w-18,true),x+9,y+5-scroll,TitleTheme.GOLD,false);
            g.drawString(font,TitleTheme.fit(font,hint,w-18,false),x+9,y+17-scroll,0xFFB5B799,false);
        });
    }
    private void paper(int x,int y,int w,int h) {paintings.add(g->TitleTheme.paper(g,x,y-scroll,w,h));}
    private void stripe(int x,int y,int w,int h,int color) {paintings.add(g->g.fill(x,y-scroll,x+w,y+h-scroll,color));}
    private void label(Component text,int x,int y,int width,int color,boolean bold) {
        paintings.add(g->g.drawString(font,TitleTheme.fit(font,text,width,bold),x,y-scroll,color,false));
    }
    private int paragraph(Component text,int x,int y,int width,int color) {
        var lines=font.split(text,Math.max(24,width));int lineY=y;
        for(var line:lines) {int current=lineY;paintings.add(g->g.drawString(font,line,x,current-scroll,color,false));lineY+=11;}
        return lineY;
    }
    private EditBox input(int x,int y,int w,String value,int maximum,Component label,java.util.function.Predicate<String> filter) {
        EditBox field=new EditBox(font,x,y,w,19,label);
        field.setMaxLength(maximum);if(filter!=null)field.setFilter(filter);field.setValue(value);
        field.setTextColor(TitleTheme.LIGHT);field.setTextColorUneditable(0xFFADAA8E);field.setEditable(!waiting);
        field.setTooltip(Tooltip.create(label));addWidget(field);bodyWidgets.add(new BodyWidget(field,y));return field;
    }
    private SealButton bodyButton(int x,int y,int w,int h,Component label,Runnable press,boolean selected) {
        SealButton button=new SealButton(x,y,w,h,label,press,selected);button.active=!waiting;
        addWidget(button);bodyWidgets.add(new BodyWidget(button,y));return button;
    }
    private int maximumScroll() {return Math.max(0,contentHeight-(bodyBottom-bodyTop)+5);}
    private void positionWidgets() {
        for(BodyWidget entry:bodyWidgets) {
            AbstractWidget widget=entry.widget();widget.setY(entry.y()-scroll);
            widget.visible=widget.getY()>=bodyTop&&widget.getY()+widget.getHeight()<=bodyBottom;
            if(!widget.visible)widget.setFocused(false);
        }
    }
    private void request(String operation,JsonObject arguments) {
        if(waiting)return;
        if(!"list".equals(operation)&&!"close".equals(operation)&&!canManage())return;
        rememberFields();JsonObject payload=new JsonObject();
        payload.addProperty("dSearch",dSearch);payload.addProperty("pSearch",pSearch);
        payload.addProperty("dPage",number(data,"dPage"));payload.addProperty("pPage",number(data,"pPage"));
        payload.addProperty("selectedPlayer",string(data,"selectedPlayer"));
        for(var entry:arguments.entrySet())payload.add(entry.getKey(),entry.getValue());
        waiting=true;waitingTicks=0;pendingOperation=operation;requestSender.accept(operation,payload);rebuildWidgets();
    }
    @Override public void tick() {
        super.tick();ticks++;
        if(waiting&&++waitingTicks>=200){waiting=false;notice="gui.realmtitles.wait_timeout";error=true;rebuildWidgets();}
        // Refreshes are server-authorized reads at the actual desk; a stale governor screen never grants authority.
        if(ticks%100==0&&!waiting&&!(getFocused() instanceof EditBox field&&field.isFocused()))request("list",new JsonObject());
    }
    @Override public void onClose() {
        requestSender.accept("close",new JsonObject());super.onClose();
    }
    @Override public void resize(Minecraft minecraft,int width,int height) {rememberFields();super.resize(minecraft,width,height);}
    @Override public boolean isPauseScreen() {return false;}
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial) {}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        g.fill(0,0,width,height,0xCF111B17);
        for(int i=-height;i<width;i+=64)for(int j=0;j<height;j+=8)g.fill(i+j/2,j,i+j/2+1,j+8,0x0C8F9D68);
        TitleTheme.frame(g,px,py,pw,ph);
        TitleTheme.seal(g,px+30,py+(compact()?17:23),1,true);
        g.drawString(font,TitleTheme.fit(font,getTitle(),pw-87,true),px+51,py+(compact()?13:13),TitleTheme.GOLD,false);
        if(!compact())g.drawString(font,TitleTheme.fit(font,tr(canManage()?"governor_subtitle":"public_subtitle"),pw-76,false),px+51,py+29,0xFFBABC9C,false);
        g.enableScissor(px+15,bodyTop,px+pw-14,bodyBottom);
        for(Consumer<GuiGraphics> paint:paintings)paint.accept(g);
        positionWidgets();for(BodyWidget entry:bodyWidgets)if(entry.widget().visible)entry.widget().render(g,mouseX,mouseY,partial);
        g.disableScissor();
        int max=maximumScroll();
        if(max>0) {
            int track=bodyBottom-bodyTop,thumb=Math.max(12,track*track/Math.max(1,contentHeight));
            int top=bodyTop+(track-thumb)*scroll/max;
            g.fill(px+pw-11,bodyTop,px+pw-9,bodyBottom,0xFF425341);
            g.fill(px+pw-12,top,px+pw-9,top+thumb,TitleTheme.GOLD);
        }
        g.fill(px+16,bodyBottom+5,px+pw-16,bodyBottom+6,0xFF8B794C);
        Component message=waiting?tr("waiting"):!notice.isBlank()?Component.translatable(notice):tr(canManage()?"manager":"read_only",string(data,"managerName"));
        int messageY=compact()?py+ph-24:py+ph-34;
        g.drawString(font,TitleTheme.fit(font,message,pw-119,false),px+17,messageY,error?0xFFF0A18B:0xFFCECCAC,false);
        if(!compact())g.drawString(font,TitleTheme.fit(font,tr("physical_hint"),pw-119,false),px+17,py+ph-20,0xFF8FA48B,false);
        super.render(g,mouseX,mouseY,partial);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical) {
        if(mouseX>=px&&mouseX<px+pw&&mouseY>=bodyTop&&mouseY<bodyBottom) {
            scroll=Math.clamp(scroll-(int)(vertical*28),0,maximumScroll());positionWidgets();return true;
        }
        return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        // Hidden fields must not keep keyboard focus while the ledger is scrolled.
        if(mouseY<bodyTop||mouseY>=bodyBottom)for(BodyWidget entry:bodyWidgets)entry.widget().setFocused(false);
        return super.mouseClicked(mouseX,mouseY,button);
    }
    private final class SealButton extends AbstractButton {
        private final Runnable press;private final boolean selected;
        SealButton(int x,int y,int width,int height,Component label,Runnable action,boolean selected) {
            super(x,y,width,height,label);this.press=action;this.selected=selected;setTooltip(Tooltip.create(label));
        }
        @Override public void onPress() {if(active)press.run();}
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {defaultButtonNarrationText(output);}
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float tick) {
            boolean hover=active&&isHoveredOrFocused();int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            g.fill(x,y,x+w,y+h,hover?0xFFF0D08B:selected?TitleTheme.GOLD:0xFF958052);
            g.fill(x+1,y+1,x+w-1,y+h-1,!active?0xFF455043:selected?0xFF486044:hover?0xFF3C5642:TitleTheme.GREEN);
            g.fill(x+2,y+2,x+w-2,y+3,selected?0xFF899262:0xFF4C6349);
            if(selected)g.fill(x+3,y+h-3,x+w-3,y+h-2,0xFFA18D51);
            Component fitted=TitleTheme.fit(font,getMessage(),w-10,true);
            g.drawString(font,fitted,x+(w-font.width(fitted))/2,y+(h-8)/2,active?TitleTheme.LIGHT:0xFF9B9B80,false);
        }
    }
}
