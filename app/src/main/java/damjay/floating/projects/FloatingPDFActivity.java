package damjay.floating.projects;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
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
        DIALOG_EXIT_LISTENER = (dialogInterface, i) -> this.m104lambda$new$0$damjayfloatingprojectsFloatingPDFActivity(dialogInterface, i);
    }

    void m104lambda$new$0$damjayfloatingprojectsFloatingPDFActivity(DialogInterface dialog, int id) {
        finish();
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
        if (!"android.intent.action.VIEW".equals(intent.getAction()) || (uri = intent.getData()) == null) {
            return false;
        }
        String name = IOUtils.getFileName(this, uri);
        if (name == null) {
            new Throwable("Name is null").printStackTrace();
            return false;
        }
        File newFile = new File(getCacheDir(), name);
        if (!IOUtils.safeCopy(this, uri, newFile)) {
            Toast.makeText(this, R.string.file_read_error, 0).show();
            return false;
        }
        return openFloatingPDF(null, newFile);
    }

    private void initializeViews() {
        try {
            this.filePath = (EditText) findViewById(R.id.file_path);
            Button loadFileButton = (Button) findViewById(R.id.selectFile);
            Button selectFiles = (Button) findViewById(R.id.browseFile);
            Button searchFiles = (Button) findViewById(R.id.searchFile);
            populateList();
            loadFileButton.setOnClickListener((view) -> this.m101xebe72a6a(view));
            selectFiles.setOnClickListener((view) -> this.m102xa55eb809(view));
            searchFiles.setOnClickListener((view) -> this.m103x5ed645a8(view));
        } catch (Throwable report) {
            report.printStackTrace();
        }
    }

    void m101xebe72a6a(View v) {
        String text = this.filePath.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, R.string.invalid_path_message, 0).show();
        } else {
            openFloatingPDF(new File(text), null);
        }
    }

    void m102xa55eb809(View v) {
        FileBrowserActivity.currentInput = this.filePath.getText().toString();
        FileBrowserActivity.callback = getDefaultCallback();
        Intent intent = new Intent(this, (Class<?>) FileBrowserActivity.class);
        startActivity(intent);
    }

    void m103x5ed645a8(View v) {
        FileSearchActivity.callback = getDefaultCallback();
        Intent intent = new Intent(this, (Class<?>) FileSearchActivity.class);
        startActivity(intent);
    }

    class AnonymousClass1 implements FileBrowserActivity.FileCallback {
        @Override
        public String titleOfBrowser() {
            return FileBrowserActivity.FileCallback.CC.$default$titleOfBrowser(this);
        }

        AnonymousClass1() {
        }

        @Override
        public void fileCallback(String filePath) {
            FloatingPDFActivity.returnedPath = filePath;
        }

        @Override
        public String extensionAllowed() {
            return FloatingPDFActivity.PDF_EXTENSION;
        }
    }

    private FileBrowserActivity.FileCallback getDefaultCallback() {
        return new AnonymousClass1();
    }

    public boolean openFloatingPDF(File file, File newFile) {
        if (newFile == null) {
            if (file != null) {
                try {
                    if (file.exists()) {
                        newFile = copyToCache(file);
                        if (newFile == null) {
                            Toast.makeText(this, R.string.file_load_error, 1).show();
                            return false;
                        }
                    }
                } catch (Throwable th) {
                    Toast.makeText(this, R.string.file_load_error, 1).show();
                    return false;
                }
            }
            Toast.makeText(this, R.string.file_load_error, 1).show();
            return false;
        }
        try {
            PDFReaderService.savePathHistory(getCacheDir(), newFile, 0);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (PDFReaderService.pdfFile != null) {
            stopService(new Intent(this, (Class<?>) PDFReaderService.class));
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
            File[] fileArrListFiles = getCacheDir().listFiles();
            this.files = fileArrListFiles;
            if (fileArrListFiles == null) {
                return;
            }
            for (File file : fileArrListFiles) {
                HashMap<String, Object> map = new HashMap<>();
                if (!file.getName().toLowerCase().endsWith(HISTORY_EXTENSION)) {
                    map.put(entries[0], file.getName());
                    map.put(entries[1], FormatUtils.formatDate(file.lastModified()) + ", " + FormatUtils.formatSize(file.length()));
                    fileList.add(file);
                    list.add(map);
                }
            }
            this.files = (File[]) fileList.toArray(new File[0]);
            SimpleAdapter adapter = new HistorySimpleAdapter(this, list, R.layout.history_files, entries, new int[]{R.id.file_name, R.id.file_info}, new AnonymousClass2());
            listView.setAdapter((ListAdapter) adapter);
            listView.setOnItemClickListener((adapterView, view, i, j) -> this.m106x26cf4243(adapterView, view, i, j));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    class AnonymousClass2 implements HistorySimpleAdapter.Callback {
        AnonymousClass2() {
        }

        @Override
        public void delete(int position) {
            new AlertDialog.Builder(FloatingPDFActivity.this).setMessage(R.string.delete_file_confirm).setPositiveButton(android.R.string.yes, (dialogInterface, i) -> this.m107lambda$delete$0$damjayfloatingprojectsFloatingPDFActivity$2(position, dialogInterface, i)).setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null).show();
        }

        void m107lambda$delete$0$damjayfloatingprojectsFloatingPDFActivity$2(int position, DialogInterface dialog, int id) {
            if (FloatingPDFActivity.this.files[position].delete()) {
                FloatingPDFActivity.this.populateList();
            } else {
                Toast.makeText(FloatingPDFActivity.this, R.string.delete_file_error, 1).show();
            }
        }

        @Override
        public void run(int position) {
            if (FloatingPDFActivity.this.files == null) {
                return;
            }
            FloatingPDFActivity floatingPDFActivity = FloatingPDFActivity.this;
            floatingPDFActivity.openFloatingPDF(null, floatingPDFActivity.files[position]);
        }
    }

    void m106x26cf4243(AdapterView adapterView, View view, int position, long id) {
        File[] fileArr = this.files;
        if (fileArr == null) {
            return;
        }
        openFloatingPDF(null, fileArr[position]);
    }

    private File copyToCache(File file) {
        File createdFile = new File(getCacheDir(), file.getName());
        try {
            FileInputStream inputStream = new FileInputStream(file);
            FileOutputStream outputStream = new FileOutputStream(createdFile);
            byte[] buffer = new byte[51200];
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
            startService(new Intent(this, (Class<?>) PDFReaderService.class));
            finish();
            return true;
        }
        return false;
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            if (this.alertDialog != null) {
                return false;
            }
            AlertDialog alertDialogCreate = new AlertDialog.Builder(this).setTitle(R.string.grant_permissions).setMessage(R.string.display_permission_message).setCancelable(false).setPositiveButton(R.string.settings, (dialogInterface, i) -> this.m98x2bce278d(dialogInterface, i)).setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER).create();
            this.alertDialog = alertDialogCreate;
            alertDialogCreate.show();
            return false;
        }
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            if (this.alertDialog != null) {
                return false;
            }
            AlertDialog alertDialogCreate2 = new AlertDialog.Builder(this).setTitle(R.string.grant_permissions).setMessage(R.string.manage_files_permission_message).setCancelable(false).setPositiveButton(android.R.string.ok, (dialogInterface, i) -> this.m99xe545b52c(dialogInterface, i)).setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER).create();
            this.alertDialog = alertDialogCreate2;
            alertDialogCreate2.show();
            return false;
        }
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 30 && checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != 0) {
            if (this.alertDialog != null) {
                return false;
            }
            AlertDialog alertDialogCreate3 = new AlertDialog.Builder(this).setTitle(R.string.grant_permissions).setMessage(R.string.files_permission_message).setCancelable(false).setPositiveButton(android.R.string.ok, (dialogInterface, i) -> this.m100x9ebd42cb(dialogInterface, i)).setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER).create();
            this.alertDialog = alertDialogCreate3;
            alertDialogCreate3.show();
            return false;
        }
        closeAlertDialog();
        return true;
    }

    void m98x2bce278d(DialogInterface dialog, int id) {
        Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, 100);
    }

    void m99xe545b52c(DialogInterface dialog, int id) {
        try {
            Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
            startActivityForResult(intent, 101);
        } catch (Throwable th) {
            Intent intent2 = new Intent();
            intent2.setAction("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
            startActivityForResult(intent2, 101);
        }
        closeAlertDialog();
    }

    void m100x9ebd42cb(DialogInterface dialog, int id) {
        ActivityCompat.requestPermissions(this, new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"}, 101);
        closeAlertDialog();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeAlertDialog();
    }

    @Override
    protected void onResume() {
        String str;
        super.onResume();
        EditText editText = this.filePath;
        if (editText != null && (str = returnedPath) != null) {
            editText.setText(str);
            returnedPath = null;
        }
        checkPermission();
    }

    private void closeAlertDialog() {
        AlertDialog alertDialog = this.alertDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
            this.alertDialog = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 101) {
            return;
        }
        if (grantResults[0] == 0) {
            closeAlertDialog();
            viewIntent();
        } else {
            new AlertDialog.Builder(this).setMessage(R.string.permission_denied_grant_manually).setCancelable(false).setPositiveButton(R.string.settings, (dialogInterface, i) -> this.m105xbba11916(dialogInterface, i)).setNegativeButton(R.string.exit, DIALOG_EXIT_LISTENER).show();
        }
    }

    void m105xbba11916(DialogInterface dialog, int id) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 30) {
            intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
        } else {
            intent = new Intent("android.settings.APPLICATION_SETTINGS");
        }
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
        closeAlertDialog();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 100:
            case 101:
                if (resultCode == -1) {
                    closeAlertDialog();
                    viewIntent();
                } else if (checkPermission()) {
                    viewIntent();
                }
                break;
        }
    }
}
