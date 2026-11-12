package pt.contracts.client;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemSearchTest {
    private final ItemSearch.Entry oak = new ItemSearch.Entry("minecraft:oak_log", "Tronco de carvalho", List.of("minecraft:logs", "c:logs"), 64);
    private final ItemSearch.Entry stone = new ItemSearch.Entry("minecraft:stone", "Pedra", List.of("minecraft:stone_tool_materials"), 0);
    private final ItemSearch.Entry copper = new ItemSearch.Entry("create:copper_casing", "Caixa de cobre", List.of("create:casings"), 3);
    private final List<ItemSearch.Entry> catalogue = List.of(copper, stone, oak);

    @Test void namesWorkWithoutAnIdentifier() { assertEquals(List.of(oak), ItemSearch.filter(catalogue, "tronco carvalho", false)); }
    @Test void caseWhitespaceAndAccentsDoNotBlockSearch() { assertEquals(List.of(copper), ItemSearch.filter(catalogue, "  CÁIXA   CÓBRE  ", false)); }
    @Test void installedModAndOptionalIdentifierCanNarrowResults() {
        assertEquals(List.of(copper), ItemSearch.filter(catalogue, "create", false));
        assertEquals(List.of(oak), ItemSearch.filter(catalogue, "minecraft:oak_log", false));
    }
    @Test void tagQueriesReturnConcreteItemsRatherThanChangingTheContractTarget() {
        var found = ItemSearch.filter(catalogue, "#minecraft:logs carvalho", false);
        assertEquals(List.of(oak), found);
        assertEquals("minecraft:oak_log", found.getFirst().id());
        assertEquals(List.of(copper), ItemSearch.filter(catalogue, "#casings", false));
    }
    @Test void inventoryFilterOnlyKeepsEligibleBaseItemsWithPositiveCounts() {
        assertEquals(List.of(copper, oak), ItemSearch.filter(catalogue, "", true));
        assertTrue(ItemSearch.filter(catalogue, "pedra", true).isEmpty());
    }
    @Test void blankAndMissingQueriesPreserveOrderWithoutChangingTheCatalogue() {
        assertEquals(catalogue, ItemSearch.filter(catalogue, "  ", false));
        assertTrue(ItemSearch.filter(catalogue, "missing item", false).isEmpty());
        assertEquals(3, catalogue.size());
    }
}
