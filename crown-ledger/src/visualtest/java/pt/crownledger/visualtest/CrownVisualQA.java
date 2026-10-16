package pt.crownledger.visualtest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
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
    private static final List<String> CURRENCY_CHECKS = new ArrayList<>();
    private static final List<Map<String, Object>> UI_REQUESTS = new ArrayList<>();
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
                validateModels(mc); validateCurrencyInteractions(mc); show(mc); return;
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
            if (index < FILENAMES.length) show(mc); else complete(true, "Registered treasury chest, all placed states and production public/governor menus render with valid models and textures; native currency controls and their request payloads passed.");
        } catch (Throwable failure) { failure.printStackTrace(); complete(false, failure.toString()); }
    }

    private static void show(Minecraft mc) {
        if (index == 0) mc.setScreen(new ChestPreview());
        else if (index == 1) {
            validateAccess(mc);
            openDemo(mc, demo(true));
        } else {
            check(mc.screen instanceof TreasuryScreen, "Expected treasury before opening kingdom rules");
            check(press(mc.screen, "gui.kingdomtreasury.policy"), "Governor could not open kingdom rules");
            check(((TreasuryScreen) mc.screen).children().stream().anyMatch(child -> child instanceof EditBox), "Governor tax controls must be visible");
        }
        captureScreen = mc.screen;
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1, 1);
        ticks = 0; screenStarted = System.currentTimeMillis();
    }

    private static void validateAccess(Minecraft mc) {
        Screen previous = mc.screen;
        openDemo(mc, demo(false));
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

    private static void validateCurrencyInteractions(Minecraft mc) throws ReflectiveOperationException {
        JsonObject chron = currencyDemo("CHRONICLES", "EMERALD", true);
        TreasuryScreen screen = openDemo(mc, chron);
        click(screen, "contribute"); selector(screen, "CHRONICLES", true); expectWallet(screen, 7_650);
        setField(screen, "donation", "1.25");
        assertMoney(request(screen, "donate", "donate"), "CHRONICLES", "amount", 125);
        acknowledge(mc, screen, chron);
        click(screen, "currency_chronicles"); selector(screen, "EMERALD", true); expectWallet(screen, 128);
        expectField(screen, "donation", "0");
        setField(screen, "donation", "7");
        assertMoney(request(screen, "donate", "donate"), "EMERALD", "amount", 7);
        acknowledge(mc, screen, chron);
        setField(screen, "donation", "1.25");
        int before = UI_REQUESTS.size(); click(screen, "donate");
        check(UI_REQUESTS.size() == before, "Fractional emerald donation must not send a request");
        click(screen, "currency_emerald"); selector(screen, "CHRONICLES", true);
        expectField(screen, "donation", "0");
        assertCurrency(request(screen, "claim_emeralds", "claim"), "EMERALD"); acknowledge(mc, screen, chron);
        assertCurrency(request(screen, "claim_coins", "claim"), "CHRONICLES"); acknowledge(mc, screen, chron);
        CURRENCY_CHECKS.add("Donation defaults to the active available currency, switches both ways, clears amounts, parses Chronicle cents, rejects fractional emeralds, and preserves both credit claims.");

        JsonObject policy = currencyDemo("EMERALD", "CHRONICLES", true);
        screen = openDemo(mc, policy); click(screen, "policy");
        expectField(screen, "fixedTax", "1.25"); expectField(screen, "salary", "2.50");
        expectField(screen, "marketTaxPercent", "5");
        click(screen, "participation_page"); selector(screen, "CHRONICLES", true);
        click(screen, "currency_chronicles"); selector(screen, "EMERALD", true);
        click(screen, "economy_page"); expectField(screen, "fixedTax", "0"); expectField(screen, "salary", "0");
        expectField(screen, "marketTaxPercent", "5");
        setField(screen, "fixedTax", "9"); setField(screen, "salary", "12");
        click(screen, "participation_page"); click(screen, "currency_emerald"); selector(screen, "CHRONICLES", true);
        click(screen, "economy_page"); expectField(screen, "fixedTax", "0"); expectField(screen, "salary", "0");
        expectField(screen, "marketTaxPercent", "5");
        setField(screen, "fixedTax", "1.25"); setField(screen, "salary", "2.50");
        JsonObject saved = request(screen, "save_policy", "policy");
        assertMoney(saved, "CHRONICLES", "fixedTax", 125); assertMoney(saved, "CHRONICLES", "salary", 250);
        check(saved.get("marketTaxBasisPoints").getAsInt() == 500, "Market sales tax must retain 500 basis points across both currency changes");
        acknowledge(mc, screen, policy);
        click(screen, "contribute");
        check(button(screen, "claim_emeralds").active && button(screen, "claim_coins").active,
                "Changing the policy currency must preserve existing credits in both currencies");
        CURRENCY_CHECKS.add("Policy currency clears fixed tax and salary, preserves independent market tax at 500 basis points and historical credits, and saves Chronicle amounts as cents.");

        screen = openDemo(mc, policy); click(screen, "policy");
        click(screen, "mode_percentage"); // mode remains independent of the market sales tax
        setField(screen, "marketTaxPercent", "5.25");
        saved = request(screen, "save_policy", "policy");
        check(saved.get("marketTaxBasisPoints").getAsInt() == 525 && "FIXED".equals(saved.get("taxMode").getAsString()), "Market tax must parse exact basis points independently of fixed periodic mode");
        acknowledge(mc, screen, policy); setField(screen, "marketTaxPercent", "100.01");
        before = UI_REQUESTS.size(); click(screen, "save_policy");
        check(UI_REQUESTS.size() == before, "Out-of-range market tax must not send a request");
        setField(screen, "marketTaxPercent", "5");
        screen.init(mc, 320, 240);
        expectField(screen, "marketTaxPercent", "5");
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
            check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= 320 && widget.getY() + widget.getHeight() <= 240, "Market tax controls exceed the 320x240 viewport");
        screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        CURRENCY_CHECKS.add("The governor edits market tax independently of the periodic/contract mode, saves exact basis points, rejects rates above 100%, and retains visible controls at 320x240.");

        JsonObject unavailable = currencyDemo("CHRONICLES", "CHRONICLES", false);
        screen = openDemo(mc, unavailable); click(screen, "contribute"); selector(screen, "EMERALD", false);
        check(!button(screen, "claim_coins").active, "Chronicle claim must be disabled when the bridge is absent even with stored credit");
        before = UI_REQUESTS.size();
        check(!press(screen, "gui.kingdomtreasury.currency_emerald") && !press(screen, "gui.kingdomtreasury.claim_coins"),
                "Unavailable Chronicle controls must not accept a click");
        check(UI_REQUESTS.size() == before, "Unavailable Chronicle controls must not send requests");
        setField(screen, "donation", "13");
        assertMoney(request(screen, "donate", "donate"), "EMERALD", "amount", 13); acknowledge(mc, screen, unavailable);
        assertCurrency(request(screen, "claim_emeralds", "claim"), "EMERALD"); acknowledge(mc, screen, unavailable);
        click(screen, "policy");
        check(!button(screen, "save_policy").active, "Unavailable Chronicle policy must not save before recovery to emeralds");
        click(screen, "participation_page"); selector(screen, "CHRONICLES", true);
        click(screen, "currency_chronicles"); selector(screen, "EMERALD", false);
        click(screen, "economy_page"); expectField(screen, "fixedTax", "0"); expectField(screen, "salary", "0");
        expectField(screen, "marketTaxPercent", "5");
        setField(screen, "fixedTax", "4"); setField(screen, "salary", "8");
        saved = request(screen, "save_policy", "policy");
        assertMoney(saved, "EMERALD", "fixedTax", 4); assertMoney(saved, "EMERALD", "salary", 8);
        acknowledge(mc, screen, unavailable);
        CURRENCY_CHECKS.add("Without Chronicles, donations fall back to emeralds, Chronicle selection and claims are disabled, emerald claims remain available, and a saved Chronicle policy can recover to emeralds.");

        JsonObject contract = currencyDemo("EMERALD", "EMERALD", true); contract.addProperty("contractCurrency", "CHRONICLES");
        screen = openDemo(mc, contract); click(screen, "contracts"); click(screen, "create_public");
        setField(screen, "contractTitle", "Demonstration supply order"); setField(screen, "contractItem", "minecraft:oak_log");
        setField(screen, "contractQuantity", "64"); setField(screen, "contractReward", "3.45");
        assertMoney(request(screen, "publish", "publish"), "CHRONICLES", "reward", 345);
        acknowledge(mc, screen, contract);
        CURRENCY_CHECKS.add("Public contract reward parsing and the published currency use the contract currency independently of the active policy currency.");
        ClientHooks.open("{\"close\":true}");
    }

    private static TreasuryScreen openDemo(Minecraft mc, JsonObject data) {
        ClientHooks.open("{\"close\":true}");
        ClientHooks.open(data.toString());
        check(mc.screen instanceof TreasuryScreen, "Production treasury menu did not open");
        var screen = (TreasuryScreen) mc.screen;
        try {
            var sender = TreasuryScreen.class.getDeclaredField("requestSender"); sender.setAccessible(true);
            BiConsumer<String, JsonObject> recorder = (action, payload) -> {
                var recorded = new LinkedHashMap<String, Object>(); recorded.put("action", action); recorded.put("payload", payload.deepCopy());
                UI_REQUESTS.add(recorded);
            };
            sender.set(screen, recorder);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not isolate the production menu request transport", failure); }
        return screen;
    }
    private static void acknowledge(Minecraft mc, TreasuryScreen screen, JsonObject fixture) {
        var reply = fixture.deepCopy(); reply.addProperty("success", true); ClientHooks.open(reply.toString());
        check(mc.screen == screen, "Demonstration response replaced the active production screen");
    }
    private static void click(Screen screen, String suffix) {
        check(press(screen, "gui.kingdomtreasury." + suffix), "Could not press native treasury button: " + suffix);
    }
    private static AbstractButton button(Screen screen, String suffix) {
        String key = "gui.kingdomtreasury." + suffix;
        for (var child : screen.children()) if (child instanceof AbstractButton button
                && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key)) return button;
        throw new IllegalStateException("Missing native treasury button: " + suffix);
    }
    private static void selector(Screen screen, String currency, boolean active) {
        var selector = button(screen, "CHRONICLES".equals(currency) ? "currency_chronicles" : "currency_emerald");
        check(selector.active == active, "Incorrect currency selector availability for " + currency);
    }
    private static EditBox field(TreasuryScreen screen, String key) throws ReflectiveOperationException {
        var declared = TreasuryScreen.class.getDeclaredField("fields"); declared.setAccessible(true);
        Object value = ((Map<?, ?>) declared.get(screen)).get(key);
        check(value instanceof EditBox, "Missing production monetary field: " + key);
        return (EditBox) value;
    }
    private static void setField(TreasuryScreen screen, String key, String value) throws ReflectiveOperationException {
        field(screen, key).setValue(value); expectField(screen, key, value);
    }
    private static void expectField(TreasuryScreen screen, String key, String expected) throws ReflectiveOperationException {
        check(expected.equals(field(screen, key).getValue()), "Unexpected " + key + " amount; expected " + expected);
    }
    private static void expectWallet(TreasuryScreen screen, long expected) throws ReflectiveOperationException {
        var balance = TreasuryScreen.class.getDeclaredMethod("donationBalance"); balance.setAccessible(true);
        check(((Long) balance.invoke(screen)) == expected, "Donation balance did not follow the selected currency wallet");
    }
    private static JsonObject request(TreasuryScreen screen, String button, String action) {
        int previous = UI_REQUESTS.size(); click(screen, button);
        check(UI_REQUESTS.size() == previous + 1, "Native " + button + " button did not send exactly one request");
        var request = UI_REQUESTS.get(previous); check(action.equals(request.get("action")), "Unexpected action from " + button);
        return (JsonObject) request.get("payload");
    }
    private static void assertCurrency(JsonObject payload, String currency) {
        check(payload.has("currency") && currency.equals(payload.get("currency").getAsString()), "Wrong currency in native menu request: " + payload);
    }
    private static void assertMoney(JsonObject payload, String currency, String field, long amount) {
        assertCurrency(payload, currency);
        check(payload.has(field) && payload.get(field).getAsLong() == amount, "Wrong " + field + " in native menu request: " + payload);
    }
    private static JsonObject currencyDemo(String active, String policyCurrency, boolean chronLoaded) {
        var data = demo(true); data.addProperty("currency", policyCurrency); data.addProperty("activeCurrency", active); data.addProperty("chroniclesLoaded", chronLoaded);
        data.getAsJsonObject("policy").addProperty("currency", policyCurrency);
        data.getAsJsonObject("policy").addProperty("fixedTax", 125); data.getAsJsonObject("policy").addProperty("salary", 250);
        data.getAsJsonObject("credits").addProperty("CHRONICLES", 350);
        if (!chronLoaded) data.getAsJsonObject("wallets").addProperty("CHRONICLES", -1);
        return data;
    }

    private static JsonObject demo(boolean manager) {
        var data = new JsonObject();
        data.addProperty("valid", true); data.addProperty("manager", manager); data.addProperty("managerName", "Queen Elowen");
        data.addProperty("currency", "EMERALD"); data.addProperty("playerBalance", 128); data.addProperty("marketsAvailable", true);
        data.addProperty("activeCurrency", "EMERALD"); data.addProperty("chroniclesLoaded", true); data.addProperty("contractCurrency", "EMERALD");
        var wallets = new JsonObject(); wallets.addProperty("EMERALD", 128); wallets.addProperty("CHRONICLES", 7_650); data.add("wallets", wallets);
        data.addProperty("salaryNext", 1_792_080_000_000L); data.addProperty("taxDue", 0);
        data.addProperty("pendingPayroll", false); data.addProperty("pendingPayrollCost", 0);
        var balances = new JsonObject(); balances.addProperty("EMERALD", 960); balances.addProperty("CHRONICLES", 12_500); data.add("balances", balances);
        var credits = new JsonObject(); credits.addProperty("EMERALD", 15); credits.addProperty("CHRONICLES", 0); data.add("credits", credits);
        var policy = new JsonObject();
        policy.addProperty("currency", "EMERALD"); policy.addProperty("taxMode", "PERCENTAGE"); policy.addProperty("taxBasisPoints", 500); policy.addProperty("marketTaxBasisPoints", 500);
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
            report.put("currencyInteractionChecks", List.copyOf(CURRENCY_CHECKS)); report.put("recordedUiRequests", List.copyOf(UI_REQUESTS));
            report.put("requestTransport", "Recorded locally; no game-server network requests"); report.put("policyScreenshotPage", "economy: independent market sales tax at 5%, contract mode and salaries");
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
