package pt.cronicasrp.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import pt.cronicasrp.domain.CharacterDraft;
import pt.cronicasrp.domain.CharacterRules;
import pt.cronicasrp.domain.SignatureDrawing;
import pt.cronicasrp.domain.RpProgress;
import pt.cronicasrp.network.ProfilePackets;
import pt.cronicasrp.compat.OriginsBridge;

public final class CharacterCreationScreen extends BookScreen {
    private final boolean editing;
    private int stage;
    private String nameValue = "", ageValue = "24", raceValue = OriginsBridge.NO_ORIGIN, descriptionValue = "", signatureValue = "", drawingValue = "";
    private String error = "";
    private String occupationValue = "Aventureiro";
    private boolean waiting;
    private RpgEditBox name, age;
    private RpgStoryBox description;
    private SignaturePad pad;
    private RpgButton submit;
    private int formX, formWidth, portraitX, portraitWidth;

    public CharacterCreationScreen(boolean editing) {
        super(editing ? UiText.s("gui.cronicasrp.title.memories") : UiText.s("gui.cronicasrp.title.pact"), true);
        this.editing = editing;
        if (editing) {
            var p = ClientState.profile(); stage = 1; nameValue = p.name(); ageValue = Integer.toString(p.age());
            raceValue = p.race(); descriptionValue = p.description(); signatureValue = p.signature(); drawingValue = p.signatureDrawing();
            occupationValue = p.progress().occupation();
        }
    }
    public boolean isEditing() { return editing; }
    private int bodyY() { return top + 70; }
    private void capture() {
        if (name != null) nameValue = name.getValue();
        if (age != null) ageValue = age.getValue();
        if (description != null) descriptionValue = description.getValue();
        if (pad != null) drawingValue = pad.value();
    }
    private void rebuild() { capture(); clearWidgets(); init(); }
    @Override protected void init() {
        capture(); measure(); name = null; age = null; description = null; pad = null; submit = null;
        portraitX = left + 26; portraitWidth = Math.clamp(bookWidth * 27 / 100, 78, 150);
        formX = portraitX + portraitWidth + 22; formWidth = left + bookWidth - 26 - formX;
        if (!editing) raceValue = OriginsBridge.race(minecraft.player).map(c -> c.id().toString()).orElse(OriginsBridge.NO_ORIGIN);
        int y = bodyY();
        if (stage == 0) {
            name = addRenderableWidget(new RpgEditBox(font, formX, y + 13, formWidth - 56, UiText.s("gui.cronicasrp.field.rp_name")));
            name.setMaxLength(CharacterRules.NAME_LIMIT); name.setValue(nameValue); name.setHint(UiText.c("gui.cronicasrp.field.name_hint"));
            age = addRenderableWidget(new RpgEditBox(font, formX + formWidth - 48, y + 13, 48, UiText.s("gui.cronicasrp.field.rp_age")));
            age.setMaxLength(3); age.setFilter(s -> s.isEmpty() || s.matches("[0-9]{1,3}")); age.setValue(ageValue);
            var originInfo = button(formX, y + 49, formWidth,
                    UiText.s("gui.cronicasrp.label.lineage_prefix") + UiText.selectedRace(), false, this::showOrigins).fitLabel();
            originInfo.setTooltip(Tooltip.create(OriginsBridge.race(minecraft.player).map(c -> c.description().copy()
                    .append("\n\n").append(UiText.c("gui.cronicasrp.origins.choose_help")))
                    .orElseGet(() -> UiText.c("gui.cronicasrp.origins.choose_help").copy())));
            occupationButton(y + (bookHeight < 270 ? 88 : 99));
            setInitialFocus(name);
        } else if (stage == 1) {
            occupationButton(y);
            description = addRenderableWidget(new RpgStoryBox(font, formX, y + 40, formWidth - 5, Math.max(30, footerY() - y - 59)));
            description.setCharacterLimit(CharacterRules.DESCRIPTION_LIMIT); description.setValue(descriptionValue);
            setInitialFocus(description);
        } else if (stage == 2) {
            int ph = Math.min(90, Math.max(36, footerY() - y - 64));
            int pw = Math.min(formWidth, (ph - 8) * SignatureDrawing.WIDTH / SignatureDrawing.HEIGHT + 8);
            pad = addRenderableWidget(new SignaturePad(formX + (formWidth - pw) / 2, y + 20, pw, ph, drawingValue));
            int toolY = pad.getY() + ph + 5, toolW = (pw - 5) / 2;
            button(pad.getX(), toolY, toolW, UiText.s("gui.cronicasrp.button.undo"), false, () -> pad.undo());
            button(pad.getX() + pw - toolW, toolY, toolW, UiText.s("gui.cronicasrp.button.clear"), false, () -> pad.clear());
        }
        button(formX, footerY(), 68, stage == 0 || editing && stage == 1 ? UiText.s("gui.cronicasrp.button.back") : UiText.s("gui.cronicasrp.button.previous"), false, () -> {
            if (stage == 0 || editing && stage == 1) onClose();
            else { capture(); stage--; error = ""; rebuild(); }
        });
        String label = stage == 3 ? (editing ? UiText.s("gui.cronicasrp.button.save_record") : UiText.s("gui.cronicasrp.button.seal_destiny")) : UiText.s("gui.cronicasrp.button.continue");
        int actionWidth = Math.min(122, formWidth - 75);
        submit = button(formX + formWidth - actionWidth, footerY(), actionWidth,
                waiting ? UiText.s("gui.cronicasrp.status.saving") : label, true, () -> { if (stage == 3) send(); else next(); });
        if (!editing && (stage == 0 || stage == 3) && !OriginsBridge.ready(minecraft.player)) submit.active = false;
        if (waiting) for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.active = false;
    }
    private void showOrigins() { capture(); OriginsCreationFlow.open(this); }
    @Override public void tick() {
        super.tick();
        if (submit != null && !waiting && !editing && (stage == 0 || stage == 3))
            submit.active = OriginsBridge.ready(minecraft.player);
    }
    private void occupationButton(int y) {
        var b = button(formX, y, formWidth, UiText.s("gui.cronicasrp.label.occupation_prefix") + UiText.occupation(occupationValue), false, () -> {
            occupationValue = RpProgress.OCCUPATIONS.get((RpProgress.OCCUPATIONS.indexOf(occupationValue) + 1) % RpProgress.OCCUPATIONS.size());
            rebuild();
        });
        b.setTooltip(Tooltip.create(UiText.c("gui.cronicasrp.tooltip.occupation")));
    }
    private void next() {
        if (waiting) return;
        capture(); error = "";
        if (stage == 0) {
            if (nameValue.strip().length() < 2) { error = UiText.s("error.cronicasrp.name_letters"); return; }
            try { int age = Integer.parseInt(ageValue); if (age < 1 || age > 999) throw new NumberFormatException(); }
            catch (NumberFormatException ex) { error = UiText.s("error.cronicasrp.age_choice"); return; }
            if (!OriginsBridge.ready(minecraft.player)) { error = UiText.s("error.cronicasrp.origins_required"); return; }
            signatureValue = nameValue.strip();
        }
        if (stage == 2) {
            try { drawingValue = SignatureDrawing.validate(drawingValue, !editing || !ClientState.profile().signatureDrawing().isEmpty()); }
            catch (IllegalArgumentException ex) { error = UiText.error(ex.getMessage()); return; }
        }
        stage++; rebuild();
    }
    private void send() {
        if (waiting) return;
        if (!editing && !OriginsBridge.ready(minecraft.player)) {
            error = UiText.s("error.cronicasrp.origins_required"); return;
        }
        capture(); error = ""; waiting = true; rebuild();
        if (editing) PacketDistributor.sendToServer(new ProfilePackets.Edit(descriptionValue, signatureValue, drawingValue, occupationValue));
        else PacketDistributor.sendToServer(new ProfilePackets.Create(new CharacterDraft(nameValue, Integer.parseInt(ageValue),
                raceValue, descriptionValue, signatureValue, drawingValue, occupationValue)));
    }
    public void serverError(Component message) { waiting = false; error = message.getString(); rebuild(); }
    @Override public boolean isPauseScreen() { return !waiting && minecraft != null && OriginsBridge.ready(minecraft.player); }
    @Override public boolean shouldCloseOnEsc() { return !waiting; }
    @Override public void onClose() { if (!waiting) minecraft.setScreen(editing ? new CharacterProfileScreen() : null); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (pad != null && pad.drawing()) return pad.mouseDragged(x, y, button, dx, dy);
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (pad != null && pad.drawing()) { pad.mouseReleased(x, y, button); setDragging(false); return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override protected void renderBook(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RpgTheme.centered(g, RpgTheme.heading(editing ? UiText.s("gui.cronicasrp.title.memories") : UiText.s("gui.cronicasrp.title.pact")), width / 2, top + 25, GOLD, bookWidth - 60);
        String[] stages = {UiText.s("gui.cronicasrp.stage.name"), UiText.s("gui.cronicasrp.stage.story"), UiText.s("gui.cronicasrp.stage.signature"), UiText.s("gui.cronicasrp.stage.oath")};
        RpgTheme.centered(g, Component.literal(stages[stage]), width / 2, top + 46, RpgTheme.ASH, bookWidth - 50);
        int y = bodyY();
        portrait(g, portraitX, y - 2, portraitWidth, footerY() - y + 20, mouseX, mouseY, true);
        RpgTheme.rule(g, formX, top + 61, formWidth, 0x997B704F);
        if (stage == 0) {
            text(g, UiText.s("gui.cronicasrp.field.rp_name"), formX, y, formWidth - 56, RpgTheme.LIGHT);
            text(g, UiText.s("gui.cronicasrp.label.age"), formX + formWidth - 48, y, 48, RpgTheme.LIGHT);
            text(g, UiText.s("gui.cronicasrp.label.lineage"), formX, y + 38, formWidth, GOLD);
            text(g, UiText.s("gui.cronicasrp.origins.class_prefix") + UiText.playerClass(), formX, y + 73, formWidth, RpgTheme.LIGHT);
            if (bookHeight >= 270) paragraph(g, UiText.s("gui.cronicasrp.origins.creation_help"), formX, y + 125,
                    formWidth, footerY() - y - 139, RpgTheme.ASH);
        } else if (stage == 1) {
            text(g, UiText.s("gui.cronicasrp.label.your_story"), formX, y + 25, formWidth - 63, GOLD);
            String count = (description == null ? descriptionValue.length() : description.getValue().length()) + " / 600";
            text(g, count, formX + formWidth - font.width(count), y + 25, 63, RpgTheme.ASH);
        } else if (stage == 2) {
            text(g, UiText.s("gui.cronicasrp.signature.controls"), formX, y, formWidth, RpgTheme.ASH);
            if (editing && drawingValue.isEmpty()) text(g, UiText.s("gui.cronicasrp.signature.keep_old"), formX, footerY() - 14, formWidth, RpgTheme.ASH);
        } else {
            heading(g, nameValue.strip(), formX, y + 1, formWidth, GOLD);
            text(g, UiText.selectedRace() + " · " + ageValue + UiText.s("gui.cronicasrp.label.years_suffix"), formX, y + 22, formWidth, RpgTheme.LIGHT);
            text(g, UiText.playerClass() + " · " + UiText.occupation(occupationValue), formX, y + 37, formWidth, GOLD);
            paragraph(g, descriptionValue.isBlank() ? UiText.s("gui.cronicasrp.story.begin") : descriptionValue,
                    formX, y + 52, formWidth, Math.max(9, footerY() - y - 111), RpgTheme.ASH);
            int signatureH = Math.min(52, Math.max(26, bookHeight / 7));
            int signatureW = Math.min(formWidth, (signatureH - 8) * SignatureDrawing.WIDTH / SignatureDrawing.HEIGHT + 8);
            int sx = formX + formWidth - signatureW, sy = footerY() - signatureH - 20;
            if (!drawingValue.isEmpty()) {
                RpgTheme.frame(g, sx, sy, signatureW, signatureH, 0xFFE6D8B5, 0xFF9B8052);
                SignaturePad.renderInk(g, SignatureDrawing.decode(drawingValue), sx + 4, sy + 4, signatureW - 8, signatureH - 8, INK);
            } else text(g, signatureValue, formX, sy + 10, formWidth, GOLD);
            text(g, editing ? UiText.s("gui.cronicasrp.story.continue") : UiText.s("gui.cronicasrp.story.origin_pending"), formX, footerY() - 14, formWidth, RpgTheme.ASH);
        }
        if (!error.isEmpty()) {
            g.fill(formX - 2, footerY() - 15, formX + formWidth + 2, footerY() - 3, 0xFF251622);
            text(g, error, formX, footerY() - 13, formWidth, 0xFFF0A399);
        }
    }
}
