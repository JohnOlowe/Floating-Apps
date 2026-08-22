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
        this.view.findViewById(R.id.minimizedBible).setOnClickListener((view) -> this.m168xf6d40ab7(view));
        this.view.findViewById(R.id.minimizeBible).setOnClickListener((view) -> this.m169x9174cd38(view));
        setArrayAdapters();
    }

    void m168xf6d40ab7(View v) {
        maximizeView();
    }

    void m169x9174cd38(View v) {
        minimizeView();
    }

    private void setArrayAdapters() {
        ArrayAdapter<String> bookListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, this.bibleAdapter.getBooks());
        bookListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.bookList.setAdapter((SpinnerAdapter) bookListAdapter);
        this.bookList.setOnItemSelectedListener(new AnonymousClass1());
        ArrayAdapter<String> chapterListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, getCountTill(this.bibleAdapter.getNumberOfChapters()));
        chapterListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.chapterList.setAdapter((SpinnerAdapter) chapterListAdapter);
        this.chapterList.setOnItemSelectedListener(new AnonymousClass2());
    }

    class AnonymousClass1 implements AdapterView.OnItemSelectedListener {
        AnonymousClass1() {
        }

        @Override
        public void onItemSelected(AdapterView<?> spinner, View view, int position, long id) {
            if (BibleService.this.bibleAdapter.getCurrentBookIndex() != position) {
                BibleService.this.bibleAdapter.makeChapter(position, 0);
                BibleService bibleService = BibleService.this;
                ArrayAdapter<String> chapterListAdapter = new ArrayAdapter<>(bibleService, android.R.layout.simple_spinner_item, bibleService.getCountTill(bibleService.bibleAdapter.getNumberOfChapters()));
                chapterListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                BibleService.this.chapterList.setAdapter((SpinnerAdapter) chapterListAdapter);
                BibleService.this.bibleAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> spinner) {
        }
    }

    class AnonymousClass2 implements AdapterView.OnItemSelectedListener {
        AnonymousClass2() {
        }

        @Override
        public void onItemSelected(AdapterView<?> spinner, View view, int position, long id) {
            BibleService.this.bibleAdapter.makeChapter(BibleService.this.bibleAdapter.getCurrentBookIndex(), position);
            BibleService.this.bibleAdapter.notifyDataSetChanged();
            BibleService.this.verseList.setAdapter((ListAdapter) BibleService.this.bibleAdapter);
        }

        @Override
        public void onNothingSelected(AdapterView<?> spinner) {
        }
    }

    private void initViewSize() {
        this.view.findViewById(R.id.bibleCloseView).setOnClickListener((view) -> this.m165x655c341e(view));
        this.view.findViewById(R.id.bibleLaunchApp).setOnClickListener((view) -> this.m166xfffcf69f(view));
        this.view.getViewTreeObserver().addOnGlobalLayoutListener(() -> this.m167x9a9db920());
    }

    void m165x655c341e(View v) {
        stopSelf();
    }

    void m166xfffcf69f(View v) {
        ViewsUtils.launchApp(this, MainActivity.class);
    }

    void m167x9a9db920() {
        ViewGroup.LayoutParams layoutParams = this.view.findViewById(R.id.bibleNavPadding).getLayoutParams();
        ViewGroup.LayoutParams layoutParams2 = this.verseList.getLayoutParams();
        int viewWidth = ViewsUtils.getViewWidth(350.0f);
        layoutParams2.width = viewWidth;
        layoutParams.width = viewWidth;
        this.verseList.getLayoutParams().height = ViewsUtils.getViewHeight(400.0f);
    }

    private void minimizeView() {
        this.verseList.setVisibility(8);
        this.view.findViewById(R.id.windowControls).setVisibility(8);
        this.view.findViewById(R.id.bibleNavPadding).setVisibility(8);
        this.view.findViewById(R.id.minimizedBible).setVisibility(0);
    }

    private void maximizeView() {
        this.verseList.setVisibility(0);
        this.view.findViewById(R.id.windowControls).setVisibility(0);
        this.view.findViewById(R.id.bibleNavPadding).setVisibility(0);
        this.view.findViewById(R.id.minimizedBible).setVisibility(8);
    }

    public String[] getCountTill(int chapters) {
        String[] chapterArray = new String[chapters];
        for (int i = 1; i <= chapters; i++) {
            chapterArray[i - 1] = i + "";
        }
        return chapterArray;
    }

    private WindowManager.LayoutParams getLayoutParams() {
        int type = Build.VERSION.SDK_INT >= 26 ? 2038 : 2002;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-2, -2, type, 8, -3);
        params.gravity = 8388659;
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
