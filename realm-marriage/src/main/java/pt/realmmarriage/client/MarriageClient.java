package pt.realmmarriage.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import pt.realmmarriage.MarriageSounds;
import pt.realmmarriage.network.MarriageNetwork;
import static pt.realmmarriage.client.MarriageScreen.*;
import static pt.realmmarriage.client.MarriageTheme.*;

@EventBusSubscriber(modid="realmmarriage",value=Dist.CLIENT)
public final class MarriageClient {
    public static final KeyMapping OBJECT=new KeyMapping("key.realmmarriage.object",GLFW.GLFW_KEY_G,"key.categories.realmmarriage");
    public static final class Preferences {
        public boolean enabled=true,reduced=false;public float volume=.65f;
        public void save(){try{Files.createDirectories(Path.of("config"));Files.writeString(Path.of("config/realm-marriage-visuals.json"),new GsonBuilder().setPrettyPrinting().create().toJson(this));}catch(java.io.IOException ignored){}}
    }
    private static Preferences preferences;
    private static JsonObject scene;
    private static long received,phaseStarted;
    private static String phaseKey="";
    private static int lastSecond=-1,soundsPlayed;
    private MarriageClient(){}
    public static Preferences preferences(){if(preferences==null){preferences=new Preferences();try{Path p=Path.of("config/realm-marriage-visuals.json");if(Files.isRegularFile(p)&&Files.size(p)<=4096){Preferences loaded=new Gson().fromJson(Files.readString(p),Preferences.class);if(loaded!=null&&Float.isFinite(loaded.volume)&&loaded.volume>=0&&loaded.volume<=1)preferences=loaded;}}catch(java.io.IOException|RuntimeException ignored){}}return preferences;}
    public static int soundsPlayed(){return soundsPlayed;}
    public static JsonObject currentScene(){return scene;}
    public static void receive(String kind,String json){try{if(json==null||json.length()>65536)return;JsonObject payload=JsonParser.parseString(json).getAsJsonObject();Minecraft mc=Minecraft.getInstance();
        switch(kind){case "open"->{if(mc.screen instanceof MarriageScreen s)s.accept(payload);else mc.setScreen(new MarriageScreen(payload,false));}case "snapshot"->{if(mc.screen instanceof MarriageScreen s)s.accept(payload);}case "document"->mc.setScreen(new MarriageScreen(payload,true));case "changed"->{if(mc.screen instanceof MarriageScreen)MarriageNetwork.request("refresh",new JsonObject());}case "scene"->{String key=str(payload,"id")+":"+str(payload,"kind");boolean changed=!key.equals(phaseKey);scene=payload;received=System.nanoTime();if(changed){phaseKey=key;phaseStarted=received;lastSecond=-1;String sound=switch(str(payload,"kind")){case "opening"->"opening";case "suspended"->"objection";case "married"->"married";default->"";};if(!sound.isBlank()&&preferences().enabled)sound(sound);if(str(payload,"kind").equals("suspended")&&mc.player!=null&&mc.player.getUUID().toString().equals(str(payload,"objectorId")))mc.setScreen(new ObjectionScreen(str(payload,"id")));}}default->{}}
    }catch(RuntimeException ignored){}}
    private static void sound(String id){if(preferences().volume>0){Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(MarriageSounds.sound(id),1f,preferences().volume));soundsPlayed++;}}
    private static double remaining(){return scene==null?0:Math.max(0,(number(scene,"until")-number(scene,"now"))/1000.0-(System.nanoTime()-received)/1000000000.0);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){while(OBJECT.consumeClick())if(scene!=null&&str(scene,"kind").equals("objections")&&bool(scene,"canObject")&&!bool(scene,"party")&&remaining()>0&&Minecraft.getInstance().screen==null){JsonObject in=new JsonObject();in.addProperty("union",str(scene,"id"));MarriageNetwork.request("objection",in);}}
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event){scene=null;phaseKey="";lastSecond=-1;}
    public static void render(GuiGraphics g,int width,int height){if(scene==null)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.options.hideGui)return;String kind=str(scene,"kind");boolean objections=kind.equals("objections"),married=kind.equals("married"),interrupted=kind.equals("suspended");if(!preferences().enabled&&!objections)return;
        double elapsed=(System.nanoTime()-phaseStarted)/1000000000.0,duration=objections?60:married?6.5:interrupted?8:4;if(elapsed>duration||objections&&remaining()<=0)return;
        if(objections){int sec=(int)Math.ceil(remaining());if(sec!=lastSecond){lastSecond=sec;if(sec<=3&&sec>0)sound("countdown");}}
        double alpha=Math.min(1,elapsed/.35)*Math.min(1,(duration-elapsed)/.6),scale=Math.min(1,(width-22)/512.0);int pw=512,ph=married?155:140,px=(int)((width-pw*scale)/2),py=Math.max(8,(int)(height*.075));
        g.pose().pushPose();g.pose().translate(px,py-Math.pow(1-Math.min(1,elapsed/.5),2)*35,0);g.pose().scale((float)scale,(float)scale,1);g.setColor(1,1,1,(float)alpha);g.blit(ResourceLocation.parse("realmmarriage:textures/gui/ceremony_scroll.png"),0,0,pw,ph,0,0,512,160,512,160);g.setColor(1,1,1,1);
        int color=interrupted?0xFF8C2948:WINE;String title=tr(objections?"hud.objections":married?"hud.married":interrupted?"hud.interrupted":"hud."+kind).getString();var lines=mc.font.split(Component.literal(title),440);int yy=18;for(var line:lines){g.drawString(mc.font,line,(512-mc.font.width(line))/2,yy,color,false);yy+=12;}
        if(objections){String hint=bool(scene,"party")?tr("hud.party_hint").getString():tr("hud.press",OBJECT.getTranslatedKeyMessage()).getString();g.drawCenteredString(mc.font,hint,256,yy+7,INK);g.pose().pushPose();g.pose().translate(256,yy+31,0);g.pose().scale(2.2f,2.2f,1);g.drawString(mc.font,Integer.toString((int)Math.ceil(remaining())),-mc.font.width(Integer.toString((int)Math.ceil(remaining())))/2,0,WINE,false);g.pose().popPose();int fill=(int)(400*Math.min(1,remaining()/Math.max(1,number(scene,"duration"))));g.fill(56,ph-20,456,ph-17,0xFFBAA77E);g.fill(56,ph-20,56+fill,ph-17,GOLD);}
        else{String main=interrupted?tr("hud.objector",str(scene,"objector")).getString():str(scene,"a")+" & "+str(scene,"b");g.drawCenteredString(mc.font,mc.font.plainSubstrByWidth(main,445),256,yy+12,INK);if(married){double stamp=Math.min(1,Math.max(0,(elapsed-.6)/.65));g.pose().pushPose();g.pose().translate(256,yy+63,0);g.pose().scale((float)(stamp*1.35),(float)(stamp*1.35),1);rings(g,-26,-26,52);g.pose().popPose();g.drawCenteredString(mc.font,tr("hud.official"),256,ph-23,GREEN);}else if(interrupted)g.drawCenteredString(mc.font,tr("hud.discussion"),256,yy+34,MUTED);}
        if(!preferences().reduced){int count=married?30:12;Random random=new Random(str(scene,"id").hashCode());for(int i=0;i<count;i++){double p=(elapsed*(.08+random.nextDouble()*.1)+random.nextDouble())%1;int xx=(int)(random.nextDouble()*512),yy2=(int)((1-p)*ph);if(married&&i%3==0){g.fill(xx,yy2,xx+2,yy2+2,0xFFE6A0B8);g.fill(xx+3,yy2,xx+5,yy2+2,0xFFE6A0B8);g.fill(xx,yy2+2,xx+5,yy2+3,0xFFE6A0B8);g.fill(xx+1,yy2+3,xx+4,yy2+4,0xFFE6A0B8);g.fill(xx+2,yy2+4,xx+3,yy2+5,0xFFE6A0B8);}else g.fill(xx,yy2,xx+2,yy2+2,GOLD);}}
        g.pose().popPose();
    }
    private static final class ObjectionScreen extends Screen {
        private final String union;private EditBox reason;
        ObjectionScreen(String union){super(tr("objection"));this.union=union;}
        @Override protected void init(){int w=Math.min(490,width-16),x=(width-w)/2,y=(height-150)/2;reason=new EditBox(font,x+16,y+63,w-32,20,tr("reason"));reason.setMaxLength(256);addRenderableWidget(reason);addRenderableWidget(button(x+16,y+109,Math.min(120,(w-40)/2),21,tr("send"),()->{JsonObject in=new JsonObject();in.addProperty("union",union);in.addProperty("reason",reason.getValue());MarriageNetwork.request("reason",in);onClose();}));addRenderableWidget(button(x+w-Math.min(120,(w-40)/2)-16,y+109,Math.min(120,(w-40)/2),21,tr("close"),this::onClose));}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){int w=Math.min(490,width-16),x=(width-w)/2,y=(height-150)/2;g.fill(0,0,width,height,0x901D1520);panel(g,x,y,w,150);g.drawString(font,title,x+16,y+16,0xFFFFEBC9,false);g.drawString(font,tr("reason_hint"),x+16,y+43,INK,false);super.render(g,mx,my,partial);}
        @Override public boolean isPauseScreen(){return false;}
    }
    @EventBusSubscriber(modid="realmmarriage",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(OBJECT);}
        @SubscribeEvent public static void layers(RegisterGuiLayersEvent e){e.registerAboveAll(ResourceLocation.parse("realmmarriage:ceremony"),(g,delta)->{var window=Minecraft.getInstance().getWindow();render(g,window.getGuiScaledWidth(),window.getGuiScaledHeight());});}
    }
}
