package pt.realmmarkets.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StallValidationTest {
    private static Stall stall(String title, List<Listing> listings) {
        return new Stall(UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld", -14, title, Currency.EMERALD, 0, listings);
    }

    @Test void readableUnicodeIncludingJoinedEmojiIsPreserved() {
        String title = "Banca da Íria 👩‍🌾";
        assertEquals(title, stall(title, List.of()).title());
        assertEquals("x".repeat(40), stall("x".repeat(40), List.of()).title());
    }

    @Test void titlesRejectBlankOverlongControlsFormattingAndBrokenSurrogates() {
        for (String title : new String[]{"", "  ", "x".repeat(41), "A\nB", "A\tB", "A\u007fB", "A§aB", "A\ud800B", "A\udc00B"}) {
            assertThrows(IllegalArgumentException.class, () -> stall(title, List.of()), title);
        }
        assertThrows(IllegalArgumentException.class, () -> stall(null, List.of()));
    }

    @Test void listingsRejectNegativeOrOverflowingStockAndInvalidPrices() {
        assertEquals(0, new Listing(0, "minecraft:apple", 0, 1).stock());
        assertEquals(4_096, new Listing(7, "minecraft:apple", 4_096, 1_000_000_000L).stock());
        for (int stock : new int[]{Integer.MIN_VALUE, -1, 4_097, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new Listing(0, "minecraft:apple", stock, 1));
        }
        for (long price : new long[]{Long.MIN_VALUE, -1, 0, 1_000_000_001L, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new Listing(0, "minecraft:apple", 0, price));
        }
    }

    @Test void itemAndDimensionIdsUseExplicitMinecraftResourceSyntax() {
        assertEquals("other_mod:tools/bronze_hammer", new Listing(0, "other_mod:tools/bronze_hammer", 0, 1).item());
        for (String item : new String[]{"apple", ":apple", "minecraft:", "Minecraft:apple", "minecraft:Apple", "mine craft:apple", "minecraft:apple\n", "a:b:c", "a:" + "b".repeat(255)}) {
            assertThrows(IllegalArgumentException.class, () -> new Listing(0, item, 0, 1), item);
        }
        assertThrows(IllegalArgumentException.class, () -> new Listing(0, null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Stall(UUID.randomUUID(), UUID.randomUUID(), "overworld", 0, "A", Currency.EMERALD, 0, List.of()));
    }

    @Test void duplicateSlotsAreRejectedRatherThanSilentlyReplacingStock() {
        List<Listing> duplicates = List.of(new Listing(0, "minecraft:apple", 3, 1), new Listing(0, "minecraft:carrot", 4, 2));
        assertThrows(IllegalArgumentException.class, () -> stall("Shop", duplicates));
        assertThrows(IllegalArgumentException.class, () -> new Listing(-1, "minecraft:apple", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Listing(8, "minecraft:apple", 0, 1));
    }

    @Test void savedStallCannotHaveInvalidRetainedFunds() {
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Stall(id, owner, "minecraft:overworld", 0, "A", Currency.EMERALD, -1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Stall(id, owner, "minecraft:overworld", 0, "A", Currency.EMERALD, Stall.MAX_EARNINGS + 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Stall(id, owner, "minecraft:overworld", 0, "A", Currency.EMERALD, Long.MAX_VALUE, List.of()));
    }

    @Test void listingCollectionIsDefensivelyCopiedAndCanonicalized() {
        List<Listing> listings = new ArrayList<>(List.of(new Listing(7, "minecraft:apple", 3, 1), new Listing(0, "minecraft:carrot", 4, 2)));
        Stall stall = stall("Shop", listings);
        listings.clear();
        assertEquals(List.of(0, 7), stall.listings().stream().map(Listing::slot).toList());
        assertThrows(UnsupportedOperationException.class, () -> stall.listings().clear());
        assertFalse(stall.empty());
        assertTrue(stall("Shop", List.of(new Listing(0, "minecraft:apple", 0, 1))).empty());
    }
}
