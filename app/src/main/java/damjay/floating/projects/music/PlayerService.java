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
            intent.setFlags(872415232);
            startActivity(intent);
            stopSelf();
            return;
        }
        this.playerLayout = LayoutInflater.from(this).inflate(R.layout.service_player, (ViewGroup) null);
        initializeViews();
        setOnClickListeners();
        View view = this.playerLayout;
        ViewsUtils.addTouchListener(view, ViewsUtils.getViewTouchListener(this, view, this.windowManager, this.layoutParams), true, true, ListView.class, null);
    }

    private void initializeViews() {
        this.windowManager = (WindowManager) getSystemService("window");
        this.expandedPlayer = this.playerLayout.findViewById(R.id.expanded_player);
        this.collapsedPlayer = this.playerLayout.findViewById(R.id.collapsed_player);
        this.toggleFocus = this.playerLayout.findViewById(R.id.toggle_focus);
        ListView listView = (ListView) this.playerLayout.findViewById(R.id.music_list);
        this.musicFiles = listView;
        listView.setAdapter((ListAdapter) new MusicListAdapter(this, this.chosenDirectories, this.historyFiles));
        WindowManager windowManager = this.windowManager;
        View view = this.playerLayout;
        WindowManager.LayoutParams floatingLayoutParams = ViewsUtils.getFloatingLayoutParams(true);
        this.layoutParams = floatingLayoutParams;
        windowManager.addView(view, floatingLayoutParams);
        this.expandedPlayer.setVisibility(8);
        this.collapsedPlayer.setVisibility(0);
    }

    private void setOnClickListeners() {
        this.playerLayout.findViewById(R.id.launch_app).setOnClickListener((view) -> this.m212xbc711aa2(view));
        this.playerLayout.findViewById(R.id.minimize_player).setOnClickListener((view) -> this.m213x75e8a841(view));
        this.playerLayout.findViewById(R.id.close_player).setOnClickListener((view) -> this.m214x2f6035e0(view));
        this.playerLayout.findViewById(R.id.minimize_player).callOnClick();
        this.collapsedPlayer.setOnClickListener((view) -> this.m215xe8d7c37f(view));
        this.toggleFocus.setOnClickListener((view) -> this.m216xa24f511e(view));
    }

    void m212xbc711aa2(View v) {
        ViewsUtils.launchApp(this, MainActivity.class);
    }

    void m213x75e8a841(View v) {
        this.expandedPlayer.setVisibility(8);
        this.collapsedPlayer.setVisibility(0);
        this.layoutParams.flags = 8;
        this.windowManager.updateViewLayout(this.playerLayout, this.layoutParams);
    }

    void m214x2f6035e0(View v) {
        stopSelf();
    }

    void m215xe8d7c37f(View v) {
        this.expandedPlayer.setVisibility(0);
        this.collapsedPlayer.setVisibility(8);
        this.layoutParams.flags = 32;
        this.windowManager.updateViewLayout(this.playerLayout, this.layoutParams);
    }

    void m216xa24f511e(View v) {
        WindowManager.LayoutParams layoutParams = this.layoutParams;
        layoutParams.flags = layoutParams.flags == 8 ? 32 : 8;
        this.windowManager.updateViewLayout(this.playerLayout, this.layoutParams);
    }

    private boolean searchDirectoriesAvailable() {
        File savedHistoryFile = new File(getCacheDir(), MUSIC_HISTORY_FILE);
        File chosenDirectoriesFile = new File(getCacheDir(), CHOSEN_FOLDERS_FILE);
        this.historyFiles = getContentArray(savedHistoryFile);
        this.chosenDirectories = getContentArray(chosenDirectoriesFile);
        return (this.historyFiles.size() == 0 && this.chosenDirectories.size() == 0) ? false : true;
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
        WindowManager windowManager = this.windowManager;
        if (windowManager != null) {
            windowManager.removeView(this.playerLayout);
        }
    }
}
