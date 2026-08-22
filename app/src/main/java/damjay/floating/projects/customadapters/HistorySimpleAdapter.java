package damjay.floating.projects.customadapters;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleAdapter;
import androidx.appcompat.widget.PopupMenu;
import damjay.floating.projects.R;
import java.util.ArrayList;
import java.util.HashMap;

public class HistorySimpleAdapter extends SimpleAdapter {
    private Callback callback;
    private Context context;

    public interface Callback {
        void delete(int i);

        void run(int i);
    }

    public HistorySimpleAdapter(Context context, ArrayList<HashMap<String, Object>> list, int id, String[] entries, int[] content, Callback callback) {
        super(context, list, id, entries, content);
        this.callback = callback;
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        view.findViewById(R.id.delete_button).setOnClickListener((view) -> this.m203xda70dc34(position, view));
        view.setOnLongClickListener((view) -> this.m205x2edbb72(position, view));
        view.setOnClickListener((view) -> this.m206x972c2b11(position, view));
        return view;
    }

    void m203xda70dc34(int position, View p1) {
        this.callback.delete(position);
    }

    boolean m205x2edbb72(int position, View view1) {
        PopupMenu menu = new PopupMenu(this.context, view1);
        menu.setOnMenuItemClickListener((menuItem) -> this.m204x6eaf4bd3(position, menuItem));
        menu.inflate(R.menu.history_menu);
        menu.show();
        return true;
    }

    boolean m204x6eaf4bd3(int position, MenuItem item) {
        if (item.getItemId() == R.id.open_history_file) {
            this.callback.run(position);
            return true;
        }
        if (item.getItemId() == R.id.delete_history_file) {
            this.callback.delete(position);
            return true;
        }
        return true;
    }

    void m206x972c2b11(int position, View view12) {
        this.callback.run(position);
    }
}
