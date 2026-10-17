package pt.realmnotifications.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardLayoutTest {
    @Test void allAnchorsKeepVisibleCardsInsideCompactAndFullScreens() {
        for(int screenWidth:new int[]{320,576,800,1920}) for(int screenHeight=120;screenHeight<=1080;screenHeight++) {
            int w=CardLayout.width(screenWidth),slots=CardLayout.visible(screenHeight);
            for(String position:new String[]{"TOP_LEFT","TOP_CENTER","TOP_RIGHT","BOTTOM_LEFT","BOTTOM_RIGHT"}) {
                int x=CardLayout.x(screenWidth,w,position);
                assertTrue(x>=0&&x+w<=screenWidth,position);
                for(int slot=0;slot<slots;slot++) {
                    int y=CardLayout.y(screenHeight,slot,position);
                    assertTrue(y>=0&&y+CardLayout.HEIGHT<=screenHeight,position+" slot"+slot+" height="+screenHeight);
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
