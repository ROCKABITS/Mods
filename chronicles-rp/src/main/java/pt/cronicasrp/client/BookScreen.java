package pt.cronicasrp.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

abstract class BookScreen extends Screen {
    protected static final int INK = RpgTheme.INK, MUTED = RpgTheme.MUTED, GOLD = RpgTheme.GOLD;
    protected int left, top, bookWidth, bookHeight;
    private final boolean ritual;
    protected BookScreen(String title) { this(title, false); }
    protected BookScreen(String title, boolean ritual) { super(Component.literal(title)); this.ritual = ritual; }
    protected void measure() {
        bookWidth = Math.min(820, width - (width < 400 ? 12 : 40));
        bookHeight = Math.min(500, height - (height < 280 ? 12 : 24));
        left = (width - bookWidth) / 2; top = (height - bookHeight) / 2;
    }
    protected int footerY() { return top + bookHeight - 48; }
    protected int innerWidth() { return bookWidth - 52; }
    protected int pageLeft() { return left + Math.max(24, bookWidth * 9 / 100); }
    protected int pageRight() { return left + bookWidth * 54 / 100; }
    protected int pageWidth() { return bookWidth * 37 / 100; }
    @Override public final void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RpgTheme.backdrop(g, width, height, left, top, bookWidth, bookHeight);
        text(g, "REALM ORIGINS", left + 23, top + 12, bookWidth / 2 - 26, GOLD);
        String tagline = UiText.s("gui.cronicasrp.realm.tagline");
        text(g, tagline, left + bookWidth - 23 - Math.min(font.width(tagline), bookWidth / 2 - 26),
                top + 12, bookWidth / 2 - 26, MUTED);
        text(g, UiText.s("gui.cronicasrp.realm.exit_hint"), left + 23, top + bookHeight - 12, bookWidth / 2 - 26, MUTED);
        String hint = UiText.s("gui.cronicasrp.realm.keys_hint");
        text(g, hint, left + bookWidth - 23 - Math.min(font.width(hint), bookWidth / 2 - 26),
                top + bookHeight - 12, bookWidth / 2 - 26, MUTED);
        renderBook(g, mouseX, mouseY, partialTick);
    }
    protected abstract void renderBook(GuiGraphics g, int mouseX, int mouseY, float partialTick);
    protected void text(GuiGraphics g, String value, int x, int y, int maxWidth, int color) {
        g.drawString(font, font.plainSubstrByWidth(value, Math.max(0, maxWidth)), x, y, color, false);
    }
    protected void heading(GuiGraphics g, String value, int x, int y, int maxWidth, int color) {
        g.drawString(font, RpgTheme.clipped(RpgTheme.heading(value), maxWidth), x, y, color, false);
    }
    protected void paragraph(GuiGraphics g, String value, int x, int y, int maxWidth, int maxHeight, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(value), Math.max(1, maxWidth));
        for (int i = 0; i < Math.min(lines.size(), Math.max(0, maxHeight / font.lineHeight)); i++)
            g.drawString(font, lines.get(i), x, y + i * font.lineHeight, color, false);
    }
    protected RpgButton button(int x, int y, int w, String label, boolean primary, Runnable action) {
        return addRenderableWidget(new RpgButton(x, y, w, 20, label, ritual, primary, action));
    }
    protected void portrait(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, boolean mysterious) {
        int cx = x + w / 2;
        int ground = y + h - 20, floorW = Math.max(20, w - 8), floorH = Math.clamp(floorW / 5, 16, 36);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(RpgTheme.PEDESTAL, x + 4, ground - floorH / 2, floorW, floorH, 0, 0, 768, 192, 768, 192);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        if (minecraft.player != null) {
            int scale = Math.max(14, Math.min(w / 2 - 10, (h - 28) * 9 / 20));
            float modelOffset = (ground - (y + (h - 13) / 2F)) / scale
                    - minecraft.player.getBbHeight() / (2F * minecraft.player.getScale());
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 3, y + 3, x + w - 3, y + h - 16,
                    scale, modelOffset, cx + (mouseX - cx) * 0.15F,
                    y + h / 2F + (mouseY - y - h / 2F) * 0.10F, minecraft.player);

        }
    }
    // Minecraft only pauses an unpublished single-player world; multiplayer keeps running.
    @Override public boolean isPauseScreen() { return true; }
}
