package damjay.floating.projects.autoclicker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Verifies the extended clicker command format (the "@-commands") used to move points and
 * swipe between them, and that it matches the TYPE_TEXT wire layout produced by
 * BluetoothOperations. Pure JVM, no Android.
 */
public class ClickerCommandTest {

    @Test
    public void moveCommandCarriesIndexAndAbsoluteCoordinates() {
        String command = ClickerCommand.move(3, 540, 1200);
        assertEquals("@MOVE,3,540,1200", command);
    }

    @Test
    public void swipeCommandCarriesBothEndpointsAndDuration() {
        String command = ClickerCommand.swipe(100, 200, 300, 900, 250);
        assertEquals("@SWIPE,100,200,300,900,250", command);
    }

    @Test
    public void sizeCommandCarriesScreenDimensions() {
        assertEquals("@SIZE,1080,1920", ClickerCommand.size(1080, 1920));
    }

    @Test
    public void parsesMoveCommandsBack() {
        ClickerCommand.Parsed parsed = ClickerCommand.parse("@MOVE,2,77,88");
        assertTrue(parsed.isMove());
        assertFalse(parsed.isSwipe());
        assertEquals(3, parsed.args.length);
        assertEquals(2, parsed.args[0]);
        assertEquals(77, parsed.args[1]);
        assertEquals(88, parsed.args[2]);
    }

    @Test
    public void parsesSwipeCommandsBack() {
        ClickerCommand.Parsed parsed = ClickerCommand.parse("@SWIPE,1,2,3,4,300");
        assertTrue(parsed.isSwipe());
        assertEquals(5, parsed.args.length);
        assertEquals(300, parsed.args[4]);
    }

    @Test
    public void parsesSizeCommandsBack() {
        ClickerCommand.Parsed parsed = ClickerCommand.parse("@SIZE,720,1280");
        assertTrue(parsed.isSize());
        assertEquals(720, parsed.args[0]);
        assertEquals(1280, parsed.args[1]);
    }

    @Test
    public void ordinaryTextIsNotACommand() {
        assertFalse(ClickerCommand.isCommand("hello"));
        assertFalse(ClickerCommand.isCommand("@"));
        assertFalse(ClickerCommand.isCommand(null));
    }

    @Test
    public void malformedCommandsAreRejectedNotThrownAtTheReader() {
        String[] bad = {
            "@MOVE,1",          // too few args
            "@SWIPE,1,2,3,4",   // missing duration
            "@FLY,1,2,3",       // unknown command
            "@MOVE,x,y,z",      // non-numeric
            "plain",            // not a command at all
        };
        for (String candidate : bad) {
            try {
                ClickerCommand.parse(candidate);
                fail("expected " + candidate + " to be rejected");
            } catch (IllegalArgumentException expected) {
                // The service catches this and carries on.
            }
        }
    }

    @Test
    public void encodedFrameMatchesTheTextWireLayout() {
        byte[] frame = ClickerCommand.encodeTextFrame("@MOVE,0,16,16");
        // TYPE_TEXT tag (0), then the 2-byte unsigned UTF length (12), then the body.
        assertEquals(0, frame[0] & 0xFF);
        assertEquals(0, frame[1] & 0xFF);
        assertEquals(12, frame[2] & 0xFF);
        assertEquals("@MOVE,0,16,16", new String(frame, 3, frame.length - 3,
                                                  java.nio.charset.StandardCharsets.UTF_8));
    }
}
