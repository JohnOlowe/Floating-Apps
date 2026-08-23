package damjay.floating.projects.timer;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.utils.ViewsUtils;

/**
 * Floating timer service providing stopwatch and timer functionality.
 * Displays over other apps with minimized and expanded views.
 */
public class TimerService extends Service implements Runnable {

    private View view;
    private View collapsedTimer;
    private View expandedTimer;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;

    // Stopwatch state
    private boolean stopWatchActive = false;
    private boolean stopWatchCounting = false;
    private long stopWatchStartTime;
    private long currentStopWatchTime = 0;
    private Button stopWatchButton;
    private final Handler handler = new Handler();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        view = LayoutInflater.from(this).inflate(R.layout.service_timer, (ViewGroup) null);
        initializeViews();
        setOnClickListeners();
        ViewsUtils.addTouchListener(view,
                ViewsUtils.getViewTouchListener(this, view, windowManager, layoutParams),
                true, true, (Class<?>) null);
    }

    private void initializeViews() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        collapsedTimer = view.findViewById(R.id.collapsedTimer);
        expandedTimer = view.findViewById(R.id.expandedTimer);
        layoutParams = ViewsUtils.getFloatingLayoutParams();
        windowManager.addView(view, layoutParams);
        hideInternalViews();
        view.findViewById(R.id.select_timer_mode).setVisibility(View.VISIBLE);
    }

    private void hideInternalViews() {
        view.findViewById(R.id.select_timer_mode).setVisibility(View.GONE);
        view.findViewById(R.id.new_timer_expanded).setVisibility(View.GONE);
        view.findViewById(R.id.repeating_timer_expanded).setVisibility(View.GONE);
        view.findViewById(R.id.counting_timer_view).setVisibility(View.GONE);
    }

    private void setOnClickListeners() {
        view.findViewById(R.id.launch_app).setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        view.findViewById(R.id.minimizeTimer).setOnClickListener(v -> {
            expandedTimer.setVisibility(View.GONE);
            collapsedTimer.setVisibility(View.VISIBLE);
        });
        view.findViewById(R.id.closeTimer).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.minimizeTimer).callOnClick();
        collapsedTimer.setOnClickListener(v -> {
            expandedTimer.setVisibility(View.VISIBLE);
            collapsedTimer.setVisibility(View.GONE);
        });

        stopWatchButton = (Button) view.findViewById(R.id.start_stop_watch);
        stopWatchButton.setOnClickListener(v -> {
            String currentText = stopWatchButton.getText().toString();
            if (currentText.equals(getString(R.string.start))) {
                stopWatchStartTime = System.currentTimeMillis();
                stopWatchButton.setText(R.string.pause);
                ((Button) view.findViewById(R.id.back_to_timer_mode)).setText(R.string.stop);
                stopWatchCounting = true;
                stopWatchActive = true;
                new Thread(this).start();
            } else if (currentText.equals(getString(R.string.pause))) {
                stopWatchCounting = false;
                stopWatchButton.setText(R.string.play);
            } else if (currentText.equals(getString(R.string.play))) {
                stopWatchStartTime = System.currentTimeMillis() - currentStopWatchTime;
                stopWatchCounting = true;
                stopWatchButton.setText(R.string.pause);
            }
        });

        view.findViewById(R.id.back_to_timer_mode).setOnClickListener(v -> {
            if (((Button) v).getText().toString().equals(getString(R.string.stop))) {
                stopWatchActive = false;
                ((Button) v).setText(R.string.cancel);
            } else {
                hideInternalViews();
                view.findViewById(R.id.select_timer_mode).setVisibility(View.VISIBLE);
            }
        });

        view.findViewById(R.id.new_timer).setOnClickListener(getShowClickListener(R.id.new_timer_expanded));
        view.findViewById(R.id.schedule_timer).setOnClickListener(getShowClickListener(R.id.repeating_timer_expanded));
        view.findViewById(R.id.cancel_repeating_timer).setOnClickListener(getShowClickListener(R.id.select_timer_mode));
    }

    private View.OnClickListener getShowClickListener(int visibleViewId) {
        return v -> {
            hideInternalViews();
            view.findViewById(visibleViewId).setVisibility(View.VISIBLE);
        };
    }

    @Override
    public void run() {
        while (stopWatchActive) {
            if (stopWatchCounting) {
                handler.post(() -> {
                    TextView textView = view.findViewById(R.id.stop_watch_time);
                    long currentTime = System.currentTimeMillis() - stopWatchStartTime;
                    currentStopWatchTime = currentTime;
                    textView.setText(formatTime(currentTime));
                });
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                // Interrupted during stopwatch
            }
        }
    }

    private String formatTime(long millis) {
        return (millis / 3600000) + ":"
                + ((millis % 3600000) / 60000) + ":"
                + ((millis % 60000) / 1000)
                + "," + (millis % 1000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && view != null) {
            windowManager.removeView(view);
        }
    }
}
