package pt.seasoneight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import pt.seasoneight.SeasonConfig;

/** Paints over the original renderer AFTER it has advanced reloads and handled completion/errors.
 * No reload callback, transition, resource counter, error path or overlay ownership is replaced. */
public final class LoadingPresentation {
    public static long frames;
    public static String lastKind = "";
    public static float lastProgress;
    public static void paint(GuiGraphics g, float progress, String kind) {
        var mc = Minecraft.getInstance();
        if (!SeasonConfig.enabled(SeasonConfig.LOADING) || !(mc.getOverlay() instanceof LoadingOverlay)) return;
        g.flush();
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        g.pose().pushPose();
        try {
            // NeoForge's early framebuffer renderer leaves a pixel-space, bottom-up projection.
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, g.guiWidth(), g.guiHeight(), 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewMatrix().identity().translate(0, 0, -11000);
            // The underlying screen's font and tooltips have written closer depth values.
            // Put this opaque presentation in front of that GUI, without clearing its depth buffer.
            g.pose().translate(0, 0, 2000);
            draw(g, Mth.clamp(progress, 0, 1), mc.options.languageCode.startsWith("pt"));
            g.flush();
            frames++; lastKind = kind; lastProgress = progress;
        } finally {
            g.pose().popPose();
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewMatrix().set(modelView);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }
    private static void draw(GuiGraphics g, float progress, boolean portuguese) {
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2;
        SeasonArt.background(g, w, h);
        int crest = Math.max(48, Math.min(110, h / 3));
        SeasonArt.seal(g, cx, h / 2 - 43, crest);
        int logoW = Math.min(264, w - 48);
        int logoH = Math.min(68, Math.max(36, (h - 100) / 3));
        SeasonArt.image(g, "wordmark", cx - logoW / 2, h / 2 + (h < 270 ? 6 : 17), logoW, logoH);
        int bw = Math.min(280, w - 64), by = Math.min(h - 38, h / 2 + 112);
        g.fill(cx - bw / 2, by, cx + bw / 2, by + 4, 0xFF30263F);
        int end = cx - bw / 2 + Math.round(bw * progress);
        g.fillGradient(cx - bw / 2, by, end, by + 4, 0xFFB995CE, 0xFF735091);
        if (progress > 0) g.fill(Math.max(cx - bw / 2, end - 2), by, end, by + 4, 0xFFE7CC92);
        SeasonArt.image(g, portuguese ? "loading-pt" : "loading-en", cx - 140, by + 13, 280, 16);
        for (int side : new int[]{-1, 1}) SeasonArt.diamond(g, cx + side * (bw / 2 + 9), by + 2, 2, 0xFFAD8D61);
    }
    private LoadingPresentation() {}
}
