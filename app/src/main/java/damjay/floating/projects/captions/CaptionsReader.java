package damjay.floating.projects.captions;

import android.os.Handler;
import java.util.ArrayList;

public class CaptionsReader implements Runnable {
    public static final int PAUSED = 0;
    public static final int PLAYING = 1;
    private long captionTime;
    private final CaptionsCallback captionsCallback;
    private long startTime;
    private int playMode = PAUSED;
    private boolean isDisplaying = false;
    private int currentCaptionIndex = 0;
    public final Handler handler = new Handler();

    public interface CaptionsCallback {
        void displayCaption(String str);
    }

    public CaptionsReader(String captionsText, CaptionsCallback captionsCallback) {
        CaptionElement.initializeCaptionElements(captionsText);
        this.captionsCallback = captionsCallback;
    }

    public void fastBackward() {}

    public void fastForward() {}

    public void gotoPreviousCaption() {
        if (!isDisplaying || currentCaptionIndex == 0) {
            return;
        }
        CaptionElement prevCaptionElement = CaptionElement.getCaptionAtIndex(Math.max(1, currentCaptionIndex - 1));
        if (prevCaptionElement != null) {
            startTime = System.currentTimeMillis() - prevCaptionElement.startTime;
        }
        if (!isPlaying()) {
            play();
        }
    }

    public void gotoNextCaption() {
        if (!isDisplaying) {
            return;
        }
        CaptionElement nextCaptionElement = CaptionElement.getCaptionAtIndex(currentCaptionIndex + 1);
        if (nextCaptionElement != null) {
            startTime = System.currentTimeMillis() - nextCaptionElement.startTime;
        }
        if (!isPlaying()) {
            play();
        }
    }

    public void play() {
        startTime = System.currentTimeMillis() - captionTime;
        setPlayMode(PLAYING);
    }

    public void pause() {
        setPlayMode(PAUSED);
        captionTime = System.currentTimeMillis() - startTime;
    }

    public boolean isPlaying() {
        return playMode == PLAYING;
    }

    private void setPlayMode(int playMode) {
        this.playMode = playMode;
    }

    public void startDisplaying() {
        isDisplaying = true;
        startTime = System.currentTimeMillis();
        new Thread(this).start();
    }

    public boolean isDisplaying() {
        return isDisplaying;
    }

    public String getCaptions(long time) {
        CaptionElement correspondingCaption = CaptionElement.getCorrespondingCaption(time);
        if (correspondingCaption != null) {
            currentCaptionIndex = correspondingCaption.getCaptionIndex();
        }
        return correspondingCaption == null ? "" : correspondingCaption.getCaptionText();
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
                }
            }
        }
    }

    static class CaptionElement {
        public static final int READING_CAPTIONS_TEXT = 0;
        public static final int READING_TIME_RANGE = 1;
        public static final int IDLE_MODE = 2;
        public static ArrayList<CaptionElement> captionElements;
        private String captionText;
        private long endTime;
        private final int index;
        private long startTime;

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

        public static CaptionElement getCorrespondingCaption(long time) {
            return getCaptionAtIndex(getCorrespondingCaptionIndex(time));
        }

        private static int getCorrespondingCaptionIndex(long time) {
            int beginIndex = 0;
            int endIndex = captionElements.size() - 1;

            // Binary search to narrow down the range
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

        private static long parseTimestamp(String rangeLine, boolean isStart) {
            int arrowIndex = rangeLine.indexOf("-->");
            String raw = isStart ? rangeLine.substring(0, arrowIndex)
                                 : rangeLine.substring(arrowIndex + 3);
            return timestampToMillis(stripExtra(raw));
        }

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
            return (totalSeconds * 1000) + Integer.parseInt(millisPart);
        }

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
