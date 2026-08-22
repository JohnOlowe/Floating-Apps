package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.autoclicker.service.ClickerAccessibilityService;

public class ActionSelectorActivity extends AppCompatActivity {
    public static final int SERVICE_ACCESSIBILITY = 100;
    static BluetoothSocket bluetoothSocket;
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_action_selector);
        setResponse();
    }

    private void setResponse() {
        findViewById(R.id.asService).setOnClickListener((view) -> this.m134xf626466(view));
        findViewById(R.id.asController).setOnClickListener((view) -> this.m135x4842c505(view));
    }

    void m134xf626466(View v) {
        startAccessibilityService();
    }

    void m135x4842c505(View v) {
        showControlActivity();
    }

    private void startAccessibilityService() {
        if (checkIfEnabled()) {
            startClickerAccessibilityService();
        }
    }

    private void startClickerAccessibilityService() {
        Intent intent = new Intent(this, (Class<?>) ClickerAccessibilityService.class);
        ClickerAccessibilityService.bluetoothSocket = bluetoothSocket;
        finish();
        startService(intent);
    }

    private void showControlActivity() {
        Intent intent = new Intent(this, (Class<?>) ClickerActivity.class);
        ClickerActivity.bluetoothSocket = bluetoothSocket;
        startActivity(intent);
    }

    private boolean checkIfEnabled() throws Settings.SettingNotFoundException {
        int accessEnabled = 0;
        try {
            accessEnabled = Settings.Secure.getInt(getContentResolver(), "accessibility_enabled");
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        if (accessEnabled == 0) {
            AlertDialog alertDialogCreate = new AlertDialog.Builder(this).setTitle(R.string.activate_service).setMessage(R.string.activate_service_message).setPositiveButton(R.string.settings, (dialogInterface, i) -> this.m132x36cbf9f1(dialogInterface, i)).setNegativeButton(R.string.cancel, (dialogInterface, i) -> this.m133x6fac5a90(dialogInterface, i)).create();
            this.alertDialog = alertDialogCreate;
            alertDialogCreate.show();
        }
        return accessEnabled != 0;
    }

    void m132x36cbf9f1(DialogInterface dialog, int id) {
        showAccessibilityPage();
    }

    void m133x6fac5a90(DialogInterface dialog, int id) {
        finish();
    }

    private void showAccessibilityPage() {
        AlertDialog alertDialog = this.alertDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
            this.alertDialog = null;
        }
        Intent intent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
        intent.addFlags(268435456);
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (checkIfEnabled()) {
            startClickerAccessibilityService();
        }
    }
}
