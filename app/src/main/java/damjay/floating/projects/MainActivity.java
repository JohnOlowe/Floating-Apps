package damjay.floating.projects;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.autoclicker.activity.ModeSelectorActivity;
import damjay.floating.projects.bible.BibleService;
import damjay.floating.projects.calculate.CalculatorService;
import damjay.floating.projects.captions.FloatingCaptionsActivity;
import damjay.floating.projects.music.PlayerService;
import damjay.floating.projects.notes.NoteService;
import damjay.floating.projects.timer.TimerService;
import damjay.floating.projects.utils.ViewsUtils;

public class MainActivity extends AppCompatActivity {
    public static final int FLOAT_PERMISSION_REQUEST = 100;
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.floating_pdf)
                .setOnClickListener(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                        ? v
                        -> Toast.makeText(this, R.string.pdf_not_supported, Toast.LENGTH_LONG).show()
                        : getActivityClickListener(FloatingPDFActivity.class));
        findViewById(R.id.floating_calculator).setOnClickListener(getServiceClickListener(CalculatorService.class));
        findViewById(R.id.floating_bible).setOnClickListener(getServiceClickListener(BibleService.class));
        findViewById(R.id.floating_timer).setOnClickListener(getServiceClickListener(TimerService.class));
        findViewById(R.id.floating_clicker).setOnClickListener(getActivityClickListener(ModeSelectorActivity.class));
        findViewById(R.id.floating_music).setOnClickListener(getServiceClickListener(PlayerService.class));
        findViewById(R.id.floating_copyTextField).setOnClickListener(getServiceClickListener(NoteService.class));
        findViewById(R.id.floating_browser)
                .setOnClickListener(
                        v -> Toast.makeText(this, R.string.floating_browser_coming, Toast.LENGTH_LONG).show());
        findViewById(R.id.floating_captions)
                .setOnClickListener(getActivityClickListener(FloatingCaptionsActivity.class));
        ViewsUtils.mainClass = MainActivity.class;
        checkBatteryOptimization();
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        if (!((PowerManager) getSystemService("power"))
                        .isIgnoringBatteryOptimizations(MainActivity.class.getPackage().getName())) {
            if (alertDialog != null) {
                return;
            }
            AlertDialog alertDialogCreate =
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.ignore_battery_optimization)
                            .setPositiveButton(R.string.settings,
                                    (dialog, id) -> {
                                        Intent intent = new Intent();
                                        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                        intent.setData(Uri.parse("package:" + getPackageName()));
                                        closeAlertDialog();
                                        startActivity(intent);
                                    })
                            .setNegativeButton(R.string.cancel,
                                    (dialog, id) -> {
                                        closeAlertDialog();
                                        checkPermissions();
                                    })
                            .setCancelable(false)
                            .create();
            alertDialog = alertDialogCreate;
            alertDialogCreate.show();
            return;
        }
        checkPermissions();
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            if (alertDialog != null) {
                return false;
            }
            AlertDialog alertDialogCreate =
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.grant_permissions)
                            .setMessage(R.string.display_permission_message)
                            .setCancelable(false)
                            .setPositiveButton(R.string.settings,
                                    (dialog, id) -> {
                                        closeAlertDialog();
                                        Toast.makeText(this, R.string.activate_display_over_app_message,
                                                     Toast.LENGTH_LONG)
                                                .show();
                                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:" + getPackageName()));
                                        startActivityForResult(intent, FLOAT_PERMISSION_REQUEST);
                                    })
                            .setNegativeButton(R.string.exit, (dialog, id) -> finish())
                            .create();
            alertDialog = alertDialogCreate;
            alertDialogCreate.show();
            return false;
        }
        closeAlertDialog();
        return true;
    }

    private View.OnClickListener getActivityClickListener(Class<?> clazz) {
        return (view) -> {
            Intent intent = new Intent(this, clazz);
            startActivity(intent);
        };
    }

    private View.OnClickListener getServiceClickListener(Class<?> clazz) {
        return (view) -> {
            Intent intent = new Intent(this, clazz);
            startService(intent);
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (checkPermissions()) {
            closeAlertDialog();
        }
    }

    private void closeAlertDialog() {
        AlertDialog alertDialog = alertDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (alertDialog == null) {
            checkPermissions();
        }
    }
}
