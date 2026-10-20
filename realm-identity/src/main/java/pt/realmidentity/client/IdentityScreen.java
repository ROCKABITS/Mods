package pt.realmidentity.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pt.realmidentity.domain.InkDrawing;
import pt.realmidentity.network.IdentityNetwork;

/** A server-owned passport snapshot, opened only by a desk or a physical document. */
public final class IdentityScreen extends Screen {
    private static final int INK = 0xFF342C22, MUTED = 0xFF77694F, GOLD = 0xFFB99853;
    private static final int PAPER = 0xFFEADCB6, LIGHT = 0xFFF6EBCF, GREEN = 0xFF365B43;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter CONSULTED_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);
    private JsonObject state;
    private long receivedAtNanos;
    private InkDrawing signatureDrawing;
    private BiConsumer<String, JsonObject> requestSender = IdentityNetwork::send;
    private boolean waiting;
    private int x, y, w, h, bodyTop, bodyBottom, scroll, contentHeight, ticks, waitingTicks;
    private String notice = "";

    private IdentityScreen(JsonObject data) {
        super(tr("title"));
        state = data;
        receivedAtNanos = System.nanoTime();
        readDrawing();
        notice = str(data, "message", "");
    }

    public static void receive(String json) {
        JsonObject data;
        try { data = json == null || json.isBlank() ? new JsonObject() : JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException malformed) { return; }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (data.size() == 0 || bool(data, "close")) {
                if (mc.screen instanceof IdentityScreen) mc.setScreen(null);
                return;
            }
            if (mc.screen instanceof IdentityScreen current) current.accept(data);
            else mc.setScreen(new IdentityScreen(data));
        });
    }

    private void accept(JsonObject data) {
        boolean modeChanged = station() != bool(data, "stationMode");
        state = data;
        receivedAtNanos = System.nanoTime();
        readDrawing();
        waiting = false; waitingTicks = 0;
        notice = str(data, "message", "");
        if (modeChanged) scroll = 0;
        rebuildWidgets();
    }

    private static Component tr(String key, Object... args) { return Component.translatable("gui.realmidentity." + key, args); }
    private String text(String key, Object... args) { return tr(key, args).getString(); }
    private static String str(JsonObject data, String key, String fallback) {
        try { return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : fallback; }
        catch (RuntimeException invalid) { return fallback; }
    }
    private static long number(JsonObject data, String key) {
        try { return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsLong() : 0; }
        catch (RuntimeException invalid) { return 0; }
    }
    private static boolean bool(JsonObject data, String key) {
        try { return data.has(key) && data.get(key).getAsBoolean(); }
        catch (RuntimeException invalid) { return false; }
    }
    private static JsonObject object(JsonObject data, String key) {
        return data.has(key) && data.get(key).isJsonObject() ? data.getAsJsonObject(key) : new JsonObject();
    }
    private boolean station() { return bool(state, "stationMode") && !bool(state, "readOnly"); }
    private void readDrawing() {
        signatureDrawing = null;
        try {
            String encoded = str(object(state, "profile"), "signatureDrawing", "");
            if (!encoded.isEmpty() && encoded.length() <= InkDrawing.MAX_LENGTH) signatureDrawing = InkDrawing.decode(encoded);
        } catch (IllegalArgumentException malformed) { /* An invalid drawing is displayed as absent. */ }
    }
    private boolean validProfile() { return !"INVALID".equals(str(state, "status", "")) && object(state, "profile").size() > 0; }
    private String value(JsonObject data, String key) {
        String result = str(data, key, "").strip();
        if ("age".equals(key) && "0".equals(result)) return "—";
        return result.isEmpty() ? "—" : result;
    }

    @Override protected void init() {
        w = Math.min(592, width - 12); h = Math.min(394, height - 12);
        x = (width - w) / 2; y = (height - h) / 2;
        bodyTop = y + 51; bodyBottom = y + h - (station() ? 63 : 48);
        if (station()) {
            int gap = 5, available = w - 24, closeWidth = Math.min(84, available / 4);
            int actionWidth = (available - closeWidth - gap * 2) / 2;
            button(x + 12, y + h - 31, actionWidth, "issue", () -> request("issue")).active = !waiting && bool(state, "canIssue");
            button(x + 12 + actionWidth + gap, y + h - 31, actionWidth, "reissue", () -> request("reissue")).active = !waiting && bool(state, "canReissue");
            button(x + w - 12 - closeWidth, y + h - 31, closeWidth, "close", this::onClose);
        } else button(x + (w - 110) / 2, y + h - 31, 110, "close", this::onClose);
    }

    private void request(String operation) {
        if (!station() || waiting) return;
        waiting = true; waitingTicks = 0;
        requestSender.accept(operation, new JsonObject());
        rebuildWidgets();
    }

    @Override public void tick() {
        super.tick();
        if (!station()) return;
        ticks++;
        if (waiting && ++waitingTicks >= 200) { waiting = false; notice = "gui.realmidentity.wait_timeout"; rebuildWidgets(); }
        if (ticks % 100 == 0 && !waiting) request("refresh");
    }

    @Override public void onClose() {
        if (station()) requestSender.accept("close", new JsonObject());
        super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xC518211C);
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x80000000);
        g.fill(x, y, x + w, y + h, 0xFF473426);
        for (int i = 5; i < h; i += 13) g.fill(x + 2, y + i, x + w - 2, y + i + 1, 0xFF513B29);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, GOLD);
        g.fill(x + 7, y + 7, x + w - 7, y + h - 7, PAPER);
        g.fill(x + 9, y + 9, x + w - 9, y + 42, GREEN);
        g.drawCenteredString(font, getTitle(), x + w / 2, y + 14, LIGHT);
        g.drawCenteredString(font, tr(station() ? "station_subtitle" : "document_subtitle"), x + w / 2, y + 28, 0xFFDBCA9F);
        corner(g, x + 13, y + 46); corner(g, x + w - 15, y + 46);
        g.enableScissor(x + 11, bodyTop, x + w - 11, bodyBottom);
        int end = renderBody(g, bodyTop + 3 - scroll);
        contentHeight = end - (bodyTop + 3 - scroll);
        g.disableScissor();
        int maximum = Math.max(0, contentHeight - (bodyBottom - bodyTop - 5));
        scroll = Math.max(0, Math.min(scroll, maximum));
        if (maximum > 0) {
            int track = bodyBottom - bodyTop;
            int thumb = Math.max(12, track * track / (contentHeight + 5));
            g.fill(x + w - 12, bodyTop, x + w - 11, bodyBottom, 0xFFCBBA91);
            int thumbY = bodyTop + (track - thumb) * scroll / maximum;
            g.fill(x + w - 13, thumbY, x + w - 10, thumbY + thumb, GOLD);
        }
        g.fill(x + 12, bodyBottom + 2, x + w - 12, bodyBottom + 3, GOLD);
        if (station()) {
            g.drawString(font, tr("cost"), x + 14, y + h - 58, INK, false);
            String stock = text("stock", number(state, "paperCount"), number(state, "inkCount"));
            g.drawString(font, stock, x + w - 14 - font.width(stock), y + h - 58, MUTED, false);
        }
        if (waiting || !notice.isBlank()) {
            Component message = waiting ? tr("waiting") : Component.translatable(notice);
            g.drawCenteredString(font, fit(message.getString(), w - 30), x + w / 2, y + h - 43, waiting ? MUTED : GREEN);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int renderBody(GuiGraphics g, int cy) {
        if(state.has("marriageDocument"))return renderMarriageCertificate(g,cy);
        int left = x + 21, inner = w - 42;
        boolean invalid = "INVALID".equals(str(state, "status", ""));
        g.drawString(font, tr(station() ? "preview" : "issued_snapshot"), left, cy, MUTED, false);
        cy += 17;
        if (!validProfile()) {
            cy = paragraph(g, text(invalid ? "invalid_explanation" : "profile_unavailable"), left, cy, inner - 42, INK);
            cy += 12;
        } else {
            JsonObject profile = object(state, "profile");
            String source = str(profile, "source", str(state, "source", ""));
            boolean narrow = inner < 480;
            int signatureTop = cy;
            if (narrow) {
                cy = field(g, "name", value(profile, "name"), left, cy, inner);
                cy = field(g, "age", value(profile, "age"), left, cy, inner);
                cy = field(g, "race", value(profile, "race"), left, cy, inner);
                cy = field(g, "occupation", value(profile, "occupation"), left, cy, inner);
            } else {
                int column = inner - 274, half = (column - 12) / 2;
                cy = field(g, "name", value(profile, "name"), left, cy, column);
                int ageEnd = field(g, "age", value(profile, "age"), left, cy, half);
                cy = Math.max(ageEnd, field(g, "race", value(profile, "race"), left + half + 12, cy, half));
                cy = field(g, "occupation", value(profile, "occupation"), left, cy, column);
            }
            int signatureLeft = narrow ? left : left + inner - 256;
            if (narrow) signatureTop = cy;
            g.drawString(font, tr("signature"), signatureLeft, signatureTop, MUTED, false);
            signatureTop += 12;
            int signatureWidth = Math.min(InkDrawing.WIDTH, inner), signatureHeight = signatureWidth * InkDrawing.HEIGHT / InkDrawing.WIDTH;
            g.fill(signatureLeft, signatureTop, signatureLeft + signatureWidth, signatureTop + signatureHeight, 0xFFF4E8C9);
            if (signatureDrawing != null && "CHRONICLES".equals(source)) drawSignature(g, signatureDrawing, signatureLeft, signatureTop, signatureWidth);
            else g.drawString(font, "—", signatureLeft + 8, signatureTop + Math.max(3, signatureHeight / 2 - 4), MUTED, false);
            g.fill(signatureLeft, signatureTop + signatureHeight, signatureLeft + signatureWidth, signatureTop + signatureHeight + 1, GOLD);
            cy = Math.max(cy, signatureTop + signatureHeight + 10);
            cy = field(g, "signature_text", value(profile, "signature"), left, cy, inner);
            String owner = str(state, "ownerName", "");
            if (!owner.isBlank()) { g.drawString(font, tr("minecraft_owner", owner), left, cy + 1, MUTED, false); cy += 15; }
            else if (!str(state, "ownerUuid", "").isBlank()) cy = paragraph(g, text("minecraft_owner_uuid", str(state, "ownerUuid", "")), left, cy + 1, inner, MUTED) + 4;
            g.drawString(font, tr("source_" + ("CHRONICLES".equals(source) ? "chronicles" : "minecraft")), left, cy + 1, MUTED, false);
            cy += 17;
        }
        if (!invalid) cy = renderTitles(g, left, cy + 4, inner);
        if(!invalid && state.has("marriage")){JsonObject marriage=object(state,"marriage");cy=paragraph(g,text("marriage_status",text("marriage_"+str(marriage,"status","UNAVAILABLE"))),left,cy+5,inner,GREEN)+5;if(!str(marriage,"name","").isBlank())cy=paragraph(g,text("marriage_spouse",str(marriage,"name","")),left,cy,inner,INK)+5;}
        if (!station()) {
            if (!invalid) {
                String serial = str(state, "serial", "—");
                long issued = number(state, "issuedAt");
                String date = issued <= 0 ? "—" : DATE.format(Instant.ofEpochMilli(issued));
                cy = paragraph(g, text("serial_date", serial, date), left, cy, inner, MUTED);
            }
            cy = paragraph(g, text(bool(state, "authentic") && !invalid ? "authentic" : "unverified"), left, cy + 3, inner - 42, invalid ? 0xFF8C342D : GREEN);
            seal(g, x + w - 51, cy - 10, bool(state, "authentic") && !invalid);
            cy = paragraph(g, text("status_" + str(state, "status", "PROFILE_UNAVAILABLE").toLowerCase(Locale.ROOT)), left, cy + 5, inner, MUTED);
            String presenter = str(state, "presenterName", "");
            if (!invalid && !presenter.isBlank()) cy = paragraph(g, text("presenter", presenter), left, cy + 5, inner, INK);
            if (!invalid && "ITEM".equals(str(state, "mode", "")) && !str(state, "holderName", "").isBlank())
                cy = paragraph(g, text("holder", str(state, "holderName", "")), left, cy + 5, inner, INK);
            if (!invalid && bool(state, "authentic") && !bool(state, "holderIsOwner"))
                cy = paragraph(g, text("holder_not_owner"), left, cy + 3, inner, 0xFF8C342D);
            cy = paragraph(g, text("snapshot_hint"), left, cy + 7, inner, MUTED);
        } else cy = paragraph(g, text("station_hint"), left, cy + 2, inner, MUTED);
        return cy + 8;
    }

    private int renderMarriageCertificate(GuiGraphics g,int cy){
        JsonObject doc=object(state,"marriageDocument");int left=x+19,inner=w-38;
        cy=paragraph(g,text("marriage_certificate"),left,cy,inner,GREEN)+12;
        cy=paragraph(g,str(doc,"a","")+" & "+str(doc,"b",""),left,cy,inner,INK)+10;
        cy=paragraph(g,text("marriage_priest",str(doc,"priest","")),left,cy,inner,INK)+8;
        cy=paragraph(g,text("marriage_regime",text("marriage_mode_"+str(doc,"mode","SEPARATE"))),left,cy,inner,INK)+8;
        cy=paragraph(g,text("marriage_date",DATE.format(Instant.ofEpochMilli(number(doc,"marriedAt")))),left,cy,inner,INK)+8;
        if(doc.has("witnesses"))for(var row:doc.getAsJsonArray("witnesses"))cy=paragraph(g,text("marriage_witness",row.getAsString()),left,cy,inner,INK)+5;
        cy=paragraph(g,text(str(doc,"phase","").equals("MARRIED")?"marriage_current":"marriage_historical"),left,cy+7,inner,GREEN)+10;
        cy=paragraph(g,text("marriage_verified",str(doc,"id","")),left,cy,inner,MUTED)+12;return cy;
    }

    /** Only public honours from this consultation; never part of the issued profile. */
    private int renderTitles(GuiGraphics g, int left, int cy, int inner) {
        String status = str(state, "titlesStatus", "absent");
        if ("absent".equals(status)) return cy;
        g.fill(left, cy, left + inner, cy + 1, GOLD);
        cy += 9;
        g.drawString(font, tr("titles_heading"), left, cy, GREEN, false);
        cy += 14;
        long asOf = number(state, "titlesAsOf");
        if (asOf > 0) cy = paragraph(g, text("titles_as_of", CONSULTED_DATE.format(Instant.ofEpochMilli(asOf))), left, cy, inner, MUTED) + 5;
        if (!"available".equals(status)) return paragraph(g, text("titles_unavailable"), left, cy, inner, MUTED) + 11;
        List<JsonObject> titles = activeTitles();
        if (titles.isEmpty()) return paragraph(g, text("titles_empty"), left, cy, inner, MUTED) + 11;
        long now = consultationNow();
        for (JsonObject title : titles) {
            Component name = Component.literal(str(title, "name", "")).withStyle(ChatFormatting.BOLD);
            String description = str(title, "description", "");
            long expiry = number(title, "expiresAt");
            String validity = expiry == 0 ? text("title_permanent")
                    : text("title_until", CONSULTED_DATE.format(Instant.ofEpochMilli(expiry)), remaining(expiry - now));
            int available = inner - 20;
            int lines = font.split(name, Math.max(40, available)).size()
                    + (description.isBlank() ? 0 : font.split(Component.literal(description), Math.max(40, available)).size())
                    + font.split(Component.literal(validity), Math.max(40, available)).size();
            int cardHeight = 16 + lines * 11;
            g.fill(left, cy, left + inner, cy + cardHeight, 0xFFDCCDA6);
            g.fill(left, cy, left + 3, cy + cardHeight, GOLD);
            int lineY = cy + 7;
            for (var line : font.split(name, Math.max(40, available))) {
                g.drawString(font, line, left + 10, lineY, GREEN, false); lineY += 11;
            }
            if (!description.isBlank()) lineY = paragraph(g, description, left + 10, lineY, available, INK);
            paragraph(g, validity, left + 10, lineY + 2, available, MUTED);
            cy += cardHeight + 5;
        }
        return paragraph(g, text("titles_hint"), left, cy + 2, inner, MUTED) + 12;
    }

    private long consultationNow() {
        long elapsed = Math.max(0, (System.nanoTime() - receivedAtNanos) / 1_000_000);
        return number(state, "titlesAsOf") + elapsed;
    }

    private List<JsonObject> activeTitles() {
        List<JsonObject> result = new ArrayList<>();
        if (!"available".equals(str(state, "titlesStatus", ""))
                || !state.has("titles") || !state.get("titles").isJsonArray()) return result;
        long now = consultationNow();
        for (var entry : state.getAsJsonArray("titles")) {
            if (result.size() >= 16) break;
            if (!entry.isJsonObject()) continue;
            JsonObject title = entry.getAsJsonObject();
            long expiry = number(title, "expiresAt");
            if (number(title, "grantedAt") <= now && (expiry == 0 || now < expiry)) result.add(title);
        }
        return result;
    }

    private String remaining(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        if (minutes >= 1440) return text("title_days", (minutes + 1439) / 1440);
        if (minutes >= 60) return text("title_hours", (minutes + 59) / 60);
        return text("title_minutes", minutes);
    }

    private int field(GuiGraphics g, String key, String value, int left, int cy, int fieldWidth) {
        g.drawString(font, tr(key), left, cy, MUTED, false);
        int end = paragraph(g, value, left, cy + 12, fieldWidth, INK);
        g.fill(left, end + 1, left + fieldWidth, end + 2, 0xFFCBB98E);
        return end + 9;
    }
    private int paragraph(GuiGraphics g, String value, int left, int cy, int available, int color) {
        for (var line : font.split(Component.literal(value), Math.max(40, available))) {
            g.drawString(font, line, left, cy, color, false); cy += 11;
        }
        return cy;
    }
    private String fit(String value, int available) {
        return font.width(value) <= available ? value : font.plainSubstrByWidth(value, Math.max(0, available - font.width("…"))) + "…";
    }
    private static void drawSignature(GuiGraphics g, InkDrawing drawing, int left, int top, int targetWidth) {
        float scale = targetWidth / (float) InkDrawing.WIDTH;
        g.pose().pushPose(); g.pose().translate(left, top, 0); g.pose().scale(scale, scale, 1);
        for (int row = 0; row < InkDrawing.HEIGHT; row++) {
            int start = -1;
            for (int col = 0; col <= InkDrawing.WIDTH; col++) {
                boolean set = col < InkDrawing.WIDTH && drawing.isSet(col, row);
                if (set && start < 0) start = col;
                else if (!set && start >= 0) { g.fill(start, row, col, row + 1, INK); start = -1; }
            }
        }
        g.pose().popPose();
    }
    private static void corner(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 2, cy, cx + 3, cy + 1, GOLD); g.fill(cx, cy - 2, cx + 1, cy + 3, GOLD);
    }
    private static void seal(GuiGraphics g, int cx, int cy, boolean authentic) {
        int color = authentic ? GREEN : 0xFF77634D;
        g.fill(cx - 9, cy - 7, cx + 9, cy + 8, color);
        g.fill(cx - 6, cy - 10, cx + 6, cy + 11, color);
        g.fill(cx - 12, cy - 3, cx + 12, cy + 4, color);
        g.fill(cx - 5, cy - 5, cx + 5, cy + 6, GOLD);
        g.fill(cx - 3, cy - 3, cx + 3, cy + 4, color);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= x && mouseX < x + w && mouseY >= bodyTop && mouseY < bodyBottom) {
            scroll = Math.max(0, Math.min(scroll - (int) (vertical * 24), Math.max(0, contentHeight - (bodyBottom - bodyTop - 5))));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private AbstractButton button(int bx, int by, int bw, String key, Runnable action) {
        return addRenderableWidget(new AbstractButton(bx, by, bw, 20, tr(key)) {
            @Override public void onPress() { action.run(); }
            @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
            @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
                int border = isHoveredOrFocused() && active ? 0xFFD9BD78 : GOLD;
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
                g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, active ? GREEN : 0xFFB5A780);
                g.drawCenteredString(font, fit(getMessage().getString(), getWidth() - 8), getX() + getWidth() / 2, getY() + 6, active ? LIGHT : 0xFF6D624C);
            }
        });
    }
}
