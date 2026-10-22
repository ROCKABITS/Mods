package pt.crownledger.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Independent public funds, reserved payroll, and one frozen period awaiting full funding. */
public final class Treasury {
    public static final int MAX_HISTORY = 256;
    public enum Currency { EMERALD, CHRONICLES }
    public enum Kind { DEPOSIT, CONTRACT_FUNDING, CONTRACT_RETURN, TAX_COLLECTED, PAYROLL_ALLOCATED,
        SALARY_CLAIM, PAYROLL_CANCELLED, POLICY_CHANGED, MANAGER_CHANGED, MARKET_TAX, JUSTICE_INCOME, CASINO_TAX, VILLAGER_TAX, VILLAGER_POLICY, ECONOMY_TAX, ECONOMY_POLICY,
        VOTE_RESERVED, VOTE_RETURNED, VOTE_APPROVED, VOTE_REJECTED }

    public record Activity(String name, int minutes) {
        public Activity {
            name = text(name, 64);
            if (minutes < 1) throw new IllegalArgumentException("Activity minutes must be positive");
        }
    }

    public record History(long time, Kind kind, UUID actor, String details, Currency currency, long amount) {
        public History {
            Treasury.time(time);
            Objects.requireNonNull(kind, "History kind");
            Objects.requireNonNull(currency, "History currency");
            details = text(details, 256);
            if (amount < 0) throw new IllegalArgumentException("History amount cannot be negative");
        }
    }

    public record PendingBatch(UUID id, long closedAt, Currency currency, long salary, long fixedTax,
                               Map<UUID, String> eligible) {
        public PendingBatch {
            Objects.requireNonNull(id, "Batch ID");
            time(closedAt);
            Objects.requireNonNull(currency, "Batch currency");
            if (salary < 0 || fixedTax < 0) throw new IllegalArgumentException("Batch values cannot be negative");
            Objects.requireNonNull(eligible, "Eligible citizens");
            Map<UUID, String> names = new LinkedHashMap<>();
            eligible.forEach((uuid, name) -> names.put(Objects.requireNonNull(uuid, "Citizen UUID"), text(name, 64)));
            eligible = Map.copyOf(names);
            multiply(Math.max(0, salary - fixedTax), eligible.size());
        }

        public long netSalary() { return Math.max(0, salary - fixedTax); }
        public long walletTax() { return Math.max(0, fixedTax - salary); }
        public long totalNet() { return multiply(netSalary(), eligible.size()); }
    }

    public record Snapshot(Policy policy, Map<Currency, Long> balances,
                           Map<UUID, Map<Currency, Long>> claims,
                           Map<UUID, Map<Currency, Long>> taxDue,
                           Map<UUID, Activity> activity, List<History> history,
                           PendingBatch pending, long periodStartedAt, long nextCycleAt,
                           UUID standaloneManager) { }

    private Policy policy = Policy.DEFAULT;
    private final EnumMap<Currency, Long> balances = new EnumMap<>(Currency.class);
    private final Map<UUID, EnumMap<Currency, Long>> claims = new HashMap<>();
    private final Map<UUID, EnumMap<Currency, Long>> taxDue = new HashMap<>();
    private final Map<UUID, Activity> activity = new LinkedHashMap<>();
    private final List<History> history = new ArrayList<>();
    private PendingBatch pending;
    private long periodStartedAt = -1;
    private long nextCycleAt = -1;
    private UUID standaloneManager;

    public Treasury() { }

