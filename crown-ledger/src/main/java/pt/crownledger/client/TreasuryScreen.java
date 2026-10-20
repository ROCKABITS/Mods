package pt.crownledger.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pt.crownledger.network.Net;

/** Physical treasury interface. Permissions, balances and all mutations remain server-owned. */
public final class TreasuryScreen extends Screen {
    private static final int INK = 0xFF36291F, MUTED = 0xFF756249, GOLD = 0xFFAE8542;
    private static final int PAPER = 0xFFE9D7AE, LIGHT = 0xFFF6E9CB, GREEN = 0xFF315E47;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private JsonObject state;
    private String tab = "overview", notice = "", pending = "", taxMode = "PERCENTAGE";
    private String donationCurrency, editingCurrency = "EMERALD";
    private BiConsumer<String, JsonObject> requestSender = Net::send;
    private boolean waiting, publicForm;
    private long sentAt;
    private int x, y, w, h, top, page, policyPage, ticks;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, EditBox> fields = new LinkedHashMap<>();

    private TreasuryScreen(JsonObject state) {
        super(tr("title"));
        this.state = state;
        donationCurrency = activeCurrency();
        notice = str(state, "message", "");
    }

    public static void receive(String json) {
        JsonObject data;
        try { data = json == null || json.isBlank() ? new JsonObject() : JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException malformed) { return; }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (data.size() == 0 || bool(data, "close") || data.has("valid") && !bool(data, "valid")) {
                if (mc.screen instanceof TreasuryScreen) mc.setScreen(null);
                return;
            }
            if (mc.screen instanceof TreasuryScreen current) current.accept(data);
            else mc.setScreen(new TreasuryScreen(data));
        });
    }

    private void accept(JsonObject data) {
        capture();
        boolean wasWaiting = waiting;
        String focused = "";
        for (var entry : fields.entrySet()) if (entry.getValue().isFocused()) focused = entry.getKey();
        boolean saved = "policy".equals(pending) && bool(data, "success");
        state = data;
        if (!chroniclesLoaded() && "CHRONICLES".equals(donationCurrency)) {
            donationCurrency = "EMERALD";
            value("donation", "0");
        }
        waiting = false;
        pending = "";
        String message = str(data, "message", "");
        if (!message.isBlank()) notice = message;
        else if (wasWaiting) notice = "";
        if (!manager() && ("policy".equals(tab) || "contracts".equals(tab))) tab = "overview";
        if (saved) loadPolicy();
        rebuildWidgets();
        if (fields.containsKey(focused)) setFocused(fields.get(focused));
    }

    private static Component tr(String key, Object... args) { return Component.translatable("gui.kingdomtreasury." + key, args); }
    private String text(String key, Object... args) { return tr(key, args).getString(); }
    private static String str(JsonObject object, String key, String fallback) {
        try { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException malformed) { return fallback; }
    }
    private static long number(JsonObject object, String key) {
        try { return object.has(key) ? object.get(key).getAsLong() : 0; }
        catch (RuntimeException malformed) { return 0; }
    }
    private static boolean bool(JsonObject object, String key) {
        try { return object.has(key) && object.get(key).getAsBoolean(); }
        catch (RuntimeException malformed) { return false; }
    }
    private static JsonObject object(JsonObject source, String key) {
        return source.has(key) && source.get(key).isJsonObject() ? source.getAsJsonObject(key) : new JsonObject();
    }
    private JsonArray entries(String key) { return state.has(key) && state.get(key).isJsonArray() ? state.getAsJsonArray(key) : new JsonArray(); }
    private boolean manager() { return bool(state, "manager"); }
    private String currency() { return str(state, "currency", "EMERALD"); }
    private boolean chroniclesLoaded() { return bool(state, "chroniclesLoaded"); }
    private String activeCurrency() { return chroniclesLoaded() && "CHRONICLES".equals(str(state, "activeCurrency", currency())) ? "CHRONICLES" : "EMERALD"; }
    private String contractCurrency() { return str(state, "contractCurrency", activeCurrency()); }
    private String currencyKey(String currency) { return "CHRONICLES".equals(currency) ? "currency_chronicles" : "currency_emerald"; }
    private long donationBalance() { return number(object(state, "wallets"), donationCurrency); }
    private JsonObject policy() { return object(state, "policy"); }
    private String money(long amount, String currency) {
        return "CHRONICLES".equals(currency) ? BigDecimal.valueOf(amount, 2).toPlainString() + " €" : text("emerald_amount", amount);
    }
    private String money(long amount) { return money(amount, currency()); }
    private String percent(long basisPoints) { return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString(); }
    private String date(long millis) { return millis <= 0 ? text("not_scheduled") : DATE.format(Instant.ofEpochMilli(millis)); }
    private void capture() { fields.forEach((key, field) -> values.put(key, field.getValue())); }
    private void value(String key, String value) {
        values.put(key, value);
        if (fields.containsKey(key)) fields.get(key).setValue(value);
    }

    @Override protected void init() {
        capture();
        fields.clear();
        w = Math.min(650, width - 16); h = Math.min(422, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        String[] publicTabs = {"overview", "contribute", "history"};
        int tabWidth = (w - 28) / 3;
        for (int i = 0; i < publicTabs.length; i++) {
            String name = publicTabs[i];
            button(x + 10 + i * (tabWidth + 4), y + 32, tabWidth, name, () -> select(name)).active = !tab.equals(name) && !waiting;
        }
        top = y + 62;
        if (manager()) {
            int half = (w - 24) / 2;
            button(x + 10, y + 56, half, "policy", () -> select("policy")).active = !tab.equals("policy") && !waiting;
            button(x + 14 + half, y + 56, half, "contracts", () -> select("contracts")).active = !tab.equals("contracts") && !waiting;
            top = y + 86;
        }
        switch (tab) {
            case "contribute" -> initContribute();
            case "policy" -> initPolicy();
            case "contracts" -> initContracts();
            case "history" -> initPagination(entries("history").size(), 30);
            default -> initOverview();
        }
    }

    private void select(String target) {
        capture(); tab = target; page = 0; notice = "";
        if (target.equals("policy")) loadPolicy();
        rebuildWidgets();
    }

    private void initOverview() {
        button(x + 12, y + h - 35, 83, "refresh", () -> send("snapshot", new JsonObject()));
        if (manager() && bool(state, "pendingPayroll")) {
            var cancel = button(x + w - 157, y + h - 35, 145, "cancel_payroll", () -> send("cancelPayroll", new JsonObject()));
            cancel.active = !waiting;
            cancel.setTooltip(Tooltip.create(tr("cancel_payroll_hint")));
        }
    }

    private void initContribute() {
        int half = (w - 34) / 2;
        var selector = button(x + 12, top + 23, half, currencyKey(donationCurrency), () -> {
            if (!chroniclesLoaded()) return;
            capture(); donationCurrency = "EMERALD".equals(donationCurrency) ? "CHRONICLES" : "EMERALD";
            value("donation", "0"); rebuildWidgets();
        });
        selector.active = !waiting && chroniclesLoaded();
        selector.setTooltip(Tooltip.create(tr(chroniclesLoaded() ? "donation_currency_hint" : "currency_unavailable")));
        field("donation", x + 22 + half, top + 23, half, "donation_amount", "10", false);
        var donate = button(x + 12, top + 51, w - 24, "donate", this::donate);
        donate.active = !waiting && donationBalance() >= 0 && (!"CHRONICLES".equals(donationCurrency) || chroniclesLoaded());
        donate.setTooltip(Tooltip.create(tr("donation_hint")));
        int claimY = Math.min(top + 132, y + h - 59);
        var emerald = button(x + 12, claimY, half, "claim_emeralds", () -> claim("EMERALD"));
        emerald.active = !waiting && number(object(state, "credits"), "EMERALD") > 0;
        emerald.setTooltip(Tooltip.create(tr("credit_hint", money(number(object(state, "credits"), "EMERALD"), "EMERALD"))));
        var coins = button(x + 22 + half, claimY, half, "claim_coins", () -> claim("CHRONICLES"));
        coins.active = !waiting && chroniclesLoaded() && number(object(state, "credits"), "CHRONICLES") > 0;
        coins.setTooltip(Tooltip.create(tr(chroniclesLoaded() ? "credit_hint" : "credit_unavailable", money(number(object(state, "credits"), "CHRONICLES"), "CHRONICLES"))));
        button(x + 12, y + h - 35, 83, "refresh", () -> send("snapshot", new JsonObject()));
    }

    private void claim(String currency) { var data = new JsonObject(); data.addProperty("currency", currency); send("claim", data); }
    private void donate() {
        capture();
        try {
            if ("CHRONICLES".equals(donationCurrency) && !chroniclesLoaded()) throw new IllegalArgumentException();
            long amount = amount(values.get("donation"), donationCurrency);
            if (amount <= 0) throw new IllegalArgumentException();
            var data = new JsonObject(); data.addProperty("amount", amount); data.addProperty("currency", donationCurrency); send("donate", data);
        } catch (RuntimeException invalid) { notice = "gui.kingdomtreasury.invalid_amount"; }
    }

    private void loadPolicy() {
        JsonObject policy = policy();
        editingCurrency = str(policy, "currency", currency());
        taxMode = str(policy, "taxMode", "PERCENTAGE");
        value("taxPercent", percent(number(policy, "taxBasisPoints")));
        value("marketTaxPercent", percent(number(policy, "marketTaxBasisPoints")));value("casinoTaxPercent",percent(number(policy,"casinoTaxBasisPoints")));
        for (String key : new String[]{"fixedTax", "salary"}) value(key, plainMoney(number(policy, key), editingCurrency));
        for (String key : new String[]{"periodHours", "activeMinutes", "minimumReputation", "reputationReward", "dailyReputationLimit"})
            value(key, Long.toString(number(policy, key)));
    }

    private void initPolicy() {
        int half = (w - 34) / 2, right = x + 22 + half;
        if (policyPage == 0) {
            var mode = button(x + 12, top + 11, half, "PERCENTAGE".equals(taxMode) ? "mode_percentage" : "mode_fixed", () -> {
                capture(); taxMode = "PERCENTAGE".equals(taxMode) ? "FIXED" : "PERCENTAGE"; rebuildWidgets();
            });
            mode.setTooltip(Tooltip.create(tr("mode_hint")));
            field("taxPercent", right, top + 11, half, "tax_percent", "0", false).active = "PERCENTAGE".equals(taxMode);
            field("fixedTax", x + 12, top + 43, half, "fixed_tax", "0", false).active = "FIXED".equals(taxMode);
            field("salary", right, top + 43, half, "salary", "0", false);
            field("marketTaxPercent", x + 12, top + 75, half, "market_tax_percent", "0", false);field("casinoTaxPercent",right,top+75,half,"casino_tax_percent","0",false);
        } else {
            field("periodHours", x + 12, top + 11, half, "period_hours", "168", true);
            field("activeMinutes", right, top + 11, half, "active_minutes", "30", true);
            field("minimumReputation", x + 12, top + 43, half, "minimum_reputation", "0", true);
            field("reputationReward", right, top + 43, half, "reputation_reward", "1", true);
            field("dailyReputationLimit", x + 12, top + 75, half, "daily_limit", "3", true);
            var selector = button(right, top + 75, half, currencyKey(editingCurrency), () -> {
                if ("EMERALD".equals(editingCurrency) && !chroniclesLoaded()) return;
                capture(); editingCurrency = "EMERALD".equals(editingCurrency) ? "CHRONICLES" : "EMERALD";
                value("fixedTax", "0"); value("salary", "0"); rebuildWidgets();
            });
            selector.active = !waiting && (chroniclesLoaded() || "CHRONICLES".equals(editingCurrency));
            selector.setTooltip(Tooltip.create(tr(selector.active ? "policy_currency_hint" : "currency_unavailable")));
        }
        button(x + 12, y + h - 35, 104, policyPage == 0 ? "participation_page" : "economy_page", () -> {
            capture(); policyPage = 1 - policyPage; rebuildWidgets();
        });
        var save = button(x + w - 132, y + h - 35, 120, "save_policy", this::savePolicy);
        save.active = !waiting && (!"CHRONICLES".equals(editingCurrency) || chroniclesLoaded());
        save.setTooltip(Tooltip.create(tr("policy_hint")));
    }

    private void savePolicy() {
        capture();
        try {
            if ("CHRONICLES".equals(editingCurrency) && !chroniclesLoaded()) throw new IllegalArgumentException();
            var data = new JsonObject();
            long bps = new BigDecimal(values.get("taxPercent").replace(',', '.')).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (bps < 0 || bps > 10_000) throw new IllegalArgumentException();
            long marketBps = new BigDecimal(values.get("marketTaxPercent").replace(',', '.')).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (marketBps < 0 || marketBps > 10_000) throw new IllegalArgumentException();
            data.addProperty("marketTaxBasisPoints", marketBps);
            long casinoBps=new BigDecimal(values.get("casinoTaxPercent").replace(',','.')).movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact();if(casinoBps<0||casinoBps>10000)throw new IllegalArgumentException();data.addProperty("casinoTaxBasisPoints",casinoBps);
            data.addProperty("taxMode", taxMode); data.addProperty("taxBasisPoints", bps); data.addProperty("currency", editingCurrency);
            data.addProperty("fixedTax", nonnegativeAmount("fixedTax")); data.addProperty("salary", nonnegativeAmount("salary"));
            addRange(data, "periodHours", 1, 720); addRange(data, "activeMinutes", 1, 1440);
            addRange(data, "minimumReputation", 0, 100); addRange(data, "reputationReward", 0, 100); addRange(data, "dailyReputationLimit", 0, 100);
            send("policy", data);
        } catch (RuntimeException invalid) { notice = "gui.kingdomtreasury.invalid_policy"; }
    }
    private void addRange(JsonObject data, String key, int min, int max) {
        long value = Long.parseLong(values.get(key));
        if (value < min || value > max) throw new IllegalArgumentException();
        data.addProperty(key, value);
    }
    private long nonnegativeAmount(String key) {
        long value = amount(values.get(key), editingCurrency); if (value < 0) throw new IllegalArgumentException(); return value;
    }
    private static String plainMoney(long amount, String currency) { return "CHRONICLES".equals(currency) ? BigDecimal.valueOf(amount, 2).toPlainString() : Long.toString(amount); }
    private static long amount(String input, String currency) {
        BigDecimal value = new BigDecimal(input.replace(',', '.'));
        return ("CHRONICLES".equals(currency) ? value.movePointRight(2) : value).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private void initContracts() {
        if (publicForm) {
            int half = (w - 34) / 2;
            field("contractTitle", x + 12, top + 11, w - 24, "contract_title", "", false).setMaxLength(64);
            field("contractItem", x + 12, top + 43, w - 24, "contract_item", "minecraft:oak_log", false).setMaxLength(128);
            field("contractQuantity", x + 12, top + 75, half, "contract_quantity", "64", true);
            field("contractReward", x + 22 + half, top + 75, half, "contract_reward", "20", false);
            button(x + 12, y + h - 35, 83, "back", () -> { capture(); publicForm = false; rebuildWidgets(); });
            var publish = button(x + w - 152, y + h - 35, 140, "publish", this::publish); publish.active = !waiting;
        } else {
            button(x + 12, top, (w - 28) / 2, "create_public", () -> { publicForm = true; rebuildWidgets(); });
            var collect = button(x + 16 + (w - 28) / 2, top, (w - 28) / 2, "collect_materials", () -> send("collect", new JsonObject()));
            collect.active = !waiting; collect.setTooltip(Tooltip.create(tr("collect_hint")));
            int count = rows(44, 29);
            JsonArray list = entries("contracts");
            page = clampPage(page, list.size(), count);
            for (int i = 0; i < count && page * count + i < list.size(); i++) {
                JsonObject contract = list.get(page * count + i).getAsJsonObject();
                if (!"OPEN".equals(str(contract, "status", "OPEN"))) continue;
                var cancel = button(x + w - 88, top + 39 + i * 44, 76, "cancel_contract", () -> {
                    var data = new JsonObject(); data.addProperty("id", str(contract, "id", ""));
                    data.addProperty("contractId", str(contract, "id", "")); send("cancelPublic", data);
                });
                cancel.active = !waiting; cancel.setTooltip(Tooltip.create(tr("cancel_contract_hint")));
            }
            initPagination(list.size(), 44, 29);
        }
    }
    private void publish() {
        capture();
        try {
            int quantity = Integer.parseInt(values.get("contractQuantity"));
            long reward = amount(values.get("contractReward"), contractCurrency());
            if (quantity < 1 || quantity > 4096 || reward < 1 || values.get("contractTitle").isBlank() || values.get("contractItem").isBlank())
                throw new IllegalArgumentException();
            var data = new JsonObject();
            data.addProperty("title", values.get("contractTitle")); data.addProperty("item", values.get("contractItem"));
            data.addProperty("quantity", quantity); data.addProperty("reward", reward); data.addProperty("currency", contractCurrency()); send("publish", data);
        } catch (RuntimeException invalid) { notice = "gui.kingdomtreasury.invalid_contract"; }
    }

    private int rows(int rowHeight, int offset) { return Math.max(1, (y + h - 44 - top - offset) / rowHeight); }
    private int clampPage(int requested, int entries, int count) { return Math.max(0, Math.min(requested, Math.max(0, (entries - 1) / count))); }
    private void initPagination(int entries, int rowHeight) { initPagination(entries, rowHeight, 0); }
    private void initPagination(int entries, int rowHeight, int offset) {
        int count = rows(rowHeight, offset); page = clampPage(page, entries, count);
        var previous = button(x + 12, y + h - 35, 90, "previous", () -> { page--; rebuildWidgets(); }); previous.active = !waiting && page > 0;
        var next = button(x + w - 102, y + h - 35, 90, "next", () -> { page++; rebuildWidgets(); }); next.active = !waiting && (page + 1) * count < entries;
    }

    private EditBox field(String key, int fx, int fy, int fw, String label, String fallback, boolean integer) {
        var field = addRenderableWidget(new EditBox(font, fx, fy, fw, 20, tr(label)));
        field.setMaxLength(128); field.setValue(values.getOrDefault(key, fallback)); field.setTextColor(0xFFF8EBD2);
        if (integer) field.setFilter(value -> value.matches("[0-9]*"));
        else if (!key.equals("contractTitle") && !key.equals("contractItem")) field.setFilter(value -> value.matches("[0-9]*([.,][0-9]{0,2})?"));
        field.setTooltip(Tooltip.create(tr(label + "_hint"))); fields.put(key, field); return field;
    }

    private void send(String action, JsonObject data) {
        if (waiting) return;
        capture(); waiting = true; sentAt = System.nanoTime(); pending = action; notice = "gui.kingdomtreasury.waiting";
        requestSender.accept(action, data); rebuildWidgets();
    }

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        g.fill(0, 0, width, height, 0xB0181814); frame(g, x, y, w, h, PAPER, GOLD);
        g.fill(x + 4, y + 4, x + w - 4, y + 27, 0xFF3B4A3B);
        line(g, getTitle().getString(), x + 12, y + 10, w / 2 - 15, 0xFFF4E3B7);
        String role = text(manager() ? "governor" : "public_access");
        line(g, role, x + w / 2, y + 10, w / 2 - 12, 0xFFE0C080);
        switch (tab) {
            case "contribute" -> renderContribute(g);
            case "history" -> renderHistory(g);
            case "policy" -> renderPolicy(g);
            case "contracts" -> renderContracts(g);
            default -> renderOverview(g);
        }
        super.render(g, mx, my, tick);
        if (!notice.isBlank()) {
            String message = Component.translatable(notice).getString();
            line(g, message, x + 12, y + h - 11, w - 24, GREEN);
            if (mx >= x && mx < x + w && my >= y + h - 14 && my < y + h)
                g.renderTooltip(font, font.split(Component.literal(message), Math.max(160, w - 24)), mx, my);
        }
    }

    private void renderOverview(GuiGraphics g) {
        int half = (w - 34) / 2;
        frame(g, x + 10, top, half + 4, 44, LIGHT, GOLD);
        frame(g, x + 20 + half, top, half + 4, 44, LIGHT, GOLD);
        line(g, text("treasury_emeralds"), x + 16, top + 6, half - 8, MUTED);
        line(g, money(number(object(state, "balances"), "EMERALD"), "EMERALD"), x + 16, top + 23, half - 8, GREEN);
        line(g, text("treasury_coins"), x + 26 + half, top + 6, half - 8, MUTED);
        line(g, money(number(object(state, "balances"), "CHRONICLES"), "CHRONICLES"), x + 26 + half, top + 23, half - 8, GREEN);
        JsonObject policy = policy(); int lineY = top + 54;
        String mode = str(policy, "taxMode", "PERCENTAGE");
        String[] info = {
                "PERCENTAGE".equals(mode) ? text("tax_rate", percent(number(policy, "taxBasisPoints"))) : text("fixed_tax_summary", money(number(policy, "fixedTax"))),
                text("market_tax_rate", percent(number(policy, "marketTaxBasisPoints"))),
                text("casino_tax_rate",percent(number(policy,"casinoTaxBasisPoints"))),
                text("minimum_summary", number(policy, "minimumReputation")),
                text("salary_summary", money(number(policy, "salary")), number(policy, "periodHours")),
                text("next_cycle", date(number(state, "salaryNext"))),
                text("activity_summary", number(policy, "activeMinutes")),
                text("tax_due", money(number(state, "taxDue"))),
                text("payroll_pending", money(number(state, "pendingPayrollCost"), str(state, "pendingPayrollCurrency", currency()))),
                text("manager_name", str(state, "managerName", text("no_governor")))
        };
        int available = y + h - 44 - lineY;
        int spacing = available >= info.length * 16 ? 16 : 12;
        for (int i = 0; i < info.length && lineY + i * spacing + 9 < y + h - 40; i++)
            line(g, info[i], x + 14, lineY + i * spacing, w - 28, i == 0 ? INK : MUTED);
    }

    private void renderContribute(GuiGraphics g) {
        int half = (w - 34) / 2;
        line(g, donationBalance() < 0 ? text("wallet_unavailable") : text("your_balance", money(donationBalance(), donationCurrency)), x + 12, top, w - 24, INK);
        line(g, text("donation_currency"), x + 12, top + 12, half, INK);
        line(g, text("donation_amount"), x + 22 + half, top + 12, half, INK);
        int claimY = Math.min(top + 132, y + h - 59);
        if (claimY - top >= 120) {
            line(g, text("available_credit"), x + 12, claimY - 28, w - 24, INK);
            line(g, text("credit_stays"), x + 12, claimY - 15, w - 24, MUTED);
        }
        if (h > 300) wrapped(g, text("donation_hint"), x + 12, claimY + 36, w - 24, 3, MUTED);
    }

    private void renderPolicy(GuiGraphics g) {
        int half = (w - 34) / 2, right = x + 22 + half;
        if (policyPage == 0) {
            line(g, text("tax_mode"), x + 12, top, half, INK);
            line(g, text("tax_percent"), right, top, half, INK);
            line(g, text("fixed_tax"), x + 12, top + 32, half, INK);
            line(g, text("salary"), right, top + 32, half, INK);
            line(g, text("market_tax_percent"), x + 12, top + 64, half, INK);
            line(g,text("casino_tax_percent"),right,top+64,half,INK);
            if (h > 300) {
                frame(g, x + 10, top + 116, w - 20, 83, LIGHT, GOLD);
                wrapped(g, text("policy_hint"), x + 17, top + 124, w - 34, 6, MUTED);
                line(g, text("policy_currency", text(currencyKey(editingCurrency))), x + 14, top + 211, w - 28, GREEN);
            }
        } else {
            String[] labels = {"period_hours", "active_minutes", "minimum_reputation", "reputation_reward", "daily_limit"};
            for (int i = 0; i < labels.length; i++) line(g, text(labels[i]), i % 2 == 0 ? x + 12 : right, top + i / 2 * 32, half, INK);
            line(g, text("fiscal_currency"), right, top + 64, half, INK);
            if (h > 310) {
                wrapped(g, text("policy_currency_hint"), x + 14, top + 108, w - 28, 2, GREEN);
                wrapped(g, text("participation_hint"), x + 14, top + 138, w - 28, 5, MUTED);
            }
        }
    }

    private void renderHistory(GuiGraphics g) {
        JsonArray history = entries("history"); int count = rows(30, 0);
        if (history.isEmpty()) line(g, text("history_empty"), x + 14, top + 10, w - 28, MUTED);
        for (int i = 0; i < count && page * count + i < history.size(); i++) {
            JsonObject entry = history.get(page * count + i).getAsJsonObject(); int rowY = top + i * 30;
            frame(g, x + 10, rowY, w - 20, 28, i % 2 == 0 ? LIGHT : 0xFFE0CAA2, 0xFFC0A87E);
            String description = str(entry, "description", str(entry, "kind", ""));
            String label=Component.translatable(description).getString();if("gui.kingdomtreasury.villager_policy_changed".equals(description))label+=" "+str(entry,"details","")+" · "+str(entry,"actorName","");
            if("gui.kingdomtreasury.economy_policy_changed".equals(description)){label=economicApproval(str(entry,"details",""))+" · "+str(entry,"actorName","");}else if("gui.kingdomtreasury.economy_tax_income".equals(description)){String details=str(entry,"details","");if(details.startsWith("realmeconomy.service-tax:"))label+=" · "+Component.translatable("gui.kingdomtreasury.service."+details.substring("realmeconomy.service-tax:".length()).toLowerCase(java.util.Locale.ROOT)).getString();}
            if(str(entry,"details","").equals("realmmarriage.wedding-fee"))label=Component.translatable("gui.kingdomtreasury.marriage_fee").getString();
            if(str(entry,"details","").equals("contracts.daily-tax"))label=Component.translatable("gui.kingdomtreasury.daily_quest_tax").getString();
            line(g,label,x+15,rowY+3,w-30,INK);
            line(g, date(number(entry, "at")) + " · " + money(number(entry, "amount"), str(entry, "currency", currency())),
                    x + 15, rowY + 16, w - 30, MUTED);
        }
        renderPage(g, history.size(), count);
    }

    private static String economicApproval(String text){
        try{String[] p=text.split(":",2);String[] values=p[1].split(">");if(p[0].equals("BEACON_PERIOD"))return Component.translatable("gui.kingdomtreasury.period_change",values[0],values[1]).getString();String[] old=values[0].split(","),next=values[1].split(",");return Component.translatable("gui.kingdomtreasury.economy_change",Component.translatable("gui.kingdomtreasury.service."+p[0].toLowerCase(java.util.Locale.ROOT)),java.math.BigDecimal.valueOf(Long.parseLong(old[0]),2).toPlainString(),java.math.BigDecimal.valueOf(Long.parseLong(next[0]),2).toPlainString(),java.math.BigDecimal.valueOf(Long.parseLong(old[3]),2).stripTrailingZeros().toPlainString(),java.math.BigDecimal.valueOf(Long.parseLong(next[3]),2).stripTrailingZeros().toPlainString()).getString();}catch(RuntimeException invalid){return Component.translatable("gui.kingdomtreasury.economy_policy_changed").getString();}
    }

    private void renderContracts(GuiGraphics g) {
        if (publicForm) {
            int half = (w - 34) / 2;
            line(g, text("contract_title"), x + 12, top, w - 24, INK);
            line(g, text("contract_item"), x + 12, top + 32, w - 24, INK);
            line(g, text("contract_quantity"), x + 12, top + 64, half, INK);
            line(g, text("contract_reward"), x + 22 + half, top + 64, half, INK);
            line(g, text("contract_currency", text(currencyKey(contractCurrency()))), x + 14, top + 108, w - 28, GREEN);
            if (h > 310) wrapped(g, text("public_contract_hint"), x + 14, top + 126, w - 28, 5, MUTED);
            return;
        }
        JsonArray list = entries("contracts"); int count = rows(44, 29);
        if (list.isEmpty()) line(g, text("contracts_empty"), x + 14, top + 39, w - 28, MUTED);
        for (int i = 0; i < count && page * count + i < list.size(); i++) {
            JsonObject contract = list.get(page * count + i).getAsJsonObject(); int rowY = top + 29 + i * 44;
            frame(g, x + 10, rowY, w - 20, 41, i % 2 == 0 ? LIGHT : 0xFFE0CAA2, 0xFFC0A87E);
            line(g, str(contract, "title", ""), x + 15, rowY + 3, w - 110, INK);
            line(g, number(contract, "quantity") + " × " + str(contract, "item", ""), x + 15, rowY + 15, w - 110, MUTED);
            line(g, text("contract_payment", money(number(contract, "reward"), str(contract, "currency", currency()))), x + 15, rowY + 27, w - 110, GREEN);
        }
        renderPage(g, list.size(), count);
    }

    private void renderPage(GuiGraphics g, int total, int count) {
        line(g, text("page", page + 1, Math.max(1, (total + count - 1) / count)), x + 110, y + h - 29, w - 220, MUTED);
    }
    private void wrapped(GuiGraphics g, String value, int lx, int ly, int maxWidth, int maxLines, int color) {
        var lines = font.split(Component.literal(value), maxWidth);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.drawString(font, lines.get(i), lx, ly + i * 11, color, false);
    }
    private void line(GuiGraphics g, String value, int lx, int ly, int maxWidth, int color) {
        if (maxWidth > 0) g.drawString(font, font.plainSubstrByWidth(value, maxWidth), lx, ly, color, false);
    }
    private static void frame(GuiGraphics g, int fx, int fy, int fw, int fh, int color, int border) {
        g.fill(fx, fy, fx + fw, fy + fh, color); g.fill(fx, fy, fx + fw, fy + 1, border);
        g.fill(fx, fy + fh - 1, fx + fw, fy + fh, border); g.fill(fx, fy, fx + 1, fy + fh, border); g.fill(fx + fw - 1, fy, fx + fw, fy + fh, border);
    }
    private PaperButton button(int bx, int by, int bw, String key, Runnable action) { return addRenderableWidget(new PaperButton(bx, by, bw, tr(key), action)); }
    private static final class PaperButton extends AbstractButton {
        private final Runnable action;
        PaperButton(int x, int y, int width, Component label, Runnable action) { super(x, y, width, 20, label); this.action = action; }
        @Override public void onPress() { action.run(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
            frame(g, getX(), getY(), width, height, active && isHoveredOrFocused() ? LIGHT : active ? 0xFFE0C696 : 0xFFD4C3A2, active && isHoveredOrFocused() ? GREEN : GOLD);
            var font = Minecraft.getInstance().font; float scale = Math.min(1F, (width - 8F) / Math.max(1, font.width(getMessage())));
            g.pose().pushPose(); g.pose().translate(getX() + (width - font.width(getMessage()) * scale) / 2, getY() + (20 - 9 * scale) / 2, 0);
            g.pose().scale(scale, scale, 1); g.drawString(font, getMessage(), 0, 0, active ? INK : MUTED, false); g.pose().popPose();
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!waiting && vertical != 0 && (tab.equals("history") || tab.equals("contracts") && !publicForm)) {
            int count = rows(tab.equals("history") ? 30 : 44, tab.equals("history") ? 0 : 29);
            int total = entries(tab.equals("history") ? "history" : "contracts").size();
            page = clampPage(page + (vertical < 0 ? 1 : -1), total, count); rebuildWidgets(); return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override public void tick() {
        super.tick(); ticks++;
        if (waiting && System.nanoTime() - sentAt >= 5_000_000_000L) { waiting = false; pending = ""; notice = "gui.kingdomtreasury.no_response"; rebuildWidgets(); }
        if (ticks % 100 == 0 && !waiting && fields.isEmpty()) send("snapshot", new JsonObject());
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {}
    @Override public boolean isPauseScreen() { return false; }
}
