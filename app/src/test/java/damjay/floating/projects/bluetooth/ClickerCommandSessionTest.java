package damjay.floating.projects.bluetooth;

import static damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import damjay.floating.projects.autoclicker.ClickerCommand;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Verifies the extended text commands (@MOVE / @SWIPE / @SIZE) cross the wire as TYPE_TEXT and
 * decode into exactly what the accessibility service acts on. Pure JVM, no Android.
 */
public class ClickerCommandSessionTest {

    private static final BluetoothOperations.CallbackDispatcher DIRECT = Runnable::run;
    private static final InputStream NO_INPUT = new ByteArrayInputStream(new byte[0]);

    /** Collects the decoded text commands the way the service's onSuccess(TYPE_TEXT) does. */
    private static class CommandCollector implements BluetoothOperationsCallback {
        final List<ClickerCommand.Parsed> commands = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onSuccess(byte type, Object value) {
            if (type == TYPE_TEXT && ClickerCommand.isCommand((String) value)) {
                commands.add(ClickerCommand.parse((String) value));
            }
        }

        @Override
        public void onError(Throwable t) {
            // Reaching the end of the in-memory stream is reported as an EOFException, which is
            // the normal end of the conversation rather than a failure.
            if (!(t instanceof EOFException)) {
                errors.add(t);
            }
        }
    }

    /** Encodes the text commands on one end and reads them on the other. */
    private CommandCollector runTextSession(String... commands) {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        BluetoothOperations controller = new BluetoothOperations(NO_INPUT, wire, DIRECT);
        BluetoothOperationsCallback ignoreErrors =
                new BluetoothOperationsCallback() {
                    @Override
                    public void onSuccess(byte type, Object value) {}

                    @Override
                    public void onError(Throwable t) {
                        throw new AssertionError("send failed", t);
                    }
                };
        for (String command : commands) {
            controller.write(command, ignoreErrors);
        }

        CommandCollector collector = new CommandCollector();
        new BluetoothOperations(
                        new ByteArrayInputStream(wire.toByteArray()),
                        new ByteArrayOutputStream(),
                        DIRECT)
                .readSynchronously(collector);
        return collector;
    }

    @Test
    public void moveCommandsTravelAsTextAndKeepTheirCoordinates() {
        CommandCollector collector =
                runTextSession(
                        ClickerCommand.size(1080, 1920),
                        ClickerCommand.move(0, 500, 600),
                        ClickerCommand.move(1, 200, 900));

        assertTrue(collector.errors.isEmpty());
        assertEquals(3, collector.commands.size());

        ClickerCommand.Parsed size = collector.commands.get(0);
        assertTrue(size.isSize());
        assertEquals(1080, size.args[0]);
        assertEquals(1920, size.args[1]);

        ClickerCommand.Parsed firstMove = collector.commands.get(1);
        assertTrue(firstMove.isMove());
        assertEquals(0, firstMove.args[0]);
        assertEquals(500, firstMove.args[1]);
        assertEquals(600, firstMove.args[2]);

        ClickerCommand.Parsed secondMove = collector.commands.get(2);
        assertEquals(1, secondMove.args[0]);
        assertEquals(200, secondMove.args[1]);
        assertEquals(900, secondMove.args[2]);
    }

    @Test
    public void swipeCommandsTravelWithBothEndpointsAndDuration() {
        CommandCollector collector =
                runTextSession(ClickerCommand.swipe(515, 615, 215, 915, 300));

        assertTrue(collector.errors.isEmpty());
        assertEquals(1, collector.commands.size());
        ClickerCommand.Parsed swipe = collector.commands.get(0);
        assertTrue(swipe.isSwipe());
        assertEquals(515, swipe.args[0]);
        assertEquals(615, swipe.args[1]);
        assertEquals(215, swipe.args[2]);
        assertEquals(915, swipe.args[3]);
        assertEquals(300, swipe.args[4]);
    }
}
