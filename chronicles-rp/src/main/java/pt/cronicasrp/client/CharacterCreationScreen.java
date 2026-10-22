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
    private RpgEditBox name, age, occupation;
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
    private int bodyY() { return top + (bookHeight < 280 ? (stage == 0 ? 58 : 68) : 86); }
    private boolean compact() { return bookHeight < 280; }
    private int occupationY() { return stage == 0 ? bodyY() + (compact() ? 63 : 99) : bodyY(); }
    private int storyLabelY() { return bodyY() + (compact() ? 67 : 82); }
    private void capture() {
        if (name != null) nameValue = name.getValue();
        if (age != null) ageValue = age.getValue();
        if (occupation != null) {
            occupationValue = occupation.getValue();
            for (String suggestion : RpProgress.OCCUPATIONS)
                if (UiText.occupation(suggestion).equals(occupationValue)) { occupationValue = suggestion; break; }
        }
        if (description != null) descriptionValue = description.getValue();
        if (pad != null) drawingValue = pad.value();
    }
    private void rebuild() { capture(); clearWidgets(); init(); }
    @Override protected void init() {
        capture(); measure(); name = null; age = null; occupation = null; description = null; pad = null; submit = null;
        portraitX = left + Math.max(16, bookWidth / 15); portraitWidth = bookWidth * 39 / 100;
        formX = left + bookWidth * 55 / 100; formWidth = left + bookWidth - Math.max(16, bookWidth / 15) - formX;
        if (!editing) raceValue = OriginsBridge.race(minecraft.player).map(c -> c.id().toString()).orElse(OriginsBridge.NO_ORIGIN);
        int y = bodyY();
        if (stage == 0) {
            name = addRenderableWidget(new RpgEditBox(font, formX, y + (compact() ? 11 : 13), formWidth - 56, UiText.s("gui.cronicasrp.field.rp_name")));
            name.setMaxLength(CharacterRules.NAME_LIMIT); name.setValue(nameValue); name.setHint(UiText.c("gui.cronicasrp.field.name_hint"));
            age = addRenderableWidget(new RpgEditBox(font, formX + formWidth - 48, y + (compact() ? 11 : 13), 48, UiText.s("gui.cronicasrp.field.rp_age")));
            if (compact()) { name.setHeight(18); age.setHeight(18); }
            age.setMaxLength(3); age.setFilter(s -> s.isEmpty() || s.matches("[0-9]{1,3}")); age.setValue(ageValue);
            var originInfo = button(formX, y + (compact() ? 32 : 49), formWidth,
                    UiText.s("gui.cronicasrp.label.lineage_prefix") + UiText.selectedRace(), false, this::showOrigins).fitLabel();
            if (compact()) originInfo.setHeight(18);
            originInfo.setTooltip(Tooltip.create(OriginsBridge.race(minecraft.player).map(c -> c.description().copy()
                    .append("\n\n").append(UiText.c("gui.cronicasrp.origins.choose_help")))
                    .orElseGet(() -> UiText.c("gui.cronicasrp.origins.choose_help").copy())));
            occupationFields(occupationY());
            setInitialFocus(name);
        } else if (stage == 1) {
            occupationFields(occupationY());
            int descriptionY = storyLabelY() + (compact() ? 11 : 15);
            description = addRenderableWidget(new RpgStoryBox(font, formX, descriptionY, formWidth - 5, Math.max(20, footerY() - descriptionY - 13)));
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
        int backWidth = Math.min(68, (formWidth - 8) / 2);
        button(formX, footerY(), backWidth, stage == 0 || editing && stage == 1 ? UiText.s("gui.cronicasrp.button.back") : UiText.s("gui.cronicasrp.button.previous"), false, () -> {
            if (stage == 0 || editing && stage == 1) onClose();
            else { capture(); stage--; error = ""; rebuild(); }
        });
        String label = stage == 3 ? (editing ? UiText.s("gui.cronicasrp.button.save_record") : UiText.s("gui.cronicasrp.button.seal_destiny")) : UiText.s("gui.cronicasrp.button.continue");
        int actionWidth = formWidth - backWidth - 8;
        submit = button(formX + formWidth - actionWidth, footerY(), actionWidth,
                waiting ? UiText.s("gui.cronicasrp.status.saving") : label, true, () -> { if (stage == 3) send(); else next(); }).fitLabel();
        if (!editing && (stage == 0 || stage == 3) && !OriginsBridge.ready(minecraft.player)) submit.active = false;
        if (waiting) for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.active = false;
    }
    private void showOrigins() { capture(); OriginsCreationFlow.open(this); }
    @Override public void tick() {
        super.tick();
        if (submit != null && !waiting && !editing && (stage == 0 || stage == 3))
            submit.active = OriginsBridge.ready(minecraft.player);
    }
    private void occupationFields(int y) {
        occupation = addRenderableWidget(new RpgEditBox(font, formX, y + (compact() ? 11 : 13), formWidth,
                UiText.s("gui.cronicasrp.label.occupation")));
        if (compact()) occupation.setHeight(18);
        occupation.setMaxLength(RpProgress.OCCUPATION_LIMIT);
        occupation.setValue(UiText.occupation(occupationValue));
        occupation.setHint(UiText.c("gui.cronicasrp.occupation.placeholder"));
        occupation.setResponder(value -> {
            if (error.equals(UiText.s("error.cronicasrp.occupation"))) {
                try { RpProgress.checkedOccupation(value); error = ""; }
                catch (IllegalArgumentException ignored) { /* Keep the explanation while the word is invalid. */ }
            }
        });
        occupation.setTooltip(Tooltip.create(UiText.c("gui.cronicasrp.tooltip.occupation")));
        String[] suggestions = {"Ferreiro", "Mercador", "Bardo"};
        int gap = 4, buttonWidth = (formWidth - gap * 2) / 3;
        for (int i = 0; i < suggestions.length; i++) {
            String value = suggestions[i];
            var choice = button(formX + i * (buttonWidth + gap), y + (compact() ? 42 : 49), buttonWidth,
                    UiText.occupation(value), false, () -> {
                        occupation.setValue(UiText.occupation(value)); setFocused(occupation);
                    }).fitLabel();
            choice.setHeight(compact() ? 16 : 18);
            choice.setTooltip(Tooltip.create(UiText.c("gui.cronicasrp.occupation.quick", UiText.occupation(value))));
        }
    }
    private boolean validateOccupation() {
        try {
            occupationValue = RpProgress.checkedOccupation(occupationValue);
            if (occupation != null) occupation.setValue(UiText.occupation(occupationValue));
            return true;
        } catch (IllegalArgumentException ex) {
            error = UiText.error(ex.getMessage());
            if (occupation != null) setFocused(occupation);
            return false;
        }
    }
    private void next() {
        if (waiting) return;
        capture(); error = "";
        if ((stage == 0 || stage == 1) && !validateOccupation()) return;
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
        capture(); error = "";
        if (!validateOccupation()) return;
        waiting = true; rebuild();
        if (editing) PacketDistributor.sendToServer(new ProfilePackets.Edit(descriptionValue, signatureValue, drawingValue, occupationValue));
        else PacketDistributor.sendToServer(new ProfilePackets.Create(new CharacterDraft(nameValue, Integer.parseInt(ageValue),
                raceValue, descriptionValue, signatureValue, drawingValue, occupationValue)));
    }
    public void serverError(Component message) { waiting = false; error = message.getString(); rebuild(); }
    @Override public boolean isPauseScreen() { return !waiting && minecraft != null && OriginsBridge.ready(minecraft.player); }
    @Override public boolean shouldCloseOnEsc() { return !waiting; }
    @Override public void onClose() { if (!waiting) minecraft.setScreen(editing ? new CharacterProfileScreen() : null); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        // Keep Enter as a newline in the history editor and preserve focused button actions.
        if ((key == 257 || key == 335) && !(getFocused() instanceof RpgStoryBox)
                && !(getFocused() instanceof net.minecraft.client.gui.components.AbstractButton)
                && submit != null && submit.active) { submit.onPress(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (pad != null && pad.drawing()) return pad.mouseDragged(x, y, button, dx, dy);
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (pad != null && pad.drawing()) { pad.mouseReleased(x, y, button); setDragging(false); return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override protected void renderBook(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String[] stages = {UiText.s("gui.cronicasrp.stage.name"), UiText.s("gui.cronicasrp.stage.story"), UiText.s("gui.cronicasrp.stage.signature"), UiText.s("gui.cronicasrp.stage.oath")};
        RpgTheme.centered(g, Component.literal(UiText.s("gui.cronicasrp.realm.chapter") + " " + (stage + 1) + " / " + stages[stage]),
                width / 2, top + 39, GOLD, bookWidth - 44);
        String[] subtitles = {"gui.cronicasrp.realm.chapter_0", "gui.cronicasrp.realm.chapter_1", "gui.cronicasrp.realm.chapter_2", "gui.cronicasrp.realm.chapter_3"};
        if (bookHeight >= 280) RpgTheme.centered(g, UiText.c(subtitles[stage]), width / 2, top + 57, RpgTheme.LIGHT, bookWidth - 44);
        int y = bodyY();
        portrait(g, portraitX, y - 6, portraitWidth, footerY() - y + 26, mouseX, mouseY, true);
        RpgTheme.frame(g, formX - 10, y - 8, formWidth + 20, footerY() - y - 3, RpgTheme.PANEL, 0x203D3548);
        for (int i = 0; i < 4; i++) {
            int dx = width / 2 - 21 + i * 14;
            g.fill(dx, footerY() + 21, dx + 5, footerY() + 26, i == stage ? RpgTheme.LIGHT : i < stage ? GOLD : 0xFF40374E);
        }
        if (stage == 0) {
            text(g, UiText.s("gui.cronicasrp.field.rp_name"), formX, y, formWidth - 56, RpgTheme.LIGHT);
            text(g, UiText.s("gui.cronicasrp.label.age"), formX + formWidth - 48, y, 48, RpgTheme.LIGHT);
            if (!compact()) text(g, UiText.s("gui.cronicasrp.label.lineage"), formX, y + 38, formWidth, GOLD);
            text(g, UiText.s("gui.cronicasrp.origins.class_prefix") + UiText.playerClass(), formX, y + (compact() ? 52 : 73), formWidth, RpgTheme.LIGHT);
            if (!compact()) paragraph(g, UiText.s("gui.cronicasrp.origins.creation_help"), formX, y + 181,
                    formWidth, footerY() - y - 197, RpgTheme.ASH);
        } else if (stage == 1) {
            text(g, UiText.s("gui.cronicasrp.label.your_story"), formX, storyLabelY(), formWidth - 63, GOLD);
            String count = (description == null ? descriptionValue.length() : description.getValue().length()) + " / 600";
            text(g, count, formX + formWidth - font.width(count), storyLabelY(), 63, RpgTheme.ASH);
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
                RpgTheme.frame(g, sx, sy, signatureW, signatureH, RpgTheme.PANEL, RpgTheme.BORDER);
                SignaturePad.renderInk(g, SignatureDrawing.decode(drawingValue), sx + 4, sy + 4, signatureW - 8, signatureH - 8, INK);
            } else text(g, signatureValue, formX, sy + 10, formWidth, GOLD);
            text(g, editing ? UiText.s("gui.cronicasrp.story.continue") : UiText.s("gui.cronicasrp.story.origin_pending"), formX, footerY() - 14, formWidth, RpgTheme.ASH);
        }
        if (stage == 0 || stage == 1) {
            text(g, UiText.s("gui.cronicasrp.label.occupation"), formX, occupationY(), formWidth, RpgTheme.LIGHT);
            if (error.isEmpty()) text(g, UiText.s(compact() ? "gui.cronicasrp.occupation.one_word_short" : "gui.cronicasrp.occupation.one_word"),
                    formX, occupationY() + (compact() ? 31 : 36), formWidth, RpgTheme.ASH);
        }
        if (!error.isEmpty()) {
            int errorY = stage <= 1 ? occupationY() + (compact() ? 31 : 36) : footerY() - 13;
            g.fill(formX - 2, errorY - 2, formX + formWidth + 2, errorY + 10, 0xFF251622);
            text(g, error, formX, errorY, formWidth, 0xFFF0A399);
        }
    }
}
