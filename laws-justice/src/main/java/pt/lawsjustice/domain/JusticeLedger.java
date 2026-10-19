package pt.lawsjustice.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable custody state. Only explicit online ticks serve a sentence; dates
 * are audit information and never produce elapsed sentence time.
 */
public final class JusticeLedger {
    public static final int MAX_PRISONS = 128;
    public static final int MAX_CELLS = 64;
    public static final int MAX_ACTIVE = 4096;
    public static final int MAX_HISTORY = 2000;
    public static final long MAX_SENTENCE_TICKS = 51_840_000L;
    public static final long MAX_TICKS = MAX_SENTENCE_TICKS;
    public static final int MAX_AREA_EXTENT = 1024;
    public static final long MAX_AREA_VOLUME = 16_777_216L;
    private static final int HORIZONTAL_LIMIT = 29_999_984;
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    public static final String SENTENCED = "SENTENCED";
    public static final String COMPLETED = "COMPLETED";
    public static final String EMERGENCY_RELEASE = "EMERGENCY_RELEASE";
    public static final String WORK_REDUCTION = "WORK_REDUCTION";
    public static final String BAIL_RELEASE="BAIL_RELEASE", JUDICIAL_RELEASE="JUDICIAL_RELEASE";

    public record Point(int x, int y, int z) {
        public Point {
            require(x >= -HORIZONTAL_LIMIT && x <= HORIZONTAL_LIMIT
                    && z >= -HORIZONTAL_LIMIT && z <= HORIZONTAL_LIMIT
                    && y >= -2048 && y <= 2047, "coordinates");
        }
    }

    public record Area(String dimension, Point min, Point max) {
        public Area {
            require(dimension != null && dimension.length() <= 256
                    && DIMENSION.matcher(dimension).matches(), "dimension");
            notNull(min); notNull(max);
            long width = (long) max.x() - min.x() + 1;
            long height = (long) max.y() - min.y() + 1;
            long depth = (long) max.z() - min.z() + 1;
            require(width > 0 && height > 0 && depth > 0, "area");
            require(width <= MAX_AREA_EXTENT && height <= MAX_AREA_EXTENT && depth <= MAX_AREA_EXTENT
                    && width * height * depth <= MAX_AREA_VOLUME, "area_size");
        }

