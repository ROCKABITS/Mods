package pt.contracts.visualtest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import pt.contracts.ContractsMod;
import pt.contracts.ContractBoardBlock;
import pt.contracts.client.ContractsScreen;
import pt.contracts.client.ContractItemPickerScreen;
import pt.contracts.domain.Ledger;

/**
 * Opt-in client smoke test: renders the real, resource-loaded Minecraft item
 * models, checks their baked sprites, and captures the framebuffer. No world,
 * server, account, or production client UI is needed. Excluded from release JARs.
 */
@EventBusSubscriber(modid = ContractsMod.ID, value = Dist.CLIENT)
public final class BoardVisualQA {
    private static final Path OUT = Path.of(System.getProperty("contract.qaOutput", "qa-results/boards-client"))
            .toAbsolutePath().normalize();
    private static final String[] IDS = {"contract_board", "tall_contract_board", "wide_contract_board"};
    private static final String[] NAMES = {"Small contract board", "Roofed contract board", "Grand contract board"};
    private static final String[] SIZES = {"1 × 1 block", "1 × 2 blocks", "2 × 2 blocks"};
    private static final String[] FILENAMES = {
            "contracts-boards-minecraft.png", "contracts-board-small.png",
            "contracts-board-tall.png", "contracts-board-wide.png",
            "contracts-menu-browse.png", "contracts-menu-create.png",
            "contracts-item-catalogue.png", "contracts-item-search.png", "contracts-item-selected.png",
            "contracts-menu-compact.png", "contracts-menu-portuguese.png"
    };
    private static final long STARTED = System.currentTimeMillis();
    private static final Map<String, Integer> QUAD_COUNTS = new LinkedHashMap<>();
    private static List<ItemStack> stacks;
    private static Screen captureScreen;
    private static final List<String> CAPTURED = new ArrayList<>();
    private static boolean done, languageRequested;
    private static int captureIndex, screenTicks;
    private static long screenStarted;

