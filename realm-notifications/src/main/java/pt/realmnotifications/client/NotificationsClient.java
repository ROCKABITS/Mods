package pt.realmnotifications.client;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="realmnotifications",value=Dist.CLIENT)
public final class NotificationsClient {
    public record Entry(String kind,Component title,Component body,String time) {}
    private static final class Card {
        final Entry entry;double age;int count=1;
        Card(Entry entry) {this.entry=entry;}
    }
    private static NotificationPreferences preferences;
    private static final ArrayDeque<Entry> HISTORY=new ArrayDeque<>();
    private static final ArrayDeque<Card> PENDING=new ArrayDeque<>();
    private static final List<Card> VISIBLE=new ArrayList<>();
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm");
    private static long lastFrame,lastSound;
    private static int soundsPlayed;
    private NotificationsClient() {}
    public static NotificationPreferences preferences() {
        if(preferences==null)preferences=NotificationPreferences.load();return preferences;
    }
    public static List<Entry> history() {return List.copyOf(HISTORY);}
    public static int pendingCount() {return PENDING.size();}
    public static int visibleCount() {return VISIBLE.size();}
    public static int soundsPlayed() {return soundsPlayed;}
    public static void clearHistory() {HISTORY.clear();}
    public static void clearCards() {PENDING.clear();VISIBLE.clear();lastFrame=0;}
    public static void resetSession() {clearCards();clearHistory();lastSound=0;soundsPlayed=0;}
    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {resetSession();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null)return;
        while(NotificationClientEvents.HISTORY_KEY.consumeClick()) {
            if(mc.screen==null)mc.setScreen(new NotificationScreen(null,true));
        }
    }
    public static void receive(String kind,Component title,Component body) {
        var mc=Minecraft.getInstance();
        if(!mc.isSameThread()) {mc.execute(()->receive(kind,title,body));return;}
        if(mc.player==null || mc.level==null)return;
        Entry e=new Entry(kind,title.copy(),body.copy(),LocalTime.now().format(TIME));
        if(preferences().history) {HISTORY.addFirst(e);while(HISTORY.size()>100)HISTORY.removeLast();}
        if(!preferences().enabled)return;
        for(Card c:VISIBLE)if(c.age<1500 && same(c.entry,e)) {c.count=Math.min(99,c.count+1);return;}
        if(!PENDING.isEmpty() && same(PENDING.getLast().entry,e)) {Card c=PENDING.getLast();c.count=Math.min(99,c.count+1);return;}
        if(PENDING.size()>=32)PENDING.removeFirst();PENDING.addLast(new Card(e));
    }
    private static boolean same(Entry a,Entry b) {return a.kind.equals(b.kind)&&a.title.getString().equals(b.title.getString())&&a.body.getString().equals(b.body.getString());}
    private static void sound() {
        long now=System.nanoTime()/1_000_000;
        if(!preferences().sound || now-lastSound<350)return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP,1.3f,0.14f));
        lastSound=now;soundsPlayed++;
    }
    public static void render(GuiGraphics g,int screenWidth,int screenHeight) {
        var mc=Minecraft.getInstance();
        long now=System.nanoTime();
        if(mc.player==null || mc.level==null || mc.screen!=null || mc.options.hideGui || !preferences().enabled) {lastFrame=now;return;}
        double elapsed=lastFrame==0?0:Math.clamp((now-lastFrame)/1_000_000.0,0,100);lastFrame=now;
        if(mc.isPaused())elapsed=0;
        int slots=CardLayout.visible(screenHeight);
        while(VISIBLE.size()>slots) {Card c=VISIBLE.removeLast();c.age=0;while(PENDING.size()>=32)PENDING.removeLast();PENDING.addFirst(c);}
        while(VISIBLE.size()<slots&&!PENDING.isEmpty()) {VISIBLE.add(PENDING.removeFirst());sound();}
        double duration=preferences().seconds*1000.0;
        for(Card c:VISIBLE)c.age+=elapsed;
        VISIBLE.removeIf(c->c.age>=duration+340);
        int w=CardLayout.width(screenWidth),slot=0;
        String position=preferences().position.name();
        for(Card c:VISIBLE) {
            int x=CardLayout.x(screenWidth,w,position),y=CardLayout.y(screenHeight,slot++,position);
            double entrance=CardLayout.ease(c.age/360),exit=CardLayout.ease((c.age-duration)/340);
            double visibility=preferences().animations?entrance*(1-exit):1;
            int move=(int)((1-visibility)*(w+18));
            if(position.endsWith("LEFT"))x-=move;
            else if(position.endsWith("RIGHT"))x+=move;
            else y-= (int)((1-visibility)*96);
            // Motion plus a short soft fade. Flush brackets keep shader tint local to this card.
            g.pose().pushPose();
            g.setColor(1,1,1,(float)Math.clamp(visibility*2,0,1));
            NotificationTheme.card(g,x,y,w,c.entry.kind,c.entry.title,c.entry.body,1-c.age/duration,c.count);
            g.flush();g.setColor(1,1,1,1);g.pose().popPose();
        }
    }
}
