package damjay.floating.projects.music;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

import damjay.floating.projects.R;

public class FolderChooserActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "music_prefs";
    public static final String MUSIC_FOLDERS = "music_folders";

    private final ArrayList<String> folders = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_chooser);
        loadFolders();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, folders);
        ((ListView) findViewById(R.id.music_folders)).setAdapter(adapter);
        EditText field = findViewById(R.id.folder_path);
        field.setText(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
        findViewById(R.id.add_music_folder).setOnClickListener(v -> addFolder(field.getText().toString()));
        findViewById(R.id.save_music_prefs).setOnClickListener(v -> {
            saveFolders();
            finish();
        });
    }

    private void addFolder(String path) {
        File file = new File(path);
        if (!file.exists() || !file.isDirectory()) {
            Toast.makeText(this, R.string.directory_not_exist, Toast.LENGTH_LONG).show();
            return;
        }
        if (!folders.contains(file.getAbsolutePath())) {
            folders.add(file.getAbsolutePath());
            adapter.notifyDataSetChanged();
        }
    }

    private void loadFolders() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        folders.addAll(prefs.getStringSet(MUSIC_FOLDERS, new HashSet<>()));
        if (folders.isEmpty()) folders.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
    }

    private void saveFolders() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putStringSet(MUSIC_FOLDERS, new HashSet<>(folders)).apply();
    }
}
