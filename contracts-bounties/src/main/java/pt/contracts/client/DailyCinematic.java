package pt.contracts.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import pt.contracts.daily.*;
import pt.contracts.daily.DailyRules.Difficulty;

/** A HUD layer: never opens a screen, captures the mouse, moves the camera or confirms a payment. */
@EventBusSubscriber(modid="contracts",value=Dist.CLIENT)
public final class DailyCinematic {
    public static final KeyMapping SKIP=new KeyMapping("key.contracts.daily_skip",GLFW.GLFW_KEY_BACKSPACE,"key.categories.contracts");
    private static final CelebrationQueue<JsonObject> QUEUE=new CelebrationQueue<>();
    private static DailyVisualPreferences preferences;
    private static JsonObject active;
    private static long frame;
    private static double age;
    private static boolean sounded;
    private static int soundsPlayed;
    private DailyCinematic(){}
    public static DailyVisualPreferences preferences(){if(preferences==null)preferences=DailyVisualPreferences.load();return preferences;}
    public static int pending(){return QUEUE.size();}
    public static boolean active(){return active!=null;}
    public static double age(){return age;}
    public static int soundsPlayed(){return soundsPlayed;}
    public static void skip(){active=null;age=0;frame=0;}
    public static void reset(){skip();QUEUE.clear();soundsPlayed=0;}
    public static void receive(String json){
        try{
            DailyRules.jsonEnvelope(json,4096);JsonObject e=JsonParser.parseString(json).getAsJsonObject();UUID id=UUID.fromString(DailyScreen.str(e,"id"));Difficulty difficulty=Difficulty.valueOf(DailyScreen.str(e,"difficulty"));
            if(DailyScreen.str(e,"title").length()>120||DailyScreen.number(e,"money")<0||DailyScreen.number(e,"money")>1_000_000_000L||DailyScreen.number(e,"reputation")<0||DailyScreen.number(e,"reputation")>100)return;
            if(DailyScreen.number(e,"total")<0||DailyScreen.number(e,"total")>1_000_000_000L||DailyScreen.number(e,"tax")<0||DailyScreen.number(e,"tax")>1_000_000_000L)return;
            JsonObject ack=new JsonObject();ack.addProperty("id",id.toString());
            if(!preferences().seen.contains(id.toString())){
                if(preferences().enabled&&!QUEUE.offer(new CelebrationQueue.Event<>(id,DailyScreen.number(e,"day"),difficulty==Difficulty.ALL,e)))return;
                preferences().seen.add(id.toString());while(preferences().seen.size()>512)preferences().seen.removeFirst();preferences().save();
                if(!preferences().enabled)fallback(e);
            }
            DailyNetwork.request("ack",ack);
        }catch(RuntimeException ignored){}
    }
    private static void fallback(JsonObject e){
        Component title=DailyTheme.tr(DailyScreen.str(e,"difficulty").equals("ALL")?"all_complete":"complete_title"),body=Component.literal(DailyScreen.label(DailyScreen.str(e,"title")).getString()+" · +"+(DailyScreen.str(e,"currency").equals("EMERALD")?DailyTheme.tr("emerald_amount",DailyScreen.number(e,"money")/100).getString():DailyTheme.money(DailyScreen.number(e,"money"))));
        try{Class.forName("pt.realmnotifications.client.NotificationsClient").getMethod("receive",String.class,Component.class,Component.class).invoke(null,"reward",title,body);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError absent){var player=Minecraft.getInstance().player;if(player!=null)player.displayClientMessage(Component.literal(title.getString()+" "+body.getString()),true);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){while(SKIP.consumeClick())if(Minecraft.getInstance().screen==null)skip();}
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event){reset();}
    private static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    public static void render(GuiGraphics g,int width,int height){
        var mc=Minecraft.getInstance();long now=System.nanoTime();
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui||mc.isPaused()){frame=now;return;}
        if(active==null){var next=QUEUE.poll();if(next==null){frame=now;return;}active=next.value();age=0;frame=now;sounded=false;}
        if(!preferences().enabled){fallback(active);skip();return;}
        double dt=frame==0?0:Math.clamp((now-frame)/1_000_000_000.0,0,.1);frame=now;age+=dt;
        Difficulty difficulty=Difficulty.valueOf(DailyScreen.str(active,"difficulty"));boolean all=difficulty==Difficulty.ALL;
        double duration=all?preferences().duration+1.5:Math.max(3,preferences().duration-(difficulty==Difficulty.EASY?1.2:difficulty==Difficulty.MEDIUM?.5:0));
        double timeline=age/duration*(all?6.5:5),alpha=smooth(timeline/.5)*(1-smooth((timeline-(all?5.5:4))/.9));
        if(age>=duration){skip();return;}
        if(timeline>=.5&&!sounded){sounded=true;if(preferences().volume>0){mc.getSoundManager().play(SimpleSoundInstance.forUI(DailySounds.sound(difficulty),1f,preferences().volume));soundsPlayed++;}}
        boolean simple=preferences().simplified;double scale=Math.min(1,Math.min((width-26)/512.0,(height-40)/230.0));
        if(difficulty==Difficulty.EASY)scale*=.85;
        int centerX=width/2,centerY=(int)(height*(simple?.19:.25));
        if(!simple){for(int n=0;n<7;n++){int a=(int)(alpha*(all?13:8)*(7-n)/7);int color=a<<24|0x18101F;g.fill(n*6,n*4,width-n*6,n*4+4,color);g.fill(n*6,height-n*4-4,width-n*6,height-n*4,color);g.fill(n*6,n*4,n*6+6,height-n*4,color);g.fill(width-n*6-6,n*4,width-n*6,height-n*4,color);}}
        int particles=preferences().reducedParticles?6:simple?8:difficulty==Difficulty.EASY?12:all?44:difficulty==Difficulty.HARD?30:20;
        long seed=DailyScreen.str(active,"id").hashCode();Random random=new Random(seed);
        if(!simple)for(int i=0;i<particles;i++){
            double angle=random.nextDouble()*Math.PI*2,speed=14+random.nextDouble()*36;double distance=(22+timeline*speed)*scale;
            int px=centerX+(int)(Math.cos(angle)*distance*2.5),py=centerY+(int)(Math.sin(angle)*distance*.65-timeline*5),size=(int)Math.max(1,(1+random.nextInt(2))*scale);
            int color=(int)(alpha*(110+random.nextInt(100)))<<24|((difficulty==Difficulty.HARD&&i%3==0)?0xD4A5FF:0xFFDA76);g.fill(px-size,py,px+size+1,py+size,color);if(i%3==0)g.fill(px,py-size,px+1,py+size*2,color);
        }
        double entrance=.9+.1*smooth(timeline/.6),exit=1-smooth((timeline-(all?5.5:4))/.9)*.08;scale*=entrance*exit;
        g.pose().pushPose();g.pose().translate(centerX,centerY+(1-smooth(timeline/.5))*-20,0);g.pose().scale((float)scale,(float)scale,1);g.setColor(1,1,1,(float)alpha);
        if(!simple)for(int ring=4;ring>=1;ring--){int glow=(int)(alpha*(difficulty==Difficulty.HARD?9:12))<<24|(difficulty==Difficulty.HARD?0xAA72D0:0xF1CB6D);g.fill(-256-ring*2,-96-ring*2,256+ring*2,96+ring*2,glow);}
        var paper=ResourceLocation.parse("contracts:textures/gui/daily/scroll_"+difficulty.name().toLowerCase(Locale.ROOT)+".png");g.blit(paper,-256,-96,0,0,512,192,512,192);g.flush();g.setColor(1,1,1,1);
        int alphaByte=(int)(255*alpha)<<24;int titleColor=alphaByte|(all?0x795219:difficulty==Difficulty.HARD?0x65416F:0x382C24);
        String banner=DailyTheme.tr(all?"all_complete":"complete_title").getString();g.drawCenteredString(mc.font,mc.font.plainSubstrByWidth(banner,420),0,-66,titleColor);
        if(timeline>=1){String title=all?DailyTheme.tr("all_total").getString():DailyScreen.label(DailyScreen.str(active,"title")).getString().toUpperCase(Locale.ROOT);g.drawCenteredString(mc.font,mc.font.plainSubstrByWidth(title,400),0,-39,alphaByte|0x382C24);}
        if(timeline>=1.5){long value=all?DailyScreen.number(active,"total"):DailyScreen.number(active,"money");long shown=Math.round(value*smooth((timeline-1.5)/1.25));String amount=(all?"":"+")+(DailyScreen.str(active,"currency").equals("EMERALD")?DailyTheme.tr("emerald_amount",shown/100).getString():DailyTheme.money(shown));g.pose().pushPose();g.pose().scale(2,2,1);g.drawString(mc.font,amount,-mc.font.width(amount)/2,-3,alphaByte|0x315C3F,false);g.pose().popPose();}
        if(timeline>=3){
            int rep=(int)DailyScreen.number(active,"reputation");String subtitle=all?DailyTheme.tr("five_of_five").getString():DailyTheme.tr("reputation",rep).getString();g.drawCenteredString(mc.font,subtitle,0,29,titleColor);
            double stamp=smooth((timeline-3)/.35);int size=(int)(52+(1-stamp)*35);g.setColor(1,1,1,(float)(alpha*stamp));DailyTheme.seal(g,177-size/2,24-size/2,size,difficulty.name());g.flush();g.setColor(1,1,1,1);
        }
        g.drawCenteredString(mc.font,DailyTheme.tr("skip_hint",SKIP.getTranslatedKeyMessage()),0,51,alphaByte|0x8B795A);g.pose().popPose();
    }
    @EventBusSubscriber(modid="contracts",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(SKIP);}
        @SubscribeEvent public static void layers(RegisterGuiLayersEvent event){event.registerAboveAll(ResourceLocation.parse("contracts:daily_cinematic"),(g,delta)->{var window=Minecraft.getInstance().getWindow();render(g,window.getGuiScaledWidth(),window.getGuiScaledHeight());});}
    }
}
