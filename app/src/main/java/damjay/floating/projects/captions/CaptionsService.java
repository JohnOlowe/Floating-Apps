package damjay.floating.projects.captions;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.utils.ViewsUtils;

/**
 * Service that displays floating caption text over other apps.
 * Supports settings for text size, color, and playback control.
 */
public class CaptionsService extends Service {

    // File and content tracking for captions
    public static String captionsFileName;
    public static String contentCaptions;

    // UI components for captions
    private View captionsView;
    private View captionsSettingsView;
    private View collapsedCaptionsView;
    private TextView captionsTextView;

    // Playback control
    private CaptionsReader captionsReader;
    private boolean settingsShouldPauseCaptions = true;

    // Window management
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeCaptionView();
        initializeFloatingParameters();
        initializeSettingViews();
        setupCaptionTextDisplay();
    }

    /** Initialize the caption view and text component */
    private void initializeCaptionView() {
        captionsView = LayoutInflater.from(this).inflate(R.layout.service_captions, (ViewGroup) null);
        TextView textView = new TextView(this);
        captionsTextView = textView;
        textView.setLayoutParams(new ViewGroup.LayoutParams(
                android.content.res.Resources.getSystem().getDisplayMetrics().widthPixels,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        captionsTextView.setGravity(Gravity.CENTER);
    }

    /** Set up floating window parameters */
    private void initializeFloatingParameters() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = ViewsUtils.getFloatingLayoutParams(false);
    }

    /** Initialize settings views and controls */
    private void initializeSettingViews() {
        captionsSettingsView = captionsView.findViewById(R.id.captions_settings);
        collapsedCaptionsView = captionsView.findViewById(R.id.collapsed_captions_settings);

        String captionContent = contentCaptions;
        if (captionContent != null) {
            captionsReader = new CaptionsReader(captionContent, this::displayCaptionText);
            TextView fileNameView = (TextView) captionsSettingsView.findViewById(R.id.captions_file_name);
            fileNameView.setText(captionsFileName);
        }

        setClickListeners();
        setupSeekBar();
        setupColorFields();
        windowManager.addView(captionsView, layoutParams);
        ViewsUtils.addTouchListener(captionsView,
                ViewsUtils.getViewTouchListener(this, captionsView, windowManager, layoutParams),
                true, true, EditText.class, SeekBar.class, CheckBox.class, null);
        hideSettingsView();
    }

    /** Configure playback and navigation click listeners */
    private void setClickListeners() {
        collapsedCaptionsView.setOnClickListener(v -> showSettingsView());

        ImageView pauseButton = (ImageView) captionsSettingsView.findViewById(R.id.pause);
        pauseButton.setOnClickListener(v -> {
            if (captionsReader != null) {
                if (captionsReader.isPlaying()) {
                    captionsReader.pause();
                } else {
                    captionsReader.play();
                }
                pauseButton.setImageResource(captionsReader.isPlaying() ? R.drawable.play : R.drawable.pause);
            }
        });

        if (captionsReader != null) {
            captionsSettingsView.findViewById(R.id.fast_backward)
                    .setOnClickListener(v -> captionsReader.gotoPreviousCaption());
            captionsSettingsView.findViewById(R.id.fast_forward)
                    .setOnClickListener(v -> captionsReader.gotoNextCaption());
        }

        CheckBox pauseCheckbox = (CheckBox) captionsSettingsView.findViewById(R.id.pause_while_opening);
        pauseCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsShouldPauseCaptions = isChecked);

        captionsSettingsView.findViewById(R.id.launch_caption_settings)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, FloatingCaptionsActivity.class));

        listenForWindowControls();
    }

    /** Configure window control buttons (focus, minimize, close, launch) */
    private void listenForWindowControls() {
        captionsSettingsView.findViewById(R.id.launch_app)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));

        captionsSettingsView.findViewById(R.id.toggle_focus).setOnClickListener(v -> {
            if (layoutParams.flags == WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) {
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                ((ImageView) v).setImageResource(R.drawable.focus_on);
            } else {
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                ((ImageView) v).setImageResource(R.drawable.focus_off);
            }
            windowManager.updateViewLayout(captionsView, layoutParams);
        });

        captionsSettingsView.findViewById(R.id.minimize_captions_settings)
                .setOnClickListener(v -> hideSettingsView());
        captionsSettingsView.findViewById(R.id.close_captions).setOnClickListener(v -> stopSelf());
    }

    /** Set up the caption text display with floating parameters */
    private void setupCaptionTextDisplay() {
        if (captionsReader != null) {
            captionsReader.startDisplaying();
            WindowManager.LayoutParams captionTextParams = ViewsUtils.getFloatingLayoutParams(
                    0, (android.content.res.Resources.getSystem().getDisplayMetrics().heightPixels * 5) / 6);
            WindowManager.LayoutParams originalCaptionTextParams =
                    ViewsUtils.getFloatingLayoutParams(captionTextParams.x, captionTextParams.y);
            windowManager.addView(captionsTextView, originalCaptionTextParams);

            captionsTextView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (!captionsTextView.getText().toString().isEmpty()) {
                    originalCaptionTextParams.x = (android.content.res.Resources.getSystem().getDisplayMetrics().widthPixels
                                                           - captionsTextView.getMeasuredWidth()) / 2;
                }
                originalCaptionTextParams.y = captionTextParams.y - captionsTextView.getMeasuredHeight();
                windowManager.updateViewLayout(captionsTextView, originalCaptionTextParams);
            });

            View.OnTouchListener captionTextTouchListener = ViewsUtils.getViewTouchListener(
                    this, captionsTextView, windowManager, captionTextParams);
            captionsTextView.setOnTouchListener((view, event) -> {
                boolean result = captionTextTouchListener.onTouch(view, event);
                if (!captionsTextView.getText().toString().isEmpty()) {
                    originalCaptionTextParams.x = (android.content.res.Resources.getSystem().getDisplayMetrics().widthPixels
                                                           - captionsTextView.getMeasuredWidth()) / 2;
                }
                originalCaptionTextParams.y = captionTextParams.y - captionsTextView.getMeasuredHeight();
                windowManager.updateViewLayout(captionsTextView, originalCaptionTextParams);
                return result;
            });
        }
    }

    /** Configure seek bar for caption text size */
    private void setupSeekBar() {
        SeekBar seekBar = (SeekBar) captionsSettingsView.findViewById(R.id.captions_size_seek_bar);
        TextView sizeText = (TextView) captionsSettingsView.findViewById(R.id.captions_size_text);
        watchSeekBar(seekBar, sizeText, captionsTextView);
    }

    /** Configure color fields for caption text color */
    private void setupColorFields() {
        watchColorFields(this,
                (EditText) captionsSettingsView.findViewById(R.id.red_rgb_field),
                (EditText) captionsSettingsView.findViewById(R.id.green_rgb_field),
                (EditText) captionsSettingsView.findViewById(R.id.blue_rgb_field),
                captionsTextView);
    }

    /** Show settings view and optionally pause captions */
    private void showSettingsView() {
        collapsedCaptionsView.setVisibility(View.GONE);
        captionsSettingsView.setVisibility(View.VISIBLE);
        if (settingsShouldPauseCaptions && captionsReader != null) {
            captionsReader.pause();
        }
        if (captionsReader != null) {
            ((ImageView) captionsSettingsView.findViewById(R.id.pause))
                    .setImageResource(captionsReader.isPlaying() ? R.drawable.play : R.drawable.pause);
        }
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        windowManager.updateViewLayout(captionsView, layoutParams);
    }

    /** Hide settings view and optionally resume captions */
    private void hideSettingsView() {
        collapsedCaptionsView.setVisibility(View.VISIBLE);
        captionsSettingsView.setVisibility(View.GONE);
        if (settingsShouldPauseCaptions && captionsReader != null) {
            captionsReader.play();
        }
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(captionsView, layoutParams);
    }

    /** Adjust caption text size based on seek bar progress */
    public static void watchSeekBar(SeekBar seekBar, TextView captionsSizeText, TextView captionsTextView) {
        int baseSize = FloatingCaptionsActivity.MIN_CAPTIONS_TEXT_SIZE;
        captionsSizeText.setText("(" + (seekBar.getProgress() + baseSize) + ")");
        captionsTextView.setTextSize(seekBar.getProgress() + baseSize);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                captionsSizeText.setText("(" + (progress + baseSize) + ")");
                captionsTextView.setTextSize(progress + baseSize);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    /** Monitor color fields and apply RGB value to caption text */
    public static void watchColorFields(
            Context context, EditText red, EditText green, EditText blue, TextView captionText) {
        red.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int color = parseColor(s);
                updateCaptionColor(red, green, blue, captionText);
            }

            @Override public void afterTextChanged(Editable s) {}
        });
        green.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int color = parseColor(s);
                updateCaptionColor(red, green, blue, captionText);
            }

            @Override public void afterTextChanged(Editable s) {}
        });
        blue.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int color = parseColor(s);
                updateCaptionColor(red, green, blue, captionText);
            }

            @Override public void afterTextChanged(Editable s) {}
        });
        red.setOnFocusChangeListener((v, hasFocus) -> {
            EditText editText = (EditText) v;
            if (editText.getText().toString().isEmpty()) {
                editText.setText("0");
            }
        });
        green.setOnFocusChangeListener((v, hasFocus) -> {
            EditText editText = (EditText) v;
            if (editText.getText().toString().isEmpty()) {
                editText.setText("0");
            }
        });
        blue.setOnFocusChangeListener((v, hasFocus) -> {
            EditText editText = (EditText) v;
            if (editText.getText().toString().isEmpty()) {
                editText.setText("0");
            }
        });
        setDefaultColorForMode(context, red, green, blue);
    }

    /** Parse color value ensuring it stays within 0-255 range */
    private static int parseColor(CharSequence sequence) {
        try {
            String value = sequence.toString().isEmpty() ? "0" : sequence.toString();
            return Math.max(0, Math.min(255, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Apply RGB color from fields to caption text */
    private static void updateCaptionColor(EditText red, EditText green, EditText blue, TextView captionText) {
        int r = parseColorValue(red.getText().toString());
        int g = parseColorValue(green.getText().toString());
        int b = parseColorValue(blue.getText().toString());
        captionText.setTextColor(Color.rgb(r, g, b));
    }

    /** Parse individual color component */
    private static int parseColorValue(String value) {
        try {
            return Math.max(0, Math.min(255, Integer.parseInt(value.isEmpty() ? "0" : value)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Set default color based on night mode configuration */
    private static void setDefaultColorForMode(Context context, EditText red, EditText green, EditText blue) {
        String defaultColor = (context.getResources().getConfiguration().uiMode & 48) == 32 ? "255" : "0";
        red.setText(defaultColor);
        green.setText(defaultColor);
        blue.setText(defaultColor);
    }

    /** Update caption text display with HTML formatting support */
    public void displayCaptionText(String text) {
        captionsTextView.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            captionsTextView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT));
        } else {
            captionsTextView.setText(Html.fromHtml(text));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null) {
            if (captionsView != null) {
                windowManager.removeView(captionsView);
            }
            if (captionsTextView != null && captionsReader != null && captionsReader.isDisplaying()) {
                windowManager.removeView(captionsTextView);
            }
        }
    }
}
