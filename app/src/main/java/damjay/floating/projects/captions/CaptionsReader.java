package damjay.floating.projects.captions;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CaptionsReader {
    private final List<CaptionElement> captions = new ArrayList<>();
    private final Handler handler = new Handler();
    private CaptionsCallback callback;
    private boolean stopped;
    private long startedAt;
    private int index;

    public CaptionsReader(String content) {
        parse(content == null ? "" : content);
    }

    public void start(CaptionsCallback callback) {
        this.callback = callback;
        stopped = false;
        startedAt = System.currentTimeMillis();
        index = 0;
        scheduleNext();
    }

    public void stop() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
    }

    private void scheduleNext() {
        if (stopped || index >= captions.size()) {
            if (callback != null) callback.onFinish();
            return;
        }
        CaptionElement element = captions.get(index++);
        long delay = Math.max(0, element.startMs - (System.currentTimeMillis() - startedAt));
        handler.postDelayed(() -> {
            if (stopped || callback == null) return;
            callback.onCaption(element.text);
            handler.postDelayed(() -> {
                if (!stopped && callback != null) callback.onCaption("");
                scheduleNext();
            }, Math.max(1, element.endMs - element.startMs));
        }, delay);
    }

    private void parse(String content) {
        String[] blocks = content.replace("\r", "").split("\n\n+");
        for (String block : blocks) {
            String[] lines = block.trim().split("\n");
            if (lines.length < 2) continue;
            int timeIndex = lines[0].contains("-->") ? 0 : 1;
            if (lines.length <= timeIndex) continue;
            String[] times = lines[timeIndex].split("-->");
            if (times.length != 2) continue;
            StringBuilder text = new StringBuilder();
            for (int i = timeIndex + 1; i < lines.length; i++) {
                if (text.length() > 0) text.append('\n');
                text.append(lines[i]);
            }
            captions.add(new CaptionElement(parseTime(times[0].trim()), parseTime(times[1].trim()), text.toString()));
        }
    }

    private long parseTime(String time) {
        String[] main = time.replace(',', '.').split(":");
        if (main.length != 3) return 0;
        double seconds = Double.parseDouble(main[2]);
        return (Long.parseLong(main[0]) * 3600000L) + (Long.parseLong(main[1]) * 60000L) + (long) (seconds * 1000);
    }

    public static class CaptionElement {
        public final long startMs;
        public final long endMs;
        public final String text;

        public CaptionElement(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }

        @Override
        public String toString() {
            return String.format(Locale.getDefault(), "%d-%d: %s", startMs, endMs, text);
        }
    }

    public interface CaptionsCallback {
        void onCaption(String text);
        default void onFinish() {}
    }
}
