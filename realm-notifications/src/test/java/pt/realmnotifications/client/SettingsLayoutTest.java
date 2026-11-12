package pt.realmnotifications.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsLayoutTest {
    @Test void everySettingIsReachableWithoutOverlappingNavigationOrFooter() {
        for (int screenHeight = 180; screenHeight <= 1080; screenHeight++) {
            var layout = SettingsLayout.of(Math.min(360, screenHeight - 16));
            boolean[] reached = new boolean[SettingsLayout.OPTIONS];
            for (int page = 0; page < layout.pages(); page++) {
                int bottom = layout.pages() > 1 ? layout.navigationY() - 4 : layout.footerY() - 14;
                for (int option = layout.first(page); option < layout.end(page); option++) {
                    assertFalse(reached[option]);
                    reached[option] = true;
                    int y = layout.rowY(option - layout.first(page));
                    assertTrue(y >= layout.contentY());
                    assertTrue(y + SettingsLayout.CONTROL_HEIGHT <= bottom,
                            "height=" + screenHeight + ", option=" + option);
                }
            }
            for (boolean seen : reached) assertTrue(seen);
        }
    }

    @Test void shortestWindowPaginatesInsteadOfCoveringFooterButtons() {
        var layout = SettingsLayout.of(164); // 320 x 180 GUI, 8px panel margins.
        assertEquals(2, layout.rowsPerPage());
        assertEquals(3, layout.pages());
        assertEquals(4, layout.first(2));
        assertEquals(6, layout.end(2));
    }

    @Test void normalAndLargeWindowsKeepAllSixSettingsOnOnePage() {
        assertEquals(1, SettingsLayout.of(224).pages());
        assertEquals(1, SettingsLayout.of(360).pages());
        assertEquals(19, SettingsLayout.of(224).rowStep());
        assertEquals(28, SettingsLayout.of(360).rowStep());
    }

    @Test void resizingClampsTheOldPageToTheNewRange() {
        assertEquals(0, SettingsLayout.of(360).page(2));
        assertEquals(0, SettingsLayout.of(164).page(-1));
        assertEquals(2, SettingsLayout.of(164).page(100));
    }
}
