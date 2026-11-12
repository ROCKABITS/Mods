package pt.contracts.client;

/** GUI-scaled catalogue geometry. Minecraft's minimum 320 x 240 viewport is supported. */
public record ItemPickerLayout(int x, int y, int width, int height, int columns, int rows, int cellWidth) {
    public static final int TOP = 110, CELL_HEIGHT = 48, GAP = 6;
    public record Cell(int x, int y, int width, int height) {}
    public static ItemPickerLayout of(int screenWidth, int screenHeight) {
        int width = Math.min(610, screenWidth - 16), height = Math.min(390, screenHeight - 16);
        int columns = Math.max(1, (width - 18) / 76);
        int rows = Math.max(1, (height - 49 - TOP + GAP) / (CELL_HEIGHT + GAP));
        return new ItemPickerLayout((screenWidth - width) / 2, (screenHeight - height) / 2,
                width, height, columns, rows, (width - 24 - (columns - 1) * GAP) / columns);
    }
    public int capacity() { return columns * rows; }
    public int pages(int count) { return Math.max(1, (count + capacity() - 1) / capacity()); }
    public int page(int requested, int count) { return Math.clamp(requested, 0, pages(count) - 1); }
    public int footerY() { return y + height - 32; }
    public Cell cell(int index) {
        return new Cell(x + 12 + (index % columns) * (cellWidth + GAP),
                y + TOP + (index / columns) * (CELL_HEIGHT + GAP), cellWidth, CELL_HEIGHT);
    }
}
