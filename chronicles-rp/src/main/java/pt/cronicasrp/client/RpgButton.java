package pt.cronicasrp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

final class RpgButton extends AbstractButton {
    private final Runnable action;
    private final boolean dark, primary;
    private boolean selected;
    private boolean fitLabel;
    RpgButton(int x, int y, int w, int h, String label, boolean dark, boolean primary, Runnable action) {
        super(x, y, w, h, Component.literal(label)); this.action = action; this.dark = dark; this.primary = primary;
    }
    RpgButton selected(boolean value) { selected = value; return this; }
    RpgButton fitLabel() { fitLabel = true; return this; }
    @Override public void onPress() { action.run(); }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHoveredOrFocused();
        int edge = !active ? 0xFF5C5D59 : hover || selected ? RpgTheme.GOLD : dark ? 0xFF655C4B : 0xFFAF9462;
        int background = dark ? (primary || selected ? 0xF03B3340 : 0xB0101B2C) : selected ? 0xAA9F814B : 0x667C5B32;
        if (hover) background = dark ? 0xF0504340 : 0xAAA98A58;
        RpgTheme.frame(g, getX(), getY(), width, height, background, edge);
        g.fill(getX() + 5, getY() + 2, getX() + width - 5, getY() + 3, (edge & 0xFFFFFF) | 0x30000000);
        int color = !active ? 0xFF8A8272 : dark ? (primary || hover ? RpgTheme.GOLD : RpgTheme.LIGHT) : RpgTheme.INK;
        var font = Minecraft.getInstance().font;
        if (fitLabel) {
            float scale = Math.min(1F, (width - 8F) / Math.max(1, font.width(getMessage())));
            g.pose().pushPose();
            g.pose().translate(getX() + (width - font.width(getMessage()) * scale) / 2, getY() + (height - font.lineHeight * scale) / 2 + 1, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, getMessage(), 0, 0, color, false); g.pose().popPose();
        } else {
            var label = RpgTheme.clipped(getMessage(), width - 10);
            g.drawString(font, label, getX() + (width - font.width(label)) / 2, getY() + (height - font.lineHeight) / 2 + 1, color, false);
        }
        if (selected) g.fill(getX() + width / 2 - 8, getY() + height - 2, getX() + width / 2 + 8, getY() + height - 1, edge);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
