package pt.seasoneight.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import pt.seasoneight.SeasonConfig;
import pt.seasoneight.SeasonEight;

public final class SeasonArt {
    private record Art(ResourceLocation id, int width, int height) {}
    private static final Map<String, Art> ART = new HashMap<>();
    public static double time() { return SeasonConfig.enabled(SeasonConfig.MOTION) ? (Util.getMillis() % 1_000_000) / 1000.0 : 0; }
    public static void background(GuiGraphics g, int w, int h) {
        g.fillGradient(0, 0, w, h, 0xFF080912, 0xFF141020);
        // Soft bands, distant pinpricks and fine astronomical rings stay behind the controls.
        for (int i = 0; i < 32; i++) {
            int half = Math.max(1, h / 100), y = h * (i + 8) / 48;
            g.fillGradient(w / 2, y - half, w, y + half, 0x012C193E, 0x032C193E);
        }
        double t = time();
        for (int i = 0; i < 76; i++) {
            int x = Math.floorMod(i * 233 + i * i * 13, Math.max(1, w));
            int y = Math.floorMod(i * 137 + i * i * 7, Math.max(1, h));
            int a = 28 + (int)(20 * (1 + Math.sin(t * .55 + i * 2.3)));
            g.fill(x, y, x + 1, y + 1, (a << 24) | (i % 5 == 0 ? 0xDCB779 : 0xC3A9DB));
            if (i % 19 == 0) diamond(g, x, y, 2, 0x395F506F);
        }
        int cx = (int)(w * .74), cy = h / 2 - 10;
        for (int r : new int[]{70, 108, 147, 191, 233}) ring(g, cx, cy, r, 0x174C3A61);
        g.fill(0, 0, w, 2, 0xFF67513F);
        g.fill(0, h - 2, w, h, 0xFF241D31);
    }
    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = 0; i < 220; i++) {
            double a = Math.PI * 2 * i / 220;
            int x = cx + (int)(Math.cos(a) * r), y = cy + (int)(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
    public static void diamond(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int half = radius - Math.abs(y);
            g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, color);
        }
    }
    public static void seal(GuiGraphics g, int cx, int cy, int size) {
        int floatY = (int)(Math.sin(time() * .6) * 2);
        image(g, "crest", cx - size / 2, cy - size / 2 + floatY, size, size);
    }
    /** Direct classpath textures are available even before Minecraft's first font/resource reload completes. */
    public static void image(GuiGraphics g, String name, int x, int y, int w, int h) {
        var a = ART.computeIfAbsent(name, key -> {
            try (var input = SeasonArt.class.getResourceAsStream("/assets/seasoneight/art/" + key + ".png")) {
                if (input == null) throw new java.io.IOException("Missing art: " + key);
                var pixels = NativeImage.read(input);
                var id = ResourceLocation.fromNamespaceAndPath(SeasonEight.ID, "direct/" + key);
                var texture = new DynamicTexture(pixels);
                texture.setFilter(true, false);
                Minecraft.getInstance().getTextureManager().register(id, texture);
                return new Art(id, pixels.getWidth(), pixels.getHeight());
            } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read Season 8 art", error); }
        });
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale((float)w / a.width(), (float)h / a.height(), 1);
        g.blit(a.id(), 0, 0, 0, 0, a.width(), a.height(), a.width(), a.height());
        g.pose().popPose();
    }
    private SeasonArt() {}
}
