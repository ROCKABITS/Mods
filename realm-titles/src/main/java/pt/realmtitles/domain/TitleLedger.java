package pt.realmtitles.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Immutable, world-wide public title registry. Time is supplied by the server in
 * epoch milliseconds, so offline expiry does not depend on loaded players or ticks.
 * A replacement is fully validated before callers can commit it to SavedData.
 */
public final class TitleLedger {
    public static final int MAX_DEFINITIONS = 256;
    public static final int MAX_GRANTS_PER_OWNER = 16;
    public static final int MAX_GRANTS = 100_000;
    public static final int MAX_CITIZENS = 100_000;
    public static final int NAME_LIMIT = 48;
    public static final int DESCRIPTION_LIMIT = 256;
    public static final int CITIZEN_NAME_LIMIT = 64;
    public static final int MAX_DAYS = 365;
    public static final long DAY_MILLIS = 86_400_000L;

    public record Definition(UUID id, String name, String description) {
        public Definition {
            required(id);
            checkedText(name, NAME_LIMIT, false, "invalid_name");
            checkedText(description, DESCRIPTION_LIMIT, true, "invalid_description");
        }
    }

    public record Grant(UUID id, UUID owner, UUID titleId, long grantedAt, long expiresAt) {
        public Grant {
            required(id);
            required(owner);
            required(titleId);
            checkedTime(grantedAt);
            if (expiresAt != 0 && expiresAt <= grantedAt) throw error("invalid_expiry");
        }

        /** The expiry instant itself is excluded. Permanent grants use zero. */
        public boolean activeAt(long now) {
            checkedTime(now);
            return expiresAt == 0 || now < expiresAt;
        }
    }

    /** Only an observed public name and eligibility are retained, never profile data. */
    public record Citizen(UUID id, String name, boolean eligible) {
        public Citizen {
            required(id);
            checkedText(name, CITIZEN_NAME_LIMIT, false, "invalid_player_name");
        }
    }

    public record Snapshot(List<Definition> definitions, List<Grant> grants, List<Citizen> citizens) {
        public Snapshot {
            if (definitions == null || grants == null || citizens == null) throw error("invalid_data");
            definitions = List.copyOf(definitions);
            grants = List.copyOf(grants);
            citizens = List.copyOf(citizens);
        }
    }

    private record OwnerTitle(UUID owner, UUID titleId) {}

    private final Map<UUID, Definition> definitions;
    private final Map<UUID, Grant> grants;
    private final Map<UUID, Citizen> citizens;
    private final Map<UUID, List<Grant>> grantsByOwner;

    public TitleLedger() { this(List.of(), List.of(), List.of()); }

