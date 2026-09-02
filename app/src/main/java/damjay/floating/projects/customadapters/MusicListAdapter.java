package damjay.floating.projects.customadapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import damjay.floating.projects.R;

import java.io.File;
import java.util.ArrayList;

/**
 * Adapter for displaying music file listings with audio extension filtering.
 * Supports directory scanning and song history tracking.
 */
public class MusicListAdapter extends BaseAdapter {

    /** Supported audio file extensions */
    public static final String[] AUDIO_FILE_EXTENSIONS = {"mp3", "aac", "m4a", "wav", "ogg"};

    private final Context context;
    private final ArrayList<MusicFile> musicList = new ArrayList<>();

    public MusicListAdapter(Context context, ArrayList<String> chosenDirectories,
                             ArrayList<String> savedSongHistory) {
        this.context = context;
        initializeMusicList(savedSongHistory, chosenDirectories);
    }

    /** Initialize music list from saved history or chosen directories */
    private void initializeMusicList(ArrayList<String> savedSongHistory,
                                      ArrayList<String> chosenDirectories) {
        if (savedSongHistory != null && !savedSongHistory.isEmpty()) {
            for (String songPath : savedSongHistory) {
                musicList.add(new MusicFile(songPath));
            }
            return;
        }
        if (chosenDirectories != null) {
            for (String directory : chosenDirectories) {
                checkAudioFiles(new File(directory));
            }
        }
    }

    /** Recursively scan directory for audio files */
    private void checkAudioFiles(File parent) {
        File[] childFiles = parent.listFiles();
        if (childFiles == null) return;
        for (File file : childFiles) {
            if (!file.isDirectory()) {
                String fileName = file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    String extension = fileName.substring(dotIndex + 1).toLowerCase();
                    for (String audioExt : AUDIO_FILE_EXTENSIONS) {
                        if (audioExt.equals(extension)) {
                            musicList.add(new MusicFile(file));
                            break;
                        }
                    }
                }
            } else {
                checkAudioFiles(file);
            }
        }
    }

    @Override
    public int getCount() {
        return musicList.size();
    }

    @Override
    public Object getItem(int position) {
        return (musicList.size() <= position) ? null : musicList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        if (view == null) {
            view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
        }
        MusicFile musicFile = musicList.get(position);
        TextView text1 = view.findViewById(android.R.id.text1);
        TextView text2 = view.findViewById(android.R.id.text2);
        text1.setText(musicFile.getFileName());
        text2.setText(musicFile.getFullPath());
        return view;
    }

    /** Internal representation of a music file */
    public static class MusicFile {
        private final String directoryName;
        private final String fileName;
        private final String fullPath;

        MusicFile(File file) {
            this(file.getPath());
        }

        MusicFile(String path) {
            // fullPath is the complete path TO THE FILE -- MediaPlayer is handed this.
            // It used to be set to the parent directory instead, so every "play" attempt
            // pointed MediaPlayer at a folder and playback failed with "An error occurred".
            fullPath = path;
            int slash = path.lastIndexOf('/');
            fileName = slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
            String parentPath = slash > 0 ? path.substring(0, slash) : "";
            if ("/storage/emulated/0".equals(parentPath)) {
                directoryName = "Internal Storage Root";
            } else if (parentPath.isEmpty()) {
                directoryName = "Storage Root";
            } else {
                int parentSlash = parentPath.lastIndexOf('/');
                directoryName =
                        parentSlash >= 0 && parentSlash < parentPath.length() - 1
                                ? parentPath.substring(parentSlash + 1)
                                : parentPath;
            }
        }

        public String getFullPath() {
            return fullPath;
        }

        public String getDirectoryName() {
            return directoryName;
        }

        public String getFileName() {
            return fileName;
        }

        @Override
        public String toString() {
            return "fileName=" + fileName + ", fullPath=" + fullPath;
        }
    }
}
