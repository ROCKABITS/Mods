package pt.cronicasrp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;

final class RpgTheme {
    static final int INK = 0xFFE7E2EE, MUTED = 0xFFA49CAF;
    // GOLD remains the shared accent name for existing widgets; the new palette is lilac.
    static final int GOLD = 0xFFC7B6DE, LIGHT = 0xFFF1EDF5, ASH = 0xFFADA6BA;
    static final int PANEL = 0xB8100E19, BORDER = 0xFF494052;
    static final ResourceLocation JOURNAL_FONT = ResourceLocation.fromNamespaceAndPath("cronicasrp", "journal");
    static final ResourceLocation PEDESTAL = ResourceLocation.fromNamespaceAndPath("cronicasrp", "textures/gui/origin_pedestal.png");
    static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("cronicasrp", "textures/gui/journal.png");
    static final ResourceLocation SCROLL = ResourceLocation.fromNamespaceAndPath("cronicasrp", "textures/gui/ritual.png");
    private RpgTheme() {}
    static Component heading(String value) { return Component.literal(value); }
    static FormattedCharSequence clipped(Component value, int maxWidth) {
        return Language.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(value, Math.max(1, maxWidth)));
    }
    static String money(long value) { return pt.cronicasrp.i18n.ModMessages.money(value).getString(); }
    static String number(long value) { return pt.cronicasrp.i18n.ModMessages.number(value).getString(); }
    static int originColor(String origin) {
        return switch (origin) {
            case "Desamparada" -> 0xFFBBA99D; case "Humilde" -> 0xFFA5B59A;
            case "Comum" -> 0xFF9DC2B0; case "Confortável" -> 0xFF9ABCD9;
            case "Abastada" -> 0xFFE0C58C; case "Nobre" -> 0xFFCFACDF;
            case "Herdeira" -> 0xFFF1D599; default -> GOLD;
        };
    }
    static void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x + 1, y, x + w - 1, y + 1, border); g.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border); g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
    }
    static void rule(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color); diamond(g, x + w / 2, y, 3, color);
    }
    static void diamond(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = radius - Math.abs(dy); g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }
    static void ring(GuiGraphics g, int cx, int cy, int radius, int color, double rotation) {
        for (int i = 0; i < 96; i++) {
            double a = i * Math.PI * 2 / 96 + rotation;
            int x = cx + (int) (Math.cos(a) * radius), y = cy + (int) (Math.sin(a) * radius);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
    static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        int steps = Math.max(80, rx * 5);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            int x = cx + (int) (Math.cos(a) * rx), y = cy + (int) (Math.sin(a) * ry);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
    static void backdrop(GuiGraphics g, int width, int height, int left, int top, int w, int h) {
        g.fillGradient(0, 0, width, height, 0xFF06070D, 0xFF0B0913);
        g.fillGradient(left, top + 20, left + w / 2, top + h - 20, 0xFF0C0D19, 0xFF080911);
        double time = net.minecraft.Util.getMillis() / 2200.0;
        for (int i = 0; i < 74; i++) {
            int x = left + Math.floorMod(i * 137 + 29, Math.max(1, w));
            int y = top + 24 + Math.floorMod(i * 83 + 17, Math.max(1, h - 48));
            int alpha = (int) (19 + 19 * (1 + Math.sin(time + i * 2.1)) / 2);
            g.fill(x, y, x + 1, y + 1, (alpha << 24) | 0xC4BAD9);
        }
        g.fill(left + 20, top + 29, left + w - 20, top + 30, 0xFF28232F);
        g.fill(left + 20, top + h - 21, left + w - 20, top + h - 20, 0xFF28232F);
        diamond(g, left + w / 2, top + h - 21, 2, GOLD);
        // Quiet edge columns frame the stage without covering text or the character.
        g.fillGradient(left, top, left + 2, top + h, 0x203F344F, 0x003F344F);
        g.fillGradient(left + w - 2, top, left + w, top + h, 0x203F344F, 0x003F344F);
    }
    static void centered(GuiGraphics g, Component text, int cx, int y, int color, int maxWidth) {
        var font = Minecraft.getInstance().font; var trimmed = clipped(text, maxWidth);
        g.drawString(font, trimmed, cx - font.width(trimmed) / 2, y, color, false);
    }
}
