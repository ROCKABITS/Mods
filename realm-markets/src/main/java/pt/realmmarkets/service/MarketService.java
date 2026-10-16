package pt.realmmarkets.service;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.Vec3;
import pt.realmmarkets.api.MarketsApi;
import pt.realmmarkets.block.MarketStallBlock;
import pt.realmmarkets.compat.ChroniclesBridge;
import pt.realmmarkets.data.MarketData;
import pt.realmmarkets.domain.Currency;
import pt.realmmarkets.domain.Listing;
import pt.realmmarkets.domain.MarketLedger;
import pt.realmmarkets.domain.SaleAmounts;
import pt.realmmarkets.domain.Stall;
import pt.realmmarkets.network.MarketNetwork;

/** All stock, wallet and ownership decisions happen on the server thread. */
public final class MarketService {
    private static final Gson JSON = new Gson();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final String PREFIX = "gui.realmmarkets.";
    private MarketService() {}

    private record Session(MinecraftServer server, ResourceKey<Level> dimension, BlockPos master,
                           UUID stall, long expiresAt, long expiresTick) {}

    public static void open(ServerPlayer player, BlockPos master) {
        try {
            requirePlayer(player);
            physical(player, master);
            Stall stall = MarketData.get(player.getServer()).ledger.at(dimension(player.level()), master.asLong())
                    .orElseThrow(() -> failure("stall_unavailable"));
            SESSIONS.put(player.getUUID(), new Session(player.getServer(), player.level().dimension(), master.immutable(),
                    stall.id(), System.currentTimeMillis() + 300_000, ticks(player.getServer()) + 6000));
            send(player, "");
        } catch (RuntimeException | LinkageError failure) { close(player, error(failure)); }
    }

