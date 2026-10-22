package pt.cronicasrp.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

final class RpgStoryBox extends MultiLineEditBox {
    RpgStoryBox(Font font, int x, int y, int w, int h) {
        super(font, x, y, w, h, UiText.c("gui.cronicasrp.story.prompt"), UiText.c("gui.cronicasrp.story.narration"));
    }
    @Override protected void renderBackground(GuiGraphics g) {
        RpgTheme.frame(g, getX(), getY(), width, height, RpgTheme.PANEL, isFocused() ? RpgTheme.GOLD : RpgTheme.BORDER);
    }
    @Override protected void renderDecorations(GuiGraphics g) {
        if (scrollbarVisible()) {
            int thumb = Math.max(10, (height - 6) * height / Math.max(1, getInnerHeight()));
            int offset = (int) ((height - 6 - thumb) * scrollAmount() / Math.max(1, getMaxScrollAmount()));
            g.fill(getX() + width + 1, getY() + 3, getX() + width + 3, getY() + height - 3, 0x666A6275);
            g.fill(getX() + width + 1, getY() + 3 + offset, getX() + width + 3, getY() + 3 + offset + thumb, RpgTheme.GOLD);
        }
    }
}
