package damjay.floating.projects.captions;

import android.os.Handler;

import java.util.ArrayList;

/**
 * Reads and displays subtitle/caption text synchronized with video/content playback.
 * Parses caption text with timestamp ranges and manages playback state.
 */
public class CaptionsReader implements Runnable {

    public static final int PAUSED = 0;
    public static final int PLAYING = 1;

    // Playback state tracking
    private long captionTime;
    private long startTime;
    private int playMode = PAUSED;
    private boolean isDisplaying = false;
    private int currentCaptionIndex = 0;

    // Callback for displaying caption text updates
    private final CaptionsCallback captionsCallback;

    // Handler for posting caption updates to the UI thread
    public final Handler handler = new Handler();

    /** Callback interface for caption display updates */
    public interface CaptionsCallback {
        void displayCaption(String text);
    }

    public CaptionsReader(String captionsText, CaptionsCallback captionsCallback) {
        CaptionElement.initializeCaptionElements(captionsText);
        this.captionsCallback = captionsCallback;
    }

    /** Navigate to previous caption */
    public void fastBackward() {}

    public void fastForward() {}

    public void gotoPreviousCaption() {
        if (!isDisplaying || currentCaptionIndex == 0) {
            return;
        }
        CaptionElement prevCaption = CaptionElement.getCaptionAtIndex(Math.max(1, currentCaptionIndex - 1));
        if (prevCaption != null) {
            startTime = System.currentTimeMillis() - prevCaption.startTime;
        }
        if (!isPlaying()) {
            play();
        }
    }

    /** Navigate to next caption */
    public void gotoNextCaption() {
        if (!isDisplaying) {
            return;
        }
        CaptionElement nextCaption = CaptionElement.getCaptionAtIndex(currentCaptionIndex + 1);
        if (nextCaption != null) {
            startTime = System.currentTimeMillis() - nextCaption.startTime;
        }
        if (!isPlaying()) {
            play();
        }
    }

    /** Resume caption playback */
    public void play() {
        startTime = System.currentTimeMillis() - captionTime;
        setPlayMode(PLAYING);
    }

    /** Pause caption playback */
    public void pause() {
        setPlayMode(PAUSED);
        captionTime = System.currentTimeMillis() - startTime;
    }

    public boolean isPlaying() {
        return playMode == PLAYING;
    }

    private void setPlayMode(int mode) {
        this.playMode = mode;
    }

    /** Start displaying captions */
    public void startDisplaying() {
        isDisplaying = true;
        startTime = System.currentTimeMillis();
        new Thread(this).start();
    }

    public boolean isDisplaying() {
        return isDisplaying;
    }

    /** Get caption text for the given time offset */
    public String getCaptions(long time) {
        CaptionElement caption = CaptionElement.getCorrespondingCaption(time);
        if (caption != null) {
            currentCaptionIndex = caption.getCaptionIndex();
        }
        return caption == null ? "" : caption.getCaptionText();
    }

