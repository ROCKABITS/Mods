package pt.realmprogression.client;
import java.util.*;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;

/** Cosmetic state is server-issued. Banners never decide whether crafting is allowed. */
public final class ClientProgress {
    private static Set<String> locked=Set.of();private static final ArrayDeque<String> QUEUE=new ArrayDeque<>();private static String current;private static long started;
    public static Set<String> locked(){return locked;}
    public static void receive(String json){var result=new TreeSet<String>();for(var raw:JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("locked")){String ns=raw.getAsString();pt.realmprogression.domain.ProgressLedger.id(ns);result.add(ns);}if(result.size()>2048)throw new IllegalArgumentException();locked=Set.copyOf(result);}
    public static void clear(){locked=Set.of();QUEUE.clear();current=null;}
    public static void announce(String name){if(QUEUE.size()<32)QUEUE.add(name);}
    public static void render(GuiGraphics g){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
        long now=System.currentTimeMillis();if(current==null&&!QUEUE.isEmpty()){current=QUEUE.remove();started=now;mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,.9F,.45F));}
        if(current==null)return;double age=(now-started)/1000.0;if(age>4.5){current=null;return;}
        int w=Math.min(330,mc.getWindow().getGuiScaledWidth()-16),x=(mc.getWindow().getGuiScaledWidth()-w)/2,y=(int)(12-Math.max(0,1-age/.4)*65-Math.max(0,(age-4.1)/.4)*65);
        ResearchArt.panel(g,x,y,w,53);ResearchArt.seal(g,x+23,y+26,true);
        g.drawString(mc.font,Component.translatable("gui.realmprogression.unlocked_title"),x+45,y+11,ResearchArt.GOLD,false);
        g.drawString(mc.font,mc.font.plainSubstrByWidth(current,w-56),x+45,y+26,ResearchArt.TEXT,false);
        g.fill(x+45,y+42,x+w-12,y+43,ResearchArt.GOLD);
    }
    private ClientProgress(){}
}
