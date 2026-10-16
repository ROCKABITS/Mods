package pt.cronicasrp.client;

import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import pt.cronicasrp.domain.CharacterProfile;
import pt.cronicasrp.domain.EconomySettings;

/** This screen reveals already saved server data. It never sends a money/roll request. */
public final class DestinyRevealScreen extends BookScreen {
    private final CharacterProfile result;
    private final String digits;
    private final List<String> origins;
    private final long started = Util.getMillis();
    private boolean skipped, finished, chimed;
    private int lastSoundStep = -1;
    private RpgButton continueButton, skipButton;
    public DestinyRevealScreen(CharacterProfile result) {
        super(UiText.s("gui.cronicasrp.title.destiny"), true); this.result = result;
        digits = String.format(Locale.ROOT, "%04d", result.initialBalance() / 100);
        var choices = new java.util.ArrayList<>(EconomySettings.defaults().bands().stream().map(EconomySettings.Band::name).toList());
        if (!choices.contains(result.origin())) choices.add(result.origin());
        origins = List.copyOf(choices);
    }
    @Override protected void init() {
        measure();
        skipButton = button(left + 32, footerY(), 109, UiText.s("gui.cronicasrp.button.reveal"), false, () -> { skipped = true; finish(); });
        continueButton = button(left + bookWidth - 154, footerY(), 122, finished ? UiText.s("gui.cronicasrp.button.open_journal") : UiText.s("gui.cronicasrp.status.revealing"), true,
                () -> minecraft.setScreen(new CharacterProfileScreen()));
        continueButton.active = finished; skipButton.visible = !finished;
    }
    private double elapsed() { return skipped ? duration() : Math.max(0, (Util.getMillis() - started) / 1000.0); }
    private double duration() { return 3.5 + (digits.length() - 1) * 0.22 + 0.55; }
    private void finish() {
        finished = true;
        if (continueButton != null) { continueButton.active = true; continueButton.setMessage(UiText.c("gui.cronicasrp.button.open_journal")); }
        if (skipButton != null) skipButton.visible = false;
        if (!chimed && minecraft != null) {
            chimed = true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.85F, 0.35F));
        }
    }
    @Override public void tick() {
        double t = elapsed();
        if (!finished && t >= duration()) finish();
        int step = (int) (t * Math.max(2, 11 - t * 1.5));
        if (!finished && t < 3.7 && step != lastSoundStep) {
            lastSoundStep = step;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.65F, 0.08F));
        }
    }
    @Override public void onClose() { minecraft.setScreen(new CharacterProfileScreen()); }
    @Override protected void renderBook(GuiGraphics g, int mx, int my, float partialTick) {
        double t = elapsed();
        RpgTheme.centered(g, RpgTheme.heading(UiText.s("gui.cronicasrp.title.destiny")), width / 2, top + 25, GOLD, bookWidth - 60);
        RpgTheme.centered(g, Component.literal(finished ? UiText.s("gui.cronicasrp.destiny.sealed") : UiText.s("gui.cronicasrp.destiny.intro")),
                width / 2, top + 48, RpgTheme.ASH, bookWidth - 60);
        int slotW = Math.min(54, (bookWidth - 100) / digits.length()), gap = 5;
        int total = digits.length() * slotW + (digits.length() - 1) * gap;
        int sx = left + (bookWidth - total) / 2, sy = top + 83;
        int slotH = Math.min(82, Math.max(46, bookHeight - 157));
        RpgTheme.centered(g, UiText.c("gui.cronicasrp.destiny.starting_funds"), width / 2, top + 67, GOLD, bookWidth - 60);
        for (int i = 0; i < digits.length(); i++) {
            int x = sx + i * (slotW + gap), target = digits.charAt(i) - '0';
            double stop = 3.5 + i * 0.22, u = Math.min(1, t / stop);
            double position = (50 + i * 10 + target) * (1 - Math.pow(1 - u, 3));
            boolean landed = u >= 1; int base = (int) Math.floor(position); double fraction = position - base;
            RpgTheme.frame(g, x, sy, slotW, slotH, 0xE80A1320, landed ? 0xFFC6A468 : 0xFF746C65);
            g.enableScissor(x + 2, sy + 2, x + slotW - 2, sy + slotH - 2);
            for (int row = -1; row <= 2; row++) {
                String label = Integer.toString(Math.floorMod(base + row, 10));
                var text = RpgTheme.heading(label); float scale = Math.min(2.3F, (slotW - 8F) / Math.max(1, font.width(text)));
                float y = (float) (sy + slotH / 2.0 - 11 + (row - fraction) * 33);
                g.pose().pushPose(); g.pose().translate(x + (slotW - font.width(text) * scale) / 2F, y, 0); g.pose().scale(scale, scale, 1);
                g.drawString(font, text, 0, 0, row == 0 && landed ? GOLD : RpgTheme.LIGHT, false); g.pose().popPose();
            }
            g.disableScissor();
            g.fillGradient(x + 1, sy + 1, x + slotW - 1, sy + 16, 0xF00A1320, 0x000A1320);
            g.fillGradient(x + 1, sy + slotH - 16, x + slotW - 1, sy + slotH - 1, 0x000A1320, 0xF00A1320);
            RpgTheme.diamond(g, x + slotW / 2, sy - 4, 2, landed ? GOLD : 0xFF6F6A61);
        }
        String origin = t >= 3.2 ? result.origin() : origins.get((int) (t * Math.max(2, 13 - t * 2)) % origins.size());
        int oy = sy + slotH + 10, oh = footerY() - oy - 8;
        int edge = t >= 3.2 ? RpgTheme.originColor(origin) : 0xFF6E6770;
        RpgTheme.frame(g, left + 38, oy, bookWidth - 76, Math.max(18, oh), 0x880A1424, edge);
        RpgTheme.centered(g, RpgTheme.heading(UiText.origin(origin)), width / 2, oy + 5, t >= 3.2 ? GOLD : RpgTheme.ASH, bookWidth - 95);
        if (oh > 47) RpgTheme.centered(g, Component.literal(finished ? RpgTheme.money(result.initialBalance()) + UiText.s("gui.cronicasrp.destiny.story_suffix") : UiText.s("gui.cronicasrp.destiny.slowing")),
                width / 2, oy + 29, RpgTheme.ASH, bookWidth - 100);
        if (finished && oh > 83) {
            RpgTheme.centered(g, Component.literal(originFlavor(result.origin())), width / 2, oy + 49, RpgTheme.LIGHT, bookWidth - 100);
            RpgTheme.ring(g, width / 2, oy + oh - 21, 9, 0x887E745E, t * 0.1);
            RpgTheme.diamond(g, width / 2, oy + oh - 21, 3, GOLD);
        }
        if (finished) text(g, UiText.s("gui.cronicasrp.destiny.once"), left + 32, footerY() + 5, bookWidth - 205, RpgTheme.ASH);
    }
    private static String originFlavor(String origin) {
        return switch (origin) {
            case "Desamparada" -> UiText.s("gui.cronicasrp.origin_lore.destitute");
            case "Humilde" -> UiText.s("gui.cronicasrp.origin_lore.humble");
            case "Comum" -> UiText.s("gui.cronicasrp.origin_lore.common");
            case "Confortável" -> UiText.s("gui.cronicasrp.origin_lore.comfortable");
            case "Abastada" -> UiText.s("gui.cronicasrp.origin_lore.wealthy");
            case "Nobre" -> UiText.s("gui.cronicasrp.origin_lore.noble");
            case "Herdeira" -> UiText.s("gui.cronicasrp.origin_lore.heir");
            default -> UiText.s("gui.cronicasrp.origin_lore.other");
        };
    }
}
