package damjay.floating.projects.autoclicker;

/**
 * Pure geometry/index helpers for the floating click points.
 *
 * <p>Deliberately free of any Android dependency so the fiddly parts -- the 1-based button to
 * 0-based point mapping and the grid placement -- can be verified by plain JVM unit tests
 * instead of requiring a device or an emulator.
 */
public final class ClickPointLayout {

    private ClickPointLayout() {}

    /**
     * Maps a button as labelled on the controller (1, 2, 3 ...) to the index of the click point
     * it should tap (0, 1, 2 ...).
     *
     * <p>The original code used the button value directly as the list index, so button "1"
     * tapped the second point and the last point could never be reached.
     */
    public static int indexForButton(int buttonNumber) {
        return buttonNumber - 1;
    }

    /** True when {@code index} addresses an existing point in a list of {@code pointCount}. */
    public static boolean isValidIndex(int index, int pointCount) {
        return index >= 0 && index < pointCount;
    }

    /**
     * Centre of a point whose top-left corner is at {@code coordinate}. Gestures must be
     * dispatched at the centre; the original code tapped the top-left corner.
     */
    public static int centerOf(int coordinate, int pointSizePx) {
        return coordinate + pointSizePx / 2;
    }

    /**
     * Position for the next click point, laid out left-to-right and wrapping onto new rows.
     *
     * @param hasPrevious false when this is the first point being placed
     * @return a two element array, {x, y}
     */
    public static int[] nextPosition(
            boolean hasPrevious,
            int lastX,
            int lastY,
            int pointSizePx,
            int spacingPx,
            int screenWidthPx,
            int screenHeightPx) {
        if (!hasPrevious) {
            // First point near the top-left, offset so it is not hidden under the toolbar.
            return new int[] {spacingPx, spacingPx};
        }

        int maxX = screenWidthPx - pointSizePx;
        int nextX = lastX + pointSizePx + spacingPx;
        if (nextX <= maxX) {
            // Continue on the same row.
            return new int[] {nextX, lastY};
        }

        // Wrap to a new row, and back to the top if we run off the bottom.
        int y = lastY + pointSizePx + spacingPx;
        if (y > screenHeightPx - pointSizePx) y = spacingPx;
        return new int[] {spacingPx, y};
    }
}