        public static Area between(String dimension, Point first, Point second) {
            notNull(first); notNull(second);
            return new Area(dimension,
                    new Point(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z())),
                    new Point(Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z())));
        }

        public boolean contains(Point point) {
            notNull(point);
            return point.x() >= min.x() && point.x() <= max.x()
                    && point.y() >= min.y() && point.y() <= max.y()
                    && point.z() >= min.z() && point.z() <= max.z();
        }

        public boolean contains(Area other) {
            notNull(other);
            return dimension.equals(other.dimension()) && contains(other.min()) && contains(other.max());
        }

        /** Areas sharing a block intersect; adjacent blocks do not. */
        public boolean intersects(Area other) {
            notNull(other);
            return dimension.equals(other.dimension())
                    && min.x() <= other.max().x() && max.x() >= other.min().x()
                    && min.y() <= other.max().y() && max.y() >= other.min().y()
                    && min.z() <= other.max().z() && max.z() >= other.min().z();
        }
    }

    public record Cell(UUID id, String name, Area area, Point spawn, int capacity) {
        public Cell {
            notNull(id); text(name, 48, "name"); notNull(area); notNull(spawn);
            require(capacity >= 1 && capacity <= 16, "capacity");
            require(area.contains(spawn) && area.contains(new Point(spawn.x(),spawn.y()+1,spawn.z())), "cell_spawn");
        }
    }

    public record Prison(UUID id, String name, Area area, Point release, List<Cell> cells, long revision) {
        public Prison {
            notNull(id); text(name, 48, "name"); notNull(area); notNull(release); notNull(cells);
            require(revision >= 0, "revision");
            require(!area.contains(release), "release_inside");
            require(cells.size() <= MAX_CELLS, "capacity");
            Set<UUID> ids = new HashSet<>();
            ArrayList<Cell> copy = new ArrayList<>(cells.size());
            for (Cell cell : cells) {
                notNull(cell);
                require(ids.add(cell.id()), "duplicate");
                require(area.contains(cell.area()), "cell_area");
                for (Cell previous : copy) require(!cell.area().intersects(previous.area()), "overlap");
                copy.add(cell);
            }
            cells = List.copyOf(copy);
        }
    }

    public record Sentence(UUID id, UUID player, String playerName, UUID prison, UUID cell,
                           UUID authority, String authorityName, String reason, long issuedAt,
                           long originalTicks, long servedTicks, long reductionTicks, long revision) {
        public Sentence {
            notNull(id); notNull(player); notNull(prison); notNull(cell); notNull(authority);
            text(playerName, 64, "name"); text(authorityName, 64, "name"); text(reason, 300, "reason");
            require(issuedAt >= 0, "date");
            ticks(originalTicks, servedTicks, reductionTicks);
            require(revision >= 0, "revision");
        }
        public long remainingTicks() { return originalTicks - servedTicks - reductionTicks; }
    }

    public record Decision(UUID id, UUID sentence, UUID player, String playerName, UUID authority,
                           String authorityName, String action, String reason, long at,
                           long originalTicks, long servedTicks, long reductionTicks) {
        public Decision {
            notNull(id); notNull(sentence); notNull(player); notNull(authority);
            text(playerName, 64, "name"); text(authorityName, 64, "name"); text(reason, 300, "reason");
            require(at >= 0, "date");
            ticks(originalTicks, servedTicks, reductionTicks);
            require(SENTENCED.equals(action) || COMPLETED.equals(action) || EMERGENCY_RELEASE.equals(action) || WORK_REDUCTION.equals(action) || BAIL_RELEASE.equals(action) || JUDICIAL_RELEASE.equals(action), "action");
            require(!SENTENCED.equals(action) || servedTicks == 0 && reductionTicks == 0, "history");
            require(!COMPLETED.equals(action) || servedTicks + reductionTicks == originalTicks, "history");
            require(!(EMERGENCY_RELEASE.equals(action)||BAIL_RELEASE.equals(action)||JUDICIAL_RELEASE.equals(action)) || servedTicks + reductionTicks < originalTicks, "history");
            require(!WORK_REDUCTION.equals(action) || reductionTicks > 0, "history");
        }
    }

    private final Map<UUID, Prison> prisons;
    private final Map<UUID, Sentence> active;
    private final List<Decision> history;

    private JusticeLedger(Map<UUID, Prison> prisons, Map<UUID, Sentence> active, List<Decision> history) {
        notNull(prisons); notNull(active); notNull(history);
        require(prisons.size() <= MAX_PRISONS && active.size() <= MAX_ACTIVE && history.size() <= MAX_HISTORY, "capacity");
        LinkedHashMap<UUID, Prison> prisonCopy = new LinkedHashMap<>();
        Set<UUID> cellIds = new HashSet<>();
        for (Map.Entry<UUID, Prison> entry : prisons.entrySet()) {
            notNull(entry.getKey()); Prison prison = notNull(entry.getValue());
            require(entry.getKey().equals(prison.id()), "reference");
            for (Prison previous : prisonCopy.values()) require(!prison.area().intersects(previous.area()), "overlap");
            for (Cell cell : prison.cells()) require(cellIds.add(cell.id()), "duplicate");
            prisonCopy.put(entry.getKey(), prison);
        }
        LinkedHashMap<UUID, Sentence> activeCopy = new LinkedHashMap<>();
        Set<UUID> sentenceIds = new HashSet<>();
        Map<UUID, Integer> occupancy = new HashMap<>();
        for (Map.Entry<UUID, Sentence> entry : active.entrySet()) {
            notNull(entry.getKey()); Sentence sentence = notNull(entry.getValue());
            require(entry.getKey().equals(sentence.player()), "reference");
            require(sentence.remainingTicks() > 0, "completed_active");
            require(sentenceIds.add(sentence.id()), "duplicate");
            Prison prison = prisonCopy.get(sentence.prison());
            require(prison != null, "prison_missing");
            Cell cell = findCell(prison, sentence.cell());
            require(cell != null, "cell_missing");
            int occupied = occupancy.merge(cell.id(), 1, Integer::sum);
            require(occupied <= cell.capacity(), "cell_full");
            activeCopy.put(entry.getKey(), sentence);
        }
        validateHistory(history, activeCopy);
        this.prisons = Collections.unmodifiableMap(prisonCopy);
        this.active = Collections.unmodifiableMap(activeCopy);
        this.history = List.copyOf(history);
    }

    /** Only custody updates derived from this validated instance enter here. */
    private JusticeLedger(JusticeLedger previous, LinkedHashMap<UUID, Sentence> changed, List<Decision> history) {
        this.prisons = previous.prisons;
        this.active = Collections.unmodifiableMap(changed);
        this.history = List.copyOf(history);
    }

    public static JusticeLedger empty() { return new JusticeLedger(Map.of(), Map.of(), List.of()); }

    /** No invalid record is skipped or repaired when loading durable custody. */
    public static JusticeLedger restore(Map<UUID, Prison> prisons, Map<UUID, Sentence> active, List<Decision> history) {
        return new JusticeLedger(prisons, active, history);
    }

    public Map<UUID, Prison> prisons() { return prisons; }
    public Map<UUID, Sentence> active() { return active; }
    public List<Decision> history() { return history; }

    /** New prisons supply revision zero; every successful write increments it. */
    public JusticeLedger putPrison(Prison prison) {
        notNull(prison);
        Prison previous = prisons.get(prison.id());
        if (previous == null) {
            require(prison.revision() == 0, "revision");
            require(prisons.size() < MAX_PRISONS, "capacity");
            prison = new Prison(prison.id(), prison.name(), prison.area(), prison.release(), prison.cells(), 1);
        } else {
            idle(prison.id());
            state(prison.revision() == previous.revision(), "stale");
            prison = new Prison(prison.id(), prison.name(), prison.area(), prison.release(), prison.cells(), next(previous.revision()));
        }
        LinkedHashMap<UUID, Prison> changed = new LinkedHashMap<>(prisons);
        changed.put(prison.id(), prison);
        return new JusticeLedger(changed, active, history);
    }

    public JusticeLedger putCell(UUID prisonId, Cell cell) {
        notNull(cell);
        Prison prison = prison(prisonId); idle(prisonId);
        ArrayList<Cell> cells = new ArrayList<>(prison.cells());
        int index = -1;
        for (int i = 0; i < cells.size(); i++) if (cells.get(i).id().equals(cell.id())) { index = i; break; }
        if (index < 0) cells.add(cell); else cells.set(index, cell);
        return putPrison(new Prison(prison.id(), prison.name(), prison.area(), prison.release(), cells, prison.revision()));
    }

    public JusticeLedger removeCell(UUID prisonId, UUID cellId) {
        notNull(cellId);
        Prison prison = prison(prisonId); idle(prisonId);
        ArrayList<Cell> cells = new ArrayList<>(prison.cells());
        state(cells.removeIf(cell -> cell.id().equals(cellId)), "cell_missing");
        return putPrison(new Prison(prison.id(), prison.name(), prison.area(), prison.release(), cells, prison.revision()));
    }

    public JusticeLedger removePrison(UUID prisonId) {
        prison(prisonId); idle(prisonId);
        LinkedHashMap<UUID, Prison> changed = new LinkedHashMap<>(prisons);
        changed.remove(prisonId);
        return new JusticeLedger(changed, active, history);
    }

    public JusticeLedger sentence(Sentence sentence) {
        notNull(sentence);
        state(!active.containsKey(sentence.player()), "already_sentenced");
        require(active.size() < MAX_ACTIVE, "capacity");
        require(sentence.servedTicks() == 0 && sentence.reductionTicks() == 0 && sentence.revision() == 0, "sentence");
        state(active.values().stream().noneMatch(value -> value.id().equals(sentence.id()))
                && history.stream().noneMatch(value -> value.sentence().equals(sentence.id())), "duplicate");
        LinkedHashMap<UUID, Sentence> changed = new LinkedHashMap<>(active);
        changed.put(sentence.player(), sentence);
        return new JusticeLedger(prisons, changed,
                append(decision(sentence, sentence.authority(), sentence.authorityName(), SENTENCED, sentence.reason(), sentence.issuedAt())));
    }

    /**
     * Advances one online player. A second tick after completion is a no-op.
     * Even an extreme elapsed argument is clamped without integer overflow.
     */
    public JusticeLedger advance(UUID player, long onlineTicks, long now) {
        notNull(player); require(onlineTicks >= 0, "ticks"); require(now >= 0, "date");
        Sentence previous = active.get(player);
        if (previous == null || onlineTicks == 0) return this;
        long served = previous.servedTicks() + Math.min(onlineTicks, previous.remainingTicks());
        long revision = previous.revision() == Long.MAX_VALUE ? Long.MAX_VALUE : previous.revision() + 1;
        Sentence updated = new Sentence(previous.id(), previous.player(), previous.playerName(), previous.prison(), previous.cell(),
                previous.authority(), previous.authorityName(), previous.reason(), previous.issuedAt(), previous.originalTicks(),
                served, previous.reductionTicks(), revision);
        LinkedHashMap<UUID, Sentence> changed = new LinkedHashMap<>(active);
        if (updated.remainingTicks() > 0) {
            changed.put(player, updated);
            return new JusticeLedger(this, changed, history);
        }
        changed.remove(player);
        return new JusticeLedger(this, changed,
                append(decision(updated, updated.authority(), updated.authorityName(), COMPLETED, updated.reason(), now)));
    }

    /** Only validated, completed community work calls this operation. Clock and reduction remain independent. */
    public JusticeLedger reduce(UUID player,long requestedTicks,int maximumPercent,long now) {
        notNull(player);require(requestedTicks>0,"work_reward");require(maximumPercent>=0&&maximumPercent<=100,"work_cap");require(now>=0,"date");
        Sentence previous=active.get(player);state(previous!=null,"sentence_missing");
        long budget=Math.max(0,previous.originalTicks()*maximumPercent/100-previous.reductionTicks());
        long amount=Math.min(Math.min(requestedTicks,budget),previous.remainingTicks());state(amount>0,"work_cap");
        Sentence updated=new Sentence(previous.id(),previous.player(),previous.playerName(),previous.prison(),previous.cell(),previous.authority(),previous.authorityName(),previous.reason(),previous.issuedAt(),previous.originalTicks(),previous.servedTicks(),previous.reductionTicks()+amount,previous.revision()==Long.MAX_VALUE?Long.MAX_VALUE:previous.revision()+1);
        LinkedHashMap<UUID,Sentence> changed=new LinkedHashMap<>(active);
        List<Decision> decisions=append(decision(updated,updated.authority(),updated.authorityName(),WORK_REDUCTION,"Community service",now));
        if(updated.remainingTicks()>0)changed.put(player,updated);
        else {changed.remove(player);decisions=append(decisions,decision(updated,updated.authority(),updated.authorityName(),COMPLETED,updated.reason(),now));}
        return new JusticeLedger(this,changed,decisions);
    }

    public JusticeLedger release(UUID player, UUID actor, String actorName, String reason, long now) {return releaseAs(player,actor,actorName,reason,now,EMERGENCY_RELEASE);}
    public JusticeLedger judicialRelease(UUID player,UUID actor,String actorName,String reason,long now,boolean bail){return releaseAs(player,actor,actorName,reason,now,bail?BAIL_RELEASE:JUDICIAL_RELEASE);}
    private JusticeLedger releaseAs(UUID player, UUID actor, String actorName, String reason, long now,String action) {
        notNull(player); notNull(actor); text(actorName, 64, "name"); text(reason, 300, "reason"); require(now >= 0, "date");
        Sentence sentence = active.get(player);
        state(sentence != null, "sentence_missing");
        LinkedHashMap<UUID, Sentence> changed = new LinkedHashMap<>(active);
        changed.remove(player);
        return new JusticeLedger(this, changed, append(decision(sentence, actor, actorName, action, reason, now)));
    }

    private Prison prison(UUID id) {
        notNull(id); Prison prison = prisons.get(id); state(prison != null, "prison_missing"); return prison;
    }

    private void idle(UUID prison) {
        state(active.values().stream().noneMatch(value -> value.prison().equals(prison)), "prison_active");
    }

    private List<Decision> append(Decision decision) { return append(history,decision); }
    private static List<Decision> append(List<Decision> previous,Decision decision) {
        ArrayList<Decision> result = new ArrayList<>(Math.min(MAX_HISTORY, previous.size() + 1));
        result.addAll(previous.subList(previous.size() == MAX_HISTORY ? 1 : 0, previous.size()));
        result.add(decision);return result;
    }

    private Decision decision(Sentence sentence, UUID authority, String authorityName, String action, String reason, long now) {
        UUID id;
        do { id = UUID.randomUUID(); } while (hasDecision(id));
        return new Decision(id, sentence.id(), sentence.player(), sentence.playerName(), authority, authorityName,
                action, reason, now, sentence.originalTicks(), sentence.servedTicks(), sentence.reductionTicks());
    }

    private boolean hasDecision(UUID id) { return history.stream().anyMatch(value -> value.id().equals(id)); }

    private static Cell findCell(Prison prison, UUID cellId) {
        for (Cell cell : prison.cells()) if (cell.id().equals(cellId)) return cell;
        return null;
    }

    private static void validateHistory(List<Decision> history, Map<UUID, Sentence> active) {
        Set<UUID> decisions = new HashSet<>();
        Map<UUID, Decision> started = new HashMap<>();
        Map<UUID, Decision> finished = new HashMap<>();
        Map<UUID, Decision> openByPlayer = new HashMap<>();
        Map<UUID, Sentence> activeIds = new HashMap<>();
        for (Sentence sentence : active.values()) activeIds.put(sentence.id(), sentence);
        for (Decision entry : history) {
            notNull(entry);
            require(decisions.add(entry.id()), "duplicate");
            if (WORK_REDUCTION.equals(entry.action())) {
                require(!finished.containsKey(entry.sentence()),"history");
                Decision start=started.get(entry.sentence());
                if(start!=null)require(start.player().equals(entry.player())&&start.playerName().equals(entry.playerName())&&start.originalTicks()==entry.originalTicks()&&start.authority().equals(entry.authority()),"history");
            } else if (SENTENCED.equals(entry.action())) {
                require(!finished.containsKey(entry.sentence()) && started.putIfAbsent(entry.sentence(), entry) == null, "history");
                require(openByPlayer.putIfAbsent(entry.player(), entry) == null, "history");
                Sentence sentence = activeIds.get(entry.sentence());
                if (sentence != null) require(matchesStart(entry, sentence), "history");
            } else {
                require(!activeIds.containsKey(entry.sentence()) && finished.putIfAbsent(entry.sentence(), entry) == null, "history");
                Decision open = openByPlayer.remove(entry.player());
                require(open == null || open.sentence().equals(entry.sentence()), "history");
                Decision start = started.get(entry.sentence());
                if (start != null) require(start.player().equals(entry.player())
                        && start.playerName().equals(entry.playerName()) && start.originalTicks() == entry.originalTicks(), "history");
                if (start != null && COMPLETED.equals(entry.action())) require(start.authority().equals(entry.authority())
                        && start.authorityName().equals(entry.authorityName()) && start.reason().equals(entry.reason()), "history");
            }
        }
        for (Decision open : openByPlayer.values()) {
            Sentence sentence = active.get(open.player());
            require(sentence != null && sentence.id().equals(open.sentence()), "history");
        }
    }

    private static boolean matchesStart(Decision start, Sentence sentence) {
        return start.player().equals(sentence.player()) && start.playerName().equals(sentence.playerName())
                && start.authority().equals(sentence.authority()) && start.authorityName().equals(sentence.authorityName())
                && start.reason().equals(sentence.reason()) && start.at() == sentence.issuedAt()
                && start.originalTicks() == sentence.originalTicks();
    }

    private static void ticks(long original, long served, long reduction) {
        require(original >= 1 && original <= MAX_SENTENCE_TICKS && served >= 0 && served <= original, "ticks");
        require(reduction >= 0 && reduction <= original - served, "reduction");
    }

    private static long next(long revision) {
        state(revision < Long.MAX_VALUE, "revision"); return revision + 1;
    }

    private static <T> T notNull(T value) {
        require(value != null, "invalid"); return value;
    }

    private static void text(String value, int maximum, String error) {
        require(value != null && !value.isBlank() && value.length() <= maximum
                && value.codePoints().noneMatch(Character::isISOControl), error);
    }

    private static void require(boolean accepted, String error) {
        if (!accepted) throw new IllegalArgumentException("lawsjustice.error." + error);
    }

    private static void state(boolean accepted, String error) {
        if (!accepted) throw new IllegalStateException("lawsjustice.error." + error);
    }
}
