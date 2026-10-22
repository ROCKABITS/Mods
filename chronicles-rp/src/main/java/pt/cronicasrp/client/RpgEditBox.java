package pt.cronicasrp.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class RpgEditBox extends EditBox {
    RpgEditBox(Font font, int x, int y, int w, String label) {
        super(font, x, y, w, 20, Component.literal(label));
        setBordered(false); setTextColor(RpgTheme.LIGHT); setTextShadow(false);
    }
    @Override public int getInnerWidth() { return width - 10; }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RpgTheme.frame(g, getX(), getY(), width, height, RpgTheme.PANEL, isFocused() ? RpgTheme.GOLD : RpgTheme.BORDER);
        g.pose().pushPose(); g.pose().translate(5, 6, 0);
        super.renderWidget(g, mouseX, mouseY, partialTick); g.pose().popPose();
    }
    @Override public void onClick(double x, double y) { super.onClick(x - 5, y - 6); }
}
