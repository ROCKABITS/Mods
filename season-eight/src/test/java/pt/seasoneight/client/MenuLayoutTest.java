package pt.seasoneight.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MenuLayoutTest {
    @Test void allControlsFitAndDoNotOverlapAtEverySupportedGuiSize() {
        for (int width = 320; width <= 1280; width += 13)
            for (int height = 180; height <= 720; height += 11) {
                var l = MenuLayout.of(width, height);
                assertEquals(9, l.controls().size());
                for (var a : l.controls()) {
                    assertTrue(a.x() >= 0 && a.y() >= 0 && a.x() + a.w() <= width && a.y() + a.h() <= height - 21,
                            width + "x" + height + " outside: " + a);
                    assertTrue(a.w() >= 45 && a.h() >= 16);
                    for (var b : l.controls()) if (a != b) assertFalse(a.overlaps(b), width + "x" + height + " overlap " + a + " / " + b);
                }
                assertTrue(l.logoY() + l.logoHeight() < l.controls().getFirst().y());
            }
    }
    @Test void compactLayoutKeepsAllActionsAndLargeLayoutShowsDescription() {
        assertFalse(MenuLayout.of(427, 240).description());
        assertTrue(MenuLayout.of(800, 450).description());
        assertFalse(MenuLayout.of(320, 180).artwork());
        assertTrue(MenuLayout.of(800, 450).artwork());
    }
}
