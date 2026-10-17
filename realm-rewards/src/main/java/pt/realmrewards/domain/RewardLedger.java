package pt.realmrewards.domain;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** UUIDs and advancement IDs are authoritative; changing rules never clears completion history. */
public final class RewardLedger {
    public static final long MAX_BALANCE = 900_000_000_000_000_000L, MAX_REWARD = 100_000_000L;
    public static final int MAX_PLAYERS = 100_000, MAX_ENTRIES = 50_000;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    public enum Kind { TASK, GOAL, CHALLENGE, BACKGROUND }
    public record Account(Set<String> completed, long pending) {
        public Account {
            completed = Set.copyOf(completed);
            if (completed.size() > MAX_ENTRIES || pending < 0 || pending > MAX_BALANCE) throw new IllegalArgumentException("Invalid reward account");
            completed.forEach(RewardLedger::id);
        }
    }
    private final EnumMap<Kind, Long> defaults;
    private final Map<String, Long> overrides;
    private final Map<UUID, Account> accounts;
    public RewardLedger() {
        this(Map.of(Kind.TASK, 200L, Kind.GOAL, 500L, Kind.CHALLENGE, 1000L, Kind.BACKGROUND, 0L), Map.of(), Map.of());
    }
    public RewardLedger(Map<Kind, Long> defaults, Map<String, Long> overrides, Map<UUID, Account> accounts) {
        this.defaults = new EnumMap<>(Kind.class);
        if (defaults.size() != Kind.values().length) throw new IllegalArgumentException("Missing reward defaults");
        for (Kind kind : Kind.values()) this.defaults.put(kind, reward(Objects.requireNonNull(defaults.get(kind))));
        if (this.defaults.get(Kind.BACKGROUND) != 0 || overrides.size() > MAX_ENTRIES || accounts.size() > MAX_PLAYERS) throw new IllegalArgumentException("Reward registry limit");
        this.overrides = new HashMap<>();
        overrides.forEach((key, value) -> this.overrides.put(id(key), reward(value)));
        this.accounts = new HashMap<>();
        accounts.forEach((key, value) -> this.accounts.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
    }
    public RewardLedger copy() { return new RewardLedger(defaults, overrides, accounts); }
    public Map<Kind, Long> defaults() { return Map.copyOf(defaults); }
    public Map<String, Long> overrides() { return Map.copyOf(overrides); }
    public Map<UUID, Account> accounts() { return Map.copyOf(accounts); }
    public Account account(UUID player) { return accounts.getOrDefault(Objects.requireNonNull(player), new Account(Set.of(), 0)); }
    public long amount(String advancement, Kind kind) { return overrides.getOrDefault(id(advancement), defaults.get(Objects.requireNonNull(kind))); }
    public void configure(String advancement, long value) {
        id(advancement); reward(value);
        if (!overrides.containsKey(advancement) && overrides.size() >= MAX_ENTRIES) throw new IllegalStateException("Reward rule limit");
        overrides.put(advancement, value);
    }
    public void reset(String advancement) { overrides.remove(id(advancement)); }
    public void defaults(long task, long goal, long challenge) {
        reward(task); reward(goal); reward(challenge);
        defaults.put(Kind.TASK, task); defaults.put(Kind.GOAL, goal); defaults.put(Kind.CHALLENGE, challenge);
    }
    public boolean baseline(UUID player, Collection<String> alreadyCompleted) {
        Account before = account(player);
        Set<String> next = new HashSet<>(before.completed());
        alreadyCompleted.forEach(value -> next.add(id(value)));
        if (next.equals(before.completed())) return false;
        put(player, new Account(next, before.pending())); return true;
    }
    public boolean earn(UUID player, String advancement, Kind kind) {
        advancement = id(advancement);
        Account before = account(player);
        if (before.completed().contains(advancement)) return false;
        long value = amount(advancement, kind);
        if (value > MAX_BALANCE - before.pending()) throw new IllegalStateException("Pending rewards are full");
        Set<String> next = new HashSet<>(before.completed()); next.add(advancement);
        put(player, new Account(next, before.pending() + value)); return true;
    }
    public void paid(UUID player, long value) {
        Account before = account(player);
        if (value <= 0 || value > before.pending()) throw new IllegalArgumentException("Invalid pending payout");
        put(player, new Account(before.completed(), before.pending() - value));
    }
    private void put(UUID player, Account account) {
        if (!accounts.containsKey(player) && accounts.size() >= MAX_PLAYERS) throw new IllegalStateException("Reward player limit");
        accounts.put(Objects.requireNonNull(player), account);
    }
    public static String id(String value) {
        if (value == null || value.length() > 512 || !ID.matcher(value).matches()) throw new IllegalArgumentException("Invalid advancement identifier");
        return value;
    }
    public static long reward(long value) {
        if (value < 0 || value > MAX_REWARD) throw new IllegalArgumentException("Reward must be between 0 and 1000000 euros");
        return value;
    }
    public static long parse(String value) {
        if (value == null || !value.strip().matches("[0-9]{1,7}([.,][0-9]{1,2})?")) throw new IllegalArgumentException("Invalid euro amount");
        return reward(new BigDecimal(value.strip().replace(',', '.')).movePointRight(2).longValueExact());
    }
    public static String euros(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }
}
