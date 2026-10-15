package pt.crownledger.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import pt.crownledger.block.BlockRegistry;
import pt.crownledger.compat.ChroniclesBridge;
import pt.crownledger.compat.ContractsBridge;
import pt.crownledger.compat.RealmBridge;
import pt.crownledger.compat.WalletBridge;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.Policy;
import pt.crownledger.domain.Treasury;
import pt.crownledger.domain.Treasury.Currency;
import pt.crownledger.domain.Treasury.Kind;
import pt.crownledger.network.CrownNetwork;

/** Server-owned operations. Network entry points validate a live physical chest session first. */
public final class CrownService {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson JSON = new Gson();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<MinecraftServer, Long> NEXT_MINUTE = new HashMap<>();
    private static final long SESSION_NANOS = 300_000_000_000L;
    private static final long MINUTE_NANOS = 60_000_000_000L;

    private CrownService() {}

    private static final class Session {
        final BlockPos chest;
        final ResourceKey<Level> dimension;
        final long expires = System.nanoTime() + SESSION_NANOS;
        long lastAction;
        Session(ServerPlayer player, BlockPos chest) {
            this.chest = chest == null ? null : chest.immutable();
            this.dimension = player.level().dimension();
        }
    }

    public static void start(MinecraftServer server) {
        requireThread(server);
        TreasuryData data = TreasuryData.get(server);
        Treasury next = data.treasury.copy();
        if (next.periodStartedAt() < 0) {
            Policy previous = next.policy();
            Currency currency = WalletBridge.activeCurrency();
            if (previous.currency() != currency) next.setPolicy(new Policy(previous.minimumReputation(), previous.taxMode(),
                    previous.taxBasisPoints(), previous.fixedTax(), previous.salary(), previous.periodHours(),
                    previous.activeMinutes(), currency, previous.reputationPerPublicContract(), previous.dailyReputationLimit()),
                    null, System.currentTimeMillis());
            next.cycle(System.currentTimeMillis());
            commit(data, next);
        }
        try { ContractsBridge.start(server); }
        catch (RuntimeException | LinkageError ex) { LOG.error("Contracts integration could not start; treasury funds are retained", ex); }
        NEXT_MINUTE.put(server, System.nanoTime() + MINUTE_NANOS);
    }

    public static void stop(MinecraftServer server) {
        NEXT_MINUTE.remove(server);
        SESSIONS.clear();
        try { ContractsBridge.stop(); }
        catch (RuntimeException | LinkageError ex) { LOG.warn("Contracts integration could not unregister cleanly", ex); }
    }

    public static void logout(UUID player) { SESSIONS.remove(player); }

