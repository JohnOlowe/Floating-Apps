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

public class TimerService extends Service implements Runnable {
    private View collapsedTimer;
    private View expandedTimer;
    private WindowManager.LayoutParams layoutParams;
    private boolean stopWatchActive;
    private Button stopWatchButton;
    private boolean stopWatchCounting;
    private long stopWatchStartTime;
    private View view;
    private WindowManager windowManager;
    private Handler handler = new Handler();
    private long currentStopWatchTime = 0;

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
        View view = view;
        ViewsUtils.addTouchListener(view,
                ViewsUtils.getViewTouchListener(this, view, windowManager, layoutParams), true, true, null);
    }

    private void initializeViews() {
        windowManager = (WindowManager) getSystemService("window");
        collapsedTimer = view.findViewById(R.id.collapsedTimer);
        expandedTimer = view.findViewById(R.id.expandedTimer);
        WindowManager windowManager = windowManager;
        View view = view;
        WindowManager.LayoutParams floatingLayoutParams = ViewsUtils.getFloatingLayoutParams();
        layoutParams = floatingLayoutParams;
        windowManager.addView(view, floatingLayoutParams);
        hideViews();
    }

    private void hideInternalViews() {
        view.findViewById(R.id.select_timer_mode).setVisibility(View.GONE);
        view.findViewById(R.id.new_timer_expanded).setVisibility(View.GONE);
        view.findViewById(R.id.repeating_timer_expanded).setVisibility(View.GONE);
        view.findViewById(R.id.counting_timer_view).setVisibility(View.GONE);
    }

    private void hideViews() {
        hideInternalViews();
        view.findViewById(R.id.select_timer_mode).setVisibility(View.VISIBLE);
    }

    private void setOnClickListeners() {
        view.findViewById(R.id.launch_app).setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        view.findViewById(R.id.minimizeTimer).setOnClickListener((v) -> {
            expandedTimer.setVisibility(View.GONE);
            collapsedTimer.setVisibility(View.VISIBLE);
        });
        view.findViewById(R.id.closeTimer).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.minimizeTimer).callOnClick();
        collapsedTimer.setOnClickListener((v) -> {
            expandedTimer.setVisibility(View.VISIBLE);
            collapsedTimer.setVisibility(View.GONE);
        });
        Button button = (Button) view.findViewById(R.id.start_stop_watch);
        stopWatchButton = button;
        button.setOnClickListener((button) -> {
            String currentText = stopWatchButton.getText().toString();
            if (currentText.equals(getResources().getString(R.string.start))) {
                stopWatchStartTime = System.currentTimeMillis();
                stopWatchButton.setText(R.string.pause);
                ((Button) view.findViewById(R.id.back_to_timer_mode)).setText(R.string.stop);
                stopWatchCounting = true;
                stopWatchActive = true;
                new Thread(this).start();
                return;
            }
            if (currentText.equals(getResources().getString(R.string.pause))) {
                stopWatchCounting = false;
                stopWatchButton.setText(R.string.play);
            } else if (currentText.equals(getResources().getString(R.string.play))) {
                stopWatchStartTime = System.currentTimeMillis() - currentStopWatchTime;
                stopWatchCounting = true;
                stopWatchButton.setText(R.string.pause);
            }
        });
        view.findViewById(R.id.back_to_timer_mode).setOnClickListener((v) -> {
            if (((Button) v).getText().toString().equals(getResources().getString(R.string.stop))) {
                stopWatchActive = false;
                ((Button) v).setText(R.string.cancel);
            } else {
                hideInternalViews();
                view.findViewById(R.id.select_timer_mode).setVisibility(View.VISIBLE);
            }
        });
        view.findViewById(R.id.new_timer).setOnClickListener(getShowClickListener(R.id.new_timer_expanded));
        view.findViewById(R.id.schedule_timer)
                .setOnClickListener(getShowClickListener(R.id.repeating_timer_expanded));
        view.findViewById(R.id.cancel_repeating_timer)
                .setOnClickListener(getShowClickListener(R.id.select_timer_mode));
    }

    @Override
    public void run() {
        while (stopWatchActive) {
            if (stopWatchCounting) {
                handler.post(() -> {
                    TextView textView = (TextView) view.findViewById(R.id.stop_watch_time);
                    long jCurrentTimeMillis = System.currentTimeMillis() - stopWatchStartTime;
                    currentStopWatchTime = jCurrentTimeMillis;
                    textView.setText(millisToString(jCurrentTimeMillis));
                });
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
            }
        }
    }

    private String millisToString(long millis) {
        String timeBuilder = (millis / 3600000) + ":" + ((millis % 3600000) / 60000) + ":" + ((millis % 60000) / 1000)
                + "," + (millis % 1000);
        return timeBuilder;
    }

    private View.OnClickListener getShowClickListener(int visibleView) {
        return (v) -> {
            hideInternalViews();
            view.findViewById(visibleView).setVisibility(View.VISIBLE);
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        windowManager.removeView(view);
    }
}
