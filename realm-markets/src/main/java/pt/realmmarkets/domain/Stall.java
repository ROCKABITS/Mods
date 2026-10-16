package pt.realmmarkets.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-owned, immutable state for one physical market stall. */
public record Stall(UUID id, UUID owner, String dimension, long position, String title,
                    Currency currency, long earnings, List<Listing> listings) {
    public static final int TITLE_LIMIT = 40;
    public static final long MAX_EARNINGS = 900_000_000_000_000_000L;

    public Stall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Listing.requireIdentifier(dimension, "dimension");
        requireTitle(title);
        Objects.requireNonNull(currency, "currency");
        if (earnings < 0 || earnings > MAX_EARNINGS) throw new IllegalArgumentException("Invalid retained earnings.");
        Objects.requireNonNull(listings, "listings");
        if (listings.size() > Listing.SLOT_COUNT) throw new IllegalArgumentException("Too many offers.");
        Set<Integer> slots = new HashSet<>();
        for (Listing listing : listings) {
            Objects.requireNonNull(listing, "listing");
            if (!slots.add(listing.slot())) throw new IllegalArgumentException("Duplicate offer slot.");
        }
        listings = listings.stream().sorted(Comparator.comparingInt(Listing::slot)).toList();
    }

    public static void requireTitle(String title) {
        if (title == null || title.isBlank() || title.length() > TITLE_LIMIT) {
            throw new IllegalArgumentException("The stall title must contain 1 to 40 characters.");
        }
        for (int offset = 0; offset < title.length();) {
            int codePoint = title.codePointAt(offset);
            if (codePoint == '\u00a7' || Character.isISOControl(codePoint)
                    || Character.getType(codePoint) == Character.SURROGATE) {
                throw new IllegalArgumentException("Invalid characters in the stall title.");
            }
            offset += Character.charCount(codePoint);
        }
    }

    public Optional<Listing> listing(int slot) {
        Listing.requireSlot(slot);
        return listings.stream().filter(listing -> listing.slot() == slot).findFirst();
    }

    public boolean empty() { return earnings == 0 && listings.stream().allMatch(listing -> listing.stock() == 0); }
}
