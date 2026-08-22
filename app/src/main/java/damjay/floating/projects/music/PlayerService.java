package damjay.floating.projects.music;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ListAdapter;
import android.widget.ListView;
import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.MusicListAdapter;
import damjay.floating.projects.utils.ViewsUtils;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Scanner;

public class PlayerService extends Service {
    public static final String CHOSEN_FOLDERS_FILE = "music_chosen_folders.hst";
    public static final String MUSIC_HISTORY_FILE = "music_history.hst";
    private ArrayList<String> chosenDirectories;
    private View collapsedPlayer;
    private View expandedPlayer;
    private ArrayList<String> historyFiles;
    private WindowManager.LayoutParams layoutParams;
    private ListView musicFiles;
    private View playerLayout;
    private View toggleFocus;
    private WindowManager windowManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!searchDirectoriesAvailable()) {
            Intent intent = new Intent();
            intent.setClass(this, FolderChooserActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            stopSelf();
            return;
        }
        playerLayout = LayoutInflater.from(this).inflate(R.layout.service_player, (ViewGroup) null);
        initializeViews();
        setOnClickListeners();
        View view = playerLayout;
        ViewsUtils.addTouchListener(view,
                ViewsUtils.getViewTouchListener(this, view, windowManager, layoutParams), true, true,
                ListView.class, null);
    }

    private void initializeViews() {
        windowManager = (WindowManager) getSystemService("window");
        expandedPlayer = playerLayout.findViewById(R.id.expanded_player);
        collapsedPlayer = playerLayout.findViewById(R.id.collapsed_player);
        toggleFocus = playerLayout.findViewById(R.id.toggle_focus);
        ListView listView = (ListView) playerLayout.findViewById(R.id.music_list);
        musicFiles = listView;
        listView.setAdapter((ListAdapter) new MusicListAdapter(this, chosenDirectories, historyFiles));
        WindowManager windowManager = windowManager;
        View view = playerLayout;
        WindowManager.LayoutParams floatingLayoutParams = ViewsUtils.getFloatingLayoutParams(true);
        layoutParams = floatingLayoutParams;
        windowManager.addView(view, floatingLayoutParams);
        expandedPlayer.setVisibility(View.GONE);
        collapsedPlayer.setVisibility(View.VISIBLE);
    }

    private void setOnClickListeners() {
        playerLayout.findViewById(R.id.launch_app)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        playerLayout.findViewById(R.id.minimize_player).setOnClickListener((v) -> {
            expandedPlayer.setVisibility(View.GONE);
            collapsedPlayer.setVisibility(View.VISIBLE);
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(playerLayout, layoutParams);
        });
        playerLayout.findViewById(R.id.close_player).setOnClickListener(v -> stopSelf());
        playerLayout.findViewById(R.id.minimize_player).callOnClick();
        collapsedPlayer.setOnClickListener((v) -> {
            expandedPlayer.setVisibility(View.VISIBLE);
            collapsedPlayer.setVisibility(View.GONE);
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            windowManager.updateViewLayout(playerLayout, layoutParams);
        });
        toggleFocus.setOnClickListener((v) -> {
            WindowManager.LayoutParams layoutParams = layoutParams;
            layoutParams.flags = layoutParams.flags == WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(playerLayout, layoutParams);
        });
    }

    private boolean searchDirectoriesAvailable() {
        File savedHistoryFile = new File(getCacheDir(), MUSIC_HISTORY_FILE);
        File chosenDirectoriesFile = new File(getCacheDir(), CHOSEN_FOLDERS_FILE);
        historyFiles = getContentArray(savedHistoryFile);
        chosenDirectories = getContentArray(chosenDirectoriesFile);
        return (historyFiles.size() == 0 && chosenDirectories.size() == 0) ? false : true;
    }

    private ArrayList<String> getContentArray(File file) {
        ArrayList<String> content = new ArrayList<>();
        try {
            Scanner stream = new Scanner(new FileInputStream(file));
            while (stream.hasNextLine()) {
                content.add(stream.nextLine());
            }
        } catch (Throwable th) {
        }
        return content;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        WindowManager windowManager = windowManager;
        if (windowManager != null) {
            windowManager.removeView(playerLayout);
        }
    }
}
