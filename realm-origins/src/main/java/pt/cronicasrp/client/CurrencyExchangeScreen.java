package pt.cronicasrp.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import pt.cronicasrp.CurrencyItems;
import pt.cronicasrp.domain.CurrencyDenomination;
import pt.cronicasrp.domain.Money;
import pt.cronicasrp.network.CashPackets;

/** A separate book page for exchanging the account balance and real inventory items. */
public final class CurrencyExchangeScreen extends BookScreen {
    private CurrencyDenomination selected = CurrencyDenomination.NOTE_5;
    private boolean depositing, pending, success, error;
    private String message = UiText.s("gui.cronicasrp.cash.select");
    private RpgEditBox amount, quantity;
    private RpgButton withdrawTab, depositTab, valueButton, piecesButton, allButton;
    private final List<RpgButton> transactionButtons = new ArrayList<>();
    public CurrencyExchangeScreen() { super(UiText.s("gui.cronicasrp.title.exchange")); }
    @Override protected void init() {
        String previousAmount = amount == null ? UiText.s("gui.cronicasrp.cash.default_amount") : amount.getValue();
        String previousQuantity = quantity == null ? "1" : quantity.getValue();
        measure(); transactionButtons.clear();
        int lx = pageLeft(), rx = pageRight(), w = pageWidth();
        int cw = (w - 4) / 3, ch = Math.max(18, (footerY() - (top + 73) - 5) / 5);
        for (int i = 0; i < CurrencyDenomination.values().length; i++) {
            var denomination = CurrencyDenomination.values()[i];
            addRenderableWidget(new CashButton(lx + i % 3 * (cw + 2), top + 73 + i / 3 * ch, cw, ch - 2, denomination));
        }
        withdrawTab = button(rx, top + 45, (w - 2) / 2, UiText.s("gui.cronicasrp.button.withdraw"), false, () -> { depositing = false; updateControls(); });
        depositTab = button(rx + (w + 2) / 2, top + 45, (w - 2) / 2, UiText.s("gui.cronicasrp.button.deposit"), false, () -> { depositing = true; updateControls(); });
        amount = addRenderableWidget(new RpgEditBox(font, rx, top + (compact() ? 90 : 111), w, UiText.s("gui.cronicasrp.cash.amount")).parchment());
        if (compact()) amount.setHeight(18);
        amount.setMaxLength(24); amount.setValue(previousAmount);
        amount.setTooltip(Tooltip.create(UiText.c("gui.cronicasrp.tooltip.amount")));
        valueButton = button(rx, top + (compact() ? 110 : 134), w, UiText.s("gui.cronicasrp.button.withdraw_amount"), true, this::sendValue);
        int quantityY = top + (compact() ? 148 : 176);
        quantity = addRenderableWidget(new RpgEditBox(font, rx, quantityY, Math.max(33, w / 4), UiText.s("gui.cronicasrp.cash.quantity")).parchment());
        if (compact()) quantity.setHeight(18);
        quantity.setMaxLength(7); quantity.setValue(previousQuantity);
        quantity.setTooltip(Tooltip.create(UiText.c("gui.cronicasrp.tooltip.quantity")));
        int qw = quantity.getWidth();
        piecesButton = button(rx + qw + 4, quantityY + 1, w - qw - 4, UiText.s("gui.cronicasrp.button.withdraw_items"), false, this::sendPieces).fitLabel();
        allButton = button(rx, footerY(), w, UiText.s("gui.cronicasrp.button.deposit_all"), false,
                () -> send(new CashPackets.Request(CashPackets.DEPOSIT_ALL, 0, "", 0)));
        allButton.setTooltip(Tooltip.create(UiText.c("gui.cronicasrp.tooltip.deposit_all")));
        button(lx, footerY(), w, UiText.s("gui.cronicasrp.button.back_profile"), false, this::onClose);
        transactionButtons.add(valueButton); transactionButtons.add(piecesButton); transactionButtons.add(allButton);
        updateControls();
    }
    private boolean compact() { return bookHeight < 320; }
    private void updateControls() {
        if (withdrawTab == null) return;
        withdrawTab.selected(!depositing); depositTab.selected(depositing);
        valueButton.setMessage(Component.literal(depositing ? UiText.s("gui.cronicasrp.button.deposit_amount") : UiText.s("gui.cronicasrp.button.withdraw_amount")));
        piecesButton.setMessage(Component.literal(depositing ? UiText.s("gui.cronicasrp.button.deposit_items") : UiText.s("gui.cronicasrp.button.withdraw_items")));
        for (var button : transactionButtons) button.active = !pending;
    }
    private void sendValue() {
        try { send(new CashPackets.Request(depositing ? CashPackets.DEPOSIT : CashPackets.WITHDRAW, Money.positive(amount.getValue()), "", 0)); }
        catch (IllegalArgumentException ex) { fail(ex.getMessage()); }
    }
    private void sendPieces() {
        try {
            if (!quantity.getValue().matches("[0-9]{1,7}")) throw new IllegalArgumentException("Escreve uma quantidade inteira de peças.");
            int count = Integer.parseInt(quantity.getValue());
            if (count <= 0 || count > 1_000_000) throw new IllegalArgumentException("Escolhe entre 1 e 1.000.000 de peças.");
            send(new CashPackets.Request(depositing ? CashPackets.DEPOSIT_PIECES : CashPackets.WITHDRAW_PIECES, 0, selected.id(), count));
        } catch (IllegalArgumentException ex) { fail(ex.getMessage()); }
    }
    private void send(CashPackets.Request request) {
        if (pending) return;
        pending = true; success = false; error = false; message = UiText.s("gui.cronicasrp.cash.counting"); updateControls();
        PacketDistributor.sendToServer(request);
    }
    private void fail(String message) { success = false; error = true; this.message = UiText.error(message); }
    public void serverResult(CashPackets.Result result) {
        pending = false; success = result.success(); error = !result.success(); message = result.message().getString(); updateControls();
    }
    private long owned(CurrencyDenomination denomination) {
        if (minecraft.player == null) return 0;
        long count = 0;
        for (int i = 0; i < 36; i++) {
            var stack = minecraft.player.getInventory().getItem(i);
            if (CurrencyItems.denomination(stack) == denomination) count += stack.getCount();
        }
        var offhand = minecraft.player.getOffhandItem();
        if (CurrencyItems.denomination(offhand) == denomination) count += offhand.getCount();
        return count;
    }
    private long physical() {
        long cents = 0;
        for (var denomination : CurrencyDenomination.values()) cents += owned(denomination) * denomination.cents();
        return cents;
    }
    @Override protected void renderBook(GuiGraphics g, int mx, int my, float partialTick) {
        int lx = pageLeft(), rx = pageRight(), w = pageWidth();
        heading(g, UiText.s("gui.cronicasrp.cash.in_hand"), lx, top + 27, w, INK);
        var cash = JournalTheme.heading(JournalTheme.money(physical()));
        float scale = Math.min(1.25F, (w - 2F) / Math.max(1, font.width(cash)));
        g.pose().pushPose(); g.pose().translate(lx, top + 46, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, cash, 0, 0, INK, false); g.pose().popPose();
        text(g, UiText.s("gui.cronicasrp.cash.inventory"), lx, top + 61, w, MUTED);
        heading(g, UiText.s("gui.cronicasrp.cash.heading"), rx, top + 27, w, INK);
        text(g, UiText.s("gui.cronicasrp.cash.digital_prefix") + JournalTheme.money(ClientState.profile().balance()), rx, top + 70, w, INK);
        text(g, UiText.s("gui.cronicasrp.cash.amount"), rx, top + (compact() ? 80 : 100), w, MUTED);
        JournalTheme.rule(g, rx, top + (compact() ? 131 : 157), w, 0x668B724A);
        text(g, UiText.s("gui.cronicasrp.cash.item_prefix") + UiText.denomination(selected).getString(), rx, top + (compact() ? 136 : 164), w, INK);
        if (!compact()) {
            paragraph(g, depositing
                    ? UiText.s("gui.cronicasrp.cash.deposit_hint")
                    : UiText.s("gui.cronicasrp.cash.withdraw_hint"),
                    rx, top + 204, w, Math.max(0, footerY() - top - 260), MUTED);
        }
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        int x = pageRight(), y = footerY() - (compact() ? 13 : 48), w = pageWidth();
        int color = success ? 0xFF466F52 : error ? 0xFF864C39 : MUTED;
        if (compact()) text(g, message, x, y, w, color);
        else paragraph(g, message, x, y, w, 32, color);
        boolean clipped = compact() ? font.width(message) > w : font.split(Component.literal(message), w).size() * font.lineHeight > 32;
        if (clipped && mx >= x && mx <= x + w && my >= y && my <= y + (compact() ? 9 : 32))
            g.renderTooltip(font, Component.literal(message), mx, my);
        for (var child : children()) if (child instanceof CashButton button && button.isHoveredOrFocused())
            g.renderTooltip(font, List.of(UiText.denomination(button.denomination),
                    UiText.c("message.cronicasrp.cash.owned", owned(button.denomination), pt.cronicasrp.i18n.ModMessages.money(owned(button.denomination) * button.denomination.cents()))), java.util.Optional.empty(), mx, my);
    }
    @Override public void onClose() { minecraft.setScreen(new CharacterProfileScreen(3)); }
    @Override public boolean isPauseScreen() { return false; }
    private final class CashButton extends AbstractButton {
        private final CurrencyDenomination denomination;
        CashButton(int x, int y, int w, int h, CurrencyDenomination denomination) {
            super(x, y, w, h, UiText.denomination(denomination)); this.denomination = denomination;
        }
        @Override public void onPress() { selected = denomination; }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            boolean chosen = selected == denomination;
            JournalTheme.frame(g, getX(), getY(), width, height, chosen ? 0x779F814B : isHoveredOrFocused() ? 0x559D814B : 0x18755831,
                    chosen ? 0xFF9A742A : 0x558D734A);
            int icon = height < 23 ? 13 : 16;
            g.pose().pushPose(); g.pose().translate(getX() + 2, getY() + (height - icon) / 2F, 0); g.pose().scale(icon / 16F, icon / 16F, 1);
            g.renderItem(CurrencyItems.stack(denomination, 1), 0, 0); g.pose().popPose();
            int tx = getX() + icon + 4, available = width - icon - 5;
            float scale = Math.min(0.85F, available / (float) Math.max(font.width(denomination.shortLabel()), font.width("×" + owned(denomination))));
            g.pose().pushPose(); g.pose().translate(tx, getY() + (height - 17 * scale) / 2F, 0); g.pose().scale(scale, scale, 1);
            g.drawString(font, denomination.shortLabel(), 0, 0, INK, false);
            g.drawString(font, "×" + owned(denomination), 0, 9, MUTED, false); g.pose().popPose();
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
