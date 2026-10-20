package pt.contracts;

import com.google.gson.Gson;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pt.contracts.api.ContractsApi;
import pt.contracts.api.ContractsApi.Policy;
import pt.contracts.compat.ChroniclesBridge;
import pt.contracts.compat.NotificationsBridge;
import pt.contracts.domain.Ledger;
import pt.contracts.domain.Ledger.Contract;
import pt.contracts.domain.Ledger.Currency;
import pt.contracts.domain.Ledger.Delivery;
import pt.contracts.domain.Ledger.Funding;
import pt.contracts.domain.Ledger.Progress;
import pt.contracts.domain.Ledger.Status;
import pt.contracts.network.*;

/** All entry points execute on the server thread; client packets never supply a wallet or recipient. */
public final class ContractsService {
    private static final Gson JSON = new Gson();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final long SESSION_NANOS = 300_000_000_000L;
    private static final int PAGE_SIZE = 8;
    private ContractsService() {}

    private static final class Session {
        final BlockPos board;
        final ResourceKey<Level> dimension;
        final long expires = System.nanoTime() + SESSION_NANOS;
        long lastAction;
        int page;
        Session(ServerPlayer player, BlockPos board) {
            this.board = board == null ? null : board.immutable();
            this.dimension = player.level().dimension();
        }
    }

    public static void open(ServerPlayer player, BlockPos board) {
        requireThread(player);
        SESSIONS.put(player.getUUID(), new Session(player, board));
        try { requireSession(player); flushProgress(player); send(player, ""); }
        catch (IllegalArgumentException | IllegalStateException ex) {
            close(player, errorMessage(ex));
        }
    }
    public static void logout(UUID id) { SESSIONS.remove(id); }
    public static void clearSessions() { SESSIONS.clear(); }

