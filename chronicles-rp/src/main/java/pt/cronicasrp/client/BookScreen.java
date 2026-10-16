package pt.cronicasrp.client;

import java.util.List;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
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
        bookWidth = Math.min(600, width - 14); bookHeight = Math.min(380, height - 14);
        left = (width - bookWidth) / 2; top = (height - bookHeight) / 2;
    }
    protected int footerY() { return top + bookHeight - 42; }
    protected int innerWidth() { return bookWidth - 52; }
    protected int pageLeft() { return left + Math.max(24, bookWidth * 9 / 100); }
    protected int pageRight() { return left + bookWidth * 54 / 100; }
    protected int pageWidth() { return bookWidth * 37 / 100; }
    @Override public final void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Screen blurs before the journal art, keeping the parchment and text sharp.
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, ritual ? 0xD3080C18 : 0xB00C1018);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        g.blit(ritual ? RpgTheme.SCROLL : RpgTheme.BOOK, left, top, bookWidth, bookHeight, 0, 0, 1, 1, 1, 1);
        RenderSystem.disableBlend();
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
        return addRenderableWidget(new RpgButton(x, y, w, 18, label, ritual, primary, action));
    }
    protected void portrait(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, boolean mysterious) {
        RpgTheme.frame(g, x, y, w, h, mysterious ? 0x9010192B : 0x18755831, mysterious ? 0xFF6C6558 : 0x778D734A);
        int cx = x + w / 2;
        double time = Util.getMillis() / 2400.0;
        if (mysterious) {
            RpgTheme.ring(g, cx, y + h / 2, Math.min(w / 2 - 8, h / 2 - 8), 0x667A849C, time * 0.04);
            for (int i = 0; i < 7; i++) {
                int px = x + 8 + (i * 31 % Math.max(1, w - 16));
                int py = y + 5 + (int) ((i * 29 + time * 9) % Math.max(1, h - 21));
                RpgTheme.diamond(g, px, py, 1, 0x60DFC899);
            }
        }
        if (minecraft.player != null) {
            int scale = Math.max(14, Math.min(w / 2 - 10, (h - 23) * 9 / 20));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 3, y + 3, x + w - 3, y + h - 16,
                    scale, 0.05F, cx + (mouseX - cx) * 0.15F,
                    y + h / 2F + (mouseY - y - h / 2F) * 0.10F, minecraft.player);
            RpgTheme.centered(g, Component.literal(ClientState.profile().created()
                    ? ClientState.profile().name() : minecraft.player.getGameProfile().getName()), cx,
                    y + h - 13, mysterious ? RpgTheme.ASH : MUTED, w - 10);
        }
    }
    // Minecraft only pauses an unpublished single-player world; multiplayer keeps running.
    @Override public boolean isPauseScreen() { return true; }
}
