package damjay.floating.projects.notes;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.utils.ViewsUtils;

/**
 * Floating note service providing clipboard text capture and floating display.
 * Allows copying and displaying text over other applications.
 */
public class NoteService extends Service {

    private View view;
    private View collapsedField;
    private View expandedField;
    private EditText textField;
    private Button copyButton;
    private View toggleFocus;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        view = LayoutInflater.from(this).inflate(R.layout.service_note, null);
        initializeViews();
        setOnClickListeners();
        ViewsUtils.addTouchListener(view,
                ViewsUtils.getViewTouchListener(this, view, windowManager, layoutParams),
                true, true, Button.class, ImageView.class, LinearLayout.class, RelativeLayout.class);
    }

    /** Initialize floating window layout and components */
    private void initializeViews() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        collapsedField = view.findViewById(R.id.collapsed_field);
        expandedField = view.findViewById(R.id.expanded_field);
        toggleFocus = view.findViewById(R.id.toggle_focus);
        textField = (EditText) view.findViewById(R.id.copy_text_field);
        copyButton = (Button) view.findViewById(R.id.copy_button);

        layoutParams = ViewsUtils.getFloatingLayoutParams(true);
        windowManager.addView(view, layoutParams);

        expandedField.setVisibility(View.GONE);
        collapsedField.setVisibility(View.VISIBLE);
    }

    /** Configure all interaction listeners for floating controls */
    private void setOnClickListeners() {
        view.findViewById(R.id.launch_app).setOnClickListener(
                v -> ViewsUtils.launchApp(this, MainActivity.class));

        view.findViewById(R.id.minimize_field).setOnClickListener(v -> {
            expandedField.setVisibility(View.GONE);
            collapsedField.setVisibility(View.VISIBLE);
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(view, layoutParams);
        });

        view.findViewById(R.id.close_field).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.minimize_field).performClick();

        collapsedField.setOnClickListener(v -> {
            expandedField.setVisibility(View.VISIBLE);
            collapsedField.setVisibility(View.GONE);
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            ((ImageView) toggleFocus).setImageResource(R.drawable.focus_on);
            windowManager.updateViewLayout(view, layoutParams);
        });

        toggleFocus.setOnClickListener(v -> {
            if (layoutParams.flags == WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) {
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                ((ImageView) toggleFocus).setImageResource(R.drawable.focus_on);
            } else {
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                ((ImageView) toggleFocus).setImageResource(R.drawable.focus_off);
            }
            windowManager.updateViewLayout(view, layoutParams);
        });

        copyButton.setOnClickListener(v -> {
            String text = textField.getText().toString();
            if (text.trim().isEmpty()) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(null, text);
            clipboard.setPrimaryClip(clip);
            textField.setText("");
            toggleFocus.performClick();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && view != null) {
            windowManager.removeView(view);
        }
    }
}
