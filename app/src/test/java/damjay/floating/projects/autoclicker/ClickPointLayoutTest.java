package damjay.floating.projects.autoclicker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClickPointLayoutTest {

    /**
     * The controller labels its buttons 1..n but the points are stored in a 0-based list. The
     * original code used the button value as the index directly, so button "1" tapped the second
     * point, and the last point on screen could never be tapped at all.
     */
    @Test
    public void buttonOneTapsTheFirstPoint() {
        assertEquals(0, ClickPointLayout.indexForButton(1));
        assertEquals(1, ClickPointLayout.indexForButton(2));
        assertEquals(4, ClickPointLayout.indexForButton(5));
    }

    @Test
    public void everyButtonIsReachableIncludingTheLast() {
        int pointCount = 3;
        for (int button = 1; button <= pointCount; button++) {
            int index = ClickPointLayout.indexForButton(button);
            assertTrue(
                    "button " + button + " should address a point",
                    ClickPointLayout.isValidIndex(index, pointCount));
        }
        // And nothing outside that range is accepted.
        assertFalse(ClickPointLayout.isValidIndex(ClickPointLayout.indexForButton(0), pointCount));
        assertFalse(ClickPointLayout.isValidIndex(ClickPointLayout.indexForButton(4), pointCount));
    }

    /** Gestures must land in the middle of a point, not on its top-left corner. */
    @Test
    public void tapsLandOnTheCentreOfAPoint() {
        assertEquals(65, ClickPointLayout.centerOf(50, 30));
        assertEquals(15, ClickPointLayout.centerOf(0, 30));
    }

    @Test
    public void firstPointIsInsetFromTheCorner() {
        int[] position = ClickPointLayout.nextPosition(false, 0, 0, 30, 16, 1080, 1920);
        assertArrayEquals(new int[] {16, 16}, position);
    }

    /**
     * Placement used to depend on getMeasuredWidth()/getMeasuredHeight(), which return 0 before
     * a view has been laid out, so every point was created at the same coordinates and they all
     * stacked on top of each other.
     */
    @Test
    public void pointsDoNotStackOnTopOfEachOther() {
        int size = 30;
        int spacing = 16;
        int[] first = ClickPointLayout.nextPosition(false, 0, 0, size, spacing, 1080, 1920);
        int[] second =
                ClickPointLayout.nextPosition(true, first[0], first[1], size, spacing, 1080, 1920);

        assertFalse(
                "consecutive points must not share a position",
                first[0] == second[0] && first[1] == second[1]);
        assertEquals(first[0] + size + spacing, second[0]);
        assertEquals(first[1], second[1]);
    }

    @Test
    public void pointsWrapOntoTheNextRowAtTheScreenEdge() {
        int size = 30;
        int spacing = 16;
        int screenWidth = 200;
        // Sitting close enough to the right edge that another point will not fit.
        int[] wrapped =
                ClickPointLayout.nextPosition(true, 160, 16, size, spacing, screenWidth, 1920);

        assertEquals("wraps back to the left margin", spacing, wrapped[0]);
        assertEquals("drops one row down", 16 + size + spacing, wrapped[1]);
    }

    @Test
    public void wrappingReturnsToTheTopWhenOutOfVerticalRoom() {
        int size = 30;
        int spacing = 16;
        int[] wrapped = ClickPointLayout.nextPosition(true, 160, 180, size, spacing, 200, 200);

        assertArrayEquals(new int[] {spacing, spacing}, wrapped);
    }

    @Test
    public void everyPointOfAFullRowStaysOnScreen() {
        int size = 30;
        int spacing = 16;
        int screenWidth = 400;
        int screenHeight = 800;

        boolean hasPrevious = false;
        int x = 0;
        int y = 0;
        for (int i = 0; i < 12; i++) {
            int[] position =
                    ClickPointLayout.nextPosition(
                            hasPrevious, x, y, size, spacing, screenWidth, screenHeight);
            x = position[0];
            y = position[1];
            hasPrevious = true;

            assertTrue("point " + i + " ran off the right edge", x + size <= screenWidth);
            assertTrue("point " + i + " ran off the bottom", y + size <= screenHeight);
            assertTrue("point " + i + " has a negative coordinate", x >= 0 && y >= 0);
        }
    }
}
