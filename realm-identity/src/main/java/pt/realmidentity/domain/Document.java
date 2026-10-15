package pt.realmidentity.domain;

import java.util.Objects;
import java.util.UUID;

/** The authoritative server record; physical items carry only its random ID. */
public record Document(UUID id, UUID owner, long serial, long issuedAt, ProfileSnapshot profile) {
    public Document {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(profile, "profile");
        if (serial < 1) throw new IllegalArgumentException("Invalid document serial.");
        if (issuedAt < 0) throw new IllegalArgumentException("Invalid issue time.");
    }
}
