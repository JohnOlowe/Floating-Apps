package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
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
        findViewById(R.id.asService).setOnClickListener((v) -> startAccessibilityService());
        findViewById(R.id.asController).setOnClickListener((v) -> showControlActivity());
    }
    
    private void startAccessibilityService() {
        if(checkIfEnabled())
            startClickerAccessibilityService();
    }

    private void startClickerAccessibilityService() {
        // Hand the session straight to the running service instance. startService() alone is not
        // enough: the system creates an accessibility service when the user ENABLES it, so its
        // onCreate() has long since run with no socket to work with.
        boolean attached = ClickerAccessibilityService.attach(bluetoothSocket);
        if (!attached) {
            // Not bound yet; it will pick the socket up from onServiceConnected(). Nudge it too,
            // in case it is already bound but the static reference was lost with the process.
            startService(new Intent(this, ClickerAccessibilityService.class));
            Toast.makeText(this, R.string.clicker_service_starting, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void showControlActivity() {
        Intent intent = new Intent(this, ClickerActivity.class);
        ClickerActivity.bluetoothSocket = bluetoothSocket;
        startActivity(intent);
    }
    
    private boolean checkIfEnabled() {
        boolean enabled = isAccessibilityServiceEnabled();
        if (!enabled) {
            alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.activate_service)
                .setMessage(R.string.activate_service_message)
                .setPositiveButton(R.string.settings, (dialog, id) -> showAccessibilityPage())
                .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                .create();
            alertDialog.show();
        }
        return enabled;
    }

    /**
     * Checks whether THIS specific accessibility service is enabled, rather than relying on the
     * global {@link Settings.Secure#ACCESSIBILITY_ENABLED} flag (which only tells us that *some*
     * accessibility service is enabled).
     */
    private boolean isAccessibilityServiceEnabled() {
        String expectedFull = getPackageName() + "/" + ClickerAccessibilityService.class.getName();
        String expectedShort = getPackageName() + "/." + ClickerAccessibilityService.class.getSimpleName();
        String enabledServices =
                Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        for (String service : splitter) {
            if (expectedFull.equalsIgnoreCase(service) || expectedShort.equalsIgnoreCase(service)) {
                return true;
            }
        }
        return false;
    }
    
    private void showAccessibilityPage() {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, SERVICE_ACCESSIBILITY);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == SERVICE_ACCESSIBILITY && checkIfEnabled())
            startClickerAccessibilityService();
    }

}
