package damjay.floating.projects.captions;

import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

public class FloatingCaptionsActivity extends AppCompatActivity {
    public static final String EXTENSION_ALLOWED = "srt";
    public static final int JUST_STARTING = 0;
    public static final int MIN_CAPTIONS_TEXT_SIZE = 10;
    public static final int SETTING_OPENED = 1;
    public static int captionsState = 0;
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
        filePathField = (TextView) findViewById(R.id.caption_file_path);
        findViewById(R.id.start_captions).setOnClickListener((v) -> {
            File captionsFile;
            String contentCaptions;
            String filePath = filePathField.getText().toString().trim();
            if (filePath.isEmpty()
                    || (contentCaptions = getContentCaptions((captionsFile = new File(filePath)))) == null) {
                return;
            }
            CaptionsService.contentCaptions = contentCaptions;
            CaptionsService.captionsFileName = captionsFile.getName();
            Intent intent = new Intent(this, CaptionsService.class);
            startService(intent);
        });
        findViewById(R.id.search_files).setOnClickListener((v) -> {
            FileSearchActivity.callback = getCaptionsCallback();
            Intent intent = new Intent(this, FileSearchActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.browse_files).setOnClickListener((v) -> {
            FileBrowserActivity.callback = getCaptionsCallback();
            FileBrowserActivity.currentInput = filePathField.getText().toString();
            Intent intent = new Intent(this, FileBrowserActivity.class);
            startActivity(intent);
        });
        TextView sampleText = (TextView) findViewById(R.id.sample_text);
        CaptionsService.watchSeekBar((SeekBar) findViewById(R.id.captions_size_seek_bar),
                (TextView) findViewById(R.id.captions_size_text), sampleText);
        CaptionsService.watchColorFields(this, (EditText) findViewById(R.id.red_rgb_field),
                (EditText) findViewById(R.id.green_rgb_field), (EditText) findViewById(R.id.blue_rgb_field),
                sampleText);
    }

    private FileBrowserActivity.FileCallback getCaptionsCallback() {
        return new FileBrowserActivity.FileCallback() {
            @Override
            public void fileCallback(String filePath) {
                FloatingCaptionsActivity.this.filePathField.setText(filePath);
            }

            @Override
            public String extensionAllowed() {
                return FloatingCaptionsActivity.EXTENSION_ALLOWED;
            }

            @Override
            public String titleOfBrowser() {
                return FloatingCaptionsActivity.this.getResources().getString(R.string.floating_captions);
            }
        };
    }

    private String getContentCaptions(File captionsFile) {
        try {
            long fileLength = captionsFile.length();
            if (fileLength <= 1024 * 1024 && fileLength > 0) {
                byte[] captionsBytes = new byte[(int) fileLength];
                FileInputStream captionsStream = new FileInputStream(captionsFile);
                int read = captionsStream.read(captionsBytes, 0, captionsBytes.length);
                try {
                    captionsStream.close();
                } catch (Throwable th) {
                }
                if (read == 0) {
                    return null;
                }
                return new String(captionsBytes, 0, read);
            }
            return null;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            if (alertDialog != null) {
                return false;
            }
            AlertDialog alertDialogCreate =
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.grant_permissions)
                            .setMessage(R.string.manage_files_permission_message)
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok,
                                    (dialog, id) -> {
                                        try {
                                            Intent intent = new Intent(
                                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                            intent.setData(Uri.parse(String.format(
                                                    "package:%s", getApplicationContext().getPackageName())));
                                            startActivityForResult(intent, FloatingPDFActivity.FILE_REQUEST_PERMISSION);
                                        } catch (Throwable th) {
                                            Intent intent2 = new Intent();
                                            intent2.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                            startActivityForResult(intent2, FloatingPDFActivity.FILE_REQUEST_PERMISSION);
                                        }
                                        closeAlertDialog();
                                    })
                            .setNegativeButton(R.string.exit, FloatingPDFActivity.DIALOG_EXIT_LISTENER)
                            .create();
            alertDialog = alertDialogCreate;
            alertDialogCreate.show();
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                && checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
            if (alertDialog != null) {
                return false;
            }
            AlertDialog alertDialogCreate2 =
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.grant_permissions)
                            .setMessage(R.string.files_permission_message)
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok,
                                    (dialog, id) -> {
                                        ActivityCompat.requestPermissions(this,
                                                new String[] {"android.permission.READ_EXTERNAL_STORAGE",
                                                        "android.permission.WRITE_EXTERNAL_STORAGE"},
                                                101);
                                        closeAlertDialog();
                                    })
                            .setNegativeButton(R.string.exit, FloatingPDFActivity.DIALOG_EXIT_LISTENER)
                            .create();
            alertDialog = alertDialogCreate2;
            alertDialogCreate2.show();
            return false;
        }
        closeAlertDialog();
        return true;
    }

    private void closeAlertDialog() {
        AlertDialog alertDialog = alertDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != FloatingPDFActivity.FILE_REQUEST_PERMISSION) {
            return;
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            closeAlertDialog();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.permission_denied_grant_manually)
                    .setCancelable(false)
                    .setPositiveButton(R.string.settings,
                            (dialog, id) -> {
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
