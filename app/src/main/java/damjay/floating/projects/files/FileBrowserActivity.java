package damjay.floating.projects.files;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.FileListAdapter;
import java.io.File;
import java.io.IOException;

public class FileBrowserActivity extends AppCompatActivity {
    public static FileCallback callback;
    public static String currentInput;
    ListView fileList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);
        getSupportActionBar().setTitle(callback.titleOfBrowser());
        this.fileList = (ListView) findViewById(R.id.fileList);
        View upButton = findViewById(R.id.traverseUp);
        this.fileList.setAdapter((ListAdapter) new FileListAdapter(this, validateInput()));
        this.fileList.setOnItemClickListener((a, v, position, id) -> {
            FileItem item = (FileItem) this.fileList.getItemAtPosition(position);
            if (item.isDirectory()) {
                FileListAdapter listAdapter = (FileListAdapter) this.fileList.getAdapter();
                listAdapter.updatePath(item.getFile());
                if (item.isDirectory()) {
                    this.fileList.setAdapter((ListAdapter) listAdapter);
                    return;
                }
                return;
            }
            if (item.getFileName().toLowerCase().endsWith("." + callback.extensionAllowed())) {
                showPDF(item.getFile());
            } else {
                new AlertDialog.Builder(this)
                        .setMessage(getResources().getString(
                                R.string.incorrect_format_message, callback.extensionAllowed().toUpperCase()))
                        .setPositiveButton(android.R.string.yes, (dialog, id1) -> showPDF(item.getFile()))
                        .setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null)
                        .show();
            }
        });
        upButton.setOnClickListener(view -> onBackPressed());
    }

    private File validateInput() {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        File file = new File(currentInput);
        if (file.isFile()) {
            file = file.getParentFile();
        }
        if (file == null || file.listFiles() == null) {
            return null;
        }
        return file;
    }

    @Override
    public void onBackPressed() {
        File parent = null;
        FileListAdapter listAdapter = (FileListAdapter) this.fileList.getAdapter();
        try {
            if (listAdapter.folder == null) {
                super.onBackPressed();
                callback = null;
                return;
            }
            if (listAdapter.getCount() == 0) {
                parent = listAdapter.folder.getParentFile();
            } else {
                parent = ((FileItem) this.fileList.getItemAtPosition(0)).getFile().getParentFile().getParentFile();
            }
            if (parent != null) {
                listAdapter.updatePath(parent);
                this.fileList.setAdapter((ListAdapter) listAdapter);
            }
        } catch (Throwable th) {
        }
    }

    public void showPDF(File file) {
        try {
            if (fileCallback != null) {
                fileCallback.fileCallback(file.getCanonicalPath());
                callback = null;
            }
        } catch (IOException e) {
        }
        super.onBackPressed();
    }

    public interface FileCallback {
        String extensionAllowed();

        void fileCallback(String str);

        default String titleOfBrowser() {
            return "Floating " + extensionAllowed().toUpperCase();
        }
    }
}
