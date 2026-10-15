package pt.contracts;

import com.google.gson.Gson;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pt.contracts.compat.ChroniclesBridge;
import pt.contracts.domain.Ledger;
import pt.contracts.domain.Ledger.Contract;
import pt.contracts.domain.Ledger.Currency;
import pt.contracts.domain.Ledger.Delivery;
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
        if (player.isSpectator()) return;
        SESSIONS.put(player.getUUID(), new Session(player, board));
        try { requireSession(player); send(player, ""); }
        catch (IllegalArgumentException | IllegalStateException ex) {
            logout(player.getUUID());
            player.displayClientMessage(Component.translatable(ex.getMessage()), false);
        }
    }
    public static void logout(UUID id) { SESSIONS.remove(id); }
    public static void clearSessions() { SESSIONS.clear(); }

    public static void requireSession(ServerPlayer player) {
        requireThread(player);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || System.nanoTime() - session.expires >= 0 || player.isSpectator())
            throw new IllegalStateException("gui.contracts.session_missing");
        if (session.board != null) {
            var state = player.level().getBlockState(session.board);
            if (!session.dimension.equals(player.level().dimension())
                    || player.distanceToSqr(Vec3.atCenterOf(session.board)) > 64
                    || !(state.getBlock() instanceof ContractBoardBlock board)
                    || !board.validStructure(player.level(), session.board, state))
                throw new IllegalStateException("gui.contracts.board_range");
        }
    }

    public static void action(ServerPlayer player, ContractsPackets.Action packet) {
        try {
            requireSession(player);
            Session session = SESSIONS.get(player.getUUID());
            long now = System.nanoTime();
            if (now - session.lastAction < 50_000_000L) { send(player, ""); return; }
            session.lastAction = now;
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
                case "refresh" -> message = "";
                default -> throw new IllegalArgumentException("gui.contracts.invalid_request");
            }
            send(player, message);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            if (SESSIONS.containsKey(player.getUUID())) send(player, errorMessage(ex));
            else player.displayClientMessage(Component.translatable(errorMessage(ex)), false);
        }
    }

    public static Contract publish(ServerPlayer player, String title, String itemId, int quantity, long reward) {
        requireThread(player);
        Item item = item(itemId);
        if (item == Items.AIR) throw new IllegalArgumentException("O ar não pode ser entregue.");
        ContractsData data = ContractsData.get(player.server);
        Ledger next = copy(data.ledger);
        Currency currency = currency();
        Contract contract = next.publish(UUID.randomUUID(), player.getUUID(), player.getGameProfile().getName(),
                title, itemId, quantity, reward, currency);
        if (currency == Currency.CHRONICLES) {
            ChroniclesBridge.debit(player, reward);
        } else {
            // A plan validates the whole debit before touching any slot.
            List<ItemStack> inventory = inventory(player);
            remove(inventory, Items.EMERALD, reward);
            apply(player, inventory);
        }
        commit(data, next, player);
        return contract;
    }

    public static void complete(ServerPlayer player, UUID id) {
        requireThread(player);
        ContractsData data = ContractsData.get(player.server);
        Ledger next = copy(data.ledger);
        next.complete(id, player.getUUID());
        Contract contract = next.list().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        List<ItemStack> inventory = inventory(player);
        remove(inventory, item(contract.item()), contract.quantity());
        apply(player, inventory);
        commit(data, next, player);
    }

    public static void cancel(ServerPlayer player, UUID id) {
        requireThread(player);
        ContractsData data = ContractsData.get(player.server);
        Ledger next = copy(data.ledger);
        next.cancel(id, player.getUUID());
        commit(data, next, player);
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
        return amount;
    }

    public static int collect(ServerPlayer player) {
        requireThread(player);
        ContractsData data = ContractsData.get(player.server);
        Ledger next = copy(data.ledger);
        List<ItemStack> inventory = inventory(player);
        int collected = 0;
        for (Delivery delivery : data.ledger.deliveries(player.getUUID())) {
            // Removed modded items stay in storage instead of turning into air or deleting other claims.
            ResourceLocation key = ResourceLocation.tryParse(delivery.item());
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;
            Item item = BuiltInRegistries.ITEM.get(key);
            int count = insert(inventory, item, delivery.quantity());
            if (count > 0) {
                next.takeDelivery(player.getUUID(), delivery.item(), count);
                collected += count;
            }
        }
        if (collected > 0) { apply(player, inventory); commit(data, next, player); }
        return collected;
    }

    private static Currency currency() { return ChroniclesBridge.loaded() ? Currency.CHRONICLES : Currency.EMERALD; }
    private static Ledger copy(Ledger ledger) { return new Ledger(ledger.list(), ledger.creditsSnapshot(), ledger.deliveriesSnapshot()); }
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
        ContractsData data = ContractsData.get(player.server);
        Session session = SESSIONS.get(player.getUUID());
        List<Contract> open = new ArrayList<>(data.ledger.list().stream().filter(c -> c.status() == Status.OPEN).toList());
        Collections.reverse(open);
        int pages = Math.max(1, (open.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(session == null ? 0 : session.page, pages - 1);
        if (session != null) session.page = page;
        Currency currency = currency();
        long wallet = currency == Currency.EMERALD ? count(inventory(player), Items.EMERALD) : 0;
        if (currency == Currency.CHRONICLES) {
            try {
                if (ChroniclesBridge.available(player)) wallet = ChroniclesBridge.balance(player);
                else if (message.isBlank()) message = "gui.contracts.profile_required";
            } catch (IllegalStateException ex) { if (message.isBlank()) message = ex.getMessage(); }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("me", player.getUUID().toString()); state.put("currency", currency.name()); state.put("wallet", wallet);
        state.put("emeraldCredit", data.ledger.credits(player.getUUID(), Currency.EMERALD));
        state.put("chroniclesCredit", data.ledger.credits(player.getUUID(), Currency.CHRONICLES));
        state.put("page", page); state.put("pages", pages); state.put("message", message);
        state.put("contracts", open.subList(page * PAGE_SIZE, Math.min(open.size(), (page + 1) * PAGE_SIZE)));
        // Only a tooltip preview travels over the network; all stored deliveries remain claimable.
        state.put("deliveries", data.ledger.deliveries(player.getUUID()).stream().limit(9).toList());
        return JSON.toJson(state);
    }
    private static void send(ServerPlayer player, String message) { ContractsNetwork.send(player, snapshot(player, message)); }
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
