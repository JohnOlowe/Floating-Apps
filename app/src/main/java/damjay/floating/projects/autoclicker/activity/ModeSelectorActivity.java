package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
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
        return (v) -> {
            if (permissionsGranted()) {
                if (checkBluetooth()) {
                    startActivity(new Intent(this, clazz));
                    return;
                } else {
                    pendingLaunchClass = clazz;
                    return;
                }
            }
            showPermissions();
        };
    }

    private boolean checkBluetooth() {
        BluetoothManager bluetoothManager;
        BluetoothAdapter adapter;
        if (getPackageManager().hasSystemFeature("android.hardware.bluetooth")
                && (bluetoothManager = (BluetoothManager) getSystemService("bluetooth")) != null
                && (adapter = bluetoothManager.getAdapter()) != null) {
            if (!adapter.isEnabled()) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.activate_bluetooth)
                        .setPositiveButton(R.string.ok,
                                (dialog, id) -> {
                                    dialog.dismiss();
                                    Intent intent = new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE");
                                    startActivityForResult(intent, 104);
                                })
                        .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                        .setCancelable(false)
                        .create()
                        .show();
                return false;
            }
            return true;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.bluetooth_not_supported)
                .setPositiveButton(R.string.back, (dialog, id) -> finish())
                .setCancelable(false)
                .create()
                .show();
        return false;
    }

    private boolean permissionsGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_CONNECT") == 0;
    }

    private void showPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale("android.permission.BLUETOOTH_CONNECT")) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.bluetooth_permission_needed)
                        .setPositiveButton(R.string.grant,
                                (dialog, id) -> {
                                    dialog.dismiss();
                                    requestPermissions();
                                })
                        .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                        .create()
                        .show();
            } else {
                requestPermissions();
            }
        }
    }

    private void requestPermissions() {
        requestPermissions(new String[] {"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_CONNECT"},
                BLUETOOTH_PERMISSIONS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 104) {
            if (resultCode != -1) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.enable_bluetooth)
                        .setPositiveButton(R.string.cancel, (dialog, id) -> finish())
                        .setCancelable(false)
                        .create()
                        .show();
                return;
            } else {
                if (pendingLaunchClass != null) {
                    if (permissionsGranted()) {
                        startActivity(new Intent(this, pendingLaunchClass));
                    }
                    pendingLaunchClass = null;
                    return;
                }
                return;
            }
        }
        if (requestCode == 105) {
            if (permissionsGranted()) {
                checkBluetooth();
            } else {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.bluetooth_permission_needed)
                        .setPositiveButton(R.string.settings,
                                (dialog, id) -> {
                                    dialog.dismiss();
                                    ViewsUtils.openAppInfo(
                                            this, MainActivity.class.getPackage().getName(), BLUETOOTH_PERMISSIONS);
                                })
                        .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                        .setCancelable(false)
                        .create()
                        .show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 105) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkBluetooth();
            } else {
                if (permissionsGranted()) {
                    return;
                }
                new AlertDialog.Builder(this)
                        .setMessage(R.string.bluetooth_permission_needed)
                        .setPositiveButton(R.string.settings,
                                (dialog, id) -> {
                                    dialog.dismiss();
                                    ViewsUtils.openAppInfo(
                                            this, MainActivity.class.getPackage().getName(), BLUETOOTH_PERMISSIONS);
                                })
                        .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                        .setCancelable(false)
                        .create()
                        .show();
            }
        }
    }
}
