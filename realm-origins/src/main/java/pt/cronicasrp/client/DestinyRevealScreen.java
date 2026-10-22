package pt.cronicasrp.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import pt.cronicasrp.domain.CharacterProfile;

/** A visual introduction to already saved server data. Never rolls or pays money. */
public final class DestinyRevealScreen extends BookScreen {
    private final CharacterProfile result;
    private final boolean welcome;
    private final long started = Util.getMillis();
    private boolean skipped, finished, chimed;
    private RpgButton continueButton, skipButton, journalButton;
    public DestinyRevealScreen(CharacterProfile result) { this(result, false); }
    public DestinyRevealScreen(CharacterProfile result, boolean welcome) {
        super(UiText.s("gui.cronicasrp.title.destiny"), true);
        this.result = result; this.welcome = welcome;
    }
    @Override protected void init() {
        measure();
        int actionWidth = Math.min(160, bookWidth / 3);
        continueButton = button(width / 2 - actionWidth / 2, footerY(), actionWidth,
                finished ? actionLabel() : UiText.s("gui.cronicasrp.status.revealing"), true, this::continueJourney).fitLabel();
        skipButton = button(left + 20, footerY(), Math.min(100, bookWidth / 4), UiText.s("gui.cronicasrp.button.reveal"), false,
                () -> { skipped = true; finish(); }).fitLabel();
        journalButton = button(left + 20, footerY(), Math.min(100, bookWidth / 4), UiText.s("gui.cronicasrp.button.open_journal"), false,
                () -> minecraft.setScreen(new CharacterProfileScreen())).fitLabel();
        continueButton.active = finished; skipButton.visible = !finished; journalButton.visible = welcome && finished;
    }
    private String actionLabel() { return UiText.s(welcome ? "gui.cronicasrp.realm.enter_world" : "gui.cronicasrp.button.open_journal"); }
    private void continueJourney() { if (finished) minecraft.setScreen(welcome ? null : new CharacterProfileScreen()); }
    private double elapsed() { return skipped ? 4.5 : Math.max(0, (Util.getMillis() - started) / 1000.0); }
    private void finish() {
        finished = true;
        if (continueButton != null) { continueButton.active = true; continueButton.setMessage(Component.literal(actionLabel())); }
        if (skipButton != null) skipButton.visible = false;
        if (journalButton != null) journalButton.visible = welcome;
        if (!chimed && minecraft != null) {
            chimed = true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.85F, 0.35F));
        }
    }
    @Override public void tick() { if (!finished && elapsed() >= 4.5) finish(); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) { if (finished) continueJourney(); else { skipped = true; finish(); } return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { minecraft.setScreen(welcome ? null : new CharacterProfileScreen()); }
    @Override protected void renderBook(GuiGraphics g, int mx, int my, float partialTick) {
        RpgTheme.centered(g, UiText.c("gui.cronicasrp.realm.new_chapter"), width / 2, top + 40, GOLD, bookWidth - 40);
        RpgTheme.centered(g, UiText.c("gui.cronicasrp.realm.welcome", result.name()), width / 2, top + 58, RpgTheme.LIGHT, bookWidth - 50);
        int avatarW = Math.min(310, bookWidth * 2 / 3), avatarY = top + 77;
        int avatarH = Math.max(48, footerY() - avatarY - 52);
        portrait(g, width / 2 - avatarW / 2, avatarY, avatarW, avatarH, mx, my, true);
        double fraction = Math.min(1, elapsed() / 4.1);
        long shown = finished ? result.initialBalance() : Math.round(result.initialBalance() * (1 - Math.pow(1 - fraction, 3)));
        RpgTheme.centered(g, Component.literal(UiText.s("gui.cronicasrp.destiny.starting_funds") + "  " + RpgTheme.money(shown)),
                width / 2, footerY() - 39, GOLD, bookWidth - 50);
        RpgTheme.centered(g, Component.literal(UiText.origin(result.origin())), width / 2, footerY() - 24,
                RpgTheme.originColor(result.origin()), bookWidth - 50);
    }
}
