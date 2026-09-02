package damjay.floating.projects.autoclicker;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The extended clicker command protocol.
 *
 * <p>The original protocol is a single signed byte per command: {@code -1} adds a point,
 * {@code -2} removes one, and {@code 1..127} taps point <em>n</em>. That leaves no room for
 * coordinates (screens are far bigger than 127&nbsp;px) or for anything beyond a tap.
 *
 * <p>Rather than spend more of the tiny byte range, every new command is sent as a
 * {@code TYPE_TEXT} frame whose body starts with {@link #COMMAND_PREFIX} ('@'). The rest is a
 * tiny comma-separated instruction set:
 *
 * <ul>
 *   <li>{@code @SIZE,<width>,<height>} -- the service announces its real screen size when a
 *       session starts, so the controller can scale its layout to it</li>
 *   <li>{@code @MOVE,<index>,<x>,<y>} -- move point {@code index} (0-based) so its top-left is
 *       at the absolute screen coordinate (x, y)</li>
 *   <li>{@code @SWIPE,<fromX>,<fromY>,<toX>,<toY>,<durationMs>} -- swipe between two points</li>
 * </ul>
 *
 * Coordinates are the phone's absolute screen pixels, matching what
 * {@code dispatchGesture} expects. The class is deliberately Android-free so the wire format
 * can be verified by plain JVM unit tests.
 */
public final class ClickerCommand {

    private ClickerCommand() {}

    /** Every extended command frame begins with this character; plain taps/add/remove never do. */
    public static final char COMMAND_PREFIX = '@';

    public static final String SIZE = "SIZE";
    public static final String MOVE = "MOVE";
    public static final String SWIPE = "SWIPE";

    /** Builds a {@code @SIZE} command body. */
    public static String size(int width, int height) {
        return COMMAND_PREFIX + SIZE + "," + width + "," + height;
    }

    /** Builds a {@code @MOVE} command body. */
    public static String move(int index, int x, int y) {
        return COMMAND_PREFIX + MOVE + "," + index + "," + x + "," + y;
    }

    /** Builds a {@code @SWIPE} command body. */
    public static String swipe(int fromX, int fromY, int toX, int toY, long durationMs) {
        return COMMAND_PREFIX + SWIPE + "," + fromX + "," + fromY + "," + toX + "," + toY + ","
                + durationMs;
    }

    /**
     * Encodes a command body the way {@code BluetoothOperations.write(String)} puts it on the
     * wire: a {@code TYPE_TEXT} tag byte, the two-byte UTF length, then the UTF-8 bytes.
     */
    public static byte[] encodeTextFrame(String body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] utf = body.getBytes(StandardCharsets.UTF_8);
        out.write(0); // TYPE_TEXT
        out.write((utf.length >> 8) & 0xFF);
        out.write(utf.length & 0xFF);
        out.write(utf, 0, utf.length);
        return out.toByteArray();
    }

    /** True when a received text frame is one of our extended commands rather than chatter. */
    public static boolean isCommand(String text) {
        return text != null && text.length() > 1 && text.charAt(0) == COMMAND_PREFIX;
    }

    /** Parsed command. {@code kind} is {@link #MOVE} or {@link #SWIPE}; numbers are in {@code args}. */
    public static final class Parsed {
        public final String kind;
        public final long[] args;

        Parsed(String kind, long[] args) {
            this.kind = kind;
            this.args = args;
        }

        public boolean isSize() {
            return SIZE.equals(kind);
        }

        public boolean isMove() {
            return MOVE.equals(kind);
        }

        public boolean isSwipe() {
            return SWIPE.equals(kind);
        }
    }

    /**
     * Parses a command body.
     *
     * @throws IllegalArgumentException for anything that is not a well-formed command, so a
     *     malformed frame can be ignored instead of crashing the reader thread
     */
    public static Parsed parse(String text) {
        if (!isCommand(text)) {
            throw new IllegalArgumentException("not a clicker command: " + text);
        }
        String[] parts = text.substring(1).split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("malformed clicker command: " + text);
        }
        String kind = parts[0];
        long[] args = new long[parts.length - 1];
        try {
            for (int i = 1; i < parts.length; i++) {
                args[i - 1] = Long.parseLong(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("non-numeric argument in: " + text, e);
        }
        if (SIZE.equals(kind) && args.length != 2) {
            throw new IllegalArgumentException("SIZE needs width,height: " + text);
        }
        if (MOVE.equals(kind) && args.length != 3) {
            throw new IllegalArgumentException("MOVE needs index,x,y: " + text);
        }
        if (SWIPE.equals(kind) && args.length != 5) {
            throw new IllegalArgumentException("SWIPE needs x1,y1,x2,y2,duration: " + text);
        }
        if (!SIZE.equals(kind) && !MOVE.equals(kind) && !SWIPE.equals(kind)) {
            throw new IllegalArgumentException("unknown clicker command: " + kind);
        }
        return new Parsed(kind, args);
    }
}