    public static void requireSession(ServerPlayer player) {
        requireThread(player);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.board == null || System.nanoTime() - session.expires >= 0 || player.isSpectator())
            throw new IllegalStateException("gui.contracts.session_missing");
        // Check dimension, distance and loaded chunk before any block/state access.
        if (!session.dimension.equals(player.level().dimension())
                || player.distanceToSqr(Vec3.atCenterOf(session.board)) > 64
                || !player.level().hasChunkAt(session.board))
            throw new IllegalStateException("gui.contracts.board_range");
        var state = player.level().getBlockState(session.board);
        if (!(state.getBlock() instanceof ContractBoardBlock board)
                || !board.validStructure(player.level(), session.board, state))
            throw new IllegalStateException("gui.contracts.board_range");
    }

    public static void action(ServerPlayer player, ContractsPackets.Action packet) {
        try {
            requireSession(player);
            Session session = SESSIONS.get(player.getUUID());
            long now = System.nanoTime();
            if (now - session.lastAction < 50_000_000L) { send(player, ""); return; }
            session.lastAction = now;
            if(packet.operation().equals("daily_open")){pt.contracts.daily.DailyService.open(player);return;}
            String message;
            switch (packet.operation()) {
                case "publish" -> {
                    if (!currency().name().equals(packet.currency())) throw new IllegalArgumentException("gui.contracts.currency_changed");
                    publish(player, packet.title(), packet.item(), packet.quantity(), packet.reward());
                    session.page = 0;
                    message = "gui.contracts.published";
                }
                case "complete" -> {
                    complete(player, UUID.fromString(packet.contractId()));
                    message = "gui.contracts.completed";
                }
                case "cancel" -> {
                    cancel(player, UUID.fromString(packet.contractId()));
                    message = "gui.contracts.cancelled";
                }
                case "claim_money" -> {
                    long amount = claimMoney(player, Currency.valueOf(packet.currency()));
                    message = amount > 0 ? "gui.contracts.money_claimed" : "gui.contracts.nothing_money";
                }
                case "claim_items" -> {
                    int count = collect(player);
                    message = count > 0 ? "gui.contracts.items_claimed" : "gui.contracts.nothing_items";
                }
                case "page" -> { session.page = Math.max(0, packet.quantity()); message = ""; }
                case "refresh" -> { flushProgress(player); message = ""; }
                default -> throw new IllegalArgumentException("gui.contracts.invalid_request");
            }
            send(player, message);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Never retry a snapshot after its physical session became invalid.
            try { requireSession(player); }
            catch (IllegalArgumentException | IllegalStateException invalid) {
                close(player, errorMessage(invalid));
                return;
            }
            try { send(player, errorMessage(ex)); }
            catch (RuntimeException invalid) { close(player, errorMessage(invalid)); }
        }
    }

    public static Contract publish(ServerPlayer player, String title, String itemId, int quantity, long reward) {
        requireThread(player);
        Policy policy = ContractsApi.policy(player.server);
        requireReputation(player, policy);
        Item item = item(itemId);
        if (item == Items.AIR) throw new IllegalArgumentException("O ar não pode ser entregue.");
        ContractsData data = ContractsData.get(player.server);
        Ledger next = copy(data.ledger);
        Currency currency = currency();
        Contract contract = next.publish(UUID.randomUUID(), player.getUUID(), player.getGameProfile().getName(),
                title, itemId, quantity, reward, currency, Funding.PLAYER, player.getUUID(),
                policy.taxBasisPoints(), 0, 0);
        debit(player, reward, currency);
        commit(data, next, player);
        NotificationsBridge.notify(player, "contract", Component.translatable("notification.contracts.published", contract.title()));
        return contract;
    }

    public static void complete(ServerPlayer player, UUID id) {
        requireThread(player);
        ContractsData data = ContractsData.get(player.server);
        Ledger next = copy(data.ledger);
        long epochDay=LocalDate.now(ZoneOffset.UTC).toEpochDay();int dailyReputation=0;
        if(contract(next,id).funding()==Funding.KINGDOM)try{
            var daily=pt.contracts.daily.DailyData.get(player.getServer());daily.available();
            epochDay=Math.max(epochDay,Math.floorDiv(daily.ledger.highWater,86_400_000L));dailyReputation=daily.ledger.reputation(player.getUUID(),System.currentTimeMillis());
        }catch(RuntimeException|LinkageError unavailable){dailyReputation=100;ContractsMod.LOG.warn("Daily reputation is unavailable; public delivery and money remain usable with no additional reputation");}
        next.complete(id,player.getUUID(),epochDay,dailyReputation);
        Contract contract = next.list().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        List<ItemStack> inventory = inventory(player);
        remove(inventory, item(contract.item()), contract.quantity());
        apply(player, inventory);
        commit(data, next, player);
        flushProgress(player);
        NotificationsBridge.notify(player, "contract", Component.translatable("notification.contracts.completed",
                contract.title(), money(contract.netReward(), contract.currency())));
        ServerPlayer author = player.server.getPlayerList().getPlayer(contract.author());
        if (author != null) NotificationsBridge.notify(author, "contract",
                Component.translatable("notification.contracts.delivery", contract.title()));
    }

    public static void cancel(ServerPlayer player, UUID id) {
        requireThread(player);
        ContractsData data = ContractsData.get(player.server);
        Ledger next = copy(data.ledger);
        if (contract(next, id).funding() == Funding.KINGDOM)
            throw new IllegalStateException("gui.contracts.public_only");
        Contract cancelled = contract(next, id);
        next.cancel(id, player.getUUID());
        commit(data, next, player);
        NotificationsBridge.notify(player, "contract", Component.translatable("notification.contracts.cancelled", cancelled.title()));
    }

    /** Money stays claimable if its integration is unavailable or the inventory/wallet is full. */
    public static long claimMoney(ServerPlayer player, Currency currency) {
        requireThread(player);
        ContractsData data = ContractsData.get(player.server);
        long pending = data.ledger.credits(player.getUUID(), currency);
        if (pending == 0) return 0;
        Ledger next = copy(data.ledger);
        long amount;
        if (currency == Currency.CHRONICLES) {
            amount = Math.min(pending, ChroniclesBridge.MAX_CENTS - ChroniclesBridge.balance(player));
            if (amount == 0) return 0;
            next.takeCredit(player.getUUID(), currency, amount);
            ChroniclesBridge.credit(player, amount);
        } else {
            List<ItemStack> inventory = inventory(player);
            amount = insert(inventory, Items.EMERALD, pending);
            if (amount == 0) return 0;
            next.takeCredit(player.getUUID(), currency, amount);
            apply(player, inventory);
        }
        commit(data, next, player);
        NotificationsBridge.notify(player, "money", Component.translatable("notification.contracts.claimed", money(amount, currency)));
        return amount;
    }

    public static int collect(ServerPlayer player) {
        requireThread(player);
        return collect(player, player.getUUID());
    }

    private static int collect(ServerPlayer player, UUID recipient) {
        ContractsData data = ContractsData.get(player.server);
        Ledger next = copy(data.ledger);
        List<ItemStack> inventory = inventory(player);
        int collected = 0;
        for (Delivery delivery : data.ledger.deliveries(recipient)) {
            // Removed modded items stay in storage instead of turning into air or deleting other claims.
            ResourceLocation key = ResourceLocation.tryParse(delivery.item());
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;
            Item item = BuiltInRegistries.ITEM.get(key);
            int count = insert(inventory, item, delivery.quantity());
            if (count > 0) {
                next.takeDelivery(recipient, delivery.item(), count);
                collected += count;
            }
        }
        if (collected > 0) { apply(player, inventory); commit(data, next, player); }
        return collected;
    }

    /** Public money is reserved from the realm's actual stored balance, never a manager's wallet. */
    public static Contract publishPublic(ServerPlayer manager, String title, String itemId, int quantity, long reward) {
        requireThread(manager);
        ContractsApi.requireManager(manager);
        Policy policy = ContractsApi.policy(manager.server);
        item(itemId);
        ContractsData data = ContractsData.get(manager.server);
        Ledger next = copy(data.ledger);
        Contract contract = next.publish(UUID.randomUUID(), Ledger.KINGDOM_ACCOUNT, "Kingdom",
                title, itemId, quantity, reward, currency(), Funding.KINGDOM, manager.getUUID(),
                policy.taxBasisPoints(), policy.reputationPerPublicContract(), policy.dailyReputationLimit());
        next.takeCredit(Ledger.KINGDOM_ACCOUNT, contract.currency(), reward);
        commit(data, next, manager);
        NotificationsBridge.notify(manager, "contract", Component.translatable("notification.contracts.public_published", contract.title()));
        return contract;
    }

    /** Donation entry point for a physical Treasury menu; amount is debited before ledger commit. */
    public static void depositKingdom(ServerPlayer donor, long amount, String currencyName) {
        requireThread(donor);
        if (!ContractsApi.treasuryAvailable()) throw new IllegalStateException("gui.contracts.treasury_unavailable");
        ContractsApi.policy(donor.server);
        Currency currency = Currency.valueOf(currencyName);
        if (currency != currency()) throw new IllegalArgumentException("gui.contracts.currency_changed");
        if (amount < 1 || amount > ChroniclesBridge.MAX_CENTS)
            throw new IllegalArgumentException("Invalid treasury deposit amount");
        ContractsData data = ContractsData.get(donor.server);
        Ledger next = copy(data.ledger);
        next.addCredit(Ledger.KINGDOM_ACCOUNT, currency, amount);
        debit(donor, amount, currency);
        commit(data, next, donor);
    }

    public static long kingdomBalance(MinecraftServer server, String currencyName) {
        ContractsApi.requireServerThread(server);
        return ContractsData.get(server).ledger.credits(Ledger.KINGDOM_ACCOUNT, Currency.valueOf(currencyName));
    }

    public static List<Delivery> kingdomDeliveries(MinecraftServer server) {
        ContractsApi.requireServerThread(server);
        return ContractsData.get(server).ledger.deliveries(Ledger.KINGDOM_ACCOUNT);
    }

    public static void cancelPublic(ServerPlayer manager, UUID id) {
        requireThread(manager);
        ContractsApi.requireManager(manager);
        ContractsData data = ContractsData.get(manager.server);
        Ledger next = copy(data.ledger);
        if (contract(next, id).funding() != Funding.KINGDOM)
            throw new IllegalArgumentException("gui.contracts.public_only");
        Contract cancelled = contract(next, id);
        next.cancel(id, Ledger.KINGDOM_ACCOUNT);
        commit(data, next, manager);
        NotificationsBridge.notify(manager, "contract", Component.translatable("notification.contracts.public_cancelled", cancelled.title()));
    }

    public static int collectKingdom(ServerPlayer manager) {
        requireThread(manager);
        ContractsApi.requireManager(manager);
        return collect(manager, Ledger.KINGDOM_ACCOUNT);
    }

    private static Contract contract(Ledger ledger, UUID id) {
        return ledger.list().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Contract does not exist"));
    }

    private static Component money(long amount, Currency currency) {
        return currency == Currency.CHRONICLES
                ? Component.literal(String.format(java.util.Locale.ROOT, "%d,%02d €", amount / 100, amount % 100))
                : Component.translatable("notification.contracts.emeralds", Long.toString(amount));
    }

    private static void requireReputation(ServerPlayer player, Policy policy) {
        if (policy.minimumReputation() == 0) return;
        if (!ChroniclesBridge.loaded() || !ChroniclesBridge.available(player))
            throw new IllegalStateException("gui.contracts.profile_required");
        if (ChroniclesBridge.reputation(player) < policy.minimumReputation())
            throw new IllegalStateException("gui.contracts.reputation_required");
    }

    private static void debit(ServerPlayer player, long amount, Currency currency) {
        if (currency == Currency.CHRONICLES) ChroniclesBridge.debit(player, amount);
        else {
            List<ItemStack> inventory = inventory(player);
            remove(inventory, Items.EMERALD, amount);
            apply(player, inventory);
        }
    }

    /** Applying progress may fail independently of an already-settled material/payment transaction. */
    private static void flushProgress(ServerPlayer player) {
        ContractsData data = ContractsData.get(player.server);
        Progress pending = data.ledger.pendingProgress(player.getUUID());
        if (pending.reputation() == 0 && pending.missions() == 0) return;
        try {
            if (!ChroniclesBridge.available(player)) return;
            Ledger next = copy(data.ledger);
            next.takeProgress(player.getUUID(), pending);
            ChroniclesBridge.applyProgress(player, pending.reputation(), pending.missions());
            commit(data, next, player);
        } catch (RuntimeException | LinkageError ex) {
            ContractsMod.LOG.warn("Contract progress remains pending for {}", player.getUUID(), ex);
        }
    }

    private static Currency currency() { return ChroniclesBridge.loaded() ? Currency.CHRONICLES : Currency.EMERALD; }
    private static Ledger copy(Ledger ledger) { return ledger.copy(); }
    private static void commit(ContractsData data, Ledger next, ServerPlayer player) {
        data.ledger = next;
        data.setDirty();
        // Settlement is complete before notifying clients. A disconnected client must not
        // cause an inventory change to be applied without its matching ledger transition.
        try { player.containerMenu.broadcastChanges(); }
        catch (RuntimeException ex) { ContractsMod.LOG.warn("Contract settled, but inventory synchronization failed for {}", player.getUUID(), ex); }
    }
    private static void requireThread(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalStateException("Os contratos só podem ser usados na thread principal do servidor.");
        if (player.isSpectator()) throw new IllegalStateException("Espectadores não podem alterar contratos.");
    }
    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key) || BuiltInRegistries.ITEM.get(key) == Items.AIR)
            throw new IllegalArgumentException("Indica um objeto válido, por exemplo minecraft:oak_log.");
        return BuiltInRegistries.ITEM.get(key);
    }
    private static List<ItemStack> inventory(ServerPlayer player) {
        return player.getInventory().items.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    private static boolean plain(ItemStack stack, Item item) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, new ItemStack(item));
    }
    private static long count(List<ItemStack> inventory, Item item) {
        return inventory.stream().filter(stack -> plain(stack, item)).mapToLong(ItemStack::getCount).sum();
    }
    private static void remove(List<ItemStack> inventory, Item item, long amount) {
        if (amount < 1 || count(inventory, item) < amount)
            throw new IllegalStateException("gui.contracts.insufficient_items");
        long remaining = amount;
        for (ItemStack stack : inventory) {
            if (!plain(stack, item)) continue;
            int take = (int) Math.min(remaining, stack.getCount());
            stack.shrink(take); remaining -= take;
            if (remaining == 0) break;
        }
    }
    private static int insert(List<ItemStack> inventory, Item item, long amount) {
        ItemStack prototype = new ItemStack(item);
        if (prototype.isEmpty()) return 0;
        long remaining = amount;
        for (ItemStack stack : inventory) {
            if (!plain(stack, item)) continue;
            int add = (int) Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
            stack.grow(add); remaining -= add;
            if (remaining == 0) return (int) amount;
        }
        for (int i = 0; i < inventory.size() && remaining > 0; i++) {
            if (!inventory.get(i).isEmpty()) continue;
            int add = (int) Math.min(remaining, prototype.getMaxStackSize());
            inventory.set(i, new ItemStack(item, add)); remaining -= add;
        }
        return (int) (amount - remaining); // Bounded by the 36 inventory slots, even for a long credit balance.
    }
    private static void apply(ServerPlayer player, List<ItemStack> inventory) {
        for (int i = 0; i < inventory.size(); i++) player.getInventory().items.set(i, inventory.get(i));
        player.getInventory().setChanged();
    }

    public static String snapshot(ServerPlayer player, String message) {
        requireSession(player);
        ContractsData data = ContractsData.get(player.server);
        Session session = SESSIONS.get(player.getUUID());
        List<Contract> open = new ArrayList<>(data.ledger.list().stream().filter(c -> c.status() == Status.OPEN).toList());
        Collections.reverse(open);
        int pages = Math.max(1, (open.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(session == null ? 0 : session.page, pages - 1);
        if (session != null) session.page = page;
        Currency currency = currency();
        long wallet = currency == Currency.EMERALD ? count(inventory(player), Items.EMERALD) : 0;
        boolean profileAvailable = currency == Currency.EMERALD;
        if (currency == Currency.CHRONICLES) {
            try {
                if (ChroniclesBridge.available(player)) {
                    wallet = ChroniclesBridge.balance(player);
                    profileAvailable = true;
                }
                else if (message.isBlank()) message = "gui.contracts.profile_required";
            } catch (IllegalStateException ex) { if (message.isBlank()) message = ex.getMessage(); }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("me", player.getUUID().toString()); state.put("currency", currency.name()); state.put("wallet", wallet);
        state.put("emeraldCredit", data.ledger.credits(player.getUUID(), Currency.EMERALD));
        state.put("chroniclesCredit", data.ledger.credits(player.getUUID(), Currency.CHRONICLES));
        state.put("page", page); state.put("pages", pages); state.put("message", message);
        state.put("contracts", open.subList(page * PAGE_SIZE, Math.min(open.size(), (page + 1) * PAGE_SIZE))
                .stream().map(ContractsService::contractState).toList());
        int reputation = -1;
        try { if (ChroniclesBridge.available(player)) reputation = ChroniclesBridge.reputation(player); }
        catch (RuntimeException | LinkageError ignored) { /* Existing claims remain accessible if the optional adapter is broken. */ }
        Policy policy = Policy.DEFAULT;
        String publicationReason = profileAvailable ? "" : "gui.contracts.profile_required";
        boolean canPublish = profileAvailable;
        try {
            policy = ContractsApi.policy(player.server);
            if (policy.minimumReputation() > 0 && reputation < policy.minimumReputation()) {
                canPublish = false;
                publicationReason = reputation < 0 ? "gui.contracts.profile_required" : "gui.contracts.reputation_required";
            }
        } catch (RuntimeException | LinkageError ex) {
            canPublish = false;
            publicationReason = "gui.contracts.treasury_unavailable";
        }
        Progress pending = data.ledger.pendingProgress(player.getUUID());
        state.put("reputation", reputation); state.put("requiredReputation", policy.minimumReputation());
        state.put("canPublish", canPublish); state.put("treasuryAvailable", ContractsApi.treasuryAvailable());
        state.put("publicationReason", publicationReason); state.put("taxBasisPoints", policy.taxBasisPoints());
        state.put("reputationPending", pending.reputation()); state.put("missionsPending", pending.missions());
        // Only a tooltip preview travels over the network; all stored deliveries remain claimable.
        state.put("deliveries", data.ledger.deliveries(player.getUUID()).stream().limit(9).toList());
        return JSON.toJson(state);
    }

    private static Map<String, Object> contractState(Contract contract) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", contract.id()); state.put("author", contract.author()); state.put("authorName", contract.authorName());
        state.put("title", contract.title()); state.put("item", contract.item()); state.put("quantity", contract.quantity());
        state.put("reward", contract.reward()); state.put("currency", contract.currency()); state.put("status", contract.status());
        if (contract.worker() != null) state.put("worker", contract.worker());
        state.put("funding", contract.funding()); state.put("issuer", contract.issuer());
        state.put("taxBasisPoints", contract.taxBasisPoints()); state.put("tax", contract.tax());
        state.put("netReward", contract.netReward()); state.put("reputationReward", contract.reputationReward());
        state.put("dailyReputationLimit", contract.dailyReputationLimit());
        return state;
    }

    private static void send(ServerPlayer player, String message) { ContractsNetwork.send(player, snapshot(player, message)); }
    private static void close(ServerPlayer player, String message) {
        logout(player.getUUID());
        try { ContractsNetwork.send(player, JSON.toJson(Map.of("close", true, "message", message))); }
        catch (RuntimeException ex) { ContractsMod.LOG.debug("Could not close contract screen for {}", player.getUUID(), ex); }
        player.displayClientMessage(Component.translatable(message), false);
    }
    private static String errorMessage(RuntimeException exception) {
        return switch (String.valueOf(exception.getMessage())) {
            case "Authors cannot complete their own contracts" -> "gui.contracts.own_contract";
            case "Only the author can cancel this contract" -> "gui.contracts.only_author";
            case "Contract does not exist" -> "gui.contracts.unavailable";
            case "Contract is no longer open" -> "gui.contracts.settled";
            case "The contract archive is full of open contracts" -> "gui.contracts.archive_full";
            case "You already have 16 open contracts" -> "gui.contracts.too_many";
            default -> Objects.toString(exception.getMessage(), "Não foi possível concluir a operação.");
        };
    }
}
