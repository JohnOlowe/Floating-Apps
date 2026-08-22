package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.utils.ViewsUtils;

public class ModeSelectorActivity extends AppCompatActivity {
    public static final int BLUETOOTH_PERMISSIONS = 105;
    public static final int ENABLE_BLUETOOTH = 104;
    private Class pendingLaunchClass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_selector);
        getSupportActionBar().setTitle(R.string.select_connect_mode);
        initializeViews();
        if (permissionsGranted()) {
            checkBluetooth();
        } else {
            showPermissions();
        }
    }

    private void initializeViews() {
        findViewById(R.id.asHost).setOnClickListener(getClickListener(HostActivity.class));
        findViewById(R.id.asGuest).setOnClickListener(getClickListener(GuestActivity.class));
    }

    public View.OnClickListener getClickListener(Class clazz) {
        return (view) -> this.m150xd41293f6(clazz, view);
    }

    void m150xd41293f6(Class clazz, View v) {
        if (permissionsGranted()) {
            if (checkBluetooth()) {
                startActivity(new Intent(this, (Class<?>) clazz));
                return;
            } else {
                this.pendingLaunchClass = clazz;
                return;
            }
        }
        showPermissions();
    }

    private boolean checkBluetooth() {
        BluetoothManager bluetoothManager;
        BluetoothAdapter adapter;
        if (getPackageManager().hasSystemFeature("android.hardware.bluetooth") && (bluetoothManager = (BluetoothManager) getSystemService("bluetooth")) != null && (adapter = bluetoothManager.getAdapter()) != null) {
            if (!adapter.isEnabled()) {
                new AlertDialog.Builder(this).setMessage(R.string.activate_bluetooth).setPositiveButton(R.string.ok, (dialogInterface, i) -> this.m147x8a32d535(dialogInterface, i)).setNegativeButton(R.string.cancel, (dialogInterface, i) -> this.m148x26a0d194(dialogInterface, i)).setCancelable(false).create().show();
                return false;
            }
            return true;
        }
        new AlertDialog.Builder(this).setMessage(R.string.bluetooth_not_supported).setPositiveButton(R.string.back, (dialogInterface, i) -> this.m149xc30ecdf3(dialogInterface, i)).setCancelable(false).create().show();
        return false;
    }

    void m147x8a32d535(DialogInterface dialog, int id) {
        dialog.dismiss();
        Intent intent = new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE");
        startActivityForResult(intent, 104);
    }

    void m148x26a0d194(DialogInterface dialog, int id) {
        finish();
    }

    void m149xc30ecdf3(DialogInterface dialog, int id) {
        finish();
    }

    private boolean permissionsGranted() {
        return Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_CONNECT") == 0;
    }

    private void showPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (shouldShowRequestPermissionRationale("android.permission.BLUETOOTH_CONNECT")) {
                new AlertDialog.Builder(this).setMessage(R.string.bluetooth_permission_needed).setPositiveButton(R.string.grant, (dialogInterface, i) -> this.m156x2b559d4b(dialogInterface, i)).setNegativeButton(R.string.cancel, (dialogInterface, i) -> this.m157xc7c399aa(dialogInterface, i)).create().show();
            } else {
                requestPermissions();
            }
        }
    }

    void m156x2b559d4b(DialogInterface dialog, int id) {
        dialog.dismiss();
        requestPermissions();
    }

    void m157xc7c399aa(DialogInterface dialog, int id) {
        finish();
    }

    private void requestPermissions() {
        requestPermissions(new String[]{"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_CONNECT"}, BLUETOOTH_PERMISSIONS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 104) {
            if (resultCode != -1) {
                new AlertDialog.Builder(this).setMessage(R.string.enable_bluetooth).setPositiveButton(R.string.cancel, (dialogInterface, i) -> this.m151xbe9eb02b(dialogInterface, i)).setCancelable(false).create().show();
                return;
            } else {
                if (this.pendingLaunchClass != null) {
                    if (permissionsGranted()) {
                        startActivity(new Intent(this, (Class<?>) this.pendingLaunchClass));
                    }
                    this.pendingLaunchClass = null;
                    return;
                }
                return;
            }
        }
        if (requestCode == 105) {
            if (permissionsGranted()) {
                checkBluetooth();
            } else {
                new AlertDialog.Builder(this).setMessage(R.string.bluetooth_permission_needed).setPositiveButton(R.string.settings, (dialogInterface, i) -> this.m152x5b0cac8a(dialogInterface, i)).setNegativeButton(R.string.cancel, (dialogInterface, i) -> this.m153xf77aa8e9(dialogInterface, i)).setCancelable(false).create().show();
            }
        }
    }

    void m151xbe9eb02b(DialogInterface dialog, int id) {
        finish();
    }

    void m152x5b0cac8a(DialogInterface dialog, int id) {
        dialog.dismiss();
        ViewsUtils.openAppInfo(this, MainActivity.class.getPackage().getName(), BLUETOOTH_PERMISSIONS);
    }

    void m153xf77aa8e9(DialogInterface dialog, int id) {
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 105) {
            if (grantResults[0] == 0) {
                checkBluetooth();
            } else {
                if (permissionsGranted()) {
                    return;
                }
                new AlertDialog.Builder(this).setMessage(R.string.bluetooth_permission_needed).setPositiveButton(R.string.settings, (dialogInterface, i) -> this.m155x84e2d22(dialogInterface, i)).setNegativeButton(R.string.cancel, (dialogInterface, i) -> this.m154xceadb450(dialogInterface, i)).setCancelable(false).create().show();
            }
        }
    }

    void m155x84e2d22(DialogInterface dialog, int id) {
        dialog.dismiss();
        ViewsUtils.openAppInfo(this, MainActivity.class.getPackage().getName(), BLUETOOTH_PERMISSIONS);
    }

    void m154xceadb450(DialogInterface dialog, int id) {
        finish();
    }
}
