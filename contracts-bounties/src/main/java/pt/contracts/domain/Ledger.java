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
    public static final int MAX_PENDING_MISSIONS = 1_000_000;
    public static final UUID KINGDOM_ACCOUNT = new UUID(0, 1);
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public enum Currency { EMERALD, CHRONICLES }
    public enum Status { OPEN, COMPLETED, CANCELLED }
    public enum Funding { PLAYER, KINGDOM }

    public record Contract(UUID id, UUID author, String authorName, String title, String item,
                           int quantity, long reward, Currency currency, Status status, UUID worker,
                           Funding funding, UUID issuer, int taxBasisPoints, int reputationReward,
                           int dailyReputationLimit) {
        /** Legacy contracts keep their original payment and never gain retroactive rewards. */
        public Contract(UUID id, UUID author, String authorName, String title, String item,
                        int quantity, long reward, Currency currency, Status status, UUID worker) {
            this(id, author, authorName, title, item, quantity, reward, currency, status, worker,
                    Funding.PLAYER, author, 0, 0, 0);
        }

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
            require(funding, "Funding");
            require(issuer, "Issuer");
            if (taxBasisPoints < 0 || taxBasisPoints > 10_000) {
                throw new IllegalArgumentException("Tax must be between 0 and 10000 basis points");
            }
            if (reputationReward < 0 || reputationReward > 100
                    || dailyReputationLimit < 0 || dailyReputationLimit > 100) {
                throw new IllegalArgumentException("Reputation reward and daily limit must be between 0 and 100");
            }
            if (funding == Funding.KINGDOM) {
                if (!KINGDOM_ACCOUNT.equals(author) || KINGDOM_ACCOUNT.equals(issuer)) {
                    throw new IllegalArgumentException("Public contracts require the kingdom account and a player issuer");
                }
            } else if (!author.equals(issuer) || reputationReward != 0 || dailyReputationLimit != 0) {
                throw new IllegalArgumentException("Private contracts require their author as issuer and cannot award progress");
            }
            if (status == Status.COMPLETED) {
                require(worker, "Completed contract worker");
                if (author.equals(worker)) {
                    throw new IllegalArgumentException("Authors cannot complete their own contracts");
                }
            } else if (worker != null) {
                throw new IllegalArgumentException("Only completed contracts can have a worker");
            }
        }

        public long tax() { return reward * taxBasisPoints / 10_000; }
        public long netReward() { return reward - tax(); }
    }

    public record Progress(int reputation, int missions) {
        public Progress {
            if (reputation < 0 || reputation > 100 || missions < 0 || missions > MAX_PENDING_MISSIONS) {
                throw new IllegalArgumentException("Pending progress is outside the supported limits");
            }
        }
    }

    public record DailyProgress(long day, int reputation) {
        public DailyProgress {
            if (reputation < 0 || reputation > 100) {
                throw new IllegalArgumentException("Daily reputation must be between 0 and 100");
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
    private final Map<UUID, Progress> progress = new HashMap<>();
    private final Map<UUID, DailyProgress> daily = new HashMap<>();

    public Ledger() { }

    /** Restores a complete saved snapshot. Invalid or ambiguous state is rejected. */
    public Ledger(List<Contract> contracts, Map<UUID, Map<Currency, Long>> credits,
                  Map<UUID, List<Delivery>> deliveries) {
        this(contracts, credits, deliveries, Map.of(), Map.of());
    }

    public Ledger(List<Contract> contracts, Map<UUID, Map<Currency, Long>> credits,
                  Map<UUID, List<Delivery>> deliveries, Map<UUID, Progress> progress,
                  Map<UUID, DailyProgress> daily) {
        require(contracts, "Contracts");
        require(credits, "Credits");
        require(deliveries, "Deliveries");
        require(progress, "Progress");
        require(daily, "Daily progress");
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
        for (var entry : progress.entrySet()) {
            require(entry.getKey(), "Progress recipient");
            require(entry.getValue(), "Pending progress");
            if (entry.getValue().reputation() != 0 || entry.getValue().missions() != 0) {
                this.progress.put(entry.getKey(), entry.getValue());
            }
        }
        for (var entry : daily.entrySet()) {
            require(entry.getKey(), "Daily progress recipient");
            require(entry.getValue(), "Daily progress");
            this.daily.put(entry.getKey(), entry.getValue());
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
        return publish(id, author, authorName, title, item, quantity, reward, currency,
                Funding.PLAYER, author, 0, 0, 0);
    }

    public synchronized Contract publish(UUID id, UUID author, String authorName, String title,
                                         String item, int quantity, long reward, Currency currency,
                                         Funding funding, UUID issuer, int taxBasisPoints,
                                         int reputationReward, int dailyReputationLimit) {
        Contract contract = new Contract(id, author, authorName, title, item, quantity, reward,
                currency, Status.OPEN, null, funding, issuer, taxBasisPoints, reputationReward, dailyReputationLimit);
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
        complete(id, worker, Math.floorDiv(System.currentTimeMillis(), 86_400_000L));
    }

    public synchronized void complete(UUID id, UUID worker, long epochDay) {
        require(worker, "Worker");
        Contract contract = openContract(id);
        if (contract.author().equals(worker)) {
            throw new IllegalArgumentException("Authors cannot complete their own contracts");
        }
        long newWorkerCredit = checkedCredit(worker, contract.currency(), contract.netReward());
        long newKingdomCredit = KINGDOM_ACCOUNT.equals(worker) ? newWorkerCredit
                : credits(KINGDOM_ACCOUNT, contract.currency());
        try {
            newKingdomCredit = Math.addExact(newKingdomCredit, contract.tax());
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Kingdom credit would overflow", exception);
        }
        int newQuantity;
        try {
            newQuantity = Math.addExact(deliveryQuantity(contract.author(), contract.item()), contract.quantity());
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("The author's delivery storage would overflow", exception);
        }
        Contract completed = new Contract(contract.id(), contract.author(), contract.authorName(),
                contract.title(), contract.item(), contract.quantity(), contract.reward(),
                contract.currency(), Status.COMPLETED, worker, contract.funding(), contract.issuer(),
                contract.taxBasisPoints(), contract.reputationReward(), contract.dailyReputationLimit());
        Progress newProgress = null;
        DailyProgress newDaily = null;
        if (contract.funding() == Funding.KINGDOM && !worker.equals(contract.issuer())) {
            DailyProgress previous = daily.get(worker);
            long day = previous == null ? epochDay : Math.max(epochDay, previous.day());
            int earned = previous == null || epochDay > previous.day() ? 0 : previous.reputation();
            int award = Math.min(contract.reputationReward(), Math.max(0, contract.dailyReputationLimit() - earned));
            Progress pending = pendingProgress(worker);
            newProgress = new Progress(Math.min(100, pending.reputation() + award),
                    Math.min(MAX_PENDING_MISSIONS, pending.missions() + 1));
            newDaily = new DailyProgress(day, earned + award);
        }
        // Every overflow and state transition is checked before the first mutation.
        setCredit(worker, contract.currency(), KINGDOM_ACCOUNT.equals(worker) ? newKingdomCredit : newWorkerCredit);
        if (!KINGDOM_ACCOUNT.equals(worker)) setCredit(KINGDOM_ACCOUNT, contract.currency(), newKingdomCredit);
        deliveries.computeIfAbsent(contract.author(), key -> new LinkedHashMap<>())
                .put(contract.item(), newQuantity);
        contracts.put(id, completed);
        if (newProgress != null) {
            progress.put(worker, newProgress);
            daily.put(worker, newDaily);
        }
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
                contract.currency(), Status.CANCELLED, null, contract.funding(), contract.issuer(),
                contract.taxBasisPoints(), contract.reputationReward(), contract.dailyReputationLimit());
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

    /** Adds funded account credit; callers must already own the corresponding value. */
    public synchronized void addCredit(UUID recipient, Currency currency, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Credit amount cannot be negative");
        setCredit(recipient, currency, checkedCredit(recipient, currency, amount));
    }

    public synchronized void transferCredit(UUID from, UUID to, Currency currency, long amount) {
        require(from, "Credit sender");
        require(to, "Credit recipient");
        require(currency, "Currency");
        if (amount < 0) throw new IllegalArgumentException("Transfer amount cannot be negative");
        long available = credits(from, currency);
        if (amount > available) throw new IllegalStateException("Not enough claimable credit");
        if (from.equals(to) || amount == 0) return;
        long received = checkedCredit(to, currency, amount);
        setCredit(from, currency, available - amount);
        setCredit(to, currency, received);
    }

    public synchronized Progress pendingProgress(UUID recipient) {
        require(recipient, "Progress recipient");
        return progress.getOrDefault(recipient, new Progress(0, 0));
    }

    /** Compare-and-clear prevents an old synchronization request consuming newer awards. */
    public synchronized void takeProgress(UUID recipient, Progress exact) {
        require(exact, "Exact pending progress");
        if (!pendingProgress(recipient).equals(exact)) {
            throw new IllegalStateException("Pending progress changed before it was claimed");
        }
        progress.remove(recipient);
    }

    public synchronized Map<UUID, Progress> progressSnapshot() { return Map.copyOf(progress); }
    public synchronized Map<UUID, DailyProgress> dailySnapshot() { return Map.copyOf(daily); }

    public synchronized Ledger copy() {
        return new Ledger(list(), creditsSnapshot(), deliveriesSnapshot(), progressSnapshot(), dailySnapshot());
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
        if (amount == 0) {
            Map<Currency, Long> balances = credits.get(recipient);
            if (balances != null) {
                balances.remove(currency);
                if (balances.isEmpty()) credits.remove(recipient);
            }
        } else {
            credits.computeIfAbsent(recipient, key -> new EnumMap<>(Currency.class)).put(currency, amount);
        }
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
