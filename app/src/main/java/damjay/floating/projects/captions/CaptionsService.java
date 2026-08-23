package damjay.floating.projects.captions;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.utils.ViewsUtils;

public class CaptionsService extends Service {
    public static final String EXTRA_CAPTIONS = "captions";
    public static final String EXTRA_FILE_NAME = "fileName";
    public static final String EXTRA_TEXT_SIZE = "textSize";
    public static final String EXTRA_TEXT_COLOR = "textColor";

    private View view;
    private View collapsed;
    private View settings;
    private TextView captionsText;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private CaptionsReader captionsReader;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("InflateParams")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (view == null) initializeView();
        if (intent != null && intent.hasExtra(EXTRA_CAPTIONS)) {
            String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            ((TextView) view.findViewById(R.id.captions_file_name)).setText(fileName == null ? "" : fileName);
            captionsText.setTextSize(intent.getIntExtra(EXTRA_TEXT_SIZE, 18));
            captionsText.setTextColor(intent.getIntExtra(EXTRA_TEXT_COLOR, Color.WHITE));
            captionsReader = new CaptionsReader(intent.getStringExtra(EXTRA_CAPTIONS));
            captionsReader.start(text -> {
                captionsText.setText(text);
                captionsText.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }
        return START_STICKY;
    }

    private void initializeView() {
        view = LayoutInflater.from(this).inflate(R.layout.service_captions, null);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = ViewsUtils.getFloatingLayoutParams();
        windowManager.addView(view, layoutParams);
        collapsed = view.findViewById(R.id.collapsed_captions_settings);
        settings = view.findViewById(R.id.captions_settings);
        captionsText = view.findViewById(R.id.captions_text);

        view.findViewById(R.id.launch_app).setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        view.findViewById(R.id.close_captions).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.minimize_captions_settings).setOnClickListener(v -> minimize());
        collapsed.setOnClickListener(v -> maximize());
        watchSeekBar(view.findViewById(R.id.captions_size_seek_bar), view.findViewById(R.id.captions_size_text), captionsText);
        watchColorFields(this, view.findViewById(R.id.red_rgb_field), view.findViewById(R.id.green_rgb_field), view.findViewById(R.id.blue_rgb_field), captionsText);
        ViewsUtils.addTouchListener(view, ViewsUtils.getViewTouchListener(this, view, windowManager, layoutParams), true, true, TextView.class, EditText.class, SeekBar.class, null);
        minimize();
    }

    private void minimize() {
        settings.setVisibility(View.GONE);
        collapsed.setVisibility(View.VISIBLE);
    }

    private void maximize() {
        settings.setVisibility(View.VISIBLE);
        collapsed.setVisibility(View.GONE);
    }

    public static void watchSeekBar(SeekBar seekBar, TextView sizeText, TextView sampleText) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = FloatingCaptionsActivity.MIN_CAPTIONS_TEXT_SIZE + progress;
                sizeText.setText(String.valueOf(size));
                sampleText.setTextSize(size);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        seekBar.setProgress(seekBar.getProgress());
    }

    public static void watchColorFields(android.content.Context context, EditText red, EditText green, EditText blue, TextView sampleText) {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateColor(red, green, blue, sampleText); }
            @Override public void afterTextChanged(Editable s) {}
        };
        red.addTextChangedListener(watcher);
        green.addTextChangedListener(watcher);
        blue.addTextChangedListener(watcher);
        updateColor(red, green, blue, sampleText);
    }

    private static void updateColor(EditText red, EditText green, EditText blue, TextView sampleText) {
        sampleText.setTextColor(Color.rgb(parseColor(red), parseColor(green), parseColor(blue)));
    }

    private static int parseColor(EditText field) {
        try {
            return Math.max(0, Math.min(255, Integer.parseInt(field.getText().toString())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (captionsReader != null) captionsReader.stop();
        if (windowManager != null && view != null) windowManager.removeView(view);
    }
}
