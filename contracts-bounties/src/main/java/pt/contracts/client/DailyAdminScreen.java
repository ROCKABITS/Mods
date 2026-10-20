package pt.contracts.client;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pt.contracts.daily.*;

/** Physical, freshly authorized governance; bounded forms never submit a balance or recipient amount. */
public final class DailyAdminScreen extends Screen {
    private static final Set<String> MONEY=Set.of("easyMin","easyMax","mediumMin","mediumMax","hardMin","hardMax","dailyCap","allBonus","reward");
    private static final Set<String> PERCENT=Set.of("taxBps","taxCapBps","bonusCapBps");
    private static final List<String> SETTINGS=List.of("easyMin","easyMax","mediumMin","mediumMax","hardMin","hardMax","dailyCap","allBonus","taxCapBps","bonusCapBps","reputationLimit","easyReputation","mediumReputation","hardReputation","resetMinute","easyCount","mediumCount","hardCount","emeraldFallback","progressNotices","enable.MINING","enable.COMBAT","enable.FARMING","enable.FISHING","enable.EXPLORATION","enable.DELIVERY");
    private static final List<String> MODEL=List.of("id","title","category","difficulty","target","quantity","reward","weight","validation","mods","dimensions","biomes","enabled");
    private final Screen parent;
    private final boolean admin;
    private JsonObject data,model,repair;
    private String tab,search="",message="";
    private final Map<String,String> edits=new LinkedHashMap<>();
    private final Map<String,EditBox> fields=new LinkedHashMap<>();
    private int x,y,w,h,page,rows,catalogScroll,logScroll;
    private boolean waiting;
    private long waitSince;
    private EditBox searchField;
    public DailyAdminScreen(Screen parent,JsonObject data,boolean admin){super(DailyTheme.tr(admin?"admin":"government"));this.parent=parent;this.data=data;this.admin=admin;tab=admin?"settings":"policy";}
    public void accept(JsonObject data){capture();this.data=data;waiting=false;message=DailyScreen.str(data,"message");if(message.endsWith("_saved"))edits.clear();rebuildWidgets();}
    private void capture(){fields.forEach((key,field)->edits.put(key,field.getValue()));if(searchField!=null)search=searchField.getValue();}
    private void switchTab(String tab){capture();this.tab=tab;model=null;page=0;edits.clear();fields.clear();searchField=null;message="";rebuildWidgets();}
    private List<String> specs(){if(tab.equals("repair"))return List.of("player","quest","progress","reason");if(model!=null)return MODEL;if(tab.equals("policy")){List<String> list=new ArrayList<>();list.add("taxBps");for(var c:DailyRules.Category.values())list.add("bonus."+c.name());return list;}return SETTINGS;}
    @Override protected void init(){
        capture();fields.clear();searchField=null;w=Math.min(680,width-16);h=Math.min(390,height-16);x=(width-w)/2;y=(height-h)/2;rows=Math.max(3,(h-117)/30);
        if(admin){int tw=(w-32)/4;String[] tabs={"settings","policy","catalog","audit"};for(int i=0;i<tabs.length;i++){String key=tabs[i];addRenderableWidget(DailyTheme.button(x+10+i*(tw+4),y+35,tw,20,DailyTheme.tr(key),()->switchTab(key)));}}
        if(tab.equals("catalog")&&model==null)initCatalog();else if(tab.equals("audit"))initAudit();else initForm();
        if(admin&&tab.equals("audit"))addRenderableWidget(DailyTheme.button(x+w/2-56,y+h-29,112,20,DailyTheme.tr("repair"),()->{repair=new JsonObject();repair.addProperty("player","");repair.addProperty("quest","");repair.addProperty("progress",0);repair.addProperty("reason","");switchTab("repair");}));
        int fy=y+h-29;
        addRenderableWidget(DailyTheme.button(x+12,fy,Math.min(100,(w-36)/3),20,DailyTheme.tr("back"),()->{capture();minecraft.setScreen(parent);DailyNetwork.request("refresh",new JsonObject());}));
        addRenderableWidget(DailyTheme.button(x+w-Math.min(130,(w-36)/3)-12,fy,Math.min(130,(w-36)/3),20,DailyTheme.tr(tab.equals("catalog")&&model==null?"new_template":tab.equals("audit")?"refresh":"save"),()->{
            if(tab.equals("catalog")&&model==null)newModel();else if(tab.equals("audit"))send("refresh",new JsonObject());else save();
        })).active=!waiting;
        if((!tab.equals("catalog")&&!tab.equals("audit"))||model!=null){int total=(specs().size()+rows-1)/rows;addRenderableWidget(DailyTheme.button(x+w/2-62,fy,28,20,Component.literal("‹"),()->{capture();page=Math.max(0,page-1);rebuildWidgets();}));addRenderableWidget(DailyTheme.button(x+w/2+34,fy,28,20,Component.literal("›"),()->{capture();page=Math.min(total-1,page+1);rebuildWidgets();}));}
    }
    private JsonObject source(){return tab.equals("repair")?repair:model!=null?model:data.getAsJsonObject("settings");}
    private String value(String key){
        if(edits.containsKey(key))return edits.get(key);JsonObject src=source();
        if(key.startsWith("enable.")){String category=key.substring(7);return Boolean.toString(DailyScreen.array(src,"enabled").asList().stream().anyMatch(v->v.getAsString().equals(category)));}
        if(key.startsWith("bonus."))return BigDecimal.valueOf(DailyScreen.number(src.getAsJsonObject("bonuses"),key.substring(6)),2).toPlainString();
        if(key.equals("mods")||key.equals("dimensions")||key.equals("biomes")){List<String> values=new ArrayList<>();DailyScreen.array(src,key).forEach(e->values.add(e.getAsString()));return String.join(",",values);}
        if(key.equals("resetMinute")){int minute=(int)DailyScreen.number(src,key);return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);}
        if(MONEY.contains(key)){long amount=DailyScreen.number(src,key);return key.equals("reward")&&amount<0?"":BigDecimal.valueOf(amount,2).toPlainString();}
        if(PERCENT.contains(key))return BigDecimal.valueOf(DailyScreen.number(src,key),2).toPlainString();
        return DailyScreen.str(src,key);
    }
    private boolean toggle(String key){return key.startsWith("enable.")||Set.of("emeraldFallback","progressNotices","enabled").contains(key);}
    private void initForm(){
        List<String> specs=specs();page=Math.clamp(page,0,Math.max(0,(specs.size()-1)/rows));
        for(int n=0;n<rows&&page*rows+n<specs.size();n++){
            String key=specs.get(page*rows+n);int top=y+69+n*30,left=x+w/2-10,fw=w/2-5;
            if(model!=null&&Set.of("category","difficulty","validation").contains(key)){
                String shown=key.equals("category")||key.equals("difficulty")?DailyTheme.tr(key+"."+value(key).toLowerCase(Locale.ROOT)).getString():value(key);
                var b=addRenderableWidget(DailyTheme.button(left,top,fw,20,Component.literal(shown),()->{
                    capture();List<String> options=key.equals("category")?Arrays.stream(DailyRules.Category.values()).map(Enum::name).toList():key.equals("difficulty")?List.of("EASY","MEDIUM","HARD"):value("category").equals("EXPLORATION")?List.of("NEW_BIOME","NEW_STRUCTURE"):List.of(switch(value("category")){case "MINING"->"NATURAL_BLOCK";case "COMBAT"->"NATURAL_KILL";case "FARMING"->"MATURE_CROP";case "FISHING"->"ROD_CATCH";default->"CONSUME_ITEMS";});
                    edits.put(key,options.get((options.indexOf(value(key))+1)%options.size()));
                    if(key.equals("category"))edits.put("validation",switch(value(key)){case "MINING"->"NATURAL_BLOCK";case "COMBAT"->"NATURAL_KILL";case "FARMING"->"MATURE_CROP";case "FISHING"->"ROD_CATCH";case "EXPLORATION"->"NEW_BIOME";default->"CONSUME_ITEMS";});rebuildWidgets();
                }));b.active=!waiting;
            }
            else if(toggle(key)){var b=addRenderableWidget(DailyTheme.button(left,top,fw,20,DailyTheme.tr(Boolean.parseBoolean(value(key))?"on":"off"),()->{capture();edits.put(key,Boolean.toString(!Boolean.parseBoolean(value(key))));rebuildWidgets();}));b.active=!waiting;}
            else{EditBox field=addRenderableWidget(new EditBox(font,left,top,fw,20,fieldLabel(key)));field.setMaxLength(key.equals("title")?120:key.equals("mods")||key.equals("dimensions")||key.equals("biomes")?512:180);field.setValue(value(key));field.setTextColor(DailyTheme.PALE);field.setEditable(!waiting);field.setTooltip(Tooltip.create(DailyTheme.tr("field_hint."+key)));fields.put(key,field);}
        }
    }
    private Component fieldLabel(String key){return key.startsWith("enable.")?DailyTheme.tr("category."+key.substring(7).toLowerCase(Locale.ROOT)):key.startsWith("bonus.")?DailyTheme.tr("category_bonus",DailyTheme.tr("category."+key.substring(6).toLowerCase(Locale.ROOT))):DailyTheme.tr("field."+key);}
    private void initCatalog(){
        int controls=y+64;searchField=addRenderableWidget(new EditBox(font,x+14,controls,w-150,20,DailyTheme.tr("search")));searchField.setMaxLength(128);searchField.setValue(search);searchField.setHint(DailyTheme.tr("search"));
        addRenderableWidget(DailyTheme.button(x+w-127,controls,112,20,DailyTheme.tr("search"),()->{capture();JsonObject input=new JsonObject();input.addProperty("search",search);input.addProperty("page",0);send("inspect",input);}));
        var catalog=DailyScreen.array(data,"catalog");int visible=Math.max(1,(h-146)/28);catalogScroll=Math.clamp(catalogScroll,0,Math.max(0,catalog.size()-visible));
        for(int i=0;i<visible&&i+catalogScroll<catalog.size();i++){JsonObject template=catalog.get(i+catalogScroll).getAsJsonObject();addRenderableWidget(DailyTheme.button(x+w-88,y+96+i*28,73,20,DailyTheme.tr("edit"),()->{model=template.deepCopy();edits.clear();fields.clear();page=0;rebuildWidgets();}));}
        int page=(int)DailyScreen.number(data,"catalogPage"),pages=Math.max(1,((int)DailyScreen.number(data,"catalogTotal")+pageSize()-1)/pageSize());
        addRenderableWidget(DailyTheme.button(x+w/2-62,y+h-29,28,20,Component.literal("‹"),()->catalogPage(Math.max(0,page-1)))).active=page>0;
        addRenderableWidget(DailyTheme.button(x+w/2+34,y+h-29,28,20,Component.literal("›"),()->catalogPage(Math.min(pages-1,page+1)))).active=page+1<pages;
    }
    private int pageSize(){return Math.max(1,(int)DailyScreen.number(data,"catalogPageSize"));}
    private void catalogPage(int page){capture();JsonObject input=new JsonObject();input.addProperty("search",search);input.addProperty("page",page);catalogScroll=0;send("inspect",input);}
    private void initAudit(){var errors=DailyScreen.array(data,"errors");int count=Math.max(1,(h-130)/30);for(int i=0;i<Math.min(errors.size(),count);i++){var error=errors.get(i).getAsJsonObject();addRenderableWidget(DailyTheme.button(x+w-105,y+75+i*30,90,20,DailyTheme.tr("retry"),()->{JsonObject input=new JsonObject();input.addProperty("player",DailyScreen.str(error,"player"));send("retry_player",input);}));}}
    private void newModel(){model=JsonParser.parseString("{\"id\":\"contracts:custom_quest\",\"title\":\"A royal request\",\"category\":\"DELIVERY\",\"difficulty\":\"EASY\",\"target\":\"minecraft:oak_log\",\"quantity\":64,\"reward\":-1,\"weight\":10,\"mods\":[],\"dimensions\":[],\"biomes\":[],\"enabled\":true,\"validation\":\"CONSUME_ITEMS\"}").getAsJsonObject();edits.clear();fields.clear();page=0;rebuildWidgets();}
    private void save(){
        try{capture();JsonObject input=new JsonObject();input.addProperty("revision",DailyScreen.number(data,"revision"));JsonObject edited=source().deepCopy();
            for(var entry:edits.entrySet()){String key=entry.getKey(),text=entry.getValue();
                if(key.startsWith("bonus.")){edited.getAsJsonObject("bonuses").addProperty(key.substring(6),new BigDecimal(text.replace(',','.')).movePointRight(2).intValueExact());}
                else if(key.startsWith("enable.")){JsonArray enabled=edited.getAsJsonArray("enabled");String category=key.substring(7);JsonArray next=new JsonArray();enabled.forEach(e->{if(!e.getAsString().equals(category))next.add(e);});if(Boolean.parseBoolean(text))next.add(category);edited.add("enabled",next);}
                else if(toggle(key))edited.addProperty(key,Boolean.parseBoolean(text));
                else if(Set.of("mods","dimensions","biomes").contains(key)){JsonArray list=new JsonArray();for(String value:text.split(","))if(!value.isBlank())list.add(value.strip());edited.add(key,list);}
                else if(key.equals("resetMinute")){var time=java.time.LocalTime.parse(text);if(time.getSecond()!=0||time.getNano()!=0)throw new IllegalArgumentException();edited.addProperty(key,time.getHour()*60+time.getMinute());}
                else if(MONEY.contains(key))edited.addProperty(key,key.equals("reward")&&text.isBlank()?-1:new BigDecimal(text.replace(',','.')).movePointRight(2).longValueExact());
                else if(PERCENT.contains(key))edited.addProperty(key,new BigDecimal(text.replace(',','.')).movePointRight(2).intValueExact());
                else if(tab.equals("repair")&&Set.of("player","quest","reason").contains(key)||model!=null&&Set.of("id","title","category","difficulty","target","validation").contains(key))edited.addProperty(key,text.strip());
                else edited.addProperty(key,Integer.parseInt(text));
            }
            if(tab.equals("repair"))send("repair_progress",edited);else if(model!=null){input.add("template",edited);send("template",input);}else if(tab.equals("policy")){input.addProperty("taxBps",DailyScreen.number(edited,"taxBps"));input.add("bonuses",edited.get("bonuses"));send("policy",input);}else{input.add("settings",edited);send("settings",input);}
        }catch(RuntimeException invalid){message="daily.contracts.invalid_request";}
    }
    private void send(String operation,JsonObject input){waiting=true;waitSince=System.nanoTime();DailyNetwork.request(operation,input);}
    @Override public void tick(){if(waiting&&System.nanoTime()-waitSince>5_000_000_000L){waiting=false;message="daily.contracts.retry_done";rebuildWidgets();}}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xCB111D17);DailyTheme.panel(g,x,y,w,h);g.drawString(font,title,x+15,y+15,DailyTheme.GOLD,false);
        Component hover=null;
        if(tab.equals("catalog")&&model==null){var catalog=DailyScreen.array(data,"catalog");int visible=Math.max(1,(h-146)/28);for(int i=0;i<visible&&i+catalogScroll<catalog.size();i++){var template=catalog.get(i+catalogScroll).getAsJsonObject();int top=y+94+i*28;g.fill(x+12,top,x+w-12,top+25,DailyTheme.PAPER);g.drawString(font,font.plainSubstrByWidth(DailyScreen.label(DailyScreen.str(template,"title")).getString(),w-116),x+19,top+4,DailyTheme.INK,false);g.drawString(font,font.plainSubstrByWidth(DailyScreen.str(template,"id"),w-116),x+19,top+15,DailyTheme.MUTED,false);}int pages=Math.max(1,((int)DailyScreen.number(data,"catalogTotal")+pageSize()-1)/pageSize());g.drawCenteredString(font,(DailyScreen.number(data,"catalogPage")+1)+" / "+pages,x+w/2,y+h-23,DailyTheme.PALE);}
        else if(tab.equals("audit")){
            JsonArray errors=DailyScreen.array(data,"errors"),audit=DailyScreen.array(data,"audit");int count=Math.max(1,(h-130)/30);
            if(errors.isEmpty()){int visible=Math.max(1,(h-113)/27);for(int i=0;i<visible&&i+logScroll<audit.size();i++){var entry=audit.get(i+logScroll).getAsJsonObject();int top=y+68+i*27;String at=java.time.Instant.ofEpochMilli(DailyScreen.number(entry,"at")).atOffset(java.time.ZoneOffset.UTC).toLocalDateTime().toString();g.drawString(font,font.plainSubstrByWidth(at+" · "+DailyScreen.str(entry,"operation"),w-30),x+15,top,DailyTheme.GOLD,false);g.drawString(font,font.plainSubstrByWidth(DailyScreen.str(entry,"actor"),w-30),x+15,top+11,DailyTheme.PALE,false);if(my>=top&&my<top+26)hover=Component.literal(DailyScreen.str(entry,"details"));}}
            else for(int i=0;i<Math.min(errors.size(),count);i++){var error=errors.get(i).getAsJsonObject();int top=y+73+i*30;g.drawString(font,font.plainSubstrByWidth(DailyScreen.str(error,"player"),w-125),x+15,top,DailyTheme.GOLD,false);g.drawString(font,font.plainSubstrByWidth(Component.translatable(DailyScreen.str(error,"error")).getString(),w-125),x+15,top+12,0xFFFFB79A,false);}
        }else{
            var specs=specs();for(int n=0;n<rows&&page*rows+n<specs.size();n++){String key=specs.get(page*rows+n);g.drawString(font,font.plainSubstrByWidth(fieldLabel(key).getString(),w/2-35),x+16,y+75+n*30,DailyTheme.PALE,false);}g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(specs.size()+rows-1)/rows),x+w/2,y+h-23,DailyTheme.GOLD);
        }
        if(!message.isBlank())g.drawCenteredString(font,font.plainSubstrByWidth(Component.translatable(message).getString(),w-24),x+w/2,y+h-46,0xFFFFC8A1);
        super.render(g,mx,my,partial);if(hover!=null)g.renderTooltip(font,font.split(hover,Math.min(360,width-24)),mx,my);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(tab.equals("catalog")&&model==null){catalogScroll=Math.max(0,catalogScroll-(int)Math.signum(vertical));rebuildWidgets();return true;}if(tab.equals("audit")){logScroll=Math.clamp(logScroll-(int)Math.signum(vertical),0,Math.max(0,DailyScreen.array(data,"audit").size()-1));return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
