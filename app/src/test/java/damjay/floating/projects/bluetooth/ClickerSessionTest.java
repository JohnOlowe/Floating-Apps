package damjay.floating.projects.bluetooth;

import static damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import damjay.floating.projects.autoclicker.ClickPointLayout;
import damjay.floating.projects.autoclicker.activity.ClickerActivity;
import damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsCallback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Simulates a whole controller/clicker session without any Bluetooth hardware.
 *
 * <p>One {@link BluetoothOperations} plays the controller phone and writes commands; a second one
 * plays the phone running the accessibility service, decodes them, and drives a stand-in for the
 * on-screen click points. Everything the service does that is not Android UI -- deciding which
 * point a button refers to and where to tap it -- is real production code.
 */
public class ClickerSessionTest {

    private static final BluetoothOperations.CallbackDispatcher DIRECT = Runnable::run;
    private static final InputStream NO_INPUT = new ByteArrayInputStream(new byte[0]);

    private static final byte ADD = ClickerActivity.CLICKER_ADD_POINT;
    private static final byte DELETE = ClickerActivity.CLICKER_DELETE_POINT;

    private static final int POINT_SIZE = 30;
    private static final int SPACING = 16;
    private static final int SCREEN_WIDTH = 1080;
    private static final int SCREEN_HEIGHT = 1920;

    /** Stand-in for the accessibility service: keeps the points and records the taps. */
    private static class FakeClicker implements BluetoothOperationsCallback {
        final List<int[]> points = new ArrayList<>();
        final List<int[]> taps = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onSuccess(byte type, Object value) {
            if (type != TYPE_BYTE) return;
            byte command = (Byte) value;
            if (command == ADD) {
                addPoint();
            } else if (command == DELETE) {
                if (!points.isEmpty()) points.remove(points.size() - 1);
            } else {
                int index = ClickPointLayout.indexForButton(command);
                if (ClickPointLayout.isValidIndex(index, points.size())) {
                    int[] point = points.get(index);
                    taps.add(
                            new int[] {
                                ClickPointLayout.centerOf(point[0], POINT_SIZE),
                                ClickPointLayout.centerOf(point[1], POINT_SIZE)
                            });
                }
            }
        }

        private void addPoint() {
            boolean hasPrevious = !points.isEmpty();
            int lastX = hasPrevious ? points.get(points.size() - 1)[0] : 0;
            int lastY = hasPrevious ? points.get(points.size() - 1)[1] : 0;
            points.add(
                    ClickPointLayout.nextPosition(
                            hasPrevious, lastX, lastY, POINT_SIZE, SPACING,
                            SCREEN_WIDTH, SCREEN_HEIGHT));
        }

        @Override
        public void onError(Throwable t) {
            errors.add(t);
        }
    }

    /** Plays a controller script and returns the state of the receiving side afterwards. */
    private FakeClicker runSession(byte... commands) {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        BluetoothOperations controller = new BluetoothOperations(NO_INPUT, wire, DIRECT);
        BluetoothOperationsCallback ignored =
                new BluetoothOperationsCallback() {
                    @Override
                    public void onSuccess(byte type, Object value) {}

                    @Override
                    public void onError(Throwable t) {
                        throw new AssertionError("controller failed to send", t);
                    }
                };
        for (byte command : commands) {
            controller.write(command, ignored);
        }

        FakeClicker clicker = new FakeClicker();
        new BluetoothOperations(
                        new ByteArrayInputStream(wire.toByteArray()),
                        new ByteArrayOutputStream(),
                        DIRECT)
                .readSynchronously(clicker);
        return clicker;
    }

    @Test
    public void addingPointsFromTheControllerCreatesThemOnTheOtherDevice() {
        FakeClicker clicker = runSession(ADD, ADD, ADD);

        assertEquals(3, clicker.points.size());
    }

    @Test
    public void removingAPointFromTheControllerRemovesItOnTheOtherDevice() {
        FakeClicker clicker = runSession(ADD, ADD, ADD, DELETE);

        assertEquals(2, clicker.points.size());
    }

    @Test
    public void removingFromAnEmptyScreenIsHarmless() {
        FakeClicker clicker = runSession(DELETE, DELETE);

        assertEquals(0, clicker.points.size());
        assertTrue(clicker.taps.isEmpty());
    }

    /** End to end version of the off-by-one fix, across the wire. */
    @Test
    public void buttonOneTapsTheFirstPointAndButtonTwoTheSecond() {
        FakeClicker clicker = runSession(ADD, ADD, (byte) 1, (byte) 2);

        assertEquals(2, clicker.taps.size());
        int[] firstPoint = clicker.points.get(0);
        int[] secondPoint = clicker.points.get(1);
        assertArrayEqualsAsPoint(
                ClickPointLayout.centerOf(firstPoint[0], POINT_SIZE),
                ClickPointLayout.centerOf(firstPoint[1], POINT_SIZE),
                clicker.taps.get(0));
        assertArrayEqualsAsPoint(
                ClickPointLayout.centerOf(secondPoint[0], POINT_SIZE),
                ClickPointLayout.centerOf(secondPoint[1], POINT_SIZE),
                clicker.taps.get(1));
    }

    @Test
    public void theLastPointIsReachable() {
        // Three points, press button 3: this is the press that used to be silently ignored.
        FakeClicker clicker = runSession(ADD, ADD, ADD, (byte) 3);

        assertEquals(1, clicker.taps.size());
        int[] lastPoint = clicker.points.get(2);
        assertArrayEqualsAsPoint(
                ClickPointLayout.centerOf(lastPoint[0], POINT_SIZE),
                ClickPointLayout.centerOf(lastPoint[1], POINT_SIZE),
                clicker.taps.get(0));
    }

    @Test
    public void pressingAButtonWithNoPointsDoesNothing() {
        FakeClicker clicker = runSession((byte) 1, (byte) 2);

        assertTrue(clicker.taps.isEmpty());
    }

    @Test
    public void pressingAButtonBeyondTheLastPointDoesNothing() {
        FakeClicker clicker = runSession(ADD, (byte) 2, (byte) 9);

        assertEquals(1, clicker.points.size());
        assertTrue(clicker.taps.isEmpty());
    }

    @Test
    public void aLongSessionStaysInSync() {
        FakeClicker clicker =
                runSession(ADD, ADD, (byte) 1, ADD, (byte) 3, DELETE, (byte) 2, ADD, (byte) 3);

        assertEquals(3, clicker.points.size());
        assertEquals("every button press should have produced exactly one tap", 4, clicker.taps.size());
    }

    private static void assertArrayEqualsAsPoint(int expectedX, int expectedY, int[] actual) {
        assertEquals("x", expectedX, actual[0]);
        assertEquals("y", expectedY, actual[1]);
    }
}
