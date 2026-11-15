package pt.realmnotifications.client;

/** Panel-relative settings geometry, including space for navigation and save errors. */
public record SettingsLayout(int contentY, int footerY, int rowStep, int rowsPerPage) {
    public static final int OPTIONS = 6, CONTROL_HEIGHT = 17;

    public static SettingsLayout of(int panelHeight) {
        boolean compact = panelHeight < 300;
        int content = compact ? 61 : 80;
        int footer = panelHeight - 30;
        int step = compact ? 19 : 28;
        int rows = OPTIONS;
        if (content + (OPTIONS - 1) * step + CONTROL_HEIGHT > footer - 14) {
            rows = Math.clamp((footer - 37 - content - CONTROL_HEIGHT) / step + 1, 1, OPTIONS);
        }
        return new SettingsLayout(content, footer, step, rows);
    }

    public int pages() { return (OPTIONS + rowsPerPage - 1) / rowsPerPage; }
    public int page(int requested) { return Math.clamp(requested, 0, pages() - 1); }
    public int first(int page) { return page(page) * rowsPerPage; }
    public int end(int page) { return Math.min(OPTIONS, first(page) + rowsPerPage); }
    public int rowY(int row) { return contentY + row * rowStep; }
    public int navigationY() { return footerY - 33; }
}
