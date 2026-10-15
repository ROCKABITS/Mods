package pt.contracts.visualtest;

import com.google.gson.GsonBuilder;
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
import net.minecraft.client.resources.model.BakedModel;
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
import pt.contracts.ContractsMod;
import pt.contracts.ContractBoardBlock;

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
    private static final String[] NAMES = {"Mural de contratos", "Mural com telhado", "Mural grande"};
    private static final String[] SIZES = {"1 × 1 bloco", "1 × 2 blocos", "2 × 2 blocos"};
    private static final long STARTED = System.currentTimeMillis();
    private static final Map<String, Integer> QUAD_COUNTS = new LinkedHashMap<>();
    private static List<ItemStack> stacks;
    private static PreviewScreen preview;
    private static boolean done;
    private static long previewStarted;

    private BoardVisualQA() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (done) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() - STARTED > 180_000L) {
                throw new IllegalStateException("Client visual QA timed out on "
                        + (mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName()));
            }
            if (mc.getOverlay() != null) return;
            if (preview == null) {
                if (!(mc.screen instanceof TitleScreen)) {
                    continueAccessibility(mc.screen);
                    return;
                }
                mc.getWindow().setWindowed(1600, 900);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                stacks = loadAndValidateModels(mc);
                preview = new PreviewScreen();
                mc.setScreen(preview);
                previewStarted = System.currentTimeMillis();
                return;
            }
            if (mc.screen != preview) throw new IllegalStateException("Unexpected screen during board preview");
            // Wait for actual frames as well as ticks so CI cannot capture before rendering.
            if (preview.renderedFrames < 30 || System.currentTimeMillis() - previewStarted < 2_000L) return;
            Files.createDirectories(OUT);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth() >= 1200 && image.getHeight() >= 680,
                        "Screenshot window was smaller than requested: " + image.getWidth() + "x" + image.getHeight());
                image.writeToFile(OUT.resolve("contracts-boards-minecraft.png"));
            }
            complete(true, "Three registered board items have nonempty baked geometry, no missing sprites, and an actual Minecraft client screenshot.");
        } catch (Throwable failure) {
            failure.printStackTrace();
            complete(false, failure.toString());
        }
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
            var key = ResourceLocation.fromNamespaceAndPath(ContractsMod.ID, id);
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

        private PreviewScreen() {
            super(Component.literal("Contracts & Bounties"));
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF192321);
            graphics.fill(32, 22, width - 32, 23, 0xFF506252);
            graphics.fill(32, height - 23, width - 32, height - 22, 0xFF506252);
            graphics.drawCenteredString(font, getTitle(), width / 2, 38, 0xFFEED9AE);
            graphics.drawCenteredString(font, "Murais de contratos · madeira, pergaminhos e telhas", width / 2, 56, 0xFFA4B4A3);
            int modelTop = height / 2 - 98;
            int labelTop = height - 94;
            for (int index = 0; index < stacks.size(); index++) {
                int center = width * (index * 2 + 1) / 6;
                // GuiGraphics invokes Minecraft's item renderer and lighting, including
                // the model's own GUI transformation. This is not an external mock-up.
                graphics.pose().pushPose();
                graphics.pose().translate(center - 88, modelTop, 0);
                graphics.pose().scale(11, 11, 11);
                graphics.renderItem(stacks.get(index), 0, 0);
                graphics.pose().popPose();
                graphics.drawCenteredString(font, NAMES[index], center, labelTop, 0xFFF3E6CD);
                graphics.drawCenteredString(font, SIZES[index], center, labelTop + 17, 0xFFA4B4A3);
            }
            graphics.drawCenteredString(font, "Minecraft 1.21.1 · NeoForge · Modelos renderizados no jogo", width / 2, height - 49, 0xFF879B87);
            renderedFrames++;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
