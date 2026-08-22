package damjay.floating.projects.captions;

import android.os.Handler;
import java.util.ArrayList;

public class CaptionsReader implements Runnable {
    public static final int PAUSED = 0;
    public static final int PLAYING = 1;
    private long captionTime;
    private final CaptionsCallback captionsCallback;
    private long startTime;
    private int playMode = 0;
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
        int i;
        if (!isDisplaying || (i = currentCaptionIndex) == 0) {
            return;
        }
        CaptionElement prevCaptionElement = CaptionElement.getCaptionAtIndex(Math.max(1, i - 1));
        startTime =
                prevCaptionElement != null ? System.currentTimeMillis() - prevCaptionElement.startTime : startTime;
        if (!isPlaying()) {
            play();
        }
    }

    public void gotoNextCaption() {
        if (isDisplaying) {
            CaptionElement nextCaptionElement = CaptionElement.getCaptionAtIndex(currentCaptionIndex + 1);
            startTime = nextCaptionElement != null ? System.currentTimeMillis() - nextCaptionElement.startTime
                                                        : startTime;
            if (!isPlaying()) {
                play();
            }
        }
    }

    public void play() {
        startTime = System.currentTimeMillis() - captionTime;
        setPlayMode(1);
    }

    public void pause() {
        setPlayMode(0);
        captionTime = System.currentTimeMillis() - startTime;
    }

    public boolean isPlaying() {
        return playMode == 1;
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
        public static final int IDLE_MODE = 2;
        public static final int READING_CAPTIONS_TEXT = 0;
        public static final int READING_TIME_RANGE = 1;
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
            int counter = 1;
            int readMode = 2;
            CaptionElement captionElement = null;
            StringBuilder captionDisplayText = new StringBuilder();
            for (String str : captionLines) {
                String captionLine = stripExtra(str);
                if (captionLine.length() == 2 && counter == 1
                        && ("" + captionLine.charAt(1)).equals(String.valueOf(counter))) {
                    captionLine = captionLine.substring(1);
                }
                if (readMode == 2 && captionLine.equals(String.valueOf(counter))) {
                    captionElement = new CaptionElement(counter);
                    readMode = 1;
                } else if (readMode == 1) {
                    if (captionLine.contains("-->")) {
                        long startTime = parseForStartTime(captionLine);
                        long endTime = parseForEndTime(captionLine);
                        captionElement.setTimeRange(startTime, endTime);
                        readMode = 0;
                    }
                } else if (readMode == 0) {
                    if (!captionLine.isEmpty()) {
                        captionDisplayText.append(captionLine).append("\n");
                    } else {
                        readMode = 2;
                        captionElement.setCaptionText(stripExtra(captionDisplayText.toString()));
                        captionElements.add(captionElement);
                        captionDisplayText = new StringBuilder();
                        counter++;
                    }
                }
            }
        }

        public static CaptionElement getCorrespondingCaption(long time) {
            return getCaptionAtIndex(getCorrespondingCaptionIndex(time));
        }

        private static int getCorrespondingCaptionIndex(long time) {
            int beginIndex = 0;
            int endIndex = captionElements.size() - 1;
            while (endIndex - beginIndex > 5) {
                int middleIndex = (beginIndex + endIndex) / 2;
                CaptionElement middleCaptionElement = captionElements.get(middleIndex);
                if (middleCaptionElement.startTime <= time) {
                    if (middleCaptionElement.endTime >= time) {
                        return middleIndex + 1;
                    }
                    beginIndex = middleIndex;
                } else {
                    endIndex = middleIndex;
                }
            }
            for (int index = beginIndex; index <= endIndex; index++) {
                CaptionElement captionElement = captionElements.get(index);
                if (captionElement.startTime <= time && captionElement.endTime >= time) {
                    return index + 1;
                }
            }
            return 0;
        }

        public static CaptionElement getCaptionAtIndex(int index) {
            if (index > captionElements.size() || index == 0) {
                return null;
            }
            return captionElements.get(index - 1);
        }

        private static long parseForStartTime(String rangeLine) {
            String startTime = stripExtra(rangeLine.substring(0, rangeLine.indexOf("-->")));
            return captionStringToLong(startTime);
        }

        private static long parseForEndTime(String rangeLine) {
            String endTime = stripExtra(rangeLine.substring(rangeLine.indexOf("-->") + 3));
            return captionStringToLong(endTime);
        }

        private static long captionStringToLong(String startTime) {
            String[] startTimeComponents = startTime.split(":");
            long parsedStartTime = 0;
            for (int i = 0; i < startTimeComponents.length; i++) {
                String component = startTimeComponents[i];
                int commaIndex = component.indexOf(44);
                if (commaIndex > 0) {
                    component = component.substring(0, commaIndex);
                }
                double d = Integer.parseInt(component);
                double dPow = Math.pow(60.0d, (startTimeComponents.length - i) - 1);
                parsedStartTime += (long) (d * dPow);
            }
            int i2 = startTimeComponents.length;
            String lastPart = startTimeComponents[i2 - 1];
            String msPart = lastPart.substring(lastPart.indexOf(44) + 1);
            return (1000 * parsedStartTime) + ((long) Integer.parseInt(msPart));
        }

        private static String stripExtra(String inputString) {
            int start = 0;
            int end = inputString.length();
            while (start < end && isExtraCharacter(inputString.charAt(start))) start++;
            while (end > start && isExtraCharacter(inputString.charAt(end - 1))) end--;
            return inputString.substring(start, end);
        }

        private static boolean isExtraCharacter(char character) {
            return character == '\n' || character == '\r' || character == ' ' || character == 187 || character == 191
                    || character == 239;
        }
    }
}
