package pt.realmnotifications.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardLayoutTest {
    @Test void allAnchorsKeepVisibleCardsInsideCompactAndFullScreens() {
        for(int[] size:new int[][]{{320,240},{800,450},{1920,1080}}) {
            int w=CardLayout.width(size[0]),slots=CardLayout.visible(size[1]);
            for(String position:new String[]{"TOP_LEFT","TOP_CENTER","TOP_RIGHT","BOTTOM_LEFT","BOTTOM_RIGHT"}) {
                int x=CardLayout.x(size[0],w,position);
                assertTrue(x>=0&&x+w<=size[0],position);
                for(int slot=0;slot<slots;slot++) {
                    int y=CardLayout.y(size[1],slot,position);
                    assertTrue(y>=0&&y+CardLayout.HEIGHT<=size[1],position+" slot"+slot);
                }
            }
        }
    }
    @Test void compactScreensUseTwoCardsRatherThanCoveringBottomHud() {
        assertEquals(2,CardLayout.visible(240));assertEquals(3,CardLayout.visible(450));
        assertEquals(278,CardLayout.width(320));
    }
    @Test void easingIsClampedMonotonicAndHasExactEndpoints() {
        assertEquals(0,CardLayout.ease(-1));assertEquals(1,CardLayout.ease(2));
        double previous=0;
        for(int i=0;i<=100;i++) {double next=CardLayout.ease(i/100.0);assertTrue(next>=previous);previous=next;}
        assertTrue(CardLayout.ease(.5)>.5);
    }
}
