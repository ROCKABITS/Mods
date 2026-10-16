package pt.realmmarkets.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pt.realmmarkets.network.MarketNetwork;
import pt.realmmarkets.domain.SaleAmounts;

/** Only displays the inventory and prices verified by the physical stall's server session. */
public final class MarketScreen extends Screen {
    private static final int INK = 0xFF352B22, MUTED = 0xFF78684E, GOLD = 0xFFB39454;
    private static final int PAPER = 0xFFE9DCBA, LIGHT = 0xFFF8EDD2, GREEN = 0xFF3C5B42;
    private static final int RED = 0xFF8D443C, WOOD = 0xFF443225;
    private static final long MAX_PRICE = 1_000_000_000L;
    private final Map<AbstractWidget, Integer> bodyWidgets = new HashMap<>();
    private final JsonObject[] listings = new JsonObject[8];
    private final ItemStack[] listingItems = new ItemStack[8];
    private BiConsumer<String, JsonObject> requestSender = MarketNetwork::send;
    private JsonObject state;
    private EditBox priceInput, quantityInput, titleInput;
    private AbstractButton configureButton, stockButton, withdrawButton, buyButton, claimButton, renameButton;
    private int selectedSlot, x, y, w, h, bodyTop, bodyBottom, scroll, contentHeight, ticks, waitingTicks;
    private int gridLeft, gridWidth, gridTop, gridColumns, gridRows, cardWidth, cardHeight, detailLeft, detailWidth, detailTop;
    private int quantityY, priceY, sampleY, renameY, claimY;
    private String priceDraft = "", quantityDraft = "1", titleDraft, notice = "";
    private boolean waiting, building;

    private MarketScreen(JsonObject data) {
        super(tr("title"));
        state = data;
        titleDraft = str(data, "title", "");
        notice = str(data, "message", "");
        readListings();
        resetPriceDraft();
    }

