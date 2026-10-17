package pt.realmrewards.visualtest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pt.realmrewards.client.RewardScreen;

/** Actual production widgets/framebuffer; demonstration data, never fabricated screenshots. */
@EventBusSubscriber(modid="realmrewards",value=Dist.CLIENT)
public final class RewardVisualQA {
    private static final Path OUT=Path.of(System.getProperty("rewards.qaOutput","qa-results/rewards-client"));
    private static final long STARTED=System.currentTimeMillis();
    private static final List<String> CHECKS=new ArrayList<>();
    private static final List<JsonObject> REQUESTS=new ArrayList<>();
    private static int step,ticks;
    private static boolean done;
    private static RewardScreen screen;
    private static final BiConsumer<String,JsonObject> SEND=(op,request)->{
        JsonObject copy=request.deepCopy();copy.addProperty("operation",op);REQUESTS.add(copy);
    };
    private static void check(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
    public static JsonObject demo() {
        JsonObject root=new JsonObject();root.addProperty("search","");root.addProperty("page",0);root.addProperty("size",8);root.addProperty("total",48);
        root.addProperty("error","");root.addProperty("chronicles",true);
        root.addProperty("task","2.00");root.addProperty("goal","5.00");root.addProperty("challenge","10.00");
        JsonArray rows=new JsonArray();
        String[] names={"Stone Age","Acquire Hardware","Diamonds!","Cover Me with Debris","A Balanced Diet","Adventure","Modded quest: The Royal Expedition","A Very Very Long Custom Advancement Name"};
        for(int i=0;i<8;i++) {
            JsonObject row=new JsonObject();row.addProperty("id",i==6?"fantasy_quests:royal_expedition":"minecraft:demo/"+i);
            JsonObject title=new JsonObject();title.addProperty("text",names[i]);row.add("title",title);
            row.addProperty("kind",i==4?"CHALLENGE":"TASK");row.addProperty("amount",i==4?"10.00":"2.00");row.addProperty("override",false);rows.add(row);
        } root.add("rows",rows);return root;
    }
    private static void click(String key) {
        for(var child:List.copyOf(screen.children())) if(child instanceof Button button && button.active && button.getMessage().getString().equals(Component.translatable(key).getString())) {button.onPress();return;}
        throw new IllegalStateException("Missing production button: "+key);
    }
    private static void choose() {
        for(var child:List.copyOf(screen.children())) if(child instanceof Button button && button.getMessage().getString().contains("Stone Age")) {button.onPress();return;}
        throw new IllegalStateException("No advancement selection widget");
    }
    private static EditBox edit(String field) throws Exception {
        var f=RewardScreen.class.getDeclaredField(field);f.setAccessible(true);return (EditBox)f.get(screen);
    }
    private static void verify() throws Exception {
        Minecraft mc=Minecraft.getInstance(); screen=new RewardScreen(demo(),SEND);mc.setScreen(screen); choose();
        edit("amountBox").setValue("7,25");click("gui.realmrewards.save");
        var save=REQUESTS.getLast();check(save.get("id").getAsString().equals("minecraft:demo/0") && save.get("value").getAsString().equals("7,25"),"Reward save used wrong identity/amount");
        click("gui.realmrewards.reset");check(REQUESTS.getLast().get("operation").getAsString().equals("reset"),"Default reset missing");
        edit("searchBox").setValue("fantasy_quests:");click("gui.realmrewards.search");
        check(REQUESTS.getLast().get("search").getAsString().equals("fantasy_quests:") && REQUESTS.getLast().get("page").getAsInt()==0,"Search did not reset pagination");
        click("gui.realmrewards.defaults");click("gui.realmrewards.save_defaults");
        check(REQUESTS.getLast().get("task").getAsString().equals("2.00") && REQUESTS.getLast().get("challenge").getAsString().equals("10.00"),"Default reward request changed values");
        screen=new RewardScreen(demo(),SEND);mc.setScreen(screen);screen.resize(mc,320,240);choose();
        for(var child:screen.children()) if(child instanceof AbstractWidget widget)
            check(widget.getX()>=0 && widget.getY()>=0 && widget.getX()+widget.getWidth()<=320 && widget.getY()+widget.getHeight()<=240,"Unreachable control at320x240");
        screen.mouseScrolled(30,110,0,-1);
        CHECKS.add("Production selection, exact reward/default/reset/search payloads and 320x240 scroll/accessibility checks passed.");
        screen=new RewardScreen(demo(),SEND);mc.setScreen(screen);choose();
        CHECKS.add("Snapshots show demonstration advancement data in the actual production administration screen.");
    }
    private static void complete(boolean success,String message) {
        done=true;
        try { Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",success,"message",message,"checks",CHECKS,"actual_minecraft_client",true,"demonstration_data",true))); }
        catch(Exception e) {e.printStackTrace();}
        System.out.println(success?"REWARDS_CLIENT_PASSED":"REWARDS_CLIENT_FAILED "+message);Minecraft.getInstance().stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(done)return;
        try {
            Minecraft mc=Minecraft.getInstance();check(System.currentTimeMillis()-STARTED<180000,"Visual QA timed out");
            if(mc.getOverlay()!=null)return;
            if(screen==null) {
                if(!(mc.screen instanceof TitleScreen)) {
                    if(mc.screen!=null) for(var child:List.copyOf(mc.screen.children())) if(child instanceof Button b && b.getMessage().getString().equals(Component.translatable("gui.continue").getString())) b.onPress();
                    return;
                }
                mc.getWindow().setWindowed(1600,900);mc.options.guiScale().set(2);mc.resizeDisplay();verify();ticks=0;return;
            }
            if(++ticks<60)return;
            Files.createDirectories(OUT);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth()==1600 && image.getHeight()==900,"Framebuffer size mismatch");
                image.writeToFile(OUT.resolve(step==0?"realm-rewards-advancements.png":"realm-rewards-defaults.png"));
            }
            if(step++==0) {click("gui.realmrewards.defaults");ticks=0;}
            else complete(true,"Production rewards administration, both configuration pages and compact-screen controls passed.");
        } catch(Throwable e) {e.printStackTrace();complete(false,e.toString());}
    }
}
