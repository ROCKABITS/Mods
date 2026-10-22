package pt.seasoneight.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;

/** Uses the real vanilla button's action, account restrictions, message and tooltip. */
public final class SeasonButton extends AbstractButton {
    private final AbstractButton original;
    private final boolean primary;
    public SeasonButton(AbstractButton original, MenuLayout.Box box, boolean primary) {
        super(box.x(), box.y(), box.w(), box.h(), original.getMessage());
        this.original = original; this.primary = primary;
        this.active = original.active; this.visible = original.visible;
        this.setTooltip(original.getTooltip());
    }
    @Override public void onPress() { if (active) original.onPress(); }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        boolean hover = isHoveredOrFocused() && active;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x, y, x + w, y + h, active ? (hover ? 0xFF8D739F : primary ? 0xFF65516E : 0xFF40384B) : 0xFF29242F);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1,
                hover ? 0xFF40334E : primary ? 0xFF30263F : 0xEF1A1723,
                hover ? 0xFF241C30 : primary ? 0xFF201A2D : 0xEF12101B);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hover ? 0xFFCAB1DC : 0xFF64506F);
        g.fill(x, y + 4, x + 2, y + h - 4, active ? primary ? 0xFFDBBB7E : hover ? 0xFFBDA0D0 : 0xFF716081 : 0xFF39323D);
        if (primary) {
            SeasonArt.diamond(g, x + 15, y + h / 2, 3, active ? 0xFFDDBD80 : 0xFF685C4B);
            g.drawString(net.minecraft.client.Minecraft.getInstance().font, ">", x + w - 17, y + (h - 8) / 2,
                    hover ? 0xFFFFDFA0 : 0xFFC9AB77, false);
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textColor = active ? (hover ? 0xFFFFF1D8 : primary ? 0xFFF2DFBD : 0xFFD1C6DD) : 0xFF736B7D;
        g.drawCenteredString(font, font.plainSubstrByWidth(getMessage().getString(), w - (primary ? 54 : 12)),
                x + w / 2, y + (h - 8) / 2, textColor);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