    public static Stall requireSession(ServerPlayer player) {
        requirePlayer(player);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.server() != player.getServer()
                || !session.dimension().equals(player.level().dimension())
                || System.currentTimeMillis() >= session.expiresAt() || ticks(player.getServer()) >= session.expiresTick()) {
            throw failure("session_expired");
        }
        physical(player, session.master());
        Stall stall = MarketData.get(player.getServer()).ledger.at(dimension(player.level()), session.master().asLong())
                .orElseThrow(() -> failure("stall_unavailable"));
        if (!stall.id().equals(session.stall())) throw failure("session_expired");
        return stall;
    }

    private static void physical(ServerPlayer player, BlockPos master) {
        if (master == null || !player.level().hasChunkAt(master)
                || player.distanceToSqr(Vec3.atCenterOf(master)) > 64) throw failure("stall_range");
        var state = player.level().getBlockState(master);
        if (!(state.getBlock() instanceof MarketStallBlock block) || state.getValue(MarketStallBlock.PART) != 0
                || !block.validStructure(player.level(), master, state)) throw failure("stall_range");
    }

    /** Placement preflight runs before BlockItem consumes an item or changes the world. */
    public static void mayPlace(ServerPlayer player, BlockPos master) {
        requirePlayer(player);
        MarketLedger ledger = MarketData.get(player.getServer()).ledger;
        if (ledger.at(dimension(player.level()), master.asLong()).isPresent()) throw failure("location_reserved");
        if (ledger.stalls().size() >= MarketLedger.MAX_STALLS) throw failure("market_limit");
    }

    public static void placed(ServerPlayer player, BlockPos master) {
        mayPlace(player, master);
        var state = player.level().getBlockState(master);
        if (!(state.getBlock() instanceof MarketStallBlock block) || state.getValue(MarketStallBlock.PART) != 0
                || !block.validStructure(player.level(), master, state)) throw failure("stall_range");
        MarketData data = MarketData.get(player.getServer());
        MarketLedger next = data.ledger.copy();
        next.create(player.getUUID(), dimension(player.level()), master.asLong(), "Banca de " + player.getGameProfile().getName(),
                ChroniclesBridge.loaded() ? Currency.CHRONICLES : Currency.EMERALD);
        data.replace(next);
    }

    /** No operator or creative exemption. Unregistered command-created empty geometry has no owner. */
    public static boolean canBreak(ServerPlayer player, BlockPos master) {
        try {
            requirePlayer(player);
            var record = MarketData.get(player.getServer()).ledger.at(dimension(player.level()), master.asLong());
            return record.isEmpty() || (record.get().owner().equals(player.getUUID()) && record.get().empty());
        } catch (RuntimeException | LinkageError failure) { return false; }
    }

    /** External commands can remove geometry; funded records are retained rather than destroyed. */
    public static void removed(Level level, BlockPos master) {
        if (level.isClientSide || level.getServer() == null) return;
        MarketData data = MarketData.get(level.getServer());
        var record = data.ledger.at(dimension(level), master.asLong());
        if (record.isPresent() && record.get().empty()) {
            MarketLedger next = data.ledger.copy();
            next.remove(record.get().id(), record.get().owner());
            data.replace(next);
        }
    }

    public static void action(ServerPlayer player, String operation, String json) {
        try {
            requirePlayer(player);
            if (operation == null || operation.length() > 24) throw new IllegalArgumentException();
            Map<String, Object> request = request(operation, json);
            if (operation.equals("close")) { SESSIONS.remove(player.getUUID()); return; }
            requireSession(player);
            String message = switch (operation) {
                case "refresh" -> "";
                case "configure" -> {
                    trustedConfigure(player, integer(request, "slot"), number(request, "price"));
                    yield PREFIX + "configured";
                }
                case "stock" -> {
                    trustedStock(player, integer(request, "slot"), integer(request, "quantity"));
                    yield PREFIX + "stocked";
                }
                case "withdraw" -> {
                    trustedWithdraw(player, integer(request, "slot"), integer(request, "quantity"));
                    yield PREFIX + "withdrawn";
                }
                case "buy" -> {
                    int slot = integer(request, "slot");
                    Listing current = listing(requireSession(player), slot);
                    // The client supplies only its displayed quote. Actual item/price always
                    // come from the server; repricing/replacing an offer cancels this request.
                    if (current.unitPrice() != number(request, "price") || !current.item().equals(request.get("item"))) {
                        throw failure("offer_changed");
                    }
                    buy(player, slot, integer(request, "quantity"), integer(request, "marketTaxBasisPoints"));
                    yield PREFIX + "purchased";
                }
                case "claim" -> { trustedClaim(player); yield PREFIX + "claimed"; }
                case "rename" -> { trustedRename(player, (String) request.get("title")); yield PREFIX + "renamed"; }
                default -> throw new IllegalArgumentException();
            };
            send(player, message);
        } catch (RuntimeException | LinkageError failure) {
            try { requireSession(player); send(player, error(failure)); }
            catch (RuntimeException | LinkageError invalid) { close(player, error(invalid)); }
        }
    }

    public static Map<String, Object> snapshot(ServerPlayer player) {
        Stall stall = requireSession(player);
        boolean owner = stall.owner().equals(player.getUUID());
        boolean chronicles = false;
        long balance = 0;
        try { chronicles = ChroniclesBridge.available(player); }
        catch (RuntimeException | LinkageError ignored) { /* Installed but unavailable never changes currency. */ }
        if (stall.currency() == Currency.EMERALD) balance = count(copyInventory(player), Items.EMERALD);
        else if (chronicles) balance = ChroniclesBridge.balance(player);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("open", true); view.put("owner", owner); view.put("title", stall.title());
        view.put("currency", stall.currency().name()); view.put("earnings", owner ? stall.earnings() : 0L);
        view.put("balance", balance); view.put("message", ""); view.put("chroniclesAvailable", chronicles);
        // A treasury outage must not prevent owners recovering stock or existing earnings.
        int taxBasisPoints = 0;
        boolean treasuryAvailable = true;
        try { taxBasisPoints = MarketsApi.salesTaxBasisPoints(player.getServer()); }
        catch (RuntimeException | LinkageError unavailable) { treasuryAvailable = false; }
        view.put("marketTaxBasisPoints", taxBasisPoints);
        view.put("treasuryAvailable", treasuryAvailable);
        view.put("sample", owner && plain(player.getMainHandItem())
                ? BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString() : "");
        List<Map<String, Object>> offers = new ArrayList<>();
        for (Listing offer : stall.listings()) {
            offers.add(Map.of("slot", offer.slot(), "item", offer.item(), "stock", offer.stock(), "price", offer.unitPrice()));
        }
        view.put("listings", offers);
        return view;
    }

    public static void trustedConfigure(ServerPlayer player, int slot, long price) {
        Stall stall = owner(player);
        ItemStack held = player.getMainHandItem();
        if (!plain(held)) throw failure("plain_item_required");
        MarketData data = MarketData.get(player.getServer());
        MarketLedger next = data.ledger.copy();
        next.configure(stall.id(), player.getUUID(), slot, BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), price);
        data.replace(next);
    }

    public static void trustedStock(ServerPlayer player, int slot, int quantity) {
        Stall stall = owner(player);
        Listing listing = listing(stall, slot);
        Item item = item(listing.item());
        MarketData data = MarketData.get(player.getServer());
        MarketLedger next = data.ledger.copy();
        next.stock(stall.id(), player.getUUID(), slot, quantity);
        List<ItemStack> inventory = copyInventory(player);
        consume(inventory, item, quantity, "stock_missing");
        commit(player, data, next, inventory);
    }

    public static void trustedWithdraw(ServerPlayer player, int slot, int quantity) {
        Stall stall = owner(player);
        Listing listing = listing(stall, slot);
        Item item = item(listing.item());
        MarketData data = MarketData.get(player.getServer());
        MarketLedger next = data.ledger.copy();
        next.withdraw(stall.id(), player.getUUID(), slot, quantity);
        List<ItemStack> inventory = copyInventory(player);
        insert(inventory, item, quantity);
        commit(player, data, next, inventory);
    }

    public static void trustedBuy(ServerPlayer player, int slot, int quantity) {
        buy(player, slot, quantity, null);
    }

    private static void buy(ServerPlayer player, int slot, int quantity, Integer expectedTaxBasisPoints) {
        Stall stall = requireSession(player);
        int taxBasisPoints = MarketsApi.salesTaxBasisPoints(player.getServer());
        if (expectedTaxBasisPoints != null && taxBasisPoints != expectedTaxBasisPoints) throw failure("offer_changed");
        Listing listing = listing(stall, slot);
        Item item = item(listing.item());
        MarketData data = MarketData.get(player.getServer());
        MarketLedger next = data.ledger.copy();
        SaleAmounts amounts = next.purchase(stall.id(), player.getUUID(), slot, quantity, taxBasisPoints);
        long cost = amounts.gross();
        List<ItemStack> inventory = copyInventory(player);
        if (stall.currency() == Currency.EMERALD) consume(inventory, Items.EMERALD, cost, "insufficient_funds");
        insert(inventory, item, quantity);
        // Capacity, stock, self-purchase and retained-earnings cap have all been checked
        // on copies before the only fallible external write (the online RP wallet).
        if (stall.currency() == Currency.CHRONICLES) ChroniclesBridge.debit(player, cost);
        commit(player, data, next, inventory);
    }

    /** Collect as much as fits. Any uncollected amount remains owned by this stall. */
    public static void trustedClaim(ServerPlayer player) {
        Stall stall = owner(player);
        if (stall.earnings() == 0) throw failure("nothing_to_claim");
        MarketData data = MarketData.get(player.getServer());
        MarketLedger next = data.ledger.copy();
        List<ItemStack> inventory = copyInventory(player);
        long amount;
        if (stall.currency() == Currency.EMERALD) {
            amount = Math.min(stall.earnings(), capacity(inventory, Items.EMERALD));
            if (amount == 0) throw failure("inventory_full");
            insert(inventory, Items.EMERALD, Math.toIntExact(amount));
        } else {
            amount = Math.min(stall.earnings(), ChroniclesBridge.MAX_CENTS - ChroniclesBridge.balance(player));
            if (amount == 0) throw failure("wallet_full");
        }
        next.claim(stall.id(), player.getUUID(), amount);
        if (stall.currency() == Currency.CHRONICLES) ChroniclesBridge.credit(player, amount);
        commit(player, data, next, inventory);
    }

    public static void trustedRename(ServerPlayer player, String title) {
        Stall stall = owner(player);
        MarketData data = MarketData.get(player.getServer());
        MarketLedger next = data.ledger.copy();
        next.rename(stall.id(), player.getUUID(), title);
        data.replace(next);
    }

    private static Stall owner(ServerPlayer player) {
        Stall stall = requireSession(player);
        if (!stall.owner().equals(player.getUUID())) throw failure("owner_required");
        return stall;
    }

    private static Listing listing(Stall stall, int slot) {
        if (slot < 0 || slot >= Listing.SLOT_COUNT) throw new IllegalArgumentException();
        return stall.listing(slot).orElseThrow(() -> failure("offer_missing"));
    }

    /** Components must exactly match the item's own defaults; no lossy flattening. */
    public static boolean plain(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() instanceof BundleItem
                || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK)
                || (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof EntityBlock)
                || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(DataComponents.CONTAINER_LOOT) || stack.has(DataComponents.BLOCK_ENTITY_DATA)
                || stack.has(DataComponents.ENTITY_DATA) || stack.has(DataComponents.CHARGED_PROJECTILES)
                || stack.has(DataComponents.WRITABLE_BOOK_CONTENT) || stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) return false;
        return ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) throw failure("item_unavailable");
        Item item = BuiltInRegistries.ITEM.get(key);
        if (!plain(new ItemStack(item))) throw failure("plain_item_required");
        return item;
    }

    private static List<ItemStack> copyInventory(ServerPlayer player) {
        List<ItemStack> inventory = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) inventory.add(player.getInventory().getItem(slot).copy());
        return inventory;
    }

    private static long count(List<ItemStack> inventory, Item item) {
        long total = 0;
        for (ItemStack stack : inventory) if (stack.is(item) && plain(stack)) total += stack.getCount();
        return total;
    }

    private static void consume(List<ItemStack> inventory, Item item, long amount, String error) {
        if (amount <= 0 || count(inventory, item) < amount) throw failure(error);
        long remaining = amount;
        for (ItemStack stack : inventory) {
            if (!stack.is(item) || !plain(stack)) continue;
            int removed = (int) Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
            if (remaining == 0) return;
        }
        throw new IllegalStateException("Inventory preflight mismatch.");
    }

    private static long capacity(List<ItemStack> inventory, Item item) {
        int max = Math.min(64, new ItemStack(item).getMaxStackSize());
        long result = 0;
        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) result += max;
            else if (stack.is(item) && plain(stack)) result += Math.max(0, max - stack.getCount());
        }
        return result;
    }

    private static void insert(List<ItemStack> inventory, Item item, int amount) {
        if (amount <= 0 || capacity(inventory, item) < amount) throw failure("inventory_full");
        int remaining = amount;
        int max = Math.min(64, new ItemStack(item).getMaxStackSize());
        for (ItemStack stack : inventory) {
            if (!stack.is(item) || !plain(stack)) continue;
            int added = Math.min(remaining, Math.max(0, max - stack.getCount()));
            stack.grow(added);
            remaining -= added;
            if (remaining == 0) return;
        }
        for (int slot = 0; slot < 36; slot++) {
            if (!inventory.get(slot).isEmpty()) continue;
            int added = Math.min(remaining, max);
            inventory.set(slot, new ItemStack(item, added));
            remaining -= added;
            if (remaining == 0) return;
        }
        throw new IllegalStateException("Inventory preflight mismatch.");
    }

    private static void commit(ServerPlayer player, MarketData data, MarketLedger next, List<ItemStack> inventory) {
        // No callbacks or packet sends occur between these in-memory assignments.
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, inventory.get(slot));
        data.replace(next);
        player.getInventory().setChanged();
        try { player.containerMenu.broadcastChanges(); }
        catch (RuntimeException ignored) { /* A disconnected client cannot roll back a completed sale. */ }
    }

    /** Strict flat objects, unique fields and decimal integer syntax only. */
    private static Map<String, Object> request(String operation, String raw) {
        Set<String> fields = switch (operation) {
            case "refresh", "claim", "close" -> Set.of();
            case "configure" -> Set.of("slot", "price");
            case "stock", "withdraw" -> Set.of("slot", "quantity");
            case "buy" -> Set.of("slot", "quantity", "price", "item", "marketTaxBasisPoints");
            case "rename" -> Set.of("title");
            default -> throw new IllegalArgumentException();
        };
        if (raw == null || raw.length() > 2048) throw new IllegalArgumentException();
        Map<String, Object> result = new HashMap<>();
        try (JsonReader reader = new JsonReader(new StringReader(raw))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new IllegalArgumentException();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!fields.contains(field) || result.containsKey(field)) throw new IllegalArgumentException();
                if (field.equals("title") || field.equals("item")) {
                    if (reader.peek() != JsonToken.STRING) throw new IllegalArgumentException();
                    String value = reader.nextString();
                    if (value.length() > 256) throw new IllegalArgumentException();
                    result.put(field, value);
                } else {
                    if (reader.peek() != JsonToken.NUMBER) throw new IllegalArgumentException();
                    String number = reader.nextString();
                    if (!number.matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException();
                    result.put(field, Long.parseLong(number));
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || !result.keySet().equals(fields)) throw new IllegalArgumentException();
        } catch (IOException | NumberFormatException failure) { throw new IllegalArgumentException("Invalid market request.", failure); }
        return result;
    }

    private static int integer(Map<String, Object> values, String key) { return Math.toIntExact(number(values, key)); }
    private static long number(Map<String, Object> values, String key) { return (long) values.get(key); }
    private static long ticks(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static String dimension(Level level) { return level.dimension().location().toString(); }

    private static void requirePlayer(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()
                || !player.isAlive() || player.isSpectator()) throw failure("session_expired");
    }

    private static void send(ServerPlayer player, String message) {
        Map<String, Object> view = snapshot(player);
        view.put("message", message);
        MarketNetwork.send(player, JSON.toJson(view));
    }

    private static void close(ServerPlayer player, String message) {
        if (player == null) return;
        SESSIONS.remove(player.getUUID());
        try { MarketNetwork.send(player, JSON.toJson(Map.of("open", false, "message", message))); }
        catch (RuntimeException ignored) { /* Player may have disconnected. */ }
        if (!message.isEmpty()) player.sendSystemMessage(Component.translatable(message));
    }

    private static String error(Throwable failure) {
        String message = failure.getMessage();
        if (message != null && message.startsWith(PREFIX) && message.matches("gui\\.realmmarkets\\.[a-z_]+")) return message;
        if (failure instanceof SecurityException) return PREFIX + "owner_required";
        if (failure instanceof IllegalArgumentException || failure instanceof ArithmeticException) return PREFIX + "invalid_request";
        return PREFIX + "operation_failed";
    }

    private static IllegalStateException failure(String key) { return new IllegalStateException(PREFIX + key); }
    public static void logout(UUID player) { SESSIONS.remove(player); }
    public static void stop(MinecraftServer server) {
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().server() == server);
        MarketsApi.stop(server);
    }
}
