package pt.realmeconomy.jeiguitest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pt.realmeconomy.client.ColonyScreen;
import pt.realmeconomy.client.ServicesScreen;
import pt.realmeconomy.client.TradingScreen;
import pt.realmeconomy.menu.ColonyMenu;
import pt.realmeconomy.menu.EconomyMenu;
import pt.realmeconomy.menu.ServicesMenu;

/** Real JEI, client and integrated world. Menu data is presentation-only; no financial requests. */
@EventBusSubscriber(modid="realmeconomy", value=Dist.CLIENT)
public final class JeiGuiQA {
    private static final Path OUT=Path.of(System.getProperty("economy.jeiQAOutput", "qa-results/jei-client"));
    private static final long START=System.currentTimeMillis();
    private static final List<String> CHECKS=new ArrayList<>(), SHOTS=new ArrayList<>();
    private static long next;
    private static int stage;
    private static boolean done;
    private static Minecraft mc() { return Minecraft.getInstance(); }
    private static void check(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
    private static Object field(Object value,String name) throws Exception {
        var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }
    private static boolean click(String key) {
        if(mc().screen==null)return false;
        for(var child:List.copyOf(mc().screen.children()))if(child instanceof AbstractButton b && b.active
                && b.getMessage().getString().equals(Component.translatable(key).getString())) {b.onPress();return true;}
        return false;
    }
    private static void show(int index) throws Exception {
        var p=mc().player;
        Screen screen=switch(index) {
            case 0->new ColonyScreen(new ColonyMenu(17,p.getInventory()),p.getInventory(),Component.literal("Colony"));
            case 1->new TradingScreen(new EconomyMenu(18,p.getInventory()),p.getInventory(),Component.literal("Trading"));
            default->new ServicesScreen(new ServicesMenu(19,p.getInventory()),p.getInventory(),Component.literal("Services"));
        };
        JsonObject data=new JsonObject();data.addProperty("name","Realm · Colony Ledger");data.addProperty("context","DESK");
        data.addProperty("balance",0);data.addProperty("wallet",17500);data.addProperty("owner","Player");
        data.addProperty("manager",true);data.addProperty("administrator",true);data.addProperty("walletAvailable",true);
        var policy=new JsonObject();policy.addProperty("enabled",true);policy.addProperty("dailyCap",25000);data.add("policy",policy);
        var state=screen.getClass().getDeclaredField("data");state.setAccessible(true);state.set(screen,data);
        var sender=screen.getClass().getDeclaredField("requestSender");sender.setAccessible(true);
        sender.set(screen,(BiConsumer<String,JsonObject>)(op,body)->{});
        mc().setScreen(screen);
    }
    private static void legacyBounds() throws Exception {
        for(String name:List.of("leftPos","topPos","imageWidth","imageHeight")) {
            Field f=AbstractContainerScreen.class.getDeclaredField(name);f.setAccessible(true);
            f.setInt(mc().screen,name.equals("imageWidth")?176:name.equals("imageHeight")?166:0);
        }
    }
    private static void bounds() throws Exception {
        var screen=mc().screen;
        var properties=JeiRuntimeCapture.runtime.getScreenHelper().getGuiProperties(screen).orElseThrow();
        check(properties.guiLeft()==(int)field(screen,"x") && properties.guiTop()==(int)field(screen,"y")
                && properties.guiXSize()==(int)field(screen,"w") && properties.guiYSize()==(int)field(screen,"h"),
                "JEI's real screen helper does not match the painted panel: "+screen.getClass().getSimpleName());
    }
    private static boolean ingredientInsidePanel() throws Exception {
        var screen=mc().screen;int x=(int)field(screen,"x"),y=(int)field(screen,"y"),w=(int)field(screen,"w"),h=(int)field(screen,"h");
        Field mx=mc().mouseHandler.getClass().getDeclaredField("xpos"),my=mc().mouseHandler.getClass().getDeclaredField("ypos");
        mx.setAccessible(true);my.setAccessible(true);double oldX=mx.getDouble(mc().mouseHandler),oldY=my.getDouble(mc().mouseHandler);
        try {
            for(int yy=y+12;yy<y+h-12;yy+=18)for(int xx=x+12;xx<x+w-12;xx+=18) {
                mx.setDouble(mc().mouseHandler,xx*mc().getWindow().getScreenWidth()/(double)screen.width);
                my.setDouble(mc().mouseHandler,yy*mc().getWindow().getScreenHeight()/(double)screen.height);
                if(JeiRuntimeCapture.runtime.getIngredientListOverlay().getIngredientUnderMouse().isPresent())return true;
            }
            return false;
        } finally {mx.setDouble(mc().mouseHandler,oldX);my.setDouble(mc().mouseHandler,oldY);}
    }
    private static void capture(String name) throws Exception {
        Files.createDirectories(OUT);
        try(var shot=Screenshot.takeScreenshot(mc().getMainRenderTarget())) {shot.writeToFile(OUT.resolve(name));}
        SHOTS.add(name);
    }
    private static void fixed(String name) throws Exception {
        bounds();check(!JeiRuntimeCapture.runtime.getIngredientListOverlay().isListDisplayed(),"JEI list should not fit beside the full-width panel");
        capture(name);CHECKS.add(mc().screen.getClass().getSimpleName()+": correct JEI rectangle and no overlay on the panel.");
    }
    private static void finish(boolean success,String message) {
        done=true;
        try {Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "success",success,"message",message,"stage",stage,"actualMinecraftClient",true,"actualJei",true,
                "jeiVersion","19.70.0.469","syntheticFinancialSnapshots",true,"checks",CHECKS,"screenshots",SHOTS)));}
        catch(Exception e){e.printStackTrace();}
        System.out.println("ECONOMY_JEI_GUI_QA success="+success+" "+message);mc().stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(done)return;long now=System.currentTimeMillis();
        try {
            check(now-START<300000,"JEI GUI test timed out");
            if(mc().getOverlay()!=null||now<next)return;next=now+1200;
            switch(stage) {
                case 0->{if(mc().screen instanceof TitleScreen) {
                    mc().getWindow().setWindowed(1600,900);mc().options.guiScale().set(2);mc().options.renderDistance().set(2);
                    mc().options.simulationDistance().set(5);mc().resizeDisplay();CreateWorldScreen.openFresh(mc(),mc().screen);stage++;
                }else click("gui.continue");}
                case 1->{if(mc().screen instanceof CreateWorldScreen world) {
                    world.getUiState().setName("Realm Economy JEI GUI regression");world.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    world.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
                    check(click("selectWorld.create"),"Create-world button missing");stage++;
                }}
                case 2->{if(mc().player!=null && mc().level!=null && JeiRuntimeCapture.runtime!=null) {mc().setScreen(new InventoryScreen(mc().player));stage++;next=now+2500;}}
                case 3->{check(JeiRuntimeCapture.runtime.getIngredientListOverlay().isListDisplayed(),"Positive control: JEI must be active on vanilla inventory");
                    capture("jei-vanilla-before.png");show(0);legacyBounds();stage++;}
                case 4->{check(JeiRuntimeCapture.runtime.getIngredientListOverlay().isListDisplayed()&&ingredientInsidePanel(),"Legacy-bounds control did not reproduce the user's overlap");
                    capture("colony-jei-legacy-reproduction.png");CHECKS.add("Legacy 176x166 bounds reproduce real JEI ingredients over the painted colony panel.");
                    mc().screen.resize(mc(),mc().getWindow().getGuiScaledWidth(),mc().getWindow().getGuiScaledHeight());stage++;}
                case 5->{fixed("colony-jei-fixed.png");show(1);stage++;}
                case 6->{fixed("trading-jei-fixed.png");show(2);stage++;}
                case 7->{fixed("services-jei-fixed.png");mc().options.guiScale().set(1);mc().resizeDisplay();show(0);stage++;}
                case 8->{bounds();check(JeiRuntimeCapture.runtime.getIngredientListOverlay().isListDisplayed(),"JEI must remain available when side space exists");
                    check(!ingredientInsidePanel(),"JEI ingredients overlap the wide-screen colony panel");capture("colony-jei-wide.png");
                    CHECKS.add("Wide viewport: JEI remains visible beside the panel and no ingredients hit the painted panel.");
                    mc().options.guiScale().set(3);mc().resizeDisplay();show(0);stage++;}
                case 9->{fixed("colony-jei-resized.png");mc().options.guiScale().set(2);mc().resizeDisplay();mc().setScreen(new InventoryScreen(mc().player));stage++;}
                case 10->{check(JeiRuntimeCapture.runtime.getIngredientListOverlay().isListDisplayed(),"JEI was globally disabled or not restored on vanilla inventory");
                    capture("jei-vanilla-after.png");CHECKS.add("Vanilla inventory still displays JEI after closing the Realm screens.");finish(true,"Actual JEI overlap reproduced and corrected; all three Realm screens and resize controls passed.");}
                default->throw new IllegalStateException("Unexpected stage");
            }
        } catch(Throwable e) {e.printStackTrace();finish(false,e.toString());}
    }
}
