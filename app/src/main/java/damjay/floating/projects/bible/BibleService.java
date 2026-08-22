package damjay.floating.projects.bible;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.BibleAdapter;
import damjay.floating.projects.utils.ViewsUtils;

public class BibleService extends Service {
    private BibleAdapter bibleAdapter;
    private Spinner bookList;
    private Spinner chapterList;
    private WindowManager.LayoutParams params;
    private ListView verseList;
    private View view;
    private WindowManager windowManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.windowManager = (WindowManager) getSystemService("window");
        this.view = LayoutInflater.from(this).inflate(R.layout.bible_layout, (ViewGroup) null);
        this.params = getLayoutParams();
        initializeViewItems();
        initViewSize();
        minimizeView();
        this.windowManager.addView(this.view, this.params);
        this.windowManager.updateViewLayout(this.view, this.params);
        addTouchListeners(this.view);
    }

    private void initializeViewItems() {
        this.verseList = (ListView) this.view.findViewById(R.id.bibleVerses);
        if (this.bibleAdapter == null) {
            BibleAdapter bibleAdapter = new BibleAdapter(this);
            this.bibleAdapter = bibleAdapter;
            this.verseList.setAdapter((ListAdapter) bibleAdapter);
        }
        this.bookList = (Spinner) this.view.findViewById(R.id.bibleBookSpinner);
        this.chapterList = (Spinner) this.view.findViewById(R.id.bibleChapterSpinner);
        this.view.findViewById(R.id.minimizedBible).setOnClickListener(v -> maximizeView());
        this.view.findViewById(R.id.minimizeBible).setOnClickListener(v -> minimizeView());
        setArrayAdapters();
    }

    private void setArrayAdapters() {
        ArrayAdapter<String> bookListAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, this.bibleAdapter.getBooks());
        bookListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.bookList.setAdapter((SpinnerAdapter) bookListAdapter);
        this.bookList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> spinner, View view, int position, long id) {
                if (BibleService.this.bibleAdapter.getCurrentBookIndex() != position) {
                    BibleService.this.bibleAdapter.makeChapter(position, 0);
                    ArrayAdapter<String> chapterListAdapter =
                            new ArrayAdapter<>(bibleService, android.R.layout.simple_spinner_item,
                                    bibleService.getCountTill(bibleService.bibleAdapter.getNumberOfChapters()));
                    chapterListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    BibleService.this.chapterList.setAdapter((SpinnerAdapter) chapterListAdapter);
                    BibleService.this.bibleAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> spinner) {}
        });
        ArrayAdapter<String> chapterListAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, getCountTill(this.bibleAdapter.getNumberOfChapters()));
        chapterListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.chapterList.setAdapter((SpinnerAdapter) chapterListAdapter);
        this.chapterList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> spinner, View view, int position, long id) {
                BibleService.this.bibleAdapter.makeChapter(
                        BibleService.this.bibleAdapter.getCurrentBookIndex(), position);
                BibleService.this.bibleAdapter.notifyDataSetChanged();
                BibleService.this.verseList.setAdapter((ListAdapter) BibleService.this.bibleAdapter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> spinner) {}
        });
    }

    private void initViewSize() {
        this.view.findViewById(R.id.bibleCloseView).setOnClickListener(v -> stopSelf());
        this.view.findViewById(R.id.bibleLaunchApp)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        this.view.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            ViewGroup.LayoutParams layoutParams = this.view.findViewById(R.id.bibleNavPadding).getLayoutParams();
            ViewGroup.LayoutParams layoutParams2 = this.verseList.getLayoutParams();
            int viewWidth = ViewsUtils.getViewWidth(350.0f);
            layoutParams2.width = viewWidth;
            layoutParams.width = viewWidth;
            this.verseList.getLayoutParams().height = ViewsUtils.getViewHeight(400.0f);
        });
    }

    private void minimizeView() {
        this.verseList.setVisibility(View.GONE);
        this.view.findViewById(R.id.windowControls).setVisibility(View.GONE);
        this.view.findViewById(R.id.bibleNavPadding).setVisibility(View.GONE);
        this.view.findViewById(R.id.minimizedBible).setVisibility(View.VISIBLE);
    }

    private void maximizeView() {
        this.verseList.setVisibility(View.VISIBLE);
        this.view.findViewById(R.id.windowControls).setVisibility(View.VISIBLE);
        this.view.findViewById(R.id.bibleNavPadding).setVisibility(View.VISIBLE);
        this.view.findViewById(R.id.minimizedBible).setVisibility(View.GONE);
    }

    public String[] getCountTill(int chapters) {
        String[] chapterArray = new String[chapters];
        for (int i = 1; i <= chapters; i++) {
            chapterArray[i - 1] = i + "";
        }
        return chapterArray;
    }

    private WindowManager.LayoutParams getLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                                                  : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        params.x = 0;
        params.y = 100;
        return params;
    }

    private void addTouchListeners(View view) {
        View.OnTouchListener listener = ViewsUtils.getViewTouchListener(this, view, this.windowManager, this.params);
        ViewsUtils.addTouchListener(view, listener, true, true, ListView.class, Spinner.class, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.windowManager.removeView(this.view);
    }
}
