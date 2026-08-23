package damjay.floating.projects.music;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.MusicListAdapter;
import damjay.floating.projects.utils.ViewsUtils;

public class PlayerService extends Service {
    public static final String CHOSEN_FOLDERS_FILE = "music_chosen_folders.hst";
    public static final String MUSIC_HISTORY_FILE = "music_history.hst";
    private View view;
    private View collapsed;
    private View expanded;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private MediaPlayer mediaPlayer;
    private MusicListAdapter adapter;
    private int currentIndex = -1;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("InflateParams")
    @Override
    public void onCreate() {
        super.onCreate();
        view = LayoutInflater.from(this).inflate(R.layout.service_player, null);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = ViewsUtils.getFloatingLayoutParams();
        windowManager.addView(view, layoutParams);
        initializeViews();
    }

    private void initializeViews() {
        collapsed = view.findViewById(R.id.collapsed_player);
        expanded = view.findViewById(R.id.expanded_player);
        adapter = new MusicListAdapter(this, readFolders(), null);
        ListView musicList = view.findViewById(R.id.music_list);
        musicList.setAdapter(adapter);
        musicList.setOnItemClickListener((parent, itemView, position, id) -> play(position));

        view.findViewById(R.id.launch_app).setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        view.findViewById(R.id.close_player).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.minimize_player).setOnClickListener(v -> minimize());
        view.findViewById(R.id.toggle_focus).setOnClickListener(v -> ViewsUtils.launchApp(this, FolderChooserActivity.class));
        view.findViewById(R.id.previous).setOnClickListener(v -> play(currentIndex <= 0 ? adapter.getCount() - 1 : currentIndex - 1));
        view.findViewById(R.id.next).setOnClickListener(v -> play((currentIndex + 1) % Math.max(adapter.getCount(), 1)));
        view.findViewById(R.id.play_pause).setOnClickListener(v -> togglePlay());
        collapsed.setOnClickListener(v -> maximize());
        ViewsUtils.addTouchListener(view, ViewsUtils.getViewTouchListener(this, view, windowManager, layoutParams), true, true, ListView.class, null);
        minimize();
    }

    private ArrayList<String> readFolders() {
        SharedPreferences prefs = getSharedPreferences(FolderChooserActivity.PREFS_NAME, MODE_PRIVATE);
        ArrayList<String> folders = new ArrayList<>(prefs.getStringSet(FolderChooserActivity.MUSIC_FOLDERS, new HashSet<>()));
        if (folders.isEmpty()) folders.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
        return folders;
    }

    private void play(int position) {
        if (adapter.getCount() == 0) {
            Toast.makeText(this, R.string.choose_music_folder_msg, Toast.LENGTH_LONG).show();
            ViewsUtils.launchApp(this, FolderChooserActivity.class);
            return;
        }
        MusicListAdapter.MusicFile file = (MusicListAdapter.MusicFile) adapter.getItem(position);
        if (file == null) return;
        try {
            if (mediaPlayer != null) mediaPlayer.release();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getFullPath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(mp -> play((currentIndex + 1) % Math.max(adapter.getCount(), 1)));
            currentIndex = position;
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_LONG).show();
        }
    }

    private void togglePlay() {
        if (mediaPlayer == null) {
            play(currentIndex < 0 ? 0 : currentIndex);
        } else if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.start();
        }
    }

    private void minimize() {
        expanded.setVisibility(View.GONE);
        collapsed.setVisibility(View.VISIBLE);
    }

    private void maximize() {
        expanded.setVisibility(View.VISIBLE);
        collapsed.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) mediaPlayer.release();
        if (windowManager != null && view != null) windowManager.removeView(view);
    }
}
