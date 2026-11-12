package pt.contracts.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Local selection only: the existing publish request still sends one server-validated item ID. */
public final class ContractItemPickerScreen extends Screen {
    private final ContractsScreen parent;
    private final List<ItemSearch.Entry> catalogue;
    private ItemPickerLayout layout;
    private List<ItemSearch.Entry> results;
    private EditBox search;
    private String query = "";
    private boolean inventoryOnly;
    private int page;
    private final List<ItemButton> itemButtons = new ArrayList<>();
    private Button previous, next;

    public ContractItemPickerScreen(ContractsScreen parent) {
        super(tr("picker_title"));
        this.parent = parent;
        Map<String, Integer> owned = new HashMap<>();
        var player = Minecraft.getInstance().player;
        if (player != null) for (var stack : player.getInventory().items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, new ItemStack(stack.getItem())))
                owned.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        var entries = new ArrayList<ItemSearch.Entry>();
        for (var item : BuiltInRegistries.ITEM) if (item != Items.AIR) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            // The existing packet bounds an item ID to 128 characters.
            if (id.length() > 128 || new ItemStack(item).isEmpty()) continue;
            entries.add(new ItemSearch.Entry(id, item.getDescription().getString(),
                    item.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).toList(), owned.getOrDefault(id, 0)));
        }
        entries.sort(Comparator.comparing((ItemSearch.Entry e) -> ItemSearch.normalize(e.name())).thenComparing(ItemSearch.Entry::id));
        catalogue = List.copyOf(entries);
        results = catalogue;
    }

    public ContractsScreen parent() { return parent; }
    private static Component tr(String key, Object... args) { return Component.translatable("gui.contracts." + key, args); }

    @Override protected void init() {
        layout = ItemPickerLayout.of(width, height);
        int x = layout.x(), y = layout.y(), w = layout.width();
        search = addRenderableWidget(new EditBox(font, x + 12, y + 48, w - 24, 20, tr("picker_search")));
        search.setMaxLength(128);
        search.setValue(query);
        search.setHint(tr("picker_search"));
        search.setTooltip(Tooltip.create(tr("picker_search_hint")));
        search.setResponder(value -> { query = value; page = 0; filter(); });
        int tabWidth = (w - 30) / 2;
        var all = addRenderableWidget(DailyTheme.button(x + 12, y + 75, tabWidth, 18, tr("picker_all"), () -> mode(false)));
        var inventory = addRenderableWidget(DailyTheme.button(x + 18 + tabWidth, y + 75, tabWidth, 18, tr("picker_inventory"), () -> mode(true)));
        all.active = inventoryOnly;
        inventory.active = !inventoryOnly;
        inventory.setTooltip(Tooltip.create(tr("picker_inventory_hint")));
        itemButtons.clear();
        for (int i = 0; i < layout.capacity(); i++) {
            var cell = layout.cell(i);
            itemButtons.add(addRenderableWidget(new ItemButton(cell)));
        }
        int footer = layout.footerY();
        addRenderableWidget(DailyTheme.button(x + 12, footer, 66, 20, tr("back"), this::onClose));
        previous = addRenderableWidget(DailyTheme.button(x + w - 132, footer, 54, 20, Component.literal("←"), () -> move(-1)));
        previous.setTooltip(Tooltip.create(tr("previous")));
        next = addRenderableWidget(DailyTheme.button(x + w - 72, footer, 60, 20, Component.literal("→"), () -> move(1)));
        next.setTooltip(Tooltip.create(tr("next")));
        filter();
        setInitialFocus(search);
    }

    private void mode(boolean inventory) { inventoryOnly = inventory; page = 0; rebuildWidgets(); }
    private void filter() {
        results = ItemSearch.filter(catalogue, query, inventoryOnly);
        page = layout.page(page, results.size());
        updatePage();
    }
    private void move(int direction) {
        page = layout.page(page + direction, results.size());
        updatePage();
    }
    private void updatePage() {
        int start = page * layout.capacity();
        for (int i = 0; i < itemButtons.size(); i++) {
            var button = itemButtons.get(i);
            button.entry = start + i < results.size() ? results.get(start + i) : null;
            button.visible = button.active = button.entry != null;
            if (button.entry != null) {
                button.setMessage(Component.literal(button.entry.name()));
                button.setTooltip(Tooltip.create(Component.literal(button.entry.name()).append("\n" + button.entry.id())
                        .append("\n").append(tr("picker_owned", button.entry.owned()))));
            }
        }
        previous.active = page > 0;
        next.active = page + 1 < layout.pages(results.size());
    }

    @Override public void tick() { parent.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g) {}
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        int x = layout.x(), y = layout.y(), w = layout.width();
        g.fill(0, 0, width, height, 0xD5101B17);
        DailyTheme.panel(g, x, y, w, layout.height());
        DailyTheme.seal(g, x + 12, y + 9, 24, "MEDIUM");
        g.drawString(font, getTitle(), x + 44, y + 13, DailyTheme.GOLD, false);
        g.drawString(font, tr("picker_search"), x + 13, y + 34, DailyTheme.PALE, false);
        g.drawString(font, tr("picker_results", results.size()), x + 13, y + 98, DailyTheme.PALE, false);
        if (results.isEmpty()) g.drawWordWrap(font, tr(inventoryOnly ? "picker_inventory_empty" : "picker_empty"),
                x + 18, y + ItemPickerLayout.TOP + 12, w - 36, DailyTheme.PALE);
        g.drawCenteredString(font, tr("picker_page", page + 1, layout.pages(results.size())),
                x + 78 + (w - 210) / 2, layout.footerY() + 6, DailyTheme.GOLD);
        super.render(g, mx, my, partial);
    }
    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        if (vertical != 0 && mx >= layout.x() && mx < layout.x() + layout.width()
                && my >= layout.y() + ItemPickerLayout.TOP && my < layout.footerY()) {
            move(vertical > 0 ? -1 : 1); return true;
        }
        return super.mouseScrolled(mx, my, vertical);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            move(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1); return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && search.isFocused() && results.size() == 1) {
            parent.selectItem(results.get(0).id()); return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private final class ItemButton extends AbstractButton {
        private ItemSearch.Entry entry;
        ItemButton(ItemPickerLayout.Cell cell) { super(cell.x(), cell.y(), cell.width(), cell.height(), Component.empty()); }
        @Override public void onPress() { if (entry != null) parent.selectItem(entry.id()); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            if (entry == null) return;
            int x = getX(), y = getY(), edge = isHoveredOrFocused() ? DailyTheme.GOLD : 0xFF8D805B;
            g.fill(x, y, x + getWidth(), y + getHeight(), edge);
            g.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, isHoveredOrFocused() ? 0xFFFFEAC0 : DailyTheme.PAPER);
            g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(entry.id()))), x + (getWidth() - 16) / 2, y + 7);
            String name = entry.name();
            if (font.width(name) > getWidth() - 8) name = font.plainSubstrByWidth(name, getWidth() - 8 - font.width("…")) + "…";
            g.drawCenteredString(font, name, x + getWidth() / 2, y + 32, DailyTheme.INK);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
