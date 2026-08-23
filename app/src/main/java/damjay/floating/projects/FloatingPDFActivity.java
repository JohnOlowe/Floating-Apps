package damjay.floating.projects;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.HistorySimpleAdapter;
import damjay.floating.projects.files.FileBrowserActivity;
import damjay.floating.projects.files.FileItem;
import damjay.floating.projects.utils.FormatUtils;
import damjay.floating.projects.utils.IOUtils;
import damjay.floating.projects.utils.ViewsUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Floating PDF viewer activity supporting local files and URL intent viewing.
 * Manages file history, permission requests, and overlay display.
 */
public class FloatingPDFActivity extends AppCompatActivity {

    /** File extension for PDF documents */
    public static final String PDF_EXTENSION = "pdf";
    public static final String HISTORY_EXTENSION = ".hst";
    public static final String HISTORY_FILE = "history" + HISTORY_EXTENSION;

    /** Request codes for permission handling */
    private static final int FLOAT_PERMISSION_REQUEST = 100;
    private static final int FILE_REQUEST_PERMISSION = 101;

    /** Global listener for dialog exit actions */
    public static DialogInterface.OnClickListener DIALOG_EXIT_LISTENER;

    /** Path returned from file browser for selection */
    public static String returnedPath;

    private File[] files;
    private EditText filePath;
    private AlertDialog alertDialog;

    /** Initialize dialog exit listener */
    {
        DIALOG_EXIT_LISTENER = (dialog, id) -> finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (viewIntent()) {
            return;
        }
        setContentView(R.layout.activity_floating_pdf);
        getSupportActionBar().setTitle(R.string.floating_pdf);
        initializeViews();
        checkPermission();
    }

