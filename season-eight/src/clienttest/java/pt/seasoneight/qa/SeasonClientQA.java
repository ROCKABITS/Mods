package pt.seasoneight.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import pt.seasoneight.SeasonConfig;
import pt.seasoneight.SeasonEight;
import pt.seasoneight.client.LoadingPresentation;
import pt.seasoneight.client.SeasonTitleScreen;
import pt.seasoneight.client.WindowBranding;

/** Real Minecraft client harness, opt-in source set excluded from production JARs. */
@EventBusSubscriber(modid = SeasonEight.ID, value = Dist.CLIENT)
public final class SeasonClientQA {
    private static final Path OUT = Path.of(System.getProperty("seasoneight.qaOutput"));
    private static final long START = System.currentTimeMillis();
    private static int step;
    private static long next, renderFrames, actionFrame = -1;
    private static Screen rendered;
    private static boolean done, initialLoading, reloadLoading, compactReloadStarted, compactLoading;
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) { rendered = event.getScreen(); renderFrames++; }
    private static void check(boolean value, String error) { if (!value) throw new IllegalStateException(error); }
    private static void capture(String file) throws Exception {
        Files.createDirectories(OUT);
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            var mc = Minecraft.getInstance();
            if (file.contains("loading") && mc.screen instanceof SeasonTitleScreen) {
                var box = pt.seasoneight.client.MenuLayout.of(mc.screen.width,mc.screen.height).controls().getLast();
                double scale = (double)image.getWidth()/mc.screen.width;
                int bright=0;
                for (int y=(int)(box.y()*scale); y<(int)((box.y()+box.h())*scale); y++)
                    for (int x=(int)(box.x()*scale); x<(int)((box.x()+box.w())*scale); x++) {
                        int pixel=image.getPixelRGBA(x,y);
                        if ((pixel&255)>125 && ((pixel>>8)&255)>125 && ((pixel>>16)&255)>125) bright++;
                    }
                check(bright<=8,"Underlying menu text leaked through the loading screen: " + bright + " bright pixels");
            }
            image.writeToFile(OUT.resolve(file + ".png"));
        }
        System.out.println("S8_QA captured " + file);
    }
    private static void click(String key) {
        var b = Minecraft.getInstance().screen.children().stream().filter(c -> c instanceof AbstractButton button &&
                button.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key))
                .map(c -> (AbstractButton)c).findFirst().orElseThrow(() -> new IllegalStateException("Missing action " + key));
        check(b.visible && b.active, "Inactive action " + key);
        // Exercise the real mouse hitbox, not only an invocation of the stored callback.
        check(b.mouseClicked(b.getX() + b.getWidth()/2.0, b.getY() + b.getHeight()/2.0, 0), "Click not handled " + key);
    }
    private static void menu() { Minecraft.getInstance().setScreen(new TitleScreen()); }
    private static void controls() {
        var s = Minecraft.getInstance().screen;
        check(s instanceof SeasonTitleScreen, "Wrong title screen " + s);
        var widgets = s.children().stream().filter(c -> c instanceof AbstractWidget).map(c -> (AbstractWidget)c).toList();
        check(widgets.size() == 9, "Expected all 9 vanilla menu actions, found " + widgets.size());
        for (var a : widgets) {
            check(a.getX() >= 0 && a.getY() >= 0 && a.getX()+a.getWidth() <= s.width && a.getY()+a.getHeight() <= s.height - 21,
                    "Control outside the screen: " + a.getMessage().getString());
            for (var b : widgets) if (a != b) check(a.getX()+a.getWidth() <= b.getX() || b.getX()+b.getWidth() <= a.getX() ||
                    a.getY()+a.getHeight() <= b.getY() || b.getY()+b.getHeight() <= a.getY(), "Overlapping controls");
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (done || LoadingPresentation.frames < 2) return;
        try {
            if (!initialLoading && LoadingPresentation.lastKind.equals("neoforge")) { capture("01-startup-loading"); initialLoading = true; }
            if (step == 13 && !reloadLoading && LoadingPresentation.lastKind.equals("minecraft") && Minecraft.getInstance().getOverlay() != null) {
                capture("04-resource-loading-pt"); reloadLoading = true;
            }
            if (step == 14 && compactReloadStarted && !compactLoading && LoadingPresentation.lastKind.equals("minecraft") && Minecraft.getInstance().getOverlay() != null) {
                capture("07-resource-loading-compact"); compactLoading = true;
            }
        } catch (Exception error) { finish(false, error.toString()); }
    }
    private static void finish(boolean ok, String message) {
        done = true;
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("result.json"), new com.google.gson.Gson().toJson(Map.of(
                    "success",ok,"step",step,"message",message,"startup_loading",initialLoading,"resource_loading",reloadLoading,
                    "native_icon",WindowBranding.installed,"loading_frames",LoadingPresentation.frames,
                    "integration",net.neoforged.fml.ModList.get().isLoaded("cronicasrp"),"compact_loading",compactLoading)));
        } catch (Exception error) { System.err.println(error); }
        System.out.println("S8_QA " + ok + " " + message);
        Minecraft.getInstance().stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        var mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (now - START > 240000) { finish(false,"Timeout at step " + step); return; }
        if (now < next || mc.getOverlay() != null || mc.screen == null) return;
        if (mc.screen != rendered || renderFrames <= actionFrame) return;
        actionFrame = renderFrames; next = now + 700;
        try {
            switch(step) {
                case 0 -> {
                    if (mc.screen.getClass().getSimpleName().contains("Accessibility")) { click("gui.continue"); return; }
                    if (!(mc.screen instanceof SeasonTitleScreen)) return;
                    if (!mc.options.languageCode.equals("en_us")) {
                        mc.options.languageCode="en_us"; mc.getLanguageManager().setSelected("en_us"); mc.reloadResourcePacks(); return;
                    }
                    mc.options.guiScale().set(2); mc.resizeDisplay();
                    check(mc.getWindow().getWidth()==1600,"Wrong initial dimensions"); step++;
                }
                case 1 -> {
                    controls();
                    check(net.minecraft.client.resources.language.I18n.get("seasoneight.menu.credits").equals("Credits"),"English translation was not loaded");
                    var createTitle = Minecraft.class.getDeclaredMethod("createTitle"); createTitle.setAccessible(true);
                    check(createTitle.invoke(mc).equals("Season 8"),"Wrong window title");
                    check(WindowBranding.installed,"Native window icon was not installed on X11");
                    capture("02-main-menu-en"); click("menu.singleplayer"); step++;
                }
                case 2 -> {
                    if (mc.screen.getClass().getSimpleName().equals("GenericDirtMessageScreen")) return;
                    check(mc.screen.getClass().getSimpleName().equals("SelectWorldScreen") || mc.screen.getClass().getSimpleName().equals("CreateWorldScreen"),
                            "Singleplayer action failed: " + mc.screen.getClass().getSimpleName());
                    menu(); step++;
                }
                case 3 -> { click("fml.menu.mods"); step++; }
                case 4 -> { check(mc.screen.getClass().getSimpleName().equals("ModListScreen"),"Mods action failed"); menu(); step++; }
                case 5 -> { click("menu.options"); step++; }
                case 6 -> { check(mc.screen.getClass().getSimpleName().equals("OptionsScreen"),"Options action failed"); menu(); step++; }
                case 7 -> { click("options.language"); step++; }
                case 8 -> { check(mc.screen.getClass().getSimpleName().equals("LanguageSelectScreen"),"Language action failed"); menu(); step++; }
                case 9 -> { click("seasoneight.menu.accessibility"); step++; }
                case 10 -> { check(mc.screen.getClass().getSimpleName().equals("AccessibilityOptionsScreen"),"Accessibility action failed"); menu(); step++; }
                case 11 -> { click("seasoneight.menu.credits"); step++; }
                case 12 -> {
                    check(mc.screen.getClass().getSimpleName().equals("CreditsAndAttributionScreen"),"Credits action failed");
                    menu(); mc.options.languageCode="pt_pt"; mc.getLanguageManager().setSelected("pt_pt");
                    mc.reloadResourcePacks(); step++;
                }
                case 13 -> {
                    check(reloadLoading,"Resource reload overlay was never rendered");
                    check(net.minecraft.client.resources.language.I18n.get("seasoneight.menu.credits").equals("Créditos"),"Portuguese translation was not loaded");
                    controls(); capture("03-main-menu-pt");
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),854,480); mc.resizeDisplay(); step++;
                }
                case 14 -> {
                    controls(); check(mc.getWindow().getWidth()==854,"Compact width was not applied");
                    if (!compactReloadStarted) { compactReloadStarted=true; mc.reloadResourcePacks(); return; }
                    check(compactLoading,"Compact loading overlay was not rendered");
                    capture("05-main-menu-compact");
                    SeasonConfig.MOTION.set(false); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),640,360); mc.resizeDisplay(); step++;
                }
                case 15 -> {
                    controls(); capture("06-main-menu-small-reduced-motion");
                    if (mc.allowsMultiplayer()) { click("menu.multiplayer"); step++; }
                    else { step = 17; menu(); }
                }
                case 16 -> {
                    check(mc.screen.getClass().getSimpleName().equals("SafetyScreen") || mc.screen.getClass().getSimpleName().equals("JoinMultiplayerScreen"),"Multiplayer safety/action failed");
                    menu(); step++;
                }
                case 17 -> {
                    check(initialLoading,"Initial NeoForge loading overlay was never captured");
                    SeasonConfig.MENU.set(false); menu(); step++;
                }
                case 18 -> {
                    check(mc.screen.getClass()==TitleScreen.class,"Disabling the custom menu failed");
                    SeasonConfig.MENU.set(true); SeasonConfig.MOTION.set(true);
                    menu(); controls();
                    finish(true,"Both real loading paths, nine preserved actions, six navigation targets, account-aware multiplayer, EN/PT, three dimensions, reduced motion and vanilla fallback verified");
                }
            }
        } catch (Throwable error) { error.printStackTrace(); finish(false,error.toString()); }
    }
}