    public static void receive(String json) {
        JsonObject data;
        try { data = JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException malformed) { return; }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!bool(data, "open")) {
                if (mc.screen instanceof MarketScreen) mc.setScreen(null);
                return;
            }
            if (mc.screen instanceof MarketScreen current) current.accept(data);
            else mc.setScreen(new MarketScreen(data));
        });
    }

    private void accept(JsonObject data) {
        boolean oldOwner = owner();
        int focusedField = priceInput != null && priceInput.isFocused() ? 1 : quantityInput != null && quantityInput.isFocused() ? 2 : titleInput != null && titleInput.isFocused() ? 3 : 0;
        String previousTitle = str(state, "title", "");
        String previousPrice = unitPrice() <= 0 ? "" : editPrice(unitPrice());
        boolean syncPrice = priceDraft.equals(previousPrice);
        String previousCurrency = str(state, "currency", "");
        if (titleDraft.equals(previousTitle)) titleDraft = str(data, "title", "");
        state = data;
        readListings();
        if (syncPrice || !previousCurrency.equals(str(data, "currency", ""))) resetPriceDraft();
        waiting = false; waitingTicks = 0;
        notice = str(data, "message", "");
        if (oldOwner != owner()) scroll = 0;
        rebuildWidgets();
        EditBox focused = focusedField == 1 ? priceInput : focusedField == 2 ? quantityInput : focusedField == 3 ? titleInput : null;
        if (focused != null && focused.visible) { setFocused(focused); focused.setFocused(true); }
    }

    private static Component tr(String key, Object... args) { return Component.translatable("gui.realmmarkets." + key, args); }
    private static String text(String key, Object... args) { return tr(key, args).getString(); }
    private static String str(JsonObject data, String key, String fallback) {
        try { return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : fallback; }
        catch (RuntimeException malformed) { return fallback; }
    }
    private static long number(JsonObject data, String key) {
        try { return data.has(key) ? data.get(key).getAsLong() : 0; }
        catch (RuntimeException malformed) { return 0; }
    }
    private static boolean bool(JsonObject data, String key) {
        try { return data.has(key) && data.get(key).getAsBoolean(); }
        catch (RuntimeException malformed) { return false; }
    }
    private boolean owner() { return bool(state, "owner"); }
    private boolean treasuryAvailable() { return bool(state, "treasuryAvailable"); }
    private int marketTaxBasisPoints() { return (int) number(state, "marketTaxBasisPoints"); }
    private String taxPercent() { return java.math.BigDecimal.valueOf(marketTaxBasisPoints(), 2).stripTrailingZeros().toPlainString(); }
    private SaleAmounts saleQuote() {
        if (!treasuryAvailable() || total() <= 0) return null;
        try { return SaleAmounts.quote(total(), marketTaxBasisPoints()); }
        catch (RuntimeException invalid) { return null; }
    }
    private boolean chronicles() { return "CHRONICLES".equals(str(state, "currency", "")); }
    private boolean knownCurrency() { return chronicles() || "EMERALD".equals(str(state, "currency", "")); }
    private boolean walletAvailable() { return knownCurrency() && (!chronicles() || bool(state, "chroniclesAvailable")); }
    private JsonObject selected() { return listings[selectedSlot]; }
    private boolean configured() { return selected() != null && !listingItems[selectedSlot].isEmpty(); }
    private ItemStack sample() { return item(str(state, "sample", "")); }
    private int stock() { return configured() ? (int) number(selected(), "stock") : 0; }
    private long unitPrice() { return configured() ? number(selected(), "price") : 0; }
    private void readListings() {
        for (int i = 0; i < 8; i++) { listings[i] = null; listingItems[i] = ItemStack.EMPTY; }
        if (!state.has("listings") || !state.get("listings").isJsonArray()) return;
        for (JsonElement element : state.getAsJsonArray("listings")) {
            if (!element.isJsonObject()) continue;
            JsonObject offer = element.getAsJsonObject();
            long slot = number(offer, "slot");
            if (slot < 0 || slot >= 8) continue;
            listings[(int) slot] = offer;
            listingItems[(int) slot] = item(str(offer, "item", ""));
        }
    }
    private static ItemStack item(String id) {
        try {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.get(key));
        } catch (RuntimeException malformed) { return ItemStack.EMPTY; }
    }

    @Override protected void init() {
        building = true;
        bodyWidgets.clear();
        configureButton = stockButton = withdrawButton = buyButton = claimButton = renameButton = null;
        priceInput = quantityInput = titleInput = null;
        w = Math.min(680, width - 12); h = Math.min(444, height - 12);
        x = (width - w) / 2; y = (height - h) / 2;
        bodyTop = y + 58; bodyBottom = y + h - 47;
        int inner = w - 36;
        boolean wide = w >= 500;
        gridLeft = x + 18; gridTop = 17; cardHeight = wide ? 48 : 46;
        if (wide) {
            gridWidth = Math.min(264, (inner - 18) / 2);
            gridColumns = 2; gridRows = 4;
            detailLeft = gridLeft + gridWidth + 18; detailWidth = inner - gridWidth - 18; detailTop = 0;
        } else {
            gridWidth = inner; gridColumns = inner >= 244 ? 4 : 2; gridRows = 8 / gridColumns;
            detailLeft = gridLeft; detailWidth = inner; detailTop = gridTop + gridRows * (cardHeight + 5) + 11;
        }
        cardWidth = (gridWidth - (gridColumns - 1) * 5) / gridColumns;
        for (int slot = 0; slot < 8; slot++) {
            final int chosen = slot;
            int bx = gridLeft + slot % gridColumns * (cardWidth + 5);
            int by = gridTop + slot / gridColumns * (cardHeight + 5);
            AbstractButton card = new AbstractButton(bx, bodyTop + by, cardWidth, cardHeight, tr("offer_number", slot + 1)) {
                @Override public void onPress() { select(chosen); }
                @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
                @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) { renderOffer(g, this, chosen); }
            };
            body(card, by);
        }

        quantityY = detailTop + 87;
        quantityInput = field(detailLeft + detailWidth - 75, quantityY + 12, 75, quantityDraft, "quantity", 4);
        quantityInput.setResponder(value -> { quantityDraft = value; updateActions(); });
        if (owner()) {
            int actionY = quantityY + 84, half = (detailWidth - 5) / 2;
            stockButton = bodyButton(detailLeft, actionY, half, "stock", () -> quantityAction("stock"));
            withdrawButton = bodyButton(detailLeft + half + 5, actionY, detailWidth - half - 5, "withdraw", () -> quantityAction("withdraw"));
            priceY = actionY + 30;
            priceInput = field(detailLeft + detailWidth - 112, priceY + 12, 112, priceDraft, "price_per_item", 16);
            priceInput.setResponder(value -> { priceDraft = value; updateActions(); });
            configureButton = bodyButton(detailLeft, priceY + 39, detailWidth, "configure", this::configure);
            sampleY = priceY + 68;
            renameY = sampleY + 40;
            titleInput = field(detailLeft, renameY + 12, detailWidth - 64, titleDraft, "stall_name", 40);
            titleInput.setResponder(value -> { titleDraft = value; updateActions(); });
            renameButton = bodyButton(detailLeft + detailWidth - 59, renameY + 11, 59, "save_name", this::rename);
            claimY = renameY + 44;
            claimButton = bodyButton(detailLeft, claimY + 14, detailWidth, "claim", () -> request("claim", new JsonObject()));
            contentHeight = Math.max(gridTop + gridRows * (cardHeight + 5), claimY + 49);
        } else {
            buyButton = bodyButton(detailLeft, quantityY + 84, detailWidth, "buy", () -> quantityAction("buy"));
            contentHeight = Math.max(gridTop + gridRows * (cardHeight + 5), quantityY + 170);
        }
        addRenderableWidget(button(x + w - 94, y + h - 30, 78, "close", this::onClose));
        building = false;
        positionBody();
        updateActions();
    }

    private EditBox field(int fx, int fy, int fw, String initial, String label, int maxLength) {
        EditBox box = new EditBox(font, fx + 5, bodyTop + fy + 6, fw - 10, 12, tr(label));
        box.setBordered(false); box.setTextColor(INK); box.setTextColorUneditable(MUTED);
        box.setMaxLength(maxLength); box.setValue(initial);
        return body(box, fy + 6);
    }
    private <T extends AbstractWidget> T body(T widget, int relativeY) {
        bodyWidgets.put(widget, relativeY);
        return addRenderableWidget(widget);
    }
    private AbstractButton bodyButton(int bx, int relativeY, int bw, String key, Runnable action) {
        return body(button(bx, bodyTop + relativeY, bw, key, action), relativeY);
    }
    private void positionBody() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (bodyBottom - bodyTop))));
        for (var entry : bodyWidgets.entrySet()) {
            AbstractWidget widget = entry.getKey();
            widget.setY(bodyTop + entry.getValue() - scroll);
            widget.visible = widget.getY() >= bodyTop && widget.getY() + widget.getHeight() <= bodyBottom;
        }
    }
    private void select(int slot) {
        selectedSlot = slot;
        resetPriceDraft();
        if (priceInput != null) priceInput.setValue(priceDraft);
        updateActions();
    }
    private void resetPriceDraft() { priceDraft = unitPrice() <= 0 ? "" : editPrice(unitPrice()); }
    private String editPrice(long price) { return chronicles() ? price / 100 + "." + twoDigits(price % 100) : Long.toString(price); }

    /** A price is interpreted as integer minor units; decimal math never uses floating point. */
    private static long parsePrice(String input, boolean cents) {
        if (input == null) return -1;
        String value = input.strip();
        if (value.isEmpty() || value.length() > 16) return -1;
        long whole = 0, fraction = 0;
        int fractionDigits = -1;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.' || c == ',') {
                if (!cents || fractionDigits >= 0 || i == 0) return -1;
                fractionDigits = 0; continue;
            }
            if (c < '0' || c > '9') return -1;
            if (fractionDigits >= 0) {
                if (++fractionDigits > 2) return -1;
                fraction = fraction * 10 + c - '0';
            } else {
                whole = whole * 10 + c - '0';
                if (whole > MAX_PRICE) return -1;
            }
        }
        if (fractionDigits == 0) return -1;
        if (fractionDigits == 1) fraction *= 10;
        long result = cents ? whole * 100 + fraction : whole;
        return result >= 1 && result <= MAX_PRICE ? result : -1;
    }
    private static int parseQuantity(String input) {
        if (input == null || input.isEmpty() || input.length() > 4) return -1;
        int result = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c < '0' || c > '9') return -1;
            result = result * 10 + c - '0';
        }
        return result >= 1 && result <= 4096 ? result : -1;
    }
    private long total() {
        int quantity = parseQuantity(quantityDraft);
        if (!configured() || quantity < 0) return -1;
        try { return Math.multiplyExact(unitPrice(), quantity); }
        catch (ArithmeticException overflow) { return -1; }
    }
    private void updateActions() {
        if (building) return;
        int quantity = parseQuantity(quantityDraft);
        boolean ready = !waiting;
        if (owner()) {
            if (configureButton == null) return;
            ItemStack held = sample();
            boolean matches = configured() && !held.isEmpty() && ItemStack.isSameItem(listingItems[selectedSlot], held);
            configureButton.active = ready && knownCurrency() && !held.isEmpty() && parsePrice(priceDraft, chronicles()) > 0 && (stock() == 0 || matches);
            stockButton.active = ready && configured() && quantity > 0 && stock() + quantity <= 4096;
            withdrawButton.active = ready && configured() && quantity > 0 && quantity <= stock();
            claimButton.active = ready && walletAvailable() && number(state, "earnings") > 0;
            renameButton.active = ready && validTitle(titleDraft) && !titleDraft.strip().equals(str(state, "title", ""));
        } else if (buyButton != null) buyButton.active = ready && treasuryAvailable() && saleQuote() != null && walletAvailable() && quantity > 0 && quantity <= stock() && total() > 0 && total() <= number(state, "balance");
    }
    private static boolean validTitle(String title) {
        if (title == null || title.strip().isEmpty() || title.length() > 40) return false;
        for (int i = 0; i < title.length(); i++) if (Character.isISOControl(title.charAt(i)) || title.charAt(i) == '§') return false;
        return true;
    }
    private void configure() {
        long price = parsePrice(priceDraft, chronicles());
        if (!owner() || configureButton == null || !configureButton.active || price < 1) return;
        JsonObject payload = new JsonObject(); payload.addProperty("slot", selectedSlot); payload.addProperty("price", price);
        request("configure", payload);
    }
    private void quantityAction(String operation) {
        int quantity = parseQuantity(quantityDraft);
        AbstractButton target = switch (operation) { case "stock" -> stockButton; case "withdraw" -> withdrawButton; case "buy" -> buyButton; default -> null; };
        if (target == null || !target.active || quantity < 1) return;
        JsonObject payload = new JsonObject(); payload.addProperty("slot", selectedSlot); payload.addProperty("quantity", quantity);
        if ("buy".equals(operation)) {
            payload.addProperty("price", unitPrice());
            payload.addProperty("item", str(selected(), "item", ""));
            payload.addProperty("marketTaxBasisPoints", marketTaxBasisPoints());
        }
        request(operation, payload);
    }
    private void rename() {
        if (!owner() || renameButton == null || !renameButton.active) return;
        JsonObject payload = new JsonObject(); payload.addProperty("title", titleDraft.strip());
        request("rename", payload);
    }
    private void request(String operation, JsonObject payload) {
        if (waiting) return;
        waiting = true; waitingTicks = 0;
        requestSender.accept(operation, payload);
        updateActions();
    }

    @Override public void tick() {
        super.tick(); ticks++;
        if (waiting && ++waitingTicks >= 200) { waiting = false; notice = "gui.realmmarkets.wait_timeout"; updateActions(); }
        if (ticks % 100 == 0 && !waiting) request("refresh", new JsonObject());
    }
    @Override public void onClose() { requestSender.accept("close", new JsonObject()); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xD218211B);
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x90000000);
        g.fill(x, y, x + w, y + h, WOOD);
        for (int line = 6; line < h; line += 14) g.fill(x + 2, y + line, x + w - 2, y + line + 1, 0xFF503A29);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, GOLD);
        g.fill(x + 7, y + 7, x + w - 7, y + h - 7, PAPER);
        renderAwning(g);
        g.drawString(font, getTitle(), x + 19, y + 15, LIGHT, false);
        String badge = text(owner() ? "owner_mode" : "buyer_mode");
        g.drawString(font, badge, x + w - 19 - font.width(badge), y + 15, 0xFFE1D0A3, false);
        int balanceWidth = Math.min(w / 2 - 28, 220);
        String balance = text("wallet", walletAvailable() ? money(number(state, "balance")) : "—");
        g.drawString(font, fit(str(state, "title", ""), w - balanceWidth - 48), x + 19, y + 38, INK, false);
        g.drawString(font, fit(balance, balanceWidth), x + w - 19 - Math.min(balanceWidth, font.width(balance)), y + 38, GREEN, false);
        g.fill(x + 16, y + 51, x + w - 16, y + 52, GOLD);

        g.enableScissor(x + 12, bodyTop, x + w - 12, bodyBottom);
        renderBody(g);
        super.render(g, mouseX, mouseY, partialTick);
        g.disableScissor();
        // The close button is outside the clipped body and is drawn separately.
        for (var child : children()) if (child instanceof AbstractWidget widget && !bodyWidgets.containsKey(widget)) widget.render(g, mouseX, mouseY, partialTick);
        renderScroll(g);
        g.fill(x + 16, bodyBottom + 3, x + w - 16, bodyBottom + 4, GOLD);
        String message = waiting ? text("waiting") : !notice.isBlank() ? Component.translatable(notice).getString()
                : !treasuryAvailable() ? text("treasury_unavailable") : !knownCurrency() ? text("currency_unavailable") : text(owner() ? "owner_hint" : "buyer_hint");
        g.drawString(font, fit(message, w - 36), x + 18, y + h - 41, waiting ? MUTED : GREEN, false);
        g.drawString(font, tr(!knownCurrency() ? "currency_unavailable" : chronicles() ? "currency_chronicles" : "currency_emerald"), x + 18, y + h - 22, MUTED, false);
        if (mouseY >= bodyTop && mouseY < bodyBottom) {
            for (int i = 0; i < 8; i++) {
                if (listingItems[i].isEmpty()) continue;
                int bx = gridLeft + i % gridColumns * (cardWidth + 5), by = bodyTop + gridTop + i / gridColumns * (cardHeight + 5) - scroll;
                if (mouseX >= bx && mouseX < bx + cardWidth && mouseY >= by && mouseY < by + cardHeight)
                    g.renderTooltip(font, listingItems[i], mouseX, mouseY);
            }
        }
    }

    private void renderAwning(GuiGraphics g) {
        int left = x + 9, top = y + 9, right = x + w - 9;
        g.fill(left, top, right, top + 23, GREEN);
        for (int stripe = left; stripe < right; stripe += 30) {
            g.fill(stripe, top, Math.min(stripe + 13, right), top + 22, 0xFF45664A);
            g.fill(stripe, top + 20, Math.min(stripe + 13, right), top + 23, 0xFF314B37);
        }
        g.fill(left, top + 23, right, top + 24, GOLD);
    }
    private void renderBody(GuiGraphics g) {
        int gy = bodyTop - scroll;
        g.drawString(font, tr("offers"), gridLeft, gy, MUTED, false);
        if (w >= 500) g.fill(detailLeft - 10, gy, detailLeft - 9, gy + contentHeight - 10, 0xFFC9B88E);
        int dy = gy + detailTop;
        g.drawString(font, tr("offer_number", selectedSlot + 1), detailLeft, dy, MUTED, false);
        ItemStack selectedItem = listingItems[selectedSlot];
        g.fill(detailLeft, dy + 14, detailLeft + 24, dy + 38, LIGHT);
        g.fill(detailLeft, dy + 37, detailLeft + 24, dy + 38, GOLD);
        if (!selectedItem.isEmpty()) g.renderItem(selectedItem, detailLeft + 4, dy + 18);
        else drawPlus(g, detailLeft + 12, dy + 26, MUTED);
        String name = configured() ? selectedItem.getHoverName().getString() : text("empty_offer");
        g.drawString(font, fit(name, detailWidth - 32), detailLeft + 32, dy + 17, INK, false);
        g.drawString(font, tr("stock_count", stock()), detailLeft + 32, dy + 30, MUTED, false);
        g.drawString(font, fit(text("unit_price", configured() ? money(unitPrice()) : "—"), detailWidth), detailLeft, dy + 44, GREEN, false);
        g.drawString(font, fit(treasuryAvailable() ? text("market_tax_rate", taxPercent()) : text("treasury_unavailable_short"), detailWidth), detailLeft, dy + 59, treasuryAvailable() ? MUTED : RED, false);
        g.drawString(font, fit(text(chronicles() ? "tax_rounding_chronicles" : "tax_rounding_emerald"), detailWidth), detailLeft, dy + 72, MUTED, false);
        int qy = gy + quantityY;
        g.drawString(font, tr("quantity"), detailLeft, qy + 17, INK, false);
        fieldBackground(g, quantityInput);
        SaleAmounts quote = saleQuote();
        g.drawString(font, fit(text("total", total() > 0 ? money(total()) : "—"), detailWidth), detailLeft, qy + 35, GREEN, false);
        g.drawString(font, fit(text("tax_total", quote == null ? "—" : money(quote.tax())), detailWidth), detailLeft, qy + 49, MUTED, false);
        g.drawString(font, fit(text("seller_net", quote == null ? "—" : money(quote.net())), detailWidth), detailLeft, qy + 63, GREEN, false);
        if (owner()) {
            int py = gy + priceY;
            g.drawString(font, tr("price_per_item"), detailLeft, py, MUTED, false);
            g.drawString(font, tr(chronicles() ? "price_hint_chronicles" : "price_hint_emerald"), detailLeft, py + 17, MUTED, false);
            fieldBackground(g, priceInput);
            int sy = gy + sampleY;
            g.drawString(font, tr("held_sample"), detailLeft, sy, MUTED, false);
            ItemStack sample = sample();
            if (!sample.isEmpty()) g.renderItem(sample, detailLeft, sy + 12);
            g.drawString(font, fit(sample.isEmpty() ? text("hold_plain_item") : sample.getHoverName().getString(), detailWidth - 23), detailLeft + 23, sy + 16, sample.isEmpty() ? RED : INK, false);
            g.drawString(font, tr("stall_name"), detailLeft, gy + renameY, MUTED, false);
            fieldBackground(g, titleInput);
            g.drawString(font, fit(text("earnings", money(number(state, "earnings"))), detailWidth), detailLeft, gy + claimY, GREEN, false);
        } else {
            paragraph(g, text(!treasuryAvailable() ? "treasury_unavailable" : !knownCurrency() ? "currency_unavailable" : !walletAvailable() ? "chronicles_unavailable" : "buyer_explanation"), detailLeft, qy + 114, detailWidth, MUTED);
        }
    }
    private void renderOffer(GuiGraphics g, AbstractButton card, int slot) {
        int bx = card.getX(), by = card.getY(), cw = card.getWidth(), ch = card.getHeight();
        boolean selected = selectedSlot == slot, configured = listings[slot] != null && !listingItems[slot].isEmpty();
        g.fill(bx, by, bx + cw, by + ch, selected ? GREEN : card.isHoveredOrFocused() ? GOLD : 0xFFC7B38C);
        g.fill(bx + 1, by + 1, bx + cw - 1, by + ch - 1, selected ? 0xFFF6E9C7 : 0xFFE1D2AA);
        g.fill(bx + 2, by + ch - 14, bx + cw - 2, by + ch - 2, selected ? 0xFFD8DDB9 : 0xFFD5C399);
        if (configured) {
            g.renderItem(listingItems[slot], bx + 5, by + 5);
            String name = listingItems[slot].getHoverName().getString();
            if (cw >= 95) {
                g.drawString(font, fit(name, cw - 29), bx + 26, by + 5, INK, false);
                g.drawString(font, tr("stock_short", number(listings[slot], "stock")), bx + 26, by + 17, MUTED, false);
            } else {
                g.drawString(font, Long.toString(number(listings[slot], "stock")), bx + 25, by + 9, MUTED, false);
            }
            long price = number(listings[slot], "price");
            if (knownCurrency() && !chronicles()) {
                g.pose().pushPose(); g.pose().translate(bx + 4, by + ch - 13, 0); g.pose().scale(0.6f, 0.6f, 1);
                g.renderItem(new ItemStack(Items.EMERALD), 0, 0); g.pose().popPose();
                g.drawString(font, fit(Long.toString(price), cw - 20), bx + 17, by + ch - 11, GREEN, false);
            } else g.drawString(font, fit(money(price), cw - 8), bx + 4, by + ch - 11, GREEN, false);
        } else {
            drawPlus(g, bx + cw / 2, by + 14, 0xFF9E8C6C);
            g.drawCenteredString(font, fit(text("empty_short"), cw - 8), bx + cw / 2, by + ch - 11, MUTED);
        }
        if (selected) { g.fill(bx, by, bx + 3, by + ch, GREEN); g.fill(bx + cw - 3, by, bx + cw, by + ch, GREEN); }
    }
    private void fieldBackground(GuiGraphics g, EditBox box) {
        if (box == null) return;
        g.fill(box.getX() - 5, box.getY() - 6, box.getX() + box.getWidth() + 5, box.getY() + box.getHeight() + 4, box.isFocused() ? GREEN : GOLD);
        g.fill(box.getX() - 4, box.getY() - 5, box.getX() + box.getWidth() + 4, box.getY() + box.getHeight() + 3, LIGHT);
    }
    private void renderScroll(GuiGraphics g) {
        int viewport = bodyBottom - bodyTop, maximum = Math.max(0, contentHeight - viewport);
        if (maximum == 0) return;
        int thumb = Math.max(16, viewport * viewport / contentHeight), ty = bodyTop + (viewport - thumb) * scroll / maximum;
        g.fill(x + w - 13, bodyTop, x + w - 12, bodyBottom, 0xFFC4B38A);
        g.fill(x + w - 14, ty, x + w - 11, ty + thumb, GOLD);
    }
    private static void drawPlus(GuiGraphics g, int px, int py, int color) {
        g.fill(px - 4, py - 1, px + 5, py + 1, color); g.fill(px - 1, py - 4, px + 1, py + 5, color);
    }
    private void paragraph(GuiGraphics g, String value, int px, int py, int available, int color) {
        for (var line : font.split(Component.literal(value), Math.max(40, available))) { g.drawString(font, line, px, py, color, false); py += 11; }
    }
    private String fit(String value, int available) {
        return font.width(value) <= available ? value : font.plainSubstrByWidth(value, Math.max(0, available - font.width("…"))) + "…";
    }
    private static String twoDigits(long value) { return value < 10 ? "0" + value : Long.toString(value); }
    private String money(long amount) {
        if (amount < 0 || !knownCurrency()) return "—";
        return chronicles() ? text("euro_amount", amount / 100, twoDigits(amount % 100)) : text("emerald_amount", amount);
    }
    private AbstractButton button(int bx, int by, int bw, String key, Runnable action) {
        return new AbstractButton(bx, by, bw, 20, tr(key)) {
            @Override public void onPress() { if (active) action.run(); }
            @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
            @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHoveredOrFocused() && active ? 0xFFE0C780 : GOLD);
                g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, active ? GREEN : 0xFFB8AA88);
                g.drawCenteredString(font, fit(getMessage().getString(), getWidth() - 8), getX() + getWidth() / 2, getY() + 6, active ? LIGHT : 0xFF766A51);
            }
        };
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= x && mouseX < x + w && mouseY >= bodyTop && mouseY < bodyBottom) {
            scroll -= (int) (vertical * 28); positionBody(); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < bodyTop || mouseY >= bodyBottom) {
            for (var child : children()) if (child instanceof AbstractWidget widget && !bodyWidgets.containsKey(widget) && widget.mouseClicked(mouseX, mouseY, button)) return true;
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
