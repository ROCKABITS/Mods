package pt.seasoneight.client;

import java.util.List;

/** GUI-scaled coordinates; shared by rendering and the screen-size regression tests. */
public record MenuLayout(int width, int height, int margin, int contentWidth, int logoY,
                         int logoHeight, boolean artwork, boolean description, List<Box> controls) {
    public record Box(int x, int y, int w, int h) {
        public boolean overlaps(Box b) { return x < b.x + b.w && x + w > b.x && y < b.y + b.h && y + h > b.y; }
    }
    public static MenuLayout of(int w, int h) {
        boolean compact = h < 430;
        int margin = Math.max(12, Math.min(54, w / 15));
        int column = Math.min(compact ? 208 : 256, w - margin * 2);
        int logoY = compact ? 16 : Math.max(48, h / 7);
        int logoH = compact ? (h < 220 ? 34 : 52) : 80;
        int gap = h < 220 ? 3 : compact ? 4 : 7;
        int primary = h < 220 ? 18 : compact ? 21 : 32;
        int secondary = h < 220 ? 16 : compact ? 18 : 24;
        int total = primary * 2 + secondary * 3 + gap * 4;
        int start = compact ? h - 28 - total : Math.max(logoY + logoH + 65, h - 62 - total);
        start = Math.max(logoY + logoH + (h < 220 ? 6 : 15), start);
        int half = (column - gap) / 2;
        var boxes = new java.util.ArrayList<Box>();
        boxes.add(new Box(margin, start, column, primary));
        boxes.add(new Box(margin, start + primary + gap, column, primary));
        int rowsY = start + 2 * (primary + gap);
        for (int row = 0; row < 3; row++) {
            boxes.add(new Box(margin, rowsY + row * (secondary + gap), half, secondary));
            boxes.add(new Box(margin + half + gap, rowsY + row * (secondary + gap), column - half - gap, secondary));
        }
        boxes.add(new Box(w - margin - 48, 13, 48, 18));
        return new MenuLayout(w, h, margin, column, logoY, logoH,
                w - (margin + column) > 165, !compact, List.copyOf(boxes));
    }
}
