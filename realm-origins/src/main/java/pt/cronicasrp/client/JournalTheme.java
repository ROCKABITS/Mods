package pt.cronicasrp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;

/** Original parchment palette, kept separate from the cinematic creator. */
final class JournalTheme {
    static final int INK = 0xFF3C3024, MUTED = 0xFF847055;
    static final int GOLD = 0xFFE0BD79, LIGHT = 0xFFF0E5CF, ASH = 0xFF9CA8B5;
    static final ResourceLocation JOURNAL_FONT = ResourceLocation.fromNamespaceAndPath("cronicasrp", "journal");
    static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("cronicasrp", "textures/gui/journal.png");
    static final ResourceLocation SCROLL = ResourceLocation.fromNamespaceAndPath("cronicasrp", "textures/gui/ritual.png");
    private JournalTheme() {}
    static Component heading(String value) { return Component.literal(value).withStyle(style -> style.withFont(JOURNAL_FONT)); }
    static FormattedCharSequence clipped(Component value, int maxWidth) {
        return Language.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(value, Math.max(1, maxWidth)));
    }
    static String money(long value) { return pt.cronicasrp.i18n.ModMessages.money(value).getString(); }
    static String number(long value) { return pt.cronicasrp.i18n.ModMessages.number(value).getString(); }
    static int originColor(String origin) {
        return switch (origin) {
            case "Desamparada" -> 0xFF92735C; case "Humilde" -> 0xFF65704E;
            case "Comum" -> 0xFF466F62; case "Confortável" -> 0xFF497B98;
            case "Abastada" -> 0xFF9A742A; case "Nobre" -> 0xFF8C5997;
            case "Herdeira" -> 0xFFBF8C34; default -> 0xFF8B795C;
        };
    }
    static void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x + 1, y, x + w - 1, y + 1, border); g.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border); g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        for (int side : new int[] {0, 1}) {
            int cx = side == 0 ? x + 2 : x + w - 7;
            g.fill(cx, y + 2, cx + 5, y + 3, border); g.fill(cx, y + h - 3, cx + 5, y + h - 2, border);
        }
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
    static void centered(GuiGraphics g, Component text, int cx, int y, int color, int maxWidth) {
        var font = Minecraft.getInstance().font; var trimmed = clipped(text, maxWidth);
        g.drawString(font, trimmed, cx - font.width(trimmed) / 2, y, color, false);
    }
}
