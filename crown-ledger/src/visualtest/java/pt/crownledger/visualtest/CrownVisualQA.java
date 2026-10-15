package pt.crownledger.visualtest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import pt.crownledger.client.ClientHooks;
import pt.crownledger.client.TreasuryScreen;

/** Optional real-client model/menu smoke test. Demonstration data; excluded from release JARs. */
@EventBusSubscriber(modid = "kingdomtreasury", value = Dist.CLIENT)
public final class CrownVisualQA {
    private static final Path OUT = Path.of(System.getProperty("crown.qaOutput", "qa-results/crown-client")).toAbsolutePath().normalize();
    private static final String[] FILENAMES = {"crown-chest-minecraft.png", "crown-ledger-overview.png", "crown-ledger-policy.png"};
    private static final long STARTED = System.currentTimeMillis();
    private static final List<String> CAPTURED = new ArrayList<>();
    private static ItemStack stack;
    private static Screen captureScreen;
    private static int index, ticks, itemQuads, states;
    private static long screenStarted;
    private static boolean done, languageRequested;

    private CrownVisualQA() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() - STARTED > 180_000L) throw new IllegalStateException("Crown visual QA timed out");
            if (mc.getOverlay() != null) return;
            if (captureScreen == null) {
                if (!(mc.screen instanceof TitleScreen)) { continueAccessibility(mc.screen); return; }
                if (!languageRequested) {
                    languageRequested = true;
                    if (!"en_us".equals(mc.options.languageCode)) {
                        mc.getLanguageManager().setSelected("en_us"); mc.options.languageCode = "en_us"; mc.reloadResourcePacks(); return;
                    }
                }
                mc.getWindow().setWindowed(1600, 900); mc.options.guiScale().set(2); mc.resizeDisplay();
                validateModels(mc); show(mc); return;
            }
            if (mc.screen != captureScreen) throw new IllegalStateException("Unexpected screen during Crown visual QA");
            ticks++;
            if (ticks < 30 || System.currentTimeMillis() - screenStarted < 2_000L
                    || captureScreen instanceof ChestPreview preview && preview.frames < 30) return;
            Files.createDirectories(OUT);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth() >= 1200 && image.getHeight() >= 680, "Screenshot window is too small");
                image.writeToFile(OUT.resolve(FILENAMES[index]));
            }
            CAPTURED.add(FILENAMES[index]); index++;
            if (index < FILENAMES.length) show(mc); else complete(true, "Registered treasury chest, all placed states and production public/governor menus render with valid models and textures.");
        } catch (Throwable failure) { failure.printStackTrace(); complete(false, failure.toString()); }
    }

    private static void show(Minecraft mc) {
        if (index == 0) mc.setScreen(new ChestPreview());
        else if (index == 1) {
            validateAccess(mc);
            ClientHooks.open(demo(true).toString());
            check(mc.screen instanceof TreasuryScreen, "Production treasury menu did not open");
        } else {
            check(mc.screen instanceof TreasuryScreen, "Expected treasury before opening kingdom rules");
            check(press(mc.screen, "gui.kingdomtreasury.policy"), "Governor could not open kingdom rules");
        }
        captureScreen = mc.screen;
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1, 1);
        ticks = 0; screenStarted = System.currentTimeMillis();
    }

    private static void validateAccess(Minecraft mc) {
        Screen previous = mc.screen;
        ClientHooks.open(demo(false).toString());
        check(mc.screen instanceof TreasuryScreen, "Public players must be able to inspect the treasury");
        check(!hasButton(mc.screen, "gui.kingdomtreasury.policy"), "Public players must not receive governor controls");
        check(!hasButton(mc.screen, "gui.kingdomtreasury.contracts"), "Public players must not receive public-contract management controls");
        ClientHooks.open("{\"close\":true}");
        check(!(mc.screen instanceof TreasuryScreen), "Expired physical session must close the treasury menu");
        mc.setScreen(previous); ClientHooks.open("{\"close\":true}");
        check(mc.screen == previous, "Treasury session close must leave unrelated screens open");
    }

    private static boolean hasButton(Screen screen, String key) {
        for (var child : screen.children()) if (child instanceof AbstractButton button
                && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key)) return true;
        return false;
    }
    private static boolean press(Screen screen, String key) {
        for (var child : List.copyOf(screen.children())) if (child instanceof AbstractButton button && button.active
                && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key)) { button.onPress(); return true; }
        return false;
    }

    private static JsonObject demo(boolean manager) {
        var data = new JsonObject();
        data.addProperty("valid", true); data.addProperty("manager", manager); data.addProperty("managerName", "Queen Elowen");
        data.addProperty("currency", "EMERALD"); data.addProperty("playerBalance", 128);
        data.addProperty("salaryNext", 1_792_080_000_000L); data.addProperty("taxDue", 0);
        data.addProperty("pendingPayroll", false); data.addProperty("pendingPayrollCost", 0);
        var balances = new JsonObject(); balances.addProperty("EMERALD", 960); balances.addProperty("CHRONICLES", 12_500); data.add("balances", balances);
        var credits = new JsonObject(); credits.addProperty("EMERALD", 15); credits.addProperty("CHRONICLES", 0); data.add("credits", credits);
        var policy = new JsonObject();
        policy.addProperty("currency", "EMERALD"); policy.addProperty("taxMode", "PERCENTAGE"); policy.addProperty("taxBasisPoints", 500);
        policy.addProperty("fixedTax", 10); policy.addProperty("salary", 15); policy.addProperty("periodHours", 168); policy.addProperty("activeMinutes", 30);
        policy.addProperty("minimumReputation", 20); policy.addProperty("reputationReward", 1); policy.addProperty("dailyReputationLimit", 3); data.add("policy", policy);
        data.add("contracts", new com.google.gson.JsonArray()); data.add("history", new com.google.gson.JsonArray());
        return data;
    }

    private static void validateModels(Minecraft mc) {
        var key = ResourceLocation.fromNamespaceAndPath("kingdomtreasury", "crown_chest");
        var item = BuiltInRegistries.ITEM.get(key); check(item != Items.AIR, "Treasury chest item is not registered"); stack = new ItemStack(item);
        var model = mc.getItemRenderer().getModel(stack, null, null, 0);
        check(!model.isCustomRenderer(), "Expected JSON item model");
        check(!model.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing item particle sprite");
        var faces = new ArrayList<BakedQuad>(); faces.addAll(model.getQuads(null, null, RandomSource.create(0)));
        for (var side : Direction.values()) faces.addAll(model.getQuads(null, side, RandomSource.create(0)));
        validateFaces(faces, "treasury item"); itemQuads = faces.size();
        var block = BuiltInRegistries.BLOCK.get(key);
        for (var state : block.getStateDefinition().getPossibleStates()) {
            var placed = mc.getBlockRenderer().getBlockModel(state);
            check(!placed.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing block particle: " + state);
            var quads = new ArrayList<BakedQuad>(); quads.addAll(placed.getQuads(state, null, RandomSource.create(0)));
            for (var side : Direction.values()) quads.addAll(placed.getQuads(state, side, RandomSource.create(0)));
            validateFaces(quads, state.toString()); states++;
        }
        check(states >= 4, "Treasury chest must have four directional states");
    }
    private static void validateFaces(List<BakedQuad> faces, String name) {
        check(!faces.isEmpty(), "No model geometry: " + name);
        for (var face : faces) check(!face.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing texture: " + name);
    }
    private static void continueAccessibility(Screen screen) {
        if (screen == null || !screen.getClass().getSimpleName().contains("Accessibility")) return;
        for (var child : List.copyOf(screen.children())) if (child instanceof AbstractButton button && button.active
                && (button.getMessage().getString().equals("Continue") || button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals("gui.continue"))) { button.onPress(); return; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void complete(boolean success, String message) {
        done = true;
        try {
            Files.createDirectories(OUT);
            var report = new LinkedHashMap<String, Object>(); report.put("success", success); report.put("message", message);
            report.put("screenshot", FILENAMES[0]); report.put("screenshots", List.copyOf(CAPTURED)); report.put("images", List.copyOf(CAPTURED));
            report.put("interfaceData", "Demonstration data in the actual production TreasuryScreen");
            report.put("bakedQuadCounts", Map.of("kingdomtreasury:crown_chest", itemQuads)); report.put("validatedBlockStates", states);
            report.put("windowWidth", Minecraft.getInstance().getWindow().getWidth()); report.put("windowHeight", Minecraft.getInstance().getWindow().getHeight());
            Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { throw new IllegalStateException("Could not save Crown client visual QA", failure); }
        System.out.println("CROWN_VISUAL_QA success=" + success + " output=" + OUT + " " + message);
        Minecraft.getInstance().stop();
    }

    private static final class ChestPreview extends Screen {
        int frames;
        ChestPreview() { super(Component.literal("Crown Ledger")); }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {}
        @Override public void render(GuiGraphics g, int mx, int my, float tick) {
            g.fill(0, 0, width, height, 0xFF1D2822); g.fill(36, 24, width - 36, 25, 0xFF8C7549); g.fill(36, height - 25, width - 36, height - 24, 0xFF8C7549);
            g.drawCenteredString(font, getTitle(), width / 2, 42, 0xFFF1DFB7);
            g.drawCenteredString(font, "A public treasury for your kingdom", width / 2, 60, 0xFFB2B9A4);
            int scale = 15;
            g.pose().pushPose(); g.pose().translate(width / 2 - scale * 8, height / 2 - 125, 0); g.pose().scale(scale, scale, scale); g.renderItem(stack, 0, 0); g.pose().popPose();
            g.drawCenteredString(font, "Royal treasury chest", width / 2, height - 87, 0xFFF1DFB7);
            g.drawCenteredString(font, "Minecraft 1.21.1 · NeoForge · In-game model rendering", width / 2, height - 49, 0xFF94A18E);
            frames++;
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
