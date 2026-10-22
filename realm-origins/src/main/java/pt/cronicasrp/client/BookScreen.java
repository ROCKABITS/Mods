package pt.cronicasrp.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

abstract class BookScreen extends Screen {
    protected final int INK, MUTED, GOLD;
    protected int left, top, bookWidth, bookHeight;
    private final boolean ritual;
    protected BookScreen(String title) { this(title, false); }
    protected BookScreen(String title, boolean ritual) {
        super(Component.literal(title)); this.ritual = ritual;
        INK = ritual ? RpgTheme.INK : JournalTheme.INK;
        MUTED = ritual ? RpgTheme.MUTED : JournalTheme.MUTED;
        GOLD = ritual ? RpgTheme.GOLD : JournalTheme.GOLD;
    }
    final boolean isJournal() { return !ritual; }
    protected void measure() {
        bookWidth = ritual ? Math.min(820, width - (width < 400 ? 12 : 40)) : Math.min(600, width - 14);
        bookHeight = ritual ? Math.min(500, height - (height < 280 ? 12 : 24)) : Math.min(380, height - 14);
        left = (width - bookWidth) / 2; top = (height - bookHeight) / 2;
    }
    protected int footerY() { return top + bookHeight - (ritual ? 48 : 42); }
    protected int innerWidth() { return bookWidth - 52; }
    protected int pageLeft() { return left + Math.max(24, bookWidth * 9 / 100); }
    protected int pageRight() { return left + bookWidth * 54 / 100; }
    protected int pageWidth() { return bookWidth * 37 / 100; }
    @Override public final void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!ritual) {
            super.renderBackground(g, mouseX, mouseY, partialTick);
            g.fill(0, 0, width, height, 0xB00C1018);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            g.blit(JournalTheme.BOOK, left, top, bookWidth, bookHeight, 0, 0, 1, 1, 1, 1);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            renderBook(g, mouseX, mouseY, partialTick);
            return;
        }
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
        g.drawString(font, RpgTheme.clipped(ritual ? RpgTheme.heading(value) : JournalTheme.heading(value), maxWidth), x, y, color, false);
    }
    protected void paragraph(GuiGraphics g, String value, int x, int y, int maxWidth, int maxHeight, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(value), Math.max(1, maxWidth));
        for (int i = 0; i < Math.min(lines.size(), Math.max(0, maxHeight / font.lineHeight)); i++)
            g.drawString(font, lines.get(i), x, y + i * font.lineHeight, color, false);
    }
    protected RpgButton button(int x, int y, int w, String label, boolean primary, Runnable action) {
        return addRenderableWidget(new RpgButton(x, y, w, ritual ? 20 : 18, label, ritual, primary, action));
    }
    protected void portrait(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, boolean mysterious) {
        if (!ritual) { journalPortrait(g, x, y, w, h, mouseX, mouseY, mysterious); return; }
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
    private void journalPortrait(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, boolean mysterious) {
        JournalTheme.frame(g, x, y, w, h, mysterious ? 0x9010192B : 0x18755831, mysterious ? 0xFF6C6558 : 0x778D734A);
        int cx = x + w / 2;
        double time = net.minecraft.Util.getMillis() / 2400.0;
        if (mysterious) {
            JournalTheme.ring(g, cx, y + h / 2, Math.min(w / 2 - 8, h / 2 - 8), 0x667A849C, time * 0.04);
            for (int i = 0; i < 7; i++) {
                int px = x + 8 + (i * 31 % Math.max(1, w - 16));
                int py = y + 5 + (int) ((i * 29 + time * 9) % Math.max(1, h - 21));
                JournalTheme.diamond(g, px, py, 1, 0x60DFC899);
            }
        }
        if (minecraft.player != null) {
            int scale = Math.max(14, Math.min(w / 2 - 10, (h - 23) * 9 / 20));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 3, y + 3, x + w - 3, y + h - 16,
                    scale, 0.05F, cx + (mouseX - cx) * 0.15F,
                    y + h / 2F + (mouseY - y - h / 2F) * 0.10F, minecraft.player);
            JournalTheme.centered(g, Component.literal(ClientState.profile().created()
                    ? ClientState.profile().name() : minecraft.player.getGameProfile().getName()), cx,
                    y + h - 13, mysterious ? JournalTheme.ASH : MUTED, w - 10);
        }
    }
    // Minecraft only pauses an unpublished single-player world; multiplayer keeps running.
    @Override public boolean isPauseScreen() { return true; }
}