    @Override
    public void run() {
        String previousText = "";
        while (isDisplaying()) {
            if (isPlaying()) {
                long currentTime = System.currentTimeMillis() - startTime;
                String newText = getCaptions(currentTime);
                if (!newText.equals(previousText)) {
                    previousText = newText;
                    handler.post(() -> captionsCallback.displayCaption(newText));
                }
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    // Interrupted during caption playback
                }
            }
        }
    }

    /** Internal element representing a single caption with time range */
    static class CaptionElement {
        public static final int READING_CAPTIONS_TEXT = 0;
        public static final int READING_TIME_RANGE = 1;
        public static final int IDLE_MODE = 2;

        public static ArrayList<CaptionElement> captionElements;

        private final int index;
        private String captionText;
        private long startTime;
        private long endTime;

        public CaptionElement(int index) {
            this.index = index;
        }

        public void setTimeRange(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public void setCaptionText(String captionText) {
            this.captionText = captionText;
        }

        public String getCaptionText() {
            return captionText;
        }

        public int getCaptionIndex() {
            return index;
        }

        /** Initialize caption elements from raw text */
        public static void initializeCaptionElements(String captionText) {
            captionElements = new ArrayList<>();
            String[] captionLines = captionText.split("\n");
            int captionNumber = 1;
            int readMode = IDLE_MODE;
            CaptionElement currentElement = null;
            StringBuilder captionDisplayText = new StringBuilder();

            for (String line : captionLines) {
                String captionLine = stripExtra(line);

                // Handle BOM-prefixed first caption number
                if (captionLine.length() == 2 && captionNumber == 1
                        && captionLine.charAt(1) == Character.forDigit(captionNumber, 10)) {
                    captionLine = captionLine.substring(1);
                }

                switch (readMode) {
                    case IDLE_MODE:
                        if (captionLine.equals(String.valueOf(captionNumber))) {
                            currentElement = new CaptionElement(captionNumber);
                            readMode = READING_TIME_RANGE;
                        }
                        break;

                    case READING_TIME_RANGE:
                        if (captionLine.contains("-->")) {
                            long start = parseTimestamp(captionLine, true);
                            long end = parseTimestamp(captionLine, false);
                            currentElement.setTimeRange(start, end);
                            readMode = READING_CAPTIONS_TEXT;
                        }
                        break;

                    case READING_CAPTIONS_TEXT:
                        if (!captionLine.isEmpty()) {
                            captionDisplayText.append(captionLine).append("\n");
                        } else {
                            currentElement.setCaptionText(stripExtra(captionDisplayText.toString()));
                            captionElements.add(currentElement);
                            captionDisplayText = new StringBuilder();
                            captionNumber++;
                            readMode = IDLE_MODE;
                        }
                        break;
                }
            }
        }

        /** Find caption corresponding to the given time */
        public static CaptionElement getCorrespondingCaption(long time) {
            return getCaptionAtIndex(getCorrespondingCaptionIndex(time));
        }

        /** Binary search for caption index at given time */
        private static int getCorrespondingCaptionIndex(long time) {
            int beginIndex = 0;
            int endIndex = captionElements.size() - 1;

            // Narrow down with binary search
            while (endIndex - beginIndex > 5) {
                int middleIndex = (beginIndex + endIndex) / 2;
                CaptionElement middle = captionElements.get(middleIndex);
                if (middle.startTime <= time) {
                    if (middle.endTime >= time) {
                        return middleIndex + 1;
                    }
                    beginIndex = middleIndex;
                } else {
                    endIndex = middleIndex;
                }
            }

            // Linear scan the narrowed range
            for (int i = beginIndex; i <= endIndex; i++) {
                CaptionElement element = captionElements.get(i);
                if (element.startTime <= time && element.endTime >= time) {
                    return i + 1;
                }
            }
            return 0;
        }

        public static CaptionElement getCaptionAtIndex(int index) {
            if (index == 0 || index > captionElements.size()) {
                return null;
            }
            return captionElements.get(index - 1);
        }

        /** Parse timestamp from caption range line */
        private static long parseTimestamp(String rangeLine, boolean isStart) {
            int arrowIndex = rangeLine.indexOf("-->");
            String raw = isStart ? rangeLine.substring(0, arrowIndex)
                                 : rangeLine.substring(arrowIndex + 3);
            return timestampToMillis(stripExtra(raw));
        }

        /** Convert timestamp string to milliseconds */
        private static long timestampToMillis(String timestamp) {
            String[] parts = timestamp.split(":");
            long totalSeconds = 0;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                int commaIndex = part.indexOf(',');
                if (commaIndex > 0) {
                    part = part.substring(0, commaIndex);
                }
                long value = Integer.parseInt(part);
                int exponent = (parts.length - i) - 1;
                totalSeconds += value * (long) Math.pow(60, exponent);
            }

            String lastPart = parts[parts.length - 1];
            String millisPart = lastPart.substring(lastPart.indexOf(',') + 1);
            return (totalSeconds * 1000) + Long.parseLong(millisPart);
        }

        /** Strip extra whitespace and special characters */
        private static String stripExtra(String inputString) {
            int start = 0;
            int end = inputString.length();
            while (start < end && isExtraCharacter(inputString.charAt(start))) start++;
            while (end > start && isExtraCharacter(inputString.charAt(end - 1))) end--;
            return inputString.substring(start, end);
        }

        private static boolean isExtraCharacter(char ch) {
            return ch == '\n' || ch == '\r' || ch == ' '
                    || ch == '\u00BB'  // »
                    || ch == '\u00BF'  // ¿
                    || ch == '\uFEFF'; // BOM
        }
    }
}
