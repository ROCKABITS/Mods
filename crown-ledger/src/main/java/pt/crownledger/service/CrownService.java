package pt.crownledger.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import pt.crownledger.compat.MarketsBridge;
import pt.crownledger.compat.NotificationsBridge;
import pt.crownledger.compat.RealmBridge;
import pt.crownledger.compat.WalletBridge;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.OnlineActivity;
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
    private static final Map<MinecraftServer, OnlineActivity> ONLINE_ACTIVITY = new HashMap<>();
    private static final Map<MinecraftServer, Integer> NEXT_MARKET_SWEEP = new HashMap<>();
    private static final Map<MinecraftServer, Long> NEXT_MARKET_WARNING = new HashMap<>();
    private record IncomeNotice(String key, Currency currency) {}
    private static final Map<MinecraftServer, Map<IncomeNotice, Long>> INCOME_NOTICES = new HashMap<>();
    private static final Map<MinecraftServer, Integer> NEXT_INCOME_NOTICE = new HashMap<>();
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
                    previous.activeMinutes(), currency, previous.reputationPerPublicContract(), previous.dailyReputationLimit(),
                    previous.marketTaxBasisPoints(),previous.casinoTaxBasisPoints()),
                    null, System.currentTimeMillis());
            next.cycle(System.currentTimeMillis());
            commit(server, data, next);
        }
        try { ContractsBridge.start(server); }
        catch (RuntimeException | LinkageError ex) { LOG.error("Contracts integration could not start; treasury funds are retained", ex); }
        try { MarketsBridge.start(server); }
        catch (RuntimeException | LinkageError ex) { LOG.error("Markets integration could not start; market purchases remain unavailable", ex); }
        NEXT_MARKET_SWEEP.put(server, server.getTickCount() + 20);
        ONLINE_ACTIVITY.put(server, new OnlineActivity());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) login(player);
        NEXT_MINUTE.put(server, System.nanoTime() + MINUTE_NANOS);
    }

    public static void stop(MinecraftServer server) {
        NEXT_MINUTE.remove(server);
        ONLINE_ACTIVITY.remove(server);
        NEXT_MARKET_SWEEP.remove(server);
        NEXT_MARKET_WARNING.remove(server);
        INCOME_NOTICES.remove(server);
        NEXT_INCOME_NOTICE.remove(server);
        SESSIONS.clear();
        try { ContractsBridge.stop(); }
        catch (RuntimeException | LinkageError ex) { LOG.warn("Contracts integration could not unregister cleanly", ex); }
        try { MarketsBridge.stop(); }
        catch (RuntimeException | LinkageError ex) { LOG.warn("Markets integration could not unregister cleanly", ex); }
    }

    public static void login(ServerPlayer player) {
        if (player == null) throw new IllegalStateException("gui.kingdomtreasury.invalid_request");
        requireThread(player.getServer());
        OnlineActivity activity = ONLINE_ACTIVITY.computeIfAbsent(player.getServer(), ignored -> new OnlineActivity());
        activity.logout(player.getUUID());
        if (!player.isSpectator()) {
            activity.login(player.getUUID(), System.nanoTime());
            Treasury treasury = TreasuryData.get(player.getServer()).treasury;
            for (Currency currency : Currency.values()) {
                long pending = treasury.claims(player.getUUID(), currency);
                if (pending > 0) NotificationsBridge.notify(player, "salary",
                        Component.translatable("message.kingdomtreasury.salary_waiting", money(pending, currency)));
            }
        }
    }

    public static void logout(UUID player) {
        SESSIONS.remove(player);
        ONLINE_ACTIVITY.values().forEach(activity -> activity.logout(player));
    }

    public static void tick(MinecraftServer server) {
        requireThread(server);
        if(TreasuryData.get(server).loanJournal()!=null)return;
        long nowNanos = System.nanoTime();
        int tick = server.getTickCount();
        flushGovernorIncome(server, tick);
        Integer nextSweep = NEXT_MARKET_SWEEP.get(server);
        if (nextSweep == null || tick - nextSweep >= 0) {
            NEXT_MARKET_SWEEP.put(server, tick + 20);
            sweepMarketsSafely(server);
        }
        Long next = NEXT_MINUTE.get(server);
        boolean minuteDue = next != null && nowNanos - next >= 0;
        if (next == null) NEXT_MINUTE.put(server, nowNanos + MINUTE_NANOS);
        OnlineActivity activity = ONLINE_ACTIVITY.computeIfAbsent(server, ignored -> new OnlineActivity());
        List<ServerPlayer> active = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) { activity.logout(player.getUUID()); continue; }
            try {
                if (ChroniclesBridge.loaded() && !ChroniclesBridge.available(player)) {
                    activity.logout(player.getUUID());
                    continue;
                }
                activity.observe(player.getUUID(), nowNanos);
                active.add(player);
            } catch (RuntimeException | LinkageError ex) {
                activity.logout(player.getUUID());
                if (minuteDue) LOG.warn("Treasury activity could not read the profile for {}", player.getUUID(), ex);
            }
        }
        if (!minuteDue) return;
        NEXT_MINUTE.put(server, nowNanos + MINUTE_NANOS);
        long now = System.currentTimeMillis();
        for (ServerPlayer player : active) {
            try {
                TreasuryData data = TreasuryData.get(server);
                Treasury treasury = data.treasury.copy();
                if (activity.recordCompletedMinutes(treasury, player.getUUID(), player.getGameProfile().getName(), now, nowNanos) > 0)
                    commit(server, data, treasury);
            } catch (RuntimeException | LinkageError ex) {
                LOG.warn("Treasury activity could not settle for {}", player.getUUID(), ex);
            }
        }
        try { cycle(server, now); }
        catch (RuntimeException | LinkageError ex) { LOG.error("Treasury cycle retained its previous state", ex); }
        for (ServerPlayer player : active) {
            try { settlePlayer(player); }
            catch (RuntimeException | LinkageError ex) { LOG.warn("Treasury tax could not settle for {}", player.getUUID(), ex); }
        }
    }

    /** Deterministic server-side cycle entry point; delayed ticks never invent missed periods. */
    public static void cycle(MinecraftServer server, long now) {
        requireThread(server);
        sweep(server);
        TreasuryData data = TreasuryData.get(server);
        Treasury next = data.treasury.copy();
        if (next.cycle(now)) commit(server, data, next);
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
                            integer(request, "dailyReputationLimit", old.dailyReputationLimit()),
                            numericInteger(request, "marketTaxBasisPoints", old.marketTaxBasisPoints()),numericInteger(request,"casinoTaxBasisPoints",old.casinoTaxBasisPoints())));
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
        commit(target.getServer(), data, next);
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
        commit(manager.getServer(), data, next);
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
        commit(player.getServer(), data, next);
        sync(player);
        NotificationsBridge.notify(player, "treasury", Component.translatable("message.kingdomtreasury.donated", money(amount, currency)));
        notifyGovernor(player.getServer(), "message.kingdomtreasury.donation_received", amount, currency, player.getUUID());
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
        commit(player.getServer(), data, next);
        sync(player);
        NotificationsBridge.notify(player, "salary", Component.translatable("message.kingdomtreasury.salary_paid", money(amount, currency)));
        return amount;
    }

    /** Fixed assessments take available money once; the unpaid remainder is waived, never debt. */
    public static void settlePlayer(ServerPlayer player) {
        requirePlayer(player);
        for (Currency currency : Currency.values()) {
            TreasuryData data = TreasuryData.get(player.getServer());
            long due = data.treasury.taxDue(player.getUUID(), currency);
            if (due == 0) continue;
            if (currency == Currency.CHRONICLES && (!ChroniclesBridge.loaded() || !ChroniclesBridge.available(player))) continue;
            long available = currency == Currency.CHRONICLES && !ChroniclesBridge.loaded() ? 0 : WalletBridge.balance(player, currency);
            long paid = Math.min(due, available);
            Treasury next = data.treasury.copy();
            next.settleTax(player.getUUID(), currency, paid, player.getGameProfile().getName(), System.currentTimeMillis());
            if (paid > 0) WalletBridge.debit(player, currency, paid);
            commit(player.getServer(), data, next);
            sync(player);
            if (paid > 0) NotificationsBridge.notify(player, "treasury",
                    Component.translatable("message.kingdomtreasury.fixed_tax_paid", money(paid, currency)));
        }
    }

    /** Preflight the destination before transferring any tax/refund from Contracts escrow. */
    public static void sweep(MinecraftServer server) {
        requireThread(server);
        TreasuryData.get(server).requireBankAvailable();
        sweepMarketsSafely(server);
        if (!ContractsBridge.available()) return;
        for (Currency currency : Currency.values()) {
            long amount = Math.min(ContractsBridge.balance(server, currency), ChroniclesBridge.MAX_CENTS);
            if (amount == 0) continue;
            TreasuryData data = TreasuryData.get(server);
            Treasury next = data.treasury.copy();
            long now = System.currentTimeMillis();
            next.addFunds(currency, amount, Kind.CONTRACT_RETURN, null, "Contract tax and refunds", now);
            next.settlePending(now);
            if (ContractsBridge.debit(server, currency, amount) != amount)
                throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable");
            commit(server, data, next);
            queueGovernorIncome(server, "message.kingdomtreasury.contract_income", amount, currency);
        }
    }

    /** Debit only funded Markets escrow after the complete destination mutation has been validated. */
    public static void sweepMarkets(MinecraftServer server) {
        requireThread(server);
        TreasuryData.get(server).requireBankAvailable();
        if (!MarketsBridge.available()) return;
        Throwable failure = null;
        for (Currency currency : Currency.values()) {
            try {
                long amount = Math.min(MarketsBridge.balance(server, currency), ChroniclesBridge.MAX_CENTS);
                if (amount == 0) continue;
                TreasuryData data = TreasuryData.get(server);
                Treasury next = data.treasury.copy();
                long now = System.currentTimeMillis();
                next.addFunds(currency, amount, Kind.MARKET_TAX, null, "gui.kingdomtreasury.market_tax_income", now);
                next.settlePending(now);
                if (MarketsBridge.debit(server, currency, amount) != amount)
                    throw new IllegalStateException("gui.kingdomtreasury.markets_unavailable");
                commit(server, data, next);
                queueGovernorIncome(server, "message.kingdomtreasury.market_income", amount, currency);
            } catch (RuntimeException | LinkageError ex) {
                // Currency ledgers are independent: a full emerald account must not block digital payroll funding.
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof LinkageError linkage) throw linkage;
    }

    private static void sweepMarketsSafely(MinecraftServer server) {
        try { sweepMarkets(server); }
        catch (RuntimeException | LinkageError ex) {
            long now = System.nanoTime();
            Long nextWarning = NEXT_MARKET_WARNING.get(server);
            if (nextWarning == null || now - nextWarning >= 0) {
                NEXT_MARKET_WARNING.put(server, now + MINUTE_NANOS);
                LOG.warn("Market tax remains in Markets escrow; treasury transfer could not complete", ex);
            }
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
        commit(manager.getServer(), data, next);
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
        commit(manager.getServer(), data, next);
    }

    public static String snapshot(ServerPlayer player) { return snapshot(player, ""); }

    public static String snapshot(ServerPlayer player, String message) {
        requireSession(player);
        sweepMarketsSafely(player.getServer());
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
        view.put("activeCurrency", WalletBridge.activeCurrency().name());
        view.put("chroniclesLoaded", ChroniclesBridge.loaded());
        Map<String, Long> wallets = new LinkedHashMap<>();
        wallets.put("EMERALD", WalletBridge.balance(player, Currency.EMERALD));
        long digitalWallet = -1;
        if (ChroniclesBridge.loaded()) {
            try { digitalWallet = WalletBridge.balance(player, Currency.CHRONICLES); }
            catch (RuntimeException | LinkageError ignored) { }
        }
        wallets.put("CHRONICLES", digitalWallet);
        view.put("wallets", wallets);
        view.put("success", java.util.Set.of("gui.kingdomtreasury.donated", "gui.kingdomtreasury.claimed", "gui.kingdomtreasury.policy_saved", "gui.kingdomtreasury.published", "gui.kingdomtreasury.cancelled", "gui.kingdomtreasury.collected", "gui.kingdomtreasury.payroll_cancelled").contains(message));
        view.put("balances", treasury.balances());
        view.put("credits", Map.of(Currency.EMERALD.name(), treasury.claims(player.getUUID(), Currency.EMERALD),
                Currency.CHRONICLES.name(), treasury.claims(player.getUUID(), Currency.CHRONICLES)));
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("taxMode", policy.taxMode()); rules.put("taxBasisPoints", policy.taxBasisPoints());
        rules.put("marketTaxBasisPoints", policy.marketTaxBasisPoints());rules.put("casinoTaxBasisPoints",policy.casinoTaxBasisPoints());
        rules.put("fixedTax", policy.fixedTax()); rules.put("salary", policy.salary());
        rules.put("periodHours", policy.periodHours()); rules.put("activeMinutes", policy.activeMinutes());
        rules.put("minimumReputation", policy.minimumReputation()); rules.put("currency", policy.currency());
        rules.put("reputationReward", policy.reputationPerPublicContract()); rules.put("dailyReputationLimit", policy.dailyReputationLimit());
        view.put("policy", rules); view.put("salaryNext", treasury.nextCycleAt());
        Treasury.PendingBatch pending = treasury.pendingBatch();
        view.put("pendingPayroll", pending != null); view.put("pendingPayrollCost", pending == null ? 0 : pending.totalNet());
        view.put("pendingPayrollCurrency", pending == null ? policy.currency().name() : pending.currency().name());
        view.put("taxDue", treasury.taxDue(player.getUUID(), policy.currency()));
        view.put("contractsAvailable", ContractsBridge.available()); view.put("realmAvailable", RealmBridge.loaded());
        view.put("marketsAvailable", MarketsBridge.available());
        List<Map<String, Object>> contracts = List.of();
        if (ContractsBridge.available()) {
            try { contracts = ContractsBridge.contracts(player.getServer()); }
            catch (RuntimeException | LinkageError ex) { view.put("contractsAvailable", false); }
        }
        view.put("contracts", contracts);
        view.put("history", treasury.history().stream().map(entry -> {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("description", (entry.kind().name().startsWith("VOTE_")||entry.kind().name().startsWith("LOAN_"))?"gui.kingdomtreasury.kind_"+entry.kind().name().toLowerCase(java.util.Locale.ROOT):entry.kind()==Kind.ECONOMY_TAX?"gui.kingdomtreasury.economy_tax_income":entry.kind()==Kind.ECONOMY_POLICY?"gui.kingdomtreasury.economy_policy_changed":entry.kind()==Kind.JUSTICE_INCOME?"gui.kingdomtreasury.justice_income":entry.kind()==Kind.CASINO_TAX?"gui.kingdomtreasury.casino_tax_income":entry.kind()==Kind.VILLAGER_TAX?"gui.kingdomtreasury.villager_tax_income":entry.kind()==Kind.VILLAGER_POLICY?"gui.kingdomtreasury.villager_policy_changed":entry.details()); record.put("kind", entry.kind());
            record.put("actor",entry.actor()==null?"":entry.actor().toString());record.put("details",entry.details());record.put("actorName",entry.actor()==null?"":player.getServer().getPlayerList().getPlayer(entry.actor())!=null?pt.crownledger.compat.ChroniclesBridge.publicName(player.getServer().getPlayerList().getPlayer(entry.actor())):entry.actor().toString().substring(0,8));
            record.put("amount", entry.amount()); record.put("currency", entry.currency()); record.put("at", entry.time());
            return record;
        }).toList());
        return JSON.toJson(view);
    }

    private static void commit(MinecraftServer server, TreasuryData data, Treasury next) {
        data.requireBankAvailable();
        Treasury previous = data.treasury;
        data.treasury = next;
        data.setDirty();
        // A funded claim is collectable at the chest; it is not yet a wallet payment.
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                for (Currency currency : Currency.values()) {
                    long increase = next.claims(player.getUUID(), currency) - previous.claims(player.getUUID(), currency);
                    if (increase > 0) NotificationsBridge.notify(player, "salary",
                            Component.translatable("message.kingdomtreasury.salary_available", money(increase, currency)));
                }
            }
        } catch (RuntimeException | LinkageError disconnected) { /* Presentation cannot roll back committed public money. */ }
    }

    private static Component money(long amount, Currency currency) {
        return currency == Currency.EMERALD
                ? Component.translatable("gui.kingdomtreasury.emerald_amount", Long.toString(amount))
                : Component.translatable("message.kingdomtreasury.euro_amount", BigDecimal.valueOf(amount, 2).toPlainString(),
                        BigDecimal.valueOf(amount, 2).toPlainString().replace('.', ','));
    }

    /** Receipts are batched for ten seconds so busy markets do not flood the governor's HUD. */
    private static void queueGovernorIncome(MinecraftServer server, String key, long amount, Currency currency) {
        var pending = INCOME_NOTICES.computeIfAbsent(server, ignored -> new HashMap<>());
        var notice = new IncomeNotice(key, currency);
        long previous = pending.getOrDefault(notice, 0L);
        long total;
        try { total = Math.addExact(previous, amount); }
        catch (ArithmeticException overflow) {
            notifyGovernor(server, key, previous, currency, null);
            total = amount;
        }
        pending.put(notice, total);
        NEXT_INCOME_NOTICE.putIfAbsent(server, server.getTickCount() + 200);
    }

    private static void flushGovernorIncome(MinecraftServer server, int tick) {
        Integer next = NEXT_INCOME_NOTICE.get(server);
        if (next == null || tick - next < 0) return;
        NEXT_INCOME_NOTICE.remove(server);
        var pending = INCOME_NOTICES.remove(server);
        if (pending != null) pending.forEach((notice, amount) ->
                notifyGovernor(server, notice.key(), amount, notice.currency(), null));
    }

    private static void notifyGovernor(MinecraftServer server, String key, long amount, Currency currency, UUID exclude) {
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers())
                if (!player.getUUID().equals(exclude) && mayManage(player))
                    NotificationsBridge.notify(player, "treasury", Component.translatable(key, money(amount, currency)));
        } catch (RuntimeException | LinkageError disconnected) { /* The receipt does not control treasury transfers. */ }
    }
    private static void sync(ServerPlayer player) {
        try { player.containerMenu.broadcastChanges(); }
        catch (RuntimeException ex) { LOG.warn("Treasury settled but inventory synchronization failed for {}", player.getUUID(), ex); }
    }
    private static void send(ServerPlayer player, String message) { CrownNetwork.send(player, snapshot(player, message)); }
    private static void close(ServerPlayer player, String message) {
        SESSIONS.remove(player.getUUID());
        try { CrownNetwork.send(player, JSON.toJson(Map.of("close", true, "message", message))); }
        catch (RuntimeException ex) { LOG.debug("Could not close Crown Ledger screen for {}", player.getUUID(), ex); }
        player.displayClientMessage(Component.translatable(message), false);
    }
    private static long number(JsonObject request, String key, long fallback) {
        if (!request.has(key)) return fallback;
        return new BigDecimal(request.get(key).getAsString()).longValueExact();
    }
    private static int integer(JsonObject request, String key, int fallback) { return Math.toIntExact(number(request, key, fallback)); }
    private static int numericInteger(JsonObject request, String key, int fallback) {
        if (!request.has(key)) return fallback;
        var value = request.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("gui.kingdomtreasury.invalid_request");
        return integer(request, key, fallback);
    }
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
        TreasuryData.get(player.getServer()).requireBankAvailable();
    }
}