    /** Strict load path: corrupt, duplicate, dangling, or oversized state is rejected. */
    public TitleLedger(Collection<Definition> definitions, Collection<Grant> grants,
                       Collection<Citizen> citizens) {
        if (definitions == null || grants == null || citizens == null) throw error("invalid_data");
        if (definitions.size() > MAX_DEFINITIONS) throw error("definition_limit");
        if (grants.size() > MAX_GRANTS) throw error("grant_limit");
        if (citizens.size() > MAX_CITIZENS) throw error("citizen_limit");
        Map<UUID, Definition> definitionMap = new LinkedHashMap<>();
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Definition definition : definitions) {
            required(definition);
            if (definitionMap.putIfAbsent(definition.id(), definition) != null) throw error("duplicate_definition");
            if (!names.add(definition.name())) throw error("duplicate_name");
        }
        Map<UUID, Citizen> citizenMap = new LinkedHashMap<>();
        for (Citizen citizen : citizens) {
            required(citizen);
            if (citizenMap.putIfAbsent(citizen.id(), citizen) != null) throw error("duplicate_citizen");
        }
        Map<UUID, Grant> grantMap = new LinkedHashMap<>();
        Map<UUID, List<Grant>> ownerMap = new HashMap<>();
        Set<OwnerTitle> ownerTitles = new HashSet<>();
        for (Grant grant : grants) {
            required(grant);
            if (!definitionMap.containsKey(grant.titleId())) throw error("unknown_definition");
            if (!citizenMap.containsKey(grant.owner())) throw error("unknown_player");
            if (grantMap.putIfAbsent(grant.id(), grant) != null) throw error("duplicate_grant");
            if (!ownerTitles.add(new OwnerTitle(grant.owner(), grant.titleId()))) throw error("duplicate_assignment");
            List<Grant> owned = ownerMap.computeIfAbsent(grant.owner(), ignored -> new ArrayList<>());
            if (owned.size() >= MAX_GRANTS_PER_OWNER) throw error("owner_grant_limit");
            owned.add(grant);
        }
        this.definitions = Collections.unmodifiableMap(definitionMap);
        this.grants = Collections.unmodifiableMap(grantMap);
        this.citizens = Collections.unmodifiableMap(citizenMap);
        ownerMap.replaceAll((owner, owned) -> List.copyOf(owned));
        this.grantsByOwner = Collections.unmodifiableMap(ownerMap);
    }

    public Map<UUID, Definition> definitions() { return definitions; }
    public Map<UUID, Grant> grants() { return grants; }
    public Map<UUID, Citizen> citizens() { return citizens; }

    public Snapshot snapshot() {
        return new Snapshot(List.copyOf(definitions.values()), List.copyOf(grants.values()),
                List.copyOf(citizens.values()));
    }

    public TitleLedger createDefinition(UUID id, String name, String description) {
        Definition definition = new Definition(id, name, description);
        if (definitions.containsKey(id)) throw error("duplicate_definition");
        if (definitions.size() >= MAX_DEFINITIONS) throw error("definition_limit");
        Map<UUID, Definition> next = new LinkedHashMap<>(definitions);
        next.put(id, definition);
        return replacement(next.values(), grants.values(), citizens.values());
    }

    /** The definition UUID remains stable, so current assignments show edited wording. */
    public TitleLedger editDefinition(UUID id, String name, String description) {
        Definition definition = new Definition(id, name, description);
        if (!definitions.containsKey(id)) throw error("unknown_definition");
        if (definition.equals(definitions.get(id))) return this;
        Map<UUID, Definition> next = new LinkedHashMap<>(definitions);
        next.put(id, definition);
        return replacement(next.values(), grants.values(), citizens.values());
    }

    /** Explicitly revoke live assignments before deleting a definition. */
    public TitleLedger deleteDefinition(UUID id, long now) {
        required(id);
        checkedTime(now);
        if (!definitions.containsKey(id)) throw error("unknown_definition");
        for (Grant grant : grants.values()) {
            if (grant.titleId().equals(id) && grant.activeAt(now)) throw error("definition_in_use");
        }
        Map<UUID, Definition> next = new LinkedHashMap<>(definitions);
        next.remove(id);
        List<Grant> retained = grants.values().stream().filter(grant -> !grant.titleId().equals(id)).toList();
        return replacement(next.values(), retained, citizens.values());
    }

    public TitleLedger observeCitizen(UUID id, String name, boolean eligible) {
        Citizen citizen = new Citizen(id, name, eligible);
        Citizen previous = citizens.get(id);
        if (citizen.equals(previous)) return this;
        if (previous == null && citizens.size() >= MAX_CITIZENS) throw error("citizen_limit");
        Map<UUID, Citizen> next = new LinkedHashMap<>(citizens);
        next.put(id, citizen);
        return replacement(definitions.values(), grants.values(), next.values());
    }

    /**
     * Assign or renew one title with a fresh grant UUID. The previous grant UUID
     * immediately becomes stale; a revoke opened before renewal cannot revoke it.
     * Expired rows are discarded before checking active assignment capacity.
     */
    public TitleLedger grant(UUID owner, UUID titleId, int days, long now, UUID grantId) {
        required(owner);
        required(titleId);
        required(grantId);
        checkedTime(now);
        if (days < 0 || days > MAX_DAYS) throw error("invalid_days");
        Citizen citizen = citizens.get(owner);
        if (citizen == null) throw error("unknown_player");
        if (!citizen.eligible()) throw error("ineligible_player");
        if (!definitions.containsKey(titleId)) throw error("unknown_definition");
        if (grants.containsKey(grantId)) throw error("duplicate_grant");
        long expiresAt;
        try {
            expiresAt = days == 0 ? 0 : Math.addExact(now, Math.multiplyExact((long) days, DAY_MILLIS));
        } catch (ArithmeticException exception) {
            throw error("invalid_expiry");
        }
        Grant assigned = new Grant(grantId, owner, titleId, now, expiresAt);
        List<Grant> next = new ArrayList<>();
        int ownerCount = 0;
        for (Grant grant : grants.values()) {
            if (!grant.activeAt(now) || (grant.owner().equals(owner) && grant.titleId().equals(titleId))) continue;
            if (grant.owner().equals(owner)) ownerCount++;
            next.add(grant);
        }
        if (ownerCount >= MAX_GRANTS_PER_OWNER) throw error("owner_grant_limit");
        if (next.size() >= MAX_GRANTS) throw error("grant_limit");
        next.add(assigned);
        return replacement(definitions.values(), next, citizens.values());
    }

    /** Reject stale and expired IDs instead of revoking by owner/title. */
    public TitleLedger revoke(UUID grantId, long now) {
        required(grantId);
        checkedTime(now);
        Grant grant = grants.get(grantId);
        if (grant == null || !grant.activeAt(now)) throw error("stale_grant");
        Map<UUID, Grant> next = new LinkedHashMap<>(grants);
        next.remove(grantId);
        return replacement(definitions.values(), next.values(), citizens.values());
    }

    /** Public read: fresh epoch filtering works even when the owner is offline. */
    public List<Grant> activeGrants(UUID owner, long now) {
        required(owner);
        checkedTime(now);
        return grantsByOwner.getOrDefault(owner, List.of()).stream().filter(grant -> grant.activeAt(now)).toList();
    }

    public TitleLedger purgeExpired(long now) {
        checkedTime(now);
        List<Grant> retained = grants.values().stream().filter(grant -> grant.activeAt(now)).toList();
        if (retained.size() == grants.size()) return this;
        return replacement(definitions.values(), retained, citizens.values());
    }

    private static TitleLedger replacement(Collection<Definition> definitions, Collection<Grant> grants,
                                           Collection<Citizen> citizens) {
        return new TitleLedger(definitions, grants, citizens);
    }

    private static void checkedText(String value, int limit, boolean allowEmpty, String code) {
        if (value == null || value.length() > limit || !value.equals(value.strip())
                || (!allowEmpty && value.isBlank())) throw error(code);
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            int type = Character.getType(point);
            if (point == '\u00a7' || Character.isISOControl(point) || type == Character.FORMAT
                    || type == Character.SURROGATE || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) throw error(code);
            offset += Character.charCount(point);
        }
    }

    private static void checkedTime(long now) {
        if (now < 0) throw error("invalid_time");
    }

    private static <T> T required(T value) {
        if (value == null) throw error("invalid_data");
        return value;
    }

    private static DomainException error(String code) { return new DomainException(code); }
}
