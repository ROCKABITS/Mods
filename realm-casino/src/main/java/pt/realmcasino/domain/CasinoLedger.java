package pt.realmcasino.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, server-owned bank ledger. A caller prepares a new value, performs
 * any required durable external wallet transfer, and only then commits it.
 * A bank includes reserved money; only {@link Machine#available()} is withdrawable.
 */
public final class CasinoLedger {
    public static final int MAX_MACHINES = 4096;
    public static final int MAX_ROUNDS = 4096;
    public static final int MAX_CREDITORS = 100_000;
    public static final int MAX_HISTORY = 200;
    public static final int MAX_NAME = 48;
    public enum Kind { SLOTS, ROULETTE, BLACKJACK }
    private record Location(String dimension, long position) {}

    public record Machine(UUID id, UUID owner, Kind kind, String dimension, long position,
                          String name, long minBet, long maxBet, long bank, long reserved, long version) {
        public Machine {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(owner, "owner"); Objects.requireNonNull(kind, "kind");
            if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                throw new DomainException("invalid");
            requireName(name);
            requireLimits(kind, minBet, maxBet);
            Money.checked(bank); Money.checked(reserved);
            if (reserved > bank || version < 0) throw new DomainException("invalid");
        }
        public long available() { return bank - reserved; }
    }
    public record Round(UUID id, UUID machineId, UUID player, long stake, long maxPayout, long startedAt) {
        public Round {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(machineId, "machineId"); Objects.requireNonNull(player, "player");
            Money.positive(stake); Money.positive(maxPayout);
            if (maxPayout < stake || startedAt < 0) throw new DomainException("invalid");
        }
    }
    public record History(UUID id, UUID machineId, UUID player, long stake, long payout, long settledAt) {
        public History {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(machineId, "machineId"); Objects.requireNonNull(player, "player");
            Money.positive(stake); Money.checked(payout);
            if (settledAt < 0) throw new DomainException("invalid");
        }
    }

    private final Map<UUID, Machine> machines;
    private final Map<UUID, Round> rounds;
    private final Map<UUID, Long> credits;
    private final List<History> history;
    private final Map<Location, UUID> locations;

    public CasinoLedger() { this(List.of(), List.of(), Map.of(), List.of()); }
    public CasinoLedger(Collection<Machine> machines, Collection<Round> rounds,
                        Map<UUID, Long> credits, Collection<History> history) {
        Objects.requireNonNull(machines, "machines"); Objects.requireNonNull(rounds, "rounds");
        Objects.requireNonNull(credits, "credits"); Objects.requireNonNull(history, "history");
        if (machines.size() > MAX_MACHINES || rounds.size() > MAX_ROUNDS || credits.size() > MAX_CREDITORS || history.size() > MAX_HISTORY)
            throw new DomainException("capacity");
        LinkedHashMap<UUID, Machine> machineMap = new LinkedHashMap<>();
        LinkedHashMap<Location, UUID> locationMap = new LinkedHashMap<>();
        for (Machine machine : machines) {
            Objects.requireNonNull(machine, "machine");
            if (machineMap.putIfAbsent(machine.id(), machine) != null) throw new DomainException("duplicate");
            if (locationMap.putIfAbsent(new Location(machine.dimension(), machine.position()), machine.id()) != null)
                throw new DomainException("position_occupied");
        }
        LinkedHashMap<UUID, Long> creditMap = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> credit : credits.entrySet())
            creditMap.put(Objects.requireNonNull(credit.getKey(), "credit owner"), Money.positive(Objects.requireNonNull(credit.getValue(), "credit")));
        LinkedHashMap<UUID, Round> roundMap = new LinkedHashMap<>();
        Map<UUID, Long> reservations = new LinkedHashMap<>(), playerCapacity = new LinkedHashMap<>(creditMap);
        Map<UUID, UUID> busyMachines = new LinkedHashMap<>();
        for (Round round : rounds) {
            Objects.requireNonNull(round, "round");
            if (roundMap.putIfAbsent(round.id(), round) != null) throw new DomainException("duplicate");
            Machine machine = machineMap.get(round.machineId());
            if (machine == null || round.maxPayout() != maximumPayout(machine.kind(), round.stake()))
                throw new DomainException("invalid");
            if (busyMachines.putIfAbsent(machine.id(), round.id()) != null)
                throw new DomainException("busy");
            reservations.merge(machine.id(), round.maxPayout(), Money::add);
            playerCapacity.merge(round.player(), round.maxPayout(), Money::add);
        }
        if (playerCapacity.size() > MAX_CREDITORS) throw new DomainException("capacity");
        for (Machine machine : machineMap.values()) {
            if (machine.reserved() != reservations.getOrDefault(machine.id(), 0L))
                throw new DomainException("invalid");
        }
        LinkedHashMap<UUID, History> historyMap = new LinkedHashMap<>();
        long previousTime = -1;
        for (History entry : history) {
            Objects.requireNonNull(entry, "history entry");
            if (historyMap.putIfAbsent(entry.id(), entry) != null || roundMap.containsKey(entry.id()))
                throw new DomainException("duplicate");
            if (entry.settledAt() < previousTime) throw new DomainException("invalid");
            previousTime = entry.settledAt();
        }
        this.machines = Map.copyOf(machineMap);
        this.rounds = Map.copyOf(roundMap);
        this.credits = Map.copyOf(creditMap);
        this.history = List.copyOf(historyMap.values());
        this.locations = Map.copyOf(locationMap);
    }
    public Map<UUID, Machine> machines() { return machines; }
    public Map<UUID, Round> rounds() { return rounds; }
    public Map<UUID, Long> credits() { return credits; }
    public List<History> history() { return history; }
    public Optional<Machine> machine(UUID id) { return Optional.ofNullable(machines.get(Objects.requireNonNull(id, "id"))); }
    public Optional<Machine> at(String dimension, long position) {
        UUID id = locations.get(new Location(dimension, position));
        return id == null ? Optional.empty() : machine(id);
    }
    public long credit(UUID player) { return credits.getOrDefault(Objects.requireNonNull(player, "player"), 0L); }
    public long pending(UUID player) { return credit(player); }

    public CasinoLedger register(UUID id, UUID owner, Kind kind, String dimension, long position,
                                 String name, long minBet, long maxBet) {
        if (machines.size() >= MAX_MACHINES) throw new DomainException("capacity");
        if (machines.containsKey(Objects.requireNonNull(id, "id"))) throw new DomainException("duplicate");
        if (at(dimension, position).isPresent()) throw new DomainException("position_occupied");
        Machine machine = new Machine(id, owner, kind, dimension, position, name, minBet, maxBet, 0, 0, 0);
        return replace(machine, rounds, credits, history);
    }
    public CasinoLedger configure(UUID id, UUID owner, String name, long minBet, long maxBet) {
        Machine before = owned(id, owner);
        return replace(new Machine(id, owner, before.kind(), before.dimension(), before.position(), name,
                minBet, maxBet, before.bank(), before.reserved(), nextVersion(before)), rounds, credits, history);
    }
    public CasinoLedger deposit(UUID id, UUID owner, long amount) {
        Machine before = owned(id, owner);
        return replace(bank(before, Money.add(before.bank(), Money.positive(amount)), before.reserved()), rounds, credits, history);
    }
    /** Moves withdrawable bank money into the owner's durable claim balance. */
    public CasinoLedger withdraw(UUID id, UUID owner, long amount) {
        Machine before = owned(id, owner);
        if (Money.positive(amount) > before.available()) throw new DomainException("bank_reserved");
        Map<UUID, Long> updatedCredits = new LinkedHashMap<>(credits);
        updatedCredits.put(owner, Money.add(credit(owner), amount));
        return replace(bank(before, before.bank() - amount, before.reserved()), rounds, updatedCredits, history);
    }
    public CasinoLedger remove(UUID id, UUID owner) {
        Machine before = owned(id, owner);
        if (before.bank() != 0 || before.reserved() != 0 || rounds.values().stream().anyMatch(round -> round.machineId().equals(id)))
            throw new DomainException("bank_not_empty");
        Map<UUID, Machine> updated = new LinkedHashMap<>(machines); updated.remove(id);
        return new CasinoLedger(updated.values(), rounds.values(), credits, history);
    }
    /** The caller must successfully debit the stake before committing the returned ledger. */
    public CasinoLedger startBet(UUID machineId, UUID player, long stake, UUID roundId, long expectedVersion, long now) {
        Machine before = require(machineId);
        Objects.requireNonNull(player, "player"); Objects.requireNonNull(roundId, "roundId");
        if (before.version() != expectedVersion) throw new DomainException("stale");
        if (stake < before.minBet() || stake > before.maxBet()) throw new DomainException("bet_limits");
        if (rounds.size() >= MAX_ROUNDS) throw new DomainException("capacity");
        if (rounds.containsKey(roundId) || history.stream().anyMatch(entry -> entry.id().equals(roundId)))
            throw new DomainException("duplicate");
        if (rounds.values().stream().anyMatch(round -> round.machineId().equals(machineId)))
            throw new DomainException("busy");
        long liability = maximumPayout(before.kind(), stake);
        long bank = Money.add(before.bank(), stake);
        if (bank - before.reserved() < liability) throw new DomainException("bank_insufficient");
        // Constructor also reserves each player's worst-case pending-credit capacity.
        Map<UUID, Round> updated = new LinkedHashMap<>(rounds);
        updated.put(roundId, new Round(roundId, machineId, player, stake, liability, now));
        return replace(bank(before, bank, Money.add(before.reserved(), liability)), updated, credits, history);
    }
    /** All winnings go to a durable claim balance, including when the player is offline. */
    public CasinoLedger settle(UUID roundId, long grossPayout, long now) {
        Round round = requireRound(roundId);
        Machine machine = require(round.machineId());
        if (!permittedReturn(machine.kind(), round.stake(), grossPayout))
            throw new DomainException("payout_invalid");
        return settleInternal(round, grossPayout, now);
    }
    /** Recovery-only refund. The stake is returned once; no new money is created. */
    public CasinoLedger refund(UUID roundId, long now) {
        Round round = requireRound(roundId);
        return settleInternal(round, round.stake(), now);
    }
    /** The caller commits this value only after an exact durable external wallet credit. */
    public CasinoLedger claim(UUID player, long amount) {
        if (Money.positive(amount) > credit(player)) throw new DomainException("pending_insufficient");
        Map<UUID, Long> updated = new LinkedHashMap<>(credits);
        if (amount == credit(player)) updated.remove(player); else updated.put(player, credit(player) - amount);
        return new CasinoLedger(machines.values(), rounds.values(), updated, history);
    }
    public static long maximumPayout(Kind kind, long stake) {
        Objects.requireNonNull(kind, "kind"); Money.positive(stake);
        return switch (kind) {
            case SLOTS -> Money.multiply(stake, Slots.MAX_MULTIPLIER);
            case ROULETTE -> Money.multiply(stake, Roulette.MAX_MULTIPLIER);
            case BLACKJACK -> Blackjack.maximumPayout(stake);
        };
    }
    private CasinoLedger settleInternal(Round round, long grossPayout, long now) {
        Machine before = require(round.machineId());
        Money.checked(grossPayout);
        if (grossPayout > round.maxPayout() || now < round.startedAt()) throw new DomainException("invalid");
        Map<UUID, Round> updatedRounds = new LinkedHashMap<>(rounds); updatedRounds.remove(round.id());
        Map<UUID, Long> updatedCredits = new LinkedHashMap<>(credits);
        if (grossPayout != 0) updatedCredits.put(round.player(), Money.add(credit(round.player()), grossPayout));
        ArrayList<History> updatedHistory = new ArrayList<>(history);
        // Wall clocks can move backwards; preserve a monotonically ordered audit trail.
        long settledAt = updatedHistory.isEmpty() ? now : Math.max(now, updatedHistory.getLast().settledAt());
        updatedHistory.add(new History(round.id(), before.id(), round.player(), round.stake(), grossPayout, settledAt));
        if (updatedHistory.size() > MAX_HISTORY) updatedHistory.removeFirst();
        return replace(bank(before, before.bank() - grossPayout, before.reserved() - round.maxPayout()), updatedRounds, updatedCredits, updatedHistory);
    }
    private static boolean permittedReturn(Kind kind, long stake, long payout) {
        if (payout < 0) return false;
        if (payout == 0) return true;
        return switch (kind) {
            case SLOTS -> payout == stake || Slots.TRIPLE_RETURNS.stream().anyMatch(multiplier -> Money.multiply(stake, multiplier) == payout);
            case ROULETTE -> payout == Money.multiply(stake, 2) || payout == Money.multiply(stake, 36);
            case BLACKJACK -> payout == stake || payout == Money.multiply(stake, 2) || payout == Blackjack.maximumPayout(stake);
        };
    }
    private Machine require(UUID id) { return machine(id).orElseThrow(() -> new DomainException("machine_missing")); }
    private Round requireRound(UUID id) {
        Round round = rounds.get(Objects.requireNonNull(id, "roundId"));
        if (round == null) throw new DomainException("round_missing");
        return round;
    }
    private Machine owned(UUID id, UUID owner) {
        Machine machine = require(id);
        if (!machine.owner().equals(Objects.requireNonNull(owner, "owner"))) throw new DomainException("owner_only");
        return machine;
    }
    private CasinoLedger replace(Machine machine, Map<UUID, Round> rounds, Map<UUID, Long> credits, Collection<History> history) {
        Map<UUID, Machine> updated = new LinkedHashMap<>(machines); updated.put(machine.id(), machine);
        return new CasinoLedger(updated.values(), rounds.values(), credits, history);
    }
    private static Machine bank(Machine before, long bank, long reserved) {
        return new Machine(before.id(), before.owner(), before.kind(), before.dimension(), before.position(), before.name(),
                before.minBet(), before.maxBet(), bank, reserved, nextVersion(before));
    }
    private static long nextVersion(Machine machine) { return Math.addExact(machine.version(), 1); }
    private static void requireName(String name) {
        if (name == null || name.isBlank() || !name.equals(name.strip()) || name.codePointCount(0, name.length()) > MAX_NAME
                || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x00a7))
            throw new DomainException("invalid");
    }
    private static void requireLimits(Kind kind, long min, long max) {
        Money.positive(min); Money.positive(max);
        if (min > max) throw new DomainException("invalid");
        maximumPayout(kind, max);
        if (kind == Kind.BLACKJACK) Blackjack.requireStake(min);
    }
}
