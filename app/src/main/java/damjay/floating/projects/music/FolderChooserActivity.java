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

public class FolderChooserActivity extends AppCompatActivity {
    private Button addDirectory;
    private TextView directoriesAdded;
    private EditText directoriesInput;
    private HashSet<String> fullPath;
    private Button savePrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_chooser);
        initializeViews();
    }

    private void initializeViews() {
        this.directoriesAdded = (TextView) findViewById(R.id.directories_added);
        this.directoriesInput = (EditText) findViewById(R.id.directories_field);
        this.addDirectory = (Button) findViewById(R.id.add_chosen_directories);
        this.savePrefs = (Button) findViewById(R.id.save_music_prefs);
        this.fullPath = new HashSet<>();
        this.addDirectory.setOnClickListener((v) -> {
            boolean notAccepted = false;
            String filePath = this.directoriesInput.getText().toString().trim();
            if (filePath.isEmpty()) {
                return;
            }
            File folder = new File(filePath);
            if (folder.exists() && folder.isDirectory()) {
                this.fullPath.add(filePath);
            } else {
                if (filePath.startsWith("/")) {
                    filePath = filePath.substring(1).trim();
                }
                File folder2 = new File("/storage/emulated/0/", filePath);
                if (folder2.exists() && folder2.isDirectory()) {
                    this.fullPath.add("/storage/emulated/0/" + filePath);
                } else {
                    notAccepted = true;
                    Toast.makeText(this, R.string.directory_not_exist, Toast.LENGTH_LONG).show();
                }
            }
            if (!notAccepted) {
                boolean firstFile = true;
                StringBuilder builder = null;
                for (String dir : this.fullPath) {
                    if (firstFile) {
                        firstFile = false;
                        builder = new StringBuilder(new File(dir).getName());
                    } else {
                        builder.append(", ").append(new File(dir).getName());
                    }
                }
                if (builder != null) {
                    this.directoriesAdded.setText(builder.toString());
                }
            }
        });
        this.savePrefs.setOnClickListener((v) -> {
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

    private String getDirectoriesText() {
        boolean firstFile = true;
        StringBuilder builder = null;
        for (String folder : this.fullPath) {
            if (firstFile) {
                firstFile = false;
                builder = new StringBuilder(folder);
            } else {
                builder.append("\n").append(folder);
            }
        }
        if (builder != null) {
            return builder.toString();
        }
        return null;
    }
}
