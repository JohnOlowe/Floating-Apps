package damjay.floating.projects.bluetooth;

import static damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsCallback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Exercises the Bluetooth wire protocol over plain in-memory streams.
 *
 * <p>No device, emulator or Bluetooth radio involved: the protocol is just bytes, so both ends
 * can be driven from an ordinary JVM unit test.
 */
public class BluetoothOperationsTest {

    /** Runs callbacks inline so assertions can be made straight after the call. */
    private static final BluetoothOperations.CallbackDispatcher DIRECT = Runnable::run;

    private static final InputStream NO_INPUT = new ByteArrayInputStream(new byte[0]);

    private static class Event {
        final byte type;
        final Object value;

        Event(byte type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private static class Recorder implements BluetoothOperationsCallback {
        final List<Event> events = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onSuccess(byte type, Object returnValue) {
            events.add(new Event(type, returnValue));
        }

        @Override
        public void onError(Throwable t) {
            errors.add(t);
        }
    }

    /** Encodes whatever {@code writer} sends and returns the raw bytes that hit the wire. */
    private byte[] encode(Writer writer) {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BluetoothOperations sender = new BluetoothOperations(NO_INPUT, sink, DIRECT);
        writer.write(sender, new Recorder());
        return sink.toByteArray();
    }

    /** Feeds {@code wire} through the read loop and returns everything the receiver saw. */
    private Recorder decode(byte[] wire) {
        Recorder recorder = new Recorder();
        BluetoothOperations receiver =
                new BluetoothOperations(
                        new ByteArrayInputStream(wire), new ByteArrayOutputStream(), DIRECT);
        receiver.readSynchronously(recorder);
        return recorder;
    }

    private interface Writer {
        void write(BluetoothOperations operations, BluetoothOperationsCallback callback);
    }

    /**
     * The regression test for the bug that broke the whole feature: every value read off the
     * wire used to be reported as TYPE_TEXT regardless of what it actually was, so the clicker
     * commands (sent as TYPE_BYTE) fell through the receiver's switch and were dropped.
     */
    @Test
    public void byteIsReceivedAsAByteNotAsText() {
        Recorder received = decode(encode((ops, cb) -> ops.write((byte) 7, cb)));

        assertEquals(1, received.events.size());
        assertEquals("a byte must arrive tagged as TYPE_BYTE", TYPE_BYTE, received.events.get(0).type);
        assertEquals((byte) 7, received.events.get(0).value);
    }

    @Test
    public void clickerCommandsSurviveTheRoundTrip() {
        // -1 (add point) and -2 (delete point) are the two control commands; being negative they
        // are also the values most likely to be mangled by a sloppy byte/int conversion.
        for (byte command : new byte[] {-1, -2, 1, 2, 9, Byte.MIN_VALUE, Byte.MAX_VALUE}) {
            Recorder received = decode(encode((ops, cb) -> ops.write(command, cb)));
            assertEquals(1, received.events.size());
            assertEquals(TYPE_BYTE, received.events.get(0).type);
            assertEquals(command, received.events.get(0).value);
        }
    }

    @Test
    public void everyPrimitiveKeepsItsOwnType() {
        Recorder received = decode(encode((ops, cb) -> {
            ops.write("hello", cb);
            ops.write((byte) 1, cb);
            ops.write((short) 2, cb);
            ops.write('c', cb);
            ops.write(4, cb);
            ops.write(5L, cb);
            ops.write(6.5f, cb);
            ops.write(7.5d, cb);
        }));

        byte[] expectedTypes = {
            TYPE_TEXT, TYPE_BYTE, TYPE_SHORT, TYPE_CHAR, TYPE_INT, TYPE_LONG, TYPE_FLOAT, TYPE_DOUBLE
        };
        Object[] expectedValues = {"hello", (byte) 1, (short) 2, 'c', 4, 5L, 6.5f, 7.5d};

        assertEquals(expectedTypes.length, received.events.size());
        for (int i = 0; i < expectedTypes.length; i++) {
            assertEquals("type at index " + i, expectedTypes[i], received.events.get(i).type);
            assertEquals("value at index " + i, expectedValues[i], received.events.get(i).value);
        }
    }

    /**
     * The raw-content writer used to omit the two byte length prefix that the reader consumes
     * with readUnsignedShort(), so the first payload byte was read as the length and the stream
     * desynchronised from then on.
     */
    @Test
    public void rawContentIsLengthPrefixed() {
        byte[] payload = {10, 20, 30, 40};
        byte[] wire = encode((ops, cb) -> ops.write(payload, cb));

        assertEquals("type byte + 2 byte length + payload", 1 + 2 + payload.length, wire.length);
        assertEquals(TYPE_RAW_C0NTENT, wire[0]);
        assertEquals(0, wire[1]);
        assertEquals(payload.length, wire[2]);

        Recorder received = decode(wire);
        assertEquals(1, received.events.size());
        assertEquals(TYPE_RAW_C0NTENT, received.events.get(0).type);
        assertArrayEquals(payload, (byte[]) received.events.get(0).value);
    }

    @Test
    public void rawContentDoesNotDesynchroniseTheStream() {
        // A payload followed by a command: if the length prefix were wrong the command would be
        // misread as part of the payload.
        Recorder received = decode(encode((ops, cb) -> {
            ops.write(new byte[] {1, 2, 3}, cb);
            ops.write((byte) -1, cb);
        }));

        assertEquals(2, received.events.size());
        assertEquals(TYPE_RAW_C0NTENT, received.events.get(0).type);
        assertEquals(TYPE_BYTE, received.events.get(1).type);
        assertEquals((byte) -1, received.events.get(1).value);
    }

    @Test
    public void exitIsForwarded() {
        Recorder received = decode(encode((ops, cb) -> ops.writeExit(cb)));

        assertEquals(1, received.events.size());
        assertEquals(TYPE_EXIT, received.events.get(0).type);
    }

    /**
     * A disconnect used to hit {@code continue}, spinning the reader thread at 100% CPU forever.
     * The timeout is the assertion: a regression here hangs rather than fails.
     */
    @Test(timeout = 5000)
    public void disconnectStopsTheReadLoopAndReportsEof() {
        Recorder received = decode(new byte[0]);

        assertTrue(received.events.isEmpty());
        assertEquals(1, received.errors.size());
        assertTrue(
                "expected EOFException, got " + received.errors.get(0),
                received.errors.get(0) instanceof EOFException);
    }

    @Test(timeout = 5000)
    public void disconnectMidStreamIsReportedAfterEarlierValues() {
        byte[] good = encode((ops, cb) -> ops.write((byte) 3, cb));
        Recorder received = decode(good);

        assertEquals(1, received.events.size());
        assertEquals((byte) 3, received.events.get(0).value);
        assertEquals(1, received.errors.size());
    }

    @Test(timeout = 5000)
    public void unknownTypeIsReportedAsAnError() {
        Recorder received = decode(new byte[] {99});

        assertTrue(received.events.isEmpty());
        assertEquals(1, received.errors.size());
    }

    @Test
    public void writeReportsSuccessToItsCaller() {
        Recorder sent = new Recorder();
        BluetoothOperations sender =
                new BluetoothOperations(NO_INPUT, new ByteArrayOutputStream(), DIRECT);
        sender.write((byte) 1, sent);

        assertEquals(1, sent.events.size());
        assertEquals(TYPE_SUCCESS, sent.events.get(0).type);
        assertTrue(sent.errors.isEmpty());
    }

    @Test
    public void writeFailureIsReportedAsAnError() {
        OutputStream broken =
                new OutputStream() {
                    @Override
                    public void write(int b) throws java.io.IOException {
                        throw new java.io.IOException("device went away");
                    }
                };
        Recorder sent = new Recorder();
        new BluetoothOperations(NO_INPUT, broken, DIRECT).write((byte) 1, sent);

        assertTrue(sent.events.isEmpty());
        assertEquals(1, sent.errors.size());
    }
}
