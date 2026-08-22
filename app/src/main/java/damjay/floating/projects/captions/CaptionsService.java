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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
            WindowManager.LayoutParams captionsTextParams = ViewsUtils.getFloatingLayoutParams(0, (Resources.getSystem().getDisplayMetrics().heightPixels * 5) / 6);
            WindowManager.LayoutParams originalCaptionsTextParams = ViewsUtils.getFloatingLayoutParams(captionsTextParams.x, captionsTextParams.y);
            this.windowManager.addView(this.captionsTextView, originalCaptionsTextParams);
            this.captionsTextView.getViewTreeObserver().addOnGlobalLayoutListener(() -> this.m182x76ef97ef(originalCaptionsTextParams, captionsTextParams));
            View.OnTouchListener captionsTextTouchListener = ViewsUtils.getViewTouchListener(this, this.captionsTextView, this.windowManager, captionsTextParams);
            this.captionsTextView.setOnTouchListener((view, motionEvent) -> this.m183xba7ab5b0(captionsTextTouchListener, originalCaptionsTextParams, captionsTextParams, view, motionEvent));
        }
    }

    void m182x76ef97ef(WindowManager.LayoutParams originalCaptionsTextParams, WindowManager.LayoutParams captionsTextParams) {
        if (!this.captionsTextView.getText().toString().isEmpty()) {
            originalCaptionsTextParams.x = (Resources.getSystem().getDisplayMetrics().widthPixels - this.captionsTextView.getMeasuredWidth()) / 2;
        }
        originalCaptionsTextParams.y = captionsTextParams.y - this.captionsTextView.getMeasuredHeight();
        this.windowManager.updateViewLayout(this.captionsTextView, originalCaptionsTextParams);
    }

    boolean m183xba7ab5b0(View.OnTouchListener captionsTextTouchListener, WindowManager.LayoutParams originalCaptionsTextParams, WindowManager.LayoutParams captionsTextParams, View view, MotionEvent event) {
        boolean result = captionsTextTouchListener.onTouch(view, event);
        if (!this.captionsTextView.getText().toString().isEmpty()) {
            originalCaptionsTextParams.x = (Resources.getSystem().getDisplayMetrics().widthPixels - this.captionsTextView.getMeasuredWidth()) / 2;
        }
        originalCaptionsTextParams.y = captionsTextParams.y - this.captionsTextView.getMeasuredHeight();
        this.windowManager.updateViewLayout(this.captionsTextView, originalCaptionsTextParams);
        return result;
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
        watchSeekBar((SeekBar) this.captionsSettingsView.findViewById(R.id.captions_size_seek_bar), (TextView) this.captionsSettingsView.findViewById(R.id.captions_size_text), this.captionsTextView);
        watchColorFields(this, (EditText) this.captionsSettingsView.findViewById(R.id.red_rgb_field), (EditText) this.captionsSettingsView.findViewById(R.id.green_rgb_field), (EditText) this.captionsSettingsView.findViewById(R.id.blue_rgb_field), this.captionsTextView);
        this.windowManager.addView(this.captionsView, this.layoutParams);
        View view = this.captionsView;
        ViewsUtils.addTouchListener(view, ViewsUtils.getViewTouchListener(this, view, this.windowManager, this.layoutParams), true, true, EditText.class, SeekBar.class, CheckBox.class, null);
        hideSettingsView();
    }

    private void setClickListeners() {
        this.collapsedCaptionsView.setOnClickListener((view) -> this.m184x2f68075(view));
        ImageView pauseView = (ImageView) this.captionsSettingsView.findViewById(R.id.pause);
        pauseView.setOnClickListener((view) -> this.m185x46819e36(pauseView, view));
        if (this.captionsReader != null) {
            this.captionsSettingsView.findViewById(R.id.fast_backward).setOnClickListener((view) -> this.m186x8a0cbbf7(view));
            this.captionsSettingsView.findViewById(R.id.fast_forward).setOnClickListener((view) -> this.m187xcd97d9b8(view));
        }
        CheckBox pauseCheckbox = (CheckBox) this.captionsSettingsView.findViewById(R.id.pause_while_opening);
        pauseCheckbox.setOnCheckedChangeListener((compoundButton, z) -> this.m188x1122f779(compoundButton, z));
        this.captionsSettingsView.findViewById(R.id.launch_caption_settings).setOnClickListener((view) -> this.m189x54ae153a(view));
        listenForWindowControls();
    }

    void m184x2f68075(View v) {
        showSettingsView();
    }

    void m185x46819e36(ImageView pauseView, View v) {
        if (this.captionsReader.isPlaying()) {
            this.captionsReader.pause();
        } else {
            this.captionsReader.play();
        }
        pauseView.setImageResource(this.captionsReader.isPlaying() ? R.drawable.play : R.drawable.pause);
    }

    void m186x8a0cbbf7(View v) {
        this.captionsReader.gotoPreviousCaption();
    }

    void m187xcd97d9b8(View v) {
        this.captionsReader.gotoNextCaption();
    }

    void m188x1122f779(CompoundButton buttonView, boolean isChecked) {
        this.settingsShouldPauseCaptions = isChecked;
    }

    void m189x54ae153a(View v) {
        ViewsUtils.launchApp(this, FloatingCaptionsActivity.class);
    }

    private void listenForWindowControls() {
        this.captionsSettingsView.findViewById(R.id.launch_app).setOnClickListener((view) -> this.m180x6a9b5d8a(view));
        this.captionsSettingsView.findViewById(R.id.toggle_focus).setOnClickListener((view) -> this.m181xae267b4b(view));
        this.captionsSettingsView.findViewById(R.id.minimize_captions_settings).setOnClickListener((view) -> this.m178x4911f6c3(view));
        this.captionsSettingsView.findViewById(R.id.close_captions).setOnClickListener((view) -> this.m179x8c9d1484(view));
    }

    void m180x6a9b5d8a(View v) {
        ViewsUtils.launchApp(this, MainActivity.class);
    }

    void m181xae267b4b(View v) {
        if (this.layoutParams.flags == 8) {
            this.layoutParams.flags = 32;
            ((ImageView) v).setImageResource(R.drawable.focus_on);
        } else {
            this.layoutParams.flags = 8;
            ((ImageView) v).setImageResource(R.drawable.focus_off);
        }
        this.windowManager.updateViewLayout(this.captionsView, this.layoutParams);
    }

    void m178x4911f6c3(View v) {
        hideSettingsView();
    }

    void m179x8c9d1484(View v) {
        stopSelf();
    }

    private void showSettingsView() {
        CaptionsReader captionsReader;
        this.collapsedCaptionsView.setVisibility(8);
        this.captionsSettingsView.setVisibility(0);
        if (this.settingsShouldPauseCaptions && (captionsReader = this.captionsReader) != null) {
            captionsReader.pause();
        }
        if (this.captionsReader != null) {
            ((ImageView) this.captionsSettingsView.findViewById(R.id.pause)).setImageResource(this.captionsReader.isPlaying() ? R.drawable.play : R.drawable.pause);
        }
        this.layoutParams.flags = 32;
        this.windowManager.updateViewLayout(this.captionsView, this.layoutParams);
    }

    private void hideSettingsView() {
        CaptionsReader captionsReader;
        this.collapsedCaptionsView.setVisibility(0);
        this.captionsSettingsView.setVisibility(8);
        if (this.settingsShouldPauseCaptions && (captionsReader = this.captionsReader) != null) {
            captionsReader.play();
        }
        this.layoutParams.flags = 8;
        this.windowManager.updateViewLayout(this.captionsView, this.layoutParams);
    }

    public static void watchSeekBar(SeekBar seekBar, TextView captionsSizeText, TextView captionsTextView) {
        captionsSizeText.setText("(" + (seekBar.getProgress() + 10) + ")");
        captionsTextView.setTextSize(2, seekBar.getProgress() + 10);
        seekBar.setOnSeekBarChangeListener(new AnonymousClass1(captionsSizeText, captionsTextView));
    }

    class AnonymousClass1 implements SeekBar.OnSeekBarChangeListener {
        final TextView val$captionsSizeText;
        final TextView val$captionsTextView;

        AnonymousClass1(TextView textView, TextView textView2) {
            this.val$captionsSizeText = textView;
            this.val$captionsTextView = textView2;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            this.val$captionsSizeText.setText("(" + (progress + 10) + ")");
            this.val$captionsTextView.setTextSize(2, progress + 10);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    class AnonymousClass2 implements TextWatcher {
        final EditText val$blue;
        final TextView val$captionsText;
        final EditText val$green;
        final EditText val$red;

        AnonymousClass2(EditText editText, EditText editText2, EditText editText3, TextView textView) {
            this.val$red = editText;
            this.val$green = editText2;
            this.val$blue = editText3;
            this.val$captionsText = textView;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            boolean isEmpty = s.toString().isEmpty();
            CharSequence s2 = isEmpty ? "0" : s;
            int color = Math.max(0, Math.min(255, Integer.parseInt(s2.toString())));
            if (!isEmpty && !String.valueOf(color).equals(s2.toString())) {
                this.val$red.setText(String.valueOf(color));
                EditText editText = this.val$red;
                editText.setSelection(editText.getText().toString().length());
            }
            int rgb = (color << 16) | ViewCompat.MEASURED_STATE_MASK | (Integer.parseInt(this.val$green.getText().toString()) << 8) | Integer.parseInt(this.val$blue.getText().toString());
            this.val$captionsText.setTextColor(rgb);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    public static void watchColorFields(Context context, EditText red, EditText green, EditText blue, TextView captionsText) {
        red.addTextChangedListener(new AnonymousClass2(red, green, blue, captionsText));
        green.addTextChangedListener(new AnonymousClass3(green, red, blue, captionsText));
        blue.addTextChangedListener(new AnonymousClass4(blue, red, green, captionsText));
        View.OnFocusChangeListener onFocusChangeListener = (view, z) -> CaptionsService.lambda$watchColorFields$12(view, z);
        red.setOnFocusChangeListener(onFocusChangeListener);
        green.setOnFocusChangeListener(onFocusChangeListener);
        blue.setOnFocusChangeListener(onFocusChangeListener);
        checkIfNightMode(context, red, green, blue);
    }

    class AnonymousClass3 implements TextWatcher {
        final EditText val$blue;
        final TextView val$captionsText;
        final EditText val$green;
        final EditText val$red;

        AnonymousClass3(EditText editText, EditText editText2, EditText editText3, TextView textView) {
            this.val$green = editText;
            this.val$red = editText2;
            this.val$blue = editText3;
            this.val$captionsText = textView;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            boolean isEmpty = s.toString().isEmpty();
            CharSequence s2 = isEmpty ? "0" : s;
            int color = Math.max(0, Math.min(255, Integer.parseInt(s2.toString())));
            if (!isEmpty && !String.valueOf(color).equals(s2.toString())) {
                this.val$green.setText(String.valueOf(color));
                EditText editText = this.val$green;
                editText.setSelection(editText.getText().toString().length());
            }
            int rgb = (Integer.parseInt(this.val$red.getText().toString()) << 16) | ViewCompat.MEASURED_STATE_MASK | (color << 8) | Integer.parseInt(this.val$blue.getText().toString());
            this.val$captionsText.setTextColor(rgb);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    class AnonymousClass4 implements TextWatcher {
        final EditText val$blue;
        final TextView val$captionsText;
        final EditText val$green;
        final EditText val$red;

        AnonymousClass4(EditText editText, EditText editText2, EditText editText3, TextView textView) {
            this.val$blue = editText;
            this.val$red = editText2;
            this.val$green = editText3;
            this.val$captionsText = textView;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            boolean isEmpty = s.toString().isEmpty();
            CharSequence s2 = isEmpty ? "0" : s;
            int color = Math.max(0, Math.min(255, Integer.parseInt(s2.toString())));
            if (!isEmpty && !String.valueOf(color).equals(s2.toString())) {
                this.val$blue.setText(String.valueOf(color));
                EditText editText = this.val$blue;
                editText.setSelection(editText.getText().toString().length());
            }
            int rgb = (Integer.parseInt(this.val$red.getText().toString()) << 16) | ViewCompat.MEASURED_STATE_MASK | (Integer.parseInt(this.val$green.getText().toString()) << 8) | color;
            this.val$captionsText.setTextColor(rgb);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    static void lambda$watchColorFields$12(View v, boolean hasFocus) {
        EditText editText = (EditText) v;
        if (editText.getText().toString().isEmpty()) {
            editText.setText("0");
        }
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
