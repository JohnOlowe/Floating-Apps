package damjay.floating.projects;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
        findViewById(R.id.floating_pdf).setOnClickListener(Build.VERSION.SDK_INT < 21 ? (view) -> this.m114lambda$onCreate$0$damjayfloatingprojectsMainActivity(view) : getActivityClickListener(FloatingPDFActivity.class));
        findViewById(R.id.floating_calculator).setOnClickListener(getServiceClickListener(CalculatorService.class));
        findViewById(R.id.floating_bible).setOnClickListener(getServiceClickListener(BibleService.class));
        findViewById(R.id.floating_timer).setOnClickListener(getServiceClickListener(TimerService.class));
        findViewById(R.id.floating_clicker).setOnClickListener(getActivityClickListener(ModeSelectorActivity.class));
        findViewById(R.id.floating_music).setOnClickListener(getServiceClickListener(PlayerService.class));
        findViewById(R.id.floating_copyTextField).setOnClickListener(getServiceClickListener(NoteService.class));
        findViewById(R.id.floating_browser).setOnClickListener((view) -> this.m115lambda$onCreate$1$damjayfloatingprojectsMainActivity(view));
        findViewById(R.id.floating_captions).setOnClickListener(getActivityClickListener(FloatingCaptionsActivity.class));
        ViewsUtils.mainClass = MainActivity.class;
        checkBatteryOptimization();
    }

    void m114lambda$onCreate$0$damjayfloatingprojectsMainActivity(View v) {
        Toast.makeText(this, R.string.pdf_not_supported, 1).show();
    }

    void m115lambda$onCreate$1$damjayfloatingprojectsMainActivity(View v) {
        Toast.makeText(this, R.string.floating_browser_coming, 1).show();
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        if (!((PowerManager) getSystemService("power")).isIgnoringBatteryOptimizations(MainActivity.class.getPackage().getName())) {
            if (this.alertDialog != null) {
                return;
            }
            AlertDialog alertDialogCreate = new AlertDialog.Builder(this).setMessage(R.string.ignore_battery_optimization).setPositiveButton(R.string.settings, (dialogInterface, i) -> this.m108xc32fde28(dialogInterface, i)).setNegativeButton(R.string.cancel, (dialogInterface, i) -> this.m109x8c30d569(dialogInterface, i)).setCancelable(false).create();
            this.alertDialog = alertDialogCreate;
            alertDialogCreate.show();
            return;
        }
        checkPermissions();
    }

    void m108xc32fde28(DialogInterface dialog, int id) {
        Intent intent = new Intent();
        intent.setAction("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
        intent.setData(Uri.parse("package:" + getPackageName()));
        closeAlertDialog();
        startActivity(intent);
    }

    void m109x8c30d569(DialogInterface dialog, int id) {
        closeAlertDialog();
        checkPermissions();
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            if (this.alertDialog != null) {
                return false;
            }
            AlertDialog alertDialogCreate = new AlertDialog.Builder(this).setTitle(R.string.grant_permissions).setMessage(R.string.display_permission_message).setCancelable(false).setPositiveButton(R.string.settings, (dialogInterface, i) -> this.m110lambda$checkPermissions$4$damjayfloatingprojectsMainActivity(dialogInterface, i)).setNegativeButton(R.string.exit, (dialogInterface, i) -> this.m111lambda$checkPermissions$5$damjayfloatingprojectsMainActivity(dialogInterface, i)).create();
            this.alertDialog = alertDialogCreate;
            alertDialogCreate.show();
            return false;
        }
        closeAlertDialog();
        return true;
    }

    void m110lambda$checkPermissions$4$damjayfloatingprojectsMainActivity(DialogInterface dialog, int id) {
        closeAlertDialog();
        Toast.makeText(this, R.string.activate_display_over_app_message, 1).show();
        Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, 100);
    }

    void m111lambda$checkPermissions$5$damjayfloatingprojectsMainActivity(DialogInterface dialog, int id) {
        finish();
    }

    private View.OnClickListener getActivityClickListener(Class<?> clazz) {
        return (view) -> this.m112x3a107a11(clazz, view);
    }

    void m112x3a107a11(Class clazz, View view) {
        Intent intent = new Intent(this, (Class<?>) clazz);
        startActivity(intent);
    }

    private View.OnClickListener getServiceClickListener(Class<?> clazz) {
        return (view) -> this.m113x2f0c517e(clazz, view);
    }

    void m113x2f0c517e(Class clazz, View view) {
        Intent intent = new Intent(this, (Class<?>) clazz);
        startService(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (checkPermissions()) {
            closeAlertDialog();
        }
    }

    private void closeAlertDialog() {
        AlertDialog alertDialog = this.alertDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
            this.alertDialog = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.alertDialog == null) {
            checkPermissions();
        }
    }
}