    private BoardVisualQA() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() - STARTED > 180_000L) {
                throw new IllegalStateException("Client visual QA timed out on "
                        + (mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName()));
            }
            if (mc.getOverlay() != null) return;
            if (captureScreen == null) {
                if (!(mc.screen instanceof TitleScreen)) {
                    continueAccessibility(mc.screen);
                    return;
                }
                if (!languageRequested) {
                    languageRequested = true;
                    if (!"en_us".equals(mc.options.languageCode)) {
                        mc.getLanguageManager().setSelected("en_us");
                        mc.options.languageCode = "en_us";
                        mc.reloadResourcePacks();
                        return;
                    }
                }
                mc.getWindow().setWindowed(1600, 900);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                stacks = loadAndValidateModels(mc);
                showCapture(mc);
                return;
            }
            if (mc.screen != captureScreen) throw new IllegalStateException("Unexpected screen during gallery capture");
            if (captureIndex == 10 && screenTicks == 0) captureScreen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            screenTicks++;
            // The production interface uses the same client frame loop. Wait for
            // stable ticks and time; preview screens additionally count real renders.
            if (screenTicks < 30 || System.currentTimeMillis() - screenStarted < 2_000L
                    || captureScreen instanceof PreviewScreen preview && preview.renderedFrames < 30) return;
            Files.createDirectories(OUT);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(captureIndex == 9 ? image.getWidth() == 640 && image.getHeight() == 480 : image.getWidth() >= 1200 && image.getHeight() >= 680,
                        "Screenshot window was smaller than requested: " + image.getWidth() + "x" + image.getHeight());
                image.writeToFile(OUT.resolve(FILENAMES[captureIndex]));
            }
            CAPTURED.add(FILENAMES[captureIndex]);
            ContractsMod.LOG.info("CONTRACTS_BOARD_VISUAL_QA captured {}", FILENAMES[captureIndex]);
            captureIndex++;
            if (captureIndex < FILENAMES.length) showCapture(mc);
            else complete(true, "Three production board models and eleven actual Minecraft captures passed. The visual item catalogue, search, draft preservation, exact item selection, session handling, compact controls and Portuguese presentation passed.");
        } catch (Throwable failure) {
            failure.printStackTrace();
            complete(false, failure.toString());
        }
    }

    private static void showCapture(Minecraft mc) throws Exception {
        if (captureIndex < 4) {
            captureScreen = new PreviewScreen(captureIndex - 1); mc.setScreen(captureScreen);
        } else if (captureIndex == 4) {
            validatePublicationAccess(mc);
            ContractsScreen.receive(demonstrationSnapshot());
            check(mc.screen instanceof ContractsScreen, "Production contract interface did not open");
            captureScreen = mc.screen;
        } else if (captureIndex == 5) {
            check(click(mc.screen, "gui.contracts.create"), "Create tab did not open");
            int fields = 0;
            for (var child : mc.screen.children()) if (child instanceof EditBox field) {
                String key = ((TranslatableContents)field.getMessage().getContents()).getKey();
                switch (key) {
                    case "gui.contracts.label_title" -> field.setValue("Timber for the village inn");
                    case "gui.contracts.label_quantity" -> field.setValue("64");
                    case "gui.contracts.label_reward" -> field.setValue("20");
                    default -> throw new IllegalStateException("Unexpected form field: " + key);
                }
                fields++;
            }
            check(fields == 3, "Only title, quantity and reward should need typing");
            check(hasButton(mc.screen, "gui.contracts.publish", false), "Publishing without a selected item must be disabled");
            mc.screen.setFocused(null); captureScreen = mc.screen;
        } else if (captureIndex == 6) {
            check(click(mc.screen, "gui.contracts.choose_item"), "Missing visual material chooser");
            check(mc.screen instanceof ContractItemPickerScreen, "Catalogue did not open");
            Screen picker = mc.screen;
            ContractsScreen.receive(demonstrationSnapshot());
            check(mc.screen == picker, "Policy refresh replaced the picker and lost its draft");
            search(mc.screen).setValue("contracts:");
            check(itemButtons(mc.screen) == 3, "Namespace search must find all three registered mod boards");
            search(mc.screen).setValue("no_such_material_1234");
            check(itemButtons(mc.screen) == 0, "Unknown search must have no results");
            search(mc.screen).setValue("");
            check(click(mc.screen, "gui.contracts.picker_inventory"), "Missing inventory filter");
            check(itemButtons(mc.screen) == 0, "Offline fixture must have no inventory items");
            check(click(mc.screen, "gui.contracts.picker_all"), "Missing all-materials filter");
            check(itemButtons(mc.screen) > 0, "All-materials filter did not restore items");
            captureScreen = mc.screen;
        } else if (captureIndex == 7) {
            search(mc.screen).setValue("oak log");
            check(itemButtons(mc.screen) > 0, "Name search found no oak logs"); captureScreen = mc.screen;
        } else if (captureIndex == 8) {
            check(clickLiteral(mc.screen, "Oak Log"), "Could not select Oak Log by name and icon");
            check(mc.screen instanceof ContractsScreen, "Selection did not return to the form");
            var target = ContractsScreen.class.getDeclaredField("itemValue"); target.setAccessible(true);
            check("minecraft:oak_log".equals(target.get(mc.screen)), "Wrong item selected for the existing server request");
            for (var child : mc.screen.children()) if (child instanceof EditBox field) {
                String key = ((TranslatableContents)field.getMessage().getContents()).getKey();
                check(field.getValue().equals(key.endsWith("label_title") ? "Timber for the village inn" : key.endsWith("label_quantity") ? "64" : "20"), "Picker lost a draft field");
            }
            check(hasButton(mc.screen, "gui.contracts.publish", true), "Selected item should allow publication"); captureScreen = mc.screen;
        } else if (captureIndex == 9) {
            mc.getWindow().setWindowed(640, 480); mc.resizeDisplay(); bounds(mc.screen);
            check(clickLiteral(mc.screen, "Oak Log"), "Compact material button missing");
            check(mc.screen instanceof ContractItemPickerScreen, "Compact catalogue did not open"); bounds(mc.screen);
            check(click(mc.screen, "gui.contracts.back"), "Compact catalogue did not return"); bounds(mc.screen); captureScreen = mc.screen;
        } else {
            mc.getWindow().setWindowed(1600, 900); mc.resizeDisplay();
            mc.getLanguageManager().setSelected("pt_pt"); mc.options.languageCode = "pt_pt"; mc.reloadResourcePacks(); captureScreen = mc.screen;
        }
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1, 1); screenTicks = 0; screenStarted = System.currentTimeMillis();
    }
    private static EditBox search(Screen screen) {
        return screen.children().stream().filter(c -> c instanceof EditBox).map(c -> (EditBox)c).findFirst().orElseThrow();
    }
    private static int itemButtons(Screen screen) {
        return (int)screen.children().stream().filter(c -> c instanceof AbstractButton b && b.visible && b.active
                && b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.LiteralContents
                && !b.getMessage().getString().equals("←") && !b.getMessage().getString().equals("→")).count();
    }
    private static boolean clickLiteral(Screen screen, String label) {
        for (var child : List.copyOf(screen.children())) if (child instanceof AbstractButton b && b.active && b.visible && b.getMessage().getString().equals(label)) { b.onPress(); return true; }
        return false;
    }
    private static boolean click(Screen screen, String key) { return clickLiteral(screen, Component.translatable(key).getString()); }
    private static void bounds(Screen screen) {
        var widgets = screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible).map(c -> (net.minecraft.client.gui.components.AbstractWidget)c).toList();
        for (var a : widgets) {
            check(a.getX() >= 0 && a.getY() >= 0 && a.getX() + a.getWidth() <= screen.width && a.getY() + a.getHeight() <= screen.height, "Compact widget outside screen");
            for (var b : widgets) if (a != b) check(a.getX() >= b.getX() + b.getWidth() || a.getX() + a.getWidth() <= b.getX() || a.getY() >= b.getY() + b.getHeight() || a.getY() + a.getHeight() <= b.getY(), "Compact widgets overlap");
        }
    }

    private static void validatePublicationAccess(Minecraft mc) {
        Screen previous = mc.screen;
        var locked = JsonParser.parseString(demonstrationSnapshot()).getAsJsonObject();
        locked.addProperty("reputation", 5);
        locked.addProperty("canPublish", false);
        locked.addProperty("publicationReason", "gui.contracts.reputation_required");
        ContractsScreen.receive(locked.toString());
        check(mc.screen instanceof ContractsScreen, "Locked publishing must still allow browsing contracts");
        check(hasButton(mc.screen, "gui.contracts.create", false), "Low reputation must disable publication");
        check(hasButton(mc.screen, "gui.contracts.deliver", true), "Low reputation must still allow contract delivery");
        check(hasButton(mc.screen, "gui.contracts.claim_emeralds", true), "Low reputation must still allow collecting rewards");
        ContractsScreen.receive("");
        check(!(mc.screen instanceof ContractsScreen), "Blank state must close the contract menu");
        ContractsScreen.receive(demonstrationSnapshot());
        ContractsScreen.receive("{\"close\":true,\"message\":\"gui.contracts.board_range\"}");
        check(!(mc.screen instanceof ContractsScreen), "Expired board session must close the contract menu");
        ContractsScreen.receive(demonstrationSnapshot());
        check(click(mc.screen, "gui.contracts.create") && click(mc.screen, "gui.contracts.choose_item"), "Could not open picker for session checks");
        ContractsScreen.receive("{\"close\":true}");
        check(!(mc.screen instanceof ContractItemPickerScreen), "An expired session must close its picker too");
        ContractsScreen.receive(demonstrationSnapshot());
        check(click(mc.screen, "gui.contracts.create") && click(mc.screen, "gui.contracts.choose_item"), "Could not reopen picker");
        ContractsScreen.receive(locked.toString());
        check(mc.screen instanceof ContractsScreen && hasButton(mc.screen, "gui.contracts.create", false), "Revoked publication permission must leave the picker and lock creation");
        mc.setScreen(previous);
        ContractsScreen.receive("{\"close\":true}");
        check(mc.screen == previous, "Session close must leave unrelated screens open");
    }

    private static boolean hasButton(Screen screen, String key, boolean active) {
        for (var child : screen.children()) {
            if (child instanceof AbstractButton button && button.active == active
                    && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key)) return true;
        }
        return false;
    }

    private static String demonstrationSnapshot() {
        String[] titles = {
                "Oak timber for the village inn", "Stone for the northern watchtower",
                "Food for the harvest festival", "Lanterns for the market square",
                "Wool for the royal banners", "Glass for the alchemist's workshop"
        };
        String[] materials = {"oak_log", "stone", "bread", "lantern", "white_wool", "glass"};
        String[] authors = {"Rowan", "Aldric", "Mira", "Elowen", "Cedric", "Lyra"};
        int[] quantities = {64, 128, 96, 16, 64, 64};
        long[] rewards = {20, 32, 18, 24, 16, 24};
        var contracts = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < titles.length; i++) {
            boolean kingdom = i == 1 || i == 3;
            var contract = new LinkedHashMap<String, Object>();
            contract.put("id", new UUID(0, 101 + i).toString());
            contract.put("author", kingdom ? new UUID(0, 0).toString() : new UUID(0, 21 + i).toString());
            contract.put("issuer", new UUID(0, 21 + i).toString());
            contract.put("authorName", kingdom ? "The kingdom" : authors[i]);
            contract.put("title", titles[i]);
            contract.put("item", "minecraft:" + materials[i]);
            contract.put("quantity", quantities[i]);
            contract.put("reward", rewards[i]);
            contract.put("currency", "EMERALD");
            contract.put("status", "OPEN");
            contract.put("funding", kingdom ? "KINGDOM" : "PLAYER");
            contract.put("taxBasisPoints", 500);
            contract.put("tax", rewards[i] * 500 / 10_000);
            contract.put("netReward", rewards[i] - rewards[i] * 500 / 10_000);
            contract.put("reputationReward", kingdom ? 1 : 0);
            contract.put("dailyReputationLimit", kingdom ? 5 : 0);
            contracts.add(contract);
        }
        var data = new LinkedHashMap<String, Object>();
        data.put("me", new UUID(0, 1).toString());
        data.put("currency", "EMERALD");
        data.put("wallet", 128);
        data.put("emeraldCredit", 12);
        data.put("chroniclesCredit", 0);
        data.put("page", 0);
        data.put("pages", 1);
        data.put("message", "");
        data.put("reputation", 42);
        data.put("requiredReputation", 20);
        data.put("canPublish", true);
        data.put("treasuryAvailable", true);
        data.put("publicationReason", "");
        data.put("reputationPending", 0);
        data.put("missionsPending", 0);
        data.put("taxBasisPoints", 500);
        data.put("contracts", contracts);
        data.put("deliveries", List.of(new Ledger.Delivery("minecraft:oak_log", 64)));
        return new GsonBuilder().create().toJson(data);
    }

    private static void continueAccessibility(Screen screen) {
        if (screen == null || !screen.getClass().getSimpleName().contains("Accessibility")) return;
        for (var child : List.copyOf(screen.children())) {
            if (child instanceof AbstractButton button && button.active && button.visible
                    && (button.getMessage().getString().equals("Continue")
                    || button.getMessage().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("gui.continue"))) {
                button.onPress();
                return;
            }
        }
    }

    private static List<ItemStack> loadAndValidateModels(Minecraft mc) {
        var result = new ArrayList<ItemStack>();
        for (String id : IDS) {
            var key = new ResourceLocation(ContractsMod.ID, id);
            var item = BuiltInRegistries.ITEM.get(key);
            check(item != Items.AIR, "Board item is not registered: " + key);
            var stack = new ItemStack(item);
            BakedModel model = mc.getItemRenderer().getModel(stack, null, null, 0);
            check(!model.isCustomRenderer(), "Expected the JSON board model for " + key);
            check(!model.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()),
                    "Missing particle sprite on " + key);
            var quads = new ArrayList<BakedQuad>();
            quads.addAll(model.getQuads(null, null, RandomSource.create(0)));
            for (var side : Direction.values()) {
                quads.addAll(model.getQuads(null, side, RandomSource.create(0)));
            }
            check(!quads.isEmpty(), "Board model has no baked geometry: " + key);
            for (var quad : quads) {
                check(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()),
                        "Missing texture on baked quad for " + key);
            }
            QUAD_COUNTS.put(key.toString(), quads.size());
            var block = (ContractBoardBlock) BuiltInRegistries.BLOCK.get(key);
            for (var state : block.getStateDefinition().getPossibleStates()) {
                if (!block.variant().contains(state.getValue(ContractBoardBlock.PART))) continue;
                BakedModel placed = mc.getBlockRenderer().getBlockModel(state);
                check(!placed.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()),
                        "Missing placed-block model on " + state);
                var faces = new ArrayList<BakedQuad>();
                faces.addAll(placed.getQuads(state, null, RandomSource.create(0)));
                for (var side : Direction.values()) faces.addAll(placed.getQuads(state, side, RandomSource.create(0)));
                check(!faces.isEmpty(), "Placed block has no geometry: " + state);
                for (var face : faces) check(!face.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()),
                        "Missing placed-block texture on " + state);
            }
            result.add(stack);
        }
        return List.copyOf(result);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void complete(boolean success, String message) {
        done = true;
        try {
            Files.createDirectories(OUT);
            var report = new LinkedHashMap<String, Object>();
            report.put("success", success);
            report.put("message", message);
            report.put("screenshot", "contracts-boards-minecraft.png");
            report.put("screenshots", List.copyOf(CAPTURED));
            report.put("visualItemPicker", true);
            report.put("pickerChecks", List.of("localized name and namespace search", "inventory and empty-state filters", "draft preserved during selection and policy refresh", "exact item ID selected without typing", "320x240 non-overlapping form and catalogue", "English and Portuguese"));
            report.put("interfaceData", "Demonstration contracts rendered by the unmodified production ContractsScreen");
            report.put("bakedQuadCounts", QUAD_COUNTS);
            report.put("windowWidth", Minecraft.getInstance().getWindow().getWidth());
            report.put("windowHeight", Minecraft.getInstance().getWindow().getHeight());
            Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) {
            failure.printStackTrace();
            throw new IllegalStateException("Could not write board visual QA result", failure);
        }
        ContractsMod.LOG.info("CONTRACTS_BOARD_VISUAL_QA success={} output={} {}", success, OUT, message);
        Minecraft.getInstance().stop();
    }

    private static final class PreviewScreen extends Screen {
        private int renderedFrames;
        private final int featured;

        private PreviewScreen(int featured) {
            super(Component.literal("Contracts & Bounties"));
            this.featured = featured;
        }

        @Override
        public void renderBackground(GuiGraphics graphics) {}

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF192321);
            graphics.fill(32, 22, width - 32, 23, 0xFF506252);
            graphics.fill(32, height - 23, width - 32, height - 22, 0xFF506252);
            graphics.drawCenteredString(font, getTitle(), width / 2, 38, 0xFFEED9AE);
            graphics.drawCenteredString(font, "Crafted wood · pinned parchments · terracotta roofs", width / 2, 56, 0xFFA4B4A3);
            int modelTop = height / 2 - 98;
            int labelTop = height - 94;
            if (featured < 0) {
                for (int index = 0; index < stacks.size(); index++) {
                    int center = width * (index * 2 + 1) / 6;
                    renderBoard(graphics, index, center, modelTop, 11);
                    graphics.drawCenteredString(font, NAMES[index], center, labelTop, 0xFFF3E6CD);
                    graphics.drawCenteredString(font, SIZES[index], center, labelTop + 17, 0xFFA4B4A3);
                }
            } else {
                // A larger view still uses the exact production item model and
                // Minecraft lighting; no screenshot postprocessing or mock assets.
                renderBoard(graphics, featured, width / 2, height / 2 - 128, 16);
                graphics.drawCenteredString(font, NAMES[featured], width / 2, height - 79, 0xFFF3E6CD);
                graphics.drawCenteredString(font, SIZES[featured], width / 2, height - 62, 0xFFA4B4A3);
            }
            graphics.drawCenteredString(font, "Minecraft 1.20.1 · Forge · In-game model rendering", width / 2, height - 39, 0xFF879B87);
            renderedFrames++;
        }

        private void renderBoard(GuiGraphics graphics, int index, int center, int top, int scale) {
            graphics.pose().pushPose();
            graphics.pose().translate(center - scale * 8, top, 0);
            graphics.pose().scale(scale, scale, scale);
            graphics.renderItem(stacks.get(index), 0, 0);
            graphics.pose().popPose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
