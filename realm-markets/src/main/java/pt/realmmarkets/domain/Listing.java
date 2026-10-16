package pt.realmmarkets.domain;

/** A configured offer. A stock of zero preserves its item and price for restocking. */
public record Listing(int slot, String item, int stock, long unitPrice) {
    public static final int SLOT_COUNT = 8;
    public static final int MAX_STOCK = 4_096;
    public static final long MAX_UNIT_PRICE = 1_000_000_000L;

    public Listing {
        requireSlot(slot);
        requireIdentifier(item, "item");
        if (stock < 0 || stock > MAX_STOCK) throw new IllegalArgumentException("Invalid listing stock.");
        if (unitPrice < 1 || unitPrice > MAX_UNIT_PRICE) throw new IllegalArgumentException("Invalid price per item.");
    }

    public static void requireSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) throw new IllegalArgumentException("Invalid offer slot.");
    }

    /** Match Minecraft resource locations without depending on the game classes. */
    public static void requireIdentifier(String value, String field) {
        if (value == null || value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("Invalid " + field + " identifier.");
        }
    }
}
