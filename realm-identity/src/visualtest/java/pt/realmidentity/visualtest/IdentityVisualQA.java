package pt.realmidentity.visualtest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
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
import pt.realmidentity.client.IdentityScreen;
import pt.realmidentity.domain.InkDrawing;

/** Opt-in real Minecraft client QA. Explicit sample identities; excluded from release JARs. */
@EventBusSubscriber(modid = "realmidentity", value = Dist.CLIENT)
public final class IdentityVisualQA {
    private static final Path OUT = Path.of(System.getProperty("identity.qaOutput", "qa-results/identity-client")).toAbsolutePath().normalize();
    private static final String[] FILENAMES = {"realm-identity-items.png", "realm-identity-document.png", "realm-identity-issuance.png", "realm-identity-honours.png"};
    private static final long STARTED = System.currentTimeMillis();
    private static final List<String> CAPTURED = new ArrayList<>();
    private static final List<String> CHECKS = new ArrayList<>();
    private static final List<Map<String, Object>> REQUESTS = new ArrayList<>();
    private static final Map<String, Integer> QUADS = new LinkedHashMap<>();
    private static List<ItemStack> stacks;
    private static Screen captureScreen;
    private static int index, ticks, states;
    private static long screenStarted;
    private static boolean done, languageRequested;

