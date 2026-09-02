package damjay.floating.projects.music;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import damjay.floating.projects.R;
import damjay.floating.projects.utils.ViewsUtils;

import java.io.File;
import java.util.HashSet;

/**
 * Activity for selecting and saving music folder preferences.
 * Allows users to add directory paths for music playback.
 */
public class FolderChooserActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "music_prefs";
    public static final String MUSIC_FOLDERS = "music_folders";

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
        // Start from whatever was saved previously, so re-opening the screen and adding one
        // more folder does not silently throw the old ones away.
        fullPath =
                new HashSet<>(
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .getStringSet(MUSIC_FOLDERS, new HashSet<>()));
        if (!fullPath.isEmpty()) {
            directoriesAdded.setText(folderNames());
        }

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
                directoriesAdded.setText(folderNames());
            }
        });

        savePrefs.setOnClickListener(v -> {
            if (fullPath.isEmpty()) {
                Toast.makeText(this, R.string.directory_not_exist, Toast.LENGTH_LONG).show();
                return;
            }
            // PlayerService reads the folders back from these preferences.
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putStringSet(MUSIC_FOLDERS, new HashSet<>(fullPath)).apply();
            if (ViewsUtils.requestAudioReadPermission(this, 200)) {
                startService(new Intent(this, PlayerService.class));
                finish();
            } else {
                // Stay open: the permission result callback starts the player and finishes
                // this screen once the user answers.
                Toast.makeText(this, R.string.music_needs_permission, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200
                && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startService(new Intent(this, PlayerService.class));
            finish();
        }
    }

    /** Comma-separated list of the chosen folder names, for the on-screen summary. */
    private String folderNames() {
        StringBuilder builder = new StringBuilder();
        boolean firstFile = true;
        for (String dir : fullPath) {
            if (firstFile) {
                firstFile = false;
            } else {
                builder.append(", ");
            }
            builder.append(new File(dir).getName());
        }
        return builder.toString();
    }
}
