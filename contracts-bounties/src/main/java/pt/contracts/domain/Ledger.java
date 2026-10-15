package pt.contracts.domain;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Server-owned contract escrow and unclaimed payouts; independent of Minecraft APIs. */
public final class Ledger {
    public static final int MAX_QUANTITY = 4_096;
    public static final long MAX_REWARD = 1_000_000_000L;
    public static final int MAX_OPEN_PER_AUTHOR = 16;
    public static final int MAX_CONTRACTS = 2_048;
    public static final int MAX_ITEM_ID_LENGTH = 128;
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public enum Currency { EMERALD, CHRONICLES }
    public enum Status { OPEN, COMPLETED, CANCELLED }

    public record Contract(UUID id, UUID author, String authorName, String title, String item,
                           int quantity, long reward, Currency currency, Status status, UUID worker) {
        public Contract {
            require(id, "Contract ID");
            require(author, "Author");
            authorName = text(authorName, "Author name", 64);
            title = text(title, "Title", 64);
            item = itemId(item);
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                throw new IllegalArgumentException("Quantity must be between 1 and " + MAX_QUANTITY);
            }
            if (reward < 1 || reward > MAX_REWARD) {
                throw new IllegalArgumentException("Reward must be between 1 and " + MAX_REWARD);
            }
            require(currency, "Currency");
            require(status, "Status");
            if (status == Status.COMPLETED) {
                require(worker, "Completed contract worker");
                if (author.equals(worker)) {
                    throw new IllegalArgumentException("Authors cannot complete their own contracts");
                }
            } else if (worker != null) {
                throw new IllegalArgumentException("Only completed contracts can have a worker");
            }
        }
    }

    public record Delivery(String item, int quantity) {
        public Delivery {
            item = itemId(item);
            if (quantity < 1) {
                throw new IllegalArgumentException("Delivery quantity must be positive");
            }
        }
    }

    private final Map<UUID, Contract> contracts = new LinkedHashMap<>();
    private final Map<UUID, EnumMap<Currency, Long>> credits = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> deliveries = new HashMap<>();

    public Ledger() { }

    /** Restores a complete saved snapshot. Invalid or ambiguous state is rejected. */
    public Ledger(List<Contract> contracts, Map<UUID, Map<Currency, Long>> credits,
                  Map<UUID, List<Delivery>> deliveries) {
        require(contracts, "Contracts");
        require(credits, "Credits");
        require(deliveries, "Deliveries");
        if (contracts.size() > MAX_CONTRACTS) {
            throw new IllegalArgumentException("Too many saved contracts");
        }
        Map<UUID, Integer> openCounts = new HashMap<>();
        for (Contract contract : contracts) {
            require(contract, "Saved contract");
            if (this.contracts.putIfAbsent(contract.id(), contract) != null) {
                throw new IllegalArgumentException("Duplicate saved contract ID: " + contract.id());
            }
            if (contract.status() == Status.OPEN
                    && openCounts.merge(contract.author(), 1, Integer::sum) > MAX_OPEN_PER_AUTHOR) {
                throw new IllegalArgumentException("Too many open saved contracts for one author");
            }
        }
        for (var entry : credits.entrySet()) {
            require(entry.getKey(), "Credit recipient");
            require(entry.getValue(), "Currency balances");
            EnumMap<Currency, Long> balances = new EnumMap<>(Currency.class);
            for (var balance : entry.getValue().entrySet()) {
                require(balance.getKey(), "Credit currency");
                require(balance.getValue(), "Credit amount");
                if (balance.getValue() < 1) {
                    throw new IllegalArgumentException("Saved credit amounts must be positive");
                }
                balances.put(balance.getKey(), balance.getValue());
            }
            if (!balances.isEmpty()) {
                this.credits.put(entry.getKey(), balances);
            }
        }
        for (var entry : deliveries.entrySet()) {
            require(entry.getKey(), "Delivery recipient");
            require(entry.getValue(), "Saved deliveries");
            Map<String, Integer> items = new LinkedHashMap<>();
            for (Delivery delivery : entry.getValue()) {
                require(delivery, "Saved delivery");
                if (items.putIfAbsent(delivery.item(), delivery.quantity()) != null) {
                    throw new IllegalArgumentException("Duplicate saved delivery item: " + delivery.item());
                }
            }
            if (!items.isEmpty()) {
                this.deliveries.put(entry.getKey(), items);
            }
        }
    }

    /** The caller must reserve the full payment before publishing. */
    public synchronized Contract publish(UUID id, UUID author, String authorName, String title,
                                         String item, int quantity, long reward, Currency currency) {
        Contract contract = new Contract(id, author, authorName, title, item, quantity, reward,
                currency, Status.OPEN, null);
        if (contracts.containsKey(id)) {
            throw new IllegalArgumentException("Contract ID already exists");
        }
        long open = contracts.values().stream()
                .filter(c -> c.author().equals(author) && c.status() == Status.OPEN).count();
        if (open >= MAX_OPEN_PER_AUTHOR) {
            throw new IllegalStateException("You already have " + MAX_OPEN_PER_AUTHOR + " open contracts");
        }
        UUID evicted = null;
        if (contracts.size() >= MAX_CONTRACTS) {
            evicted = contracts.values().stream().filter(c -> c.status() != Status.OPEN)
                    .map(Contract::id).findFirst()
                    .orElseThrow(() -> new IllegalStateException("The contract archive is full of open contracts"));
        }
        if (evicted != null) {
            contracts.remove(evicted);
        }
        contracts.put(id, contract);
        return contract;
    }

    /** The caller must supply all required materials before completing. */
    public synchronized void complete(UUID id, UUID worker) {
        require(worker, "Worker");
        Contract contract = openContract(id);
        if (contract.author().equals(worker)) {
            throw new IllegalArgumentException("Authors cannot complete their own contracts");
        }
        long newCredit = checkedCredit(worker, contract.currency(), contract.reward());
        int newQuantity;
        try {
            newQuantity = Math.addExact(deliveryQuantity(contract.author(), contract.item()), contract.quantity());
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("The author's delivery storage would overflow", exception);
        }
        Contract completed = new Contract(contract.id(), contract.author(), contract.authorName(),
                contract.title(), contract.item(), contract.quantity(), contract.reward(),
                contract.currency(), Status.COMPLETED, worker);
        setCredit(worker, contract.currency(), newCredit);
        deliveries.computeIfAbsent(contract.author(), key -> new LinkedHashMap<>())
                .put(contract.item(), newQuantity);
        contracts.put(id, completed);
    }

    public synchronized void cancel(UUID id, UUID author) {
        require(author, "Author");
        Contract contract = openContract(id);
        if (!contract.author().equals(author)) {
            throw new IllegalArgumentException("Only the author can cancel this contract");
        }
        long newCredit = checkedCredit(author, contract.currency(), contract.reward());
        Contract cancelled = new Contract(contract.id(), contract.author(), contract.authorName(),
                contract.title(), contract.item(), contract.quantity(), contract.reward(),
                contract.currency(), Status.CANCELLED, null);
        setCredit(author, contract.currency(), newCredit);
        contracts.put(id, cancelled);
    }

    public synchronized List<Contract> list() {
        return List.copyOf(contracts.values());
    }

    public synchronized long credits(UUID recipient, Currency currency) {
        require(recipient, "Credit recipient");
        require(currency, "Currency");
        Map<Currency, Long> balances = credits.get(recipient);
        return balances == null ? 0 : balances.getOrDefault(currency, 0L);
    }

    public synchronized void takeCredit(UUID recipient, Currency currency, long amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Claim amount must be positive");
        }
        long available = credits(recipient, currency);
        if (amount > available) {
            throw new IllegalStateException("Not enough claimable credit");
        }
        Map<Currency, Long> balances = credits.get(recipient);
        long remaining = available - amount;
        if (remaining == 0) {
            balances.remove(currency);
            if (balances.isEmpty()) {
                credits.remove(recipient);
            }
        } else {
            balances.put(currency, remaining);
        }
    }

    public synchronized List<Delivery> deliveries(UUID recipient) {
        require(recipient, "Delivery recipient");
        Map<String, Integer> items = deliveries.get(recipient);
        if (items == null) {
            return List.of();
        }
        return items.entrySet().stream().map(e -> new Delivery(e.getKey(), e.getValue())).toList();
    }

    public synchronized void takeDelivery(UUID recipient, String item, int quantity) {
        require(recipient, "Delivery recipient");
        itemId(item);
        if (quantity < 1) {
            throw new IllegalArgumentException("Claim quantity must be positive");
        }
        int available = deliveryQuantity(recipient, item);
        if (quantity > available) {
            throw new IllegalStateException("Not enough claimable materials");
        }
        Map<String, Integer> items = deliveries.get(recipient);
        int remaining = available - quantity;
        if (remaining == 0) {
            items.remove(item);
            if (items.isEmpty()) {
                deliveries.remove(recipient);
            }
        } else {
            items.put(item, remaining);
        }
    }

    public synchronized Map<UUID, Map<Currency, Long>> creditsSnapshot() {
        Map<UUID, Map<Currency, Long>> snapshot = new HashMap<>();
        credits.forEach((recipient, balances) -> snapshot.put(recipient, Map.copyOf(balances)));
        return Map.copyOf(snapshot);
    }

    public synchronized Map<UUID, List<Delivery>> deliveriesSnapshot() {
        Map<UUID, List<Delivery>> snapshot = new HashMap<>();
        deliveries.forEach((recipient, items) -> snapshot.put(recipient,
                items.entrySet().stream().map(e -> new Delivery(e.getKey(), e.getValue())).toList()));
        return Map.copyOf(snapshot);
    }

    private Contract openContract(UUID id) {
        require(id, "Contract ID");
        Contract contract = contracts.get(id);
        if (contract == null) {
            throw new IllegalArgumentException("Contract does not exist");
        }
        if (contract.status() != Status.OPEN) {
            throw new IllegalStateException("Contract is no longer open");
        }
        return contract;
    }

    private long checkedCredit(UUID recipient, Currency currency, long amount) {
        try {
            return Math.addExact(credits(recipient, currency), amount);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Claimable credit would overflow", exception);
        }
    }

    private void setCredit(UUID recipient, Currency currency, long amount) {
        credits.computeIfAbsent(recipient, key -> new EnumMap<>(Currency.class)).put(currency, amount);
    }

    private int deliveryQuantity(UUID recipient, String item) {
        Map<String, Integer> items = deliveries.get(recipient);
        return items == null ? 0 : items.getOrDefault(item, 0);
    }

    private static String itemId(String item) {
        if (item == null || item.length() > MAX_ITEM_ID_LENGTH || !ITEM_ID.matcher(item).matches()) {
            throw new IllegalArgumentException("Item must be a namespaced ID of at most "
                    + MAX_ITEM_ID_LENGTH + " characters, such as minecraft:oak_log");
        }
        return item;
    }

    private static String text(String value, String label, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (value.chars().anyMatch(c -> Character.isISOControl(c) || c == '\u00a7')) {
            throw new IllegalArgumentException(label + " cannot contain control characters or formatting codes");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + " must contain 1 to " + maxLength + " characters");
        }
        return trimmed;
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