    /** Validates and copies every persisted collection; malformed data never becomes an empty treasury. */
    public Treasury(Snapshot saved) {
        Objects.requireNonNull(saved, "Treasury snapshot");
        policy = Objects.requireNonNull(saved.policy(), "Policy");
        Objects.requireNonNull(saved.balances(), "Balances").forEach((currency, amount) -> {
            Objects.requireNonNull(currency, "Balance currency");
            if (Objects.requireNonNull(amount, "Balance") < 0) throw new IllegalArgumentException("Negative balance");
            if (amount != 0) balances.put(currency, amount);
        });
        restoreAmounts(claims, saved.claims());
        restoreAmounts(taxDue, saved.taxDue());
        Objects.requireNonNull(saved.activity(), "Activity").forEach((id, record) ->
                activity.put(Objects.requireNonNull(id, "Active citizen"), Objects.requireNonNull(record, "Activity record")));
        if (Objects.requireNonNull(saved.history(), "History").size() > MAX_HISTORY)
            throw new IllegalArgumentException("Too much saved history");
        saved.history().forEach(entry -> history.add(Objects.requireNonNull(entry, "History record")));
        periodStartedAt = saved.periodStartedAt();
        nextCycleAt = saved.nextCycleAt();
        if (!((periodStartedAt == -1 && nextCycleAt == -1)
                || (periodStartedAt >= 0 && nextCycleAt > periodStartedAt)))
            throw new IllegalArgumentException("Invalid payroll period");
        pending = saved.pending();
        if (pending != null && (periodStartedAt < 0 || pending.closedAt() < nextCycleAt || !activity.isEmpty()))
            throw new IllegalArgumentException("Invalid frozen payroll batch");
        if (periodStartedAt < 0 && !activity.isEmpty()) throw new IllegalArgumentException("Activity without a period");
        standaloneManager = saved.standaloneManager();
    }

    public synchronized Policy policy() { return policy; }
    public synchronized UUID standaloneManager() { return standaloneManager; }
    public synchronized long periodStartedAt() { return periodStartedAt; }
    public synchronized long nextCycleAt() { return nextCycleAt; }
    public synchronized PendingBatch pendingBatch() { return pending; }
    public synchronized long balance(Currency currency) { return balances.getOrDefault(Objects.requireNonNull(currency), 0L); }
    public synchronized Map<Currency, Long> balances() {
        return Map.of(Currency.EMERALD, balance(Currency.EMERALD), Currency.CHRONICLES, balance(Currency.CHRONICLES));
    }
    public synchronized long claims(UUID player, Currency currency) { return amount(claims, player, currency); }
    public synchronized long taxDue(UUID player, Currency currency) { return amount(taxDue, player, currency); }
    public synchronized Map<UUID, Map<Currency, Long>> claimsSnapshot() { return snapshotAmounts(claims); }
    public synchronized Map<UUID, Map<Currency, Long>> taxDueSnapshot() { return snapshotAmounts(taxDue); }
    public synchronized Map<UUID, Activity> activitySnapshot() { return Map.copyOf(activity); }
    /** Newest first. */
    public synchronized List<History> history() { return List.copyOf(history); }

    public synchronized Snapshot snapshot() {
        return new Snapshot(policy, balances(), claimsSnapshot(), taxDueSnapshot(), activitySnapshot(), history(),
                pending, periodStartedAt, nextCycleAt, standaloneManager);
    }
    public synchronized Treasury copy() { return new Treasury(snapshot()); }

    public synchronized void recordGovernance(Kind kind, UUID actor, String details, long now) {
        if(kind!=Kind.VOTE_APPROVED && kind!=Kind.VOTE_REJECTED)throw new IllegalArgumentException("Invalid governance audit");
        append(new History(now,kind,actor,details,policy.currency(),0));
    }

    public synchronized void setPolicy(Policy next, UUID actor, long now) {
        Objects.requireNonNull(next, "Policy");
        History entry = new History(now, Kind.POLICY_CHANGED, actor, "Treasury policy updated", next.currency(), 0);
        policy = next;
        append(entry);
    }

    public synchronized void setStandaloneManager(UUID manager, UUID actor, long now) {
        History entry = new History(now, Kind.MANAGER_CHANGED, actor,
                manager == null ? "Standalone manager cleared" : "Standalone manager: " + manager, policy.currency(), 0);
        standaloneManager = manager;
        append(entry);
    }

    public synchronized void addFunds(Currency currency, long amount, Kind kind, UUID actor, String details, long now) {
        if (amount < 0) throw new IllegalArgumentException("Deposit cannot be negative");
        long next = add(balance(currency), amount);
        History entry = new History(now, kind, actor, details, currency, amount);
        if (amount == 0) return;
        setBalance(currency, next);
        append(entry);
    }

