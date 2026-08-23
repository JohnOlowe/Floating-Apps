package damjay.floating.projects.captions;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import damjay.floating.projects.FileSearchActivity;
import damjay.floating.projects.FloatingPDFActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.files.FileBrowserActivity;

import java.io.File;
import java.io.FileInputStream;

/**
 * Activity for selecting and launching floating caption services.
 * Allows browsing for caption files, configuring text size and color,
 * and managing display permissions.
 */
public class FloatingCaptionsActivity extends AppCompatActivity {

    public static final String EXTENSION_ALLOWED = "srt";
    public static final int MIN_CAPTIONS_TEXT_SIZE = 10;
    public static final int JUST_STARTING = 0;
    public static final int SETTING_OPENED = 1;
    public static int captionsState = JUST_STARTING;

    private AlertDialog alertDialog;
    private TextView filePathField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_floating_captions);
        initializeViews();
        checkPermission();
    }

    private void initializeViews() {
        filePathField = findViewById(R.id.caption_file_path);

        findViewById(R.id.start_captions).setOnClickListener(v -> startCaptions());

        findViewById(R.id.search_files).setOnClickListener(v -> {
            FileSearchActivity.callback = getCaptionsCallback();
            startActivity(new Intent(this, FileSearchActivity.class));
        });

        findViewById(R.id.browse_files).setOnClickListener(v -> {
            FileBrowserActivity.currentInput = filePathField.getText().toString();
            FileBrowserActivity.callback = getCaptionsCallback();
            startActivity(new Intent(this, FileBrowserActivity.class));
        });

        TextView sampleText = findViewById(R.id.sample_text);
        CaptionsService.watchSeekBar(findViewById(R.id.captions_size_seek_bar),
                findViewById(R.id.captions_size_text), sampleText);
        CaptionsService.watchColorFields(this,
                findViewById(R.id.red_rgb_field),
                findViewById(R.id.green_rgb_field),
                findViewById(R.id.blue_rgb_field),
                sampleText);
    }

    private FileBrowserActivity.FileCallback getCaptionsCallback() {
        return new FileBrowserActivity.FileCallback() {
            @Override
            public void fileCallback(String path) {
                filePathField.setText(path);
            }

            @Override
            public String titleOfBrowser() {
                return getString(R.string.floating_captions);
            }

            @Override
            public String extensionAllowed() {
                return EXTENSION_ALLOWED;
            }
        };
    }

    private void startCaptions() {
        if (!checkPermission()) return;
        String filePath = filePathField.getText().toString().trim();
        if (filePath.isEmpty()) return;

        File file = new File(filePath);
        String captions = getContentCaptions(file);
        if (captions == null) return;

        CaptionsService.contentCaptions = captions;
        CaptionsService.captionsFileName = file.getName();

        Intent intent = new Intent(this, CaptionsService.class);
        startService(intent);
        captionsState = SETTING_OPENED;
    }

    private String getContentCaptions(File file) {
        try {
            long length = file.length();
            if (length <= 0 || length > 1024 * 1024) return null;
            byte[] bytes = new byte[(int) length];
            FileInputStream input = new FileInputStream(file);
            int read = input.read(bytes, 0, bytes.length);
            input.close();
            return read <= 0 ? null : new String(bytes, 0, read);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private boolean checkPermission() {
        // Check for all files access on newer Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.grant_permissions)
                    .setMessage(R.string.manage_files_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                        closeAlertDialog();
                        try {
                            startActivity(new Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Throwable th) {
                            startActivity(new Intent(
                                    Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    })
                    .setNegativeButton(R.string.exit, (dialog, id) -> finish())
                    .create();
            alertDialog.show();
            return false;
        }

        // Check storage permissions for older versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.grant_permissions)
                    .setMessage(R.string.files_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                FloatingPDFActivity.FILE_REQUEST_PERMISSION);
                        closeAlertDialog();
                    })
                    .setNegativeButton(R.string.exit, (dialog, id) -> finish())
                    .create();
            alertDialog.show();
            return false;
        }

        closeAlertDialog();
        return true;
    }

    private void closeAlertDialog() {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != FloatingPDFActivity.FILE_REQUEST_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            closeAlertDialog();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.permission_denied_grant_manually)
                    .setCancelable(false)
                    .setPositiveButton(R.string.settings, (dialog, id) -> {
                        Intent intent;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        } else {
                            intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
                        }
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                        closeAlertDialog();
                    })
                    .setNegativeButton(R.string.exit, (dialog, id) -> finish())
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK || checkPermission()) {
            closeAlertDialog();
        }
    }
}
