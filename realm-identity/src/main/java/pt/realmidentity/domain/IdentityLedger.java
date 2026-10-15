package pt.realmidentity.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One current document per owner, shared by all dimensions of one world. */
public final class IdentityLedger {
    public static final int MAX_OWNERS = 100_000;

    public record Snapshot(UUID seal, long nextSerial, List<Document> documents) {
        public Snapshot {
            Objects.requireNonNull(seal, "seal");
            documents = List.copyOf(documents);
        }
    }

    private final UUID seal;
    private final int maxOwners;
    private final Map<UUID, Document> byId = new LinkedHashMap<>();
    private final Map<UUID, Document> byOwner = new LinkedHashMap<>();
    private long nextSerial;

    public IdentityLedger() { this(MAX_OWNERS); }

    public IdentityLedger(int maxOwners) { this(UUID.randomUUID(), 1, List.of(), maxOwners); }

    public IdentityLedger(UUID seal, long nextSerial, Collection<Document> documents) {
        this(seal, nextSerial, documents, MAX_OWNERS);
    }

    public IdentityLedger(UUID seal, long nextSerial, Collection<Document> documents, int maxOwners) {
        this.seal = Objects.requireNonNull(seal, "seal");
        Objects.requireNonNull(documents, "documents");
        if (maxOwners < 1 || maxOwners > MAX_OWNERS) throw new IllegalArgumentException("Invalid registry capacity.");
        if (nextSerial < 1) throw new IllegalArgumentException("Invalid next document serial.");
        if (documents.size() > maxOwners) throw new IllegalArgumentException("Identity registry exceeds capacity.");
        this.maxOwners = maxOwners;
        this.nextSerial = nextSerial;
        Set<Long> serials = new HashSet<>();
        for (Document document : documents) {
            Objects.requireNonNull(document, "document");
            if (document.serial() >= nextSerial) throw new IllegalArgumentException("Document serial is not below the next serial.");
            if (!serials.add(document.serial())) throw new IllegalArgumentException("Duplicate document serial.");
            if (byId.putIfAbsent(document.id(), document) != null) throw new IllegalArgumentException("Duplicate document ID.");
            if (byOwner.putIfAbsent(document.owner(), document) != null) throw new IllegalArgumentException("Duplicate document owner.");
        }
    }

    /** Validate the entire replacement before invalidating any existing document. */
    public Document issue(UUID owner, ProfileSnapshot profile, long now) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(profile, "profile");
        if (now < 0) throw new IllegalArgumentException("Invalid issue time.");
        Document previous = byOwner.get(owner);
        if (previous == null && byOwner.size() >= maxOwners) throw new IllegalStateException("The identity registry is full.");
        if (nextSerial == Long.MAX_VALUE) throw new IllegalStateException("Document serials are exhausted.");
        UUID id;
        do { id = UUID.randomUUID(); } while (byId.containsKey(id));
        Document issued = new Document(id, owner, nextSerial, now, profile);
        if (previous != null) byId.remove(previous.id());
        byId.put(issued.id(), issued);
        byOwner.put(owner, issued);
        nextSerial++;
        return issued;
    }

    /** A physical document is authenticated by its ID, never by an owner search. */
    public Optional<Document> lookup(UUID id) { return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id"))); }

    /** For issue workflows only. This is not proof that a player holds a document. */
    public Optional<Document> current(UUID owner) { return Optional.ofNullable(byOwner.get(Objects.requireNonNull(owner, "owner"))); }

    public UUID seal() { return seal; }
    public long nextSerial() { return nextSerial; }
    public List<Document> documents() { return List.copyOf(byId.values()); }
    public Snapshot snapshot() { return new Snapshot(seal, nextSerial, documents()); }
    public IdentityLedger copy() { return new IdentityLedger(seal, nextSerial, documents(), maxOwners); }
}
