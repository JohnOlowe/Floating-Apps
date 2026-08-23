package damjay.floating.projects.notes;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.QuickLaunchControl;
import damjay.floating.projects.utils.ViewsUtils;

public class NoteService extends Service {
    private View view;
    private View collapsed;
    private View expanded;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private boolean focusable;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("InflateParams")
    @Override
    public void onCreate() {
        super.onCreate();
        view = LayoutInflater.from(this).inflate(R.layout.service_note, null);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = ViewsUtils.getFloatingLayoutParams();
        windowManager.addView(view, layoutParams);
        initializeViews();
    }

    private void initializeViews() {
        collapsed = view.findViewById(R.id.collapsed_notes);
        expanded = view.findViewById(R.id.expanded_notes);
        EditText field = view.findViewById(R.id.copy_text_field);

        view.findViewById(R.id.launch_app).setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        view.findViewById(R.id.close_field).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.minimize_notes).setOnClickListener(v -> minimize());
        collapsed.setOnClickListener(v -> maximize());
        view.findViewById(R.id.copy_button).setOnClickListener(v -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(getString(R.string.floating_notes), field.getText()));
            Toast.makeText(this, R.string.copy, Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.toggle_focus).setOnClickListener(v -> toggleFocus(field));
        ViewsUtils.addTouchListener(view, ViewsUtils.getViewTouchListener(this, view, windowManager, layoutParams), true, true, EditText.class, null);
        minimize();
    }

    private void minimize() {
        expanded.setVisibility(View.GONE);
        collapsed.setVisibility(View.VISIBLE);
    }

    private void maximize() {
        expanded.setVisibility(View.VISIBLE);
        collapsed.setVisibility(View.GONE);
    }

    private void toggleFocus(EditText field) {
        focusable = !focusable;
        layoutParams.flags = focusable ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(view, layoutParams);
        if (focusable) {
            field.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && view != null) windowManager.removeView(view);
    }
}
