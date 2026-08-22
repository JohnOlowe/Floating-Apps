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
        windowManager = (WindowManager) getSystemService("window");
        view = LayoutInflater.from(this).inflate(R.layout.bible_layout, (ViewGroup) null);
        params = getLayoutParams();
        initializeViewItems();
        initViewSize();
        minimizeView();
        windowManager.addView(view, params);
        windowManager.updateViewLayout(view, params);
        addTouchListeners(view);
    }

    private void initializeViewItems() {
        verseList = (ListView) view.findViewById(R.id.bibleVerses);
        if (bibleAdapter == null) {
            BibleAdapter bibleAdapter = new BibleAdapter(this);
            this.bibleAdapter = bibleAdapter;
            verseList.setAdapter((ListAdapter) bibleAdapter);
        }
        bookList = (Spinner) view.findViewById(R.id.bibleBookSpinner);
        chapterList = (Spinner) view.findViewById(R.id.bibleChapterSpinner);
        view.findViewById(R.id.minimizedBible).setOnClickListener(v -> maximizeView());
        view.findViewById(R.id.minimizeBible).setOnClickListener(v -> minimizeView());
        setArrayAdapters();
    }

    private void setArrayAdapters() {
        ArrayAdapter<String> bookListAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bibleAdapter.getBooks());
        bookListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bookList.setAdapter((SpinnerAdapter) bookListAdapter);
        bookList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> spinner, View view, int position, long id) {
                if (BibleService.this.bibleAdapter.getCurrentBookIndex() != position) {
                    BibleService.this.bibleAdapter.makeChapter(position, 0);
                    BibleService bibleService = BibleService.this;
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
                this, android.R.layout.simple_spinner_item, getCountTill(bibleAdapter.getNumberOfChapters()));
        chapterListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chapterList.setAdapter((SpinnerAdapter) chapterListAdapter);
        chapterList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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
        view.findViewById(R.id.bibleCloseView).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.bibleLaunchApp)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        view.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            ViewGroup.LayoutParams layoutParams = view.findViewById(R.id.bibleNavPadding).getLayoutParams();
            ViewGroup.LayoutParams layoutParams2 = verseList.getLayoutParams();
            int viewWidth = ViewsUtils.getViewWidth(350.0f);
            layoutParams2.width = viewWidth;
            layoutParams.width = viewWidth;
            verseList.getLayoutParams().height = ViewsUtils.getViewHeight(400.0f);
        });
    }

    private void minimizeView() {
        verseList.setVisibility(View.GONE);
        view.findViewById(R.id.windowControls).setVisibility(View.GONE);
        view.findViewById(R.id.bibleNavPadding).setVisibility(View.GONE);
        view.findViewById(R.id.minimizedBible).setVisibility(View.VISIBLE);
    }

    private void maximizeView() {
        verseList.setVisibility(View.VISIBLE);
        view.findViewById(R.id.windowControls).setVisibility(View.VISIBLE);
        view.findViewById(R.id.bibleNavPadding).setVisibility(View.VISIBLE);
        view.findViewById(R.id.minimizedBible).setVisibility(View.GONE);
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
        View.OnTouchListener listener = ViewsUtils.getViewTouchListener(this, view, windowManager, params);
        ViewsUtils.addTouchListener(view, listener, true, true, ListView.class, Spinner.class, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        windowManager.removeView(view);
    }
}
