package pt.realmmarkets.visualtest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import pt.realmmarkets.client.MarketScreen;
import pt.realmmarkets.domain.SaleAmounts;

/** Opt-in actual-client rendering and production menu QA; never included in release JARs. */
@EventBusSubscriber(modid = "realmmarkets", value = Dist.CLIENT)
public final class MarketVisualQA {
    private static final Path OUT = Path.of(System.getProperty("markets.qaOutput", "qa-results/markets-client")).toAbsolutePath().normalize();
    private static final String[] FILENAMES = {"realm-markets-stalls.png", "realm-markets-owner.png", "realm-markets-buying.png"};
    private static final long STARTED = System.currentTimeMillis();
    private static final List<String> CAPTURED = new ArrayList<>(), CHECKS = new ArrayList<>();
    private static final List<Map<String, Object>> REQUESTS = new ArrayList<>();
    private static final Map<String, Integer> QUADS = new LinkedHashMap<>();
    private static List<ItemStack> stacks;
    private static Screen captureScreen;
    private static int index, ticks, states;
    private static long screenStarted;
    private static boolean done, languageRequested;

    private MarketVisualQA() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() - STARTED > 180_000L) throw new IllegalStateException("Markets visual QA timed out");
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
                validateModels(mc); validateInterface(mc); show(mc); return;
            }
            check(mc.screen == captureScreen, "Unexpected screen during Markets visual QA");
            ticks++;
            if (ticks < 30 || System.currentTimeMillis() - screenStarted < 2_000L
                    || captureScreen instanceof StallPreview preview && preview.frames < 30) return;
            Files.createDirectories(OUT);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth() == 1600 && image.getHeight() == 900, "Expected a 1600x900 framebuffer");
                image.writeToFile(OUT.resolve(FILENAMES[index]));
            }
            CAPTURED.add(FILENAMES[index]); index++;
            if (index < FILENAMES.length) show(mc);
            else complete(true, "Two complete native stall models, all 32 placed states, production owner/buyer screens, currency parsing, quote payloads and small-screen scrolling passed.");
        } catch (Throwable failure) { failure.printStackTrace(); complete(false, failure.toString()); }
    }

    private static void show(Minecraft mc) {
        if (index == 0) mc.setScreen(new StallPreview());
        else {
            MarketScreen screen = open(mc, demo(index == 1, "CHRONICLES", true));
            if (index == 2) {
                try { field(screen, "quantityInput", "16"); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }
        }
        captureScreen = mc.screen;
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1, 1);
        ticks = 0; screenStarted = System.currentTimeMillis();
    }

    private static MarketScreen open(Minecraft mc, JsonObject data) {
        MarketScreen.receive("{}"); MarketScreen.receive(data.toString());
        check(mc.screen instanceof MarketScreen, "Production MarketScreen did not open");
        MarketScreen screen = (MarketScreen) mc.screen;
        try {
            var sender = MarketScreen.class.getDeclaredField("requestSender"); sender.setAccessible(true);
            BiConsumer<String, JsonObject> recorder = (operation, payload) -> {
                var entry = new LinkedHashMap<String, Object>(); entry.put("operation", operation); entry.put("payload", payload.deepCopy()); REQUESTS.add(entry);
            };
            sender.set(screen, recorder);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not isolate native request transport", failure); }
        return screen;
    }

    private static void acknowledge(MarketScreen screen, JsonObject data) {
        MarketScreen.receive(data.toString());
        check(Minecraft.getInstance().screen == screen, "A response replaced the production menu");
    }

    private static AbstractButton button(Screen screen, String key) {
        for (var child : screen.children()) if (child instanceof AbstractButton button
                && button.getMessage().getContents() instanceof TranslatableContents text
                && text.getKey().equals("gui.realmmarkets." + key)) return button;
        throw new IllegalStateException("Missing native button: " + key);
    }

    private static boolean hasButton(Screen screen, String key) {
        for (var child : screen.children()) if (child instanceof AbstractButton button
                && button.getMessage().getContents() instanceof TranslatableContents text
                && text.getKey().equals("gui.realmmarkets." + key)) return true;
        return false;
    }

    private static void reveal(Screen screen, AbstractWidget widget) {
        // Real production scrolling must make even the last owner action reachable.
        for (int i = 0; !widget.visible && i < 60; i++) screen.mouseScrolled(screen.width / 2., screen.height / 2., 0, -1);
        if (!widget.visible) for (int i = 0; !widget.visible && i < 60; i++) screen.mouseScrolled(screen.width / 2., screen.height / 2., 0, 1);
        check(widget.visible, "Native control cannot be reached by scrolling: " + widget.getMessage().getString());
    }

    private static JsonObject request(MarketScreen screen, String control, String operation) {
        int previous = REQUESTS.size(); var button = button(screen, control);
        reveal(screen, button); check(button.active, "Inactive native control: " + control); button.onPress();
        check(REQUESTS.size() == previous + 1, "Native control must send exactly one request: " + control);
        var request = REQUESTS.get(previous);
        check(operation.equals(request.get("operation")), "Unexpected market operation: " + request);
        return (JsonObject) request.get("payload");
    }

    private static void field(MarketScreen screen, String name, String value) throws ReflectiveOperationException {
        var field = MarketScreen.class.getDeclaredField(name); field.setAccessible(true);
        ((EditBox) field.get(screen)).setValue(value);
    }

    private static void validateInterface(Minecraft mc) throws ReflectiveOperationException {
        JsonObject owner = demo(true, "CHRONICLES", true);
        MarketScreen screen = open(mc, owner);
        check(!hasButton(screen, "buy"), "Owners must not receive the buyer purchase action");
        field(screen, "priceInput", "1.25");
        JsonObject payload = request(screen, "configure", "configure");
        check(payload.size() == 2 && payload.get("slot").getAsInt() == 0 && payload.get("price").getAsLong() == 125,
                "Configure must send only the selected slot and exact integer cents");
        acknowledge(screen, owner); field(screen, "priceInput", "1.257");
        check(!button(screen, "configure").active, "Fractional cents must not enable configuration");
        field(screen, "priceInput", "1,25");
        check(button(screen, "configure").active, "Decimal comma must be accepted for Chronicles prices");
        field(screen, "quantityInput", "32"); payload = request(screen, "stock", "stock");
        check(payload.size() == 2 && payload.get("slot").getAsInt() == 0 && payload.get("quantity").getAsInt() == 32,
                "Stock must send only the selected slot and whole quantity");
        acknowledge(screen, owner); field(screen, "quantityInput", "7"); payload = request(screen, "withdraw", "withdraw");
        check(payload.size() == 2 && payload.get("quantity").getAsInt() == 7, "Withdrawal must send an exact item quantity");
        acknowledge(screen, owner); field(screen, "titleInput", "Aveline's Orchard"); payload = request(screen, "save_name", "rename");
        check(payload.size() == 1 && "Aveline's Orchard".equals(payload.get("title").getAsString()), "Rename must not send ownership or financial fields");
        acknowledge(screen, owner); payload = request(screen, "claim", "claim");
        check(payload.isEmpty(), "Owner earnings claim must send no client amount"); acknowledge(screen, owner);
        int before = REQUESTS.size(); for (int tick = 0; tick < 100; tick++) screen.tick();
        check(REQUESTS.size() == before + 1 && "refresh".equals(REQUESTS.get(before).get("operation"))
                && ((JsonObject) REQUESTS.get(before).get("payload")).isEmpty(), "Physical menu must refresh with an empty payload after 100 ticks");
        CHECKS.add("Production owner buttons send exact integer-cents pricing, whole stock/withdraw quantities, a title-only rename and an amount-free earnings claim; 100-tick refresh carries no client state.");

        JsonObject buyer = demo(false, "CHRONICLES", true);
        screen = open(mc, buyer);
        for (String key : List.of("configure", "stock", "withdraw", "claim", "save_name"))
            check(!hasButton(screen, key), "Buyers must not receive owner control: " + key);
        field(screen, "quantityInput", "3"); payload = request(screen, "buy", "buy");
        check(payload.size() == 5 && payload.get("slot").getAsInt() == 0 && payload.get("quantity").getAsInt() == 3
                && payload.get("marketTaxBasisPoints").getAsInt() == 500
                && payload.get("price").getAsLong() == 125 && "minecraft:apple".equals(payload.get("item").getAsString()),
                "Purchase must quote the displayed item, unit price and current 500 basis-point market tax, with an integer quantity");
        acknowledge(screen, buyer); field(screen, "quantityInput", "16");
        expectQuote(screen, 2000, 100, 1900);
        expectMoney(screen, 2000, "€20.00"); expectMoney(screen, 100, "€1.00"); expectMoney(screen, 1900, "€19.00");
        var changedTax = buyer.deepCopy(); changedTax.addProperty("marketTaxBasisPoints", 625); acknowledge(screen, changedTax);
        payload = request(screen, "buy", "buy");
        check(payload.get("marketTaxBasisPoints").getAsInt() == 625, "A refreshed tax rule must replace the quoted rate");
        acknowledge(screen, buyer); field(screen, "quantityInput", "9999");
        check(!button(screen, "buy").active, "Insufficient stock must disable purchases");
        field(screen, "quantityInput", "1.5"); check(!button(screen, "buy").active, "Fractional item quantities must be rejected");
        var poor = buyer.deepCopy(); poor.addProperty("balance", 124); acknowledge(screen, poor);
        field(screen, "quantityInput", "1"); check(!button(screen, "buy").active, "Insufficient wallet must disable purchases");
        var unavailable = demo(false, "CHRONICLES", false); acknowledge(screen, unavailable);
        check(!button(screen, "buy").active, "Unavailable Chronicles profile must disable digital purchases");
        CHECKS.add("Production buyer controls quote the displayed item, price and tax rate. A €20.00 purchase at 5% displays €1.00 tax and €19.00 seller net; refreshed rules update the sent rate. Stock, quantities, funds and unavailable Chronicles checks remain active.");

        JsonObject paused = demo(false, "EMERALD", false); paused.addProperty("treasuryAvailable", false);
        screen = open(mc, paused); check(!button(screen, "buy").active, "Unavailable tax provider must disable purchases");
        paused.addProperty("owner", true); acknowledge(screen, paused);
        check(button(screen, "withdraw").active && button(screen, "claim").active, "Tax outage must preserve stock withdrawal and earnings claims");
        payload = request(screen, "claim", "claim"); check(payload.isEmpty(), "Claims must never quote or recalculate tax");
        CHECKS.add("An unavailable tax provider pauses purchases while owner stock withdrawal and amount-free net earnings claims stay usable.");

        JsonObject emerald = demo(true, "EMERALD", false);
        screen = open(mc, emerald); field(screen, "quantityInput", "10");
        expectQuote(screen, 20, 1, 19);
        expectMoney(screen, 20, "20 emeralds"); expectMoney(screen, 1, "1 emeralds"); expectMoney(screen, 19, "19 emeralds");
        field(screen, "quantityInput", "1"); expectQuote(screen, 2, 0, 2);
        var untaxed = emerald.deepCopy(); untaxed.addProperty("marketTaxBasisPoints", 0); acknowledge(screen, untaxed);
        expectQuote(screen, 2, 0, 2); acknowledge(screen, emerald);
        field(screen, "priceInput", "2.50");
        check(!button(screen, "configure").active, "Emerald prices must reject decimals");
        field(screen, "priceInput", "2"); payload = request(screen, "configure", "configure");
        check(payload.get("price").getAsLong() == 2, "Emerald prices must remain whole units");
        acknowledge(screen, emerald);
        screen.init(mc, 320, 240);
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
            check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= 320 && widget.getY() + widget.getHeight() <= 240,
                    "Visible control exceeds a 320x240 viewport");
        for (String key : List.of("stock", "withdraw", "configure", "save_name", "claim")) reveal(screen, button(screen, key));
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
            check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= 320 && widget.getY() + widget.getHeight() <= 240,
                    "Scrolled visible control exceeds a 320x240 viewport");
        screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        MarketScreen.receive("{}"); check(!(mc.screen instanceof MarketScreen), "Expired physical session must close the market menu");
        Screen unrelated = new TitleScreen(); mc.setScreen(unrelated); MarketScreen.receive("{}");
        check(mc.screen == unrelated, "Expired market session must preserve unrelated screens");
        CHECKS.add("Whole emerald pricing: a 20-emerald total at 5% displays 1 tax and 19 seller net; a 2-emerald total rounds tax down to 0. Standalone 0% preserves gross earnings. All owner controls remain reachable by scrolling at 320x240; expiry closes only the market screen.");
    }

    private static void expectQuote(MarketScreen screen, long gross, long tax, long net) throws ReflectiveOperationException {
        var method = MarketScreen.class.getDeclaredMethod("saleQuote"); method.setAccessible(true);
        Object result = method.invoke(screen);
        check(result instanceof SaleAmounts, "Production UI has no valid tax quote");
        SaleAmounts amounts = (SaleAmounts) result;
        check(amounts.gross() == gross && amounts.tax() == tax && amounts.net() == net, "Production UI gross/tax/net mismatch: " + amounts);
    }

    private static void expectMoney(MarketScreen screen, long amount, String expected) throws ReflectiveOperationException {
        var method = MarketScreen.class.getDeclaredMethod("money", long.class); method.setAccessible(true);
        check(expected.equals(method.invoke(screen, amount)), "Production UI money label differs from " + expected);
    }

    private static JsonObject demo(boolean owner, String currency, boolean available) {
        var data = new JsonObject(); data.addProperty("open", true); data.addProperty("owner", owner);
        data.addProperty("title", "Aveline's Orchard · sample"); data.addProperty("currency", currency);
        data.addProperty("chroniclesAvailable", available); data.addProperty("balance", "CHRONICLES".equals(currency) ? 18750 : 128);
        data.addProperty("earnings", "CHRONICLES".equals(currency) ? 3275 : 48);
        data.addProperty("treasuryAvailable", true); data.addProperty("marketTaxBasisPoints", 500);
        data.addProperty("sample", "minecraft:apple"); data.addProperty("message", "gui.realmmarkets.qa_sample");
        String[] items = {"apple", "bread", "carrot", "pumpkin", "wheat", "potato", "honey_bottle", "sweet_berries"};
        int[] stocks = {64, 32, 128, 12, 96, 80, 16, 48};
        int[] prices = {125, 200, 75, 350, 50, 75, 450, 100};
        var offers = new JsonArray();
        for (int slot = 0; slot < 8; slot++) {
            var offer = new JsonObject(); offer.addProperty("slot", slot); offer.addProperty("item", "minecraft:" + items[slot]);
            offer.addProperty("stock", stocks[slot]); offer.addProperty("price", "CHRONICLES".equals(currency) ? prices[slot] : slot + 2); offers.add(offer);
        }
        data.add("listings", offers); return data;
    }

    private static void validateModels(Minecraft mc) {
        var items = new ArrayList<ItemStack>();
        int[] expectedPartQuads = {450, 408, 396, 342};
        for (String id : List.of("crimson_stall", "sage_stall")) {
            var key = ResourceLocation.fromNamespaceAndPath("realmmarkets", id);
            var item = BuiltInRegistries.ITEM.get(key); check(item != Items.AIR, "Unregistered stall item: " + id);
            var stack = new ItemStack(item); items.add(stack);
            var model = mc.getItemRenderer().getModel(stack, null, null, 0);
            check(!model.isCustomRenderer(), "Expected a native JSON stall item model: " + id);
            check(!model.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing item particle: " + id);
            var faces = new ArrayList<BakedQuad>(); faces.addAll(model.getQuads(null, null, RandomSource.create(0)));
            for (var direction : Direction.values()) faces.addAll(model.getQuads(null, direction, RandomSource.create(0)));
            validateFaces(faces, id); check(faces.size() == 1308, "Complete stall item must retain all 218 original cuboids: " + id + "=" + faces.size());
            QUADS.put("realmmarkets:" + id, faces.size());
            var block = BuiltInRegistries.BLOCK.get(key); int blockStates = 0;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var placed = mc.getBlockRenderer().getBlockModel(state);
                check(!placed.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing placed particle: " + state);
                faces = new ArrayList<>(); faces.addAll(placed.getQuads(state, null, RandomSource.create(0)));
                for (var side : Direction.values()) faces.addAll(placed.getQuads(state, side, RandomSource.create(0)));
                validateFaces(faces, state.toString());
                int part = state.getValues().entrySet().stream().filter(entry -> entry.getKey().getName().equals("part"))
                        .mapToInt(entry -> Integer.parseInt(entry.getValue().toString())).findFirst().orElseThrow();
                check(faces.size() == expectedPartQuads[part], "Placed geometry differs for " + state + ": " + faces.size());
                QUADS.put(state.toString(), faces.size()); states++; blockStates++;
            }
            check(blockStates == 16, "Each stall must have four parts in each of four facings");
        }
        stacks = List.copyOf(items); check(states == 32, "Expected 32 native placed block states");
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
            report.put("screenshots", List.copyOf(CAPTURED)); report.put("images", List.copyOf(CAPTURED));
            report.put("interfaceData", "Clearly labelled sample stock and funds in the actual production MarketScreen; not a live Chronicles profile or a record of actual trades");
            report.put("stallRendering", "Actual registered Minecraft item models, with the same original geometry and textures as the four-part placed stalls");
            report.put("interfaceChecks", List.copyOf(CHECKS)); report.put("recordedUiRequests", List.copyOf(REQUESTS));
            report.put("requestTransport", "Recorded locally; no server requests"); report.put("bakedQuadCounts", QUADS); report.put("validatedBlockStates", states);
            report.put("windowWidth", Minecraft.getInstance().getWindow().getWidth()); report.put("windowHeight", Minecraft.getInstance().getWindow().getHeight());
            Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { throw new IllegalStateException("Could not save Markets client visual QA", failure); }
        System.out.println("MARKETS_VISUAL_QA success=" + success + " output=" + OUT + " " + message);
        Minecraft.getInstance().stop();
    }

    private static final class StallPreview extends Screen {
        int frames;
        StallPreview() { super(Component.literal("REALM MARKETS")); }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {}
        @Override public void render(GuiGraphics g, int mx, int my, float tick) {
            g.fill(0, 0, width, height, 0xFF18231D);
            g.fill(0, height * 2 / 3, width, height, 0xFF1C2921);
            g.fill(32, 25, width - 32, 26, 0xFFB39558);
            g.fill(32, height - 25, width - 32, height - 24, 0xFFB39558);
            g.drawCenteredString(font, getTitle(), width / 2, 43, 0xFFF1DFB7);
            g.drawCenteredString(font, "A place for every village merchant", width / 2, 63, 0xFFACBAA6);
            int scale = 13, top = height / 2 - 115;
            int[] centers = {width * 29 / 100, width * 71 / 100};
            for (int i = 0; i < stacks.size(); i++) {
                int center = centers[i];
                // A restrained display plinth; the model itself is rendered by Minecraft.
                g.fill(center - 113, top + 236, center + 113, top + 239, 0xFF2B392C);
                g.pose().pushPose(); g.pose().translate(center - scale * 8, top, 0); g.pose().scale(scale, scale, scale);
                g.renderItem(stacks.get(i), 0, 0); g.pose().popPose();
            }
            g.drawCenteredString(font, "Crimson canopy", centers[0], height - 89, 0xFFE5C797);
            g.drawCenteredString(font, "Sage canopy", centers[1], height - 89, 0xFFE5C797);
            g.drawCenteredString(font, "Carved oak · striped canvas · produce trays · brass details", width / 2, height - 65, 0xFFB0BBA5);
            g.drawCenteredString(font, "Minecraft 1.21.1 · Actual registered models · Original artwork", width / 2, height - 47, 0xFF839781);
            frames++;
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
