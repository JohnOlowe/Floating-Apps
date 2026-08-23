package damjay.floating.projects.music;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import damjay.floating.projects.R;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;

/**
 * Activity for selecting and saving music folder preferences.
 * Allows users to add directory paths for music playback.
 */
public class FolderChooserActivity extends AppCompatActivity {

    private Button addDirectory;
    private TextView directoriesAdded;
    private EditText directoriesInput;
    private HashSet<String> fullPath;
    private Button savePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_chooser);
        initializeViews();
    }

    private void initializeViews() {
        directoriesAdded = findViewById(R.id.directories_added);
        directoriesInput = findViewById(R.id.directories_field);
        addDirectory = findViewById(R.id.add_chosen_directories);
        savePrefs = findViewById(R.id.save_music_prefs);
        fullPath = new HashSet<>();

        addDirectory.setOnClickListener(v -> {
            boolean notAccepted = false;
            String filePath = directoriesInput.getText().toString().trim();
            if (filePath.isEmpty()) {
                return;
            }
            File folder = new File(filePath);
            if (folder.exists() && folder.isDirectory()) {
                fullPath.add(filePath);
            } else {
                if (filePath.startsWith("/")) {
                    filePath = filePath.substring(1).trim();
                }
                File folder2 = new File("/storage/emulated/0/", filePath);
                if (folder2.exists() && folder2.isDirectory()) {
                    fullPath.add("/storage/emulated/0/" + filePath);
                } else {
                    notAccepted = true;
                    Toast.makeText(this, R.string.directory_not_exist, Toast.LENGTH_LONG).show();
                }
            }
            if (!notAccepted) {
                StringBuilder builder = new StringBuilder();
                boolean firstFile = true;
                for (String dir : fullPath) {
                    if (firstFile) {
                        firstFile = false;
                        builder.append(new File(dir).getName());
                    } else {
                        builder.append(", ").append(new File(dir).getName());
                    }
                }
                directoriesAdded.setText(builder.toString());
            }
        });

        savePrefs.setOnClickListener(v -> {
            File fileSafe = new File(getCacheDir(), PlayerService.CHOSEN_FOLDERS_FILE);
            String text = getDirectoriesText();
            if (text == null) {
                return;
            }
            try {
                FileOutputStream writer = new FileOutputStream(fileSafe);
                writer.write(text.getBytes());
                writer.close();
                Intent intent = new Intent();
                intent.setClass(this, PlayerService.class);
                startService(intent);
            } catch (Throwable th) {
                Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Build newline-separated directory path string from selected folders */
    private String getDirectoriesText() {
        StringBuilder builder = null;
        boolean firstFile = true;
        for (String folder : fullPath) {
            if (firstFile) {
                firstFile = false;
                builder = new StringBuilder(folder);
            } else {
                builder.append("\n").append(folder);
            }
        }
        return builder != null ? builder.toString() : null;
    }
}
