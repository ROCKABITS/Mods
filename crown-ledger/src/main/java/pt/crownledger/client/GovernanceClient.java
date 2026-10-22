package pt.crownledger.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import pt.crownledger.network.GovernancePackets;

@EventBusSubscriber(modid="kingdomtreasury",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class GovernanceClient {
    public static final KeyMapping VOTE=new KeyMapping("realm.treasury.key.vote",GLFW.GLFW_KEY_F8,"key.categories.multiplayer");
    public static JsonObject state=new JsonObject();
    private static final Set<String> SEEN=new HashSet<>();
    private static long receivedAt,bannerAt;
    private static boolean hideBanner;
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(VOTE);}
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent e){e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("kingdomtreasury","public_vote"),(g,delta)->render(g));}
    public static void request(String action,JsonObject data){
        if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new GovernancePackets.Request(action,data.toString()));
    }
    public static void open(){request("query",new JsonObject());}
    public static void receive(String json){
        try {
            JsonObject data=JsonParser.parseString(json).getAsJsonObject();state=data;receivedAt=System.nanoTime();
            if(data.has("poll")){
                String id=data.getAsJsonObject("poll").get("id").getAsString();
                if(SEEN.add(id)){bannerAt=System.nanoTime();hideBanner=false;}
            }
            var mc=Minecraft.getInstance();
            if(mc.screen instanceof GovernanceScreen screen)screen.accept(data);
            else if(data.has("open")&&data.get("open").getAsBoolean())mc.setScreen(new GovernanceScreen(data));
        }catch(RuntimeException invalid){/* A malformed visual state never changes server votes or balances. */}
    }
    public static long time(){return state.has("now")?state.get("now").getAsLong()+Math.max(0,(System.nanoTime()-receivedAt)/1_000_000):0;}
    public static void dismiss(){hideBanner=true;}
    public static Component tr(String key,Object...args){return Component.translatable("realm.treasury."+key,args);}
    public static String question(JsonObject poll){
        String q=poll.get("question").getAsString();return poll.has("offer")&&!poll.get("offer").isJsonNull()&&q.startsWith("realm.treasury.benefit.")?Component.translatable(q).getString():q;
    }
    public static String duration(long millis){long seconds=Math.max(0,millis/1000);return seconds>=3600?(seconds/3600)+" h":String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private static void render(GuiGraphics g){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||mc.screen!=null||hideBanner||!state.has("poll"))return;
        var poll=state.getAsJsonObject("poll");long left=poll.get("deadline").getAsLong()-time();if(left<=0)return;
        boolean expanded=(System.nanoTime()-bannerAt)<12_000_000_000L;
        int w=Math.min(expanded?366:240,g.guiWidth()-20),h=expanded?84:26,x=(g.guiWidth()-w)/2,y=12;
        g.fill(x+3,y+4,x+w+3,y+h+4,0x55100D09);
        g.fillGradient(x,y,x+w,y+h,0xF52D382F,0xF018211D);
        g.fill(x,y,x+w,y+1,0xFFD6B36C);g.fill(x,y+h-1,x+w,y+h,0xFF806A42);
        TreasuryArt.seal(g,x+18,y+13,9);
        g.drawString(mc.font,tr("vote.banner"),x+34,y+8,0xFFFFE3A4,false);
        String timer=duration(left);g.drawString(mc.font,timer,x+w-mc.font.width(timer)-10,y+8,0xFFCAB988,false);
        if(expanded){
            var lines=mc.font.split(Component.literal(question(poll)),w-28);
            for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(mc.font,lines.get(i),x+14,y+29+i*11,0xFFECE3CB,false);
            if(poll.has("description")&&!poll.get("description").getAsString().isBlank())
                g.drawString(mc.font,mc.font.plainSubstrByWidth(poll.get("description").getAsString().replace('\n',' '),w-28),x+14,y+51,0xFFD2C9B5,false);
            g.drawCenteredString(mc.font,tr("vote.key_hint",VOTE.getTranslatedKeyMessage()),x+w/2,y+64,0xFFD2BD8D);
        }
    }
    @EventBusSubscriber(modid="kingdomtreasury",value=Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event){
            var mc=Minecraft.getInstance();while(VOTE.consumeClick())if(mc.player!=null&&mc.screen==null)open();
        }
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
        public static void mining(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event){
            var mc=Minecraft.getInstance();
            if(event.getEntity()!=mc.player||mc.player==null||mc.player.isCreative()||mc.player.isSpectator()||!state.has("active"))return;
            for(var element:state.getAsJsonArray("active")){
                var benefit=element.getAsJsonObject();
                if(benefit.get("benefit").getAsString().equals("WORKS")&&benefit.get("expiresAt").getAsLong()>time()){
                    if(event.getNewSpeed()>0)event.setNewSpeed(event.getNewSpeed()*(1+benefit.get("strength").getAsInt()/100F));return;
                }
            }
        }
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){state=new JsonObject();hideBanner=true;SEEN.clear();}
    }
    private GovernanceClient(){}
}
