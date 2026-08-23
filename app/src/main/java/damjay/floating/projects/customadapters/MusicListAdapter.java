package damjay.floating.projects.customadapters;

import android.R;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;

public class MusicListAdapter extends BaseAdapter {
    public static final String[] AUDIO_FILE_EXTENSIONS = {"mp3", "aac"};
    private Context context;
    private ArrayList<MusicFile> musicList = new ArrayList<>();

    public MusicListAdapter(Context context, ArrayList<String> chosenDirectories, ArrayList<String> savedSongHistory) {
        this.context = context;
        initializeMusicList(savedSongHistory, chosenDirectories);
    }

    private void initializeMusicList(ArrayList<String> savedSongHistory, ArrayList<String> chosenDirectories) {
        if (savedSongHistory != null && savedSongHistory.size() > 0) {
            for (String songPath : savedSongHistory) {
                musicList.add(new MusicFile(songPath));
            }
            return;
        }
        for (String directory : chosenDirectories) {
            checkAudioFiles(new File(directory));
        }
        System.out.println("Music list is now " + musicList);
        System.out.println("Saved song history is " + savedSongHistory);
        System.out.println("Chosen directories is " + chosenDirectories);
    }

    private void checkAudioFiles(File parent) {
        File[] childFiles = parent.listFiles();
        if (childFiles == null) {
            return;
        }
        for (File file : childFiles) {
            if (!file.isDirectory()) {
                String fileName = file.getName();
                String fileExtension = fileName.substring(fileName.lastIndexOf('.', fileName.length() - 1) + 1).toLowerCase();
                for (String audioExtension : AUDIO_FILE_EXTENSIONS) {
                    if (audioExtension.equals(fileExtension)) {
                        musicList.add(new MusicFile(file));
                        break;
                    }
                }
            } else {
                checkAudioFiles(file);
            }
        }
    }

    @Override
    public int getCount() {
        if (musicList != null) {
            return musicList.size();
        }
        return 0;
    }

    @Override
    public Object getItem(int position) {
        if (musicList == null || musicList.size() <= position) {
            return null;
        }
        return musicList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.simple_list_item_2, viewGroup, false);
            view.setTag(musicList.get(position));
        }
        MusicFile musicFile = musicList.get(position);
        TextView text1 = (TextView) view.findViewById(R.id.text1);
        TextView text2 = (TextView) view.findViewById(R.id.text2);
        text1.setText(musicFile.getFileName());
        text2.setText(musicFile.getFullPath());
        return view;
    }

    static class MusicFile {
        private String directoryName;
        private String fileName;
        private String fullPath;

        MusicFile(File file) {
            this(file.getPath());
        }

        MusicFile(String path) {
            fileName = path.substring(path.lastIndexOf('/') + 1);
            String parentPath = path.substring(0, path.lastIndexOf('/'));
            fullPath = parentPath;
            if (parentPath.equals("/storage/emulated/0")) {
                directoryName = "Internal Storage Root";
            } else {
                directoryName = parentPath.substring(parentPath.lastIndexOf('/') + 1);
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

        public String toString() {
            return "fileName=" + fileName + ", fullPath=" + fullPath;
        }
    }
}
