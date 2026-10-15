package pt.contracts.client;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import pt.contracts.domain.Ledger;
import pt.contracts.network.ContractsNetwork;

/** Client-only parchment board. All mutations and inventory checks remain on the server. */
public final class ContractsScreen extends Screen {
    private static final Gson JSON = new Gson();
    private static final int INK = 0xFF392B20, MUTED = 0xFF715F48, EDGE = 0xFF92734A;
    private static final int PAPER = 0xFFEAD7AD, LIGHT = 0xFFF5E6C9, ACCENT = 0xFF315D48;
    private Snapshot snapshot;
    private boolean creating;
    private boolean waiting;
    private long waitingSince;
    private String pendingOperation = "";
    private int x, y, w, h, rowOffset, visibleRows;
    private int pendingRowOffset = -1;
    private String titleValue = "", itemValue = "minecraft:oak_log", quantityValue = "64", rewardValue = "20";
    private String notice = "";
    private EditBox titleField, itemField, quantityField, rewardField;

    private ContractsScreen(Snapshot snapshot) {
        super(tr("title"));
        this.snapshot = snapshot;
        this.notice = safe(snapshot.message);
    }

    /** Called by the client packet handler, including when opening the board for the first time. */
    public static void receive(String json) {
        Snapshot incoming;
        try {
            incoming = JSON.fromJson(json, Snapshot.class);
            if (incoming == null) return;
            if (incoming.contracts == null) incoming.contracts = new ArrayList<>();
            if (incoming.deliveries == null) incoming.deliveries = new ArrayList<>();
        } catch (RuntimeException malformed) {
            return;
        }
        final Snapshot data = incoming;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof ContractsScreen current) current.accept(data);
            else minecraft.setScreen(new ContractsScreen(data));
        });
    }

    private void accept(Snapshot data) {
        capture();
        if ("publish".equals(pendingOperation) && "gui.contracts.published".equals(data.message)) {
            creating = false;
            titleValue = "";
            titleField = null;
        }
        if (data.page != snapshot.page) rowOffset = 0;
        snapshot = data;
        if (pendingRowOffset >= 0) {
            rowOffset = pendingRowOffset == Integer.MAX_VALUE
                    ? Math.max(0, ((data.contracts.size() - 1) / Math.max(1, visibleRows)) * Math.max(1, visibleRows))
                    : pendingRowOffset;
            pendingRowOffset = -1;
        }
        waiting = false;
        pendingOperation = "";
        notice = safe(data.message);
        rebuildWidgets();
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static Component tr(String key, Object... args) { return Component.translatable("gui.contracts." + key, args); }
    private String text(String key, Object... args) { return tr(key, args).getString(); }
    private boolean chronicles() { return "CHRONICLES".equals(snapshot.currency); }
    private void capture() {
        if (titleField != null) titleValue = titleField.getValue();
        if (itemField != null) itemValue = itemField.getValue();
        if (quantityField != null) quantityValue = quantityField.getValue();
        if (rewardField != null) rewardValue = rewardField.getValue();
    }

    @Override protected void init() {
        capture();
        titleField = itemField = quantityField = rewardField = null;
        w = Math.min(610, width - 16);
        h = Math.min(390, height - 16);
        x = (width - w) / 2;
        y = (height - h) / 2;
        button(x + 10, y + 31, (w - 24) / 2, "browse", () -> switchView(false)).active = creating;
        button(x + 14 + (w - 24) / 2, y + 31, (w - 24) / 2, "create", () -> switchView(true)).active = !creating;
        if (creating) initForm(); else initBoard();
    }

    private void switchView(boolean create) {
        capture();
        creating = create;
        notice = "";
        rebuildWidgets();
    }

    private void initBoard() {
        visibleRows = Math.max(1, (h - 128) / 32);
        rowOffset = Math.max(0, Math.min(rowOffset, Math.max(0, snapshot.contracts.size() - 1)));
        for (int i = 0; i < visibleRows && rowOffset + i < snapshot.contracts.size(); i++) {
            Contract contract = snapshot.contracts.get(rowOffset + i);
            int rowY = y + 56 + i * 32;
            boolean own = safe(contract.author).equals(snapshot.me);
            PaperButton action = button(x + w - 82, rowY + 6, 69, own ? "cancel" : "deliver",
                    () -> send(own ? "cancel" : "complete", safe(contract.id), "", "", 0, 0, safe(contract.currency)));
            action.active = !waiting;
            action.setTooltip(Tooltip.create(Component.literal(contractDetails(contract))));
        }
        int controls = y + h - 61;
        PaperButton previous = button(x + 10, controls, 54, "previous", () -> move(-1));
        previous.active = !waiting && (rowOffset > 0 || snapshot.page > 0);
        PaperButton next = button(x + w - 64, controls, 54, "next", () -> move(1));
        next.active = !waiting && (rowOffset + visibleRows < snapshot.contracts.size() || snapshot.page + 1 < snapshot.pages);
        PaperButton refresh = button(x + 68, controls, 61, "refresh", () -> {
            waiting = false;
            send("refresh", "", "", "", 0, 0, "");
        });
        refresh.setTooltip(Tooltip.create(tr("refresh_hint")));
        int claimWidth = (w - 28) / 3;
        PaperButton emeralds = button(x + 10, y + h - 38, claimWidth, "claim_emeralds", () ->
                send("claim_money", "", "", "", 0, 0, "EMERALD"));
        emeralds.active = !waiting && snapshot.emeraldCredit > 0;
        emeralds.setTooltip(Tooltip.create(tr("credit", money(snapshot.emeraldCredit, "EMERALD"))));
        PaperButton coins = button(x + 14 + claimWidth, y + h - 38, claimWidth, "claim_coins", () ->
                send("claim_money", "", "", "", 0, 0, "CHRONICLES"));
        coins.active = !waiting && snapshot.chroniclesCredit > 0;
        coins.setTooltip(Tooltip.create(tr("credit", money(snapshot.chroniclesCredit, "CHRONICLES"))));
        PaperButton deliveries = button(x + 18 + 2 * claimWidth, y + h - 38, claimWidth, "claim_items", () ->
                send("claim_items", "", "", "", 0, 0, ""));
        deliveries.active = !waiting && !snapshot.deliveries.isEmpty();
        deliveries.setTooltip(Tooltip.create(Component.literal(deliveryDetails())));
    }

    private void initForm() {
        int fieldX = x + 12, fieldW = w - 24;
        titleField = field(fieldX, y + 69, fieldW, "label_title", titleValue, 64);
        titleField.setHint(tr("title_hint"));
        itemField = field(fieldX, y + 105, fieldW, "label_item", itemValue, 128);
        itemField.setHint(Component.literal("minecraft:oak_log"));
        itemField.setTooltip(Tooltip.create(tr("item_hint")));
        int half = (fieldW - 10) / 2;
        quantityField = field(fieldX, y + 141, half, "label_quantity", quantityValue, 4);
        quantityField.setFilter(value -> value.matches("[0-9]*"));
        quantityField.setTooltip(Tooltip.create(tr("quantity_hint")));
        rewardField = field(fieldX + half + 10, y + 141, half, "label_reward", rewardValue, 18);
        rewardField.setFilter(value -> value.matches("[0-9]*([.,][0-9]{0,2})?"));
        rewardField.setTooltip(Tooltip.create(tr(chronicles() ? "reward_coins_hint" : "reward_emeralds_hint")));
        PaperButton publish = button(x + w - 123, y + h - 61, 111, "publish", this::publish);
        publish.active = !waiting;
        button(x + 12, y + h - 61, 76, "back", () -> switchView(false));
        setInitialFocus(titleField);
    }

    private EditBox field(int fieldX, int fieldY, int fieldW, String label, String value, int maxLength) {
        EditBox field = addRenderableWidget(new EditBox(font, fieldX, fieldY, fieldW, 20, tr(label)));
        field.setMaxLength(maxLength);
        field.setValue(value);
        field.setTextColor(0xFFF5E6C9);
        return field;
    }

    private void publish() {
        capture();
        try {
            String title = titleValue.trim();
            if (title.isEmpty() || title.length() > 64
                    || title.chars().anyMatch(c -> Character.isISOControl(c) || c == '\u00a7')) {
                invalid("error_title"); return;
            }
            ResourceLocation id = ResourceLocation.tryParse(itemValue.trim());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || "minecraft:air".equals(id.toString())) {
                invalid("error_item"); return;
            }
            int quantity = Integer.parseInt(quantityValue);
            if (quantity < 1 || quantity > 4096) { invalid("error_quantity"); return; }
            BigDecimal input = new BigDecimal(rewardValue.replace(',', '.'));
            long reward = (chronicles() ? input.movePointRight(2) : input).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (reward <= 0) { invalid("error_reward"); return; }
            if (reward > Ledger.MAX_REWARD) {
                notice = text("error_reward_limit", money(Ledger.MAX_REWARD, snapshot.currency));
                return;
            }
            send("publish", "", title, id.toString(), quantity, reward, safe(snapshot.currency));
        } catch (NumberFormatException invalidNumber) {
            invalid(quantityValue.isEmpty() ? "error_quantity" : "error_reward");
        } catch (ArithmeticException invalidAmount) {
            invalid(chronicles() ? "error_reward" : "error_whole_emeralds");
        }
    }

    private void invalid(String key) { notice = "gui.contracts." + key; }
    private void send(String operation, String id, String title, String item, int quantity, long reward, String currency) {
        waiting = true;
        waitingSince = System.nanoTime();
        pendingOperation = operation;
        notice = "gui.contracts.waiting";
        ContractsNetwork.send(operation, id, title, item, quantity, reward, currency);
        rebuildWidgets();
    }

    private void move(int direction) {
        if (direction < 0 && rowOffset > 0) {
            rowOffset = Math.max(0, rowOffset - visibleRows);
            rebuildWidgets();
        } else if (direction > 0 && rowOffset + visibleRows < snapshot.contracts.size()) {
            rowOffset += visibleRows;
            rebuildWidgets();
        } else {
            int target = snapshot.page + direction;
            if (target < 0 || target >= snapshot.pages) return;
            pendingRowOffset = direction < 0 ? Integer.MAX_VALUE : 0;
            send("page", "", "", "", target, 0, "");
        }
    }

    private String itemName(String item) {
        ResourceLocation id = ResourceLocation.tryParse(safe(item));
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.get(id).getDescription().getString() : safe(item);
    }
    private String money(long value, String currency) {
        if ("CHRONICLES".equals(currency)) return BigDecimal.valueOf(value, 2).toPlainString() + " €";
        return text("emerald_amount", value);
    }
    private String contractDetails(Contract contract) {
        return safe(contract.title) + "\n" + text("author", safe(contract.authorName)) + "\n"
                + contract.quantity + " × " + itemName(contract.item) + "\n"
                + text("reward_amount", money(contract.reward, contract.currency));
    }
    private String deliveryDetails() {
        if (snapshot.deliveries.isEmpty()) return text("no_deliveries");
        StringBuilder result = new StringBuilder(text("deliveries_hint"));
        int shown = 0;
        for (Delivery delivery : snapshot.deliveries) {
            if (shown++ == 8) { result.append("\n…"); break; }
            result.append('\n').append(delivery.quantity).append(" × ").append(itemName(delivery.item));
        }
        return result.toString();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xAB17130F);
        frame(g, x, y, w, h, PAPER, EDGE);
        g.fill(x + 4, y + 4, x + w - 4, y + 27, 0xFFDFC694);
        String wallet = text("wallet", money(snapshot.wallet, snapshot.currency));
        int walletWidth = Math.min(font.width(wallet), w / 2);
        line(g, getTitle().getString(), x + 11, y + 10, w - walletWidth - 38, INK);
        line(g, wallet, x + w - walletWidth - 11, y + 11, walletWidth, INK);
        if (creating) renderForm(g); else renderBoard(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        if (!creating) renderRowTooltip(g, mouseX, mouseY);
        if (!notice.isEmpty()) {
            String translated = Component.translatable(notice).getString();
            line(g, translated, x + 10, y + h - 12, w - 20, ACCENT);
            if (mouseY >= y + h - 14 && mouseY <= y + h && mouseX >= x && mouseX <= x + w && font.width(translated) > w - 20)
                g.renderTooltip(font, font.split(Component.literal(translated), Math.max(120, w - 20)), mouseX, mouseY);
        }
    }

    private void renderBoard(GuiGraphics g, int mouseX, int mouseY) {
        if (snapshot.contracts.isEmpty()) {
            line(g, text("empty"), x + 14, y + 70, w - 28, MUTED);
        }
        for (int i = 0; i < visibleRows && rowOffset + i < snapshot.contracts.size(); i++) {
            Contract contract = snapshot.contracts.get(rowOffset + i);
            int rowY = y + 56 + i * 32;
            frame(g, x + 10, rowY, w - 20, 30, i % 2 == 0 ? LIGHT : 0xFFE4CFA5, 0xFFBEA375);
            int textW = w - 105;
            line(g, safe(contract.title), x + 15, rowY + 3, textW, INK);
            line(g, contract.quantity + " × " + itemName(contract.item) + " · " + money(contract.reward, contract.currency),
                    x + 15, rowY + 15, textW, MUTED);
        }
        int from = snapshot.contracts.isEmpty() ? 0 : rowOffset + 1;
        int to = Math.min(rowOffset + visibleRows, snapshot.contracts.size());
        line(g, text("page", snapshot.page + 1, Math.max(1, snapshot.pages), from, to),
                x + 135, y + h - 55, w - 205, MUTED);
    }

    private void renderRowTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX < x + 10 || mouseX >= x + w - 86 || mouseY < y + 56) return;
        int row = (mouseY - y - 56) / 32;
        if (row < 0 || row >= visibleRows || rowOffset + row >= snapshot.contracts.size()
                || (mouseY - y - 56) % 32 >= 30) return;
        g.renderTooltip(font, font.split(Component.literal(contractDetails(snapshot.contracts.get(rowOffset + row))),
                Math.min(280, w - 28)), mouseX, mouseY);
    }

    private void renderForm(GuiGraphics g) {
        line(g, text("label_title"), x + 12, y + 58, w - 24, INK);
        line(g, text("label_item"), x + 12, y + 94, w - 24, INK);
        int half = (w - 34) / 2;
        line(g, text("label_quantity"), x + 12, y + 130, half, INK);
        line(g, text(chronicles() ? "label_reward_coins" : "label_reward_emeralds"), x + 22 + half, y + 130, half, INK);
        int hintY = y + h - 36;
        var hint = font.split(tr("escrow_hint"), w - 24);
        for (int i = 0; i < Math.min(2, hint.size()); i++) g.drawString(font, hint.get(i), x + 12, hintY + i * 9, MUTED, false);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!creating && !waiting && scrollY != 0 && mouseY >= y + 56 && mouseY < y + h - 66) {
            move(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    @Override public void tick() {
        super.tick();
        if (waiting && System.nanoTime() - waitingSince >= 5_000_000_000L) {
            waiting = false;
            pendingRowOffset = -1;
            notice = "gui.contracts.no_response";
            rebuildWidgets();
        }
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!waiting && !creating && (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)) {
            move(keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
            return true;
        }
        if (!waiting && creating && hasControlDown()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            publish();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }

    private void line(GuiGraphics g, String value, int lineX, int lineY, int maxWidth, int color) {
        if (maxWidth > 0) g.drawString(font, font.plainSubstrByWidth(value, maxWidth), lineX, lineY, color, false);
    }
    private static void frame(GuiGraphics g, int fx, int fy, int fw, int fh, int color, int border) {
        g.fill(fx, fy, fx + fw, fy + fh, color);
        g.fill(fx, fy, fx + fw, fy + 1, border);
        g.fill(fx, fy + fh - 1, fx + fw, fy + fh, border);
        g.fill(fx, fy, fx + 1, fy + fh, border);
        g.fill(fx + fw - 1, fy, fx + fw, fy + fh, border);
    }
    private PaperButton button(int bx, int by, int bw, String key, Runnable action) {
        return addRenderableWidget(new PaperButton(bx, by, bw, tr(key), action));
    }
    private static final class PaperButton extends AbstractButton {
        private final Runnable action;
        PaperButton(int x, int y, int width, Component label, Runnable action) {
            super(x, y, width, 20, label);
            this.action = action;
        }
        @Override public void onPress() { action.run(); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            frame(g, getX(), getY(), width, height, !active ? 0xFFD8C7A4 : isHoveredOrFocused() ? LIGHT : 0xFFE1C697,
                    isHoveredOrFocused() && active ? ACCENT : EDGE);
            var font = Minecraft.getInstance().font;
            float scale = Math.min(1.0F, (width - 8.0F) / Math.max(1, font.width(getMessage())));
            g.pose().pushPose();
            g.pose().translate(getX() + (width - font.width(getMessage()) * scale) / 2, getY() + (height - 9 * scale) / 2, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, getMessage(), 0, 0, active ? INK : MUTED, false);
            g.pose().popPose();
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final class Snapshot {
        String me = "", currency = "EMERALD", message = "";
        long wallet, emeraldCredit, chroniclesCredit;
        int page, pages = 1;
        List<Contract> contracts = new ArrayList<>();
        List<Delivery> deliveries = new ArrayList<>();
    }
    private static final class Contract {
        String id = "", author = "", authorName = "", title = "", item = "", currency = "EMERALD", status = "";
        int quantity;
        long reward;
    }
    private static final class Delivery { String item = ""; long quantity; }
}
