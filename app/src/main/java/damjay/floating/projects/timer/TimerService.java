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
        this.view = LayoutInflater.from(this).inflate(R.layout.service_timer, (ViewGroup) null);
        initializeViews();
        setOnClickListeners();
        View view = this.view;
        ViewsUtils.addTouchListener(view,
                ViewsUtils.getViewTouchListener(this, view, this.windowManager, this.layoutParams), true, true, null);
    }

    private void initializeViews() {
        this.windowManager = (WindowManager) getSystemService("window");
        this.collapsedTimer = this.view.findViewById(R.id.collapsedTimer);
        this.expandedTimer = this.view.findViewById(R.id.expandedTimer);
        WindowManager windowManager = this.windowManager;
        View view = this.view;
        WindowManager.LayoutParams floatingLayoutParams = ViewsUtils.getFloatingLayoutParams();
        this.layoutParams = floatingLayoutParams;
        windowManager.addView(view, floatingLayoutParams);
        hideViews();
    }

    private void hideInternalViews() {
        this.view.findViewById(R.id.select_timer_mode).setVisibility(View.GONE);
        this.view.findViewById(R.id.new_timer_expanded).setVisibility(View.GONE);
        this.view.findViewById(R.id.repeating_timer_expanded).setVisibility(View.GONE);
        this.view.findViewById(R.id.counting_timer_view).setVisibility(View.GONE);
    }

    private void hideViews() {
        hideInternalViews();
        this.view.findViewById(R.id.select_timer_mode).setVisibility(View.VISIBLE);
    }

    private void setOnClickListeners() {
        this.view.findViewById(R.id.launch_app).setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        this.view.findViewById(R.id.minimizeTimer).setOnClickListener((v) -> {
            this.expandedTimer.setVisibility(View.GONE);
            this.collapsedTimer.setVisibility(View.VISIBLE);
        });
        this.view.findViewById(R.id.closeTimer).setOnClickListener(v -> stopSelf());
        this.view.findViewById(R.id.minimizeTimer).callOnClick();
        this.collapsedTimer.setOnClickListener((v) -> {
            this.expandedTimer.setVisibility(View.VISIBLE);
            this.collapsedTimer.setVisibility(View.GONE);
        });
        Button button = (Button) this.view.findViewById(R.id.start_stop_watch);
        this.stopWatchButton = button;
        button.setOnClickListener((button) -> {
            String currentText = this.stopWatchButton.getText().toString();
            if (currentText.equals(getResources().getString(R.string.start))) {
                this.stopWatchStartTime = System.currentTimeMillis();
                this.stopWatchButton.setText(R.string.pause);
                ((Button) this.view.findViewById(R.id.back_to_timer_mode)).setText(R.string.stop);
                this.stopWatchCounting = true;
                this.stopWatchActive = true;
                new Thread(this).start();
                return;
            }
            if (currentText.equals(getResources().getString(R.string.pause))) {
                this.stopWatchCounting = false;
                this.stopWatchButton.setText(R.string.play);
            } else if (currentText.equals(getResources().getString(R.string.play))) {
                this.stopWatchStartTime = System.currentTimeMillis() - this.currentStopWatchTime;
                this.stopWatchCounting = true;
                this.stopWatchButton.setText(R.string.pause);
            }
        });
        this.view.findViewById(R.id.back_to_timer_mode).setOnClickListener((v) -> {
            if (((Button) v).getText().toString().equals(getResources().getString(R.string.stop))) {
                this.stopWatchActive = false;
                ((Button) v).setText(R.string.cancel);
            } else {
                hideInternalViews();
                this.view.findViewById(R.id.select_timer_mode).setVisibility(View.VISIBLE);
            }
        });
        this.view.findViewById(R.id.new_timer).setOnClickListener(getShowClickListener(R.id.new_timer_expanded));
        this.view.findViewById(R.id.schedule_timer)
                .setOnClickListener(getShowClickListener(R.id.repeating_timer_expanded));
        this.view.findViewById(R.id.cancel_repeating_timer)
                .setOnClickListener(getShowClickListener(R.id.select_timer_mode));
    }

    @Override
    public void run() {
        while (this.stopWatchActive) {
            if (this.stopWatchCounting) {
                this.handler.post(() -> {
                    TextView textView = (TextView) this.view.findViewById(R.id.stop_watch_time);
                    long jCurrentTimeMillis = System.currentTimeMillis() - this.stopWatchStartTime;
                    this.currentStopWatchTime = jCurrentTimeMillis;
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
            this.view.findViewById(visibleView).setVisibility(View.VISIBLE);
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.windowManager.removeView(this.view);
    }
}