    /** Handle VIEW intent for opening PDF from external source */
    private boolean viewIntent() {
        Intent intent = getIntent();
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }
        Uri uri = intent.getData();
        if (uri == null) return false;
        String name = IOUtils.getFileName(this, uri);
        if (name == null) {
            new Throwable("Name is null").printStackTrace();
            return false;
        }
        File newFile = new File(getCacheDir(), name);
        if (!IOUtils.safeCopy(this, uri, newFile)) {
            Toast.makeText(this, R.string.file_read_error, Toast.LENGTH_LONG).show();
            return false;
        }
        return openFloatingPDF(null, newFile);
    }

    /** Initialize UI components and attach event handlers */
    private void initializeViews() {
        try {
            filePath = findViewById(R.id.file_path);
            Button loadFileButton = findViewById(R.id.selectFile);
            Button selectFiles = findViewById(R.id.browseFile);
            Button searchFiles = findViewById(R.id.searchFile);

            populateList();

            loadFileButton.setOnClickListener(v -> {
                String text = filePath.getText().toString();
                if (text.trim().isEmpty()) {
                    Toast.makeText(FloatingPDFActivity.this, R.string.invalid_path_message,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                openFloatingPDF(new File(text), null);
            });

            selectFiles.setOnClickListener(v -> {
                FileBrowserActivity.currentInput = filePath.getText().toString();
                FileBrowserActivity.callback = getDefaultCallback();
                Intent intent = new Intent(this, FileBrowserActivity.class);
                startActivity(intent);
            });

            searchFiles.setOnClickListener(v -> {
                FileSearchActivity.callback = getDefaultCallback();
                Intent intent = new Intent(this, FileSearchActivity.class);
                startActivity(intent);
            });
        } catch (Throwable report) {
            report.printStackTrace();
        }
    }

    /** Default callback for file browser selection with PDF extension filter */
    private FileBrowserActivity.FileCallback getDefaultCallback() {
        return new FileBrowserActivity.FileCallback() {
            @Override
            public void fileCallback(String filePath) {
                FloatingPDFActivity.returnedPath = filePath;
            }

            @Override
            public String extensionAllowed() {
                return PDF_EXTENSION;
            }
        };
    }

    /** Launch PDF reader service after validating file */
    private boolean openFloatingPDF(File file, File newFile) {
        try {
            if (newFile == null) {
                if (file == null || !file.exists()) {
                    Toast.makeText(this, R.string.file_load_error, Toast.LENGTH_LONG).show();
                    return false;
                }
                newFile = copyToCache(file);
                if (newFile == null) {
                    Toast.makeText(this, R.string.file_load_error, Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            try {
                PDFReaderService.savePathHistory(getCacheDir(), newFile, 0);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            if (PDFReaderService.pdfFile != null) {
                stopService(new Intent(this, PDFReaderService.class));
            }
            PDFReaderService.pdfFile = newFile;
            return launchService();
        } catch (Throwable t) {
            Toast.makeText(this, R.string.file_load_error, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** Populate file history list from cache directory */
    private void populateList() {
        ListView listView = findViewById(R.id.fileHistory);
        ArrayList<HashMap<String, Object>> list = new ArrayList<>();
        ArrayList<File> fileList = new ArrayList<>();
        String[] entries = {"fileName", "fileInfo"};

        try {
            File[] filesInCache = getCacheDir().listFiles();
            files = filesInCache;
            if (filesInCache == null) return;

            for (File file : filesInCache) {
                HashMap<String, Object> map = new HashMap<>();
                if (file.getName().toLowerCase().endsWith(HISTORY_EXTENSION))
                    continue;
                map.put(entries[0], file.getName());
                map.put(entries[1], FormatUtils.formatDate(file.lastModified())
                        + ", " + FormatUtils.formatSize(file.length()));
                fileList.add(file);
                list.add(map);
            }

            files = fileList.toArray(new File[0]);

            SimpleAdapter adapter = new HistorySimpleAdapter(
                    this, list, R.layout.history_files, entries,
                    new int[]{R.id.file_name, R.id.file_info},
                    new HistorySimpleAdapter.Callback() {
                        @Override
                        public void delete(final int position) {
                            new AlertDialog.Builder(FloatingPDFActivity.this)
                                    .setMessage(R.string.delete_file_confirm)
                                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                                        if (files[position].delete()) {
                                            populateList();
                                        } else {
                                            Toast.makeText(FloatingPDFActivity.this,
                                                    R.string.delete_file_error, Toast.LENGTH_LONG).show();
                                        }
                                    })
                                    .setNegativeButton(android.R.string.no, null)
                                    .show();
                        }

                        @Override
                        public void run(final int position) {
                            if (files == null) return;
                            openFloatingPDF(null, files[position]);
                        }
                    });
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                    if (files == null) return;
                    openFloatingPDF(null, files[position]);
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Copy source file to app cache directory */
    private File copyToCache(File file) {
        File createdFile = new File(getCacheDir(), file.getName());
        try {
            FileInputStream inputStream = new FileInputStream(file);
            FileOutputStream outputStream = new FileOutputStream(createdFile);
            byte[] buffer = new byte[1024 * 50];
            int read;
            while ((read = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            outputStream.close();
            return createdFile;
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
        return null;
    }

    /** Launch PDF reader service if permissions are granted */
    public boolean launchService() {
        if (checkPermission()) {
            startService(new Intent(this, PDFReaderService.class));
            finish();
            return true;
        }
        return false;
    }

    /** Check overlay and file permissions for floating PDF */
    private boolean checkPermission() {
        // Check overlay permission (required for floating window)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.grant_permissions)
                    .setMessage(R.string.display_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.settings, (dialog, id) -> {
                        Intent intent = new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, FLOAT_PERMISSION_REQUEST);
                    })
                    .setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER)
                    .create();
            alertDialog.show();
            return false;
        }

        // Check all files access for newer Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.grant_permissions)
                    .setMessage(R.string.manage_files_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                        try {
                            Intent intent = new Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, FILE_REQUEST_PERMISSION);
                        } catch (Throwable th) {
                            Intent fallback = new Intent();
                            fallback.setAction(
                                    Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivityForResult(fallback, FILE_REQUEST_PERMISSION);
                        }
                        closeAlertDialog();
                    })
                    .setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER)
                    .create();
            alertDialog.show();
            return false;
        }

        // Check read/write storage permissions for older versions
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
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            ActivityCompat.requestPermissions(
                                    FloatingPDFActivity.this,
                                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    FILE_REQUEST_PERMISSION);
                        } else {
                            ViewsUtils.openAppInfo(this, getPackageName());
                        }
                        closeAlertDialog();
                    })
                    .setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER)
                    .create();
            alertDialog.show();
            return false;
        }

        closeAlertDialog();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeAlertDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (filePath != null && returnedPath != null) {
            filePath.setText(returnedPath);
            returnedPath = null;
        }
        checkPermission();
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
        if (requestCode != FILE_REQUEST_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            closeAlertDialog();
            viewIntent();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.permission_denied_grant_manually)
                    .setCancelable(false)
                    .setPositiveButton(R.string.settings, (dialog, id) -> {
                        Intent intent;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            intent = new Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        } else {
                            intent = new Intent(
                                    Settings.ACTION_APPLICATION_SETTINGS);
                        }
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                        closeAlertDialog();
                    })
                    .setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case FILE_REQUEST_PERMISSION:
            case FLOAT_PERMISSION_REQUEST:
                if (resultCode == RESULT_OK) {
                    closeAlertDialog();
                    viewIntent();
                } else {
                    if (checkPermission()) viewIntent();
                }
                break;
        }
    }
}
