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

public class NoteService extends Service {
    private View collapsedField;
    private Button copyButton;
    private View expandedField;
    private WindowManager.LayoutParams layoutParams;
    private EditText textField;
    private View toggleFocus;
    private View view;
    private WindowManager windowManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.view = LayoutInflater.from(this).inflate(R.layout.service_note, (ViewGroup) null);
        initializeViews();
        setOnClickListeners();
        View view = this.view;
        ViewsUtils.addTouchListener(view, ViewsUtils.getViewTouchListener(this, view, this.windowManager, this.layoutParams), true, true, Button.class, ImageView.class, LinearLayout.class, RelativeLayout.class);
    }

    private void initializeViews() {
        this.windowManager = (WindowManager) getSystemService("window");
        this.collapsedField = this.view.findViewById(R.id.collapsed_field);
        this.expandedField = this.view.findViewById(R.id.expanded_field);
        this.toggleFocus = this.view.findViewById(R.id.toggle_focus);
        this.textField = (EditText) this.view.findViewById(R.id.copy_text_field);
        this.copyButton = (Button) this.view.findViewById(R.id.copy_button);
        WindowManager windowManager = this.windowManager;
        View view = this.view;
        WindowManager.LayoutParams floatingLayoutParams = ViewsUtils.getFloatingLayoutParams(true);
        this.layoutParams = floatingLayoutParams;
        windowManager.addView(view, floatingLayoutParams);
        this.expandedField.setVisibility(8);
        this.collapsedField.setVisibility(0);
    }

    private void setOnClickListeners() {
        this.view.findViewById(R.id.launch_app).setOnClickListener((view) -> this.m217x419fe28d(view));
        this.view.findViewById(R.id.minimize_field).setOnClickListener((view) -> this.m218x48c4bec(view));
        this.view.findViewById(R.id.close_field).setOnClickListener((view) -> this.m219xc778b54b(view));
        this.view.findViewById(R.id.minimize_field).callOnClick();
        this.collapsedField.setOnClickListener((view) -> this.m220x8a651eaa(view));
        this.toggleFocus.setOnClickListener((view) -> this.m221x4d518809(view));
        this.copyButton.setOnClickListener((view) -> this.m222x103df168(view));
    }

    void m217x419fe28d(View v) {
        ViewsUtils.launchApp(this, MainActivity.class);
    }

    void m218x48c4bec(View v) {
        this.expandedField.setVisibility(8);
        this.collapsedField.setVisibility(0);
        this.layoutParams.flags = 8;
        this.windowManager.updateViewLayout(this.view, this.layoutParams);
    }

    void m219xc778b54b(View v) {
        stopSelf();
    }

    void m220x8a651eaa(View v) {
        this.expandedField.setVisibility(0);
        this.collapsedField.setVisibility(8);
        this.layoutParams.flags = 32;
        ((ImageView) this.toggleFocus).setImageResource(R.drawable.focus_on);
        this.windowManager.updateViewLayout(this.view, this.layoutParams);
    }

    void m221x4d518809(View v) {
        if (this.layoutParams.flags == 8) {
            this.layoutParams.flags = 32;
            ((ImageView) this.toggleFocus).setImageResource(R.drawable.focus_on);
        } else {
            this.layoutParams.flags = 8;
            ((ImageView) this.toggleFocus).setImageResource(R.drawable.focus_off);
        }
        this.windowManager.updateViewLayout(this.view, this.layoutParams);
    }

    void m222x103df168(View v) {
        String text = this.textField.getText().toString();
        if (text.trim().isEmpty()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService("clipboard");
        ClipData clip = ClipData.newPlainText(null, text);
        clipboard.setPrimaryClip(clip);
        this.textField.setText("");
        this.toggleFocus.callOnClick();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.windowManager.removeView(this.view);
    }
}