    private IdentityVisualQA() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() - STARTED > 180_000L) throw new IllegalStateException("Identity visual QA timed out");
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
            if (mc.screen != captureScreen) throw new IllegalStateException("Unexpected screen during Identity visual QA");
            ticks++;
            if (ticks < 30 || System.currentTimeMillis() - screenStarted < 2_000L
                    || captureScreen instanceof ItemPreview preview && preview.frames < 30) return;
            Files.createDirectories(OUT);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth() == 1600 && image.getHeight() == 900, "Expected a 1600x900 framebuffer");
                image.writeToFile(OUT.resolve(FILENAMES[index]));
            }
            CAPTURED.add(FILENAMES[index]); index++;
            if (index < FILENAMES.length) show(mc);
            else complete(true, "Desk and document models, four desk facings, the production passport/issuance screens, and native station controls passed.");
        } catch (Throwable failure) { failure.printStackTrace(); complete(false, failure.toString()); }
    }

    private static void show(Minecraft mc) {
        if (index == 0) mc.setScreen(new ItemPreview());
        else {
            var screen = open(mc, demo(index == 2));
            if (index == 3) {
                try {
                    var scroll = IdentityScreen.class.getDeclaredField("scroll"); scroll.setAccessible(true); scroll.setInt(screen, 170);
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }
        }
        captureScreen = mc.screen;
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1, 1);
        ticks = 0; screenStarted = System.currentTimeMillis();
    }

    private static IdentityScreen open(Minecraft mc, JsonObject data) {
        IdentityScreen.receive("{}"); IdentityScreen.receive(data.toString());
        check(mc.screen instanceof IdentityScreen, "Production IdentityScreen did not open");
        IdentityScreen screen = (IdentityScreen) mc.screen;
        try {
            var sender = IdentityScreen.class.getDeclaredField("requestSender"); sender.setAccessible(true);
            BiConsumer<String, JsonObject> recorder = (operation, payload) -> {
                var entry = new LinkedHashMap<String, Object>(); entry.put("operation", operation); entry.put("payload", payload.deepCopy()); REQUESTS.add(entry);
            };
            sender.set(screen, recorder);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not isolate native request transport", failure); }
        return screen;
    }
    private static void acknowledge(IdentityScreen screen, JsonObject data) {
        var response = data.deepCopy(); response.addProperty("success", true); IdentityScreen.receive(response.toString());
        check(Minecraft.getInstance().screen == screen, "A response replaced the active production menu");
    }
    private static AbstractButton button(Screen screen, String suffix) {
        for (var child : screen.children()) if (child instanceof AbstractButton button
                && button.getMessage().getContents() instanceof TranslatableContents text
                && text.getKey().equals("gui.realmidentity." + suffix)) return button;
        throw new IllegalStateException("Missing native button: " + suffix);
    }
    private static boolean hasButton(Screen screen, String suffix) {
        for (var child : screen.children()) if (child instanceof AbstractButton button
                && button.getMessage().getContents() instanceof TranslatableContents text
                && text.getKey().equals("gui.realmidentity." + suffix)) return true;
        return false;
    }
    private static void request(IdentityScreen screen, String control, String operation) {
        int previous = REQUESTS.size(); var button = button(screen, control);
        check(button.active, "Inactive native control: " + control); button.onPress();
        check(REQUESTS.size() == previous + 1, "Native control must send exactly one request: " + control);
        var request = REQUESTS.get(previous);
        check(operation.equals(request.get("operation")), "Unexpected station operation");
        check(((JsonObject) request.get("payload")).isEmpty(), "Station actions must send no client identity or profile data");
    }
    private static void validateInterface(Minecraft mc) throws ReflectiveOperationException {
        var station = demo(true); var screen = open(mc, station);
        for (var child : screen.children()) check(!(child instanceof EditBox), "Identity fields must not be editable");
        check(!button(screen, "reissue").active, "A new record must not expose active reissuance");
        request(screen, "issue", "issue");
        var existing = station.deepCopy(); existing.addProperty("canIssue", false); existing.addProperty("canReissue", true);
        acknowledge(screen, existing);
        check(!button(screen, "issue").active, "An existing record must not expose active first issuance");
        request(screen, "reissue", "reissue"); acknowledge(screen, existing);
        int before = REQUESTS.size();
        for (int tick = 0; tick < 100; tick++) screen.tick();
        check(REQUESTS.size() == before + 1 && "refresh".equals(REQUESTS.get(before).get("operation")), "Physical station must refresh after 100 ticks");
        acknowledge(screen, existing);
        var noMaterials = existing.deepCopy(); noMaterials.addProperty("paperCount", 0); noMaterials.addProperty("inkCount", 0);
        noMaterials.addProperty("canIssue", false); noMaterials.addProperty("canReissue", false); acknowledge(screen, noMaterials);
        check(!button(screen, "issue").active && !button(screen, "reissue").active, "Server-disabled issuance must be inactive");
        CHECKS.add("Native issue, reissue and 100-tick refresh send empty payloads; server eligibility controls button availability; no editable character fields.");

        var viewed = demo(false); screen = open(mc, viewed);
        check(!hasButton(screen, "issue") && !hasButton(screen, "reissue"), "Read-only documents must not expose station actions");
        before = REQUESTS.size(); for (int tick = 0; tick < 210; tick++) screen.tick();
        check(REQUESTS.size() == before, "Read-only document must never run station refresh");
        var validProfile = IdentityScreen.class.getDeclaredMethod("validProfile"); validProfile.setAccessible(true);
        check((Boolean) validProfile.invoke(screen), "Issued document must display its issued profile");
        for (String status : List.of("CURRENT", "OUTDATED", "OFFLINE", "PROFILE_UNAVAILABLE")) {
            var state = viewed.deepCopy(); state.addProperty("status", status); acknowledge(screen, state);
            check((Boolean) validProfile.invoke(screen), "Authenticated issued fields must survive freshness status " + status);
            check(Component.translatable("gui.realmidentity.status_" + status.toLowerCase(java.util.Locale.ROOT)).getString().indexOf("gui.realmidentity.") < 0,
                    "Untranslated status: " + status);
        }
        var invalid = viewed.deepCopy(); invalid.addProperty("status", "INVALID"); invalid.addProperty("authentic", false);
        // Even malformed input retaining a profile must not display identity fields.
        acknowledge(screen, invalid); check(!(Boolean) validProfile.invoke(screen), "Invalid documents must hide all issued identity fields");
        var otherHolder = viewed.deepCopy(); otherHolder.addProperty("mode", "ITEM"); otherHolder.addProperty("presenterName", "");
        otherHolder.addProperty("holderName", "SampleCourier"); otherHolder.addProperty("holderUuid", "22222222-2222-2222-2222-222222222222");
        otherHolder.addProperty("holderIsOwner", false); acknowledge(screen, otherHolder);
        check((Boolean) validProfile.invoke(screen), "A different holder may read an authenticated document without becoming its owner");
        var standalone = viewed.deepCopy(); standalone.addProperty("source", "MINECRAFT");
        var profile = new JsonObject(); profile.addProperty("source", "MINECRAFT"); profile.addProperty("name", "SampleOwner");
        profile.add("age", JsonNull.INSTANCE); profile.addProperty("race", ""); profile.addProperty("occupation", "");
        profile.addProperty("signature", ""); profile.addProperty("signatureDrawing", ""); standalone.add("profile", profile);
        acknowledge(screen, standalone);
        var drawing = IdentityScreen.class.getDeclaredField("signatureDrawing"); drawing.setAccessible(true);
        check(drawing.get(screen) == null, "Standalone identity must not invent a signature drawing");
        var malformed = viewed.deepCopy(); malformed.getAsJsonObject("profile").addProperty("signatureDrawing", "ink1:bad");
        acknowledge(screen, malformed); check(drawing.get(screen) == null, "Malformed ink must render absent without crashing");
        CHECKS.add("Read-only issued snapshots retain fields across current/outdated/offline/unavailable statuses, hide fields when invalid, and have no station refresh or mutation controls. Standalone and malformed drawings render absent.");

        screen = open(mc, viewed);
        var activeTitles = IdentityScreen.class.getDeclaredMethod("activeTitles"); activeTitles.setAccessible(true);
        check(((List<?>) activeTitles.invoke(screen)).size() == 2, "The public honours section lost a permanent or temporary title");
        var received = IdentityScreen.class.getDeclaredField("receivedAtNanos"); received.setAccessible(true);
        received.setLong(screen, System.nanoTime() - 8L * 86_400_000_000_000L);
        check(((List<?>) activeTitles.invoke(screen)).size() == 1,
                "Temporary titles must expire from consultation time plus monotonic elapsed time, independent of the client wall clock");
        var full = viewed.deepCopy(); var sixteen = new JsonArray();
        for (int i = 0; i < 16; i++) {
            var title = viewed.getAsJsonArray("titles").get(0).getAsJsonObject().deepCopy();
            title.addProperty("name", "A long public honour whose name wraps safely " + i);
            title.addProperty("description", "A full public description that must wrap onto the parchment and remain reachable by scrolling. ".repeat(2));
            sixteen.add(title);
        }
        full.add("titles", sixteen); acknowledge(screen, full);
        check(((List<?>) activeTitles.invoke(screen)).size() == 16, "The document truncated the sixteen-title public registry limit");
        screen.init(mc, 320, 240);
        var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        screen.render(graphics, 0, 0, 0); graphics.flush();
        var contentHeight = IdentityScreen.class.getDeclaredField("contentHeight"); contentHeight.setAccessible(true);
        check(contentHeight.getInt(screen) > 1600, "All sixteen wrapped public title cards must contribute to the scrollable body");
        check(screen.mouseScrolled(160, 100, 0, -1000), "Honours body does not accept scroll input at 320x240");
        var scrollPosition = IdentityScreen.class.getDeclaredField("scroll"); scrollPosition.setAccessible(true);
        check(scrollPosition.getInt(screen) > 1000, "The last honours are unreachable in a compact viewport");
        CHECKS.add("Public honours preserve all sixteen wrapped title cards at 320x240, scroll to the last honour, and locally hide expired temporary grants using the server consultation time plus monotonic elapsed time. These are explicit demo payloads, not live server evidence.");

        screen = open(mc, viewed); screen.init(mc, 320, 240);
        for (var child : screen.children()) if (child instanceof AbstractButton control)
            check(control.getX() >= 0 && control.getY() >= 0 && control.getX() + control.getWidth() <= 320 && control.getY() + control.getHeight() <= 240, "Control exceeds a 320x240 viewport");
        screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        IdentityScreen.receive("{}"); check(!(mc.screen instanceof IdentityScreen), "Empty snapshot must close an IdentityScreen");
        Screen unrelated = new TitleScreen(); mc.setScreen(unrelated); IdentityScreen.receive("{}");
        check(mc.screen == unrelated, "Empty snapshot must leave unrelated screens open");
        screen = open(mc, station); IdentityScreen.receive("{\"close\":true}");
        check(!(mc.screen instanceof IdentityScreen), "Invalid physical session must close the menu");
        CHECKS.add("Controls fit a 320x240 viewport. Empty/invalid physical sessions close only the identity menu, preserving unrelated screens.");
    }

    private static JsonObject demo(boolean station) {
        var data = new JsonObject(); data.addProperty("mode", station ? "STATION" : "PRESENTED");
        data.addProperty("stationMode", station); data.addProperty("readOnly", !station); data.addProperty("success", true);
        data.addProperty("message", "gui.realmidentity.qa_sample"); data.addProperty("status", "CURRENT"); data.addProperty("authentic", true);
        data.addProperty("documentId", "sample-only-record"); data.addProperty("serial", 42); data.addProperty("issuedAt", 1_792_022_400_000L);
        data.addProperty("ownerName", "SampleOwner"); data.addProperty("ownerUuid", "11111111-1111-1111-1111-111111111111");
        data.addProperty("holderName", "SampleOwner"); data.addProperty("holderUuid", "11111111-1111-1111-1111-111111111111");
        data.addProperty("presenterName", station ? "" : "SampleOwner"); data.addProperty("presenterUuid", "11111111-1111-1111-1111-111111111111");
        data.addProperty("holderIsOwner", true); data.addProperty("source", "CHRONICLES");
        data.addProperty("paperCount", 12); data.addProperty("inkCount", 7); data.addProperty("canIssue", station); data.addProperty("canReissue", false);
        var profile = new JsonObject(); profile.addProperty("source", "CHRONICLES"); profile.addProperty("name", "Aveline Vale (sample)");
        profile.addProperty("age", 28); profile.addProperty("race", "Human"); profile.addProperty("occupation", "Royal cartographer");
        profile.addProperty("signature", "Aveline Vale"); profile.addProperty("signatureDrawing", sampleDrawing()); data.add("profile", profile);
        long consultedAt = 1_792_022_400_000L;
        data.addProperty("titlesStatus", "available"); data.addProperty("titlesAsOf", consultedAt);
        var titles = new JsonArray();
        var permanent = new JsonObject(); permanent.addProperty("id", "22222222-2222-2222-2222-222222222222");
        permanent.addProperty("titleId", "33333333-3333-3333-3333-333333333333"); permanent.addProperty("name", "Royal cartographer");
        permanent.addProperty("description", "Entrusted with the kingdom’s roads, rivers and frontiers.");
        permanent.addProperty("grantedAt", consultedAt - 86_400_000L); permanent.addProperty("expiresAt", 0); titles.add(permanent);
        var temporary = permanent.deepCopy(); temporary.addProperty("id", "44444444-4444-4444-4444-444444444444");
        temporary.addProperty("titleId", "55555555-5555-5555-5555-555555555555"); temporary.addProperty("name", "Envoy of the harvest");
        temporary.addProperty("description", "A temporary honour for service during the autumn assembly.");
        temporary.addProperty("expiresAt", consultedAt + 7L * 86_400_000L); titles.add(temporary); data.add("titles", titles);
        return data;
    }
    private static String sampleDrawing() {
        byte[] pixels = new byte[InkDrawing.BYTES];
        // A deliberate sample drawing in the exact Chronicles monochrome encoding.
        int[][][] strokes = {
            {{20,68},{38,27},{46,63},{27,54},{54,54}}, {{57,63},{64,42},{68,61},{79,44},{75,62},{88,57}},
            {{102,31},{112,64},{133,28}}, {{129,59},{140,46},{142,64},{151,57}},
            {{157,61},{171,28},{162,61},{178,56}}, {{183,56},{197,48},{188,44},{181,59},{196,62},{210,53}},
            {{26,76},{216,70}}
        };
        for (var stroke : strokes) for (int i = 1; i < stroke.length; i++) {
            int ax = stroke[i-1][0], ay = stroke[i-1][1], bx = stroke[i][0], by = stroke[i][1];
            int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
            for (int step = 0; step <= steps; step++) {
                int px = ax + (bx - ax) * step / Math.max(1, steps), py = ay + (by - ay) * step / Math.max(1, steps);
                for (int dy = 0; dy < 2; dy++) { int bit = (py + dy) * InkDrawing.WIDTH + px; pixels[bit / 8] |= (byte) (1 << bit % 8); }
            }
        }
        return InkDrawing.PREFIX + Base64.getEncoder().encodeToString(pixels);
    }

    private static void validateModels(Minecraft mc) {
        var items = new ArrayList<ItemStack>();
        for (String id : List.of("writing_desk", "identity_document")) {
            var key = ResourceLocation.fromNamespaceAndPath("realmidentity", id);
            var item = BuiltInRegistries.ITEM.get(key); check(item != Items.AIR, "Unregistered item: " + id);
            var stack = new ItemStack(item); items.add(stack);
            var model = mc.getItemRenderer().getModel(stack, null, null, 0);
            check(!model.isCustomRenderer(), "Expected a JSON item model: " + id);
            check(!model.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing item particle: " + id);
            var faces = new ArrayList<BakedQuad>(); faces.addAll(model.getQuads(null, null, RandomSource.create(0)));
            for (var direction : Direction.values()) faces.addAll(model.getQuads(null, direction, RandomSource.create(0)));
            validateFaces(faces, id); QUADS.put("realmidentity:" + id, faces.size());
        }
        stacks = List.copyOf(items);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("realmidentity", "writing_desk"));
        for (var state : block.getStateDefinition().getPossibleStates()) {
            var model = mc.getBlockRenderer().getBlockModel(state);
            check(!model.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing block particle: " + state);
            var faces = new ArrayList<BakedQuad>(); faces.addAll(model.getQuads(state, null, RandomSource.create(0)));
            for (var side : Direction.values()) faces.addAll(model.getQuads(state, side, RandomSource.create(0)));
            validateFaces(faces, state.toString()); states++;
        }
        check(states == 4, "Writing desk must have exactly four directional states");
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
            report.put("interfaceData", "Explicit sample identity/profile in the actual production IdentityScreen; not a live Chronicles runtime profile");
            report.put("signatureData", "Sample ink1 monochrome drawing, decoded by the production domain decoder and rendered by the production UI");
            report.put("interfaceChecks", List.copyOf(CHECKS)); report.put("recordedUiRequests", List.copyOf(REQUESTS));
            report.put("requestTransport", "Recorded locally; no server requests"); report.put("bakedQuadCounts", QUADS); report.put("validatedBlockStates", states);
            report.put("windowWidth", Minecraft.getInstance().getWindow().getWidth()); report.put("windowHeight", Minecraft.getInstance().getWindow().getHeight());
            Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { throw new IllegalStateException("Could not save Identity client visual QA", failure); }
        System.out.println("IDENTITY_VISUAL_QA success=" + success + " output=" + OUT + " " + message);
        Minecraft.getInstance().stop();
    }

    private static final class ItemPreview extends Screen {
        int frames;
        ItemPreview() { super(Component.literal("REALM IDENTITY")); }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {}
        @Override public void render(GuiGraphics g, int mx, int my, float tick) {
            g.fill(0, 0, width, height, 0xFF1D2822);
            g.fill(36, 24, width - 36, 25, 0xFFB99853); g.fill(36, height - 25, width - 36, height - 24, 0xFFB99853);
            g.drawCenteredString(font, getTitle(), width / 2, 43, 0xFFF1DFB7);
            g.drawCenteredString(font, "A physical record, sealed in your world", width / 2, 62, 0xFFB4BEA9);
            int scale = 12, centerY = height / 2 - 95;
            int[] centers = {width / 3, width * 2 / 3};
            for (int i = 0; i < stacks.size(); i++) {
                g.pose().pushPose(); g.pose().translate(centers[i] - scale * 8, centerY, 0); g.pose().scale(scale, scale, scale);
                g.renderItem(stacks.get(i), 0, 0); g.pose().popPose();
            }
            g.drawCenteredString(font, "Writing desk", centers[0], height - 94, 0xFFF1DFB7);
            g.drawCenteredString(font, "Identity document", centers[1], height - 94, 0xFFF1DFB7);
            g.drawCenteredString(font, "Minecraft 1.21.1 · NeoForge · Actual registered item models", width / 2, height - 54, 0xFF94A18E);
            frames++;
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