    /** Zero-value fiscal approval: audited without creating or spending treasury money. */
    public synchronized void recordEconomyPolicy(UUID actor,String details,long approvedAt){
        if(actor==null||details==null||details.length()>256||approvedAt<0)throw new IllegalArgumentException("Invalid economic approval");append(new History(approvedAt,Kind.ECONOMY_POLICY,actor,details,Currency.CHRONICLES,0));
    }
    public synchronized void recordVillagerPolicy(UUID actor,int basisPoints,long approvedAt){
        Objects.requireNonNull(actor);if(basisPoints<0||basisPoints>10000)throw new IllegalArgumentException("Invalid villager tax");
        append(new History(approvedAt,Kind.VILLAGER_POLICY,actor,java.math.BigDecimal.valueOf(basisPoints,2).stripTrailingZeros().toPlainString()+"%",Currency.CHRONICLES,0));
    }

    public synchronized void removeFunds(Currency currency, long amount, Kind kind, UUID actor, String details, long now) {
        if (amount < 0) throw new IllegalArgumentException("Expense cannot be negative");
        if (amount > balance(currency)) throw new IllegalStateException("Insufficient treasury funds");
        History entry = new History(now, kind, actor, details, currency, amount);
        if (amount == 0) return;
        setBalance(currency, balance(currency) - amount);
        append(entry);
    }

    /** The caller counts only online, non-spectator minutes. Pending batches collect no new activity. */
    public synchronized void recordActivity(UUID player, String name, int minutes, long now) {
        Objects.requireNonNull(player, "Player");
        name = text(name, 64);
        if (minutes < 1) throw new IllegalArgumentException("Activity increment must be positive");
        time(now);
        cycle(now);
        if (pending != null || now < periodStartedAt) return;
        Activity previous = activity.get(player);
        int total = (int) Math.min(Integer.MAX_VALUE, minutes + (long) (previous == null ? 0 : previous.minutes()));
        activity.put(player, new Activity(name, total));
    }

    /** Starts one real-time window; a delayed server tick never generates a backlog of periods. */
    public synchronized boolean cycle(long now) {
        time(now);
        if (periodStartedAt < 0) { start(now); return true; }
        if (pending != null) return settlePending(now);
        if (now < nextCycleAt) return false;
        Map<UUID, String> eligible = new LinkedHashMap<>();
        activity.forEach((id, record) -> { if (record.minutes() >= policy.activeMinutes()) eligible.put(id, record.name()); });
        PendingBatch batch = new PendingBatch(UUID.randomUUID(), now, policy.currency(), policy.salary(), policy.effectiveFixedTax(), eligible);
        // Validate before freezing, including the next period's deadline.
        deadline(now);
        pending = batch;
        activity.clear();
        settlePending(now);
        return true;
    }

    /** Allocates every frozen citizen together, or retains the batch unchanged. */
    public synchronized boolean settlePending(long now) {
        time(now);
        if (pending == null || now < pending.closedAt() || balance(pending.currency()) < pending.totalNet()) return false;
        long nextDeadline = deadline(now);
        Map<UUID, Long> nextClaims = new HashMap<>();
        Map<UUID, Long> nextTaxes = new HashMap<>();
        for (UUID player : pending.eligible().keySet()) {
            nextClaims.put(player, add(claims(player, pending.currency()), pending.netSalary()));
            nextTaxes.put(player, add(taxDue(player, pending.currency()), pending.walletTax()));
        }
        History entry = new History(now, Kind.PAYROLL_ALLOCATED, null,
                "Payroll " + pending.id() + ": " + pending.eligible().size() + " eligible; gross "
                        + pending.salary() + "; fixed tax " + pending.fixedTax(), pending.currency(), pending.totalNet());
        Currency currency = pending.currency();
        setBalance(currency, balance(currency) - pending.totalNet());
        nextClaims.forEach((id, amount) -> setAmount(claims, id, currency, amount));
        nextTaxes.forEach((id, amount) -> setAmount(taxDue, id, currency, amount));
        pending = null;
        activity.clear();
        periodStartedAt = now;
        nextCycleAt = nextDeadline;
        append(entry);
        return true;
    }

    public synchronized void cancelPending(UUID actor, long now) {
        time(now);
        if (pending == null) throw new IllegalStateException("No pending payroll batch");
        if (now < pending.closedAt()) throw new IllegalArgumentException("Cannot cancel a future batch");
        long nextDeadline = deadline(now);
        History entry = new History(now, Kind.PAYROLL_CANCELLED, actor,
                "Cancelled payroll " + pending.id() + ": " + pending.eligible().size() + " eligible", pending.currency(), pending.totalNet());
        pending = null;
        activity.clear();
        periodStartedAt = now;
        nextCycleAt = nextDeadline;
        append(entry);
    }

