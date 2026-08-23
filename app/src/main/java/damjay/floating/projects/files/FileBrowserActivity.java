package damjay.floating.projects.files;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;

import damjay.floating.projects.FloatingPDFActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.FileListAdapter;

public class FileBrowserActivity extends AppCompatActivity {
    public static String currentInput;
    public static FileCallback callback;

    ListView fileList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        if (getSupportActionBar() != null) {
            if (callback == null) getSupportActionBar().setTitle(R.string.floating_pdf);
            else getSupportActionBar().setTitle(callback.titleOfBrowser());
        }

        fileList = findViewById(R.id.fileList);
        View upButton = findViewById(R.id.traverseUp);

        fileList.setAdapter(new FileListAdapter(this, validateInput()));

        fileList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int position, long id) {
                final FileItem item = (FileItem) fileList.getItemAtPosition(position);
                if (item.isDirectory()) {
                    FileListAdapter listAdapter = (FileListAdapter) fileList.getAdapter();
                    listAdapter.updatePath(item.getFile());
                    fileList.setAdapter(listAdapter);
                } else if (item.getFileName().toLowerCase().endsWith("." + getExtensionAllowed().toLowerCase())) {
                    showFile(item.getFile());
                } else {
                    new AlertDialog.Builder(FileBrowserActivity.this)
                            .setMessage(getString(R.string.incorrect_format_message, getExtensionAllowed()))
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    showFile(item.getFile());
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                }
            }
        });
        upButton.setOnClickListener(view -> onBackPressed());
    }

    private String getExtensionAllowed() {
        return callback == null ? "pdf" : callback.extensionAllowed();
    }

    private File validateInput() {
        if (currentInput == null || currentInput.trim().length() == 0)
            return null;
        File file = new File(currentInput);
        if (file.isFile()) file = file.getParentFile();
        if (file.listFiles() != null) return file;
        return null;
    }

    @Override
    public void onBackPressed() {
        File parent = null;
        FileListAdapter listAdapter = (FileListAdapter) fileList.getAdapter();
        try {
            if (listAdapter.folder == null) {
                super.onBackPressed();
                return;
            }
            if (listAdapter.getCount() == 0)
                parent = listAdapter.folder.getParentFile();
            else
                parent = ((FileItem) fileList.getItemAtPosition(0)).getFile().getParentFile().getParentFile();
        } catch (Throwable t) {
        }
        if (parent != null) {
            listAdapter.updatePath(parent);
            fileList.setAdapter(listAdapter);
        }
    }

    public void showFile(File file) {
        try {
            if (callback == null) FloatingPDFActivity.returnedPath = file.getCanonicalPath();
            else {
                callback.fileCallback(file.getCanonicalPath());
                callback = null;
            }
        } catch (IOException e) {}
        super.onBackPressed();
    }

    public void showPDF(File file) {
        showFile(file);
    }

    public interface FileCallback {
        void fileCallback(String path);

        default String titleOfBrowser() {
            return "Select File";
        }

        default String extensionAllowed() {
            return "pdf";
        }
    }
}
