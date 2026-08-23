package damjay.floating.projects.customadapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

public class MusicListAdapter extends BaseAdapter {
    public static final String[] AUDIO_FILE_EXTENSIONS = {"mp3", "aac", "m4a", "wav", "ogg"};

    private final Context context;
    private final ArrayList<MusicFile> musicList = new ArrayList<>();

    public MusicListAdapter(Context context, ArrayList<String> chosenDirectories, ArrayList<String> savedSongs) {
        this.context = context;
        initializeMusicList(savedSongs, chosenDirectories);
    }

    private void initializeMusicList(ArrayList<String> savedSongs, ArrayList<String> chosenDirectories) {
        if (savedSongs != null && savedSongs.size() > 0) {
            for (String path : savedSongs) musicList.add(new MusicFile(path));
        } else if (chosenDirectories != null) {
            for (String path : chosenDirectories) checkAudioFiles(new File(path));
        }
    }

    private void checkAudioFiles(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                checkAudioFiles(file);
            } else if (isAudioFile(file)) {
                musicList.add(new MusicFile(file));
            }
        }
    }

    private boolean isAudioFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase();
        for (String audioExt : AUDIO_FILE_EXTENSIONS) if (audioExt.equals(ext)) return true;
        return false;
    }

    @Override
    public int getCount() {
        return musicList.size();
    }

    @Override
    public Object getItem(int position) {
        return position < musicList.size() ? musicList.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
        MusicFile file = musicList.get(position);
        ((TextView) convertView.findViewById(android.R.id.text1)).setText(file.getFileName());
        ((TextView) convertView.findViewById(android.R.id.text2)).setText(file.getFullPath());
        return convertView;
    }

    public static class MusicFile {
        private final File file;

        public MusicFile(File file) {
            this.file = file;
        }

        public MusicFile(String path) {
            this(new File(path));
        }

        public File getFile() {
            return file;
        }

        public String getFileName() {
            return file.getName();
        }

        public String getFullPath() {
            return file.getAbsolutePath();
        }
    }
}
