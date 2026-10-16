package pt.realmmarkets.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns real stock and retained proceeds. Callers transact on a copy and commit it
 * only after their inventory and wallet preflight succeeds on the server thread.
 */
public final class MarketLedger {
    public static final int MAX_STALLS = 10_000;
    public static final int MAX_STOCK = Listing.MAX_STOCK;
    public static final long MAX_UNIT_PRICE = Listing.MAX_UNIT_PRICE;
    public static final long MAX_EARNINGS = Stall.MAX_EARNINGS;

    private record Location(String dimension, long position) {}

    private final int capacity;
    private final Map<UUID, Stall> byId = new LinkedHashMap<>();
    private final Map<Location, UUID> byLocation = new LinkedHashMap<>();

    public MarketLedger() { this(List.of(), MAX_STALLS); }
    public MarketLedger(Collection<Stall> stalls) { this(stalls, MAX_STALLS); }
    public MarketLedger(int capacity) { this(List.of(), capacity); }

    public MarketLedger(Collection<Stall> stalls, int capacity) {
        Objects.requireNonNull(stalls, "stalls");
        if (capacity < 1 || capacity > MAX_STALLS) throw new IllegalArgumentException("Invalid stall registry capacity.");
        if (stalls.size() > capacity) throw new IllegalArgumentException("Stall registry exceeds capacity.");
        this.capacity = capacity;
        for (Stall stall : stalls) {
            Objects.requireNonNull(stall, "stall");
            if (byId.putIfAbsent(stall.id(), stall) != null) throw new IllegalArgumentException("Duplicate stall ID.");
            if (byLocation.putIfAbsent(location(stall), stall.id()) != null) {
                throw new IllegalArgumentException("Duplicate stall location.");
            }
        }
    }

    public MarketLedger copy() { return new MarketLedger(stalls(), capacity); }
    public List<Stall> stalls() { return List.copyOf(byId.values()); }
    public Optional<Stall> find(UUID id) { return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id"))); }

    public Optional<Stall> at(String dimension, long position) {
        Listing.requireIdentifier(dimension, "dimension");
        UUID id = byLocation.get(new Location(dimension, position));
        return id == null ? Optional.empty() : find(id);
    }

    public Stall create(UUID owner, String dimension, long position, String title, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Listing.requireIdentifier(dimension, "dimension");
        Stall.requireTitle(title);
        Objects.requireNonNull(currency, "currency");
        if (byId.size() >= capacity) throw new IllegalStateException("The stall registry is full.");
        Location location = new Location(dimension, position);
        if (byLocation.containsKey(location)) throw new IllegalStateException("There is already a stall here.");
        UUID id;
        do { id = UUID.randomUUID(); } while (byId.containsKey(id));
        Stall stall = new Stall(id, owner, dimension, position, title, currency, 0, List.of());
        byId.put(id, stall);
        byLocation.put(location, id);
        return stall;
    }

    /** Repricing is permitted with stock; changing the item requires an empty offer. */
    public void configure(UUID id, UUID owner, int slot, String item, long unitPrice) {
        Stall stall = owned(id, owner);
        Listing.requireSlot(slot);
        Listing previous = stall.listing(slot).orElse(null);
        Listing configured = new Listing(slot, item, previous == null ? 0 : previous.stock(), unitPrice);
        if (previous != null && previous.stock() > 0 && !previous.item().equals(item)) {
            throw new IllegalStateException("Withdraw the stock before changing its item.");
        }
        replaceListing(stall, configured, stall.earnings());
    }

    public void stock(UUID id, UUID owner, int slot, int amount) {
        Stall stall = owned(id, owner);
        requireAmount(amount);
        Listing listing = configured(stall, slot);
        int next = Math.addExact(listing.stock(), amount);
        if (next > MAX_STOCK) throw new IllegalStateException("The offer has no room for that stock.");
        replaceListing(stall, new Listing(slot, listing.item(), next, listing.unitPrice()), stall.earnings());
    }

    public void withdraw(UUID id, UUID owner, int slot, int amount) {
        Stall stall = owned(id, owner);
        requireAmount(amount);
        Listing listing = configured(stall, slot);
        if (amount > listing.stock()) throw new IllegalStateException("There is not enough stock.");
        replaceListing(stall, new Listing(slot, listing.item(), listing.stock() - amount, listing.unitPrice()), stall.earnings());
    }

    /** Return the exact price. The caller must debit the buyer before committing its copy. */
    public long purchase(UUID id, UUID buyer, int slot, int quantity) {
        Stall stall = require(id);
        Objects.requireNonNull(buyer, "buyer");
        if (stall.owner().equals(buyer)) throw new SecurityException("An owner cannot buy from their own stall.");
        requireAmount(quantity);
        Listing listing = configured(stall, slot);
        if (quantity > listing.stock()) throw new IllegalStateException("There is not enough stock.");
        long total = Math.multiplyExact(listing.unitPrice(), (long) quantity);
        long earnings = Math.addExact(stall.earnings(), total);
        if (earnings > MAX_EARNINGS) throw new IllegalStateException("The owner must collect the retained earnings first.");
        replaceListing(stall, new Listing(slot, listing.item(), listing.stock() - quantity, listing.unitPrice()), earnings);
        return total;
    }

    public void claim(UUID id, UUID owner, long amount) {
        Stall stall = owned(id, owner);
        if (amount < 1 || amount > MAX_EARNINGS) throw new IllegalArgumentException("Invalid claim amount.");
        if (amount > stall.earnings()) throw new IllegalStateException("There are not enough retained earnings.");
        replace(stall, stall.title(), stall.earnings() - amount, stall.listings());
    }

    public void rename(UUID id, UUID owner, String title) {
        Stall stall = owned(id, owner);
        Stall.requireTitle(title);
        replace(stall, title, stall.earnings(), stall.listings());
    }

    public void remove(UUID id, UUID owner) {
        Stall stall = owned(id, owner);
        if (!stall.empty()) throw new IllegalStateException("Withdraw all stock and collect all earnings first.");
        byId.remove(stall.id());
        byLocation.remove(location(stall));
    }

    private Stall require(UUID id) {
        return find(id).orElseThrow(() -> new IllegalStateException("The stall no longer exists."));
    }

    private Stall owned(UUID id, UUID owner) {
        Stall stall = require(id);
        if (!stall.owner().equals(Objects.requireNonNull(owner, "owner"))) throw new SecurityException("Only the stall owner may do this.");
        return stall;
    }

    private static Listing configured(Stall stall, int slot) {
        return stall.listing(slot).orElseThrow(() -> new IllegalStateException("Configure this offer first."));
    }

    private static void requireAmount(int amount) {
        if (amount < 1 || amount > MAX_STOCK) throw new IllegalArgumentException("Invalid item quantity.");
    }

    private static Location location(Stall stall) { return new Location(stall.dimension(), stall.position()); }

    private void replaceListing(Stall stall, Listing listing, long earnings) {
        List<Listing> listings = new ArrayList<>(stall.listings());
        listings.removeIf(previous -> previous.slot() == listing.slot());
        listings.add(listing);
        replace(stall, stall.title(), earnings, listings);
    }

    private void replace(Stall previous, String title, long earnings, List<Listing> listings) {
        Stall next = new Stall(previous.id(), previous.owner(), previous.dimension(), previous.position(),
                title, previous.currency(), earnings, listings);
        byId.put(next.id(), next);
    }
}
