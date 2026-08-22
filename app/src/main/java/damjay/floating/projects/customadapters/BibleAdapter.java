package damjay.floating.projects.customadapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import damjay.floating.projects.R;
import damjay.floating.projects.bible.CombinedChapterBibleSource;
import java.io.IOException;
import java.util.ArrayList;

public class BibleAdapter extends BaseAdapter {
    private CombinedChapterBibleSource bibleSource;
    private final Context context;
    private int curBookIndex;
    private final ArrayList<String> versesList = new ArrayList<>();

    public BibleAdapter(Context context) {
        this.context = context;
        makeChapter(0, 0);
    }

    public void makeChapter(int bookIndex, int chapterIndex) {
        this.curBookIndex = bookIndex;
        if (this.bibleSource == null) {
            try {
                Context context = this.context;
                this.bibleSource = new CombinedChapterBibleSource(context, context.getFilesDir());
            } catch (Throwable t) {
                t.printStackTrace();
                return;
            }
        }
        try {
            loadVerses(this.curBookIndex, chapterIndex);
        } catch (Throwable t2) {
            t2.printStackTrace();
        }
    }

    private void loadVerses(int bookIndex, int chapterIndex) throws IOException {
        this.versesList.clear();
        CombinedChapterBibleSource combinedChapterBibleSource = this.bibleSource;
        if (combinedChapterBibleSource == null) {
            return;
        }
        char[] verses = combinedChapterBibleSource.getChapter(bookIndex, chapterIndex);
        int[] verseIndices = this.bibleSource.getChapterIndex(bookIndex, chapterIndex);
        for (int i = 0; i < verseIndices.length / 2; i++) {
            this.versesList.add(new String(verses, verseIndices[i << 1], verseIndices[(i << 1) + 1] - verseIndices[i << 1]));
        }
    }

    public int getCurrentBookIndex() {
        return this.curBookIndex;
    }

    public int getNumberOfChapters() {
        return this.bibleSource.getNumberOfChapters(this.curBookIndex);
    }

    public String[] getBooks() {
        return this.bibleSource.getBookNames();
    }

    @Override
    public int getCount() {
        if (this.bibleSource == null) {
            return 0;
        }
        return this.versesList.size();
    }

    @Override
    public Object getItem(int position) {
        return this.versesList.size() > position ? this.versesList.get(position) : Integer.valueOf(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup vg) {
        if (view == null) {
            view = LayoutInflater.from(this.context).inflate(R.layout.bible_verse, vg, false);
        }
        TextView verseNumber = (TextView) view.findViewById(R.id.bibleVerseIndex);
        TextView verseContent = (TextView) view.findViewById(R.id.bibleVerseContent);
        verseNumber.setText((position + 1) + "");
        if (this.versesList.size() > position) {
            verseContent.setText(this.versesList.get(position));
        }
        if (position == 1) {
            System.out.println(verseContent.getText());
        }
        return view;
    }
}
