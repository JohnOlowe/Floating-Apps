package damjay.floating.projects;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import damjay.floating.projects.customadapters.HistorySimpleAdapter;
import damjay.floating.projects.files.FileBrowserActivity;
import damjay.floating.projects.utils.FormatUtils;
import damjay.floating.projects.utils.IOUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class FloatingPDFActivity extends AppCompatActivity {
    public static DialogInterface.OnClickListener DIALOG_EXIT_LISTENER = null;
    public static final int FILE_REQUEST_PERMISSION = 101;
    private static final int FLOAT_PERMISSION_REQUEST = 100;
    public static final String HISTORY_EXTENSION = ".hst";
    public static final String HISTORY_FILE = "history.hst";
    public static final String PDF_EXTENSION = "pdf";
    public static String returnedPath;
    private AlertDialog alertDialog;
    private EditText filePath;
    private File[] files;

    public FloatingPDFActivity() {
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

    private boolean viewIntent() {
        Uri uri;
        Intent intent = getIntent();
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || (uri = intent.getData()) == null) {
            return false;
        }
        String name = IOUtils.getFileName(this, uri);
        if (name == null) {
            new Throwable("Name is null").printStackTrace();
            return false;
        }
        File newFile = new File(getCacheDir(), name);
        if (!IOUtils.safeCopy(this, uri, newFile)) {
            Toast.makeText(this, R.string.file_read_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        return openFloatingPDF(null, newFile);
    }

    private void initializeViews() {
        try {
            filePath = (EditText) findViewById(R.id.file_path);
            Button loadFileButton = (Button) findViewById(R.id.selectFile);
            Button selectFiles = (Button) findViewById(R.id.browseFile);
            Button searchFiles = (Button) findViewById(R.id.searchFile);
            populateList();
            loadFileButton.setOnClickListener((v) -> {
                String text = filePath.getText().toString();
                if (text.trim().isEmpty()) {
                    Toast.makeText(this, R.string.invalid_path_message, Toast.LENGTH_SHORT).show();
                } else {
                    openFloatingPDF(new File(text), null);
                }
            });
            selectFiles.setOnClickListener((v) -> {
                FileBrowserActivity.currentInput = filePath.getText().toString();
                FileBrowserActivity.callback = getDefaultCallback();
                startActivity(new Intent(this, FileBrowserActivity.class));
            });
            searchFiles.setOnClickListener((v) -> {
                FileSearchActivity.callback = getDefaultCallback();
                startActivity(new Intent(this, FileSearchActivity.class));
            });
        } catch (Throwable report) {
            report.printStackTrace();
        }
    }

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

    public boolean openFloatingPDF(File file, File newFile) {
        if (newFile == null) {
            if (file != null && file.exists()) {
                newFile = copyToCache(file);
                if (newFile == null) {
                    Toast.makeText(this, R.string.file_load_error, Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            Toast.makeText(this, R.string.file_load_error, Toast.LENGTH_LONG).show();
            return false;
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
    }

    public void populateList() {
        ListView listView = (ListView) findViewById(R.id.fileHistory);
        ArrayList<HashMap<String, Object>> list = new ArrayList<>();
        ArrayList<File> fileList = new ArrayList<>();
        String[] entries = {"fileName", "fileInfo"};
        try {
            File[] cacheFiles = getCacheDir().listFiles();
            files = cacheFiles;
            if (cacheFiles == null) {
                return;
            }
            for (File file : cacheFiles) {
                if (!file.getName().toLowerCase().endsWith(HISTORY_EXTENSION)) {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put(entries[0], file.getName());
                    map.put(entries[1],
                            FormatUtils.formatDate(file.lastModified()) + ", " + FormatUtils.formatSize(file.length()));
                    fileList.add(file);
                    list.add(map);
                }
            }
            files = fileList.toArray(new File[0]);
            SimpleAdapter adapter = new HistorySimpleAdapter(this, list, R.layout.history_files, entries,
                    new int[] {R.id.file_name, R.id.file_info}, new HistorySimpleAdapter.Callback() {
                        @Override
                        public void delete(int position) {
                            new AlertDialog.Builder(FloatingPDFActivity.this)
                                    .setMessage(R.string.delete_file_confirm)
                                    .setPositiveButton(android.R.string.yes,
                                            (dialog, id) -> {
                                                if (files[position].delete()) {
                                                    populateList();
                                                } else {
                                                    Toast.makeText(FloatingPDFActivity.this,
                                                            R.string.delete_file_error, Toast.LENGTH_SHORT).show();
                                                }
                                            })
                                    .setNegativeButton(android.R.string.no, null)
                                    .show();
                        }

                        @Override
                        public void run(int position) {
                            if (files == null) {
                                return;
                            }
                            openFloatingPDF(null, files[position]);
                        }
                    });
            listView.setAdapter(adapter);
            listView.setOnItemClickListener((adapterView, view, position, id) -> {
                if (files != null) {
                    openFloatingPDF(null, files[position]);
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private File copyToCache(File file) {
        File createdFile = new File(getCacheDir(), file.getName());
        try {
            FileInputStream inputStream = new FileInputStream(file);
            FileOutputStream outputStream = new FileOutputStream(createdFile);
            byte[] buffer = new byte[50 * 1024];
            while (true) {
                int read = inputStream.read(buffer);
                if (read > 0) {
                    outputStream.write(buffer, 0, read);
                } else {
                    inputStream.close();
                    outputStream.close();
                    return createdFile;
                }
            }
        } catch (Throwable th) {
            return null;
        }
    }

    public boolean launchService() {
        if (checkPermission()) {
            startService(new Intent(this, PDFReaderService.class));
            finish();
            return true;
        }
        return false;
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            if (alertDialog != null) {
                return false;
            }
            alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.grant_permissions)
                    .setMessage(R.string.display_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.settings,
                            (dialog, id) -> {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + getPackageName()));
                                startActivityForResult(intent, FLOAT_PERMISSION_REQUEST);
                            })
                    .setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER)
                    .create();
            alertDialog.show();
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            if (alertDialog != null) {
                return false;
            }
            alertDialog = new AlertDialog.Builder(this)
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
                                    startActivityForResult(intent, FILE_REQUEST_PERMISSION);
                                } catch (Throwable th) {
                                    Intent intent = new Intent();
                                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                    startActivityForResult(intent, FILE_REQUEST_PERMISSION);
                                }
                                closeAlertDialog();
                            })
                    .setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER)
                    .create();
            alertDialog.show();
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                && checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE")
                        != PackageManager.PERMISSION_GRANTED) {
            if (alertDialog != null) {
                return false;
            }
            alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.grant_permissions)
                    .setMessage(R.string.files_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, id) -> {
                                ActivityCompat.requestPermissions(this,
                                        new String[] {"android.permission.READ_EXTERNAL_STORAGE",
                                                "android.permission.WRITE_EXTERNAL_STORAGE"},
                                        FILE_REQUEST_PERMISSION);
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
        if (requestCode != FILE_REQUEST_PERMISSION) {
            return;
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            closeAlertDialog();
            viewIntent();
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
                                intent.setData(Uri.fromParts("package", getPackageName(), null));
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
        if (requestCode == FLOAT_PERMISSION_REQUEST || requestCode == FILE_REQUEST_PERMISSION) {
            if (resultCode == RESULT_OK) {
                closeAlertDialog();
                viewIntent();
            } else if (checkPermission()) {
                viewIntent();
            }
        }
    }
}
