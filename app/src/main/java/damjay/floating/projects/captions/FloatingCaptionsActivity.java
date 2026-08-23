package damjay.floating.projects.captions;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;

import damjay.floating.projects.FileSearchActivity;
import damjay.floating.projects.FloatingPDFActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.files.FileBrowserActivity;

public class FloatingCaptionsActivity extends AppCompatActivity {
    public static final String EXTENSION_ALLOWED = "srt";
    public static final int JUST_STARTING = 0;
    public static final int SETTING_OPENED = 1;
    public static final int MIN_CAPTIONS_TEXT_SIZE = 10;
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
        CaptionsService.watchSeekBar(findViewById(R.id.captions_size_seek_bar), findViewById(R.id.captions_size_text), sampleText);
        CaptionsService.watchColorFields(this, findViewById(R.id.red_rgb_field), findViewById(R.id.green_rgb_field), findViewById(R.id.blue_rgb_field), sampleText);
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
        File file = new File(filePathField.getText().toString());
        String captions = getContentCaptions(file);
        if (captions == null) {
            Toast.makeText(this, R.string.file_load_error, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, CaptionsService.class);
        intent.putExtra(CaptionsService.EXTRA_CAPTIONS, captions);
        intent.putExtra(CaptionsService.EXTRA_FILE_NAME, file.getName());
        intent.putExtra(CaptionsService.EXTRA_TEXT_SIZE, MIN_CAPTIONS_TEXT_SIZE + ((SeekBar) findViewById(R.id.captions_size_seek_bar)).getProgress());
        intent.putExtra(CaptionsService.EXTRA_TEXT_COLOR, getSelectedColor());
        startService(intent);
        captionsState = SETTING_OPENED;
    }

    private int getSelectedColor() {
        return Color.rgb(parseColor(findViewById(R.id.red_rgb_field)), parseColor(findViewById(R.id.green_rgb_field)), parseColor(findViewById(R.id.blue_rgb_field)));
    }

    private int parseColor(EditText field) {
        try {
            return Math.max(0, Math.min(255, Integer.parseInt(field.getText().toString())));
        } catch (NumberFormatException e) {
            return 0;
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.grant_permissions)
                    .setMessage(R.string.manage_files_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                        closeAlertDialog();
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
                    })
                    .setNegativeButton(R.string.exit, (dialog, id) -> finish())
                    .create();
            alertDialog.show();
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.grant_permissions)
                    .setMessage(R.string.files_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, id) -> requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 10))
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
}
