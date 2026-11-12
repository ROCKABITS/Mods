package pt.contracts.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemPickerLayoutTest {
    @Test void gridAndFooterFitAllSupportedSmallAndLargeViewports() {
        for (int width = 320; width <= 1280; width += 13) for (int height = 240; height <= 720; height += 11) {
            var layout = ItemPickerLayout.of(width, height);
            for (int i = 0; i < layout.capacity(); i++) {
                var cell = layout.cell(i);
                assertTrue(cell.x() >= 8 && cell.x() + cell.width() <= width - 8);
                assertTrue(cell.width() >= 50);
                assertTrue(cell.y() >= layout.y() + ItemPickerLayout.TOP);
                assertTrue(cell.y() + cell.height() < layout.footerY());
                if (i % layout.columns() != 0) {
                    var previous = layout.cell(i - 1);
                    assertTrue(previous.x() + previous.width() < cell.x());
                }
            }
            assertTrue(layout.footerY() + 20 <= height - 8);
        }
    }
    @Test void paginationIncludesEveryResultAndClampsAfterSearchOrResize() {
        var compact = ItemPickerLayout.of(320, 240);
        assertEquals(3, compact.capacity());
        assertEquals(1, compact.pages(0));
        assertEquals(2, compact.pages(4));
        assertEquals(0, compact.page(99, 1));
        assertEquals(1, compact.page(99, 4));
        assertEquals(0, compact.page(-1, 4));
    }
}
