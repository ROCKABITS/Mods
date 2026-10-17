package pt.realmnotifications.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Local presentation settings and a private, session-only journal. No gameplay actions live here. */
public final class NotificationScreen extends Screen {
    private final Screen parent;
    private boolean journal;
    private int px,py,pw,ph,contentY,footerY,scroll;
    private boolean saveError;
    private record HistoryRow(NotificationsClient.Entry entry,List<FormattedCharSequence> lines,int height) {}
    private List<NotificationsClient.Entry> cachedEntries=List.of();
    private List<HistoryRow> rows=List.of();
    private int cachedWidth=-1,totalHistoryHeight;
    public NotificationScreen(Screen parent,boolean journal) {super(tr("title"));this.parent=parent;this.journal=journal;}
    private static Component tr(String key,Object...args) {return Component.translatable("gui.realmnotifications."+key,args);}
    private NotificationPreferences p() {return NotificationsClient.preferences();}
    private void change() {saveError=!p().save();rebuildWidgets();}
    public boolean journal() {return journal;}
    @Override protected void init() {
        pw=Math.min(640,width-16);ph=Math.min(360,height-16);px=(width-pw)/2;py=(height-ph)/2;
        boolean compact=ph<300;int tabY=py+(compact?35:50);
        contentY=py+(compact?61:80);footerY=py+ph-30;
        addRenderableWidget(button(px+17,tabY,Math.min(126,(pw-42)/2),19,tr("settings"),b->{journal=false;scroll=0;rebuildWidgets();}));
        addRenderableWidget(button(px+150>px+pw/2?px+pw/2:px+150,tabY,Math.min(126,(pw-42)/2),19,tr("history"),b->{journal=true;scroll=0;rebuildWidgets();}));
        if(!journal) {
            int left=px+18,right=pw>=560?px+168:px+pw-142;
            int controlWidth=pw>=560?122:124;
            int row=compact?19:28;
            option(right,contentY,controlWidth,tr(p().enabled?"on":"off"),b->{p().enabled=!p().enabled;if(!p().enabled)NotificationsClient.clearCards();change();});
            option(right,contentY+row,controlWidth,tr("position."+p().position.name().toLowerCase(Locale.ROOT)),b->{var a=NotificationPreferences.Position.values();p().position=a[(p().position.ordinal()+1)%a.length];change();});
            option(right,contentY+row*2,controlWidth,tr("seconds",p().seconds),b->{int[] durations={3,6,9,12,15};int next=3;for(int d:durations)if(d>p().seconds){next=d;break;}p().seconds=next;change();});
            option(right,contentY+row*3,controlWidth,tr(p().sound?"on":"off"),b->{p().sound=!p().sound;change();});
            option(right,contentY+row*4,controlWidth,tr(p().animations?"on":"off"),b->{p().animations=!p().animations;change();});
            option(right,contentY+row*5,controlWidth,tr(p().history?"on":"off"),b->{p().history=!p().history;if(!p().history)NotificationsClient.clearHistory();change();});
        }
        int bw=(pw-46)/3;
        addRenderableWidget(button(px+17,footerY,bw,19,tr("preview"),b->{
            if(minecraft.player!=null) {
                NotificationsClient.receive("info",tr("preview_title"),tr("preview_body"));
                minecraft.setScreen(null);
            }
        })).active=minecraft.player!=null;
        addRenderableWidget(button(px+23+bw,footerY,bw,19,tr("clear"),b->{NotificationsClient.clearHistory();scroll=0;}));
        addRenderableWidget(button(px+29+bw*2,footerY,bw,19,Component.translatable("gui.done"),b->onClose()));
    }
    private void option(int x,int y,int width,Component value,Consumer<Button> action) {
        var control=addRenderableWidget(button(x,y,width,17,value,action));control.setTooltip(Tooltip.create(value));
    }
    private Button button(int x,int y,int w,int h,Component label,Consumer<Button> press) {
        return new Button(x,y,w,h,label,press::accept,Button.DEFAULT_NARRATION) {
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
                int border=isHoveredOrFocused()?0xFFE3C17C:0xFF8C7548;
                g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),border);
                g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,isHoveredOrFocused()?0xFF3C5947:0xFF24392F);
                g.fill(getX()+2,getY()+2,getX()+getWidth()-2,getY()+3,0xFF4B5C42);
                int color=active?NotificationTheme.PALE:0xFF81795F;
                String text=font.plainSubstrByWidth(getMessage().getString(),getWidth()-8);
                g.drawCenteredString(font,text,getX()+getWidth()/2,getY()+(getHeight()-8)/2,color);
            }
        };
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial) { /* Own background avoids vanilla blur over labels. */ }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        g.fill(0,0,width,height,0xCF111A16);
        for(int i=0;i<width;i+=48)g.fill(i,0,i+1,height,0x143E5340);
        NotificationTheme.panel(g,px,py,pw,ph);
        NotificationTheme.icon(g,"info",px+16,py+7);
        g.drawString(font,tr("title"),px+54,py+13,NotificationTheme.GOLD,false);
        if(ph>=300)g.drawString(font,tr("subtitle"),px+54,py+29,0xFFB5B291,false);
        g.fill(px+15,contentY-8,px+pw-15,contentY-7,0xFF786C43);
        if(journal)renderHistory(g);
        else renderSettings(g);
        if(saveError)g.drawCenteredString(font,tr("save_error"),width/2,footerY-12,0xFFFFAA88);
        super.render(g,mx,my,partial);
    }
    private void renderSettings(GuiGraphics g) {
        int row=ph<300?19:28;
        String[] labels={"enabled","position","duration","sound","animations","keep_history"};
        for(int i=0;i<labels.length;i++)g.drawString(font,tr(labels[i]),px+19,contentY+i*row+5,NotificationTheme.PALE,false);
        if(pw>=560 && ph>=300) {
            int x=px+pw-302,y=contentY;
            g.drawString(font,tr("preview_label"),x,y,NotificationTheme.GOLD,false);
            NotificationTheme.card(g,x,y+21,278,"reward",Component.translatable("notification.realmnotifications.kind.reward"),tr("demo.reward"),.72,1);
            g.drawString(font,tr("private"),x,y+117,0xFFB9B598,false);
            g.drawString(font,tr("session"),x,y+132,0xFFB9B598,false);
            g.drawString(font,tr("key_hint",NotificationClientEvents.HISTORY_KEY.getTranslatedKeyMessage()),x,y+157,NotificationTheme.GOLD,false);
            g.drawString(font,tr("key_settings"),x,y+172,0xFFB9B598,false);
        }
    }
    private int historyHeight() {
        var entries=NotificationsClient.history();
        if(cachedWidth!=pw || !entries.equals(cachedEntries)) {
            cachedWidth=pw;cachedEntries=entries;List<HistoryRow> rebuilt=new ArrayList<>();totalHistoryHeight=0;
            for(var e:entries) {
                var lines=font.split(e.body(),pw-99);int h=Math.max(53,31+lines.size()*11);
                rebuilt.add(new HistoryRow(e,lines,h));totalHistoryHeight+=h+7;
            }
            rows=List.copyOf(rebuilt);
        }
        return totalHistoryHeight;
    }
    private void renderHistory(GuiGraphics g) {
        int viewHeight=footerY-contentY-9;
        int fullHeight=historyHeight();scroll=Math.clamp(scroll,0,Math.max(0,fullHeight-viewHeight));
        g.enableScissor(px+16,contentY,px+pw-16,footerY-8);
        int y=contentY-scroll;
        if(rows.isEmpty()) {
            NotificationTheme.icon(g,"info",px+pw/2-16,contentY+16);
            g.drawCenteredString(font,tr("empty"),px+pw/2,contentY+58,NotificationTheme.PALE);
        }
        for(var row:rows) {
            var entry=row.entry();var lines=row.lines();int h=row.height();
            if(y+h<contentY) {y+=h+7;continue;}
            if(y>footerY-8)break;
            g.fill(px+18,y,px+pw-21,y+h,0xFFE7D5AD);
            g.fill(px+18,y,px+pw-21,y+2,0xFFAA8950);
            NotificationTheme.icon(g,entry.kind(),px+25,y+12);
            String title=font.plainSubstrByWidth(entry.title().getString(),pw-139);
            g.drawString(font,Component.literal(title).withStyle(s->s.withBold(true)),px+65,y+9,NotificationTheme.INK,false);
            g.drawString(font,entry.time(),px+pw-55,y+10,0xFF88724C,false);
            int lineY=y+25;
            for(var line:lines) {g.drawString(font,line,px+65,lineY,NotificationTheme.INK,false);lineY+=11;}
            y+=h+7;
        }
        g.disableScissor();
        if(fullHeight>viewHeight) {
            int track=footerY-contentY-11,thumb=Math.max(14,track*viewHeight/fullHeight);
            int top=contentY+(track-thumb)*scroll/Math.max(1,fullHeight-viewHeight);
            g.fill(px+pw-16,contentY,px+pw-14,contentY+track,0xFF49604A);
            g.fill(px+pw-16,top,px+pw-14,top+thumb,NotificationTheme.GOLD);
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(journal) {scroll=Math.clamp(scroll-(int)(vertical*25),0,Math.max(0,historyHeight()-(footerY-contentY-9)));return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean isPauseScreen() {return false;}
    @Override public void onClose() {if(p().save())minecraft.setScreen(parent);else saveError=true;}
}
