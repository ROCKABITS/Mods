package pt.cronicasrp.client;

import java.util.List;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pt.cronicasrp.domain.RpProgress;
import pt.cronicasrp.domain.SignatureDrawing;
import pt.cronicasrp.CurrencyItems;

public final class CharacterProfileScreen extends BookScreen {
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation ARMOR = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private int page, storyScroll;
    private final RpgButton[] tabs = new RpgButton[4];
    private String cachedDrawing;
    private byte[] cachedInk;
    private RpgButton cashButton;
    public CharacterProfileScreen() { super(UiText.s("gui.cronicasrp.title.profile")); }
    public CharacterProfileScreen(int page) { this(); this.page = page; }
    @Override protected void init() {
        measure(); int x = pageRight(), w = pageWidth(), tw = (w - 6) / 4;
        String[] labels = {UiText.s("gui.cronicasrp.tab.profile"), UiText.s("gui.cronicasrp.tab.deeds"), bookHeight < 270 ? UiText.s("gui.cronicasrp.tab.story_short") : UiText.s("gui.cronicasrp.tab.story"), UiText.s("gui.cronicasrp.tab.wallet")};
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            tabs[i] = button(x + i * (tw + 2), top + 45, tw, labels[i], false, () -> {
                page = target; storyScroll = 0; updateTabs();
            }).fitLabel();
        }
        cashButton = button(x, footerY() - 23, w, UiText.s("gui.cronicasrp.button.exchange"), true, () -> minecraft.setScreen(new CurrencyExchangeScreen())).fitLabel();
        updateTabs();
        int identityButtonW = (pageWidth() - 4) / 2;
        button(pageLeft(), footerY(), identityButtonW, UiText.s("gui.cronicasrp.button.edit_memories"), false,
                () -> minecraft.setScreen(new CharacterCreationScreen(true))).fitLabel();
        button(pageLeft() + identityButtonW + 4, footerY(), identityButtonW, UiText.s("gui.cronicasrp.origins.button"), false,
                () -> minecraft.setScreen(new OriginsViewScreen(this))).fitLabel();
        int closeW = Math.min(58, w / 2 - 3), revealW = Math.min(75, w - closeW - 6);
        var reveal = button(x, footerY(), revealW, UiText.s("gui.cronicasrp.button.destiny"), false,
                () -> minecraft.setScreen(new DestinyRevealScreen(ClientState.profile())));
        reveal.setTooltip(Tooltip.create(UiText.c("gui.cronicasrp.tooltip.destiny")));
        button(x + w - closeW, footerY(), closeW, UiText.s("gui.cronicasrp.button.close"), true, this::onClose);
    }
    private void updateTabs() {
        for (int i = 0; i < tabs.length; i++) if (tabs[i] != null) tabs[i].selected(i == page);
        if (cashButton != null) cashButton.visible = page == 3;
    }
    private int storyHeight() { return Math.max(18, footerY() - (top + 94) - 18); }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (page == 2 && mx >= pageRight() && mx <= pageRight() + pageWidth()) {
            int lines = font.split(Component.literal(ClientState.profile().description()), pageWidth()).size();
            int visible = Math.max(1, storyHeight() / font.lineHeight);
            storyScroll = Math.clamp(storyScroll - (int) Math.signum(sy) * 2, 0, Math.max(0, lines - visible)); return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override protected void renderBook(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var p = ClientState.profile(); int lx = pageLeft(), rx = pageRight(), w = pageWidth();
        boolean compact = bookHeight < 270;
        heading(g, UiText.s("gui.cronicasrp.heading.traveler"), lx, top + 27, w, INK);
        heading(g, p.name(), lx, top + 47, w, INK);
        JournalTheme.rule(g, lx, top + 66, w, 0x668B724A);
        int rowsY = footerY() - (compact ? 86 : 117), rowH = compact ? 11 : 15;
        int portraitY = top + 76;
        if (compact) {
            JournalTheme.frame(g, lx, portraitY, w, 20, 0x18755831, 0x778D734A);
            if (minecraft.player != null) {
                var texture = minecraft.player.getSkin().texture();
                RenderSystem.enableBlend();
                g.blit(texture, lx + 4, portraitY + 2, 16, 16, 8, 8, 8, 8, 64, 64);
                g.blit(texture, lx + 4, portraitY + 2, 16, 16, 40, 8, 8, 8, 64, 64);
                RenderSystem.disableBlend();
                text(g, p.created() ? p.name() : minecraft.player.getGameProfile().getName(),
                        lx + 26, portraitY + 6, w - 31, MUTED);
            }
        } else portrait(g, lx, portraitY, w, Math.max(45, rowsY - portraitY - 9), mouseX, mouseY, false);
        String position = minecraft.player == null ? "—" : minecraft.player.blockPosition().getX() + ", "
                + minecraft.player.blockPosition().getY() + ", " + minecraft.player.blockPosition().getZ();
        row(g, UiText.s("gui.cronicasrp.label.lineage"), UiText.selectedRace() + " · " + p.age() + UiText.s("gui.cronicasrp.label.years_suffix"), lx, rowsY, w, INK, mouseX, mouseY);
        row(g, UiText.s("gui.cronicasrp.origins.class"), UiText.playerClass(), lx, rowsY + rowH, w, INK, mouseX, mouseY);
        row(g, UiText.s("gui.cronicasrp.label.occupation"), UiText.occupation(p.progress().occupation()), lx, rowsY + 2 * rowH, w, INK, mouseX, mouseY);
        row(g, UiText.s("gui.cronicasrp.label.rank"), UiText.rank(p.progress().classification()), lx, rowsY + 3 * rowH, w, 0xFF9A742A, mouseX, mouseY);
        row(g, UiText.s("gui.cronicasrp.label.position"), position, lx, rowsY + 4 * rowH, w, INK, mouseX, mouseY);
        row(g, UiText.s("gui.cronicasrp.label.playtime"), ClientState.statistics().playTime(), lx, rowsY + 5 * rowH, w, INK, mouseX, mouseY);
        row(g, UiText.s("gui.cronicasrp.label.digital"), JournalTheme.money(p.balance()), lx, rowsY + 6 * rowH, w, JournalTheme.originColor(p.origin()), mouseX, mouseY);
        heading(g, switch (page) { case 1 -> UiText.s("gui.cronicasrp.heading.achievements"); case 2 -> UiText.s("gui.cronicasrp.heading.memories"); case 3 -> UiText.s("gui.cronicasrp.heading.wallet"); default -> UiText.s("gui.cronicasrp.heading.attributes"); }, rx, top + 27, w, INK);
        int y = top + 77;
        if (page == 0) attributes(g, rx, y, w, compact);
        else if (page == 1) accomplishments(g, rx, y, w, compact, mouseX, mouseY);
        else if (page == 2) story(g, rx, y, w);
        else wallet(g, rx, y, w);
    }
    private void row(GuiGraphics g, String label, String value, int x, int y, int w, int color, int mx, int my) {
        int labelW = Math.min(w / 2 + 8, font.width(label) + 8);
        text(g, label, x, y, labelW - 3, MUTED);
        String clipped = font.plainSubstrByWidth(value, Math.max(1, w - labelW));
        g.drawString(font, clipped, x + w - font.width(clipped), y, color, false);
        if (!clipped.equals(value) && mx >= x && mx <= x + w && my >= y && my <= y + 10)
            g.renderTooltip(font, Component.literal(label + ": " + value), mx, my);
    }
    private void attributes(GuiGraphics g, int x, int y, int w, boolean compact) {
        var p = ClientState.profile(); int step = compact ? 18 : 26;
        g.pose().pushPose(); g.pose().translate(x, y - 1, 0); g.pose().scale(0.625F, 0.625F, 1);
        g.renderItem(new ItemStack(Items.EMERALD), 0, 0); g.pose().popPose();
        meter(g, UiText.s("gui.cronicasrp.label.reputation"), p.progress().reputation(), 100, x, y, w, 0xFF537C67);
        if (minecraft.player != null) {
            g.blitSprite(HEART, x, y + step, 9, 9);
            meter(g, UiText.s("gui.cronicasrp.label.health"), minecraft.player.getHealth(), minecraft.player.getMaxHealth(), x, y + step, w, 0xFF94513E);
            g.blitSprite(ARMOR, x, y + step * 2, 9, 9);
            meter(g, UiText.s("gui.cronicasrp.label.armor"), minecraft.player.getArmorValue(), 20, x, y + step * 2, w, 0xFF638394);
            text(g, UiText.s("gui.cronicasrp.label.experience_prefix") + minecraft.player.experienceLevel, x, y + step * 3, w, INK);
            if (!compact) segments(g, x, y + step * 3 + 13, w, minecraft.player.experienceProgress, 0xFF59704A);
        }
        int sh = compact ? 32 : 68, sy = footerY() - sh - 8;
        if (!compact) text(g, UiText.s("gui.cronicasrp.signature.title"), x, sy - 12, w, MUTED);
        JournalTheme.frame(g, x, sy, w, sh, 0x309A7946, 0x66886E43);
        if (!p.signatureDrawing().equals(cachedDrawing)) {
            cachedDrawing = p.signatureDrawing(); cachedInk = cachedDrawing.isEmpty() ? null : SignatureDrawing.decode(cachedDrawing);
        }
        if (cachedInk != null) {
            int sw = Math.min(w - 8, (sh - 8) * SignatureDrawing.WIDTH / SignatureDrawing.HEIGHT);
            SignaturePad.renderInk(g, cachedInk, x + (w - sw) / 2, sy + 4, sw, sh - 8, INK);
        } else heading(g, p.signature(), x + 6, sy + Math.max(5, (sh - 10) / 2), w - 12, INK);
    }
    private void meter(GuiGraphics g, String label, float value, float maximum, int x, int y, int w, int color) {
        text(g, label, x + 14, y, w - 70, INK);
        String number = String.format(java.util.Locale.ROOT, "%.0f / %.0f", value, maximum);
        g.drawString(font, number, x + w - font.width(number), y, MUTED, false);
        segments(g, x, y + 12, w, value / Math.max(1, maximum), color);
    }
    private void segments(GuiGraphics g, int x, int y, int w, float fraction, int color) {
        float filled = Math.clamp(fraction, 0F, 1F) * 10;
        for (int i = 0; i < 10; i++) {
            int sx = x + i * w / 10, ex = x + (i + 1) * w / 10 - 1;
            g.fill(sx, y, ex, y + 4, 0x40866B43);
            int fw = (int) ((ex - sx) * Math.clamp(filled - i, 0F, 1F));
            if (fw > 0) g.fill(sx, y, sx + fw, y + 4, color);
        }
    }
    private void accomplishments(GuiGraphics g, int x, int y, int w, boolean compact, int mx, int my) {
        var progress = ClientState.profile().progress(); var stats = ClientState.statistics();
        int step = compact ? 18 : 25;
        row(g, UiText.s("gui.cronicasrp.label.missions"), JournalTheme.number(progress.missions()), x, y, w, INK, mx, my);
        row(g, UiText.s("gui.cronicasrp.label.medals"), JournalTheme.number(progress.medals()), x, y + step, w, INK, mx, my);
        row(g, UiText.s("gui.cronicasrp.label.bosses"), JournalTheme.number(stats.bossesDefeated()), x, y + step * 2, w, INK, mx, my);
        row(g, UiText.s("gui.cronicasrp.label.creatures"), JournalTheme.number(stats.creaturesDefeated()), x, y + step * 3, w, INK, mx, my);
        int cy = y + step * 4 + 7, h = footerY() - cy - 7;
        JournalTheme.frame(g, x, cy, w, h, 0x18755831, 0x668D734A);
        if (compact) {
            medal(g, x + 12, cy + h / 2, 6, progress.bestMedal());
            text(g, UiText.s("gui.cronicasrp.label.best_prefix") + UiText.medal(progress.bestMedal()), x + 26, cy + (h - 9) / 2, w - 30, INK);
        } else {
            JournalTheme.centered(g, JournalTheme.heading(UiText.s("gui.cronicasrp.label.best_medal")), x + w / 2, cy + 8, INK, w - 12);
            medal(g, x + w / 2, cy + 42, 14, progress.bestMedal());
            JournalTheme.centered(g, Component.literal(progress.bestMedal() == RpProgress.Medal.NONE ? UiText.s("gui.cronicasrp.label.no_medal") : UiText.medal(progress.bestMedal())),
                    x + w / 2, cy + 66, MUTED, w - 12);
        }
    }
    private void medal(GuiGraphics g, int cx, int cy, int r, RpProgress.Medal medal) {
        int color = switch (medal) { case BRONZE -> 0xFFAA724B; case SILVER -> 0xFF9BAEAF; case GOLD -> 0xFFC7A04F; default -> 0x66836E4C; };
        if (medal != RpProgress.Medal.NONE) {
            for (int i = 0; i < r; i++) {
                g.fill(cx - r / 2 - 2, cy + i + r / 2, cx - 1, cy + i + r / 2 + 1, 0xFF6E4450);
                g.fill(cx + 1, cy + i + r / 2, cx + r / 2 + 2, cy + i + r / 2 + 1, 0xFF6E4450);
            }
            for (int dy = -r; dy <= r; dy++) {
                int dx = (int) Math.sqrt(r * r - dy * dy);
                g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
            JournalTheme.ring(g, cx, cy, r - 2, 0xFF735936, 0);
            JournalTheme.diamond(g, cx, cy, Math.max(2, r / 3), 0xFFF1DDA1);
        } else {
            JournalTheme.ring(g, cx, cy, r, color, 0);
            JournalTheme.diamond(g, cx, cy, Math.max(2, r / 3), color);
        }
    }
    private void story(GuiGraphics g, int x, int y, int w) {
        text(g, UiText.s("gui.cronicasrp.story.heading"), x, y, w, MUTED);
        String story = ClientState.profile().description().isBlank() ? UiText.s("gui.cronicasrp.story.empty") : ClientState.profile().description();
        List<FormattedCharSequence> lines = font.split(Component.literal(story), w - 6);
        int visible = Math.max(1, storyHeight() / font.lineHeight), start = Math.min(storyScroll, Math.max(0, lines.size() - visible));
        for (int i = start; i < Math.min(lines.size(), start + visible); i++)
            g.drawString(font, lines.get(i), x, top + 94 + (i - start) * font.lineHeight, INK, false);
        if (lines.size() > visible) {
            int track = storyHeight(), thumb = Math.max(8, track * visible / lines.size());
            int offset = (track - thumb) * start / Math.max(1, lines.size() - visible);
            g.fill(x + w - 2, top + 94, x + w, top + 94 + track, 0x44836A43);
            g.fill(x + w - 2, top + 94 + offset, x + w, top + 94 + offset + thumb, 0xFF967447);
            text(g, UiText.s("gui.cronicasrp.story.scroll"), x, footerY() - 13, w, MUTED);
        }
    }
    private void wallet(GuiGraphics g, int x, int y, int w) {
        var p = ClientState.profile(); text(g, UiText.s("gui.cronicasrp.wallet.digital"), x, y, w, MUTED);
        var amount = JournalTheme.heading(JournalTheme.money(p.balance()));
        float scale = Math.min(1.5F, (w - 5F) / Math.max(1, font.width(amount)));
        g.pose().pushPose(); g.pose().translate(x, y + 14, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, amount, 0, 0, INK, false); g.pose().popPose();
        long physical = 0;
        if (minecraft.player != null) for (int i = 0; i < 41; i++) {
            if (i >= 36 && i < 40) continue;
            var stack = minecraft.player.getInventory().getItem(i);
            var denomination = CurrencyItems.denomination(stack);
            if (denomination != null) physical += (long) denomination.cents() * stack.getCount();
        }
        text(g, UiText.s("gui.cronicasrp.wallet.physical_prefix") + JournalTheme.money(physical), x, y + 35, w, INK);
        text(g, UiText.s("gui.cronicasrp.wallet.total_prefix") + JournalTheme.money(p.balance() + physical), x, y + 48, w, INK);
        text(g, UiText.s("gui.cronicasrp.wallet.origin_prefix") + UiText.origin(p.origin()), x, y + 61, w, JournalTheme.originColor(p.origin()));
        text(g, UiText.s("gui.cronicasrp.wallet.initial_prefix") + JournalTheme.money(p.initialBalance()), x, y + 73, w, MUTED);
        if (bookHeight >= 270) paragraph(g, UiText.s("gui.cronicasrp.wallet.transfer_help"), x, y + 96, w, footerY() - y - 125, INK);
    }
}