    public static void tick(MinecraftServer server) {
        requireThread(server);
        long nowNanos = System.nanoTime();
        Long next = NEXT_MINUTE.get(server);
        if (next == null) { NEXT_MINUTE.put(server, nowNanos + MINUTE_NANOS); return; }
        if (nowNanos - next < 0) return;
        NEXT_MINUTE.put(server, nowNanos + MINUTE_NANOS);
        long now = System.currentTimeMillis();
        try { cycle(server, now); }
        catch (RuntimeException | LinkageError ex) { LOG.error("Treasury cycle retained its previous state", ex); }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            try {
                if (ChroniclesBridge.loaded() && !ChroniclesBridge.available(player)) continue;
                TreasuryData data = TreasuryData.get(server);
                Treasury treasury = data.treasury.copy();
                treasury.recordActivity(player.getUUID(), player.getGameProfile().getName(), 1, now);
                commit(data, treasury);
                settlePlayer(player);
            } catch (RuntimeException | LinkageError ex) {
                LOG.warn("Treasury activity/tax could not settle for {}", player.getUUID(), ex);
            }
        }
    }

    /** Deterministic server-side cycle entry point; delayed ticks never invent missed periods. */
    public static void cycle(MinecraftServer server, long now) {
        requireThread(server);
        sweep(server);
        TreasuryData data = TreasuryData.get(server);
        Treasury next = data.treasury.copy();
        if (next.cycle(now)) commit(data, next);
    }

    public static void openBoard(ServerPlayer player, BlockPos chest) {
        requirePlayer(player);
        SESSIONS.put(player.getUUID(), new Session(player, chest));
        try {
            requireSession(player);
            sweep(player.getServer());
            settlePlayer(player);
            send(player, "");
        } catch (RuntimeException ex) { close(player, error(ex)); }
    }

    public static void requireSession(ServerPlayer player) {
        requirePlayer(player);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.chest == null || System.nanoTime() - session.expires >= 0)
            throw new IllegalStateException("gui.kingdomtreasury.session_missing");
        if (!session.dimension.equals(player.level().dimension())
                || player.distanceToSqr(Vec3.atCenterOf(session.chest)) > 64
                || !player.level().hasChunkAt(session.chest))
            throw new IllegalStateException("gui.kingdomtreasury.chest_range");
        if (!player.level().getBlockState(session.chest).is(BlockRegistry.CROWN_CHEST.get()))
            throw new IllegalStateException("gui.kingdomtreasury.chest_range");
    }

    public static void action(ServerPlayer player, String operation, String payload) {
        try {
            requireSession(player);
            if (payload == null || payload.length() > 8192) throw new IllegalArgumentException("gui.kingdomtreasury.invalid_request");
            Session session = SESSIONS.get(player.getUUID());
            long now = System.nanoTime();
            if (now - session.lastAction < 50_000_000L) { send(player, ""); return; }
            session.lastAction = now;
            var value = JsonParser.parseString(payload);
            if (!value.isJsonObject()) throw new IllegalArgumentException("gui.kingdomtreasury.invalid_request");
            JsonObject request = value.getAsJsonObject();
            String message;
            switch (operation) {
                case "snapshot", "refresh" -> { sweep(player.getServer()); settlePlayer(player); message = ""; }
                case "donate" -> {
                    donate(player, number(request, "amount", 0), currency(request, "currency", WalletBridge.activeCurrency()));
                    message = "gui.kingdomtreasury.donated";
                }
                case "claim" -> {
                    long amount = claim(player, currency(request, "currency", WalletBridge.activeCurrency()));
                    message = amount > 0 ? "gui.kingdomtreasury.claimed" : "gui.kingdomtreasury.nothing_claimable";
                }
                case "policy" -> {
                    Policy old = TreasuryData.get(player.getServer()).treasury.policy();
                    setPolicy(player, new Policy(integer(request, "minimumReputation", old.minimumReputation()),
                            Policy.TaxMode.valueOf(text(request, "taxMode", old.taxMode().name(), 16)),
                            integer(request, "taxBasisPoints", old.taxBasisPoints()), number(request, "fixedTax", old.fixedTax()),
                            number(request, "salary", old.salary()), integer(request, "periodHours", old.periodHours()),
                            integer(request, "activeMinutes", old.activeMinutes()), currency(request, "currency", old.currency()),
                            integer(request, "reputationReward", old.reputationPerPublicContract()),
                            integer(request, "dailyReputationLimit", old.dailyReputationLimit())));
                    message = "gui.kingdomtreasury.policy_saved";
                }
                case "publish" -> {
                    publishPublic(player, text(request, "title", "", 64), text(request, "item", "", 128),
                            integer(request, "quantity", 0), number(request, "reward", 0));
                    message = "gui.kingdomtreasury.published";
                }
                case "cancelPublic", "cancel" -> {
                    cancelPublic(player, UUID.fromString(text(request, "contractId", text(request, "id", "", 36), 36)));
                    message = "gui.kingdomtreasury.cancelled";
                }
                case "collect", "collect_items" -> {
                    message = collect(player) > 0 ? "gui.kingdomtreasury.collected" : "gui.kingdomtreasury.nothing_materials";
                }
                case "cancelPayroll" -> { cancelPayroll(player); message = "gui.kingdomtreasury.payroll_cancelled"; }
                default -> throw new IllegalArgumentException("gui.kingdomtreasury.invalid_request");
            }
            send(player, message);
        } catch (RuntimeException ex) {
            try { requireSession(player); }
            catch (RuntimeException invalid) { close(player, error(invalid)); return; }
            try { send(player, error(ex)); }
            catch (RuntimeException invalid) { close(player, error(invalid)); }
        }
    }

    /** Console or an operator appoints a steward only in standalone mode. */
    public static void appoint(CommandSourceStack source, ServerPlayer target) {
        requirePlayer(target);
        if (source == null || source.getServer() != target.getServer() || !source.hasPermission(2))
            throw new IllegalStateException("gui.kingdomtreasury.not_manager");
        if (RealmBridge.loaded()) throw new IllegalStateException("gui.kingdomtreasury.realm_authority");
        TreasuryData data = TreasuryData.get(target.getServer());
        Treasury next = data.treasury.copy();
        UUID actor = source.getEntity() == null ? null : source.getEntity().getUUID();
        next.setStandaloneManager(target.getUUID(), actor, System.currentTimeMillis());
        commit(data, next);
    }

    public static boolean mayManage(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread() || player.isSpectator()) return false;
        try {
            if (RealmBridge.loaded()) {
                RealmBridge.Governor governor = RealmBridge.governor(player.getServer(), System.currentTimeMillis());
                return governor != null && governor.id().equals(player.getUUID());
            }
            return player.getUUID().equals(TreasuryData.get(player.getServer()).treasury.standaloneManager());
        } catch (RuntimeException | LinkageError ex) { return false; }
    }

    public static void requireManager(ServerPlayer player) {
        requirePlayer(player);
        if (!mayManage(player)) throw new IllegalStateException("gui.kingdomtreasury.not_manager");
    }

    public static void setPolicy(ServerPlayer manager, Policy policy) {
        requireManager(manager);
        if (policy.currency() == Currency.CHRONICLES && !ChroniclesBridge.loaded())
            throw new IllegalStateException("gui.kingdomtreasury.profile_required");
        TreasuryData data = TreasuryData.get(manager.getServer());
        Treasury next = data.treasury.copy();
        next.setPolicy(policy, manager.getUUID(), System.currentTimeMillis());
        commit(data, next);
    }

    public static void donate(ServerPlayer player, long amount, Currency currency) {
        requirePlayer(player);
        if (amount < 1 || amount > ChroniclesBridge.MAX_CENTS)
            throw new IllegalArgumentException("gui.kingdomtreasury.invalid_amount");
        TreasuryData data = TreasuryData.get(player.getServer());
        Treasury next = data.treasury.copy();
        long now = System.currentTimeMillis();
        next.addFunds(currency, amount, Kind.DEPOSIT, player.getUUID(), "Donation: " + player.getGameProfile().getName(), now);
        next.settlePending(now);
        WalletBridge.debit(player, currency, amount);
        commit(data, next);
        sync(player);
    }

    public static long claim(ServerPlayer player, Currency currency) {
        requirePlayer(player);
        TreasuryData data = TreasuryData.get(player.getServer());
        long pending = data.treasury.claims(player.getUUID(), currency);
        if (pending == 0) return 0;
        long amount = Math.min(pending, WalletBridge.capacity(player, currency));
        if (amount == 0) return 0;
        Treasury next = data.treasury.copy();
        next.takeSalary(player.getUUID(), currency, amount, player.getGameProfile().getName(), System.currentTimeMillis());
        WalletBridge.credit(player, currency, amount);
        commit(data, next);
        sync(player);
        return amount;
    }

    /** Fixed assessments take available money once; the unpaid remainder is waived, never debt. */
    public static void settlePlayer(ServerPlayer player) {
        requirePlayer(player);
        for (Currency currency : Currency.values()) {
            TreasuryData data = TreasuryData.get(player.getServer());
            long due = data.treasury.taxDue(player.getUUID(), currency);
            if (due == 0) continue;
            if (currency == Currency.CHRONICLES && ChroniclesBridge.loaded() && !ChroniclesBridge.available(player)) continue;
            long available = currency == Currency.CHRONICLES && !ChroniclesBridge.loaded() ? 0 : WalletBridge.balance(player, currency);
            long paid = Math.min(due, available);
            Treasury next = data.treasury.copy();
            next.settleTax(player.getUUID(), currency, paid, player.getGameProfile().getName(), System.currentTimeMillis());
            if (paid > 0) WalletBridge.debit(player, currency, paid);
            commit(data, next);
            sync(player);
        }
    }

    /** Preflight the destination before transferring any tax/refund from Contracts escrow. */
    public static void sweep(MinecraftServer server) {
        requireThread(server);
        if (!ContractsBridge.available()) return;
        for (Currency currency : Currency.values()) {
            long amount = ContractsBridge.balance(server, currency);
            if (amount == 0) continue;
            TreasuryData data = TreasuryData.get(server);
            Treasury next = data.treasury.copy();
            long now = System.currentTimeMillis();
            next.addFunds(currency, amount, Kind.CONTRACT_RETURN, null, "Contract tax and refunds", now);
            next.settlePending(now);
            if (ContractsBridge.debit(server, currency, amount) != amount)
                throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable");
            commit(data, next);
        }
    }

    public static UUID publishPublic(ServerPlayer manager, String title, String item, int quantity, long reward) {
        requireManager(manager);
        if (!ContractsBridge.available()) throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable");
        if (reward < 1 || reward > 1_000_000_000L) throw new IllegalArgumentException("gui.kingdomtreasury.invalid_amount");
        sweep(manager.getServer());
        TreasuryData data = TreasuryData.get(manager.getServer());
        Treasury next = data.treasury.copy();
        Currency currency = WalletBridge.activeCurrency();
        next.removeFunds(currency, reward, Kind.CONTRACT_FUNDING, manager.getUUID(), "Public contract: " + title, System.currentTimeMillis());
        ContractsBridge.credit(manager.getServer(), currency, reward);
        UUID id;
        try { id = ContractsBridge.publish(manager, title, item, quantity, reward); }
        catch (RuntimeException | LinkageError ex) {
            try { ContractsBridge.debit(manager.getServer(), currency, reward); }
            catch (RuntimeException | LinkageError restore) { ex.addSuppressed(restore); }
            throw ex;
        }
        commit(data, next);
        return id;
    }

    public static void cancelPublic(ServerPlayer manager, UUID id) {
        requireManager(manager);
        ContractsBridge.cancel(manager, id);
        sweep(manager.getServer());
    }

    public static int collect(ServerPlayer manager) {
        requireManager(manager);
        return ContractsBridge.collect(manager);
    }

    public static void cancelPayroll(ServerPlayer manager) {
        requireManager(manager);
        TreasuryData data = TreasuryData.get(manager.getServer());
        Treasury next = data.treasury.copy();
        next.cancelPending(manager.getUUID(), System.currentTimeMillis());
        commit(data, next);
    }

    public static String snapshot(ServerPlayer player) { return snapshot(player, ""); }

    public static String snapshot(ServerPlayer player, String message) {
        requireSession(player);
        Treasury treasury = TreasuryData.get(player.getServer()).treasury;
        Policy policy = treasury.policy();
        Map<String, Object> view = new LinkedHashMap<>();
        boolean manager = mayManage(player);
        String managerName = "";
        try {
            if (RealmBridge.loaded()) {
                RealmBridge.Governor governor = RealmBridge.governor(player.getServer(), System.currentTimeMillis());
                if (governor != null) managerName = governor.name();
            } else if (treasury.standaloneManager() != null) {
                ServerPlayer online = player.getServer().getPlayerList().getPlayer(treasury.standaloneManager());
                managerName = online == null ? treasury.standaloneManager().toString() : online.getGameProfile().getName();
            }
        } catch (RuntimeException | LinkageError ex) { if (message.isBlank()) message = "gui.kingdomtreasury.realm_unavailable"; }
        long wallet = -1;
        try { wallet = WalletBridge.balance(player, policy.currency()); }
        catch (RuntimeException | LinkageError ex) { if (message.isBlank()) message = "gui.kingdomtreasury.profile_required"; }
        view.put("manager", manager); view.put("managerName", managerName); view.put("message", message);
        view.put("currency", policy.currency().name()); view.put("contractCurrency", WalletBridge.activeCurrency().name()); view.put("playerBalance", wallet);
        view.put("success", java.util.Set.of("gui.kingdomtreasury.donated", "gui.kingdomtreasury.claimed", "gui.kingdomtreasury.policy_saved", "gui.kingdomtreasury.published", "gui.kingdomtreasury.cancelled", "gui.kingdomtreasury.collected", "gui.kingdomtreasury.payroll_cancelled").contains(message));
        view.put("balances", treasury.balances());
        view.put("credits", Map.of(Currency.EMERALD.name(), treasury.claims(player.getUUID(), Currency.EMERALD),
                Currency.CHRONICLES.name(), treasury.claims(player.getUUID(), Currency.CHRONICLES)));
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("taxMode", policy.taxMode()); rules.put("taxBasisPoints", policy.taxBasisPoints());
        rules.put("fixedTax", policy.fixedTax()); rules.put("salary", policy.salary());
        rules.put("periodHours", policy.periodHours()); rules.put("activeMinutes", policy.activeMinutes());
        rules.put("minimumReputation", policy.minimumReputation()); rules.put("currency", policy.currency());
        rules.put("reputationReward", policy.reputationPerPublicContract()); rules.put("dailyReputationLimit", policy.dailyReputationLimit());
        view.put("policy", rules); view.put("salaryNext", treasury.nextCycleAt());
        Treasury.PendingBatch pending = treasury.pendingBatch();
        view.put("pendingPayroll", pending != null); view.put("pendingPayrollCost", pending == null ? 0 : pending.totalNet());
        view.put("taxDue", treasury.taxDue(player.getUUID(), policy.currency()));
        view.put("contractsAvailable", ContractsBridge.available()); view.put("realmAvailable", RealmBridge.loaded());
        List<Map<String, Object>> contracts = List.of();
        if (ContractsBridge.available()) {
            try { contracts = ContractsBridge.contracts(player.getServer()); }
            catch (RuntimeException | LinkageError ex) { view.put("contractsAvailable", false); }
        }
        view.put("contracts", contracts);
        view.put("history", treasury.history().stream().map(entry -> {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("description", entry.details()); record.put("kind", entry.kind());
            record.put("amount", entry.amount()); record.put("currency", entry.currency()); record.put("at", entry.time());
            return record;
        }).toList());
        return JSON.toJson(view);
    }

    private static void commit(TreasuryData data, Treasury next) { data.treasury = next; data.setDirty(); }
    private static void sync(ServerPlayer player) {
        try { player.containerMenu.broadcastChanges(); }
        catch (RuntimeException ex) { LOG.warn("Treasury settled but inventory synchronization failed for {}", player.getUUID(), ex); }
    }
    private static void send(ServerPlayer player, String message) { CrownNetwork.send(player, snapshot(player, message)); }
    private static void close(ServerPlayer player, String message) {
        logout(player.getUUID());
        try { CrownNetwork.send(player, JSON.toJson(Map.of("close", true, "message", message))); }
        catch (RuntimeException ex) { LOG.debug("Could not close Crown Ledger screen for {}", player.getUUID(), ex); }
        player.displayClientMessage(Component.translatable(message), false);
    }
    private static long number(JsonObject request, String key, long fallback) {
        if (!request.has(key)) return fallback;
        return new BigDecimal(request.get(key).getAsString()).longValueExact();
    }
    private static int integer(JsonObject request, String key, int fallback) { return Math.toIntExact(number(request, key, fallback)); }
    private static Currency currency(JsonObject request, String key, Currency fallback) {
        return Currency.valueOf(text(request, key, fallback.name(), 16));
    }
    private static String text(JsonObject request, String key, String fallback, int limit) {
        String value = request.has(key) ? request.get(key).getAsString() : fallback;
        if (value.length() > limit || value.chars().anyMatch(c -> Character.isISOControl(c) || c == '\u00a7'))
            throw new IllegalArgumentException("gui.kingdomtreasury.invalid_request");
        return value.trim();
    }
    private static String error(RuntimeException exception) {
        String value = exception.getMessage();
        if (value != null && value.startsWith("gui.kingdomtreasury.")) return value;
        if ("Insufficient treasury funds".equals(value)) return "gui.kingdomtreasury.insufficient_funds";
        if ("No pending payroll batch".equals(value)) return "gui.kingdomtreasury.no_pending_payroll";
        if ("Contract is no longer open".equals(value)) return "gui.kingdomtreasury.contract_settled";
        if ("Contract does not exist".equals(value)) return "gui.kingdomtreasury.contract_missing";
        return "gui.kingdomtreasury.invalid_request";
    }
    private static void requireThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) throw new IllegalStateException("gui.kingdomtreasury.invalid_request");
    }
    private static void requirePlayer(ServerPlayer player) {
        if (player == null || player.getServer() == null || player.isSpectator())
            throw new IllegalStateException("gui.kingdomtreasury.invalid_request");
        requireThread(player.getServer());
    }
}
