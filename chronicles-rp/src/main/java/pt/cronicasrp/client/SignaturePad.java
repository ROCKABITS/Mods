package pt.cronicasrp.client;

import java.util.ArrayDeque;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import pt.cronicasrp.domain.SignatureDrawing;

final class SignaturePad extends AbstractWidget {
    private byte[] ink;
    private final ArrayDeque<byte[]> undo = new ArrayDeque<>();
    private int previousX, previousY, draggingButton = -1;
    SignaturePad(int x, int y, int width, int height, String drawing) {
        super(x, y, width, height, UiText.c("gui.cronicasrp.signature.narration"));
        ink = SignatureDrawing.decode(drawing);
    }
    String value() { return SignatureDrawing.hasInk(ink) ? SignatureDrawing.encode(ink) : ""; }
    void clear() { remember(); ink = new byte[SignatureDrawing.BYTES]; }
    void undo() { if (!undo.isEmpty()) ink = undo.removeLast(); }
    boolean drawing() { return draggingButton >= 0; }
    private void remember() { if (undo.size() >= 20) undo.removeFirst(); undo.addLast(ink.clone()); }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RpgTheme.frame(g, getX(), getY(), width, height, 0xFFF0E4C8, isHovered ? RpgTheme.GOLD : 0xFFAA8D5A);
        g.fill(getX() + 7, getY() + height * 3 / 4, getX() + width - 7, getY() + height * 3 / 4 + 1, 0x449A7950);
        renderInk(g, ink, getX() + 4, getY() + 4, width - 8, height - 8, RpgTheme.INK);
        if (isHovered) {
            g.fill(mouseX - 2, mouseY, mouseX + 3, mouseY + 1, 0xB0392C20);
            g.fill(mouseX, mouseY - 2, mouseX + 1, mouseY + 3, 0xB0392C20);
        }
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY) || (button != 0 && button != 1)) return false;
        remember(); draggingButton = button; previousX = localX(mouseX); previousY = localY(mouseY);
        brush(previousX, previousY, button == 0); return true;
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != draggingButton || draggingButton < 0) return false;
        if (!isMouseOver(mouseX, mouseY)) { previousX = -1; return true; }
        int x = localX(mouseX), y = localY(mouseY);
        if (previousX >= 0) line(previousX, previousY, x, y, button == 0); else brush(x, y, button == 0);
        previousX = x; previousY = y; return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == draggingButton) {
            // Include a short stroke's end even when press and release fall between frames.
            mouseDragged(x, y, button, 0, 0);
            draggingButton = -1; return true;
        }
        return false;
    }
    private int localX(double x) { return Math.clamp((int) ((x - getX() - 4) * SignatureDrawing.WIDTH / (width - 8)), 0, SignatureDrawing.WIDTH - 1); }
    private int localY(double y) { return Math.clamp((int) ((y - getY() - 4) * SignatureDrawing.HEIGHT / (height - 8)), 0, SignatureDrawing.HEIGHT - 1); }
    private void brush(int x, int y, boolean draw) {
        int radius = draw ? 1 : 5;
        for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++)
            if (dx * dx + dy * dy <= radius * radius) SignatureDrawing.pixel(ink, x + dx, y + dy, draw);
    }
    private void line(int x0, int y0, int x1, int y1, boolean draw) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, error = dx + dy;
        while (true) {
            brush(x0, y0, draw); if (x0 == x1 && y0 == y1) return;
            int e = error * 2; if (e >= dy) { error += dy; x0 += sx; } if (e <= dx) { error += dx; y0 += sy; }
        }
    }
    static void renderInk(GuiGraphics g, byte[] ink, int x, int y, int w, int h, int color) {
        // Horizontal ink runs avoid a separate quad for every pixel.
        for (int row = 0; row < SignatureDrawing.HEIGHT; row++) {
            int start = -1;
            for (int column = 0; column <= SignatureDrawing.WIDTH; column++) {
                boolean marked = column < SignatureDrawing.WIDTH && SignatureDrawing.pixel(ink, column, row);
                if (marked && start < 0) start = column;
                if (!marked && start >= 0) {
                    int x0 = x + start * w / SignatureDrawing.WIDTH, x1 = x + (column * w + SignatureDrawing.WIDTH - 1) / SignatureDrawing.WIDTH;
                    int y0 = y + row * h / SignatureDrawing.HEIGHT, y1 = y + ((row + 1) * h + SignatureDrawing.HEIGHT - 1) / SignatureDrawing.HEIGHT;
                    g.fill(x0, y0, x1, y1, color); start = -1;
                }
            }
        }
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { output.add(NarratedElementType.TITLE, getMessage()); }
}
