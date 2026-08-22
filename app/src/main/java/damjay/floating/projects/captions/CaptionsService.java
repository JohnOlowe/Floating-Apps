package damjay.floating.projects.captions;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.utils.ViewsUtils;

public class CaptionsService extends Service {
    public static String captionsFileName;
    public static String contentCaptions;
    private CaptionsReader captionsReader;
    private View captionsSettingsView;
    private TextView captionsTextView;
    private View captionsView;
    private View collapsedCaptionsView;
    private WindowManager.LayoutParams layoutParams;
    private boolean settingsShouldPauseCaptions = true;
    private WindowManager windowManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.captionsView = LayoutInflater.from(this).inflate(R.layout.service_captions, (ViewGroup) null);
        TextView textView = new TextView(this);
        this.captionsTextView = textView;
        textView.setLayoutParams(new ViewGroup.LayoutParams(Resources.getSystem().getDisplayMetrics().widthPixels, -2));
        this.captionsTextView.setGravity(1);
        initializeFloatingParameters();
        initializeSettingViews();
        CaptionsReader captionsReader = this.captionsReader;
        if (captionsReader != null) {
            captionsReader.startDisplaying();
            WindowManager.LayoutParams captionsTextParams = ViewsUtils.getFloatingLayoutParams(
                    0, (Resources.getSystem().getDisplayMetrics().heightPixels * 5) / 6);
            WindowManager.LayoutParams originalCaptionsTextParams =
                    ViewsUtils.getFloatingLayoutParams(captionsTextParams.x, captionsTextParams.y);
            this.windowManager.addView(this.captionsTextView, originalCaptionsTextParams);
            this.captionsTextView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (!this.captionsTextView.getText().toString().isEmpty()) {
                    originalCaptionsTextParams.x = (Resources.getSystem().getDisplayMetrics().widthPixels
                                                           - this.captionsTextView.getMeasuredWidth())
                            / 2;
                }
                originalCaptionsTextParams.y = captionsTextParams.y - this.captionsTextView.getMeasuredHeight();
                this.windowManager.updateViewLayout(this.captionsTextView, originalCaptionsTextParams);
            });
            View.OnTouchListener captionsTextTouchListener = ViewsUtils.getViewTouchListener(
                    this, this.captionsTextView, this.windowManager, captionsTextParams);
            this.captionsTextView.setOnTouchListener((view, event) -> {
                boolean result = captionsTextTouchListener.onTouch(view, event);
                if (!this.captionsTextView.getText().toString().isEmpty()) {
                    originalCaptionsTextParams.x = (Resources.getSystem().getDisplayMetrics().widthPixels
                                                           - this.captionsTextView.getMeasuredWidth())
                            / 2;
                }
                originalCaptionsTextParams.y = captionsTextParams.y - this.captionsTextView.getMeasuredHeight();
                this.windowManager.updateViewLayout(this.captionsTextView, originalCaptionsTextParams);
                return result;
            });
        }
    }

    private void initializeFloatingParameters() {
        this.windowManager = (WindowManager) getSystemService("window");
        this.layoutParams = ViewsUtils.getFloatingLayoutParams(false);
    }

    private void initializeSettingViews() {
        this.captionsSettingsView = this.captionsView.findViewById(R.id.captions_settings);
        this.collapsedCaptionsView = this.captionsView.findViewById(R.id.collapsed_captions_settings);
        String str = contentCaptions;
        if (str != null) {
            this.captionsReader = new CaptionsReader(str, (str) -> this.displayCaptionText(str));
            TextView fileName = (TextView) this.captionsSettingsView.findViewById(R.id.captions_file_name);
            fileName.setText(captionsFileName);
        }
        setClickListeners();
        watchSeekBar((SeekBar) this.captionsSettingsView.findViewById(R.id.captions_size_seek_bar),
                (TextView) this.captionsSettingsView.findViewById(R.id.captions_size_text), this.captionsTextView);
        watchColorFields(this, (EditText) this.captionsSettingsView.findViewById(R.id.red_rgb_field),
                (EditText) this.captionsSettingsView.findViewById(R.id.green_rgb_field),
                (EditText) this.captionsSettingsView.findViewById(R.id.blue_rgb_field), this.captionsTextView);
        this.windowManager.addView(this.captionsView, this.layoutParams);
        View view = this.captionsView;
        ViewsUtils.addTouchListener(view,
                ViewsUtils.getViewTouchListener(this, view, this.windowManager, this.layoutParams), true, true,
                EditText.class, SeekBar.class, CheckBox.class, null);
        hideSettingsView();
    }

    private void setClickListeners() {
        this.collapsedCaptionsView.setOnClickListener(v -> showSettingsView());
        ImageView pauseView = (ImageView) this.captionsSettingsView.findViewById(R.id.pause);
        pauseView.setOnClickListener((v) -> {
            if (this.captionsReader.isPlaying()) {
                this.captionsReader.pause();
            } else {
                this.captionsReader.play();
            }
            pauseView.setImageResource(this.captionsReader.isPlaying() ? R.drawable.play : R.drawable.pause);
        });
        if (this.captionsReader != null) {
            this.captionsSettingsView.findViewById(R.id.fast_backward)
                    .setOnClickListener(v -> this.captionsReader.gotoPreviousCaption());
            this.captionsSettingsView.findViewById(R.id.fast_forward)
                    .setOnClickListener(v -> this.captionsReader.gotoNextCaption());
        }
        CheckBox pauseCheckbox = (CheckBox) this.captionsSettingsView.findViewById(R.id.pause_while_opening);
        pauseCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> this.settingsShouldPauseCaptions = isChecked);
        this.captionsSettingsView.findViewById(R.id.launch_caption_settings)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, FloatingCaptionsActivity.class));
        listenForWindowControls();
    }

    private void listenForWindowControls() {
        this.captionsSettingsView.findViewById(R.id.launch_app)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        this.captionsSettingsView.findViewById(R.id.toggle_focus).setOnClickListener((v) -> {
            if (this.layoutParams.flags == WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) {
                this.layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                ((ImageView) v).setImageResource(R.drawable.focus_on);
            } else {
                this.layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                ((ImageView) v).setImageResource(R.drawable.focus_off);
            }
            this.windowManager.updateViewLayout(this.captionsView, this.layoutParams);
        });
        this.captionsSettingsView.findViewById(R.id.minimize_captions_settings)
                .setOnClickListener(v -> hideSettingsView());
        this.captionsSettingsView.findViewById(R.id.close_captions).setOnClickListener(v -> stopSelf());
    }

    private void showSettingsView() {
        CaptionsReader captionsReader;
        this.collapsedCaptionsView.setVisibility(View.GONE);
        this.captionsSettingsView.setVisibility(View.VISIBLE);
        if (this.settingsShouldPauseCaptions && (captionsReader = this.captionsReader) != null) {
            captionsReader.pause();
        }
        if (this.captionsReader != null) {
            ((ImageView) this.captionsSettingsView.findViewById(R.id.pause))
                    .setImageResource(this.captionsReader.isPlaying() ? R.drawable.play : R.drawable.pause);
        }
        this.layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        this.windowManager.updateViewLayout(this.captionsView, this.layoutParams);
    }

    private void hideSettingsView() {
        CaptionsReader captionsReader;
        this.collapsedCaptionsView.setVisibility(View.VISIBLE);
        this.captionsSettingsView.setVisibility(View.GONE);
        if (this.settingsShouldPauseCaptions && (captionsReader = this.captionsReader) != null) {
            captionsReader.play();
        }
        this.layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        this.windowManager.updateViewLayout(this.captionsView, this.layoutParams);
    }

    public static void watchSeekBar(SeekBar seekBar, TextView captionsSizeText, TextView captionsTextView) {
        captionsSizeText.setText("(" + (seekBar.getProgress() + 10) + ")");
        captionsTextView.setTextSize(2, seekBar.getProgress() + 10);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                captionsSizeText.setText("(" + (progress + 10) + ")");
                captionsTextView.setTextSize(2, progress + 10);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    public static void watchColorFields(
            Context context, EditText red, EditText green, EditText blue, TextView captionsText) {
        red.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean isEmpty = s.toString().isEmpty();
                CharSequence s2 = isEmpty ? "0" : s;
                int color = Math.max(0, Math.min(255, Integer.parseInt(s2.toString())));
                if (!isEmpty && !String.valueOf(color).equals(s2.toString())) {
                    red.setText(String.valueOf(color));
                    EditText editText = red;
                    editText.setSelection(editText.getText().toString().length());
                }
                int rgb = (color << 16) | ViewCompat.MEASURED_STATE_MASK
                        | (Integer.parseInt(green.getText().toString()) << 8)
                        | Integer.parseInt(blue.getText().toString());
                captionsText.setTextColor(rgb);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        green.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean isEmpty = s.toString().isEmpty();
                CharSequence s2 = isEmpty ? "0" : s;
                int color = Math.max(0, Math.min(255, Integer.parseInt(s2.toString())));
                if (!isEmpty && !String.valueOf(color).equals(s2.toString())) {
                    green.setText(String.valueOf(color));
                    EditText editText = green;
                    editText.setSelection(editText.getText().toString().length());
                }
                int rgb = (Integer.parseInt(red.getText().toString()) << 16) | ViewCompat.MEASURED_STATE_MASK
                        | (color << 8) | Integer.parseInt(blue.getText().toString());
                captionsText.setTextColor(rgb);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        blue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean isEmpty = s.toString().isEmpty();
                CharSequence s2 = isEmpty ? "0" : s;
                int color = Math.max(0, Math.min(255, Integer.parseInt(s2.toString())));
                if (!isEmpty && !String.valueOf(color).equals(s2.toString())) {
                    blue.setText(String.valueOf(color));
                    EditText editText = blue;
                    editText.setSelection(editText.getText().toString().length());
                }
                int rgb = (Integer.parseInt(red.getText().toString()) << 16) | ViewCompat.MEASURED_STATE_MASK
                        | (Integer.parseInt(green.getText().toString()) << 8) | color;
                captionsText.setTextColor(rgb);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        View.OnFocusChangeListener onFocusChangeListener = (v, hasFocus) -> {
            EditText editText = (EditText) v;
            if (editText.getText().toString().isEmpty()) {
                editText.setText("0");
            }
        };
        red.setOnFocusChangeListener(onFocusChangeListener);
        green.setOnFocusChangeListener(onFocusChangeListener);
        blue.setOnFocusChangeListener(onFocusChangeListener);
        checkIfNightMode(context, red, green, blue);
    }

    private static void checkIfNightMode(Context context, EditText red, EditText green, EditText blue) {
        String defaultColor = (context.getResources().getConfiguration().uiMode & 48) == 32 ? "255" : "0";
        red.setText(defaultColor);
        green.setText(defaultColor);
        blue.setText(defaultColor);
    }

    public void displayCaptionText(String text) {
        this.captionsTextView.setVisibility(text.isEmpty() ? 8 : 0);
        if (Build.VERSION.SDK_INT >= 24) {
            this.captionsTextView.setText(Html.fromHtml(text, 0));
        } else {
            this.captionsTextView.setText(Html.fromHtml(text));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        WindowManager windowManager = this.windowManager;
        if (windowManager != null) {
            windowManager.removeView(this.captionsView);
            if (this.captionsReader.isDisplaying()) {
                this.windowManager.removeView(this.captionsTextView);
            }
        }
    }
}