    /** Removes already-reserved money; the service credits the online wallet only after preflight. */
    public synchronized void takeSalary(UUID player, Currency currency, long amount, String name, long now) {
        if (amount < 1 || amount > claims(player, currency)) throw new IllegalStateException("Insufficient salary claim");
        History entry = new History(now, Kind.SALARY_CLAIM, player, "Salary: " + text(name, 64), currency, amount);
        setAmount(claims, player, currency, claims(player, currency) - amount);
        append(entry);
    }

    /** Uncollectable tax is waived now; it never becomes a personal debt. */
    public synchronized void settleTax(UUID player, Currency currency, long paid, String name, long now) {
        long due = taxDue(player, currency);
        if (paid < 0 || paid > due) throw new IllegalArgumentException("Tax payment exceeds the pending assessment");
        long next = add(balance(currency), paid);
        History entry = new History(now, Kind.TAX_COLLECTED, player,
                "Tax: " + text(name, 64) + "; waived " + (due - paid), currency, paid);
        setAmount(taxDue, player, currency, 0);
        setBalance(currency, next);
        if (due != 0) append(entry);
    }

    private void start(long now) {
        long next = deadline(now);
        periodStartedAt = now;
        nextCycleAt = next;
    }
    private long deadline(long now) { return add(now, policy.periodMillis()); }
    private void append(History entry) { history.addFirst(entry); if (history.size() > MAX_HISTORY) history.removeLast(); }
    private void setBalance(Currency currency, long amount) { if (amount == 0) balances.remove(currency); else balances.put(currency, amount); }

    private static long amount(Map<UUID, ? extends Map<Currency, Long>> source, UUID player, Currency currency) {
        Objects.requireNonNull(player, "Player"); Objects.requireNonNull(currency, "Currency");
        Map<Currency, Long> amounts = source.get(player);
        return amounts == null ? 0 : amounts.getOrDefault(currency, 0L);
    }
    private static void setAmount(Map<UUID, EnumMap<Currency, Long>> source, UUID player, Currency currency, long amount) {
        if (amount == 0) {
            Map<Currency, Long> amounts = source.get(player);
            if (amounts != null) { amounts.remove(currency); if (amounts.isEmpty()) source.remove(player); }
        } else source.computeIfAbsent(player, id -> new EnumMap<>(Currency.class)).put(currency, amount);
    }
    private static Map<UUID, Map<Currency, Long>> snapshotAmounts(Map<UUID, EnumMap<Currency, Long>> source) {
        Map<UUID, Map<Currency, Long>> result = new HashMap<>();
        source.forEach((id, amounts) -> result.put(id, Map.copyOf(amounts)));
        return Map.copyOf(result);
    }
    private static void restoreAmounts(Map<UUID, EnumMap<Currency, Long>> target, Map<UUID, Map<Currency, Long>> source) {
        Objects.requireNonNull(source, "Saved amounts").forEach((id, amounts) -> {
            Objects.requireNonNull(id, "Recipient");
            Objects.requireNonNull(amounts, "Currency amounts").forEach((currency, amount) -> {
                Objects.requireNonNull(currency, "Currency");
                if (Objects.requireNonNull(amount, "Amount") < 1) throw new IllegalArgumentException("Saved claims and tax assessments must be positive");
                setAmount(target, id, currency, amount);
            });
        });
    }
    private static long add(long a, long b) {
        try { return Math.addExact(a, b); }
        catch (ArithmeticException ex) { throw new IllegalStateException("Treasury amount or deadline would overflow", ex); }
    }
    private static long multiply(long a, int b) {
        try { return Math.multiplyExact(a, b); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("The complete payroll amount exceeds treasury capacity", ex); }
    }
    private static void time(long now) { if (now < 0) throw new IllegalArgumentException("Invalid treasury time"); }
    private static String text(String value, int limit) {
        Objects.requireNonNull(value, "Text");
        if (value.isBlank() || value.length() > limit || value.chars().anyMatch(c -> Character.isISOControl(c) || c == '\u00a7'))
            throw new IllegalArgumentException("Invalid treasury text");
        return value;
    }
}
